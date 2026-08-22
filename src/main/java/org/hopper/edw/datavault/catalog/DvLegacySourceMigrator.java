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
package org.hopper.edw.datavault.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvSourceDeliveryType;
import org.hopper.edw.datavault.metadata.DvSourceType;
import org.hopper.edw.datavault.metadata.IDvSource;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.json.JsonMetadataProvider;

/** One-time migration of legacy {@code data-vault-source} Hop metadata JSON into the catalog. */
public final class DvLegacySourceMigrator {

  private DvLegacySourceMigrator() {}

  /**
   * CLI entry point for one-time migration. Args: {@code [projectHome] [catalogConnection]}.
   * Invoked from {@code integration-tests/tests/catalog/migrate-sources.sh}.
   */
  public static void main(String[] args) throws Exception {
    String projectHome = args.length > 0 ? args[0] : "project";
    String catalogConnection = args.length > 1 ? args[1] : "local-catalog";
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", projectHome);

    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, variables, PluginRegistry.getInstance());

    JsonMetadataProvider metadataProvider =
        new JsonMetadataProvider(null, projectHome + "/metadata", variables);
    RecordDefinitionRegistry.getInstance().invalidate();

    int migrated =
        migrateFromMetadataFolder(
            catalogConnection, projectHome + "/metadata", variables, metadataProvider);
    System.out.println(
        "DvLegacySourceMigrator: migrated " + migrated + " Data Vault sources to catalog");
  }

  public static int migrateFromMetadataFolder(
      String catalogConnectionName,
      String metadataBaseFolder,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    Path sourceDir = Path.of(metadataBaseFolder, "data-vault-source");
    if (!Files.isDirectory(sourceDir)) {
      return 0;
    }

    int migrated = 0;
    try (Stream<Path> paths = Files.list(sourceDir)) {
      for (Path file : paths.sorted().toList()) {
        if (!Files.isRegularFile(file) || !file.toString().endsWith(".json")) {
          continue;
        }
        DataVaultSource source = loadSource(file);
        if (source == null || Utils.isEmpty(source.getName())) {
          continue;
        }
        DvSourceCatalogService.upsertSource(
            source, catalogConnectionName, variables, metadataProvider);
        migrated++;
      }
    } catch (Exception e) {
      throw new HopException("Unable to migrate legacy Data Vault sources from " + sourceDir, e);
    }

    return migrated;
  }

  private static DataVaultSource loadSource(Path file) throws HopException {
    try (Reader reader = Files.newBufferedReader(file)) {
      JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
      DataVaultSource source = new DataVaultSource();
      source.setName(getString(root, "name"));
      source.setSourceIndicator(getString(root, "sourceIndicator"));
      source.setSourceIndicatorField(getString(root, "sourceIndicatorField"));
      source.setGroup(getString(root, "group"));
      source.setDeliveryType(parseDeliveryType(getString(root, "deliveryType")));
      source.setSource(parseDvSource(root.getAsJsonObject("source")));
      return source;
    } catch (Exception e) {
      throw new HopException("Unable to load legacy Data Vault source from " + file, e);
    }
  }

  private static IDvSource parseDvSource(JsonObject sourceObject) {
    if (sourceObject == null || sourceObject.size() == 0) {
      return new DvDatabaseSource();
    }
    for (Map.Entry<String, JsonElement> entry : sourceObject.entrySet()) {
      DvSourceType sourceType = parseSourceType(entry.getKey());
      if (sourceType == DvSourceType.DATABASE && entry.getValue().isJsonObject()) {
        return parseDatabaseSource(entry.getValue().getAsJsonObject());
      }
    }
    return new DvDatabaseSource();
  }

  private static DvDatabaseSource parseDatabaseSource(JsonObject databaseObject) {
    DvDatabaseSource databaseSource = new DvDatabaseSource();
    databaseSource.setDatabaseName(getString(databaseObject, "databaseName"));
    databaseSource.setSchemaName(getString(databaseObject, "schemaName"));
    databaseSource.setTableName(getString(databaseObject, "tableName"));
    databaseSource.setDescription(getString(databaseObject, "description"));
    databaseSource.setFields(parseFields(databaseObject.getAsJsonArray("field")));
    return databaseSource;
  }

  private static List<SourceField> parseFields(JsonArray fieldArray) {
    List<SourceField> fields = new ArrayList<>();
    if (fieldArray == null) {
      return fields;
    }
    for (JsonElement element : fieldArray) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject fieldObject = element.getAsJsonObject();
      SourceField field = new SourceField(getString(fieldObject, "name"));
      field.setDescription(getString(fieldObject, "description"));
      field.setSourceDataType(getString(fieldObject, "sourceDataType"));
      field.setLength(getString(fieldObject, "length"));
      field.setPrecision(getString(fieldObject, "precision"));
      field.setHopType(getInt(fieldObject, "hopType"));
      fields.add(field);
    }
    return fields;
  }

  private static String getString(JsonObject object, String name) {
    if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
      return null;
    }
    return object.get(name).getAsString();
  }

  private static int getInt(JsonObject object, String name) {
    if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
      return 0;
    }
    return object.get(name).getAsInt();
  }

  private static DvSourceDeliveryType parseDeliveryType(String raw) {
    if (Utils.isEmpty(raw)) {
      return DvSourceDeliveryType.CHANGES_ONLY;
    }
    try {
      return DvSourceDeliveryType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      return DvSourceDeliveryType.CHANGES_ONLY;
    }
  }

  private static DvSourceType parseSourceType(String raw) {
    if (Utils.isEmpty(raw)) {
      return DvSourceType.DATABASE;
    }
    try {
      return DvSourceType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      return DvSourceType.DATABASE;
    }
  }
}
