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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Loads and configures Hop bulk loader transforms via the plugin classloader (no compile-time
 * dependency on database-specific bulk loader modules).
 */
public final class DvBulkLoadTransformSupport {

  private static final String MYSQL_FIELD_CLASS =
      "org.apache.hop.pipeline.transforms.mysqlbulkloader.MySqlBulkLoaderMeta$Field";
  private static final String PG_MAPPING_CLASS =
      "org.apache.hop.pipeline.transforms.pgbulkloader.PGBulkLoaderMappingMeta";
  private static final String ORA_MAPPING_CLASS =
      "org.apache.hop.pipeline.transforms.orabulkloader.OraBulkLoaderMappingMeta";
  private static final String SNOWFLAKE_FIELD_CLASS =
      "org.apache.hop.pipeline.transforms.snowflake.bulkloader.SnowflakeBulkLoaderField";
  private static final String MONETDB_FIELD_CLASS =
      "org.apache.hop.pipeline.transforms.monetdbbulkloader.MonetDbBulkLoaderMeta$MonetDbField";
  private static final String VERTICA_FIELD_CLASS =
      "org.apache.hop.pipeline.transforms.vertica.bulkloader.VerticaBulkLoaderField";

  /** Hop {@code LOCATION_TYPE_USER} — user stage {@code @~/table}, no CREATE STAGE required. */
  static final int SNOWFLAKE_LOCATION_TYPE_USER = 0;

  private static final int SNOWFLAKE_DATA_TYPE_CSV = 0;
  private static final int SNOWFLAKE_ON_ERROR_ABORT = 3;
  static final String SNOWFLAKE_DEFAULT_SCHEMA = "PUBLIC";

  private DvBulkLoadTransformSupport() {}

  public static ITransformMeta loadTransformMeta(String transformPluginId) throws HopException {
    try {
      ITransformMeta meta =
          PluginRegistry.getInstance()
              .loadClass(TransformPluginType.class, transformPluginId, ITransformMeta.class);
      if (meta == null) {
        throw new HopException(
            "Bulk loader transform plugin '" + transformPluginId + "' is not installed");
      }
      meta.setDefault();
      return meta;
    } catch (Exception e) {
      throw new HopException(
          "Unable to load bulk loader transform plugin '" + transformPluginId + "'", e);
    }
  }

  public static TransformMeta newBulkLoaderTransform(
      String transformPluginId, String transformName, ITransformMeta meta) {
    return new TransformMeta(transformPluginId, transformName, meta);
  }

  public static void configureNativeBulkLoader(
      String transformPluginId,
      ITransformMeta meta,
      DvTargetLoadSupport.TargetLoadContext ctx,
      IRowMeta targetLayout,
      Set<String> excludeFields)
      throws HopException {
    if (DvBulkLoadPluginSupport.MYSQL_BULK_LOADER_ID.equals(transformPluginId)) {
      configureMySqlBulkLoader(meta, ctx, targetLayout, excludeFields);
      return;
    }
    if (DvBulkLoadPluginSupport.PG_BULK_LOADER_ID.equals(transformPluginId)) {
      configurePgBulkLoader(meta, ctx, targetLayout, excludeFields);
      return;
    }
    if (DvBulkLoadPluginSupport.ORA_BULK_LOADER_ID.equals(transformPluginId)) {
      configureOraBulkLoader(meta, ctx, targetLayout, excludeFields);
      return;
    }
    if (DvBulkLoadPluginSupport.SNOWFLAKE_BULK_LOADER_ID.equals(transformPluginId)) {
      configureSnowflakeBulkLoader(meta, ctx, targetLayout, excludeFields);
      return;
    }
    if (DvBulkLoadPluginSupport.MONETDB_BULK_LOADER_ID.equals(transformPluginId)) {
      configureMonetDbBulkLoader(meta, ctx, targetLayout, excludeFields);
      return;
    }
    if (DvBulkLoadPluginSupport.VERTICA_BULK_LOADER_ID.equals(transformPluginId)) {
      configureVerticaBulkLoader(meta, ctx, targetLayout, excludeFields);
      return;
    }
    if (DvBulkLoadPluginSupport.DORIS_BULK_LOADER_ID.equals(transformPluginId)) {
      configureDorisBulkLoader(meta, ctx, targetLayout, excludeFields);
      return;
    }
    throw new HopException("Unsupported native bulk loader transform: " + transformPluginId);
  }

