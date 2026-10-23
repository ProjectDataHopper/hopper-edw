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
package org.hopper.edw.datavault.www;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmModelLoadSupport;
import org.hopper.edw.datavault.metadata.dimensional.service.DimensionalModelService;
import org.hopper.edw.datavault.virtualization.dimensional.DimensionalImportedKey;
import org.hopper.edw.datavault.virtualization.dimensional.DimensionalJdbcCatalog;
import org.hopper.edw.datavault.virtualization.dimensional.DimensionalJdbcColumn;
import org.hopper.edw.datavault.virtualization.dimensional.DimensionalJdbcTable;
import org.hopper.edw.datavault.virtualization.dimensional.DimensionalQueryExecutor;
import org.hopper.edw.datavault.virtualization.dimensional.DimensionalQueryParameter;
import org.hopper.edw.datavault.virtualization.dimensional.DimensionalQueryParameters;

/** JDBC metadata and query actions for one {@link DimensionalModelService}. */
public final class DimensionalModelDataRequests {

  private DimensionalModelDataRequests() {}

  public static String validate(DimensionalModelService service, String schemaName, String remoteUser) {
    if (service == null) {
      return null;
    }
    if (!service.isEnabled()) {
      return "Dimensional model service (schema) '" + schemaName + "' is disabled";
    }
    if (!service.allows(remoteUser)) {
      return "Dimensional model service (schema) '" + schemaName + "' is not available to this user";
    }
    if (Utils.isEmpty(service.getModelFilename())) {
      return "Dimensional model service (schema) '" + schemaName + "' has no model file configured";
    }
    return null;
  }

  public static List<SourceModelDataJson.SchemaInfo> listSchemas(
      IHopMetadataSerializer<DimensionalModelService> serializer, String remoteUser)
      throws HopException {
    List<SourceModelDataJson.SchemaInfo> list = new ArrayList<>();
    if (serializer == null) {
      return list;
    }
    for (String name : serializer.listObjectNames()) {
      if (Utils.isEmpty(name)) {
        continue;
      }
      DimensionalModelService service = serializer.load(name);
      if (service == null || !service.isEnabled() || !service.allows(remoteUser)) {
        continue;
      }
      String remarks =
          !Utils.isEmpty(service.getDescription())
              ? service.getDescription()
              : "Dimensional model service";
      list.add(new SourceModelDataJson.SchemaInfo(name, remarks));
    }
    return list;
  }

  public static DimensionalModelService load(
      IHopMetadataSerializer<DimensionalModelService> serializer, String name) throws HopException {
    if (serializer == null || Utils.isEmpty(name)) {
      return null;
    }
    DimensionalModelService service = serializer.load(name);
    if (service != null) {
      return service;
    }
    for (String existing : serializer.listObjectNames()) {
      if (existing != null && existing.equalsIgnoreCase(name)) {
        return serializer.load(existing);
      }
    }
    return null;
  }

  public static String tables(DimensionalModelService service, IVariables variables, IHopMetadataProvider provider)
      throws HopException {
    return SourceModelDataJson.tables(tableInfos(service, variables, provider));
  }

  public static List<SourceModelDataJson.TableInfo> tableInfos(
      DimensionalModelService service, IVariables variables, IHopMetadataProvider provider)
      throws HopException {
    DimensionalJdbcCatalog catalog = catalog(service, variables, provider);
    List<SourceModelDataJson.TableInfo> tables = new ArrayList<>();
    for (DimensionalJdbcTable table : catalog.tables()) {
      tables.add(
          new SourceModelDataJson.TableInfo(
              service.getName(), table.logicalName(), "TABLE", table.remarks()));
    }
    return tables;
  }

  public static String columns(
      DimensionalModelService service,
      IVariables variables,
      IHopMetadataProvider provider,
      String tablePattern)
      throws HopException {
    return SourceModelDataJson.columns(columnInfos(service, variables, provider, tablePattern));
  }

  public static List<SourceModelDataJson.ColumnInfo> columnInfos(
      DimensionalModelService service,
      IVariables variables,
      IHopMetadataProvider provider,
      String tablePattern)
      throws HopException {
    DimensionalJdbcCatalog catalog = catalog(service, variables, provider);
    List<SourceModelDataJson.ColumnInfo> columns = new ArrayList<>();
    for (DimensionalJdbcTable table : catalog.tables()) {
      if (!matches(tablePattern, table.logicalName())) {
        continue;
      }
      int position = 1;
      for (DimensionalJdbcColumn column : table.columns()) {
        columns.add(
            new SourceModelDataJson.ColumnInfo(
                service.getName(),
                table.logicalName(),
                column.name(),
                DimensionalJdbcCatalog.typeName(column.hopType()),
                column.sqlType(),
                position++,
                column.primaryKey()));
      }
    }
    return columns;
  }

  public static String importedKeys(
      DimensionalModelService service,
      IVariables variables,
      IHopMetadataProvider provider,
      String foreignTable,
      String parentTable)
      throws HopException {
    DimensionalJdbcCatalog catalog = catalog(service, variables, provider);
    List<SourceModelDataJson.ImportedKeyInfo> keys = new ArrayList<>();
    for (DimensionalImportedKey key : catalog.importedKeys(foreignTable, parentTable)) {
      keys.add(
          new SourceModelDataJson.ImportedKeyInfo(
              service.getName(),
              key.fkTable(),
              key.fkColumn(),
              service.getName(),
              key.pkTable(),
              key.pkColumn(),
              key.keySeq(),
              key.fkName()));
    }
    return SourceModelDataJson.importedKeys(keys);
  }

  public static String query(
      HttpServletRequest request,
      DimensionalModelService service,
      IVariables variables,
      IHopMetadataProvider provider,
      String sql,
      int requestedLimit)
      throws Exception {
    List<DimensionalQueryParameter> parameters =
        DimensionalQueryParameters.parse(
            request.getParameter(SourceModelDataProtocol.PARAM_PARAMS),
            request.getParameter(SourceModelDataProtocol.PARAM_PARAM_TYPES));
    DimensionalModel model =
        DmModelLoadSupport.loadDimensionalModel(
            variables.resolve(service.getModelFilename()), null, variables, provider);
    String clientInfo = request.getParameter(SourceModelDataProtocol.PARAM_CLIENT_INFO);
    if (Utils.isEmpty(clientInfo)) {
      clientInfo = request.getHeader("User-Agent");
    }
    DimensionalQueryExecutor.QueryResult result =
        DimensionalQueryExecutor.query(
            service,
            model,
            sql,
            requestedLimit,
            parameters,
            request.getRemoteUser(),
            clientInfo,
            request.getRemoteAddr(),
            variables,
            provider);
    return SourceModelDataJson.queryResult(result.rows(), result.truncated());
  }

  private static DimensionalJdbcCatalog catalog(
      DimensionalModelService service, IVariables variables, IHopMetadataProvider provider)
      throws HopException {
    DimensionalModel model =
        DmModelLoadSupport.loadDimensionalModel(
            variables.resolve(service.getModelFilename()), null, variables, provider);
    return DimensionalJdbcCatalog.build(model, variables, provider, service.getName());
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
}
