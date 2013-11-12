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
package org.icgc.dcc.submission.normalization;

import static cascading.cascade.CascadeDef.cascadeDef;
import static cascading.flow.FlowDef.flowDef;
import static java.lang.String.format;
import static lombok.AccessLevel.PRIVATE;
import static org.icgc.dcc.core.model.FieldNames.SubmissionFieldNames.SUBMISSION_OBSERVATION_ANALYSIS_ID;
import static org.icgc.dcc.core.model.SubmissionFileTypes.SubmissionFileType.SSM_P_TYPE;
import static org.icgc.dcc.submission.normalization.NormalizationUtils.getFileSchema;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.core.model.SubmissionFileTypes.SubmissionFileType;
import org.icgc.dcc.hadoop.fs.DccFileSystem2;
import org.icgc.dcc.submission.dictionary.model.FileSchema;
import org.icgc.dcc.submission.normalization.NormalizationContext.DefaultNormalizationContext;
import org.icgc.dcc.submission.normalization.NormalizationReport.NormalizationCounter;
import org.icgc.dcc.submission.normalization.steps.AlleleMasking;
import org.icgc.dcc.submission.normalization.steps.FinalCounting;
import org.icgc.dcc.submission.normalization.steps.InitialCounting;
import org.icgc.dcc.submission.normalization.steps.MutationRebuilding;
import org.icgc.dcc.submission.normalization.steps.PreMasking;
import org.icgc.dcc.submission.normalization.steps.PrimaryKeyGeneration;
import org.icgc.dcc.submission.normalization.steps.RedundantObservationRemoval;
import org.icgc.dcc.submission.normalization.steps.hacks.HackFieldDiscarding;
import org.icgc.dcc.submission.normalization.steps.hacks.HackNewFieldsSynthesis;
import org.icgc.dcc.submission.validation.core.ValidationContext;
import org.icgc.dcc.submission.validation.core.Validator;
import org.icgc.dcc.submission.validation.platform.PlatformStrategy;

import cascading.cascade.Cascade;
import cascading.cascade.CascadeConnector;
import cascading.flow.Flow;
import cascading.pipe.Pipe;
import cascading.tap.Tap;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;

/**
 * TODO See https://wiki.oicr.on.ca/display/DCCSOFT/Data+Normalizer+Component
 */
@Slf4j
@RequiredArgsConstructor(access = PRIVATE)
public final class NormalizationValidator implements Validator {

  public static final String COMPONENT_NAME = "normalizer";

  /**
   * 
   */
  private static final SubmissionFileType FOCUS_TYPE = SSM_P_TYPE;

  private static final String CASCADE_NAME = format("%s-cascade", COMPONENT_NAME);
  private static final String FLOW_NAME = format("%s-flow", COMPONENT_NAME);
  private static final String START_PIPE_NAME = format("%s-start", COMPONENT_NAME);
  private static final String END_PIPE_NAME = format("%s-end", COMPONENT_NAME);

  /**
   * 
   */
  private final DccFileSystem2 dccFileSystem2;

  /**
   * Subset...TODO
   */
  private final Config config;

  /**
   * Order typically matters.
   */
  private final ImmutableList<NormalizationStep> steps;

  public static NormalizationValidator getDefaultInstance(DccFileSystem2 dccFileSystem2, Config config) {
    return new NormalizationValidator(
        dccFileSystem2,
        config,
        new ImmutableList.Builder<NormalizationStep>() // Order matters for some steps

            .add(new InitialCounting())

            .add(new HackFieldDiscarding("mutation")) // Hack
            .add(new HackNewFieldsSynthesis("mutated_from_allele", "mutated_to_allele")) // Hack

            // Must happen before rebuilding the mutation
            .add(new PreMasking()) // Must happen no matter what
            .add(new AlleleMasking(config)) // May be skipped (partially or not)

            // Must happen after allele masking
            .add(new RedundantObservationRemoval(SUBMISSION_OBSERVATION_ANALYSIS_ID)) // May be skipped
            .add(new MutationRebuilding())

            // Must happen after removing duplicates and allele masking
            .add(new PrimaryKeyGeneration())

            .add(new HackFieldDiscarding("mutated_from_allele")) // Hack
            .add(new HackFieldDiscarding("mutated_to_allele")) // Hack

            .add(new FinalCounting())

            .build());
  }

  @Override
  public void validate(ValidationContext validationContext) {
    val optional = grabSubmissionFile(FOCUS_TYPE, validationContext);

    if (optional.isPresent()) {
      String ssmPFile = optional.get();
      log.info("Starting normalization for {} file: '{}'", FOCUS_TYPE, ssmPFile);
      normalize(ssmPFile, validationContext);
      log.info("Finished normalization for {} file: '{}'", FOCUS_TYPE, ssmPFile);
    } else {
      log.info(
          "Skipping normalization for {}, no matching file in submission: '{}'",
          new Object[] { FOCUS_TYPE, optional.get(), validationContext.getSubmissionDirectory().listFile() });
    }
  }

