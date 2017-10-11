package org.icgc.dcc.submission.ega.metadata.test;

import com.github.davidmoten.rx.jdbc.Database;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.icgc.dcc.submission.ega.refactoring.compress.UntarEGAFile;
import org.icgc.dcc.submission.ega.refactoring.compress.impl.UntarEGAFileImpl;
import org.icgc.dcc.submission.ega.refactoring.conf.EGAMetadataConfig;
import org.icgc.dcc.submission.ega.refactoring.download.impl.DownloadEGAFileImpl;
import org.icgc.dcc.submission.ega.refactoring.extractor.BadFormattedDataLogger;
import org.icgc.dcc.submission.ega.refactoring.extractor.DataExtractor;
import org.icgc.dcc.submission.ega.refactoring.extractor.impl.EGAPostgresqlBadFormattedDataLogger;
import org.icgc.dcc.submission.ega.refactoring.extractor.impl.EGASampleFileExtractor;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.io.File;
import java.util.Properties;


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

public class DownloadEGAFileTest {

  private static EGAMetadataConfig.EGAMetadataFTPConfig ftpConfig;
  private static EGAMetadataConfig.EGAMetadataPostgresqlConfig postgresqlConfig;

  private static DriverManagerDataSource driverManagerDataSource;

  private static Properties properties;

  private static String postgresqlUrl;

  private static File target_path = new File("/Users/gguo/work/data/tmp");

  private static Database database;

  private static BadFormattedDataLogger badDataLogger;

  @BeforeClass
  @SneakyThrows
  public static void initialize() {

    properties = new Properties();
    properties.load(DownloadEGAFileTest.class.getResourceAsStream("/application.properties"));

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

    postgresqlUrl = "jdbc:postgresql://" + postgresqlConfig.getHost() + "/" + postgresqlConfig.getDatabase() + "?user=" + postgresqlConfig.getUser() + "&password=" + postgresqlConfig.getPassword();

    driverManagerDataSource = new DriverManagerDataSource(postgresqlUrl);

    database = Database.from(postgresqlUrl);

    badDataLogger = new EGAPostgresqlBadFormattedDataLogger(database);
  }

  @Test
  public void download_test() {

    UntarEGAFile untar = new UntarEGAFileImpl();
    DataExtractor<Pair<String, String>> extractor = new EGASampleFileExtractor();

    (new DownloadEGAFileImpl(ftpConfig, target_path))
        .download()
        .flatMap(file ->
          rx.Observable.just(file).observeOn(Schedulers.io()).map(zipFile -> untar.untar(zipFile, target_path, zipFile.getName())).map(extractor::extract)
        )
        .subscribe(data_in_one_file -> {

        });

    try {
      Thread.sleep(600000L);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

  }

  @Test
  public void extract_test() {
    String content = "SA320820\tEGAN00001576819\tc3153125-1018-425d-a66b-d8dcdf39de89/analysis.c3153125-1018-425d-a66b-d8dcdf39de89.GNOS.xml.gz.cip\tEGAF00001722718\ttumor_4182393_RNA";
  }

}