  private static void configureMySqlBulkLoader(
      ITransformMeta meta,
      DvTargetLoadSupport.TargetLoadContext ctx,
      IRowMeta targetLayout,
      Set<String> excludeFields)
      throws HopException {
    invoke(meta, "setConnection", String.class, ctx.targetDbName);
    invoke(meta, "setTableName", String.class, ctx.targetTableName);
    invoke(meta, "setSchemaName", String.class, "");
    invoke(meta, "setDelimiter", String.class, ctx.config.resolveBulkLoadDelimiter(ctx.variables));
    invoke(meta, "setEnclosure", String.class, ctx.config.resolveBulkLoadEnclosure(ctx.variables));
    invoke(meta, "setEncoding", String.class, normalizeMysqlEncoding(ctx));
    invoke(meta, "setLoadCharSet", String.class, "UTF8MB4");
    invoke(meta, "setLocalFile", boolean.class, true);
    invoke(meta, "setReplacingData", boolean.class, false);
    invoke(meta, "setIgnoringErrors", boolean.class, false);
    String bulkSize = ctx.config.resolveTargetTableCommitSize(ctx.variables);
    if (!Utils.isEmpty(bulkSize)) {
      invoke(meta, "setBulkSize", String.class, bulkSize);
    }
    setMySqlFields(meta, targetLayout, excludeFields);
  }

  private static String normalizeMysqlEncoding(DvTargetLoadSupport.TargetLoadContext ctx) {
    String encoding = ctx.config.resolveBulkLoadEncoding(ctx.variables);
    if (Utils.isEmpty(encoding)) {
      return "UTF8";
    }
    return encoding.replace("-", "").toUpperCase();
  }

  private static void setMySqlFields(
      ITransformMeta meta, IRowMeta targetLayout, Set<String> excludeFields) throws HopException {
    List<Object> fields = new ArrayList<>();
    if (targetLayout != null) {
      for (IValueMeta vm : targetLayout.getValueMetaList()) {
        String name = vm.getName();
        if (shouldExcludeField(name, excludeFields)) {
          continue;
        }
        fields.add(createMySqlField(meta, name, name));
      }
    }
    invoke(meta, "setFields", List.class, fields);
  }

  private static Object createMySqlField(ITransformMeta meta, String streamName, String tableName)
      throws HopException {
    try {
      Class<?> fieldClass =
          Class.forName(MYSQL_FIELD_CLASS, true, meta.getClass().getClassLoader());
      Object field = fieldClass.getDeclaredConstructor().newInstance();
      invokeObject(field, "setFieldStream", String.class, streamName);
      invokeObject(field, "setFieldTable", String.class, tableName);
      invokeObject(field, "setFieldFormatTypeWithCode", String.class, "OK");
      return field;
    } catch (Exception e) {
      throw new HopException("Unable to create MySQL bulk loader field mapping", e);
    }
  }

  private static void configureOraBulkLoader(
      ITransformMeta meta,
      DvTargetLoadSupport.TargetLoadContext ctx,
      IRowMeta targetLayout,
      Set<String> excludeFields)
      throws HopException {
    invoke(meta, "setConnection", String.class, ctx.targetDbName);
    invoke(meta, "setTableName", String.class, ctx.targetTableName);
    invoke(meta, "setSchemaName", String.class, "");
    invoke(meta, "setEncoding", String.class, ctx.config.resolveBulkLoadEncoding(ctx.variables));
    invoke(meta, "setLoadAction", String.class, "APPEND");
    invoke(meta, "setLoadMethod", String.class, "AUTO_CONCURRENT");
    invoke(meta, "setDirectPath", boolean.class, false);
    invoke(meta, "setFailOnError", boolean.class, true);
    setOraMappings(meta, targetLayout, excludeFields);
  }

  /**
   * Snowflake schema for {@code COPY INTO schema.table}. Prefers the connection preferred schema;
   * defaults to {@code PUBLIC} when empty.
   */
  public static String resolveSnowflakeTargetSchema(
      DatabaseMeta targetDatabase, IVariables variables) {
    String schema = "";
    if (targetDatabase != null) {
      schema = Const.NVL(targetDatabase.getPreferredSchemaName(), "");
    }
    if (variables != null) {
      schema = variables.resolve(schema);
    }
    if (Utils.isEmpty(schema)) {
      return SNOWFLAKE_DEFAULT_SCHEMA;
    }
    return schema;
  }