  /**
   * 
   */
  private void normalize(String fileName, ValidationContext validationContext) {
    String releaseName = validationContext.getRelease().getName();
    String projectKey = validationContext.getProjectKey();

    // Plan cascade
    val pipes = planCascade(
        DefaultNormalizationContext.getNormalizationContext(
            validationContext.getDictionary(),
            FOCUS_TYPE));

    // Connect cascade
    val connectedCascade = connectCascade(
        pipes,
        validationContext.getPlatformStrategy(),
        getFileSchema(
            validationContext.getDictionary(),
            FOCUS_TYPE),
        releaseName,
        projectKey);

    // Run cascade synchronously
    connectedCascade.completeCascade();

    // Perform sanity check on counters
    NormalizationReporter.performSanityChecks(connectedCascade);

    // Report results
    val checker = NormalizationReporter.collectPotentialErrors(config, connectedCascade, fileName);
    if (checker.isLikelyErroneous()) {
      log.warn("The submission is erroneous from the normalization standpoint: '{}'", checker);
      NormalizationReporter.reportError(validationContext, checker);
    } else {
      log.info("No errors were encountered during normalization");
      internalStatisticsReport(connectedCascade);
      externalStatisticsReport(fileName, connectedCascade, validationContext);
    }
  }

  /**
   * 
   */
  private Pipes planCascade(NormalizationContext normalizationContext) {
    Pipe startPipe = new Pipe(START_PIPE_NAME);

    Pipe pipe = startPipe;
    for (NormalizationStep step : steps) {
      if (NormalizationConfig.isEnabled(step, config)) {
        log.info("Adding step '{}'", step.shortName());
        pipe = step.extend(pipe, normalizationContext);
      } else {
        log.info("Skipping disabled step '{}'", step.shortName());
      }
    }
    Pipe endPipe = new Pipe(END_PIPE_NAME, pipe);

    return new Pipes(startPipe, endPipe);
  }

  /**
   * 
   */
  private ConnectedCascade connectCascade(
      Pipes pipes,
      PlatformStrategy platformStrategy, FileSchema fileSchema,
      String releaseName, String projectKey) {

    val flowDef =
        flowDef()
            .setName(FLOW_NAME)
            .addSource(
                pipes.getStartPipe(),
                getInputTap(platformStrategy, fileSchema))
            .addTailSink(
                pipes.getEndPipe(),
                getOutputTap(releaseName, projectKey));

    Flow<?> flow = platformStrategy // TODO: not re-using the submission's platform strategy
        .getFlowConnector()
        .connect(flowDef);

    val cascade = new CascadeConnector()
        .connect(
        cascadeDef()
            .setName(CASCADE_NAME)
            .addFlow(flow));

    return new ConnectedCascade(releaseName, projectKey, flow, cascade);
  }

  /**
   * Well-formedness validation has already ensured that we have a properly formatted TSV file.
   */
  private Tap<?, ?, ?> getInputTap(PlatformStrategy platformStrategy, FileSchema fileSchema) {
    return platformStrategy.getSourceTap2(fileSchema);
  }

  /**
   * 
   */
  private Tap<?, ?, ?> getOutputTap(String releaseName, String projectKey) {
    return dccFileSystem2.getNormalizationDataOutputTap(releaseName, projectKey);
  }

  /**
   * TODO: externalise
   */
  private void internalStatisticsReport(ConnectedCascade connectedCascade) {
    String report = NormalizationReporter.createInternalReportContent(connectedCascade);
    log.info("Internal report: {}", report); // Should be small enough
    dccFileSystem2.writeNormalizationReport(
        connectedCascade.getReleaseName(),
        connectedCascade.getProjectKey(),
        report);
  }

  /**
   * TODO: externalise
   */
  private void externalStatisticsReport(
      String fileName,
      ConnectedCascade connectedCascade,
      ValidationContext validationContext) {

    for (val entry : NormalizationReport
        .builder()
        .projectKey(
            validationContext.getProjectKey())
        .counters(
            NormalizationCounter.report(connectedCascade))
        .build()
        .getExternalReportCounters()
        .entrySet()) {

      val key = entry.getKey();
      val value = entry.getValue();
      log.info("External report for '{}': '{}' -> '{}'", new Object[] { fileName, key, value });
      validationContext.reportSummary(
          fileName, key, value);
    }
  }

  /**
   * 
   */
  private Optional<String> grabSubmissionFile(SubmissionFileType type, ValidationContext validationContext) {
    return validationContext
        .getSubmissionDirectory()
        .getFile(
            validationContext
                .getDictionary()
                .getFilePattern(type));
  }

  @Override
  public String getName() {
    return COMPONENT_NAME;
  }

  @Value
  private static final class Pipes {

    private final Pipe startPipe;
    private final Pipe endPipe;
  }

  @Value
  static final class ConnectedCascade {

    private final String releaseName;
    private final String projectKey;
    private final Flow<?> flow;
    private final Cascade cascade;

    public void completeCascade() {
      cascade.complete();
    }

    public long getCounterValue(NormalizationCounter counter) {
      return flow.getFlowStats().getCounterValue(counter);
    }
  }
}
