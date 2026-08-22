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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.util.HopMetadataInstance;
import org.apache.hop.metadata.util.HopMetadataUtil;

/**
 * In-process JDBC driver for free SQL against a local {@code .hsm} file (requires full Hop).
 *
 * <p>URL forms:
 *
 * <ul>
 *   <li>{@code jdbc:hop-hsm:file=/path/to/model.hsm}
 *   <li>{@code jdbc:hop-hsm:/path/to/model.hsm}
 * </ul>
 *
 * <p>For remote Hop Server access use the thin client jar ({@code hop-hsm-jdbc}) with:
 *
 * <pre>
 * jdbc:hop-hsm://user:pass@host:port/hop/sourceModelData?modelName=service-name
 * </pre>
 *
 * <p>This local driver deliberately <strong>does not</strong> accept remote {@code //host} URLs so
 * the thin client and Hop plugin can coexist without conflict.
 */
public class HopSourceModelJdbcDriver implements Driver {

  public static final String URL_PREFIX = "jdbc:hop-hsm:";

  static {
    try {
      DriverManager.registerDriver(new HopSourceModelJdbcDriver());
    } catch (SQLException e) {
      // Ignore: registration race is acceptable.
    }
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (!acceptsURL(url)) {
      return null;
    }
    try {
      ensureHopInit();
      ParsedUrl parsed = parseUrl(url, info != null ? info : new Properties());
      Variables variables = new Variables();
      // Import connection properties as variables for ${...} in SQL / paths.
      if (info != null) {
        for (String name : info.stringPropertyNames()) {
          variables.setVariable(name, info.getProperty(name));
        }
      }
      IHopMetadataProvider metadataProvider = resolveMetadataProvider(parsed, variables);
      SourceModel model =
          SourceModelLoadSupport.load(parsed.hsmPath(), variables, metadataProvider);
      return new HopSourceModelJdbcConnection(
          model, variables, metadataProvider, parsed.rowLimit());
    } catch (SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new SQLException("Unable to open hop-hsm connection: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean acceptsURL(String url) {
    if (url == null || !url.regionMatches(true, 0, URL_PREFIX, 0, URL_PREFIX.length())) {
      return false;
    }
    // Remote HTTP driver owns jdbc:hop-hsm://… and jdbc:hop-hsm:http(s)://…
    String rest = url.substring(URL_PREFIX.length());
    if (rest.startsWith("//")
        || rest.regionMatches(true, 0, "http://", 0, 7)
        || rest.regionMatches(true, 0, "https://", 0, 8)) {
      return false;
    }
    return true;
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
    return new DriverPropertyInfo[] {
      prop("file", "Path to the .hsm source model", true),
      prop("metadataFolder", "Hop JSON metadata folder (rdbms connections)", false),
      prop("rowLimit", "Default max rows per query (0 = unlimited)", false),
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

  private static void ensureHopInit() throws Exception {
    if (!HopEnvironment.isInitialized()) {
      HopEnvironment.init();
    }
  }

  private static IHopMetadataProvider resolveMetadataProvider(ParsedUrl parsed, Variables variables)
      throws Exception {
    if (!Utils.isEmpty(parsed.metadataFolder())) {
      return HopMetadataUtil.getStandardHopMetadataProvider(variables);
    }
    // Prefer process-wide Hop metadata (GUI / CLI) when available.
    try {
      IHopMetadataProvider instance = HopMetadataInstance.getMetadataProvider();
      if (instance != null) {
        return instance;
      }
    } catch (Throwable ignored) {
      // HopMetadataInstance may not be configured outside Hop GUI/CLI.
    }
    // Fall back: standard provider (uses hop config folders from variables if set).
    try {
      IHopMetadataProvider standard = HopMetadataUtil.getStandardHopMetadataProvider(variables);
      if (standard != null) {
        return standard;
      }
    } catch (Throwable ignored) {
      // ignore
    }
    return new MemoryMetadataProvider();
  }

  record ParsedUrl(String hsmPath, String metadataFolder, int rowLimit) {}

  static ParsedUrl parseUrl(String url, Properties info) throws SQLException {
    String rest = url.substring(URL_PREFIX.length()).trim();
    String file = info.getProperty("file");
    String metadataFolder = info.getProperty("metadataFolder");
    int rowLimit = parseInt(info.getProperty("rowLimit"), 0);

    // Support file=/path;rowLimit=100 or plain /path or file=/path
    if (rest.startsWith("file=")) {
      rest = rest.substring("file=".length());
    }
    // Split query-style params after first semicolon
    String pathPart = rest;
    int semi = rest.indexOf(';');
    if (semi >= 0) {
      pathPart = rest.substring(0, semi);
      String[] params = rest.substring(semi + 1).split(";");
      for (String param : params) {
        int eq = param.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String key = param.substring(0, eq).trim();
        String value = param.substring(eq + 1).trim();
        if ("file".equalsIgnoreCase(key) && Utils.isEmpty(file)) {
          file = value;
        } else if ("metadataFolder".equalsIgnoreCase(key)) {
          metadataFolder = value;
        } else if ("rowLimit".equalsIgnoreCase(key)) {
          rowLimit = parseInt(value, rowLimit);
        }
      }
    }
    if (Utils.isEmpty(file)) {
      file = pathPart;
    }
    if (Utils.isEmpty(file)) {
      throw new SQLException(
          "hop-hsm URL must include the .hsm path, e.g. jdbc:hop-hsm:file=/path/to/model.hsm");
    }
    if (!Utils.isEmpty(metadataFolder)) {
      // HopMetadataUtil reads folders from hop config / variables; expose as PROJECT_HOME-ish.
      // Json provider uses configured folders; set both common vars.
      // Caller should also put folder on hop config; we store on info via connection later.
    }
    return new ParsedUrl(file.trim(), metadataFolder, rowLimit);
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
