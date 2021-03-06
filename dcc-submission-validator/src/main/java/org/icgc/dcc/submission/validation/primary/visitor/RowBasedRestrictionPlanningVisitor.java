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
package org.icgc.dcc.submission.validation.primary.visitor;

import java.util.Set;

import org.icgc.dcc.submission.dictionary.model.Restriction;
import org.icgc.dcc.submission.validation.primary.core.FlowType;
import org.icgc.dcc.submission.validation.primary.core.PlanElement;
import org.icgc.dcc.submission.validation.primary.core.RestrictionType;
import org.icgc.dcc.submission.validation.primary.core.RowBasedPlanElement;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;

import lombok.NonNull;

public class RowBasedRestrictionPlanningVisitor extends RowBasedFlowPlanningVisitor {

  private final Set<RestrictionType> restrictionTypes;

  public RowBasedRestrictionPlanningVisitor(@NonNull String projectKey, Set<RestrictionType> restrictionTypes) {
    super(projectKey);
    this.restrictionTypes = Sets.filter(restrictionTypes, new Predicate<RestrictionType>() {

      @Override
      public boolean apply(RestrictionType input) {
        return input.flowType() == FlowType.ROW_BASED;
      }

    });
  }

  @Override
  public void visit(Restriction restriction) {
    for (RestrictionType type : restrictionTypes) {
      if (type.builds(restriction.getType().getId())) {
        PlanElement element = type.build(getProjectKey(), getCurrentField(), restriction);
        collectPlanElement((RowBasedPlanElement) element);
      }
    }
  }

}
