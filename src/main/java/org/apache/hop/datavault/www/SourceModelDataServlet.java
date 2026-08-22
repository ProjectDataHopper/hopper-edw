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
package org.apache.hop.datavault.www;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.annotations.HopServerServlet;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.apache.hop.datavault.metadata.sourcemodel.SourcePipeline;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQuery;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.service.SourceModelService;
import org.apache.hop.datavault.virtualization.execute.SourceModelSqlExecutor;
import org.apache.hop.datavault.virtualization.jdbc.HopSourceModelJdbcResultSet;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.json.JsonMetadataProvider;
import org.apache.hop.metadata.serializer.multi.MultiMetadataProvider;
import org.apache.hop.metadata.util.HopMetadataUtil;
import org.apache.hop.www.BaseHttpServlet;
import org.apache.hop.www.IHopServerPlugin;
import org.apache.hop.www.PipelineMap;

/**
 * Hop Server servlet for free SQL over {@link SourceModelService} metadata.
 *
 * <p>Each <strong>Source model service</strong> is a JDBC <em>schema</em>. Tables inside a service
 * are logical canvas names (including JSON / pipeline feeds as VIEW).
 *
 * <p>Path: {@code /hop/sourceModelData}
 *
 * <pre>
 * jdbc:hop-hsm://user:pass@host:8182
 * jdbc:hop-hsm://user:pass@host:8182/crm
 * jdbc:hop-hsm://user:pass@host:8182?schema=crm
 * </pre>
 *
 * <p>Parameters: {@code schema} / {@code modelName} (service name), {@code action}
 * (schemas|tables|columns|query|ping), {@code sql}, {@code rowLimit}, {@code table}.
 */
@HopServerServlet(id = "sourceModelData", name = "Source model free SQL data (hop-hsm JDBC)")
public class SourceModelDataServlet extends BaseHttpServlet implements IHopServerPlugin {

  @Serial private static final long serialVersionUID = 1L;

  public static final String CONTEXT_PATH = SourceModelDataProtocol.CONTEXT_PATH;

  public SourceModelDataServlet() {}

  public SourceModelDataServlet(PipelineMap pipelineMap) {
    super(pipelineMap);
  }

  @Override
  public String getContextPath() {
    return CONTEXT_PATH;
  }

  @Override
  public String getService() {
    return CONTEXT_PATH + " (" + toString() + ")";
  }

