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
package org.hopper.edw.hsm.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** Forward-only ResultSet over materialised hop-hsm JSON rows. */
public class HopHsmJdbcResultSet implements ResultSet {

  private final Statement statement;
  private final Column[] columns;
  private final List<Object[]> rows;
  private int cursor = -1;
  private boolean closed;
  private boolean wasNull;

  record Column(String name, String typeName, int sqlType) {}

  HopHsmJdbcResultSet(Statement statement, Column[] columns, List<Object[]> rows) {
    this.statement = statement;
    this.columns = columns != null ? columns : new Column[0];
    this.rows = rows != null ? rows : List.of();
  }

  static HopHsmJdbcResultSet fromQueryResponse(Statement statement, Map<String, Object> response)
      throws SQLException {
    List<Object> cols = HsmJson.asArray(response.get("columns"));
    List<Column> columnList = new ArrayList<>();
    if (cols != null) {
      for (Object o : cols) {
        Map<String, Object> m = HsmJson.asObject(o);
        if (m == null) {
          continue;
        }
        String name = HsmJson.str(m, "n");
        String typeName = HsmJson.str(m, "t");
        int sqlType = HsmJson.integer(m, "j", Types.VARCHAR);
        columnList.add(
            new Column(name != null ? name : "c" + columnList.size(), typeName, sqlType));
      }
    }
    List<Object[]> data = new ArrayList<>();
    List<Object> rawRows = HsmJson.asArray(response.get("rows"));
    if (rawRows != null) {
      for (Object rowObj : rawRows) {
        List<Object> cells = HsmJson.asArray(rowObj);
        if (cells == null) {
          continue;
        }
        Object[] arr = new Object[columnList.size()];
        for (int i = 0; i < columnList.size(); i++) {
          arr[i] = i < cells.size() ? cells.get(i) : null;
        }
        data.add(arr);
      }
    }
    return new HopHsmJdbcResultSet(statement, columnList.toArray(new Column[0]), data);
  }

  static HopHsmJdbcResultSet of(
      Statement statement, String[] names, int[] sqlTypes, List<Object[]> rows) {
    Column[] cols = new Column[names.length];
    for (int i = 0; i < names.length; i++) {
      cols[i] = new Column(names[i], typeName(sqlTypes[i]), sqlTypes[i]);
    }
    return new HopHsmJdbcResultSet(statement, cols, rows);
  }

  private static String typeName(int sqlType) {
    return switch (sqlType) {
      case Types.BIGINT, Types.INTEGER -> "BIGINT";
      case Types.DOUBLE -> "DOUBLE";
      case Types.DECIMAL -> "DECIMAL";
      case Types.BOOLEAN -> "BOOLEAN";
      case Types.TIMESTAMP -> "TIMESTAMP";
      default -> "VARCHAR";
    };
  }

  private void checkOpen() throws SQLException {
    if (closed) {
      throw new SQLException("ResultSet is closed");
    }
  }

  private void checkRow() throws SQLException {
    checkOpen();
    if (cursor < 0 || cursor >= rows.size()) {
      throw new SQLException("No current row");
    }
  }

  private Object raw(int columnIndex) throws SQLException {
    checkRow();
    int idx = columnIndex - 1;
    if (idx < 0 || idx >= columns.length) {
      throw new SQLException("Column index out of range: " + columnIndex);
    }
    Object[] row = rows.get(cursor);
    Object v = row != null && idx < row.length ? row[idx] : null;
    wasNull = v == null;
    return v;
  }

  private int find(String label) throws SQLException {
    if (label == null) {
      throw new SQLException("Column label is null");
    }
    for (int i = 0; i < columns.length; i++) {
      if (label.equalsIgnoreCase(columns[i].name())) {
        return i + 1;
      }
    }
    throw new SQLException("Unknown column: " + label);
  }

  @Override
  public boolean next() throws SQLException {
    checkOpen();
    if (cursor + 1 < rows.size()) {
      cursor++;
      return true;
    }
    cursor = rows.size();
    return false;
  }

