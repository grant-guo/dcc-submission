/**
 * Copyright 2012(c) The Ontario Institute for Cancer Research. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with 
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT 
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED 
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER 
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.validation.service;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.icgc.dcc.dictionary.DictionaryService;
import org.icgc.dcc.dictionary.model.Dictionary;
import org.icgc.dcc.release.ReleaseService;
import org.icgc.dcc.release.model.Release;
import org.icgc.dcc.release.model.ReleaseState;
import org.icgc.dcc.release.model.Submission;
import org.icgc.dcc.release.model.SubmissionState;
import org.icgc.dcc.validation.FatalPlanningException;
import org.icgc.dcc.validation.Plan;
import org.icgc.dcc.validation.cascading.TupleState;
import org.icgc.dcc.validation.report.Outcome;
import org.icgc.dcc.validation.report.SchemaReport;
import org.icgc.dcc.validation.report.SubmissionReport;
import org.icgc.dcc.validation.report.ValidationErrorReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AbstractService;
import com.google.inject.Inject;

/**
 * Manages validation queue that:<br>
 * - launches validation for queue submissions<br>
 * - updates submission states upon termination of the validation process
 */
public class ValidationQueueManagerService extends AbstractService {

  private static final Logger log = LoggerFactory.getLogger(ValidationQueueManagerService.class);

  private static final int POLLING_FREQUENCY_PER_SEC = 1;

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  private final ReleaseService releaseService;

  private final DictionaryService dictionaryService;

  private final ValidationService validationService;

  private ScheduledFuture<?> schedule;

  @Inject
  public ValidationQueueManagerService(final ReleaseService releaseService, final DictionaryService dictionaryService,
      ValidationService validationService) {

    checkArgument(releaseService != null);
    checkArgument(dictionaryService != null);
    checkArgument(validationService != null);

    this.releaseService = releaseService;
    this.dictionaryService = dictionaryService;
    this.validationService = validationService;
  }

  @Override
  protected void doStart() {
    notifyStarted();
    startScheduler();
  }

  @Override
  protected void doStop() {
    stopScheduler();
    notifyStopped();
  }

  private void startScheduler() {
    log.info("polling queue every {} second", POLLING_FREQUENCY_PER_SEC);

    schedule = scheduler.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        Optional<String> nextProjectKey = Optional.<String> absent();
        Optional<Throwable> criticalThrowable = Optional.<Throwable> absent();
        try {
          if(isRunning() && releaseService.hasNextRelease()) {
            nextProjectKey = releaseService.getNextRelease().getNextInQueue();
            if(nextProjectKey.isPresent()) {
              String projectKey = nextProjectKey.get();
              log.info("next in queue {}", projectKey);
              validateSubmission(projectKey);
            }
          }
        } catch(FatalPlanningException e) { // potentially thrown by validateSubmission() upon file-level errors
          try {
            handleAbortedValidation(e);
          } catch(Throwable t) {
            criticalThrowable = Optional.fromNullable(t);
          }
        } catch(Throwable t) { // exception thrown within the run method are not logged otherwise (NullPointerException
                               // for instance)
          criticalThrowable = Optional.fromNullable(t);
        } finally {

          /*
           * When a scheduled job throws an exception to the executor, all future runs of the job are cancelled. Thus,
           * we should never throw an exception to our executor otherwise a server restart is necessary.
           */
          if(criticalThrowable.isPresent()) {
            Throwable t = criticalThrowable.get();
            log.error("a critical error occured while processing the validation queue", t);

            if(nextProjectKey != null && nextProjectKey.isPresent()) {
              String projectKey = nextProjectKey.get();
              try {
                dequeue(projectKey, SubmissionState.ERROR);
              } catch(Throwable t2) {
                log.error(String.format("a critical error occured while attempting to dequeue project %s", projectKey),
                    t2);
              }
            } else {
              log.error(String.format(
                  "next project in queue not present, could not dequeue nor set submission state to ",
                  SubmissionState.ERROR));
            }
          }
        }
      }

