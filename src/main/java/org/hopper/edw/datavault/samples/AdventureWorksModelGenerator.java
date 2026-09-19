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
package org.hopper.edw.datavault.samples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.json.JsonMetadataProvider;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.hopper.edw.datavault.layout.ElkGraphLayout;
import org.hopper.edw.datavault.layout.ElkLayout;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSourceImportSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceJoinType;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelConfiguration;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationshipMultiplicity;
import org.hopper.edw.datavault.metadata.sourcemodel.importing.DatabaseSchemaImportSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.importing.SourceSchemaImportOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.importing.SourceSchemaImportResult;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultApplySupport;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassification;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassifier;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultProposal;

/**
 * Headless importer: AdventureWorks SQL Server schemas → {@code .hsm} + raw Data Vault {@code
 * .hdv}. Invoked from {@code scripts/run-adventureworks.sh generate-models}.
 */
public final class AdventureWorksModelGenerator {

  private static final List<String> SCHEMAS =
      List.of("Person", "HumanResources", "Production", "Purchasing", "Sales");
  private static final Set<String> SKIP_TABLES =
      Set.of(
          "AWBuildVersion",
          "DatabaseLog",
          "ErrorLog",
          "Document",
          "ProductDocument",
          "ProductPhoto",
          "Illustration",
          "TransactionHistoryArchive");

  private AdventureWorksModelGenerator() {}

  public static void main(String[] args) throws Exception {
    Path projectHome =
        Path.of(args.length > 0 ? args[0] : "adventureworks").toAbsolutePath().normalize();
    Path hsmPath = projectHome.resolve("models/adventureworks.hsm");
    Path hdvPath = projectHome.resolve("models/adventureworks.hdv");

    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", projectHome.toString());
    variables.setVariable(
        "HOP_LICENSE_HEADER_FILE", projectHome.resolve("../license-header.txt").normalize().toString());
    applyEnvironmentDefaults(variables);
    loadHopEnvironmentFile(projectHome.resolve("environments/local-docker.json"), variables);

    HopEnvironment.init();
    Encr.init("Hop");
    PluginRegistry registry = PluginRegistry.getInstance();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, variables, registry);

    JsonMetadataProvider metadataProvider =
        new JsonMetadataProvider(
            Encr.getEncoder(), projectHome.resolve("metadata").toString(), variables);

    IHopMetadataSerializer<DatabaseMeta> dbSerializer =
        metadataProvider.getSerializer(DatabaseMeta.class);
    DatabaseMeta databaseMeta = dbSerializer.load("AdventureWorks");
    if (databaseMeta == null) {
      throw new IllegalStateException("Metadata connection AdventureWorks was not found");
    }

    SourceModel sourceModel = new SourceModel();
    sourceModel.setFilename(hsmPath.toString());
    sourceModel.setName("adventureworks");
    sourceModel.setNameSynchronizedWithFilename(true);
    sourceModel.setDescription(
        "AdventureWorks OLTP (Person, HumanResources, Production, Purchasing, Sales)");
    sourceModel.setConfigurationName("source-model");
    sourceModel.setMetadataProvider(metadataProvider);
    ModelConfigurationResolver.attach(sourceModel, metadataProvider);

    SourceModelConfiguration config = sourceModel.getConfigurationOrDefault();
    config.setDefaultDatabase("AdventureWorks");
    config.setCatalogConnection("local-catalog");
    config.setCatalogNamespace("hop/adventureworks/sources");
    config.setDefaultDataTypeMappingNames("sqlserver-types");

    SourceSchemaImportOptions options = SourceSchemaImportOptions.defaults();
    options.setDatabaseName("AdventureWorks");
    options.setPublishToCatalog(true);
    options.setCatalogConnectionName("local-catalog");

    int imported = 0;
    int relationships = 0;
    for (String schema : SCHEMAS) {
      options.setSchemaName(schema);
      List<String> tables = listTables(databaseMeta, schema, variables);
      if (tables.isEmpty()) {
        System.out.println("No tables in schema " + schema);
        continue;
      }
      System.out.println("Importing " + schema + " (" + tables.size() + " tables)...");
      SourceSchemaImportResult result =
          DatabaseSchemaImportSupport.importTables(
              sourceModel, databaseMeta, options, tables, variables, metadataProvider);
      DatabaseSchemaImportSupport.applyImportResult(sourceModel, result);
      imported += result.getImportedTablesOrEmpty().size();
      relationships += result.getImportedRelationshipsOrEmpty().size();
      for (String warning : result.getWarningsOrEmpty()) {
        System.out.println("  warn: " + warning);
      }
      for (String error : result.getErrorsOrEmpty()) {
        System.err.println("  error: " + error);
      }
    }