  @Override
  public void close() {
    closed = true;
  }

  @Override
  public boolean wasNull() {
    return wasNull;
  }

  @Override
  public String getString(int columnIndex) throws SQLException {
    Object v = raw(columnIndex);
    return v == null ? null : String.valueOf(v);
  }

  @Override
  public boolean getBoolean(int columnIndex) throws SQLException {
    Object v = raw(columnIndex);
    if (v == null) {
      return false;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof Number n) {
      return n.intValue() != 0;
    }
    return Boolean.parseBoolean(String.valueOf(v));
  }

  @Override
  public byte getByte(int columnIndex) throws SQLException {
    return (byte) getLong(columnIndex);
  }

  @Override
  public short getShort(int columnIndex) throws SQLException {
    return (short) getLong(columnIndex);
  }

  @Override
  public int getInt(int columnIndex) throws SQLException {
    return (int) getLong(columnIndex);
  }

  @Override
  public long getLong(int columnIndex) throws SQLException {
    Object v = raw(columnIndex);
    if (v == null) {
      return 0L;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    return Long.parseLong(String.valueOf(v).trim());
  }

  @Override
  public float getFloat(int columnIndex) throws SQLException {
    return (float) getDouble(columnIndex);
  }

  @Override
  public double getDouble(int columnIndex) throws SQLException {
    Object v = raw(columnIndex);
    if (v == null) {
      return 0d;
    }
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    return Double.parseDouble(String.valueOf(v).trim());
  }

  @Override
  public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
    BigDecimal bd = getBigDecimal(columnIndex);
    return bd == null ? null : bd.setScale(scale);
  }

  @Override
  public byte[] getBytes(int columnIndex) throws SQLException {
    Object v = raw(columnIndex);
    if (v == null) {
      return null;
    }
    if (v instanceof byte[] b) {
      return b;
    }
    return String.valueOf(v).getBytes();
  }

