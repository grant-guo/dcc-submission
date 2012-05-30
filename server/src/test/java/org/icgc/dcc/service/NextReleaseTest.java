package org.icgc.dcc.service;

import static org.junit.Assert.assertEquals;

import org.icgc.dcc.model.Release;
import org.icgc.dcc.model.ReleaseState;
import org.icgc.dcc.model.Submission;
import org.icgc.dcc.model.SubmissionState;
import org.junit.Test;

public class NextReleaseTest {

  @Test
  public void test() {
    Release release = new Release();
    Submission submission = new Submission();
    release.getSubmissions().add(submission);
    NextRelease nextRelease = new NextRelease(release);

    assertEquals(nextRelease.getRelease().getState(), ReleaseState.OPENED);

    nextRelease.signOff(submission);

    assertEquals(submission.getState(), SubmissionState.SIGNED_OFF);

    NextRelease newNextRelease = nextRelease.release(release);

    assertEquals(release.getState(), ReleaseState.COMPLETED);
    assertEquals(newNextRelease.getRelease().getState(), ReleaseState.OPENED);
  }
}