    SourceSchemaImportResult fkPass =
        DatabaseSchemaImportSupport.importMissingForeignKeys(
            sourceModel, databaseMeta, variables);
    DatabaseSchemaImportSupport.applyImportResult(sourceModel, fkPass);
    relationships += fkPass.getImportedRelationshipsOrEmpty().size();
    for (String warning : fkPass.getWarningsOrEmpty()) {
      System.out.println("  fk warn: " + warning);
    }
    System.out.println(
        "Cross-schema FK pass added " + fkPass.getImportedRelationshipsOrEmpty().size() + " edges");

    if (ensureSalesOrderDetailProductRelationship(sourceModel)) {
      System.out.println(
          "Added SalesOrderDetail → Product relationship (ProductID is only a composite FK to SpecialOfferProduct)");
    }

    ElkLayout layout = ElkLayout.createDefault();
    layout.setTargetWidth(1600);
    ElkGraphLayout.fromSourceModel(sourceModel).layout(layout);

    Files.createDirectories(hsmPath.getParent());
    ModelXmlWriteSupport.writeModelXml(
        SourceModel.XML_TAG, sourceModel, hsmPath.toString(), variables);
    System.out.println(
        "Wrote "
            + hsmPath
            + " tables="
            + sourceModel.getTables().size()
            + " imported="
            + imported
            + " relationships="
            + sourceModel.getRelationships().size()
            + " (this pass +"
            + relationships
            + ")");

    SourceToVaultOptions vaultOptions = SourceToVaultOptions.defaults();
    SourceToVaultClassification classification =
        SourceToVaultClassifier.classify(sourceModel, null, null, vaultOptions);
    excludeSkippedProposals(classification);
    for (String warning : classification.getWarnings()) {
      System.out.println("classify warn: " + warning);
    }
    System.out.println("Classification proposals: " + classification.getProposals().size());
    for (SourceToVaultProposal proposal : classification.getProposals()) {
      if (proposal == null) {
        continue;
      }
      System.out.println(
          "  "
              + proposal.getSourceTableName()
              + " role="
              + proposal.getRole()
              + " included="
              + proposal.isIncluded()
              + " objects="
              + proposal.getObjects().size()
              + (proposal.getSkipReason() != null ? " skip=" + proposal.getSkipReason() : ""));
    }

    DataVaultModel vaultModel = new DataVaultModel();
    vaultModel.setFilename(hdvPath.toString());
    vaultModel.setName("adventureworks");
    vaultModel.setNameSynchronizedWithFilename(true);
    vaultModel.setDescription("Raw Data Vault generated from adventureworks.hsm");
    vaultModel.setConfigurationName("data-vault");
    vaultModel.setMetadataProvider(metadataProvider);
    ModelConfigurationResolver.attach(vaultModel, metadataProvider);

