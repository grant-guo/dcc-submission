package org.icgc.dcc.submission.ega.compress.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.icgc.dcc.submission.ega.compress.UntarEGAFile;

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
public class UntarEGAFileImpl implements UntarEGAFile{

  @NonNull
  private File target_dir;

  @Override
  public File untar(File src) {
    TarGZipUnArchiver archiver = new TarGZipUnArchiver();
    ConsoleLoggerManager manager = new ConsoleLoggerManager();
    manager.initialize();
    archiver.enableLogging(manager.getLoggerForComponent("UntarEGAFileImpl"));
    archiver.setSourceFile(src);
    archiver.setDestDirectory(target_dir);
    archiver.extract();
    File untar = new File(target_dir, src.getName().substring(0, src.getName().indexOf(".tar.gz")));
    src.delete();
    return untar;
  }
}
