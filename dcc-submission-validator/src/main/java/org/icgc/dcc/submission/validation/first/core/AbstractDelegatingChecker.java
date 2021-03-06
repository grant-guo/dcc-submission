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
package org.icgc.dcc.submission.validation.first.core;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.repeat;

import org.icgc.dcc.submission.dictionary.model.FileSchema;
import org.icgc.dcc.submission.validation.core.ValidationContext;

import lombok.NonNull;
import lombok.val;

public abstract class AbstractDelegatingChecker extends AbstractChecker {

  /**
   * Metadata.
   */
  @NonNull
  protected final String name;

  /**
   * Dependencies.
   */
  @NonNull
  protected final Checker delegate;

  public AbstractDelegatingChecker(Checker delegate, boolean failFast) {
    super((ValidationContext) delegate.getReportContext(), delegate.getFileSystem(), failFast);
    this.name = this.getClass().getSimpleName();
    this.delegate = delegate;
  }

  public AbstractDelegatingChecker(FileChecker delegate) {
    this(delegate, false);
  }

  @Override
  public boolean canContinue() {
    return (delegate.canContinue() && (checkErrorCount == 0 || !isFailFast()));
  }

  @Override
  public boolean isValid() {
    return (delegate.isValid() && !getReportContext().hasErrors());
  }

  protected FileSchema getFileSchema(String fileName) {
    val optional = getDictionary().getFileSchemaByFileName(fileName);
    checkState(optional.isPresent(), "At this stage, there should be a file schema matching '%s'", fileName);
    return optional.get();
  }

  protected String banner() {
    return repeat("-", 75);
  }

}
