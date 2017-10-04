package org.icgc.dcc.submission.ega.metadata.test;

import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.icgc.dcc.submission.ega.refactoring.compress.UntarEGAFile;
import org.icgc.dcc.submission.ega.refactoring.conf.EGAMetadataConfig;
import org.icgc.dcc.submission.ega.refactoring.download.StartDownloading;
import org.icgc.dcc.submission.ega.refactoring.extractor.BadFormattedDataLogger;
import org.icgc.dcc.submission.ega.refactoring.extractor.DataExtractor;
import org.icgc.dcc.submission.ega.refactoring.extractor.impl.EGAPostgresqlBadFormattedDataLogger;
import org.icgc.dcc.submission.ega.refactoring.extractor.impl.EGASampleFileExtractor;
import org.icgc.dcc.submission.ega.refactoring.repo.EGAMetadataRepo;
import org.icgc.dcc.submission.ega.refactoring.repo.impl.EGAMetadataRepoPostgres;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

public class StartDownloadingTest {

  private static EGAMetadataConfig.EGAMetadataFTPConfig ftpConfig;
  private static EGAMetadataConfig.EGAMetadataPostgresqlConfig postgresqlConfig;

  private static DriverManagerDataSource driverManagerDataSource;

  private static Properties properties;

  @BeforeClass
  @SneakyThrows
  public static void initialize() {

    properties = new Properties();
    properties.load(StartDownloadingTest.class.getResourceAsStream("application.properties"));

    ftpConfig = new EGAMetadataConfig.EGAMetadataFTPConfig();
    ftpConfig.setHost("ftp-private.ebi.ac.uk");
    ftpConfig.setUser(properties.getProperty("ftp.user"));
    ftpConfig.setPassword(properties.getProperty("ftp.password"));
    ftpConfig.setPath("/ICGC_metadata");

    postgresqlConfig = new EGAMetadataConfig.EGAMetadataPostgresqlConfig();
    postgresqlConfig.setHost(properties.getProperty("db.host"));
    postgresqlConfig.setUser(properties.getProperty("db.user"));
    postgresqlConfig.setPassword(properties.getProperty("db.password"));
    postgresqlConfig.setDatabase("ICGC_metadata");
    postgresqlConfig.setViewName("view_ega_sample_mapping");

    driverManagerDataSource = new DriverManagerDataSource(
        "jdbc:postgresql://" + postgresqlConfig.getHost() + "/" + postgresqlConfig.getDatabase() + "?user=" + postgresqlConfig.getUser() + "&password=" + postgresqlConfig.getPassword()
    );

  }

  @Test
  public void testDownloading() {

    String dir = "/Users/gguo/work/data/tmp";

    BadFormattedDataLogger logger = new EGAPostgresqlBadFormattedDataLogger(driverManagerDataSource);

    DataExtractor<Pair<String, String>> extractor = new EGASampleFileExtractor(logger);

    EGAMetadataRepo repo = new EGAMetadataRepoPostgres(postgresqlConfig, driverManagerDataSource);

    ExecutorService executor = Executors.newFixedThreadPool(10);

    Observable
        .unsafeCreate(new StartDownloading(ftpConfig, dir))
        .compose(new UntarEGAFile(dir))
        .map(extractor::extract)
        .subscribeOn(Schedulers.from(executor))
        .subscribe(repo::persist);

    try {
      Thread.sleep(600000L);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
