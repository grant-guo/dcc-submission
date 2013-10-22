/*
 * Copyright (c) 2013 The Ontario Institute for Cancer Research. All rights reserved.                             
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
package org.icgc.dcc.submission.validation.service;

import static java.lang.String.format;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.hadoop.fs.Path;
import org.icgc.dcc.submission.dictionary.DictionaryService;
import org.icgc.dcc.submission.dictionary.model.Dictionary;
import org.icgc.dcc.submission.fs.DccFileSystem;
import org.icgc.dcc.submission.fs.ReleaseFileSystem;
import org.icgc.dcc.submission.fs.SubmissionDirectory;
import org.icgc.dcc.submission.release.model.QueuedProject;
import org.icgc.dcc.submission.release.model.Release;
import org.icgc.dcc.submission.validation.CascadingStrategy;
import org.icgc.dcc.submission.validation.FilePresenceException;
import org.icgc.dcc.submission.validation.Plan;
import org.icgc.dcc.submission.validation.Planner;
import org.icgc.dcc.submission.validation.factory.CascadingStrategyFactory;
import org.icgc.dcc.submission.validation.firstpass.FirstPassChecker;
import org.icgc.dcc.submission.validation.service.ValidationQueueService.ValidationCascadeListener;

import cascading.cascade.Cascade;
import cascading.cascade.CascadeListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

/**
 * Wraps validation call for the {@code ValidationQueueService} and {@Main} (the validation one) to use
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @_(@Inject))
public class ValidationService {

  @NonNull
  private final Planner planner;
  @NonNull
  private final DccFileSystem dccFileSystem;
  @NonNull
  private final DictionaryService dictionaryService;
  @NonNull
  private final CascadingStrategyFactory cascadingStrategyFactory;

  public Plan prepareValidation(final Release release, final QueuedProject queuedProject,
      final ValidationCascadeListener listener) throws FilePresenceException {

    String dictionaryVersion = release.getDictionaryVersion();
    Dictionary dictionary = dictionaryService.getFromVersion(dictionaryVersion);

    if (dictionary == null) {
      throw new ValidationServiceException(format("No dictionary found with version %s, in release %s",
          dictionaryVersion, release.getName()));
    } else {
      log.info("Preparing cascade for project '{}'", queuedProject.getKey());

      ReleaseFileSystem releaseFilesystem = dccFileSystem.getReleaseFilesystem(release);

      SubmissionDirectory submissionDirectory = releaseFilesystem.getSubmissionDirectory(queuedProject.getKey());

      Path rootDir = submissionDirectory.getSubmissionDirPath();
      Path outputDir = new Path(submissionDirectory.getValidationDirPath());
      Path systemDir = releaseFilesystem.getSystemDirectory();

      log.info("Validation for '{}' has rootDir = {} ", queuedProject.getKey(), rootDir);
      log.info("Validation for '{}' has outputDir = {} ", queuedProject.getKey(), outputDir);
      log.info("Validation for '{}' has systemDir = {} ", queuedProject.getKey(), systemDir);

      // TODO: File Checker
      CascadingStrategy cascadingStrategy = cascadingStrategyFactory.get(rootDir, outputDir, systemDir);
      checkWellFormedness();
      Plan plan = planValidation(queuedProject, submissionDirectory, cascadingStrategy, dictionary, listener);

      listener.setPlan(plan);

      log.info("Prepared cascade for project {}", queuedProject.getKey());
      return plan;
    }
  }

  /**
   * Plans and connects the cascade running the validation.
   * <p>
   * Note that emptying of the .validation dir happens right before launching the cascade in {@link Plan#startCascade()}
   */
  @VisibleForTesting
  public Plan planValidation(QueuedProject queuedProject, SubmissionDirectory submissionDirectory,
      CascadingStrategy cascadingStrategy, Dictionary dictionary, CascadeListener cascadeListener)
      throws FilePresenceException {
    // TODO: Separate plan and connect?
    log.info("Planning cascade for project {}", queuedProject.getKey());
    Plan plan = planner.plan(queuedProject, submissionDirectory, cascadingStrategy, dictionary);
    log.info("Planned cascade for project {}", queuedProject.getKey());

    log.info("# internal flows: {}", Iterables.size(plan.getInternalFlows()));
    log.info("# external flows: {}", Iterables.size(plan.getExternalFlows()));

    log.info("Connecting cascade for project {}", queuedProject.getKey());
    plan.connect(cascadingStrategy);
    log.info("Connected cascade for project {}", queuedProject.getKey());
    if (plan.hasFileLevelErrors()) { // determined during connection
      log.info(format("Submission has file-level errors, throwing a '%s'",
          FilePresenceException.class.getSimpleName()));
      throw new FilePresenceException(plan); // the queue manager will handle it
    }

    return plan.addCascadeListener(cascadeListener, queuedProject);
  }

  /**
   * Starts validation in a asynchronous manner.
   * <p>
   * {@code Plan} contains the {@code Cascade}.<br/>
   * This is a non-blocking call, completion is handled by
   * <code>{@link ValidationCascadeListener#onCompleted(Cascade)}</code>
   */
  public void startValidation(Plan plan) {
    QueuedProject queuedProject = plan.getQueuedProject();
    String projectKey = queuedProject.getKey();

    log.info("starting validation on project {}", projectKey);
    plan.startCascade();
  }

  /**
   * Temporarily and until properly re-written (DCC-1820).
   */
  private void checkWellFormedness() throws FilePresenceException {
    if (FirstPassChecker.check()) {
      // Always returns true for now
      log.info("Submission is well-formed.");
    } else {
      log.info("Submission has well-formedness problems"); // TODO: expand
      // FIXME: pass appropriate objects: offending project key and Map<String, TupleState> fileLevelErrors
      throw new FilePresenceException(null);
    }
  }

}
