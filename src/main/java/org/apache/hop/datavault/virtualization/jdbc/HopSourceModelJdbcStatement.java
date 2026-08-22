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
package org.apache.hop.datavault.virtualization.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.datavault.virtualization.execute.SourceModelSqlExecutor;
import org.apache.hop.datavault.virtualization.sql.SourceModelSqlEngine;
import org.apache.hop.datavault.virtualization.sql.SourceModelSqlOptions;
import org.apache.hop.datavault.virtualization.sql.SourceModelSqlPlan;

/** Read-only JDBC statement that plans free SQL and materialises rows via the local Hop engine. */
public class HopSourceModelJdbcStatement implements Statement {

  private final HopSourceModelJdbcConnection connection;
  private boolean closed;
  private int maxRows;
  private ResultSet currentResultSet;
  private int updateCount = -1;

  public HopSourceModelJdbcStatement(HopSourceModelJdbcConnection connection) {
    this.connection = connection;
    this.maxRows = connection.defaultRowLimit();
  }

  @Override
  public ResultSet executeQuery(String sql) throws SQLException {
    connection.checkOpen();
    checkOpen();
    try {
      int limit = maxRows > 0 ? maxRows : connection.defaultRowLimit();
      List<RowMetaAndData> rows;
      if (limit > 0) {
        rows =
            SourceModelSqlExecutor.preview(
                connection.model(),
                sql,
                connection.variables(),
                connection.metadataProvider(),
                limit);
      } else {
        SourceModelSqlPlan plan =
            SourceModelSqlEngine.plan(
                connection.model(),
                sql,
                connection.variables(),
                connection.metadataProvider(),
                SourceModelSqlOptions.defaults());
        rows = SourceModelSqlExecutor.execute(plan, connection.variables(), Integer.MAX_VALUE);
      }
      currentResultSet = new HopSourceModelJdbcResultSet(this, rows);
      updateCount = -1;
      return currentResultSet;
    } catch (Exception e) {
      throw new SQLException("Query failed: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean execute(String sql) throws SQLException {
    executeQuery(sql);
    return true;
  }

  @Override
  public int executeUpdate(String sql) throws SQLException {
    throw new SQLFeatureNotSupportedException("hop-hsm is read-only (no DML)");
  }

  @Override
  public void close() throws SQLException {
    closed = true;
    if (currentResultSet != null) {
      currentResultSet.close();
      currentResultSet = null;
    }
  }

  void checkOpen() throws SQLException {
    if (closed) {
      throw new SQLException("Statement is closed");
    }
  }

  @Override
  public int getMaxFieldSize() {
    return 0;
  }

  @Override
  public void setMaxFieldSize(int max) {}

  @Override
  public int getMaxRows() {
    return maxRows;
  }

  @Override
  public void setMaxRows(int max) {
    this.maxRows = Math.max(0, max);
  }

  @Override
  public void setEscapeProcessing(boolean enable) {}

  @Override
  public int getQueryTimeout() {
    return 0;
  }

  @Override
  public void setQueryTimeout(int seconds) {}

  @Override
  public void cancel() {}

  @Override
  public SQLWarning getWarnings() {
    return null;
  }

  @Override
  public void clearWarnings() {}

  @Override
  public void setCursorName(String name) {}

  @Override
  public ResultSet getResultSet() {
    return currentResultSet;
  }

  @Override
  public int getUpdateCount() {
    return updateCount;
  }

  @Override
  public boolean getMoreResults() {
    return false;
  }

  @Override
  public void setFetchDirection(int direction) {}

  @Override
  public int getFetchDirection() {
    return ResultSet.FETCH_FORWARD;
  }

  @Override
  public void setFetchSize(int rows) {}

  @Override
  public int getFetchSize() {
    return 0;
  }

  @Override
  public int getResultSetConcurrency() {
    return ResultSet.CONCUR_READ_ONLY;
  }

  @Override
  public int getResultSetType() {
    return ResultSet.TYPE_FORWARD_ONLY;
  }

  @Override
  public void addBatch(String sql) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void clearBatch() {}

  @Override
  public int[] executeBatch() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Connection getConnection() {
    return connection;
  }

  @Override
  public boolean getMoreResults(int current) {
    return false;
  }

  @Override
  public ResultSet getGeneratedKeys() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int executeUpdate(String sql, String[] columnNames) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
    return execute(sql);
  }

  @Override
  public boolean execute(String sql, int[] columnIndexes) throws SQLException {
    return execute(sql);
  }

  @Override
  public boolean execute(String sql, String[] columnNames) throws SQLException {
    return execute(sql);
  }

  @Override
  public int getResultSetHoldability() {
    return 0;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public void setPoolable(boolean poolable) {}

  @Override
  public boolean isPoolable() {
    return false;
  }

  @Override
  public void closeOnCompletion() {}

  @Override
  public boolean isCloseOnCompletion() {
    return false;
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