      /**
       * May throw unchecked FatalPlanningException upon file-level errors (too critical to continue)
       */
      private void validateSubmission(final String projectKey) throws FatalPlanningException {
        Release release = releaseService.getNextRelease().getRelease();
        if(release == null) {
          throw new ValidationServiceException("cannot access the next release");
        } else {
          String dictionaryVersion = release.getDictionaryVersion();
          Dictionary dictionary = dictionaryService.getFromVersion(dictionaryVersion);
          if(dictionary == null) {
            throw new ValidationServiceException(String
                .format("no dictionary found with version %s", dictionaryVersion));
          } else {
            if(release.getState() == ReleaseState.OPENED) {
              Plan plan = validationService.validate(release, projectKey);
              handleCascadeStatus(plan, projectKey);
            } else {
              log.info("Release was closed during validation; states not changed");
            }
          }
        }
      }

      private void handleCascadeStatus(final Plan plan, final String projectKey) {
        if(plan.getCascade().getCascadeStats().isSuccessful()) {
          handleCompletedValidation(projectKey, plan);
        } else {
          handleUnexpectedException(projectKey);
        }
      }

    }, POLLING_FREQUENCY_PER_SEC, POLLING_FREQUENCY_PER_SEC, TimeUnit.SECONDS);
  }

  private void stopScheduler() {
    try {
      boolean cancel = schedule.cancel(true);
      log.info("attempt to cancel returned {}", cancel);
    } finally {
      scheduler.shutdown();
    }
  }

  public void handleAbortedValidation(FatalPlanningException e) {
    String projectKey = e.getProjectKey();
    Plan plan = e.getPlan();
    if(plan.hasFileLevelErrors() == false) {
      new AssertionError(); // by design since this should be the condition for throwing the
                            // FatalPlanningException
    }

    Map<String, TupleState> fileLevelErrors = plan.getFileLevelErrors();
    log.error("file errors (fatal planning errors):\n\t{}", fileLevelErrors);

    log.info("about to dequeue project key {}", projectKey);
    dequeue(projectKey, SubmissionState.INVALID);

    checkArgument(projectKey != null);
    SubmissionReport report = new SubmissionReport();

    List<SchemaReport> schemaReports = new ArrayList<SchemaReport>();
    for(String schema : fileLevelErrors.keySet()) {
      SchemaReport schemaReport = new SchemaReport();
      Iterator<TupleState.TupleError> es = fileLevelErrors.get(schema).getErrors().iterator();
      List<ValidationErrorReport> errReport = Lists.newArrayList();
      while(es.hasNext()) {
        errReport.add(new ValidationErrorReport(es.next()));
      }
      schemaReport.setErrors(errReport);
      schemaReport.setName(schema);
      schemaReports.add(schemaReport);
    }
    report.setSchemaReports(schemaReports);
    setSubmissionReport(projectKey, report);
  }

  public void handleCompletedValidation(String projectKey, Plan plan) {
    checkArgument(projectKey != null);

    SubmissionReport report = new SubmissionReport();
    Outcome outcome = plan.collect(report);

    log.info("completed validation - about to dequeue project key {}/set submission its state", projectKey);
    if(outcome == Outcome.PASSED) {
      dequeue(projectKey, SubmissionState.VALID);
    } else {
      dequeue(projectKey, SubmissionState.INVALID);
    }
    setSubmissionReport(projectKey, report);
  }

  private void setSubmissionReport(String projectKey, SubmissionReport report) {
    log.info("starting report collecting on project {}", projectKey);

    Release release = releaseService.getNextRelease().getRelease();

    Submission submission = this.releaseService.getSubmission(release.getName(), projectKey);

    submission.setReport(report);

    // persist the report to DB
    this.releaseService.updateSubmissionReport(release.getName(), projectKey, submission.getReport());
    log.info("report collecting finished on project {}", projectKey);
  }

  public void handleUnexpectedException(String projectKey) {
    checkArgument(projectKey != null);
    log.info("failed validation from unknown error - about to dequeue project key {}", projectKey);
    dequeue(projectKey, SubmissionState.ERROR);
  }

  private void dequeue(String projectKey, SubmissionState state) {
    Optional<String> dequeuedProjectKey = releaseService.dequeue(projectKey, state);
    if(dequeuedProjectKey.isPresent() == false) {
      log.warn("could not dequeue project {}, maybe the queue was emptied in the meantime?", projectKey);
    }
  }

}
