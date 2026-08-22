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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.transform.dvhashkey.DvHashKeyMeta;
import org.apache.hop.datavault.transform.mergerowsplus.MergeRowsPlusMeta;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transforms.concatfields.ConcatFieldsMeta;
import org.apache.hop.pipeline.transforms.selectvalues.SelectMetadataChange;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvHubUpdatePipelineTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @Test
  void hubDataTypeMappingsUseVaultRecordSourceAlias() throws Exception {
    DvHub hub = new DvHub("hub_customer");
    hub.setHashKeyFieldName("customer_hk");
    hub.setRecordSourceFieldName("x_record_source");
    hub.setBusinessKeys(List.of(businessKey("customer_id", "customer_id", "E2E-customer-hub")));

    DataVaultSource source = e2eCustomerHubSource();
    DataVaultModel model = new DataVaultModel();
    model.getConfigurationOrDefault().setRecordSourceField("x_record_source");

    PipelineMeta pipelineMeta = new PipelineMeta();
    DvDatabaseHubSourcePipelineBuilder builder =
        new DvDatabaseHubSourcePipelineBuilder(
            new Variables(),
            testMetadataProvider(),
            model,
            pipelineMeta,
            source,
            source.getDvSourceOrDefault(),
            hub,
            new Point(0, 0));
    builder.build();
    builder.applyCatalogDataTypeMappings();

    SelectValuesMeta selectMeta =
        (SelectValuesMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> "apply data type mappings".equals(t.getName()))
                .findFirst()
                .orElseThrow()
                .getTransform();
    List<String> metaNames =
        selectMeta.getSelectOption().getMeta().stream().map(SelectMetadataChange::getName).toList();
    assertTrue(metaNames.contains("customer_id"), metaNames::toString);
    assertTrue(metaNames.contains("x_record_source"), metaNames::toString);
    assertFalse(metaNames.contains("record_source"), metaNames::toString);
    assertFalse(metaNames.contains("load_date"), metaNames::toString);
  }

  @Test
  void hubSourceSqlUsesOnlyBusinessKeysForCurrentRecordSource() throws Exception {
    DvHub hub = multiSourceCustomerHub();
    DataVaultSource prefsSource = customerPrefsSource();

    PipelineMeta pipelineMeta = new PipelineMeta();
    DvDatabaseHubSourcePipelineBuilder builder =
        new DvDatabaseHubSourcePipelineBuilder(
            new Variables(),
            testMetadataProvider(),
            new DataVaultModel(),
            pipelineMeta,
            prefsSource,
            prefsSource.getDvSourceOrDefault(),
            hub,
            new Point(0, 0));
    builder.build();

    TableInputMeta sourceMeta =
        (TableInputMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> t.getName().startsWith("source"))
                .findFirst()
                .orElseThrow()
                .getTransform();

    String sql = sourceMeta.getSql().replace('\n', ' ');
    assertTrue(sql.startsWith("SELECT DISTINCT customer_id,"));
    assertTrue(sql.contains("'crm_customer_prefs'"));
    assertTrue(sql.contains("customer_prefs"));
    assertTrue(sql.contains("ORDER BY customer_id"));
    assertFalse(sql.contains("customer_id, customer_id"));
  }

  @Test
  void hubSourceSqlOrdersByBusinessKey() throws Exception {
    DvHub hub = stringItemHub();
    DataVaultSource itemSource = itemSource();

    DataVaultModel model = new DataVaultModel();

    PipelineMeta pipelineMeta = new PipelineMeta();
    DvDatabaseHubSourcePipelineBuilder builder =
        new DvDatabaseHubSourcePipelineBuilder(
            new Variables(),
            mssqlMetadataProvider(),
            model,
            pipelineMeta,
            itemSource,
            itemSource.getDvSourceOrDefault(),
            hub,
            new Point(0, 0));
    builder.build();

    TableInputMeta sourceMeta =
        (TableInputMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> t.getName().startsWith("source"))
                .findFirst()
                .orElseThrow()
                .getTransform();

    String sql = sourceMeta.getSql().replace('\n', ' ');
    assertTrue(sql.contains("ORDER BY"));
    assertTrue(sql.contains("ITMREF_0"));
  }

  @Test
  void hubPipelineMergesOnBusinessKeysWithoutSortRows() throws Exception {
    Variables variables = catalogVariables();
    MemoryMetadataProvider metadataProvider = mssqlMetadataProvider();
    DataVaultModel model = registerItemHubModel(metadataProvider, variables);

    DvHub hub = model.findHub("hub_item");
    List<PipelineMeta> pipelines =
        hub.generateUpdatePipelines(metadataProvider, variables, model, new Date(), null);

    assertEquals(1, pipelines.size());
    PipelineMeta pipeline = pipelines.get(0);
    MergeRowsPlusMeta mergeRowsMeta =
        (MergeRowsPlusMeta) pipeline.findTransform("merge_diff").getTransform();
    assertEquals(List.of("ITMREF_0"), mergeRowsMeta.getKeyFields());
    assertNotNull(pipeline.findTransform("calc_item_hk"));
    assertTrue(
        pipeline.getTransforms().stream().noneMatch(t -> t.getTransform() instanceof SortRowsMeta));

    TableInputMeta targetMeta =
        (TableInputMeta) pipeline.findTransform("target_hub_item").getTransform();
    assertTrue(targetMeta.getSql().contains("ORDER BY"));
    assertTrue(targetMeta.getSql().contains("ITMREF_0"));
  }

  @Test
  void getBusinessKeysForSourceFiltersByRecordSourceName() {
    DvHub hub = multiSourceCustomerHub();

    assertEquals(1, hub.getBusinessKeysForSource("crm_customer_prefs", new Variables()).size());
    assertEquals(
        "customer_id",
        hub.getBusinessKeysForSource("crm_customer_prefs", new Variables()).get(0).getName());
    assertEquals(5, hub.getBusinessKeys().size());
    assertEquals(1, hub.getDistinctBusinessKeys().size());
  }

  @Test
  void compositeHubSourceSqlSelectsAllSourceParts() throws Exception {
    DvHub hub = compositeBurgerHub();
    DataVaultSource extSource = burgerExtSource();

    PipelineMeta pipelineMeta = new PipelineMeta();
    DvDatabaseHubSourcePipelineBuilder builder =
        new DvDatabaseHubSourcePipelineBuilder(
            new Variables(),
            testMetadataProvider(),
            new DataVaultModel(),
            pipelineMeta,
            extSource,
            extSource.getDvSourceOrDefault(),
            hub,
            new Point(0, 0));
    builder.build();

    TableInputMeta sourceMeta =
        (TableInputMeta)
            pipelineMeta.getTransforms().stream()
                .filter(t -> t.getName().startsWith("source"))
                .findFirst()
                .orElseThrow()
                .getTransform();

    String sql = sourceMeta.getSql().replace('\n', ' ');
    assertTrue(sql.contains("num_seq_bkcc_bk"));
    assertTrue(sql.contains("num_seq_bk"));
    assertFalse(sql.contains("burger_bk"));
  }

  @Test
  void compositeHubLayoutUsesSingleVaultBkColumnNamedByBusinessKey() throws Exception {
    Variables variables = catalogVariables();
    MemoryMetadataProvider metadataProvider = mssqlMetadataProvider();
    DataVaultModel model = registerCompositeBurgerModel(metadataProvider, variables);
    DvHub hub = model.findHub("hub_burger");

    var layout = hub.getTargetTableLayout(metadataProvider, variables, model);
    assertNotNull(layout);
    assertNotNull(layout.searchValueMeta("burger_bk"));
    assertEquals(null, layout.searchValueMeta("num_seq_bkcc_bk"));
    assertEquals(null, layout.searchValueMeta("num_seq_bk"));
    // One BK column only (plus hash, record source, load date)
    long bkCols =
        layout.getValueMetaList().stream().filter(vm -> "burger_bk".equals(vm.getName())).count();
    assertEquals(1, bkCols);
  }

  @Test
  void compositeHubPipelineComposesAndHashesParts() throws Exception {
    Variables variables = catalogVariables();
    MemoryMetadataProvider metadataProvider = mssqlMetadataProvider();
    DataVaultModel model = registerCompositeBurgerModel(metadataProvider, variables);
    DvHub hub = model.findHub("hub_burger");

    List<PipelineMeta> pipelines =
        hub.generateUpdatePipelines(metadataProvider, variables, model, new Date(), null);
    assertEquals(1, pipelines.size());
    PipelineMeta pipeline = pipelines.get(0);

    assertTrue(
        pipeline.getTransforms().stream()
            .anyMatch(t -> t.getTransform() instanceof ConcatFieldsMeta),
        "expected ConcatFields compose step");
    ConcatFieldsMeta concat =
        (ConcatFieldsMeta)
            pipeline.getTransforms().stream()
                .filter(t -> t.getTransform() instanceof ConcatFieldsMeta)
                .findFirst()
                .orElseThrow()
                .getTransform();
    assertEquals("burger_bk", concat.getExtraFields().getTargetFieldName());
    assertEquals("#", concat.getSeparator());
    assertEquals(2, concat.getOutputFields().size());

    MergeRowsPlusMeta mergeRowsMeta =
        (MergeRowsPlusMeta) pipeline.findTransform("merge_diff").getTransform();
    assertEquals(List.of("burger_bk"), mergeRowsMeta.getKeyFields());

    DvHashKeyMeta hashMeta =
        (DvHashKeyMeta) pipeline.findTransform("calc_burger_hk").getTransform();
    assertEquals(
        List.of("num_seq_bkcc_bk", "num_seq_bk"),
        hashMeta.getFields().stream().map(f -> f.getName()).toList());
    assertEquals("#", hashMeta.getBusinessKeyDelimiter());
    assertEquals("#", hashMeta.getHashContentSuffix());

    TableInputMeta targetMeta =
        (TableInputMeta) pipeline.findTransform("target_hub_burger").getTransform();
    assertTrue(targetMeta.getSql().contains("burger_bk"));
    assertFalse(targetMeta.getSql().contains("num_seq_bkcc_bk"));
  }

  private static DvHub multiSourceCustomerHub() {
    DvHub hub = new DvHub("hub_customer");
    hub.setHashKeyFieldName("customer_hk");
    List<BusinessKey> keys = new ArrayList<>();
    for (String sourceName :
        List.of(
            "crm_customer",
            "crm_customer_prefs",
            "crm_customer_address",
            "crm_customer_phone",
            "crm_customer_email")) {
      keys.add(businessKey("customer_id", "customer_id", sourceName));
    }
    hub.setBusinessKeys(keys);
    return hub;
  }

  private static DataVaultSource e2eCustomerHubSource() {
    DataVaultSource source = new DataVaultSource("E2E-customer-hub");
    source.setSourceIndicatorField("record_source");
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dbSource.setDatabaseName("CRM");
    dbSource.setSchemaName("public");
    dbSource.setTableName("customer_hub");
    source.setSource(dbSource);
    List<SourceField> fields = new ArrayList<>();
    SourceField customerId = new SourceField("customer_id");
    customerId.setHopType(IValueMeta.TYPE_INTEGER);
    customerId.setLength("9");
    fields.add(customerId);
    SourceField loadDate = new SourceField("load_date");
    loadDate.setHopType(IValueMeta.TYPE_TIMESTAMP);
    fields.add(loadDate);
    SourceField recordSource = new SourceField("record_source");
    recordSource.setHopType(IValueMeta.TYPE_STRING);
    recordSource.setLength("30");
    fields.add(recordSource);
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static DataVaultSource customerPrefsSource() {
    DataVaultSource source = new DataVaultSource("crm_customer_prefs");
    source.setSourceIndicator("crm_customer_prefs");
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dbSource.setDatabaseName("CRM");
    dbSource.setSchemaName("public");
    dbSource.setTableName("customer_prefs");
    source.setSource(dbSource);
    List<SourceField> fields = new ArrayList<>();
    SourceField field = new SourceField();
    field.setName("customer_id");
    field.setSourceDataType("Integer");
    field.setHopType(IValueMeta.TYPE_INTEGER);
    fields.add(field);
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static BusinessKey businessKey(String name, String sourceField, String recordSource) {
    BusinessKey key = new BusinessKey(name);
    key.setSourceFieldName(sourceField);
    key.setRecordSourceName(recordSource);
    key.setDataType("Integer");
    return key;
  }

  private static DvHub stringItemHub() {
    DvHub hub = new DvHub("hub_item");
    hub.setHashKeyFieldName("item_hk");
    hub.setTableName("hub_item");
    hub.setRecordSources(List.of("erp_item"));
    BusinessKey key = new BusinessKey("ITMREF_0");
    key.setName("ITMREF_0");
    key.setSourceFieldName("ITMREF_0");
    key.setRecordSourceName("erp_item");
    key.setDataType("String");
    key.setLength("20");
    hub.setBusinessKeys(List.of(key));
    return hub;
  }

  private static Variables catalogVariables() throws Exception {
    Variables variables = new Variables();
    Path projectHome = Files.createTempDirectory("hub-pipeline-test-");
    variables.setVariable("PROJECT_HOME", projectHome.toString().replace('\\', '/'));
    return variables;
  }

  private static DataVaultModel registerItemHubModel(
      MemoryMetadataProvider metadataProvider, Variables variables) throws Exception {
    DataVaultModel model = buildItemHubModel();
    registerCatalog(metadataProvider, variables);
    DvSourceCatalogService.upsertSource(itemSource(), "local-catalog", variables, metadataProvider);
    return model;
  }

  private static void registerCatalog(MemoryMetadataProvider metadataProvider, Variables variables)
      throws HopException {
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(
        Path.of(variables.resolve("${PROJECT_HOME}"), "catalog-data")
            .toString()
            .replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);
    RecordDefinitionRegistry.getInstance().invalidate();
  }

  private static DataVaultModel buildItemHubModel() {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");
    config.setDataCatalogConnection("local-catalog");
    config.setRecordSourceField("x_record_source");

    DvHub hub = stringItemHub();
    hub.setRecordSourceFieldName("x_record_source");
    model.getTables().add(hub);
    return model;
  }

  private static DataVaultSource itemSource() {
    DataVaultSource source = new DataVaultSource("erp_item");
    source.setSourceIndicator("erp_item");
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dbSource.setDatabaseName("CRM");
    dbSource.setSchemaName("dbo");
    dbSource.setTableName("STA_XBIITM");
    source.setSource(dbSource);
    List<SourceField> fields = new ArrayList<>();
    SourceField field = new SourceField();
    field.setName("ITMREF_0");
    field.setSourceDataType("nvarchar");
    field.setHopType(IValueMeta.TYPE_STRING);
    field.setLength("20");
    fields.add(field);
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static DvHub compositeBurgerHub() {
    DvHub hub = new DvHub("hub_burger");
    hub.setHashKeyFieldName("burger_hk");
    hub.setTableName("hub_burger");
    hub.setRecordSources(List.of("ext_burger"));
    BusinessKey key = new BusinessKey("burger_bk");
    key.setComposite(true);
    key.setDataType("String");
    key.setLength("100");
    key.setSourceFieldNames(List.of("num_seq_bkcc_bk", "num_seq_bk"));
    key.setRecordSourceName("ext_burger");
    hub.setBusinessKeys(List.of(key));
    return hub;
  }

  private static DataVaultSource burgerExtSource() {
    DataVaultSource source = new DataVaultSource("ext_burger");
    source.setSourceIndicator("ext_burger");
    DvDatabaseSource dbSource = new DvDatabaseSource();
    dbSource.setDatabaseName("CRM");
    dbSource.setSchemaName("public");
    dbSource.setTableName("ami_indiv_klanten");
    source.setSource(dbSource);
    List<SourceField> fields = new ArrayList<>();
    for (String name : List.of("num_seq_bkcc_bk", "num_seq_bk")) {
      SourceField field = new SourceField();
      field.setName(name);
      field.setSourceDataType("varchar");
      field.setHopType(IValueMeta.TYPE_STRING);
      field.setLength("50");
      fields.add(field);
    }
    source.getDvSourceOrDefault().setFields(fields);
    return source;
  }

  private static DataVaultModel registerCompositeBurgerModel(
      MemoryMetadataProvider metadataProvider, Variables variables) throws Exception {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    config.setTargetDatabase("Vault");
    config.setDataCatalogConnection("local-catalog");
    config.setRecordSourceField("dv_record_source");
    config.setBusinessKeyDelimiter("#");
    config.setHashContentSuffix("#");
    config.setHashContentCasing(HashContentCasing.NONE.name());
    config.setHashUsesComposedBusinessKey(false);

    DvHub hub = compositeBurgerHub();
    hub.setRecordSourceFieldName("dv_record_source");
    model.getTables().add(hub);

    registerCatalog(metadataProvider, variables);
    DvSourceCatalogService.upsertSource(
        burgerExtSource(), "local-catalog", variables, metadataProvider);
    return model;
  }

  private static MemoryMetadataProvider testMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = new MemoryMetadataProvider();
    DatabaseMeta crm = databaseMetaWithPluginId("CRM", "GENERIC");
    metadataProvider.getSerializer(DatabaseMeta.class).save(crm);
    return metadataProvider;
  }

  private static MemoryMetadataProvider mssqlMetadataProvider() throws HopException {
    MemoryMetadataProvider metadataProvider = testMetadataProvider();
    metadataProvider
        .getSerializer(DatabaseMeta.class)
        .save(databaseMetaWithPluginId("Vault", DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID));
    metadataProvider
        .getSerializer(DatabaseMeta.class)
        .save(databaseMetaWithPluginId("CRM", DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID));
    return metadataProvider;
  }

  private static DatabaseMeta databaseMetaWithPluginId(String name, String pluginId) {
    DatabaseMeta databaseMeta =
        new DatabaseMeta() {
          @Override
          public String getPluginId() {
            return pluginId;
          }
        };
    databaseMeta.setName(name);
    return databaseMeta;
  }
}
