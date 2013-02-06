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
package org.icgc.dcc.validation;

import static org.mockito.Mockito.mock;

import org.icgc.dcc.core.AbstractDccModule;
import org.icgc.dcc.dictionary.DictionaryService;
import org.icgc.dcc.validation.restriction.CodeListRestriction;
import org.icgc.dcc.validation.restriction.DiscreteValuesRestriction;
import org.icgc.dcc.validation.restriction.RangeFieldRestriction;
import org.icgc.dcc.validation.restriction.RequiredRestriction;

import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class ValidationTestModule extends AbstractDccModule {

  private Multibinder<RestrictionType> types;

  @Override
  protected void configure() {
    bind(DictionaryService.class).toInstance(mock(DictionaryService.class));

    bind(Planner.class).to(DefaultPlanner.class);
    types = Multibinder.newSetBinder(binder(), RestrictionType.class);

    bindRestriction(DiscreteValuesRestriction.Type.class);
    bindRestriction(RangeFieldRestriction.Type.class);
    bindRestriction(RequiredRestriction.Type.class);
    bindRestriction(CodeListRestriction.Type.class);
  }

  private void bindRestriction(Class<? extends RestrictionType> type) {
    types.addBinding().to(type).in(Singleton.class);
  }
}
