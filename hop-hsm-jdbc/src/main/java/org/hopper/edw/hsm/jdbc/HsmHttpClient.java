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

  private final HopHsmJdbcDriver.ParsedUrl parsed;
  private final String basicAuthHeader;

  HsmHttpClient(HopHsmJdbcDriver.ParsedUrl parsed) {
    this.parsed = parsed;
    String user = parsed.user();
    if (parsed.authType() == HsmAuthType.BASIC && user != null && !user.isEmpty()) {
      String token =
          Base64.getEncoder()
              .encodeToString(
                  (user + ":" + (parsed.password() != null ? parsed.password() : ""))
                      .getBytes(StandardCharsets.UTF_8));
      this.basicAuthHeader = "Basic " + token;
    } else {
      this.basicAuthHeader = null;
    }
  }

  Map<String, Object> call(Map<String, String> params) throws SQLException {
    return call(params, true);
  }

  private Map<String, Object> call(Map<String, String> params, boolean retryOnUnauthorized)
      throws SQLException {
    try {
      String body = HsmJson.encodeForm(params);
      HttpURLConnection conn = (HttpURLConnection) new URL(parsed.endpointUrl()).openConnection();
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setConnectTimeout(parsed.connectTimeoutMs());
      conn.setReadTimeout(parsed.readTimeoutMs());
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
      conn.setRequestProperty("Accept", "application/json");
      applyAuthorization(conn);
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      conn.setFixedLengthStreamingMode(bytes.length);
      try (OutputStream out = conn.getOutputStream()) {
        out.write(bytes);
      }
      int status = conn.getResponseCode();
      if (status == 401 && retryOnUnauthorized && parsed.authType() == HsmAuthType.OAUTH2) {
        HsmOAuth2Tokens.invalidate(parsed);
        return call(params, false);
      }
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

  private void applyAuthorization(HttpURLConnection conn) throws SQLException {
    switch (parsed.authType()) {
      case BEARER -> {
        String token = firstNonEmpty(parsed.accessToken(), parsed.password());
        if (token == null || token.isEmpty()) {
          throw new SQLException("bearer auth requires accessToken or password");
        }
        conn.setRequestProperty("Authorization", "Bearer " + token);
      }
      case OAUTH2 -> {
        String token = HsmOAuth2Tokens.accessToken(parsed);
        conn.setRequestProperty("Authorization", "Bearer " + token);
      }
      case BASIC -> {
        if (basicAuthHeader != null) {
          conn.setRequestProperty("Authorization", basicAuthHeader);
        }
      }
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

  private static String firstNonEmpty(String a, String b) {
    return a != null && !a.isEmpty() ? a : b;
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
