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
package org.icgc.dcc.submission.core;

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.hadoop.fs.FileSystem;
import org.icgc.dcc.hadoop.fs.DccFileSystem2;
import org.icgc.dcc.submission.dictionary.model.CodeList;
import org.icgc.dcc.submission.service.DictionaryService;
import org.icgc.dcc.submission.validation.ValidationExecutor;
import org.icgc.dcc.submission.validation.core.Validator;
import org.icgc.dcc.submission.validation.first.FirstPassValidator;
import org.icgc.dcc.submission.validation.key.KeyValidator;
import org.icgc.dcc.submission.validation.norm.NormalizationConfig;
import org.icgc.dcc.submission.validation.norm.NormalizationValidator;
import org.icgc.dcc.submission.validation.platform.PlatformStrategyFactory;
import org.icgc.dcc.submission.validation.platform.PlatformStrategyFactoryProvider;
import org.icgc.dcc.submission.validation.primary.PrimaryValidator;
import org.icgc.dcc.submission.validation.primary.core.RestrictionContext;
import org.icgc.dcc.submission.validation.primary.core.RestrictionType;
import org.icgc.dcc.submission.validation.primary.planner.Planner;
import org.icgc.dcc.submission.validation.primary.report.ByteOffsetToLineNumber;
import org.icgc.dcc.submission.validation.primary.restriction.CodeListRestriction;
import org.icgc.dcc.submission.validation.primary.restriction.DiscreteValuesRestriction;
import org.icgc.dcc.submission.validation.primary.restriction.RangeFieldRestriction;
import org.icgc.dcc.submission.validation.primary.restriction.RegexRestriction;
import org.icgc.dcc.submission.validation.primary.restriction.RequiredRestriction;
import org.icgc.dcc.submission.validation.primary.restriction.ScriptRestriction;
import org.icgc.dcc.submission.validation.semantic.ReferenceGenomeValidator;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.typesafe.config.Config;

/**
 * Module that wires together components of the validation subsystem.
 */
@Slf4j
@RequiredArgsConstructor
public class ValidationModule extends AbstractDccModule {

  /**
   * Config property names.
   */
  private static final String MAX_VALIDATING_CONFIG_PARAM = "validator.max_simultaneous";
  private static final String FASTA_FILE_PATH_CONFIG_PARAM = "reference.fasta";

  /**
   * Default value for maximum number of concurrent validations.
   */
  private static final int DEFAULT_MAX_VALIDATING = 1;

  @NonNull
  private final Config config;

  @Override
  protected void configure() {
    bindService();
    bindValidators();
  }

  /**
   * Binds service level components.
   */
  private void bindService() {
    // Execution facility
    bind(ValidationExecutor.class).toProvider(new Provider<ValidationExecutor>() {

      @Inject
      private Config config;

      @Override
      public ValidationExecutor get() {
        return new ValidationExecutor(getMaxValidating());
      }

      private int getMaxValidating() {
        val path = MAX_VALIDATING_CONFIG_PARAM;
        return config.hasPath(path) ? config.getInt(path) : DEFAULT_MAX_VALIDATING;
      }

    }).in(Singleton.class);
  }

  private void bindValidators() {
    // Bind common components
    bindNewTemporaryFileSystemAbstraction(); // TODO: Shouldn't be bound here, see DCC-1876
    bind(PlatformStrategyFactory.class).toProvider(PlatformStrategyFactoryProvider.class).in(Singleton.class);

    // Set binder will preserve bind order as iteration order for injectees
    val validators = newSetBinder(binder(), Validator.class);

    // Bind validators and their execution ordering
    if (config.hasPath("validators")) {
      val values = config.getList("validators").unwrapped();
      log.info("Binding validators in the following order: {}", values);

      // Externally configured validators and validator ordering
      for (val value : values) {
        if (value.equals("fpv")) {
          bindFirstPassValidator(validators);
        } else if (value.equals("kv")) {
          bindKeyValidator(validators);
        } else if (value.equals("pv")) {
          bindKeyValidator(validators);
        } else if (value.equals("rgv")) {
          bindReferenceGenomeValidator(validators);
        } else if (value.equals("nv")) {
          bindNormalizationValidator(validators);
        } else {
          checkState(false, "Invalid validator specification '%s'", value);
        }
      }
    } else {
      // Default validators and validator ordering
      bindFirstPassValidator(validators);
      bindKeyValidator(validators);
      bindPrimaryValidator(validators);
      bindReferenceGenomeValidator(validators);
      bindNormalizationValidator(validators);
    }
  }

