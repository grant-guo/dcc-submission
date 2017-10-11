package org.icgc.dcc.submission.ega.refactoring.repo.impl;

import com.github.davidmoten.rx.jdbc.Database;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.icgc.dcc.submission.ega.refactoring.conf.EGAMetadataConfig;
import org.icgc.dcc.submission.ega.refactoring.repo.EGAMetadataRepo;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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
@Slf4j
@RequiredArgsConstructor
public class EGAMetadataRepoPostgres implements EGAMetadataRepo {

  @NonNull
  private Database database;

  @NonNull
  private String viewName;

  private String table_name_prefix = "ega_sample_mapping_";

  private String sql_create_table =
      "CREATE TABLE IF NOT EXISTS ? ( " +
      "sample_id varchar(64), " +
      "file_id varchar(64) " +
      ");";

  private String sql_create_view = "CREATE OR REPLACE VIEW ? AS SELECT * from ?";

  private String sql_batch_insert = "INSERT INTO ega.{table_name} VALUES(?, ?)";

  private String sql_get_all_data_table = "select table_name from information_schema.tables where table_schema = 'ega' and table_name like 'ega_sample_mapping_%';";

  private String bad_data_table_name = "ega.bad_ega_sample_metadata";

  private String sql_clean_bad_sample_data_table = "DELETE FROM ega." + bad_data_table_name + " where timestamp < ?;";

  private String getTablename() {
    return "ega." + table_name_prefix + LocalDateTime.now(ZoneId.of("America/Toronto")).atZone(ZoneId.of("America/Toronto")).toEpochSecond();
  }

  @Override
  public Observable<Integer> call(Observable<List<Pair<String, String>>> data) {

    String table_name = this.getTablename();

    return
      Observable.concat(
          database.update(this.sql_create_table).parameter(table_name).count(),

          data.flatMap(list ->
              Observable.from(list)
                  .observeOn(Schedulers.io())
                  .flatMap(pair ->
                      database.update(sql_batch_insert)
                          .parameters("ega." + table_name, pair.getKey(), pair.getValue())
                          .count()
                  )
          ),

          database.update(this.sql_create_view).parameters("ega." + viewName, table_name).count()
      ).doOnCompleted(() -> {
        log.info("Finish writing data to table: " + table_name);
      });

  }

  @Override
  public Observable<Integer> cleanHistoryData(long timestamp) {

    return
      Observable.concat(

        database.update("DROP TABLE ?")
            .parameters(
                database.select(this.sql_get_all_data_table)
                    .getAs(String.class)
                    .filter(name -> {
                      long time = Long.parseLong( name.substring(name.lastIndexOf("_") + 1) );
                      return time < timestamp;
                    })
                    .map(name -> "ega." + name)
            ).count(),

        database.update(this.sql_clean_bad_sample_data_table).parameter(timestamp).count()

      );

  }


}
