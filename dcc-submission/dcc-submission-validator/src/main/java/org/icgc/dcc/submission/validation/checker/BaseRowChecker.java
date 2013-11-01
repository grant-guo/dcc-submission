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
package org.icgc.dcc.submission.validation.checker;

import java.util.List;

import org.icgc.dcc.submission.dictionary.model.Dictionary;
import org.icgc.dcc.submission.dictionary.model.FileSchema;
import org.icgc.dcc.submission.fs.DccFileSystem;
import org.icgc.dcc.submission.fs.SubmissionDirectory;
import org.icgc.dcc.submission.validation.checker.Util.CheckLevel;

import com.google.common.collect.ImmutableList;

public class BaseRowChecker extends BaseFileChecker implements RowChecker {

  public BaseRowChecker(DccFileSystem fs, Dictionary dict, SubmissionDirectory submissionDir, boolean failFast) {
    super(fs, dict, submissionDir, failFast);
  }

  public BaseRowChecker(DccFileSystem fs, Dictionary dict, SubmissionDirectory submissionDir) {
    this(fs, dict, submissionDir, false);
  }

  @Override
  public List<FirstPassValidationError> check(String filename) {
    return ImmutableList.of();
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public CheckLevel getCheckLevel() {
    return CheckLevel.ROW_LEVEL;
  }

  @Override
  public List<FirstPassValidationError> checkRow(FileSchema fileSchema, String row, long lineNumber) {
    return ImmutableList.of();
  }

}
