package org.icgc.dcc.submission.ega.repo.impl;

import com.github.davidmoten.rx.jdbc.Database;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.icgc.dcc.submission.ega.repo.EGAMetadataRepo;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@Slf4j
@RequiredArgsConstructor
public class EGAMetadataRepoPostgres implements EGAMetadataRepo {

  @NonNull
  private Database database;

  @NonNull
  private String viewName;

  private String table_name_prefix = "ega_sample_mapping_";

  private String sql_create_table =
      "CREATE TABLE IF NOT EXISTS ${table_name} ( " +
      "sample_id varchar(64), " +
      "file_id varchar(64) " +
      ");";

  private String sql_create_view = "CREATE OR REPLACE VIEW ega." + viewName + " AS SELECT * from ${table_name};";

  private String sql_batch_insert = "INSERT INTO ${table_name} VALUES(:1, :2);";

  private String sql_get_all_data_table = "select table_name from information_schema.tables where table_schema = 'ega' and table_name like 'ega_sample_mapping_%';";

  private String bad_data_table_name = "ega.bad_ega_sample_metadata";

  private String sql_clean_bad_sample_data_table = "DELETE FROM ega." + bad_data_table_name + " where timestamp < ?;";

  private String replaceTablename(String sql, String table_name) {
    return sql.replace("${table_name}", table_name);
  }

  private String getTablename() {
    return "ega." + table_name_prefix + LocalDateTime.now(ZoneId.of("America/Toronto")).atZone(ZoneId.of("America/Toronto")).toEpochSecond();
  }

  @Override
  public Observable<Integer> call(Observable<List<Pair<String, String>>> data) {

    String table_name = this.getTablename();

    return
      Observable.concat(
          database.update(
              this.replaceTablename(this.sql_create_table, table_name)
          ).count(),

          Observable.zip(data.count(), data, (count, list) -> {
            return
                database.commit(
                    database.update(this.replaceTablename(sql_batch_insert, table_name)).dependsOn(database.beginTransaction())
                        .batchSize(count)
                        .parameters(
                            Observable.from(
                                list.stream().map(pair -> {
                                  Map<String, String> map = new HashMap<>();
                                  map.put(":1", pair.getKey());
                                  map.put(":2", pair.getValue());
                                  return map;
                                }).collect(Collectors.toList())
                            )
                        ).count()
                ).flatMap(bool -> bool?Observable.just(1):Observable.error(new Exception("database commit failed!")));
          }).flatMap(i -> i),

          database.update(this.sql_create_view).count()
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
