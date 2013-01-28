/**
 * Copyright 2012(c) The Ontario Institute for Cancer Research. All rights reserved.
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
package org.icgc.dcc.genes.cli;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import com.mongodb.MongoURI;

public class MongoURIValidator implements IParameterValidator {

  @Override
  public void validate(String name, String uri) throws ParameterException {
    try {
      MongoURI mongoUri = new MongoURI(uri);

      String database = mongoUri.getDatabase();
      if(isNullOrEmpty(database)) {
        throw new ParameterException("Invalid option: " + name + ": uri must contain a database name");
      }

      String collection = mongoUri.getCollection();
      if(isNullOrEmpty(collection)) {
        throw new ParameterException("Invalid option: " + name + ": uri must contain a collection name");
      }
    } catch(IllegalArgumentException e) {
      throw new ParameterException("Invalid option: " + name + ": " + e.getMessage()
          + ". See http://docs.mongodb.org/manual/reference/connection-string/ for more information.");
    }
  }
}