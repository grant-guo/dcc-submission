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
package org.icgc.dcc.submission.core.model;

import static org.icgc.dcc.submission.core.util.NameValidator.PROJECT_ID_PATTERN;

import java.util.List;

import javax.validation.constraints.Pattern;

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;

import org.bson.types.ObjectId;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.hibernate.validator.constraints.NotBlank;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;
import com.google.code.morphia.annotations.Indexed;
import com.google.common.collect.Lists;

@Entity(noClassnameStored = true)
@Value
@Accessors(fluent = true)
public class Project {

  @Id
  @JsonIgnore
  @NonFinal
  ObjectId id;

  @NotBlank
  @Pattern(regexp = PROJECT_ID_PATTERN)
  @Indexed(unique = true)
  String key;

  @NotBlank
  String name;

  String alias;

  @JsonIgnore
  List<String> users = Lists.newArrayList();

  @JsonIgnore
  List<String> groups = Lists.newArrayList();

  public Project(String key, String name, String alias) {
    this.key = key;
    this.name = name;
    this.alias = alias;
  }

  public Project(String key, String name) {
    this(key, name, key);
  }

  public boolean hasUser(String name) {
    return users.contains(name);
  }
}
