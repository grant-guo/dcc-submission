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
package org.icgc.dcc.submission.core;

import static java.lang.String.format;
import static javax.mail.Message.RecipientType.TO;
import static org.icgc.dcc.submission.release.model.SubmissionState.ERROR;
import static org.icgc.dcc.submission.release.model.SubmissionState.INVALID;
import static org.icgc.dcc.submission.release.model.SubmissionState.VALID;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.submission.core.model.Feedback;
import org.icgc.dcc.submission.release.model.SubmissionState;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.typesafe.config.Config;

@Slf4j
@RequiredArgsConstructor(onConstructor = @_(@Inject))
public class MailService {

  /**
   * Server property names.
   */
  public static final String MAIL_ENABLED = "mail.enabled";
  public static final String MAIL_SMTP_HOST = "mail.smtp.host";
  public static final String MAIL_SMTP_PORT = "mail.smtp.port";
  public static final String MAIL_SMTP_SERVER = "smtp.oicr.on.ca";

  /**
   * Subject property name.
   */
  public static final String MAIL_VALIDATION_SUBJECT = "mail.subject";

  /**
   * From property names.
   */
  public static final String MAIL_NORMAL_FROM = "mail.from.email";
  public static final String MAIL_PROBLEM_FROM = "mail.from.email";

  /**
   * Recipient property names.
   */
  public static final String MAIL_ADMIN_RECIPIENT = "mail.admin.email";
  public static final String MAIL_MANUAL_SUPPORT_RECIPIENT = "mail.manual_support.email";
  public static final String MAIL_AUTOMATIC_SUPPORT_RECIPIENT = "mail.automatic_support.email";
  public static final String MAIL_NOTIFICATION_RECIPIENT = "mail.notification.email";

  /**
   * Body property names.
   */
  public static final String MAIL_SIGNOFF_BODY = "mail.signoff_body";
  public static final String MAIL_ERROR_BODY = "mail.error_body";
  public static final String MAIL_VALID_BODY = "mail.valid_body";
  public static final String MAIL_INVALID_BODY = "mail.invalid_body";

  /**
   * Application config.
   */
  @NonNull
  private final Config config;

  public void sendAdminProblem(String message) {
    send(
        from(MAIL_PROBLEM_FROM),
        to(MAIL_ADMIN_RECIPIENT),
        message,
        message);
  }

  public void sendSupportProblem(String subject, String message) {
    send(
        from(MAIL_PROBLEM_FROM),
        to(MAIL_AUTOMATIC_SUPPORT_RECIPIENT),
        subject,
        message);
  }

  public void sendValidationStarted(String releaseName, String projectKey, List<String> emails) {
    sendNotification(format("Validation started for release '%s' project '%s' (on behalf of '%s')",
        releaseName, projectKey, emails));
  }

  public void sendValidationFinished(String releaseName, String projectKey, SubmissionState state,
      List<String> emails) {
    if (!isEnabled()) {
      log.info("Mail not enabled. Skipping...");
      return;
    }

    try {
      // Always send to admin
      val addresses = addresses(emails);
      addresses.add(address(get(MAIL_ADMIN_RECIPIENT)));

      val message = message();
      message.setFrom(address(get(state == ERROR ? MAIL_PROBLEM_FROM : MAIL_NORMAL_FROM)));
      message.addRecipients(TO, recipients(addresses));
      message.setSubject(formatSubject(template(MAIL_VALIDATION_SUBJECT, projectKey, state)));
      message.setText(
          state == ERROR ? template(MAIL_ERROR_BODY, projectKey, state) : //
          state == VALID ? template(MAIL_VALID_BODY, projectKey, state, projectKey, projectKey) : //
          state == INVALID ? template(MAIL_INVALID_BODY, projectKey, state, projectKey, projectKey) : //
          format("Unexpected validation state '%s' prevented loading email text. Please see server log.", state));

      Transport.send(message);
      log.info("Emails for '{}' sent to '{}'", projectKey, addresses);
    } catch (Exception e) {
      log.error("An error occured while emailing: ", e);
    }
  }

  public void sendSignoff(String user, List<String> projectKeys, String nextReleaseName) {
    send(
        from(MAIL_NORMAL_FROM),
        to(MAIL_AUTOMATIC_SUPPORT_RECIPIENT),
        format("Signed off Projects: %s", projectKeys),
        template(MAIL_SIGNOFF_BODY, user, projectKeys, nextReleaseName));
  }

  public void sendFeedback(Feedback feedback) {
    send(
        feedback.getEmail(),
        to(MAIL_MANUAL_SUPPORT_RECIPIENT),
        feedback.getSubject(),
        feedback.getMessage());
  }

  private void sendNotification(String subject) {
    send(
        from(MAIL_NORMAL_FROM),
        to(MAIL_NOTIFICATION_RECIPIENT),
        subject,
        subject);
  }

  private void send(String from, String recipient, String subject, String text) {
    if (!isEnabled()) {
      log.info("Mail not enabled. Skipping...");
      return;
    }

    try {
      val message = message();
      message.setFrom(address(from));
      message.addRecipient(TO, address(recipient));
      message.setSubject(formatSubject(subject));
      message.setText(text);

      Transport.send(message);
      log.info("Emails for '{}' sent to '{}'", subject, recipient);
    } catch (Exception e) {
      log.error("An error occured while emailing: ", e);
    }
  }

  private Message message() {
    val props = new Properties();
    props.put(MAIL_SMTP_HOST, get(MAIL_SMTP_HOST));
    props.put(MAIL_SMTP_PORT, get(MAIL_SMTP_PORT, "25"));

    return new MimeMessage(Session.getDefaultInstance(props, null));
  }

  private String formatSubject(String text) {
    return format("[%s]: %s", getHostName(), text);
  }

  private String template(String templateName, Object... arguments) {
    return format(get(templateName), arguments);
  }

  private String from(String name) {
    return get(name);
  }

  private String to(String name) {
    return get(name);
  }

  private String get(String name) {
    return config.getString(name);
  }

  private String get(String name, String defaultValue) {
    return config.hasPath(name) ? config.getString(name) : defaultValue;
  }

  private boolean isEnabled() {
    return config.hasPath(MAIL_ENABLED) ? config.getBoolean(MAIL_ENABLED) : true;
  }

  private static Set<Address> addresses(List<String> emails) {
    val addresses = Sets.<Address> newLinkedHashSet();
    for (val email : emails) {
      try {
        val address = address(email);
        addresses.add(address);
      } catch (UnsupportedEncodingException e) {
        log.error("Illegal Address: " + e + " in " + emails);
      }
    }

    return addresses;
  }

  private static InternetAddress address(String email) throws UnsupportedEncodingException {
    return new InternetAddress(email, email);
  }

  private static Address[] recipients(Set<Address> addresses) {
    return addresses.toArray(new Address[addresses.size()]);
  }

  private static String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      // Best effort
      return "unknown host";
    }
  }

}
