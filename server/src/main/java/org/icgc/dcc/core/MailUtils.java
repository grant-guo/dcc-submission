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
package org.icgc.dcc.core;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * TODO (DCC-686)
 */
public class MailUtils { // TODO: DCC-686 - make it a service (inject Config)

  private static final Logger log = LoggerFactory.getLogger(MailUtils.class);

  public static final String SMTP_HOST = "mail.smtp.host";

  public static final String SMTP_SERVER = "smtp.oicr.on.ca";

  public static final String SUBJECT = "mail.subject";

  public static final String FROM = "mail.from.email";

  public static final String ADMIN_RECIPIENT = "mail.admin.email";

  public static final String SUPPORT_RECIPIENT = "mail.support.email";

  public static final String SIGNOFF_BODY = "mail.signoff_body";

  public static final String ERROR_BODY = "mail.error_body";

  public static final String VALID_BODY = "mail.valid_body";

  public static final String INVALID_BODY = "mail.invalid_body";

  public static void sendEmail(Config config, String subject, String text) {
    try {
      Properties props = new Properties();
      props.put(SMTP_HOST, config.getString(SMTP_HOST));
      Session session = Session.getDefaultInstance(props, null);
      Message msg = new MimeMessage(session);
      msg.setFrom(new InternetAddress(config.getString(FROM), config.getString(FROM)));
      msg.addRecipient(Message.RecipientType.TO, new InternetAddress(config.getString(SUPPORT_RECIPIENT)));
      msg.setSubject(subject);
      msg.setText(text);
      Transport.send(msg);
    } catch(AddressException e) {
      log.error("an error occured while emailing: ", e);
    } catch(MessagingException e) {
      log.error("an error occured while emailing: ", e);
    } catch(UnsupportedEncodingException e) {
      log.error("an error occured while emailing: ", e);
    }
  }
}