  @Override
  public String toString() {
    return "Source model data";
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    doGet(request, response);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (isJettyMode() && !request.getContextPath().startsWith(CONTEXT_PATH)) {
      return;
    }

    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(SourceModelDataProtocol.CONTENT_TYPE_JSON);

    try {
      IVariables variables = pipelineMap.getHopServerConfig().getVariables();
      MultiMetadataProvider metadataProvider = buildMetadataProvider(variables);
      IHopMetadataSerializer<SourceModelService> serializer =
          metadataProvider.getSerializer(SourceModelService.class);

      String action =
          ConstNvl(
                  firstNonEmpty(request.getParameter(SourceModelDataProtocol.PARAM_ACTION)),
                  SourceModelDataProtocol.ACTION_QUERY)
              .toLowerCase(Locale.ROOT);

      // Server-level actions (no schema required)
      if (SourceModelDataProtocol.ACTION_SCHEMAS.equals(action)) {
        writeJson(
            response,
            HttpServletResponse.SC_OK,
            SourceModelDataJson.schemas(listSchemas(serializer)));
        return;
      }
      if (SourceModelDataProtocol.ACTION_PING.equals(action)) {
        String schema = resolveSchemaName(request);
        writeJson(response, HttpServletResponse.SC_OK, SourceModelDataJson.ping(schema));
        return;
      }

      // Schema-scoped actions: schema = Source model service name (alias modelName)
      String schemaName = resolveSchemaName(request);
      if (Utils.isEmpty(schemaName)) {
        // tables/columns without schema → all enabled services
        if (SourceModelDataProtocol.ACTION_TABLES.equals(action)
            || SourceModelDataProtocol.ACTION_COLUMNS.equals(action)) {
          handleMultiSchemaMetadata(
              request, response, action, serializer, variables, metadataProvider);
          return;
        }
        writeJson(
            response,
            HttpServletResponse.SC_BAD_REQUEST,
            SourceModelDataJson.error(
                "Missing schema (or modelName): JDBC schema = Source model service metadata name. "
                    + "Set the connection schema, URL path, or ?schema=name"));
        return;
      }

      SourceModelService service = loadService(serializer, schemaName);
      if (service == null) {
        writeJson(
            response,
            HttpServletResponse.SC_NOT_FOUND,
            SourceModelDataJson.error(
                "Source model service (schema) '"
                    + schemaName
                    + "' not found. Create metadata type 'Source model service' on the Hop Server project."));
        return;
      }
      String err = validateService(service, schemaName);
      if (err != null) {
        writeJson(response, HttpServletResponse.SC_FORBIDDEN, SourceModelDataJson.error(err));
        return;
      }

      SourceModel model =
          SourceModelLoadSupport.load(
              variables.resolve(service.getModelFilename()), variables, metadataProvider);

      switch (action) {
        case SourceModelDataProtocol.ACTION_TABLES -> {
          if (!service.isAllowSchemaMetadata()) {
            writeJson(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                SourceModelDataJson.error("Schema metadata is disabled for this service"));
            return;
          }
          writeJson(
              response,
              HttpServletResponse.SC_OK,
              SourceModelDataJson.tables(listTables(schemaName, model)));
        }
        case SourceModelDataProtocol.ACTION_COLUMNS -> {
          if (!service.isAllowSchemaMetadata()) {
            writeJson(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                SourceModelDataJson.error("Schema metadata is disabled for this service"));
            return;
          }
          String tablePattern =
              firstNonEmpty(request.getParameter(SourceModelDataProtocol.PARAM_TABLE));
          writeJson(
              response,
              HttpServletResponse.SC_OK,
              SourceModelDataJson.columns(listColumns(schemaName, model, tablePattern)));
        }
        case SourceModelDataProtocol.ACTION_QUERY -> {
          String sql = resolveSql(request);
          if (Utils.isEmpty(sql)) {
            writeJson(
                response,
                HttpServletResponse.SC_BAD_REQUEST,
                SourceModelDataJson.error("Missing sql parameter or request body"));
            return;
          }
          int requested =
              parseInt(request.getParameter(SourceModelDataProtocol.PARAM_ROW_LIMIT), 0);
          int limit = service.resolveRowLimit(requested);
          List<RowMetaAndData> rows;
          boolean truncated = false;
          // Pass service name so Calcite accepts DBeaver-qualified SQL: FROM crm.order_header
          String schemaAlias = schemaName;
          if (limit > 0) {
            int fetch = limit < Integer.MAX_VALUE ? limit + 1 : limit;
            rows =
                SourceModelSqlExecutor.preview(
                    model, sql, variables, metadataProvider, fetch, schemaAlias);
            if (rows.size() > limit) {
              truncated = true;
              rows = new ArrayList<>(rows.subList(0, limit));
            }
          } else {
            rows =
                SourceModelSqlExecutor.preview(
                    model, sql, variables, metadataProvider, Integer.MAX_VALUE, schemaAlias);
          }
          writeJson(
              response,
              HttpServletResponse.SC_OK,
              SourceModelDataJson.queryResult(rows, truncated));
        }
        default ->
            writeJson(
                response,
                HttpServletResponse.SC_BAD_REQUEST,
                SourceModelDataJson.error(
                    "Unknown action '"
                        + action
                        + "'. Use query, tables, columns, schemas, or ping."));
      }
    } catch (HopException e) {
      logError("Source model data request failed", e);
      writeJson(
          response,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          SourceModelDataJson.error(e.getMessage()));
    } catch (Exception e) {
      logError("Source model data request failed", e);
      writeJson(
          response,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          SourceModelDataJson.error(
              e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
    }
  }

  private void handleMultiSchemaMetadata(
      HttpServletRequest request,
      HttpServletResponse response,
      String action,
      IHopMetadataSerializer<SourceModelService> serializer,
      IVariables variables,
      MultiMetadataProvider metadataProvider)
      throws Exception {
    List<SourceModelDataJson.SchemaInfo> services = listSchemas(serializer);
    if (SourceModelDataProtocol.ACTION_TABLES.equals(action)) {
      List<SourceModelDataJson.TableInfo> all = new ArrayList<>();
      for (SourceModelDataJson.SchemaInfo s : services) {
        SourceModelService service = loadService(serializer, s.name());
        if (service == null || validateService(service, s.name()) != null) {
          continue;
        }
        if (!service.isAllowSchemaMetadata()) {
          continue;
        }
        SourceModel model =
            SourceModelLoadSupport.load(
                variables.resolve(service.getModelFilename()), variables, metadataProvider);
        all.addAll(listTables(s.name(), model));
      }
      writeJson(response, HttpServletResponse.SC_OK, SourceModelDataJson.tables(all));
      return;
    }
    // columns for all schemas
    String tablePattern = firstNonEmpty(request.getParameter(SourceModelDataProtocol.PARAM_TABLE));
    List<SourceModelDataJson.ColumnInfo> all = new ArrayList<>();
    for (SourceModelDataJson.SchemaInfo s : services) {
      SourceModelService service = loadService(serializer, s.name());
      if (service == null || validateService(service, s.name()) != null) {
        continue;
      }
      if (!service.isAllowSchemaMetadata()) {
        continue;
      }
      SourceModel model =
          SourceModelLoadSupport.load(
              variables.resolve(service.getModelFilename()), variables, metadataProvider);
      all.addAll(listColumns(s.name(), model, tablePattern));
    }
    writeJson(response, HttpServletResponse.SC_OK, SourceModelDataJson.columns(all));
  }

  private static List<SourceModelDataJson.SchemaInfo> listSchemas(
      IHopMetadataSerializer<SourceModelService> serializer) throws HopException {
    List<SourceModelDataJson.SchemaInfo> list = new ArrayList<>();
    for (String name : serializer.listObjectNames()) {
      if (Utils.isEmpty(name)) {
        continue;
      }
      SourceModelService service = serializer.load(name);
      if (service == null || !service.isEnabled()) {
        continue;
      }
      String remarks =
          !Utils.isEmpty(service.getDescription())
              ? service.getDescription()
              : "Source model service";
      list.add(new SourceModelDataJson.SchemaInfo(name, remarks));
    }
    return list;
  }

  private static SourceModelService loadService(
      IHopMetadataSerializer<SourceModelService> serializer, String name) throws HopException {
    SourceModelService service = serializer.load(name);
    if (service != null) {
      return service;
    }
    // Case-insensitive fallback
    for (String n : serializer.listObjectNames()) {
      if (n != null && n.equalsIgnoreCase(name)) {
        return serializer.load(n);
      }
    }
    return null;
  }

  private static String validateService(SourceModelService service, String schemaName) {
    if (!service.isEnabled()) {
      return "Source model service (schema) '" + schemaName + "' is disabled";
    }
    if (Utils.isEmpty(service.getModelFilename())) {
      return "Source model service (schema) '" + schemaName + "' has no model file configured";
    }
    return null;
  }

  /** schema param preferred; modelName kept as alias for older clients. */
  private static String resolveSchemaName(HttpServletRequest request) {
    String schema = firstNonEmpty(request.getParameter(SourceModelDataProtocol.PARAM_SCHEMA));
    if (!Utils.isEmpty(schema)) {
      return schema;
    }
    return firstNonEmpty(request.getParameter(SourceModelDataProtocol.PARAM_MODEL_NAME));
  }

  private MultiMetadataProvider buildMetadataProvider(IVariables variables) {
    MultiMetadataProvider metadataProvider =
        new MultiMetadataProvider(Encr.getEncoder(), new ArrayList<>(), variables);
    metadataProvider.getProviders().add(HopMetadataUtil.getStandardHopMetadataProvider(variables));
    String metadataFolder = pipelineMap.getHopServerConfig().getMetadataFolder();
    if (StringUtils.isNotEmpty(metadataFolder)) {
      metadataProvider
          .getProviders()
          .add(new JsonMetadataProvider(Encr.getEncoder(), metadataFolder, variables));
    }
    return metadataProvider;
  }

  private static String resolveSql(HttpServletRequest request) throws IOException {
    String sql = firstNonEmpty(request.getParameter(SourceModelDataProtocol.PARAM_SQL));
    if (!Utils.isEmpty(sql)) {
      return sql;
    }
    String contentType = request.getContentType();
    if (contentType != null
        && (contentType.startsWith("text/plain") || contentType.startsWith("application/sql"))) {
      return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }
    if ("POST".equalsIgnoreCase(request.getMethod())
        && (contentType == null || contentType.isBlank())) {
      String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!body.isBlank() && !body.contains("=")) {
        return body.trim();
      }
    }
    return sql;
  }

  private static List<SourceModelDataJson.TableInfo> listTables(String schema, SourceModel model) {
    List<SourceModelDataJson.TableInfo> list = new ArrayList<>();
    if (model == null) {
      return list;
    }
    for (SourceTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        list.add(
            new SourceModelDataJson.TableInfo(schema, table.getName(), "TABLE", "Source table"));
      }
    }
    for (SourceQuery query : model.getQueries()) {
      if (query != null && !Utils.isEmpty(query.getName())) {
        list.add(
            new SourceModelDataJson.TableInfo(
                schema, query.getName(), "VIEW", "Source query (virtual table)"));
      }
    }
    for (SourceJson json : model.getJsonSources()) {
      if (json != null && !Utils.isEmpty(json.getName())) {
        list.add(
            new SourceModelDataJson.TableInfo(
                schema, json.getName(), "VIEW", "Source JSON extraction (logical table)"));
      }
    }
    for (SourcePipeline pipeline : model.getPipelineSources()) {
      if (pipeline != null && !Utils.isEmpty(pipeline.getName())) {
        list.add(
            new SourceModelDataJson.TableInfo(
                schema, pipeline.getName(), "VIEW", "Source pipeline feed (logical table)"));
      }
    }
    return list;
  }

