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
package org.icgc.dcc.release;

import java.util.List;

import org.icgc.dcc.core.model.InvalidStateException;
import org.icgc.dcc.core.model.Project;
import org.icgc.dcc.core.model.QProject;
import org.icgc.dcc.dictionary.model.Dictionary;
import org.icgc.dcc.dictionary.model.DictionaryState;
import org.icgc.dcc.filesystem.DccFileSystem;
import org.icgc.dcc.filesystem.ReleaseFileSystem;
import org.icgc.dcc.release.model.Release;
import org.icgc.dcc.release.model.ReleaseState;
import org.icgc.dcc.release.model.Submission;
import org.icgc.dcc.release.model.SubmissionState;
import org.icgc.dcc.web.ServerErrorCode;
import org.icgc.dcc.web.validator.InvalidNameException;
import org.icgc.dcc.web.validator.NameValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.UpdateOperations;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mysema.query.mongodb.morphia.MorphiaQuery;

import static com.google.common.base.Preconditions.checkArgument;

public class NextRelease extends BaseRelease {

  private static final Logger log = LoggerFactory.getLogger(NextRelease.class);

  private final DccLocking dccLocking;

  public NextRelease(final DccLocking dccLocking, final Release release, final Morphia morphia,
      final Datastore datastore, final DccFileSystem fs) throws IllegalReleaseStateException {
    super(release, morphia, datastore, fs);
    checkArgument(dccLocking != null);

    this.dccLocking = dccLocking; // TODO: moveup (DCC-685)?
    dccLocking.setDatastore(datastore);

    if(release.getState() != ReleaseState.OPENED) {
      throw new IllegalReleaseStateException(release, ReleaseState.OPENED);
    }
  }

  public List<String> getQueued() {
    return getRelease().getQueuedProjectKeys();
  }

  public NextRelease release(final String nextReleaseName) throws InvalidStateException {
    checkArgument(nextReleaseName != null);

    // check for next release name
    if(NameValidator.validate(nextReleaseName) == false) {
      throw new InvalidNameException(nextReleaseName);
    }

    Release nextRelease = null;
    Release oldRelease = dccLocking.acquireReleasingLock(); // TODO: for now nothing checks for it (DCC-685); also
                                                            // consider reentrance out of safety
    try {
      String errorMessage;

      if(oldRelease == null) { // just in case (can't really happen)
        errorMessage = "could not acquire lock on release";
        log.error(errorMessage);
        throw new ReleaseException("ReleaseException");
      }
      if(oldRelease.equals(this.getRelease()) == false) { // just in case (can't really happen)
        errorMessage = oldRelease + " != " + this.getRelease();
        log.error(errorMessage);
        throw new ReleaseException("ReleaseException");
      }
      if(atLeastOneSignedOff(oldRelease) == false) { // check for signed-off submission states (must have at least one)
        errorMessage = "no signed off project in " + oldRelease;
        log.error(errorMessage);
        throw new InvalidStateException(ServerErrorCode.SIGNED_OFF_SUBMISSION_REQUIRED, errorMessage);
      }
      if(oldRelease.getQueue().isEmpty() == false) {
        errorMessage = "some projects are still enqueue in " + oldRelease;
        log.error(errorMessage);
        throw new InvalidStateException(ServerErrorCode.QUEUE_NOT_EMPTY, errorMessage);
      }

      String dictionaryVersion = oldRelease.getDictionaryVersion();
      if(dictionaryVersion == null) {
        errorMessage = "could not find a dictionary matching null";
        log.error(errorMessage);
        throw new InvalidStateException(ServerErrorCode.RELEASE_MISSING_DICTIONARY, errorMessage); // TODO: new kind of
                                                                                                   // exception rather?
      }
      if(forName(nextReleaseName) != null) {
        errorMessage = "found a conflicting release for name " + nextReleaseName;
        log.error(errorMessage);
        throw new InvalidStateException(ServerErrorCode.DUPLICATE_RELEASE_NAME, errorMessage);
      }

      // critical operations
      nextRelease = createNextRelease(nextReleaseName, oldRelease, dictionaryVersion);
      setupNextReleaseFileSystem(oldRelease, nextRelease, oldRelease.getProjectKeys()); // TODO: fix situation regarding
                                                                                        // aborting fs operations?
      closeDictionary(dictionaryVersion);
      completeOldRelease(oldRelease);
    } finally {
      Release relinquishedRelease = dccLocking.relinquishReleasingLock();
      if(relinquishedRelease == null || //
          relinquishedRelease.equals(oldRelease) == false) { // just in case
        log.error("could not relinquish lock on release {}, obtaining {}",
            new Object[] { oldRelease, relinquishedRelease });
        throw new ReleaseException("ReleaseException");
      }
    }

    return new NextRelease(dccLocking, nextRelease, morphia(), datastore(), fileSystem());
  }

