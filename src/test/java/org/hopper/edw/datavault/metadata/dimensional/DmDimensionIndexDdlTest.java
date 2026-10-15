/*
 * Copyright 2026 i-Bridge bv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hopper.edw.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.metadata.DvIndexDdlSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmDimensionIndexDdlTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void dateDimensionIndexesNaturalKeyWithoutSurrogate() throws Exception {
    IHopMetadataProvider metadataProvider = h2MetadataProvider("date_idx_");
    DimensionalModel model = dateModel(false);
    DmDimension dimension = (DmDimension) model.findTable(DmDateDimensionTemplate.DEFAULT_DIMENSION_NAME);

    List<String> first = dimension.generateBuildDdl(metadataProvider, new Variables(), model);
    String sql = join(first);

    assertTrue(sql.contains("create table"), sql);
    assertTrue(sql.contains("idx_d_date_nk"), sql);
    assertTrue(sql.contains("date_key"), sql);
    assertFalse(sql.contains("idx_d_date_tk"), sql);
  }

  @Test
  void existingDateKeyTableGetsTheIndexOnce() throws Exception {
    IHopMetadataProvider metadataProvider = h2MetadataProvider("date_backfill_");
    Variables variables = new Variables();
    DatabaseMeta databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load("Vault");
    try (Database database =
        new Database(
            new SimpleLoggingObject("DmDimensionIndexDdlTest", LoggingObjectType.GENERAL, null),
            variables,
            databaseMeta)) {
      database.connect();
      database.execStatements("CREATE TABLE d_date (date_key VARCHAR(50));");

      List<String> first = new ArrayList<>();
      DvIndexDdlSupport.appendIndex(
          first,
          database,
          databaseMeta,
          variables,
          null,
          "d_date",
          DvIndexDdlSupport.SUFFIX_NATURAL_KEY,
          List.of("date_key"),
          List.of(),
          null);
      assertTrue(join(first).contains("idx_d_date_nk"), join(first));
      database.execStatements(first.get(0));

      List<String> second = new ArrayList<>();
      DvIndexDdlSupport.appendIndex(
          second,
          database,
          databaseMeta,
          variables,
          null,
          "d_date",
          DvIndexDdlSupport.SUFFIX_NATURAL_KEY,
          List.of("date_key"),
          List.of(),
          null);
      assertTrue(second.isEmpty(), join(second));
    }
  }

  @Test
  void type2IndexesNaturalKeyAndDateFromAndSkipsSurrogateWhenPrimaryKeyExists() throws Exception {
    IHopMetadataProvider metadataProvider = h2MetadataProvider("type2_pk_");
    DimensionalModel model = type2Model(true);
    DmDimension dimension = (DmDimension) model.findTable("dim_order");

    String sql = join(dimension.generateBuildDdl(metadataProvider, new Variables(), model));

    assertTrue(sql.contains("idx_d_order_nk"), sql);
    assertTrue(sql.contains("order_id"), sql);
    assertTrue(sql.contains("date_from"), sql);
    assertFalse(sql.contains("idx_d_order_tk"), sql);
    assertTrue(sql.contains("primary key"), sql);
  }

  @Test
  void type2IndexesSurrogateWhenPrimaryKeysAreOff() throws Exception {
    IHopMetadataProvider metadataProvider = h2MetadataProvider("type2_nopk_");
    DimensionalModel model = type2Model(false);
    DmDimension dimension = (DmDimension) model.findTable("dim_order");

    String sql = join(dimension.generateBuildDdl(metadataProvider, new Variables(), model));

    assertTrue(sql.contains("idx_d_order_nk"), sql);
    assertTrue(sql.contains("date_from"), sql);
    assertTrue(sql.contains("idx_d_order_tk"), sql);
    assertTrue(sql.contains("dim_key"), sql);
    assertFalse(sql.contains("primary key"), sql);
  }

  @Test
  void insertOnlyFactHasNoLookupIndex() {
    assertTrue(
        DmLookupIndexSupport.indexSpecs(new DmFact(), new DimensionalConfiguration(), new Variables())
            .isEmpty());
  }

  private static DimensionalModel dateModel(boolean generatePrimaryKeys) {
    DimensionalModel model = new DimensionalModel();
    model.setName("dates");
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");
    config.setGeneratePrimaryKeys(generatePrimaryKeys);
    model.getTables().add(DmDateDimensionTemplate.createDateDimension(null));
    return model;
  }

  private static DimensionalModel type2Model(boolean generatePrimaryKeys) {
    DimensionalModel model = new DimensionalModel();
    model.setName("orders");
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");
    config.setGeneratePrimaryKeys(generatePrimaryKeys);

    DmDimension dimension = new DmDimension();
    dimension.setName("dim_order");
    dimension.setTableName("d_order");
    dimension.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.AUTO_INCREMENT);
    dimension.setSurrogateKeyField("dim_key");
    dimension.getNaturalKeys().add(new DmNaturalKeyField("order_id"));
    dimension.getAttributes().add(new DmDimensionAttribute("status", DmScdUpdatePolicy.TYPE2));
    dimension.getSourceOrDefault().setSourceSql("select order_id, status from orders");
    model.getTables().add(dimension);
    return model;
  }

  private static String join(List<String> ddl) {
    return String.join("\n", ddl).toLowerCase(Locale.ROOT);
  }

  private static IHopMetadataProvider h2MetadataProvider(String prefix) throws HopException {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    databaseMeta.setDatabaseType("H2");
    databaseMeta.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    databaseMeta.setDBName("mem:" + prefix + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    databaseMeta.setHostname("");
    databaseMeta.setPort("");
    databaseMeta.setUsername("sa");
    databaseMeta.setPassword("");
    provider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return provider;
  }
}
