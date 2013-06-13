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
package org.icgc.dcc.sftp;

import static com.google.common.base.Joiner.on;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.util.concurrent.Service.State.TERMINATED;
import static java.lang.String.valueOf;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.mina.core.session.IoSession;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.AbstractFactoryManager;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.session.AbstractSession;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.FileSystemFactory;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.icgc.dcc.core.ProjectService;
import org.icgc.dcc.core.model.Status;
import org.icgc.dcc.core.model.UserSession;
import org.icgc.dcc.filesystem.DccFileSystem;
import org.icgc.dcc.release.ReleaseService;
import org.icgc.dcc.security.UsernamePasswordAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AbstractService;
import com.google.inject.Inject;
import com.typesafe.config.Config;

/**
 * Service abstraction to the SFTP sub-system.
 */
public class SftpServerService extends AbstractService {

  private static final String SFTP_CONFIG_SECTION = "sftp";

  private static final Logger log = LoggerFactory.getLogger(SftpServerService.class);

  private final SshServer sshd;

  @Inject
  public SftpServerService(Config config, final UsernamePasswordAuthenticator passwordAuthenticator,
      final DccFileSystem fs, final ProjectService projectService, final ReleaseService releaseService) {
    checkArgument(passwordAuthenticator != null);

    sshd = SshServer.setUpDefaultServer();
    sshd.setPort(config.getInt(getSftpConfigPath("port")));
    sshd.setKeyPairProvider(new PEMGeneratorHostKeyProvider(config.getString(getSftpConfigPath("path")), "RSA", 2048));
    setSshdProperties(config);

    sshd.setPasswordAuthenticator(new PasswordAuthenticator() {

      @Override
      public boolean authenticate(String username, String password, ServerSession session) {
        boolean authenticated = passwordAuthenticator.authenticate(username, password.toCharArray(), null) != null;
        if (authenticated) {
          sendWelcomeBanner(session);
        }

        return authenticated;
      }

      private void sendWelcomeBanner(ServerSession session) {
        try {
          Buffer buffer = session.createBuffer(SshConstants.Message.SSH_MSG_USERAUTH_BANNER, 0);
          buffer.putString("Welcome to the ICGC DCC Submission SFTP Server!\n\n");
          buffer.putString("\n");
          session.writePacket(buffer);
        } catch (IOException e) {
          log.warn("Error sending SFTP connection welcome banner: ", e);
        }
      }

    });

    sshd.setFileSystemFactory(new FileSystemFactory() {

      @Override
      public FileSystemView createFileSystemView(Session session) throws IOException {
        return new HdfsFileSystemView(fs, projectService, releaseService, passwordAuthenticator);
      }
    });

    sshd.setSubsystemFactories(ImmutableList.<NamedFactory<Command>> of(new SftpSubsystem.Factory()));
  }

  public void disconnectActiveSessions(String message) {
    List<AbstractSession> activeSessions = sshd.getActiveSessions();

    for (AbstractSession activeSession : activeSessions) {
      log.info("Sending disconnect message '{}' to {}", message, activeSession.getUsername());
      try {
        activeSession.disconnect(0, message);
      } catch (IOException e) {
        log.error("Exception sending disconnect message: {}", e);
      }
    }
  }

  public Status getActiveSessions() {
    Status status = new Status(state());
    if (state() == TERMINATED) {
      return status;
    }

    List<AbstractSession> activeSessions = sshd.getActiveSessions();
    for (AbstractSession activeSession : activeSessions) {

      // Shorthands
      IoSession ioSession = activeSession.getIoSession();
      long creationTime = ioSession.getCreationTime();
      long lastWriteTime = ioSession.getLastWriteTime();
      String username = activeSession.getUsername();

      Map<String, String> ioSessionMap = getIoSessionMap(ioSession);
      log.info(
          getLogMessage(username),
          new Object[] { username, new Date(creationTime), new Date(lastWriteTime), ioSessionMap });
      status.addUserSession(new UserSession(username, creationTime, lastWriteTime, ioSessionMap));
    }

    return status;
  }

  @Override
  protected void doStart() {
    try {
      log.info("Starting DCC SSH Server on port {}", sshd.getPort());
      sshd.start();
      notifyStarted();
    } catch (IOException e) {
      log.error("Failed to start SFTP server on {}:{} : {}",
          new Object[] { sshd.getHost(), sshd.getPort(), e.getMessage() });
      notifyFailed(e);
    }
  }

  @Override
  protected void doStop() {
    try {
      sshd.stop(true);
      notifyStopped();
    } catch (InterruptedException e) {
      log.error("Failed to stop SFTP server on {}:{} : {}",
          new Object[] { sshd.getHost(), sshd.getPort(), e.getMessage() });
      notifyFailed(e);
    }
  }

  private void setSshdProperties(Config config) {
    String nioWorkersPath = getSftpConfigPath(AbstractFactoryManager.NIO_WORKERS);
    if (config.hasPath(nioWorkersPath)) {
      Integer nioWorkers = config.getInt(nioWorkersPath);
      log.info("Setting '{}' to '{}'", AbstractFactoryManager.NIO_WORKERS, nioWorkers);
      sshd.setProperties(new ImmutableMap.Builder<String, String>()
          .put(AbstractFactoryManager.NIO_WORKERS, valueOf(nioWorkers))
          .build());
    } else {
      log.info("Using default value for '{}': '{}'",
          AbstractFactoryManager.NIO_WORKERS, FactoryManager.DEFAULT_NIO_WORKERS);
    }

  }

  private String getLogMessage(String username) {
    String intro = username == null ?
        "Authentication pending ('{}' username) " :
        "User with username '{}' has an active ";
    return intro + "SFTP session created on '{}', last written to '{}'; full ioSession is: {}";
  }

  private String getSftpConfigPath(String param) {
    return on(".").join(SFTP_CONFIG_SECTION, param);
  }

  /**
   * Returns some of the useful values for an {@link IoSession}.
   */
  private Map<String, String> getIoSessionMap(IoSession ioSession) {
    Map<String, String> map = newLinkedHashMap();
    map.put("bothIdleCount", valueOf(ioSession.getBothIdleCount()));
    map.put("creationTime", valueOf(ioSession.getCreationTime()));
    map.put("id", valueOf(ioSession.getId()));
    map.put("lastBothIdleTime", valueOf(ioSession.getLastBothIdleTime()));
    map.put("lastIoTime", valueOf(ioSession.getLastIoTime()));
    map.put("lastReaderIdleTime", valueOf(ioSession.getLastReaderIdleTime()));
    map.put("lastReadTime", valueOf(ioSession.getLastReadTime()));
    map.put("lastWriterIdleTime", valueOf(ioSession.getLastWriterIdleTime()));
    map.put("lastWriteTime", valueOf(ioSession.getLastWriteTime()));
    map.put("readBytes", valueOf(ioSession.getReadBytes()));
    map.put("readBytesThroughput", valueOf(ioSession.getReadBytesThroughput()));
    map.put("scheduledWriteBytes", valueOf(ioSession.getScheduledWriteBytes()));
    map.put("scheduledWriteMessages", valueOf(ioSession.getScheduledWriteMessages()));
    map.put("writerIdleCount", valueOf(ioSession.getWriterIdleCount()));
    map.put("writtenBytes", valueOf(ioSession.getWrittenBytes()));
    map.put("writtenBytesThroughput", valueOf(ioSession.getWrittenBytesThroughput()));
    map.put("writtenMessages", valueOf(ioSession.getWrittenMessages()));
    map.put("writtenMessagesThroughput", valueOf(ioSession.getWrittenMessagesThroughput()));
    return map;
  }
}