  private static void configureSnowflakeBulkLoader(
      ITransformMeta meta,
      DvTargetLoadSupport.TargetLoadContext ctx,
      IRowMeta targetLayout,
      Set<String> excludeFields)
      throws HopException {
    invoke(meta, "setConnection", String.class, ctx.targetDbName);
    invoke(meta, "setTargetTable", String.class, ctx.targetTableName);
    invoke(
        meta,
        "setTargetSchema",
        String.class,
        resolveSnowflakeTargetSchema(ctx.targetDatabaseMeta, ctx.variables));
    invoke(
        meta,
        "setWorkDirectory",
        String.class,
        ctx.config.resolveBulkLoadStagingFolder(ctx.variables, ctx.modelName));
    invoke(meta, "setRemoveFiles", boolean.class, true);
    invoke(meta, "setTrimWhitespace", boolean.class, false);
    // User stage (@~/table). Internal stage requires a stage name that we do not generate.
    invoke(meta, "setLocationTypeById", int.class, SNOWFLAKE_LOCATION_TYPE_USER);
    invoke(meta, "setDataTypeById", int.class, SNOWFLAKE_DATA_TYPE_CSV);
    invoke(meta, "setOnErrorById", int.class, SNOWFLAKE_ON_ERROR_ABORT);
    setSnowflakeFields(meta, targetLayout, excludeFields);
  }

  private static void configureMonetDbBulkLoader(
      ITransformMeta meta,
      DvTargetLoadSupport.TargetLoadContext ctx,
      IRowMeta targetLayout,
      Set<String> excludeFields)
      throws HopException {
    invoke(meta, "setDbConnectionName", String.class, ctx.targetDbName);
    invoke(meta, "setTableName", String.class, ctx.targetTableName);
    invoke(meta, "setSchemaName", String.class, "");
    invoke(
        meta,
        "setFieldSeparator",
        String.class,
        ctx.config.resolveBulkLoadDelimiter(ctx.variables));
    invoke(
        meta,
        "setFieldEnclosure",
        String.class,
        ctx.config.resolveBulkLoadEnclosure(ctx.variables));
    invoke(meta, "setEncoding", String.class, ctx.config.resolveBulkLoadEncoding(ctx.variables));
    invoke(meta, "setTruncate", boolean.class, false);
    setMonetDbFields(meta, targetLayout, excludeFields);
  }

  private static void configureVerticaBulkLoader(
      ITransformMeta meta,
      DvTargetLoadSupport.TargetLoadContext ctx,
      IRowMeta targetLayout,
      Set<String> excludeFields)
      throws HopException {
    invoke(meta, "setConnection", String.class, ctx.targetDbName);
    invoke(meta, "setTableName", String.class, ctx.targetTableName);
    invoke(meta, "setSchemaName", String.class, "");
    invoke(meta, "setDirect", boolean.class, true);
    invoke(meta, "setAbortOnError", boolean.class, true);
    invoke(meta, "setTruncateTable", boolean.class, false);
    invoke(meta, "setSpecifyFields", boolean.class, true);
    setVerticaFields(meta, targetLayout, excludeFields);
  }

  private static void configureDorisBulkLoader(
      ITransformMeta meta,
      DvTargetLoadSupport.TargetLoadContext ctx,
      IRowMeta targetLayout,
      Set<String> excludeFields)
      throws HopException {
    if (ctx.targetDatabaseMeta == null) {
      throw new HopException("Target database metadata is required for Doris bulk loading");
    }
    invoke(meta, "setFeHost", String.class, ctx.targetDatabaseMeta.getHostname());
    invoke(meta, "setFeHttpPort", String.class, ctx.targetDatabaseMeta.getPort());
    invoke(meta, "setDatabaseName", String.class, ctx.targetDatabaseMeta.getDatabaseName());
    invoke(meta, "setTableName", String.class, ctx.targetTableName);
    invoke(meta, "setLoginUser", String.class, ctx.targetDatabaseMeta.getUsername());
    invoke(meta, "setLoginPassword", String.class, ctx.targetDatabaseMeta.getPassword());
    invoke(meta, "setFormat", String.class, "csv");
    invoke(
        meta,
        "setColumnDelimiter",
        String.class,
        ctx.config.resolveBulkLoadDelimiter(ctx.variables));
    invoke(meta, "setLineDelimiter", String.class, "\\n");
    invoke(meta, "setHeaders", List.class, List.of());
  }

