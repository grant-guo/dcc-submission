package org.icgc.dcc.submission.ega.refactoring.download.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPFile;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.icgc.dcc.submission.ega.refactoring.conf.EGAMetadataConfig;
import org.icgc.dcc.submission.ega.refactoring.download.DownloadEGAFile;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
@RequiredArgsConstructor
@Slf4j
public class DownloadEGAFileImpl implements DownloadEGAFile{

  @NonNull
  EGAMetadataConfig.EGAMetadataFTPConfig ftpConfig;
  @NonNull
  private File tmp_data_dir;

  private int sizeOfConnPool = 5;

  private int parallel = 20;

  private ImmutableList<FTPClient> ftpClients = null;

  private ExecutorService executor = Executors.newFixedThreadPool(parallel,new ThreadFactoryBuilder().setNameFormat("download-thread-pool-%d").build());

  private int currentIndexOfFTPClient = 0;

  @Override
  public Observable<File> download() {
    return
    Observable.defer(() -> {
      return
      Observable.zip(

          Observable.just(ftpConfig).flatMap(config -> {
            FTPClient ftpClient = new FTPClient();
            try {
              ftpClient.connect(ftpConfig.getHost());
              ftpClient.login(ftpConfig.getUser(), ftpConfig.getPassword());
              ftpClient.changeDirectory(ftpConfig.getPath());
              ftpClient.setType(FTPClient.TYPE_BINARY);

              FTPFile[] files = ftpClient.list("EGA*.tar.gz");
              log.info("There are {} files to be downloaded...", files.length);

              return Observable.just(Pair.of(ftpClient, Arrays.stream(files).map(ftpFile -> ftpFile.getName()).collect(Collectors.toList())));

            } catch (Exception e) {
              return Observable.error(e);
            }
          }).first(),

          Observable.range(1, sizeOfConnPool-1).flatMap(i -> {
            FTPClient ftpClient = new FTPClient();
            try {
              ftpClient.connect(ftpConfig.getHost());
              ftpClient.login(ftpConfig.getUser(), ftpConfig.getPassword());
              ftpClient.changeDirectory(ftpConfig.getPath());
              ftpClient.setType(FTPClient.TYPE_BINARY);

              return Observable.just(ftpClient);
            } catch (Exception e) {
              return Observable.error(e);
            }
          }).toList(),

          (first, second) -> {

            ftpClients = ImmutableList.<FTPClient>builder().add(first.getLeft()).addAll(second).build();

            return
                first.getRight();
          }
      ).flatMap(list -> Observable.from(list)).flatMap(file ->
        Observable.just(file).observeOn(Schedulers.from(executor)).flatMap(filename -> {

          FTPClient ftpClient = getFtpClient();

          File localFile = new File(tmp_data_dir, filename);
          try {
            log.debug("downloading file: {} in thread {}", filename, Thread.currentThread().getName());
            ftpClient.download(filename, localFile);
            log.info("finish downloading file: {}", filename);

            return Observable.just(localFile);
          } catch (Exception e) {
            return Observable.error(e);
          }
        })
      );
    });
  }

  private synchronized FTPClient getFtpClient() {
    if(currentIndexOfFTPClient == ftpClients.size())
      currentIndexOfFTPClient = 0;

    FTPClient ret = ftpClients.get(currentIndexOfFTPClient);

    currentIndexOfFTPClient++;

    return ret;

  }

}
