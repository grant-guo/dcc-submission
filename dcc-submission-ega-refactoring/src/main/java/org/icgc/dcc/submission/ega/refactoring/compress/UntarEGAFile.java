package org.icgc.dcc.submission.ega.refactoring.compress;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;

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

@RequiredArgsConstructor
public class UntarEGAFile implements rx.Observable.Transformer<File, File>{

  @NonNull
  private String tmp_data_dir;

  @Override
  public rx.Observable<File> call(rx.Observable<File> tarFiles) {

    File destDir = new File(tmp_data_dir);

    return
      tarFiles.map(tar -> {
        TarGZipUnArchiver archiver = new TarGZipUnArchiver();
        ConsoleLoggerManager manager = new ConsoleLoggerManager();
        manager.initialize();
        archiver.enableLogging(manager.getLoggerForComponent("UntarEGAFile"));
        archiver.setSourceFile(tar);
        archiver.setDestDirectory(destDir);
        archiver.extract();
        return new File(destDir, getFilenameWithoutExt(tar.getName()));
      });
  }

  private String getFilenameWithoutExt(String name) {
    return name.substring(0, name.indexOf(".tar.gz"));
  }

}
