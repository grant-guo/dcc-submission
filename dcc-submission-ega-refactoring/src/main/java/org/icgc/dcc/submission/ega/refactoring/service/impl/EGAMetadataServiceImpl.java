package org.icgc.dcc.submission.ega.refactoring.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.icgc.dcc.submission.ega.refactoring.compress.UntarEGAFile;
import org.icgc.dcc.submission.ega.refactoring.download.DownloadEGAFile;
import org.icgc.dcc.submission.ega.refactoring.extractor.DataExtractor;
import org.icgc.dcc.submission.ega.refactoring.repo.EGAMetadataRepo;
import org.icgc.dcc.submission.ega.refactoring.service.EGAMetadataService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import rx.Observable;
import rx.schedulers.Schedulers;

import javax.annotation.PostConstruct;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
@Service
@RequiredArgsConstructor
@Slf4j
public class EGAMetadataServiceImpl implements EGAMetadataService{

  @NonNull
  private DownloadEGAFile downloader;

  @NonNull
  private UntarEGAFile untar;

  @NonNull
  private DataExtractor<Pair<String, String>> extractor;

  @NonNull
  private EGAMetadataRepo repo;

  @NonNull
  private File target_path;


  @PostConstruct
  public void executeOnBooting() {
    ( new Thread(() -> {
      execute();
    }) ).start();

  }

  @Override
  @Scheduled(cron = "${ega.metadata.cron.data}")
  public void execute() {
    this.downloader.download()
        .flatMap(file ->
          Observable.just(file)
              .observeOn(Schedulers.io())
              .map(zipFile -> untar.untar(zipFile, target_path, zipFile.getName()))
              .map(extractor::extract)
        ).compose(repo::call).subscribe();
  }


  @Scheduled(cron = "${ega.metadata.cron.clean}")
  public void cleanHistoryData() {
    this.repo.cleanHistoryData(
        LocalDateTime.now(ZoneId.of("America/Toronto")).atZone(ZoneId.of("America/Toronto")).toEpochSecond() - 7 * 24 * 3600
    ).subscribe();
  }
}