  private static void setOraMappings(
      ITransformMeta meta, IRowMeta targetLayout, Set<String> excludeFields) throws HopException {
    List<Object> mappings = new ArrayList<>();
    if (targetLayout != null) {
      for (IValueMeta vm : targetLayout.getValueMetaList()) {
        String name = vm.getName();
        if (shouldExcludeField(name, excludeFields)) {
          continue;
        }
        mappings.add(createOraMapping(meta, name, name, resolveOraDateMask(vm)));
      }
    }
    invoke(meta, "setMappings", List.class, mappings);
  }

  private static void setSnowflakeFields(
      ITransformMeta meta, IRowMeta targetLayout, Set<String> excludeFields) throws HopException {
    List<Object> fields = new ArrayList<>();
    if (targetLayout != null) {
      for (IValueMeta vm : targetLayout.getValueMetaList()) {
        String name = vm.getName();
        if (shouldExcludeField(name, excludeFields)) {
          continue;
        }
        fields.add(createSnowflakeField(meta, name, name));
      }
    }
    invoke(meta, "setSnowflakeBulkLoaderFields", List.class, fields);
  }

  private static void setMonetDbFields(
      ITransformMeta meta, IRowMeta targetLayout, Set<String> excludeFields) throws HopException {
    List<Object> fields = new ArrayList<>();
    if (targetLayout != null) {
      for (IValueMeta vm : targetLayout.getValueMetaList()) {
        String name = vm.getName();
        if (shouldExcludeField(name, excludeFields)) {
          continue;
        }
        fields.add(createMonetDbField(meta, name, name));
      }
    }
    invoke(meta, "setFields", List.class, fields);
  }

  private static void setVerticaFields(
      ITransformMeta meta, IRowMeta targetLayout, Set<String> excludeFields) throws HopException {
    List<Object> fields = new ArrayList<>();
    if (targetLayout != null) {
      for (IValueMeta vm : targetLayout.getValueMetaList()) {
        String name = vm.getName();
        if (shouldExcludeField(name, excludeFields)) {
          continue;
        }
        fields.add(createVerticaField(meta, name, name));
      }
    }
    invoke(meta, "setFields", List.class, fields);
  }

  private static String resolveOraDateMask(IValueMeta valueMeta) {
    if (valueMeta == null) {
      return "DATE";
    }
    return switch (valueMeta.getType()) {
      case IValueMeta.TYPE_TIMESTAMP -> "DATETIME";
      default -> "DATE";
    };
  }

  private static Object createOraMapping(
      ITransformMeta meta, String tableField, String streamField, String dateMask)
      throws HopException {
    try {
      Class<?> mappingClass =
          Class.forName(ORA_MAPPING_CLASS, true, meta.getClass().getClassLoader());
      Constructor<?> constructor =
          mappingClass.getConstructor(String.class, String.class, String.class);
      return constructor.newInstance(tableField, streamField, dateMask);
    } catch (Exception e) {
      throw new HopException("Unable to create Oracle bulk loader field mapping", e);
    }
  }

  private static Object createSnowflakeField(
      ITransformMeta meta, String tableField, String streamField) throws HopException {
    try {
      Class<?> fieldClass =
          Class.forName(SNOWFLAKE_FIELD_CLASS, true, meta.getClass().getClassLoader());
      Constructor<?> constructor = fieldClass.getConstructor(String.class, String.class);
      return constructor.newInstance(tableField, streamField);
    } catch (Exception e) {
      throw new HopException("Unable to create Snowflake bulk loader field mapping", e);
    }
  }

  private static Object createMonetDbField(
      ITransformMeta meta, String tableField, String streamField) throws HopException {
    try {
      Class<?> fieldClass =
          Class.forName(MONETDB_FIELD_CLASS, true, meta.getClass().getClassLoader());
      Constructor<?> constructor =
          fieldClass.getConstructor(String.class, String.class, boolean.class);
      return constructor.newInstance(streamField, tableField, true);
    } catch (Exception e) {
      throw new HopException("Unable to create MonetDB bulk loader field mapping", e);
    }
  }

  private static Object createVerticaField(
      ITransformMeta meta, String tableField, String streamField) throws HopException {
    try {
      Class<?> fieldClass =
          Class.forName(VERTICA_FIELD_CLASS, true, meta.getClass().getClassLoader());
      Constructor<?> constructor = fieldClass.getConstructor(String.class, String.class);
      return constructor.newInstance(streamField, tableField);
    } catch (Exception e) {
      throw new HopException("Unable to create Vertica bulk loader field mapping", e);
    }
  }

