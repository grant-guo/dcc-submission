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
package org.icgc.dcc.submission.validation.report;

import java.util.ArrayList;
import java.util.List;

import org.icgc.dcc.submission.dictionary.model.Field;
import org.icgc.dcc.submission.dictionary.model.FileSchema;
import org.icgc.dcc.submission.dictionary.model.SummaryType;
import org.icgc.dcc.submission.validation.FlowType;
import org.icgc.dcc.submission.validation.cascading.CompletenessBy;
import org.icgc.dcc.submission.validation.cascading.MinMaxBy;
import org.icgc.dcc.submission.validation.cascading.ValidationFields;

import cascading.flow.FlowProcess;
import cascading.operation.BaseOperation;
import cascading.operation.Function;
import cascading.operation.FunctionCall;
import cascading.operation.Insert;
import cascading.pipe.Each;
import cascading.pipe.Pipe;
import cascading.pipe.assembly.AggregateBy;
import cascading.pipe.assembly.Discard;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public abstract class SummaryPlanElement extends BaseStatsReportingPlanElement {

  protected static String fieldName(Field field, String summaryName) {
    return fieldName(field.getName(), summaryName);
  }

  protected static String fieldName(String field, String summaryName) {
    return String.format("%s#%s", field, summaryName);
  }

  protected SummaryPlanElement(FileSchema fileSchema, SummaryType summaryType, List<Field> fields, FlowType flowType) {
    super(fileSchema, fields, summaryType, flowType);
  }

  @Override
  public Pipe report(Pipe pipe) {
    pipe = keepStructurallyValidTuples(pipe);

    ArrayList<AggregateBy> summaries = new ArrayList<AggregateBy>();
    for(Field field : fields) {
      Iterables.addAll(summaries, collectAggregateBys(field));
    }

    // This is highly inefficient. This adds a constant to every row so we can group on it so that the AggregateBy
    // instances see all values.
    // Alternatively, we could set a random number to each row and group on this, each group would then produce
    // intermediate result (sum, count for average) and another pipe could then process this smaller set as done here.
    Fields constantField = new Fields("__constant__");
    pipe = new Each(pipe, new Insert(constantField, "1"), Fields.ALL);
    pipe = new AggregateBy(pipe, constantField, Iterables.toArray(summaries, AggregateBy.class)); // only one group
                                                                                                  // emerges from
                                                                                                  // grouping on
                                                                                                  // "__constant__"
    pipe = new Discard(pipe, constantField);
    pipe = new Each(pipe, new SummaryFunction(fields, summaryFields()), REPORT_FIELDS);
    return pipe;
  }

  protected AggregateBy makeDeviation(Field field) {
    return new DeviationBy(new Fields(field.getName()), field.getName(), new Fields(fieldName(field, DeviationBy.AVG),
        fieldName(field, DeviationBy.STDDEV)));
  }

  protected AggregateBy makeMinMax(Field field) {
    return new MinMaxBy(new Fields(field.getName()), new Fields(fieldName(field, MinMaxBy.MIN), fieldName(field,
        MinMaxBy.MAX)));
  }

  protected AggregateBy makeCompleteness(Field field) {
    return new CompletenessBy(new Fields(field.getName(), ValidationFields.STATE_FIELD_NAME), new Fields(fieldName(
        field, CompletenessBy.NULLS), fieldName(field, CompletenessBy.MISSING), fieldName(field,
        CompletenessBy.POPULATED)));
  }

  /**
   * Returns a iterable of {@code AggregateBy} instances to be applied to every tuples in the one group that results in
   * grouping on "__constant__"
   * <p>
   * See TODO about that __constant__ field.
   */
  protected abstract Iterable<AggregateBy> collectAggregateBys(Field field);

  /**
   * Returns a list of aggregate types such as min, max, average and stddev (possibly empty).
   */
  protected abstract Iterable<String> summaryFields();

  protected Pipe average(String field, Pipe pipe) {
    return pipe;
  }

  /**
   * Input contains only 1 tuple like:<br/>
   * <br/>
   * <table>
   * <tr>
   * <td>"f1#nulls"</td>
   * <td>"f1#missing"</td>
   * <td>...</td>
   * <td>"f2#nulls"</td>
   * <td>"f2#missing"</td>
   * <td>...</td>
   * <tr>
   * <td>4</td>
   * <td>5</td>
   * <td>...</td>
   * <td>0</td>
   * <td>3</td>
   * <td>...</td>
   * </tr>
   * </table>
   * <br/>
   * <br/>
   * And output one tuple per data-field, each tuple having only one "report" {@code Fields} like:<br/>
   * <br/>
   * <table>
   * <tr>
   * <td>"report"</td>
   * </tr>
   * <tr>
   * <td>{ "field": "f1", "nulls": 4, "missing": 5, ...}</td>
   * </tr>
   * <tr>
   * <td>{ "field": "f2", "nulls": 0, "missing": 3, ...}</td>
   * </tr>
   * </table>
   */
  @SuppressWarnings("rawtypes")
  public static class SummaryFunction extends BaseOperation implements Function {

    private final List<String> fields;

    private final List<String> summaryFields;

    public SummaryFunction(List<Field> fields, Iterable<String> summaryFields) {
      super(REPORT_FIELDS);
      this.fields = Lists.newArrayListWithCapacity(fields.size());
      for(Field f : fields) {
        this.fields.add(f.getName());
      }
      this.summaryFields = ImmutableList.copyOf(summaryFields);
    }

    @Override
    public void operate(FlowProcess flowProcess, FunctionCall functionCall) {
      TupleEntry te = functionCall.getArguments();
      for(String field : fields) {
        FieldSummary fs = new FieldSummary();
        fs.field = field;
        fs.nulls = te.getInteger(fieldName(field, CompletenessBy.NULLS));
        fs.missing = te.getInteger(fieldName(field, CompletenessBy.MISSING));
        fs.populated = te.getInteger(fieldName(field, CompletenessBy.POPULATED));
        for(String summaryField : summaryFields) {
          fs.summary.put(summaryField, te.getObject(fieldName(field, summaryField)));
        }
        functionCall.getOutputCollector().add(new Tuple(fs));
      }
    }
  }

  static class CompletenessPlanElement extends SummaryPlanElement {

    protected CompletenessPlanElement(FileSchema fileSchema, List<Field> fields, FlowType flowType) {
      super(fileSchema, null, fields, flowType);
    }

    @Override
    protected Iterable<AggregateBy> collectAggregateBys(Field field) {
      return ImmutableList.of(makeCompleteness(field));
    }

    @Override
    protected Iterable<String> summaryFields() {
      return ImmutableList.of();
    }
  }

  static class MinMaxPlanElement extends SummaryPlanElement {

    protected MinMaxPlanElement(FileSchema fileSchema, List<Field> fields, FlowType flowType) {
      super(fileSchema, SummaryType.MIN_MAX, fields, flowType);
    }

    @Override
    protected Iterable<AggregateBy> collectAggregateBys(Field field) {
      return ImmutableList.of(makeMinMax(field), makeCompleteness(field));
    }

    @Override
    protected Iterable<String> summaryFields() {
      return ImmutableList.of(MinMaxBy.MIN, MinMaxBy.MAX);
    }
  }

  static class AveragePlanElement extends SummaryPlanElement {

    protected AveragePlanElement(FileSchema fileSchema, List<Field> fields, FlowType flowType) {
      super(fileSchema, SummaryType.AVERAGE, fields, flowType);
    }

    @Override
    protected Iterable<AggregateBy> collectAggregateBys(Field field) {
      return ImmutableList.of(makeDeviation(field), makeMinMax(field), makeCompleteness(field));
    }

    @Override
    protected Iterable<String> summaryFields() {
      return ImmutableList.of(MinMaxBy.MIN, MinMaxBy.MAX, DeviationBy.AVG, DeviationBy.STDDEV);
    }
  }
}