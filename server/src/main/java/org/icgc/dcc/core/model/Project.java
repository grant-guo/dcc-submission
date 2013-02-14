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
package org.icgc.dcc.core.model;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.Pattern;

import org.hibernate.validator.constraints.NotBlank;
import org.icgc.dcc.web.validator.NameValidator;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Indexed;
import com.google.common.collect.Lists;

@Entity
public class Project extends BaseEntity implements HasName {

  @NotBlank
  @Pattern(regexp = NameValidator.NAME_PATTERN)
  @Indexed(unique = true)
  protected String key;

  @NotBlank
  protected String name;

  protected List<String> users = Lists.newArrayList();

  protected List<String> groups = Lists.newArrayList();

  public Project() {
    super();
  }

  public Project(String name) {
    super();
    this.setName(name);
  }

  public Project(String name, String key) {
    super();
    this.setName(name);
    this.setKey(key);
  }

  @Override
  public String getName() {
    return name == null ? key : name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public List<String> getUsers() {
    return users;
  }

  public void setUsers(List<String> users) {
    this.users = users;
  }

  public List<String> getGroups() {
    return groups == null ? new ArrayList<String>() : groups;
  }

  public void setGroups(List<String> groups) {
    this.groups = groups;
  }

  public boolean hasUser(String name) {
    return this.users != null && this.users.contains(name);
  }

  @Override
  public String toString() {
    return "Project [key=" + key + ", name=" + name + ", users=" + users + ", groups=" + groups + "]";
  }

}
