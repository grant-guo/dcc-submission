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
package org.icgc.dcc.submission.validation.pcawg.util;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.icgc.dcc.submission.validation.core.ClinicalFields.getSampleStudy;

import java.util.Map;

import lombok.NonNull;
import lombok.val;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PCAWGFields {

  /**
   * Constants. See {@code sample.0.study.v1}
   * 
   * <pre>
   * https://submissions.dcc.icgc.org/dictionary.html#?vFrom=0.11c&vTo=0.11c&viewMode=codelist&dataType=sample&q=sample.0.study.v1
   * </pre>
   */
  private static final String PCAWG_STUDY_CODE = "1";
  private static final String PCAWG_STUDY_VALUE = "PCAWG";

  public static boolean isMarkedPCAWGSample(@NonNull Map<String, String> sample) {
    val study = getSampleStudy(sample);

    return isMarkedPCAWGStudy(study);
  }

  private static boolean isMarkedPCAWGStudy(@NonNull String study) {
    if (isNullOrEmpty(study)) {
      return false;
    } else if (study.equals(PCAWG_STUDY_VALUE)) {
      return true;
    } else if (study.equals(PCAWG_STUDY_CODE)) {
      return true;
    } else {
      return false;
    }
  }

}
