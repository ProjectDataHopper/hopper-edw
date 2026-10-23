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
package org.hopper.edw.datavault.virtualization.dimensional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.pipeline.transforms.maskfields.MaskingPattern;
import org.apache.hop.pipeline.transforms.maskfields.MaskingValueSource;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.metadata.sourcemodel.service.SourceModelService;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmDimension;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAttribute;
import org.hopper.edw.datavault.metadata.dimensional.DmFact;
import org.hopper.edw.datavault.metadata.dimensional.DmFactDimensionRole;
import org.hopper.edw.datavault.metadata.dimensional.DmFactMeasure;
import org.hopper.edw.datavault.metadata.dimensional.DmSurrogateKeyStrategy;
import org.hopper.edw.datavault.metadata.dimensional.service.DimensionalMaskingPolicy;
import org.hopper.edw.datavault.metadata.dimensional.service.DimensionalModelService;
import org.hopper.edw.datavault.metadata.sourcemodel.service.SourceModelService;
import org.hopper.edw.datavault.virtualization.jdbc.HopSourceModelJdbcConnection;
import org.hopper.edw.datavault.virtualization.jdbc.HopSourceModelJdbcDriver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DimensionalModelServiceTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void starExposesTwoDateRolesAndExpandsTheSelectList() throws Exception {
    MemoryMetadataProvider provider = provider("POSTGRESQL", "mart");
    DimensionalModel model = retail(provider);
    DimensionalJdbcCatalog catalog =
        DimensionalJdbcCatalog.build(model, new Variables(), provider, "retail");

    List<DimensionalImportedKey> keys = catalog.importedKeys("f_orders", null);
    assertEquals(2, keys.size());
    assertEquals("d_date", keys.get(0).pkTable());
    assertEquals("date_key", keys.get(0).pkColumn());
    assertEquals("d_date", keys.get(1).pkTable());
    assertEquals("date_key", keys.get(1).pkColumn());
    assertTrue(
        keys.stream().anyMatch(key -> "order_date_key".equals(key.fkColumn())));
    assertTrue(keys.stream().anyMatch(key -> "ship_date_key".equals(key.fkColumn())));

    DimensionalSqlPlan star =
        DimensionalSqlEngine.plan(
            catalog, "SELECT * FROM customer", "retail", DimensionalMaskingPolicy.SAFE);
    assertFalse(star.sql().contains("*"));
    assertTrue(star.sql().toLowerCase().contains("email"));
    assertTrue(star.sql().toLowerCase().contains("city"));
    assertTrue(star.sql().contains("d_customer"));
    assertTrue(star.sql().contains("mart"));
    assertTrue(star.sql().contains("\""), star.sql());
    assertTrue(star.masksAnyColumn());
    assertEquals(
        "[d_customer]",
        org.apache.calcite.sql.dialect.MssqlSqlDialect.DEFAULT.quoteIdentifier("d_customer"));
  }

  @Test
  void schemaQualifiedNameMatchesTheServiceEvenWhenATableHasThatName() throws Exception {
    MemoryMetadataProvider provider = provider("POSTGRESQL", "mart");
    DimensionalModel model = retail(provider);
    ((DmDimension) model.findTable("customer")).setName("d_customer");
    DmFact orders = new DmFact();
    orders.setName("f_orders");
    orders.setTableName("f_orders");
    orders.getDimensionRoles().add(new DmFactDimensionRole("d_date", "order_date_key"));
    model.getTables().add(orders);
    DimensionalJdbcCatalog catalog =
        DimensionalJdbcCatalog.build(model, new Variables(), provider, "f_orders");
    DimensionalSqlPlan plan =
        DimensionalSqlEngine.plan(
            catalog,
            "SELECT * FROM f_orders.d_customer LIMIT 5000",
            "f_orders",
            DimensionalMaskingPolicy.SAFE);
    assertFalse(plan.sql().toLowerCase().contains("f_orders"), plan.sql());
    assertTrue(plan.sql().toLowerCase().contains("d_customer"), plan.sql());
    assertTrue(plan.sql().contains("mart"), plan.sql());

    DimensionalSqlPlan bare =
        DimensionalSqlEngine.plan(
            catalog, "SELECT city FROM d_customer", "f_orders", DimensionalMaskingPolicy.SAFE);
    assertTrue(bare.sql().toLowerCase().contains("d_customer"), bare.sql());
    assertFalse(bare.sql().toLowerCase().contains("f_orders.d_customer"), bare.sql());
  }

  @Test
  void qualifiedNameOnAnotherConnectionSchemaReachesTheDimensionalService() throws Exception {
    String name = "mem:dimroute_" + UUID.randomUUID().toString().replace("-", "");
    DatabaseMeta warehouse = h2(name);
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(DatabaseMeta.class).save(warehouse);
    Variables variables = new Variables();

    DimensionalModel model = new DimensionalModel();
    model.setName("orders");
    DimensionalConfiguration configuration = new DimensionalConfiguration();
    configuration.setTargetDatabase(warehouse.getName());
    model.setConfiguration(configuration);
    DmDimension customer = new DmDimension();
    customer.setName("d_customer");
    customer.setTableName("d_customer");
    customer.setSurrogateKeyField("dim_key");
    customer.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.AUTO_INCREMENT);
    customer.getAttributes().add(new DmDimensionAttribute("city"));
    DmFact orders = new DmFact();
    orders.setName("f_orders");
    orders.setTableName("f_orders");
    model.getTables().add(customer);
    model.getTables().add(orders);

    FileObject hdm =
        HopVfs.createTempFile("dim-route-", ".hdm", System.getProperty("java.io.tmpdir"));
    FileObject hsm =
        HopVfs.createTempFile("src-route-", ".hsm", System.getProperty("java.io.tmpdir"));
    try {
      ModelXmlWriteSupport.writeModelXml(
          HopDimensionalFileType.XML_TAG, model, HopVfs.getFilename(hdm), variables);
      SourceModel source = new SourceModel();
      source.setName("crm");
      SourceTable table = new SourceTable("contacts");
      SourceColumn column = new SourceColumn("email");
      column.setHopType(IValueMeta.TYPE_STRING);
      table.getColumns().add(column);
      source.getTables().add(table);
      ModelXmlWriteSupport.writeModelXml(
          SourceModel.XML_TAG, source, HopVfs.getFilename(hsm), variables);

      SourceModelService crm = new SourceModelService("crm");
      crm.setEnabled(true);
      crm.setModelFilename(HopVfs.getFilename(hsm));
      provider.getSerializer(SourceModelService.class).save(crm);
      DimensionalModelService service = new DimensionalModelService("f_orders");
      service.setEnabled(true);
      service.setModelFilename(HopVfs.getFilename(hdm));
      service.setMaskingPolicy(DimensionalMaskingPolicy.DISPLAY);
      provider.getSerializer(DimensionalModelService.class).save(service);

      try (Database database = database(variables, warehouse)) {
        database.execStatement("CREATE TABLE d_customer (dim_key BIGINT, city VARCHAR(40))");
        database.execStatement("INSERT INTO d_customer VALUES (1, 'Gent')");
      }

      Properties info = new Properties();
      info.put("metadataProvider", provider);
      info.put("variables", variables);
      org.hopper.edw.datavault.virtualization.jdbc.HopSourceModelJdbcDriver driver =
          new org.hopper.edw.datavault.virtualization.jdbc.HopSourceModelJdbcDriver();
      try (java.sql.Connection connection = driver.connect("jdbc:hop-hsm:service=crm", info)) {
        assertEquals("crm", connection.getSchema());
        try (ResultSet query =
            connection
                .createStatement()
                .executeQuery("SELECT * FROM f_orders.d_customer LIMIT 5000")) {
          assertTrue(query.next());
          assertEquals("Gent", query.getString("city"));
        }
        try (ResultSet quoted =
            connection
                .createStatement()
                .executeQuery("SELECT city FROM \"f_orders\".\"d_customer\" LIMIT 5000")) {
          assertTrue(quoted.next());
          assertEquals("Gent", quoted.getString("city"));
        }
      }
    } finally {
      hdm.delete();
      hsm.delete();
    }
  }

  @Test
  void safePolicyRejectsAPredicateOnAMaskedColumnAndAllowsAMeasure() throws Exception {
    MemoryMetadataProvider provider = provider("POSTGRESQL", "mart");
    DimensionalJdbcCatalog catalog =
        DimensionalJdbcCatalog.build(retail(provider), new Variables(), provider, "retail");
    DimensionalSqlException rejected =
        assertThrows(
            DimensionalSqlException.class,
            () ->
                DimensionalSqlEngine.plan(
                    catalog,
                    "SELECT email FROM customer WHERE email = 'a@b.com'",
                    "retail",
                    DimensionalMaskingPolicy.SAFE));
    assertTrue(rejected.isRejected());

    DimensionalSqlPlan sum =
        DimensionalSqlEngine.plan(
            catalog, "SELECT SUM(amount) FROM f_orders", "retail", DimensionalMaskingPolicy.SAFE);
    assertTrue(sum.sql().toLowerCase().contains("sum"));
    assertTrue(sum.sql().toLowerCase().contains("amount"));
    assertFalse(sum.masksAnyColumn());
  }

  @Test
  void redactsLiteralsAndAllowsNamedUsers() {
    DimensionalSqlLiteralRedactor.Redaction redaction =
        DimensionalSqlLiteralRedactor.redact("SELECT city FROM customer WHERE city = 'Gent' AND dim_key = 1");
    assertFalse(redaction.sql().contains("Gent"));
    assertFalse(redaction.sql().contains(" 1"));
    assertTrue(redaction.literalCount() >= 2);
    assertFalse(DimensionalSqlLiteralRedactor.hasExtraStatement("SELECT 1;"));
    assertTrue(DimensionalSqlLiteralRedactor.hasExtraStatement("SELECT 1; DELETE FROM customer"));

    DimensionalModelService service = new DimensionalModelService("retail");
    assertTrue(service.allows(null));
    service.setAllowedUsers(List.of("analyst"));
    assertTrue(service.allows("analyst"));
    assertFalse(service.allows("other"));
    assertFalse(service.allows(null));
  }

  @Test
  void sourceServiceNameCollision() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    SourceModelService source = new SourceModelService("retail");
    source.setEnabled(true);
    provider.getSerializer(SourceModelService.class).save(source);
    assertNotNull(DimensionalModelService.sourceNameConflict("retail", provider));
    source.setEnabled(false);
    provider.getSerializer(SourceModelService.class).save(source);
    assertNull(DimensionalModelService.sourceNameConflict("retail", provider));
  }

  @Test
  void masksTheProjectedCellAndAuditsTheAttempt() throws Exception {
    String name = "mem:dimsvc_" + UUID.randomUUID().toString().replace("-", "");
    DatabaseMeta warehouse = h2(name);
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(DatabaseMeta.class).save(warehouse);
    MaskingPattern pattern = new MaskingPattern();
    pattern.setName("hide-email");
    pattern.setValueSource(MaskingValueSource.SET_NULL);
    provider.getSerializer(MaskingPattern.class).save(pattern);

    DimensionalModel model = retail(provider);
    model.getConfiguration().setTargetDatabase(warehouse.getName());
    Variables variables = new Variables();
    try (Database database = database(variables, warehouse)) {
      database.execStatement(
          "CREATE TABLE d_customer (dim_key BIGINT, customer_id VARCHAR(20), email VARCHAR(80), city VARCHAR(40))");
      database.execStatement(
          "INSERT INTO d_customer (dim_key, customer_id, email, city) VALUES (1, 'C1', 'a@b.com', 'Gent')");
      database.execStatement(
          "CREATE TABLE f_orders (order_date_key BIGINT, ship_date_key BIGINT, amount DECIMAL(12,2))");
      database.execStatement("INSERT INTO f_orders VALUES (1, 2, 15.50)");
      database.execStatement("CREATE TABLE d_date (date_key BIGINT)");
    }

    DimensionalQueryExecutor.QueryResult preview =
        DimensionalQueryExecutor.preview(
            model,
            "SELECT email, city FROM customer WHERE city = 'Gent'",
            DimensionalMaskingPolicy.SAFE,
            10,
            variables,
            provider);
    assertEquals(1, preview.rows().size());
    assertNull(preview.rows().get(0).getData()[0]);
    assertEquals("Gent", preview.rows().get(0).getData()[1]);

    HopException missing =
        assertThrows(
            HopException.class,
            () -> {
              DmDimensionAttribute email = model.findTable("customer") instanceof DmDimension dimension
                  ? dimension.getAttributesOrEmpty().get(0)
                  : null;
              email.setMaskingPatternName("missing-pattern");
              DimensionalQueryExecutor.preview(
                  model,
                  "SELECT email FROM customer",
                  DimensionalMaskingPolicy.SAFE,
                  10,
                  variables,
                  provider);
            });
    assertTrue(missing.getMessage().contains("missing-pattern"));
    ((DmDimension) model.findTable("customer")).getAttributes().get(0).setMaskingPatternName("hide-email");

    DimensionalModelService service = new DimensionalModelService("retail");
    service.setAuditEnabled(true);
    service.setAuditConnection(warehouse.getName());
    service.setAuditTable("edw_dimensional_query_audit");
    service.setMaskingPolicy(DimensionalMaskingPolicy.SAFE);
    DimensionalAuditSupport.createTable(variables, provider, service);

    DimensionalQueryExecutor.QueryResult audited =
        DimensionalQueryExecutor.query(
            service,
            model,
            "SELECT city FROM customer WHERE city = 'Gent'",
            10,
            List.of(),
            "analyst",
            "test",
            "127.0.0.1",
            variables,
            provider);
    assertEquals("Gent", audited.rows().get(0).getData()[0]);

    try (Database database = database(variables, warehouse)) {
      try (var resultSet =
          database.openQuery(
              "SELECT principal, status, sql_text FROM edw_dimensional_query_audit")) {
        Object[] row = database.getRow(resultSet);
        assertNotNull(row);
        assertEquals("analyst", String.valueOf(row[0]));
        assertEquals("OK", String.valueOf(row[1]));
        assertFalse(String.valueOf(row[2]).contains("Gent"));
      }
    }

    HopException rejected =
        assertThrows(
            HopException.class,
            () ->
                DimensionalQueryExecutor.query(
                    service,
                    model,
                    "SELECT email FROM customer WHERE email = 'secret@x.com'",
                    10,
                    List.of(),
                    "analyst",
                    "test",
                    "127.0.0.1",
                    variables,
                    provider));
    assertTrue(rejected.getMessage().toLowerCase().contains("where") || rejected.getCause() != null);
    try (Database database = database(variables, warehouse)) {
      try (var resultSet =
          database.openQuery(
              "SELECT status, sql_text FROM edw_dimensional_query_audit WHERE status = 'REJECTED'")) {
        Object[] row = database.getRow(resultSet);
        assertNotNull(row);
        assertEquals("REJECTED", String.valueOf(row[0]));
        assertFalse(String.valueOf(row[1]).contains("secret@x.com"));
      }
    }
  }

  @Test
  void embeddedDriverOpensADimensionalModelService() throws Exception {
    String name = "mem:dimemb_" + UUID.randomUUID().toString().replace("-", "");
    DatabaseMeta warehouse = h2(name);
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(DatabaseMeta.class).save(warehouse);
    MaskingPattern pattern = new MaskingPattern();
    pattern.setName("hide-email");
    pattern.setValueSource(MaskingValueSource.SET_NULL);
    provider.getSerializer(MaskingPattern.class).save(pattern);

    Variables variables = new Variables();
    DimensionalModel model = new DimensionalModel();
    model.setName("retail");
    DimensionalConfiguration configuration = new DimensionalConfiguration();
    configuration.setTargetDatabase(warehouse.getName());
    model.setConfiguration(configuration);
    DmDimension customer = new DmDimension();
    customer.setName("customer");
    customer.setTableName("d_customer");
    customer.setSurrogateKeyField("dim_key");
    customer.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.AUTO_INCREMENT);
    DmDimensionAttribute email = new DmDimensionAttribute("email");
    email.setMaskingPatternName("hide-email");
    customer.getAttributes().add(email);
    customer.getAttributes().add(new DmDimensionAttribute("city"));
    DmDimension date = new DmDimension();
    date.setName("d_date");
    date.setTableName("d_date");
    date.setSurrogateKeyField("date_key");
    date.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.AUTO_INCREMENT);
    DmFact orders = new DmFact();
    orders.setName("f_orders");
    orders.setTableName("f_orders");
    orders.getDimensionRoles().add(new DmFactDimensionRole("d_date", "order_date_key"));
    orders.getDimensionRoles().add(new DmFactDimensionRole("d_date", "ship_date_key"));
    model.getTables().add(customer);
    model.getTables().add(date);
    model.getTables().add(orders);

    FileObject tempFile =
        HopVfs.createTempFile("dim-embedded-", ".hdm", System.getProperty("java.io.tmpdir"));
    try {
      ModelXmlWriteSupport.writeModelXml(
          HopDimensionalFileType.XML_TAG, model, HopVfs.getFilename(tempFile), variables);
      try (Database database = database(variables, warehouse)) {
        database.execStatement(
            "CREATE TABLE d_customer (dim_key BIGINT, email VARCHAR(80), city VARCHAR(40))");
        database.execStatement("INSERT INTO d_customer VALUES (1, 'a@b.com', 'Gent')");
      }
      DimensionalModelService service = new DimensionalModelService("retail");
      service.setEnabled(true);
      service.setModelFilename(HopVfs.getFilename(tempFile));
      service.setMaskingPolicy(DimensionalMaskingPolicy.SAFE);
      provider.getSerializer(DimensionalModelService.class).save(service);

      Properties info = new Properties();
      info.put("metadataProvider", provider);
      info.put("variables", variables);
      HopSourceModelJdbcDriver driver = new HopSourceModelJdbcDriver();
      try (HopSourceModelJdbcConnection connection =
          (HopSourceModelJdbcConnection) driver.connect("jdbc:hop-hsm:embedded", info)) {
        assertEquals("retail", connection.getSchema());
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet schemas = metadata.getSchemas()) {
          assertTrue(schemas.next());
          assertEquals("retail", schemas.getString("TABLE_SCHEM"));
        }
        try (ResultSet tables = metadata.getTables(null, "retail", "customer", null)) {
          assertTrue(tables.next());
          assertEquals("customer", tables.getString("TABLE_NAME"));
        }
        try (ResultSet keys = metadata.getPrimaryKeys(null, "retail", "customer")) {
          assertTrue(keys.next());
          assertEquals("dim_key", keys.getString("COLUMN_NAME"));
        }
        try (ResultSet imported = metadata.getImportedKeys(null, "retail", "f_orders")) {
          int roles = 0;
          while (imported.next()) {
            roles++;
            assertEquals("d_date", imported.getString("PKTABLE_NAME"));
          }
          assertEquals(2, roles);
        }
        try (ResultSet query =
            connection.createStatement().executeQuery("SELECT email, city FROM customer")) {
          assertTrue(query.next());
          assertNull(query.getString(1));
          assertEquals("Gent", query.getString(2));
        }
      }

      try (HopSourceModelJdbcConnection named =
          (HopSourceModelJdbcConnection) driver.connect("jdbc:hop-hsm:service=retail", info)) {
        assertEquals("retail", named.getSchema());
      }
    } finally {
      tempFile.delete();
    }
  }

  @Test
  void boundParameterReachesTheWarehouse() throws Exception {
    String name = "mem:dimparam_" + UUID.randomUUID().toString().replace("-", "");
    DatabaseMeta warehouse = h2(name);
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(DatabaseMeta.class).save(warehouse);
    DimensionalModel model = retail(provider);
    model.getConfiguration().setTargetDatabase(warehouse.getName());
    Variables variables = new Variables();
    try (Database database = database(variables, warehouse)) {
      database.execStatement(
          "CREATE TABLE d_customer (dim_key BIGINT, customer_id VARCHAR(20), email VARCHAR(80), city VARCHAR(40))");
      database.execStatement("INSERT INTO d_customer VALUES (1, 'C1', 'a@b.com', 'Gent')");
      database.execStatement(
          "CREATE TABLE f_orders (order_date_key BIGINT, ship_date_key BIGINT, amount DECIMAL(12,2))");
      database.execStatement("CREATE TABLE d_date (date_key BIGINT)");
    }
    DimensionalModelService service = new DimensionalModelService("retail");
    service.setMaskingPolicy(DimensionalMaskingPolicy.SAFE);
    DimensionalQueryExecutor.QueryResult result =
        DimensionalQueryExecutor.query(
            service,
            model,
            "SELECT city FROM customer WHERE dim_key = ?",
            10,
            List.of(new DimensionalQueryParameter(Types.BIGINT, "1", false)),
            "analyst",
            "test",
            "127.0.0.1",
            variables,
            provider);
    assertEquals(1, result.rows().size());
    assertEquals("Gent", result.rows().get(0).getData()[0]);
  }

  @Test
  void auditFailureReturnsNoRows() throws Exception {
    DatabaseMeta warehouse = h2("mem:dimok_" + UUID.randomUUID().toString().replace("-", ""));
    DatabaseMeta broken = h2("mem:dimdown_" + UUID.randomUUID());
    broken.setHostname("127.0.0.1");
    broken.setPort("1");
    broken.setDBName("missing");
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(DatabaseMeta.class).save(warehouse);
    provider.getSerializer(DatabaseMeta.class).save(broken);
    DimensionalModel model = retail(provider);
    model.getConfiguration().setTargetDatabase(warehouse.getName());
    Variables variables = new Variables();
    try (Database database = database(variables, warehouse)) {
      database.execStatement(
          "CREATE TABLE d_customer (dim_key BIGINT, customer_id VARCHAR(20), email VARCHAR(80), city VARCHAR(40))");
      database.execStatement("INSERT INTO d_customer VALUES (1, 'C1', 'a@b.com', 'Gent')");
      database.execStatement(
          "CREATE TABLE f_orders (order_date_key BIGINT, ship_date_key BIGINT, amount DECIMAL(12,2))");
      database.execStatement("CREATE TABLE d_date (date_key BIGINT)");
    }
    DimensionalModelService service = new DimensionalModelService("retail");
    service.setAuditEnabled(true);
    service.setAuditConnection(broken.getName());
    service.setMaskingPolicy(DimensionalMaskingPolicy.SAFE);
    HopException failure =
        assertThrows(
            HopException.class,
            () ->
                DimensionalQueryExecutor.query(
                    service,
                    model,
                    "SELECT city FROM customer",
                    10,
                    List.of(),
                    "analyst",
                    "test",
                    "127.0.0.1",
                    variables,
                    provider));
    assertTrue(failure.getMessage().toLowerCase().contains("audit"));
  }

  private static MemoryMetadataProvider provider(String databaseType, String schema) throws HopException {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("warehouse");
    databaseMeta.setDatabaseType(databaseType);
    databaseMeta.setPreferredSchemaName(schema);
    provider.getSerializer(DatabaseMeta.class).save(databaseMeta);
    return provider;
  }

  private static DimensionalModel retail(IHopMetadataProvider provider) throws HopException {
    DimensionalModel model = new DimensionalModel();
    model.setName("retail");
    DimensionalConfiguration configuration = new DimensionalConfiguration();
    configuration.setTargetDatabase("warehouse");
    model.setConfiguration(configuration);
    model.setMetadataProvider(provider);

    DmDimension customer = new DmDimension();
    customer.setName("customer");
    customer.setTableName("d_customer");
    customer.setSurrogateKeyField("dim_key");
    customer.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.AUTO_INCREMENT);
    customer.getNaturalKeys().add(new org.hopper.edw.datavault.metadata.dimensional.DmNaturalKeyField("customer_id"));
    DmDimensionAttribute email = new DmDimensionAttribute("email");
    email.setMaskingPatternName("hide-email");
    customer.getAttributes().add(email);
    customer.getAttributes().add(new DmDimensionAttribute("city"));

    DmDimension date = new DmDimension();
    date.setName("d_date");
    date.setTableName("d_date");
    date.setSurrogateKeyField("date_key");
    date.setSurrogateKeyStrategy(DmSurrogateKeyStrategy.AUTO_INCREMENT);

    DmFact orders = new DmFact();
    orders.setName("f_orders");
    orders.setTableName("f_orders");
    orders.getDimensionRoles().add(new DmFactDimensionRole("d_date", "order_date_key"));
    orders.getDimensionRoles().add(new DmFactDimensionRole("d_date", "ship_date_key"));
    orders.getMeasures().add(new DmFactMeasure("amount", true));

    model.getTables().add(customer);
    model.getTables().add(date);
    model.getTables().add(orders);
    return model;
  }

  private static DatabaseMeta h2(String dbName) {
    DatabaseMeta databaseMeta = new DatabaseMeta();
    databaseMeta.setName("warehouse-" + dbName);
    databaseMeta.setDatabaseType("H2");
    databaseMeta.setAccessType(DatabaseMeta.TYPE_ACCESS_NATIVE);
    databaseMeta.setDBName(dbName + ";DB_CLOSE_DELAY=-1");
    databaseMeta.setUsername("sa");
    databaseMeta.setPassword("");
    return databaseMeta;
  }

  private static Database database(Variables variables, DatabaseMeta databaseMeta) throws HopException {
    Database database =
        new Database(
            new SimpleLoggingObject("dimensional-service-test", LoggingObjectType.GENERAL, null),
            variables,
            databaseMeta);
    database.connect();
    return database;
  }
}
