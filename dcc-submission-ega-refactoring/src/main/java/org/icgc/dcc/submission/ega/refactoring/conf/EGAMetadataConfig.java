package org.icgc.dcc.submission.ega.refactoring.conf;

import com.github.davidmoten.rx.jdbc.Database;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.icgc.dcc.submission.ega.refactoring.compress.UntarEGAFile;
import org.icgc.dcc.submission.ega.refactoring.compress.impl.UntarEGAFileImpl;
import org.icgc.dcc.submission.ega.refactoring.download.DownloadEGAFile;
import org.icgc.dcc.submission.ega.refactoring.download.impl.DownloadEGAFileImpl;
import org.icgc.dcc.submission.ega.refactoring.extractor.BadFormattedDataLogger;
import org.icgc.dcc.submission.ega.refactoring.extractor.DataExtractor;
import org.icgc.dcc.submission.ega.refactoring.extractor.impl.EGAPostgresqlBadFormattedDataLogger;
import org.icgc.dcc.submission.ega.refactoring.extractor.impl.EGASampleFileExtractor;
import org.icgc.dcc.submission.ega.refactoring.repo.EGAMetadataRepo;
import org.icgc.dcc.submission.ega.refactoring.repo.impl.EGAMetadataRepoPostgres;
import org.icgc.dcc.submission.ega.refactoring.service.EGAMetadataService;
import org.icgc.dcc.submission.ega.refactoring.service.impl.EGAMetadataServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.File;

/**
 * Copyright (c) 2017 The Ontario Institute for Cancer Research. All rights reserved.
 * <p>
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 * <p>
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

@Configuration
public class EGAMetadataConfig {

  @Value("${icgc.dcc.submission.ega.metadata.cron.data}")
  String cron_data;

  @Value("${icgc.dcc.submission.ega.metadata.cron.clean}")
  String cron_clean;

  @Data
  public static class EGAMetadataFTPConfig{
    String host;
    String user;
    String password;
    String path;
  }

  @Data
  public static class EGAMetadataPostgresqlConfig {
    String host;
    String database;
    String user;
    String password;
    String viewName;
  }

  @Bean
  @Scope("singleton")
  @ConfigurationProperties(prefix = "icgc.dcc.submission.ega.metadata.ftp")
  public EGAMetadataFTPConfig ftpConfig() {
    return new EGAMetadataFTPConfig();
  }

  @Bean
  @Scope("singleton")
  @ConfigurationProperties(prefix = "icgc.dcc.submission.ega.metadata.postgresql")
  public EGAMetadataPostgresqlConfig postgresqlConfig() {
    return new EGAMetadataPostgresqlConfig();
  }

  @Bean
  @Scope("singleton")
  public DriverManagerDataSource driverManagerDataSource(@Qualifier("postgresqlUrl") String url) {
    return
        new DriverManagerDataSource(url);
  }

  @Bean
  @Scope("singleton")
  public String postgresqlUrl(EGAMetadataPostgresqlConfig config) {
    return "jdbc:postgresql://" + config.getHost() + "/" + config.getDatabase() + "?user=" + config.getUser() + "&password=" + config.getPassword();
  }

  @Bean
  @Scope("singleton")
  public File tmpDataDir(){
    String systemDir = System.getProperty("java.io.tmpdir");
    return new File( (systemDir.endsWith("/")?systemDir.substring(0, systemDir.length()-1):systemDir) + "/ega/metadata" );
  }

  @Bean
  @Scope("singleton")
  public DownloadEGAFile downloadEGAFile(EGAMetadataFTPConfig ftpConfig, File tmp_data_dir) {
    return new DownloadEGAFileImpl(ftpConfig, tmp_data_dir);
  }

  @Bean
  @Scope("singleton")
  public UntarEGAFile untarEGAFile() {
    return new UntarEGAFileImpl();
  }

  @Bean
  @Scope("singleton")
  public Database rxjava_jdbc_database(String postgresqlUrl) {
    return Database.from(postgresqlUrl).asynchronous();
  }

  @Bean
  @Scope("singleton")
  public BadFormattedDataLogger badFormattedDataLogger(Database database) {
    return new EGAPostgresqlBadFormattedDataLogger(database);
  }

  @Bean
  @Scope("singleton")
  public DataExtractor<Pair<String, String>> dataExtractor() {
    return new EGASampleFileExtractor();
  }

  @Bean
  @Scope("singleton")
  public EGAMetadataRepo egaMetadataRepo(Database database, EGAMetadataPostgresqlConfig config) {
    return new EGAMetadataRepoPostgres(database, config.getViewName());
  }

  @Bean
  @Scope("singleton")
  public EGAMetadataService egaMetadataService(DownloadEGAFile downloader, UntarEGAFile untar, DataExtractor<Pair<String, String>> extractor, EGAMetadataRepo repo, File target_path) {
    return new EGAMetadataServiceImpl(downloader, untar, extractor, repo, target_path);
  }
}
