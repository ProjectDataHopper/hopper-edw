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
package org.hopper.edw.datavault.virtualization.jdbc;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.service.SourceModelService;

/** Read-only JDBC connection bound to a loaded {@link SourceModel} or local metadata services. */
public class HopSourceModelJdbcConnection implements Connection {

  private final SourceModel defaultModel;
  private String defaultSchema;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final int defaultRowLimit;
  private final Map<String, SourceModel> loadedModelsBySchema = new ConcurrentHashMap<>();
  private boolean closed;
  private boolean autoCommit = true;

  public HopSourceModelJdbcConnection(
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int defaultRowLimit) {
    this(
        model,
        model != null ? model.getName() : null,
        variables,
        metadataProvider,
        defaultRowLimit);
  }

  public HopSourceModelJdbcConnection(
      SourceModel defaultModel,
      String defaultSchema,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      int defaultRowLimit) {
    this.defaultModel = defaultModel;
    this.defaultSchema = defaultSchema;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.defaultRowLimit = Math.max(0, defaultRowLimit);
    if (defaultModel != null && defaultSchema != null) {
      this.loadedModelsBySchema.put(defaultSchema.trim().toLowerCase(Locale.ROOT), defaultModel);
    }
    if (defaultModel != null && !Utils.isEmpty(defaultModel.getName())) {
      this.loadedModelsBySchema.put(
          defaultModel.getName().trim().toLowerCase(Locale.ROOT), defaultModel);
    }
  }

  SourceModel model() {
    if (defaultModel != null) {
      return defaultModel;
    }
    if (!Utils.isEmpty(defaultSchema)) {
      return getModelForSchema(defaultSchema);
    }
    return null;
  }

  public SourceModel getModelForSchema(String schemaName) {
    if (Utils.isEmpty(schemaName)) {
      return model();
    }
    String key = schemaName.trim().toLowerCase(Locale.ROOT);
    SourceModel cached = loadedModelsBySchema.get(key);
    if (cached != null) {
      return cached;
    }
    if (defaultModel != null
        && ((defaultSchema != null && defaultSchema.equalsIgnoreCase(schemaName.trim()))
            || (!Utils.isEmpty(defaultModel.getName())
                && defaultModel.getName().equalsIgnoreCase(schemaName.trim())))) {
      loadedModelsBySchema.put(key, defaultModel);
      return defaultModel;
    }
    if (metadataProvider != null) {
      try {
        IHopMetadataSerializer<SourceModelService> serializer =
            metadataProvider.getSerializer(SourceModelService.class);
        SourceModelService service = serializer.load(schemaName.trim());
        if (service != null && !Utils.isEmpty(service.getModelFilename())) {
          String filename =
              HopSourceModelJdbcDriver.resolveModelFilename(
                  service.getModelFilename(), variables, metadataProvider);
          SourceModel loaded = SourceModelLoadSupport.load(filename, variables, metadataProvider);
          if (Utils.isEmpty(loaded.getName())) {
            loaded.setName(service.getName());
          }
          loadedModelsBySchema.put(key, loaded);
          return loaded;
        }
      } catch (Exception ignored) {
        // schema not found as service
      }
    }
    return null;
  }

  public List<String> listAvailableSchemas() {
    Set<String> schemas = new LinkedHashSet<>();
    if (!Utils.isEmpty(defaultSchema)) {
      schemas.add(defaultSchema);
    }
    if (defaultModel != null && !Utils.isEmpty(defaultModel.getName())) {
      schemas.add(defaultModel.getName());
    }
    if (metadataProvider != null) {
      try {
        IHopMetadataSerializer<SourceModelService> serializer =
            metadataProvider.getSerializer(SourceModelService.class);
        for (SourceModelService s : serializer.loadAll()) {
          if (s.isEnabled()) {
            schemas.add(s.getName());
          }
        }
      } catch (Exception ignored) {
      }
    }
    return new ArrayList<>(schemas);
  }

  IVariables variables() {
    return variables;
  }

  IHopMetadataProvider metadataProvider() {
    return metadataProvider;
  }

  int defaultRowLimit() {
    return defaultRowLimit;
  }

  @Override
  public Statement createStatement() throws SQLException {
    checkOpen();
    return new HopSourceModelJdbcStatement(this);
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    checkOpen();
    return new HopSourceModelJdbcPreparedStatement(this, sql);
  }

  @Override
  public void close() {
    closed = true;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  void checkOpen() throws SQLException {
    if (closed) {
      throw new SQLException("Connection is closed");
    }
  }

  @Override
  public DatabaseMetaData getMetaData() {
    return new HopSourceModelJdbcDatabaseMetaData(this);
  }

  @Override
  public boolean getAutoCommit() {
    return autoCommit;
  }

  @Override
  public void setAutoCommit(boolean autoCommit) {
    this.autoCommit = autoCommit;
  }

  @Override
  public void commit() {
    // read-only
  }

  @Override
  public void rollback() {
    // read-only
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {
    if (!readOnly) {
      throw new SQLException("hop-hsm connections are read-only");
    }
  }

  @Override
  public String getCatalog() {
    // Flat namespace for SQL tools (DBeaver resolves bare names against active catalog).
    return null;
  }

  @Override
  public void setCatalog(String catalog) {
    // ignore
  }

  @Override
  public int getTransactionIsolation() {
    return Connection.TRANSACTION_NONE;
  }

  @Override
  public void setTransactionIsolation(int level) {
    // ignore
  }

  @Override
  public SQLWarning getWarnings() {
    return null;
  }

  @Override
  public void clearWarnings() {}

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return createStatement();
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return prepareStatement(sql);
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException {
    throw new SQLFeatureNotSupportedException("CallableStatement not supported");
  }

  @Override
  public String nativeSQL(String sql) {
    return sql;
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setHoldability(int holdability) {}

  @Override
  public int getHoldability() {
    return 0;
  }

  @Override
  public Savepoint setSavepoint() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void rollback(Savepoint savepoint) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Statement createStatement(
      int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    return createStatement();
  }

  @Override
  public PreparedStatement prepareStatement(
      String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
    return prepareStatement(sql);
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public CallableStatement prepareCall(
      String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
    return prepareStatement(sql);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
    return prepareStatement(sql);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
    return prepareStatement(sql);
  }

  @Override
  public Clob createClob() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Blob createBlob() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public NClob createNClob() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean isValid(int timeout) {
    return !closed;
  }

  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException {}

  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException {}

  @Override
  public String getClientInfo(String name) {
    return null;
  }

  @Override
  public Properties getClientInfo() {
    return new Properties();
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setSchema(String schema) {
    this.defaultSchema = schema;
  }

  @Override
  public String getSchema() {
    return defaultSchema;
  }

  @Override
  public void abort(Executor executor) {
    closed = true;
  }

  @Override
  public void setNetworkTimeout(Executor executor, int milliseconds) {}

  @Override
  public int getNetworkTimeout() {
    return 0;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface.isInstance(this)) {
      return iface.cast(this);
    }
    throw new SQLException("Not a wrapper for " + iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return iface.isInstance(this);
  }
}
