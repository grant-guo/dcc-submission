package org.icgc.dcc.submission.ega.metadata.test;

import com.github.davidmoten.rx.jdbc.Database;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.icgc.dcc.submission.ega.compress.UntarEGAFile;
import org.icgc.dcc.submission.ega.compress.impl.UntarEGAFileImpl;
import org.icgc.dcc.submission.ega.conf.EGAMetadataConfig;
import org.icgc.dcc.submission.ega.download.DownloadEGAFile;
import org.icgc.dcc.submission.ega.download.impl.DownloadEGAFileImpl;
import org.icgc.dcc.submission.ega.extractor.BadFormattedDataLogger;
import org.icgc.dcc.submission.ega.extractor.DataExtractor;
import org.icgc.dcc.submission.ega.extractor.impl.EGAPostgresqlBadFormattedDataLogger;
import org.icgc.dcc.submission.ega.extractor.impl.EGASampleFileExtractor;
import org.icgc.dcc.submission.ega.repo.EGAMetadataRepo;
import org.icgc.dcc.submission.ega.repo.impl.EGAMetadataRepoPostgres;
import org.icgc.dcc.submission.ega.service.EGAMetadataService;
import org.icgc.dcc.submission.ega.service.impl.EGAMetadataServiceImpl;
import org.junit.BeforeClass;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.stream.Collectors;

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

public class EGAMetadataServiceImplTest {

  private static EGAMetadataConfig.EGAMetadataFTPConfig ftpConfig;
  private static EGAMetadataConfig.EGAMetadataPostgresqlConfig postgresqlConfig;

  private static Properties properties;

  private static String postgresqlUrl;

  private static Database database;

  private static BadFormattedDataLogger badDataLogger;

  private static DownloadEGAFile downloader;

  private static UntarEGAFile untar;

  private static DataExtractor<Pair<String, String>> extractor;

  private static EGAMetadataRepo repo;

  private static File target_path = new File("/Users/gguo/work/data/tmp");

  private static EGAMetadataService service;

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

    database = Database.from(postgresqlUrl);

    badDataLogger = new EGAPostgresqlBadFormattedDataLogger(database);

    downloader = new DownloadEGAFileImpl(ftpConfig, target_path);
    untar = new UntarEGAFileImpl(target_path);
    extractor = new EGASampleFileExtractor();
    repo = new EGAMetadataRepoPostgres(database, postgresqlConfig.getViewName());

    service = new EGAMetadataServiceImpl(downloader, untar, extractor, repo, target_path);
  }

  @Test
  public void run_execute() {
    service.execute();

    try {
      Thread.sleep(600000L);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Test
  @SneakyThrows
  public void run_download() {

    this.downloader.download()
        .flatMap(file ->
            Observable.just(file).observeOn(Schedulers.io())
                .map(zipFile -> untar.untar(zipFile))
                .map(extractor::extract)
        ).map(list -> list.size()).toList().map(list -> list.stream().collect(Collectors.reducing( (a, b) -> a + b ))).subscribe(i -> System.out.println("size of resultset is " + i.get()));

    Thread.sleep(600000L);

  }

}