  private Release createNextRelease(final String name, final Release oldRelease, final String dictionaryVersion) {
    Release nextRelease = new Release(name);
    nextRelease.setDictionaryVersion(dictionaryVersion);
    nextRelease.setState(ReleaseState.OPENED);
    for(Submission submission : oldRelease.getSubmissions()) {
      Submission newSubmission = new Submission(submission.getProjectKey());
      if(submission.getState() == SubmissionState.SIGNED_OFF) {
        newSubmission.setState(SubmissionState.NOT_VALIDATED);
      } else {
        newSubmission.setState(submission.getState());
        newSubmission.setReport(submission.getReport());
      }
      nextRelease.addSubmission(newSubmission);
    }
    datastore().save(nextRelease); // TODO: put in ReleaseService?
    return nextRelease;
  }

  private void setupNextReleaseFileSystem(final Release oldRelease, final Release nextRelease,
      final Iterable<String> oldProjectKeys) {
    fileSystem().createReleaseFilesystem(nextRelease, Sets.newLinkedHashSet(oldProjectKeys));
    ReleaseFileSystem newReleaseFilesystem = fileSystem().getReleaseFilesystem(nextRelease);
    ReleaseFileSystem oldReleaseFilesystem = fileSystem().getReleaseFilesystem(oldRelease);
    newReleaseFilesystem.moveFrom(oldReleaseFilesystem, getProjectsToMove(oldRelease));
  }

  /**
   * Idempotent.
   */
  private void closeDictionary(final String oldDictionaryVersion) { // TODO: move to dictionary service?
    datastore().findAndModify( //
        datastore().createQuery(Dictionary.class) //
            .filter("version", oldDictionaryVersion), //
        datastore().createUpdateOperations(Dictionary.class) //
            .set("state", DictionaryState.CLOSED));
  }

  private void completeOldRelease(final Release oldRelease) {
    oldRelease.setState(ReleaseState.COMPLETED);
    oldRelease.setReleaseDate();
    List<Submission> submissions = oldRelease.getSubmissions();
    for(int i = submissions.size() - 1; i >= 0; i--) {
      if(submissions.get(i).getState() != SubmissionState.SIGNED_OFF) {
        submissions.remove(i);
      }
    }

    datastore().findAndModify( //
        query() //
            .filter("name", oldRelease.getName()), //
        update() //
            .set("state", oldRelease.getState()) //
            .set("releaseDate", oldRelease.getReleaseDate()) //
            .set("submissions", submissions));
  }

  boolean atLeastOneSignedOff(Release release) {
    for(Submission submission : release.getSubmissions()) {
      if(submission.getState() == SubmissionState.SIGNED_OFF) {
        return true;
      }
    }
    return false;
  }

  private ImmutableList<Project> getProjectsToMove(final Release oldRelease) {
    List<Project> projects = Lists.newArrayList();
    for(Submission submission : oldRelease.getSubmissions()) {
      if(submission.getState() != SubmissionState.SIGNED_OFF) {
        Project project = projectFromKey(submission.getProjectKey());
        projects.add(project);
      }
    }
    return ImmutableList.<Project> copyOf(projects);
  }

  private Release forName(final String nextReleaseName) { // TODO: put in ReleaseService?
    return query().filter("name", nextReleaseName).get();
  }

  private Query<Release> query() {
    return datastore().createQuery(Release.class);
  }

  private UpdateOperations<Release> update() {
    return datastore().createUpdateOperations(Release.class);
  }

  private Project projectFromKey(final String projectKey) { // TODO: move to project service?
    return new MorphiaQuery<Project>(morphia(), datastore(), QProject.project).where(
        QProject.project.key.eq(projectKey)).singleResult();
  }
}
