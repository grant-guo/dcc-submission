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

import java.util.List;

import lombok.NonNull;
import lombok.Value;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Value
@Slf4j
public class Validation {

  @NonNull
  private final ValidationContext context;
  @NonNull
  private final List<Validator> validators;

  public String getId() {
    return context.getProjectKey();
  }

  public void execute() throws InterruptedException {
    log.info("Executing validition '{}'...", getId());

    for (val validator : validators) {
      log.info("Executing {} validator for '{}'...", validator.getClass().getSimpleName(), getId());
      yield();
      validator.validate(context);
      yield();
    }
  }

  private void yield() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Task '" + getId() + "' was interrupted");
    }
  }

}
