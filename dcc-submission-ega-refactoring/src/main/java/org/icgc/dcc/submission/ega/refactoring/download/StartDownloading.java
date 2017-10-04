package org.icgc.dcc.submission.ega.refactoring.download;

import it.sauronsoftware.ftp4j.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icgc.dcc.submission.ega.refactoring.conf.EGAMetadataConfig;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

import java.io.File;
import java.util.concurrent.CountDownLatch;
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
@RequiredArgsConstructor
@Slf4j
public class StartDownloading implements Observable.OnSubscribe<File>{

  @NonNull
  private EGAMetadataConfig.EGAMetadataFTPConfig ftp;
  @NonNull
  private String tmp_data_dir;
  @NonNull
  private int parallel = 20;//Runtime.getRuntime().availableProcessors() + 1;

  @Override
  public void call(Subscriber<? super File> subscriber) {
    FTPClient client = new FTPClient();
    try {
      client.connect(ftp.getHost());
      client.login(ftp.getUser(), ftp.getPassword());
      client.changeDirectory(ftp.getPath());
      client.setType(FTPClient.TYPE_BINARY);

      FTPFile[] files = client.list("EGA*.tar.gz");
      log.info("There are {} files to be downloaded...", files.length);

      CountDownLatch latch = new CountDownLatch(files.length);

      FTPClient[] clients = new FTPClient[parallel];
      clients[0] = client;
      for(int i =1;i<parallel;i++){
        FTPClient fc = new FTPClient();
        fc.connect(ftp.getHost());
        fc.login(ftp.getUser(), ftp.getPassword());
        fc.changeDirectory(ftp.getPath());
        fc.setType(FTPClient.TYPE_BINARY);
        clients[i] = fc;
      }

      ExecutorService pool = Executors.newFixedThreadPool(parallel);

      Observable
          .range(0, files.length)
          .flatMap(index ->
              Observable
                  .just(index)
                  .observeOn(Schedulers.from(pool))
                  .flatMap(i -> {
                    String fileName = files[i].getName();
                    File localFile = new File(tmp_data_dir + "/" + fileName);
                    try {
                      System.out.println("downloading file '" + fileName + "' in " + Thread.currentThread().getName());
                      clients[i%parallel].download(fileName, localFile);
                      latch.countDown();
                      return Observable.just(localFile);
                    } catch (Exception e) {
                      latch.countDown();
                      log.info("Downloading file {} failed, {}", fileName, e.getMessage());
                      return Observable.error(e);
                    }
                  })
                  .onErrorResumeNext(Observable.empty())
          )
          .subscribe(file -> {
            System.out.println("emitting file on " + Thread.currentThread().getName());
            subscriber.onNext(file);
          });

      latch.await();

      for(FTPClient fc: clients){
        fc.disconnect(true);
      }

      subscriber.onCompleted();

    } catch (Exception e) {
      subscriber.onError(e);
    }
  }
}
