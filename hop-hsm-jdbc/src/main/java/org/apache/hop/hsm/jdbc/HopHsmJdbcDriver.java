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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Thin, zero-dependency JDBC driver for Hop Server source model free SQL.
 *
 * <p>Each <strong>Source model service</strong> on the server is a JDBC <em>schema</em>.
 *
 * <p>URL forms:
 *
 * <ul>
 *   <li>{@code jdbc:hop-hsm://user:pass@host:8182} — list all services as schemas
 *   <li>{@code jdbc:hop-hsm://user:pass@host:8182/crm} — default schema {@code crm}
 *   <li>{@code jdbc:hop-hsm://user:pass@host:8182?schema=crm}
 *   <li>{@code jdbc:hop-hsm:https://user:pass@host:8443/hop/sourceModelData?schema=crm}
 * </ul>
 *
 * <p>Properties: {@code user}, {@code password}, {@code schema} (or legacy {@code modelName}),
 * {@code rowLimit}, {@code connectTimeout}, {@code readTimeout} (ms).
 */
public class HopHsmJdbcDriver implements Driver {

  static {
    try {
      DriverManager.registerDriver(new HopHsmJdbcDriver());
    } catch (SQLException ignored) {
      // race ok
    }
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (!acceptsURL(url)) {
      return null;
    }
    ParsedUrl parsed = parse(url, info != null ? info : new Properties());
    HsmHttpClient http =
        new HsmHttpClient(
            parsed.endpointUrl(),
            parsed.user(),
            parsed.password(),
            parsed.connectTimeoutMs(),
            parsed.readTimeoutMs());
    http.ping();
    return new HopHsmJdbcConnection(http, parsed);
  }

  @Override
  public boolean acceptsURL(String url) {
    if (url == null
        || !url.regionMatches(true, 0, HsmProtocol.JDBC_PREFIX, 0, HsmProtocol.JDBC_PREFIX.length())) {
      return false;
    }
    String rest = url.substring(HsmProtocol.JDBC_PREFIX.length());
    return rest.startsWith("//")
        || rest.regionMatches(true, 0, "http://", 0, 7)
        || rest.regionMatches(true, 0, "https://", 0, 8);
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
    return new DriverPropertyInfo[] {
      prop("schema", "Default Source model service name (JDBC schema)", false),
      prop("modelName", "Alias for schema (legacy)", false),
      prop("user", "Hop Server username", false),
      prop("password", "Hop Server password", false),
      prop("rowLimit", "Default max rows per query", false),
      prop("connectTimeout", "HTTP connect timeout ms", false),
      prop("readTimeout", "HTTP read timeout ms", false),
    };
  }

  private static DriverPropertyInfo prop(String name, String desc, boolean required) {
    DriverPropertyInfo p = new DriverPropertyInfo(name, null);
    p.description = desc;
    p.required = required;
    return p;
  }

  @Override
  public int getMajorVersion() {
    return 1;
  }

  @Override
  public int getMinorVersion() {
    return 0;
  }

  @Override
  public boolean jdbcCompliant() {
    return false;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException();
  }

  record ParsedUrl(
      String endpointUrl,
      String defaultSchema,
      String user,
      String password,
      int rowLimit,
      int connectTimeoutMs,
      int readTimeoutMs) {}

  static ParsedUrl parse(String url, Properties info) throws SQLException {
    String rest = url.substring(HsmProtocol.JDBC_PREFIX.length());
    String scheme = "http";
    if (rest.regionMatches(true, 0, "https://", 0, 8)) {
      scheme = "https";
      rest = rest.substring(8);
    } else if (rest.regionMatches(true, 0, "http://", 0, 7)) {
      rest = rest.substring(7);
    } else if (rest.startsWith("//")) {
      rest = rest.substring(2);
    } else {
      throw new SQLException("Invalid hop-hsm remote URL: " + url);
    }

    String user = info.getProperty("user");
    String password = info.getProperty("password");
    String authorityAndPath = rest;
    int at = rest.lastIndexOf('@');
    if (at >= 0) {
      String userInfo = rest.substring(0, at);
      authorityAndPath = rest.substring(at + 1);
      int colon = userInfo.indexOf(':');
      if (colon >= 0) {
        if (isEmpty(user)) {
          user = urlDecode(userInfo.substring(0, colon));
        }
        if (isEmpty(password)) {
          password = urlDecode(userInfo.substring(colon + 1));
        }
      } else if (isEmpty(user)) {
        user = urlDecode(userInfo);
      }
    }

    String hostPort;
    String pathAndQuery;
    int slash = authorityAndPath.indexOf('/');
    if (slash >= 0) {
      hostPort = authorityAndPath.substring(0, slash);
      pathAndQuery = authorityAndPath.substring(slash);
    } else {
      hostPort = authorityAndPath;
      pathAndQuery = "";
    }

    String path = pathAndQuery;
    String query = "";
    int q = pathAndQuery.indexOf('?');
    if (q >= 0) {
      path = pathAndQuery.substring(0, q);
      query = pathAndQuery.substring(q + 1);
    }

    Map<String, String> qp = parseQuery(query);
    String defaultSchema =
        first(
            info.getProperty("schema"),
            first(info.getProperty("modelName"), first(qp.get("schema"), qp.get("modelName"))));

    // Path handling:
    //   empty or /  → default servlet path, no schema from path
    //   /hop/sourceModelData → servlet path
    //   /crm → default servlet + schema crm
    //   /hop/sourceModelData/crm → not expected; treat full path as servlet if starts with /hop/
    String servletPath = HsmProtocol.DEFAULT_PATH;
    if (path != null && !path.isEmpty() && !"/".equals(path)) {
      String normalized = path.startsWith("/") ? path : "/" + path;
      if (normalized.equals(HsmProtocol.DEFAULT_PATH)
          || normalized.startsWith(HsmProtocol.DEFAULT_PATH + "/")) {
        servletPath = HsmProtocol.DEFAULT_PATH;
        // optional trailing schema: /hop/sourceModelData/crm
        if (normalized.length() > HsmProtocol.DEFAULT_PATH.length() + 1) {
          String trailing = normalized.substring(HsmProtocol.DEFAULT_PATH.length() + 1);
          int nextSlash = trailing.indexOf('/');
          String seg = nextSlash >= 0 ? trailing.substring(0, nextSlash) : trailing;
          if (!isEmpty(seg) && isEmpty(defaultSchema)) {
            defaultSchema = urlDecode(seg);
          }
        }
      } else {
        // Single path segment = schema name, e.g. jdbc:hop-hsm://host:8182/crm
        String seg = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        int nextSlash = seg.indexOf('/');
        if (nextSlash >= 0) {
          seg = seg.substring(0, nextSlash);
        }
        if (!isEmpty(seg) && isEmpty(defaultSchema)) {
          defaultSchema = urlDecode(seg);
        }
      }
    }

    int rowLimit = parseInt(first(info.getProperty("rowLimit"), qp.get("rowLimit")), 0);
    int connectTimeout =
        parseInt(first(info.getProperty("connectTimeout"), qp.get("connectTimeout")), 15_000);
    int readTimeout =
        parseInt(first(info.getProperty("readTimeout"), qp.get("readTimeout")), 300_000);

    String endpoint = scheme + "://" + hostPort + servletPath;
    return new ParsedUrl(
        endpoint, defaultSchema, user, password, rowLimit, connectTimeout, readTimeout);
  }

  private static Map<String, String> parseQuery(String query) {
    Map<String, String> map = new LinkedHashMap<>();
    if (query == null || query.isEmpty()) {
      return map;
    }
    for (String part : query.split("&")) {
      int eq = part.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      map.put(urlDecode(part.substring(0, eq)), urlDecode(part.substring(eq + 1)));
    }
    return map;
  }

  private static String urlDecode(String s) {
    try {
      return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      return s;
    }
  }

  private static boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }

  private static String first(String a, String b) {
    return !isEmpty(a) ? a : b;
  }

  private static int parseInt(String text, int def) {
    if (isEmpty(text)) {
      return def;
    }
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
