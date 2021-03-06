/*
 * Copyright (c) 2016 The Ontario Institute for Cancer Research. All rights reserved.                             
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
package org.icgc.dcc.submission.validation.pcawg.core;

import static com.google.common.base.Suppliers.memoizeWithExpiration;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.icgc.dcc.common.core.json.Jackson.DEFAULT;

import java.net.URL;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PCAWGDictionary {

  /**
   * Constants.
   */
  public static final URL DEFAULT_PCAWG_DICTIONARY_URL = Resources.getResource("pcawg-dictionary.json");

  /**
   * Configuration
   */
  @NonNull
  private final URL url;

  /**
   * State.
   */
  private final Supplier<JsonNode> supplier = memoizeWithExpiration(this::readDictionary, 10, MINUTES);

  public PCAWGDictionary() {
    this(DEFAULT_PCAWG_DICTIONARY_URL);
  }

  public Set<String> getExcludedProjectKeys() {
    val excludedProjectKeys = readField("excludedProjectKeys");
    if (excludedProjectKeys.isMissingNode()) {
      return ImmutableSet.of();
    }

    return DEFAULT.convertValue(excludedProjectKeys, new TypeReference<Set<String>>() {});
  }

  public Set<String> getExcludedDonorIds(@NonNull String projectKey) {
    return readFieldMap(projectKey, "excludedDonorIds");
  }

  public Set<String> getExcludedSpecimenIds(@NonNull String projectKey) {
    return readFieldMap(projectKey, "excludedSpecimenIds");
  }

  public Set<String> getExcludedSampleIds(@NonNull String projectKey) {
    return readFieldMap(projectKey, "excludedSampleIds");
  }

  private JsonNode dictionary() {
    return supplier.get();
  }

  private Set<String> readFieldMap(String projectKey, final java.lang.String fieldName) {
    Map<String, Set<String>> map = readFieldProjectMap(fieldName);

    val values = map.get(projectKey);
    if (values == null) {
      return ImmutableSet.of();
    }

    return values;
  }

  private Map<String, Set<String>> readFieldProjectMap(String fieldName) {
    val values = readField(fieldName);
    if (values.isMissingNode()) {
      return ImmutableMap.of();
    }

    val type = new TypeReference<Map<String, Set<String>>>() {};
    return DEFAULT.convertValue(values, type);
  }

  private JsonNode readField(String fieldName) {
    return dictionary().path(fieldName);
  }

  @SneakyThrows
  private JsonNode readDictionary() {
    log.info("Refreshing dictionary...");
    return DEFAULT.readTree(url);
  }

}