  public static void configurePgBulkLoaderFromColumns(
      ITransformMeta meta,
      IDvTargetLoadConfiguration config,
      IVariables variables,
      String targetDbName,
      String targetTableName,
      List<String> columnNames)
      throws HopException {
    invoke(meta, "setConnection", String.class, targetDbName);
    invoke(meta, "setTableName", String.class, targetTableName);
    invoke(meta, "setSchemaName", String.class, "");
    invoke(meta, "setLoadAction", String.class, "INSERT");
    invoke(meta, "setDelimiter", String.class, config.resolveBulkLoadDelimiter(variables));
    invoke(meta, "setEnclosure", String.class, config.resolveBulkLoadEnclosure(variables));
    invoke(meta, "setStopOnError", boolean.class, true);
    List<Object> mappings = new ArrayList<>();
    if (columnNames != null) {
      for (String columnName : columnNames) {
        if (!Utils.isEmpty(columnName)) {
          mappings.add(createPgMapping(meta, columnName, columnName, "PASS THROUGH"));
        }
      }
    }
    invoke(meta, "setMappings", List.class, mappings);
  }

  private static void configurePgBulkLoader(
      ITransformMeta meta,
      DvTargetLoadSupport.TargetLoadContext ctx,
      IRowMeta targetLayout,
      Set<String> excludeFields)
      throws HopException {
    invoke(meta, "setConnection", String.class, ctx.targetDbName);
    invoke(meta, "setTableName", String.class, ctx.targetTableName);
    invoke(meta, "setSchemaName", String.class, "");
    invoke(meta, "setLoadAction", String.class, "INSERT");
    invoke(meta, "setDelimiter", String.class, ctx.config.resolveBulkLoadDelimiter(ctx.variables));
    invoke(meta, "setEnclosure", String.class, ctx.config.resolveBulkLoadEnclosure(ctx.variables));
    invoke(meta, "setStopOnError", boolean.class, true);
    setPgMappings(meta, targetLayout, excludeFields);
  }

  private static void setPgMappings(
      ITransformMeta meta, IRowMeta targetLayout, Set<String> excludeFields) throws HopException {
    List<Object> mappings = new ArrayList<>();
    if (targetLayout != null) {
      for (IValueMeta vm : targetLayout.getValueMetaList()) {
        String name = vm.getName();
        if (shouldExcludeField(name, excludeFields)) {
          continue;
        }
        mappings.add(createPgMapping(meta, name, name, resolvePgDateMask(vm)));
      }
    }
    invoke(meta, "setMappings", List.class, mappings);
  }

  private static String resolvePgDateMask(IValueMeta valueMeta) {
    if (valueMeta == null) {
      return "PASS THROUGH";
    }
    return switch (valueMeta.getType()) {
      case IValueMeta.TYPE_DATE -> "DATE";
      case IValueMeta.TYPE_TIMESTAMP -> "DATETIME";
      default -> "PASS THROUGH";
    };
  }

  private static Object createPgMapping(
      ITransformMeta meta, String tableField, String streamField, String dateMask)
      throws HopException {
    try {
      Class<?> mappingClass =
          Class.forName(PG_MAPPING_CLASS, true, meta.getClass().getClassLoader());
      Constructor<?> constructor =
          mappingClass.getConstructor(String.class, String.class, String.class);
      return constructor.newInstance(tableField, streamField, dateMask);
    } catch (Exception e) {
      throw new HopException("Unable to create PostgreSQL bulk loader field mapping", e);
    }
  }

  private static boolean shouldExcludeField(String fieldName, Set<String> excludeFields) {
    if (Utils.isEmpty(fieldName) || excludeFields == null || excludeFields.isEmpty()) {
      return false;
    }
    for (String exclude : excludeFields) {
      if (exclude != null && exclude.equalsIgnoreCase(fieldName)) {
        return true;
      }
    }
    return false;
  }

  private static void invoke(ITransformMeta meta, String methodName, Class<?> argType, Object arg)
      throws HopException {
    invokeObject(meta, methodName, argType, arg);
  }

  private static void invokeObject(Object target, String methodName, Class<?> argType, Object arg)
      throws HopException {
    try {
      Method method = target.getClass().getMethod(methodName, argType);
      method.invoke(target, arg);
    } catch (Exception e) {
      throw new HopException("Unable to configure bulk loader transform (" + methodName + ")", e);
    }
  }
}
