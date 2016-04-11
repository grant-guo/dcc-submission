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
package org.icgc.dcc.submission.http.jersey;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainerProvider;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ResourceConfig;
import org.icgc.dcc.submission.http.HttpHandlerProvider;

import com.google.inject.Inject;
import com.typesafe.config.Config;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * A {@link HttpHandlerProvider} that will mount {@code Jersey} on a particular path. The path is configured through the
 * {@code http.ws.path} parameter.
 */
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class JerseyHandler implements HttpHandlerProvider {

  @NonNull
  private final Config config;
  @NonNull
  private final ResourceConfig resourceConfig;

  @Override
  public String path() {
    return config.getString("http.ws.path");
  }

  @Override
  public HttpHandler get() {
    val provider = new GrizzlyHttpContainerProvider();
    val handler = new ApplicationHandler(resourceConfig);
    val container = provider.createContainer(HttpHandler.class, handler);

    return container;
  }

}