  @Override
  public Date getDate(int columnIndex) throws SQLException {
    Object v = raw(columnIndex);
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return new Date(n.longValue());
    }
    if (v instanceof Date d) {
      return d;
    }
    throw new SQLException("Cannot convert to Date: " + v);
  }

  @Override
  public Time getTime(int columnIndex) throws SQLException {
    Date d = getDate(columnIndex);
    return d == null ? null : new Time(d.getTime());
  }

  @Override
  public Timestamp getTimestamp(int columnIndex) throws SQLException {
    Object v = raw(columnIndex);
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return new Timestamp(n.longValue());
    }
    if (v instanceof Timestamp t) {
      return t;
    }
    throw new SQLException("Cannot convert to Timestamp: " + v);
  }

  @Override
  public InputStream getAsciiStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  @Deprecated
  public InputStream getUnicodeStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public InputStream getBinaryStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public String getString(String columnLabel) throws SQLException {
    return getString(find(columnLabel));
  }

  @Override
  public boolean getBoolean(String columnLabel) throws SQLException {
    return getBoolean(find(columnLabel));
  }

  @Override
  public byte getByte(String columnLabel) throws SQLException {
    return getByte(find(columnLabel));
  }

  @Override
  public short getShort(String columnLabel) throws SQLException {
    return getShort(find(columnLabel));
  }

  @Override
  public int getInt(String columnLabel) throws SQLException {
    return getInt(find(columnLabel));
  }

  @Override
  public long getLong(String columnLabel) throws SQLException {
    return getLong(find(columnLabel));
  }

  @Override
  public float getFloat(String columnLabel) throws SQLException {
    return getFloat(find(columnLabel));
  }

  @Override
  public double getDouble(String columnLabel) throws SQLException {
    return getDouble(find(columnLabel));
  }

  @Override
  public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
    return getBigDecimal(find(columnLabel), scale);
  }

  @Override
  public byte[] getBytes(String columnLabel) throws SQLException {
    return getBytes(find(columnLabel));
  }

  @Override
  public Date getDate(String columnLabel) throws SQLException {
    return getDate(find(columnLabel));
  }

  @Override
  public Time getTime(String columnLabel) throws SQLException {
    return getTime(find(columnLabel));
  }

  @Override
  public Timestamp getTimestamp(String columnLabel) throws SQLException {
    return getTimestamp(find(columnLabel));
  }

  @Override
  public InputStream getAsciiStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  @Deprecated
  public InputStream getUnicodeStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public InputStream getBinaryStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public SQLWarning getWarnings() {
    return null;
  }

  @Override
  public void clearWarnings() {}

  @Override
  public String getCursorName() {
    return null;
  }

  @Override
  public ResultSetMetaData getMetaData() {
    return new Meta(columns);
  }

  @Override
  public Object getObject(int columnIndex) throws SQLException {
    return raw(columnIndex);
  }

  @Override
  public Object getObject(String columnLabel) throws SQLException {
    return getObject(find(columnLabel));
  }

  @Override
  public int findColumn(String columnLabel) throws SQLException {
    return find(columnLabel);
  }

  @Override
  public Reader getCharacterStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Reader getCharacterStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
    Object v = raw(columnIndex);
    if (v == null) {
      return null;
    }
    if (v instanceof BigDecimal bd) {
      return bd;
    }
    if (v instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    return new BigDecimal(String.valueOf(v).trim());
  }

  @Override
  public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
    return getBigDecimal(find(columnLabel));
  }

  @Override
  public boolean isBeforeFirst() {
    return cursor < 0 && !rows.isEmpty();
  }

  @Override
  public boolean isAfterLast() {
    return !rows.isEmpty() && cursor >= rows.size();
  }

  @Override
  public boolean isFirst() {
    return cursor == 0 && !rows.isEmpty();
  }

  @Override
  public boolean isLast() {
    return !rows.isEmpty() && cursor == rows.size() - 1;
  }

  @Override
  public void beforeFirst() throws SQLException {
    checkOpen();
    cursor = -1;
  }

  @Override
  public void afterLast() throws SQLException {
    checkOpen();
    cursor = rows.size();
  }

  @Override
  public boolean first() throws SQLException {
    checkOpen();
    if (rows.isEmpty()) {
      return false;
    }
    cursor = 0;
    return true;
  }

  @Override
  public boolean last() throws SQLException {
    checkOpen();
    if (rows.isEmpty()) {
      return false;
    }
    cursor = rows.size() - 1;
    return true;
  }

  @Override
  public int getRow() {
    return cursor < 0 || cursor >= rows.size() ? 0 : cursor + 1;
  }

  @Override
  public boolean absolute(int row) throws SQLException {
    checkOpen();
    if (rows.isEmpty()) {
      return false;
    }
    int target = row >= 0 ? row - 1 : rows.size() + row;
    if (target < 0 || target >= rows.size()) {
      cursor = target < 0 ? -1 : rows.size();
      return false;
    }
    cursor = target;
    return true;
  }

  @Override
  public boolean relative(int rowsDelta) throws SQLException {
    return absolute(getRow() + rowsDelta);
  }

  @Override
  public boolean previous() throws SQLException {
    checkOpen();
    if (cursor > 0) {
      cursor--;
      return true;
    }
    cursor = -1;
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
  public int getType() {
    return ResultSet.TYPE_FORWARD_ONLY;
  }

  @Override
  public int getConcurrency() {
    return ResultSet.CONCUR_READ_ONLY;
  }

  @Override
  public boolean rowUpdated() {
    return false;
  }

  @Override
  public boolean rowInserted() {
    return false;
  }

  @Override
  public boolean rowDeleted() {
    return false;
  }

  // --- update* all unsupported ---
  private static SQLException ro() {
    return new SQLFeatureNotSupportedException("read-only");
  }

  @Override
  public void updateNull(int columnIndex) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBoolean(int columnIndex, boolean x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateByte(int columnIndex, byte x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateShort(int columnIndex, short x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateInt(int columnIndex, int x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateLong(int columnIndex, long x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateFloat(int columnIndex, float x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateDouble(int columnIndex, double x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateString(int columnIndex, String x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBytes(int columnIndex, byte[] x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateDate(int columnIndex, Date x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateTime(int columnIndex, Time x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
    throw ro();
  }

  @Override
  public void updateObject(int columnIndex, Object x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNull(String columnLabel) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBoolean(String columnLabel, boolean x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateByte(String columnLabel, byte x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateShort(String columnLabel, short x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateInt(String columnLabel, int x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateLong(String columnLabel, long x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateFloat(String columnLabel, float x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateDouble(String columnLabel, double x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateString(String columnLabel, String x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBytes(String columnLabel, byte[] x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateDate(String columnLabel, Date x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateTime(String columnLabel, Time x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, int length)
      throws SQLException {
    throw ro();
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader, int length)
      throws SQLException {
    throw ro();
  }

  @Override
  public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
    throw ro();
  }

  @Override
  public void updateObject(String columnLabel, Object x) throws SQLException {
    throw ro();
  }

  @Override
  public void insertRow() throws SQLException {
    throw ro();
  }

  @Override
  public void updateRow() throws SQLException {
    throw ro();
  }

  @Override
  public void deleteRow() throws SQLException {
    throw ro();
  }

  @Override
  public void refreshRow() {}

  @Override
  public void cancelRowUpdates() {}

  @Override
  public void moveToInsertRow() throws SQLException {
    throw ro();
  }

  @Override
  public void moveToCurrentRow() {}

  @Override
  public Statement getStatement() {
    return statement;
  }

  @Override
  public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
    return getObject(columnIndex);
  }

  @Override
  public Ref getRef(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Blob getBlob(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Clob getClob(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Array getArray(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
    return getObject(columnLabel);
  }

  @Override
  public Ref getRef(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Blob getBlob(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Clob getClob(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Array getArray(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Date getDate(int columnIndex, Calendar cal) throws SQLException {
    return getDate(columnIndex);
  }

  @Override
  public Date getDate(String columnLabel, Calendar cal) throws SQLException {
    return getDate(columnLabel);
  }

  @Override
  public Time getTime(int columnIndex, Calendar cal) throws SQLException {
    return getTime(columnIndex);
  }

  @Override
  public Time getTime(String columnLabel, Calendar cal) throws SQLException {
    return getTime(columnLabel);
  }

  @Override
  public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
    return getTimestamp(columnIndex);
  }

  @Override
  public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
    return getTimestamp(columnLabel);
  }

  @Override
  public URL getURL(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public URL getURL(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void updateRef(int columnIndex, Ref x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateRef(String columnLabel, Ref x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBlob(int columnIndex, Blob x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBlob(String columnLabel, Blob x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateClob(int columnIndex, Clob x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateClob(String columnLabel, Clob x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateArray(int columnIndex, Array x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateArray(String columnLabel, Array x) throws SQLException {
    throw ro();
  }

  @Override
  public RowId getRowId(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public RowId getRowId(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void updateRowId(int columnIndex, RowId x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateRowId(String columnLabel, RowId x) throws SQLException {
    throw ro();
  }

  @Override
  public int getHoldability() {
    return ResultSet.HOLD_CURSORS_OVER_COMMIT;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public void updateNString(int columnIndex, String nString) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNString(String columnLabel, String nString) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
    throw ro();
  }

  @Override
  public NClob getNClob(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public NClob getNClob(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public SQLXML getSQLXML(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public SQLXML getSQLXML(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
    throw ro();
  }

  @Override
  public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
    throw ro();
  }

  @Override
  public String getNString(int columnIndex) throws SQLException {
    return getString(columnIndex);
  }

  @Override
  public String getNString(String columnLabel) throws SQLException {
    return getString(columnLabel);
  }

  @Override
  public Reader getNCharacterStream(int columnIndex) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public Reader getNCharacterStream(String columnLabel) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    throw ro();
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x, long length)
      throws SQLException {
    throw ro();
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x, long length)
      throws SQLException {
    throw ro();
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader, long length)
      throws SQLException {
    throw ro();
  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream, long length)
      throws SQLException {
    throw ro();
  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream, long length)
      throws SQLException {
    throw ro();
  }

  @Override
  public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
    throw ro();
  }

  @Override
  public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
    throw ro();
  }

  @Override
  public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
    throw ro();
  }

  @Override
  public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
    throw ro();
  }

  @Override
  public void updateClob(int columnIndex, Reader reader) throws SQLException {
    throw ro();
  }

  @Override
  public void updateClob(String columnLabel, Reader reader) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNClob(int columnIndex, Reader reader) throws SQLException {
    throw ro();
  }

  @Override
  public void updateNClob(String columnLabel, Reader reader) throws SQLException {
    throw ro();
  }

  @Override
  public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
    Object v = getObject(columnIndex);
    if (v == null) {
      return null;
    }
    if (type.isInstance(v)) {
      return type.cast(v);
    }
    throw new SQLException("Cannot convert " + v.getClass() + " to " + type);
  }

  @Override
  public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
    return getObject(find(columnLabel), type);
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

  private static final class Meta implements ResultSetMetaData {
    private final Column[] columns;

    Meta(Column[] columns) {
      this.columns = columns;
    }

    @Override
    public int getColumnCount() {
      return columns.length;
    }

    @Override
    public boolean isAutoIncrement(int column) {
      return false;
    }

    @Override
    public boolean isCaseSensitive(int column) {
      return true;
    }

    @Override
    public boolean isSearchable(int column) {
      return true;
    }

    @Override
    public boolean isCurrency(int column) {
      return false;
    }

    @Override
    public int isNullable(int column) {
      return columnNullable;
    }

    @Override
    public boolean isSigned(int column) throws SQLException {
      int t = getColumnType(column);
      return t == Types.BIGINT || t == Types.DOUBLE || t == Types.DECIMAL || t == Types.INTEGER;
    }

    @Override
    public int getColumnDisplaySize(int column) {
      return 50;
    }

    @Override
    public String getColumnLabel(int column) throws SQLException {
      return getColumnName(column);
    }

    @Override
    public String getColumnName(int column) throws SQLException {
      return col(column).name();
    }

    @Override
    public String getSchemaName(int column) {
      return "source";
    }

    @Override
    public int getPrecision(int column) {
      return 0;
    }

    @Override
    public int getScale(int column) {
      return 0;
    }

    @Override
    public String getTableName(int column) {
      return "";
    }

    @Override
    public String getCatalogName(int column) {
      return "";
    }

    @Override
    public int getColumnType(int column) throws SQLException {
      return col(column).sqlType();
    }

    @Override
    public String getColumnTypeName(int column) throws SQLException {
      return col(column).typeName() != null ? col(column).typeName() : "VARCHAR";
    }

    @Override
    public boolean isReadOnly(int column) {
      return true;
    }

    @Override
    public boolean isWritable(int column) {
      return false;
    }

    @Override
    public boolean isDefinitelyWritable(int column) {
      return false;
    }

    @Override
    public String getColumnClassName(int column) throws SQLException {
      return switch (getColumnType(column)) {
        case Types.BIGINT, Types.INTEGER -> Long.class.getName();
        case Types.DOUBLE -> Double.class.getName();
        case Types.DECIMAL -> BigDecimal.class.getName();
        case Types.BOOLEAN -> Boolean.class.getName();
        case Types.TIMESTAMP -> Timestamp.class.getName();
        default -> String.class.getName();
      };
    }

    private Column col(int column) throws SQLException {
      int idx = column - 1;
      if (idx < 0 || idx >= columns.length) {
        throw new SQLException("Column index out of range: " + column);
      }
      return columns[idx];
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
}