  private static List<SourceModelDataJson.ColumnInfo> listColumns(
      String schema, SourceModel model, String tablePattern) {
    List<SourceModelDataJson.ColumnInfo> list = new ArrayList<>();
    if (model == null) {
      return list;
    }
    for (SourceTable table : model.getTables()) {
      if (table == null
          || Utils.isEmpty(table.getName())
          || !matches(tablePattern, table.getName())) {
        continue;
      }
      int pos = 1;
      for (SourceColumn col : table.getColumns()) {
        if (col == null || Utils.isEmpty(col.getName())) {
          continue;
        }
        int hop = col.getHopType() > 0 ? col.getHopType() : IValueMeta.TYPE_STRING;
        list.add(
            new SourceModelDataJson.ColumnInfo(
                schema,
                table.getName(),
                col.getName(),
                HopSourceModelJdbcResultSet.hopToTypeName(hop),
                HopSourceModelJdbcResultSet.hopToSqlType(hop),
                pos++,
                col.isPrimaryKey()));
      }
    }
    for (SourceQuery query : model.getQueries()) {
      if (query == null
          || Utils.isEmpty(query.getName())
          || !matches(tablePattern, query.getName())) {
        continue;
      }
      int pos = 1;
      for (SourceQueryColumn col : query.getColumns()) {
        if (col == null || Utils.isEmpty(col.getColumnName())) {
          continue;
        }
        list.add(
            new SourceModelDataJson.ColumnInfo(
                schema,
                query.getName(),
                col.resolveAlias(),
                "VARCHAR",
                java.sql.Types.VARCHAR,
                pos++,
                false));
      }
    }
    return list;
  }

  private static boolean matches(String pattern, String name) {
    if (Utils.isEmpty(pattern) || "%".equals(pattern) || "*".equals(pattern)) {
      return true;
    }
    if (name == null) {
      return false;
    }
    String regex =
        pattern.replace(".", "\\.").replace("%", ".*").replace("_", ".").replace("*", ".*");
    return name.toLowerCase(Locale.ROOT).matches(regex.toLowerCase(Locale.ROOT));
  }

  private static void writeJson(HttpServletResponse response, int status, String json)
      throws IOException {
    response.setStatus(status);
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    response.setContentLength(bytes.length);
    try (OutputStream out = response.getOutputStream()) {
      out.write(bytes);
    }
  }

  private static String firstNonEmpty(String value) {
    return Utils.isEmpty(value) ? null : value.trim();
  }

  private static String ConstNvl(String value, String def) {
    return value != null && !value.isEmpty() ? value : def;
  }

  private static int parseInt(String text, int defaultValue) {
    if (Utils.isEmpty(text)) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
