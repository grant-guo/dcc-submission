package org.icgc.dcc.submission.ega.test.metadata;

import org.icgc.dcc.submission.ega.metadata.download.impl.ShellScriptDownloader;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Optional;

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

public class ShellScriptDownloaderTest extends EGAMetadataResourcesProvider {

  @Test
  public void test_download(){
    ShellScriptDownloader downloader = new ShellScriptDownloader(
        "ftp://admin:admin@localhost:"+ defaultFtpPort + "/ICGC_metadata",
        "/tmp/submission/ega/test/data",
        "/ega/metadata/script/download_ega_metadata.sh"
    );

    Optional<File> file = downloader.download();
    Assert.assertTrue( file.isPresent() );

    Assert.assertEquals(file.get().getAbsolutePath(), "/tmp/submission/ega/test/data");

  }
}
