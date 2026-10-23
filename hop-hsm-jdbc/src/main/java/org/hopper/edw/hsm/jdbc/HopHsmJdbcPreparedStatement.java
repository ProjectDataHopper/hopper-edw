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
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Prepared statement. Bound values are sent with the SQL and are not written into the URL. */
public class HopHsmJdbcPreparedStatement extends HopHsmJdbcStatement implements PreparedStatement {

  private final String sql;
  private final List<Bound> parameters = new ArrayList<>();

  HopHsmJdbcPreparedStatement(HopHsmJdbcConnection connection, String sql) {
    super(connection);
    this.sql = sql;
  }

  private record Bound(int type, String text) {}

  private void bind(int parameterIndex, int type, String text) throws SQLException {
    if (parameterIndex < 1) {
      throw new SQLException("Parameter index starts at 1");
    }
    while (parameters.size() < parameterIndex) {
      parameters.add(null);
    }
    parameters.set(parameterIndex - 1, new Bound(type, text));
  }

  @Override
  public ResultSet executeQuery() throws SQLException {
    List<String> values = new ArrayList<>();
    List<Integer> types = new ArrayList<>();
    for (Bound bound : parameters) {
      values.add(bound != null ? bound.text : null);
      types.add(bound != null ? bound.type : Types.VARCHAR);
    }
    return executeQuery(sql, values, types);
  }

  @Override
  public int executeUpdate() throws SQLException {
    throw new SQLFeatureNotSupportedException("hop-hsm is read-only");
  }

  @Override
  public boolean execute() throws SQLException {
    executeQuery();
    return true;
  }

  @Override
  public void setNull(int parameterIndex, int sqlType) throws SQLException {
    bind(parameterIndex, sqlType, null);
  }

  @Override
  public void setBoolean(int parameterIndex, boolean x) throws SQLException {
    bind(parameterIndex, Types.BOOLEAN, Boolean.toString(x));
  }

  @Override
  public void setByte(int parameterIndex, byte x) throws SQLException {
    bind(parameterIndex, Types.TINYINT, Byte.toString(x));
  }

  @Override
  public void setShort(int parameterIndex, short x) throws SQLException {
    bind(parameterIndex, Types.SMALLINT, Short.toString(x));
  }

  @Override
  public void setInt(int parameterIndex, int x) throws SQLException {
    bind(parameterIndex, Types.INTEGER, Integer.toString(x));
  }

  @Override
  public void setLong(int parameterIndex, long x) throws SQLException {
    bind(parameterIndex, Types.BIGINT, Long.toString(x));
  }

  @Override
  public void setFloat(int parameterIndex, float x) throws SQLException {
    bind(parameterIndex, Types.FLOAT, Float.toString(x));
  }

  @Override
  public void setDouble(int parameterIndex, double x) throws SQLException {
    bind(parameterIndex, Types.DOUBLE, Double.toString(x));
  }

  @Override
  public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
    bind(parameterIndex, Types.DECIMAL, x != null ? x.toPlainString() : null);
  }

  @Override
  public void setString(int parameterIndex, String x) throws SQLException {
    bind(parameterIndex, Types.VARCHAR, x);
  }

  @Override
  public void setBytes(int parameterIndex, byte[] x) throws SQLException {
    bind(parameterIndex, Types.VARBINARY, x != null ? new String(x, java.nio.charset.StandardCharsets.UTF_8) : null);
  }

  @Override
  public void setDate(int parameterIndex, Date x) throws SQLException {
    bind(parameterIndex, Types.DATE, x != null ? Long.toString(x.getTime()) : null);
  }

  @Override
  public void setTime(int parameterIndex, Time x) throws SQLException {
    bind(parameterIndex, Types.TIME, x != null ? Long.toString(x.getTime()) : null);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
    bind(parameterIndex, Types.TIMESTAMP, x != null ? Long.toString(x.getTime()) : null);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
    throw new SQLFeatureNotSupportedException("Bound parameters not supported");
  }

  @Override
  @Deprecated
  public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
    throw new SQLFeatureNotSupportedException("Bound parameters not supported");
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
    throw new SQLFeatureNotSupportedException("Bound parameters not supported");
  }

  @Override
  public void clearParameters() {
    parameters.clear();
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
    bind(parameterIndex, targetSqlType, x != null ? String.valueOf(x) : null);
  }

  @Override
  public void setObject(int parameterIndex, Object x) throws SQLException {
    if (x == null) {
      bind(parameterIndex, Types.VARCHAR, null);
    } else if (x instanceof Integer n) {
      setInt(parameterIndex, n);
    } else if (x instanceof Long n) {
      setLong(parameterIndex, n);
    } else if (x instanceof BigDecimal n) {
      setBigDecimal(parameterIndex, n);
    } else if (x instanceof Timestamp n) {
      setTimestamp(parameterIndex, n);
    } else {
      setString(parameterIndex, String.valueOf(x));
    }
  }

  @Override
  public void addBatch() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, int length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException("Bound parameters not supported");
  }

  @Override
  public void setRef(int parameterIndex, Ref x) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBlob(int parameterIndex, Blob x) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setClob(int parameterIndex, Clob x) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setArray(int parameterIndex, Array x) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public ResultSetMetaData getMetaData() {
    return null;
  }

  @Override
  public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
    setDate(parameterIndex, x);
  }

  @Override
  public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
    setTime(parameterIndex, x);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
    setTimestamp(parameterIndex, x);
  }

  @Override
  public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
    setNull(parameterIndex, sqlType);
  }

  @Override
  public void setURL(int parameterIndex, URL x) throws SQLException {
    throw new SQLFeatureNotSupportedException("Bound parameters not supported");
  }

  @Override
  public ParameterMetaData getParameterMetaData() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setRowId(int parameterIndex, RowId x) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNString(int parameterIndex, String value) throws SQLException {
    setString(parameterIndex, value);
  }

  @Override
  public void setNCharacterStream(int parameterIndex, Reader value, long length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNClob(int parameterIndex, NClob value) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBlob(int parameterIndex, InputStream inputStream, long length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength)
      throws SQLException {
    setObject(parameterIndex, x, targetSqlType);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, long length)
      throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setClob(int parameterIndex, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public void setNClob(int parameterIndex, Reader reader) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }
}