  private void bindFirstPassValidator(Multibinder<Validator> validators) {
    bindValidator(validators, FirstPassValidator.class);
  }

  private void bindKeyValidator(Multibinder<Validator> validators) {
    bindValidator(validators, KeyValidator.class);
  }

  private void bindPrimaryValidator(Multibinder<Validator> validators) {
    bindValidator(validators, PrimaryValidator.class);

    // Builder of plans
    bind(Planner.class).in(Singleton.class);

    // Bind static
    requestStaticInjection(ByteOffsetToLineNumber.class);

    // Primary restrictions
    bindRestrictionTypes();

    // Helper
    bind(RestrictionContext.class).toInstance(new RestrictionContext() {

      @Inject
      DictionaryService dictionaryService;

      @Override
      public Optional<CodeList> getCodeList(String codeListName) {
        return dictionaryService.getCodeList(codeListName);
      }

    });

  }

  private void bindReferenceGenomeValidator(Multibinder<Validator> validators) {
    bindValidator(validators, new Provider<ReferenceGenomeValidator>() {

      @Inject
      private Config config;

      @Override
      public ReferenceGenomeValidator get() {
        return new ReferenceGenomeValidator(getFastaFilePath());
      }

      private String getFastaFilePath() {
        val path = FASTA_FILE_PATH_CONFIG_PARAM;
        checkState(config.hasPath(path), "'%s' is should be present in the config", path);

        return config.getString(path);
      }

    });
  }

  private void bindNormalizationValidator(Multibinder<Validator> validators) {
    bindValidator(validators, new Provider<NormalizationValidator>() {

      @Inject
      private Config config;

      @Inject
      private DccFileSystem2 dccFileSystem2;

      @Override
      public NormalizationValidator get() {
        return NormalizationValidator.getDefaultInstance(dccFileSystem2, getNormalizationConfig());
      }

      private Config getNormalizationConfig() {
        return config.getConfig(NormalizationConfig.NORMALIZER_CONFIG_PARAM);
      }

    });
  }

  /**
   * Any restrictions added in here should also be added in {@link ValidationTestModule} for testing.
   */
  private void bindRestrictionTypes() {
    // Set binder will preserve bind order as iteration order for injectees
    val types = Multibinder.newSetBinder(binder(), RestrictionType.class);

    bindRestriction(types, DiscreteValuesRestriction.Type.class);
    bindRestriction(types, RangeFieldRestriction.Type.class);
    bindRestriction(types, RequiredRestriction.Type.class);
    bindRestriction(types, CodeListRestriction.Type.class);
    bindRestriction(types, RegexRestriction.Type.class);
    bindRestriction(types, ScriptRestriction.Type.class);
  }

  private static void bindRestriction(Multibinder<RestrictionType> types, Class<? extends RestrictionType> type) {
    types.addBinding().to(type).in(Singleton.class);
  }

  private static void bindValidator(Multibinder<Validator> validators, Class<? extends Validator> validator) {
    validators.addBinding().to(validator).in(Singleton.class);
  }

  private static void bindValidator(Multibinder<Validator> validators, Provider<? extends Validator> provider) {
    validators.addBinding().toProvider(provider).in(Singleton.class);
  }

  /**
   * Near clone of {@link LoaderModule} - maintain both at the same time until DCC-1876 is addressed.
   * <p>
   * TODO: This is temporary until DCC-1876 is addressed.
   * <p>
   * TODO: address hard-codings
   */
  private void bindNewTemporaryFileSystemAbstraction() {
    bind(DccFileSystem2.class).toProvider(new Provider<DccFileSystem2>() {

      @Inject
      private FileSystem fileSystem;

      @Inject
      private Config config;

      @Override
      public DccFileSystem2 get() {
        return new DccFileSystem2(fileSystem, getRootDir(), isHdfs());
      }

      private String getRootDir() {
        val path = "fs.root";
        checkState(config.hasPath(path), "'%s' should be present in the config", path);

        return config.getString(path);
      }

      private boolean isHdfs() {
        val path = "fs.url";
        checkState(config.hasPath(path), "'%s' should be present in the config", path);

        return config.getString(path).startsWith("hdfs");
      }

    });
  }

}