    var applyResult =
        SourceToVaultApplySupport.apply(
            sourceModel,
            vaultModel,
            classification,
            true,
            variables,
            metadataProvider,
            vaultOptions);
    ElkGraphLayout.fromDataVaultModel(vaultModel).layout(layout);
    ModelXmlWriteSupport.writeModelXml(
        "data-vault-model", vaultModel, hdvPath.toString(), variables);
    System.out.println(
        "Wrote "
            + hdvPath
            + " tables="
            + vaultModel.getTables().size()
            + " created="
            + applyResult.getCreatedTableNames()
            + " reused="
            + applyResult.getReusedTableNames());
    for (String warning : applyResult.getWarnings()) {
      System.out.println("apply warn: " + warning);
    }
  }

  /**
   * AdventureWorks stores {@code SalesOrderDetail.ProductID} only as part of the composite FK to
   * {@code SpecialOfferProduct}. That parent classifies as a link, so Generate Data Vault never
   * creates {@code lnk_salesorderdetail_product}. Draw the missing Product edge so the sales fact
   * can join the product hub.
   *
   * @return true when the relationship was added
   */
  static boolean ensureSalesOrderDetailProductRelationship(SourceModel sourceModel) {
    if (sourceModel == null) {
      return false;
    }
    for (SourceRelationship existing : sourceModel.getRelationships()) {
      if (existing == null) {
        continue;
      }
      if ("SalesOrderDetail".equalsIgnoreCase(existing.getChildTableName())
          && "Product".equalsIgnoreCase(existing.getParentTableName())
          && existing.getChildColumns().size() == 1
          && "ProductID".equalsIgnoreCase(existing.getChildColumns().get(0))) {
        return false;
      }
    }
    SourceRelationship relationship =
        new SourceRelationship("FK_SalesOrderDetail_Product_ProductID");
    relationship.setDescription(
        "Drawn for the dimensional slice: SalesOrderDetail.ProductID is only a composite FK to SpecialOfferProduct.");
    relationship.setChildTableName("SalesOrderDetail");
    relationship.setParentTableName("Product");
    relationship.setChildColumns(List.of("ProductID"));
    relationship.setParentColumns(List.of("ProductID"));
    relationship.setDefaultJoinType(SourceJoinType.LEFT);
    relationship.setChildMultiplicity(SourceRelationshipMultiplicity.ZERO_OR_MANY);
    relationship.setParentMultiplicity(SourceRelationshipMultiplicity.ONE);
    relationship.setCardinality("0..N:1");
    sourceModel.getRelationships().add(relationship);
    return true;
  }

  private static void excludeSkippedProposals(SourceToVaultClassification classification) {
    for (SourceToVaultProposal proposal : classification.getProposals()) {
      if (proposal == null || proposal.getSourceTableName() == null) {
        continue;
      }
      String name = proposal.getSourceTableName();
      if (SKIP_TABLES.contains(name)) {
        proposal.setIncluded(false);
      }
    }
  }

  private static List<String> listTables(
      DatabaseMeta databaseMeta, String schema, Variables variables) throws Exception {
    SimpleLoggingObject logging =
        new SimpleLoggingObject("AdventureWorksModelGenerator", LoggingObjectType.GENERAL, null);
    try (Database database = new Database(logging, variables, databaseMeta)) {
      database.connect();
      String[] names = database.getTablenames(schema, false);
      LinkedHashSet<String> tables = new LinkedHashSet<>();
      if (names != null) {
        for (String raw : names) {
          String table = DvDatabaseSourceImportSupport.stripTableNameQuotes(raw);
          if (table == null || table.isBlank()) {
            continue;
          }
          int dot = table.lastIndexOf('.');
          if (dot >= 0) {
            table = table.substring(dot + 1);
          }
          if (SKIP_TABLES.contains(table)) {
            continue;
          }
          tables.add(table);
        }
      }
      List<String> sorted = new ArrayList<>(tables);
      sorted.sort(String.CASE_INSENSITIVE_ORDER);
      return sorted;
    }
  }

  private static void applyEnvironmentDefaults(Variables variables) {
    variables.setVariable("DB_SOURCE_HOST", "localhost");
    variables.setVariable("DB_SOURCE_PORT", "14333");
    variables.setVariable("DB_SOURCE_USER", "sa");
    variables.setVariable("DB_SOURCE_PASSWORD", "Test_Password123");
    variables.setVariable("DB_SOURCE_NAME", "AdventureWorks2025");
    variables.setVariable("DB_HOST", "localhost");
    variables.setVariable("DB_PORT", "54320");
    variables.setVariable("DB_USER", "test");
    variables.setVariable("DB_PASSWORD", "test");
    variables.setVariable("DB_TARGET_NAME", "test_aw_edw");
    variables.setVariable("DB_OPS_NAME", "test_aw_ops");
  }

  private static void loadHopEnvironmentFile(Path envFile, Variables variables) {
    if (envFile == null || !Files.isRegularFile(envFile)) {
      return;
    }
    try {
      String json = Files.readString(envFile);
      String[] chunks = json.split("\\{");
      for (String chunk : chunks) {
        String name = jsonStringField(chunk, "name");
        String value = jsonStringField(chunk, "value");
        if (name != null && value != null && !name.equals("variables")) {
          variables.setVariable(name, value);
        }
      }
    } catch (Exception e) {
      System.err.println("Could not parse environment file " + envFile + ": " + e.getMessage());
    }
  }

  private static String jsonStringField(String chunk, String field) {
    String needle = "\"" + field + "\"";
    int idx = chunk.indexOf(needle);
    if (idx < 0) {
      return null;
    }
    int colon = chunk.indexOf(':', idx + needle.length());
    if (colon < 0) {
      return null;
    }
    int quote = chunk.indexOf('"', colon + 1);
    if (quote < 0) {
      return null;
    }
    int end = chunk.indexOf('"', quote + 1);
    if (end < 0) {
      return null;
    }
    return chunk.substring(quote + 1, end);
  }
}
