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

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DmJunkDimensionDdlTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void generateBuildDdlIncludesHashKeyIndexForSharedSurrogate() throws Exception {
    IHopMetadataProvider metadataProvider = h2MetadataProvider();
    DimensionalModel model = modelWithJunk(true, DmJunkHashCodeStrategy.MD5, true, false);
    DmJunkDimension junk = (DmJunkDimension) model.findTable("d_orders_junk");

    List<String> ddl = junk.generateBuildDdl(metadataProvider, new Variables(), model);
    String sql = String.join("\n", ddl).toLowerCase(Locale.ROOT);

    assertTrue(sql.contains("create table"), sql);
    assertTrue(sql.contains("idx_d_orders_junk_hk"), sql);
    assertTrue(sql.contains("orders_junk_hk"), sql);
  }

  @Test
  void generateBuildDdlIndexesSeparateHashColumn() throws Exception {
    IHopMetadataProvider metadataProvider = h2MetadataProvider();
    DimensionalModel model = modelWithJunk(false, DmJunkHashCodeStrategy.MD5, false, false);
    DmJunkDimension junk = (DmJunkDimension) model.findTable("d_orders_junk");

    List<String> ddl = junk.generateBuildDdl(metadataProvider, new Variables(), model);
    String sql = String.join("\n", ddl).toLowerCase(Locale.ROOT);

    assertTrue(sql.contains("idx_d_orders_junk_hk"), sql);
    assertTrue(sql.contains("hashcode"), sql);
  }

  @Test
  void generateBuildDdlSkipsHashKeyIndexWhenStrategyIsNone() throws Exception {
    IHopMetadataProvider metadataProvider = h2MetadataProvider();
    DimensionalModel model = modelWithJunk(true, DmJunkHashCodeStrategy.NONE, false, false);
    DmJunkDimension junk = (DmJunkDimension) model.findTable("d_orders_junk");

    List<String> ddl = junk.generateBuildDdl(metadataProvider, new Variables(), model);
    String sql = String.join("\n", ddl).toLowerCase(Locale.ROOT);

    assertTrue(sql.contains("create table"), sql);
    assertFalse(sql.contains("idx_d_orders_junk_hk"), sql);
  }

  @Test
  void generateBuildDdlSkipsRedundantHashIndexWhenPrimaryKeyCoversHash() throws Exception {
    IHopMetadataProvider metadataProvider = h2MetadataProvider();
    DimensionalModel model = modelWithJunk(true, DmJunkHashCodeStrategy.MD5, true, true);
    DmJunkDimension junk = (DmJunkDimension) model.findTable("d_orders_junk");

    List<String> ddl = junk.generateBuildDdl(metadataProvider, new Variables(), model);
    String sql = String.join("\n", ddl).toLowerCase(Locale.ROOT);

    assertTrue(sql.contains("create table"), sql);
    assertFalse(sql.contains("idx_d_orders_junk_hk"), sql);
  }

  private static DimensionalModel modelWithJunk(
      boolean shareHashAndSurrogate,
      DmJunkHashCodeStrategy hashStrategy,
      boolean computeHashKey,
      boolean generatePrimaryKeys) {
    DimensionalModel model = new DimensionalModel();
    model.setName("retail-f-orders");
    DimensionalConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");
    config.setGeneratePrimaryKeys(generatePrimaryKeys);

    DmJunkDimension junk = new DmJunkDimension();
    junk.setName("d_orders_junk");
    junk.setTableName("d_orders_junk");
    junk.setSurrogateKeyField("orders_junk_hk");
    if (computeHashKey) {
      junk.setSurrogateKeyStrategy(DmJunkSurrogateKeyStrategy.COMPUTE_HASH_KEY);
    }
    junk.setHashCodeStrategy(hashStrategy);
    junk.setUseSurrogateKeyAsHashCodeField(shareHashAndSurrogate);
    junk.getKeyFields().add(new DmNaturalKeyField("j1"));
    junk.getKeyFields().add(new DmNaturalKeyField("j2"));
    junk.getKeyFields().add(new DmNaturalKeyField("j3"));
    junk.getSourceOrDefault().setSourceType(DmSourceType.FACT_TABLE);
    junk.getSourceOrDefault().setSourceFactTableName("f_orders");
    model.getTables().add(junk);
    return model;
  }

  private static IHopMetadataProvider h2MetadataProvider() throws HopException {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("Vault");
    databaseMeta.setDatabaseType("H2");
    databaseMeta.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    databaseMeta.setDBName("mem:junk_ddl_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    databaseMeta.setHostname("");
    databaseMeta.setPort("");
    databaseMeta.setUsername("sa");
    databaseMeta.setPassword("");
    provider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return provider;
  }
}
