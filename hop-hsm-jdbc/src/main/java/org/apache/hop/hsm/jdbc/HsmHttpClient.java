/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.hsm.jdbc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal HTTP client for the hop-hsm sourceModelData servlet (JDK only). */
final class HsmHttpClient {

  private final String endpointUrl;
  private final String basicAuthHeader;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;

  HsmHttpClient(
      String endpointUrl, String user, String password, int connectTimeoutMs, int readTimeoutMs) {
    this.endpointUrl = endpointUrl;
    if (user != null && !user.isEmpty()) {
      String token =
          Base64.getEncoder()
              .encodeToString(
                  (user + ":" + (password != null ? password : ""))
                      .getBytes(StandardCharsets.UTF_8));
      this.basicAuthHeader = "Basic " + token;
    } else {
      this.basicAuthHeader = null;
    }
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
  }

  Map<String, Object> call(Map<String, String> params) throws SQLException {
    try {
      String body = HsmJson.encodeForm(params);
      HttpURLConnection conn = (HttpURLConnection) new URL(endpointUrl).openConnection();
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setConnectTimeout(connectTimeoutMs);
      conn.setReadTimeout(readTimeoutMs);
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
      conn.setRequestProperty("Accept", "application/json");
      if (basicAuthHeader != null) {
        conn.setRequestProperty("Authorization", basicAuthHeader);
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      conn.setFixedLengthStreamingMode(bytes.length);
      try (OutputStream out = conn.getOutputStream()) {
        out.write(bytes);
      }
      int status = conn.getResponseCode();
      InputStream in =
          status >= 400
              ? (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream())
              : conn.getInputStream();
      String json = readAll(in);
      Map<String, Object> map = HsmJson.asObject(HsmJson.parse(json));
      if (map == null) {
        throw new SQLException("Invalid JSON response from hop-hsm server (HTTP " + status + ")");
      }
      if (!HsmJson.bool(map, "ok", false)) {
        String err = HsmJson.str(map, "error");
        throw new SQLException(
            err != null ? err : ("hop-hsm request failed (HTTP " + status + ")"));
      }
      if (status >= 400) {
        throw new SQLException("hop-hsm HTTP " + status + ": " + HsmJson.str(map, "error"));
      }
      return map;
    } catch (SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new SQLException("hop-hsm HTTP call failed: " + e.getMessage(), e);
    }
  }

  Map<String, Object> ping() throws SQLException {
    Map<String, String> params = new LinkedHashMap<>();
    params.put(HsmProtocol.PARAM_ACTION, HsmProtocol.ACTION_PING);
    return call(params);
  }

  Map<String, Object> schemas() throws SQLException {
    Map<String, String> params = new LinkedHashMap<>();
    params.put(HsmProtocol.PARAM_ACTION, HsmProtocol.ACTION_SCHEMAS);
    return call(params);
  }

  Map<String, Object> query(String schema, String sql, int rowLimit) throws SQLException {
    Map<String, String> params = new LinkedHashMap<>();
    params.put(HsmProtocol.PARAM_ACTION, HsmProtocol.ACTION_QUERY);
    if (schema != null && !schema.isEmpty()) {
      params.put(HsmProtocol.PARAM_SCHEMA, schema);
    }
    params.put(HsmProtocol.PARAM_SQL, sql);
    if (rowLimit > 0) {
      params.put(HsmProtocol.PARAM_ROW_LIMIT, Integer.toString(rowLimit));
    }
    return call(params);
  }

  Map<String, Object> tables(String schema) throws SQLException {
    Map<String, String> params = new LinkedHashMap<>();
    params.put(HsmProtocol.PARAM_ACTION, HsmProtocol.ACTION_TABLES);
    if (schema != null && !schema.isEmpty()) {
      params.put(HsmProtocol.PARAM_SCHEMA, schema);
    }
    return call(params);
  }

  Map<String, Object> columns(String schema, String table) throws SQLException {
    Map<String, String> params = new LinkedHashMap<>();
    params.put(HsmProtocol.PARAM_ACTION, HsmProtocol.ACTION_COLUMNS);
    if (schema != null && !schema.isEmpty()) {
      params.put(HsmProtocol.PARAM_SCHEMA, schema);
    }
    if (table != null && !table.isEmpty()) {
      params.put(HsmProtocol.PARAM_TABLE, table);
    }
    return call(params);
  }

  private static String readAll(InputStream in) throws Exception {
    if (in == null) {
      return "";
    }
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    while ((n = in.read(chunk)) >= 0) {
      buf.write(chunk, 0, n);
    }
    return buf.toString(StandardCharsets.UTF_8.name());
  }
}
