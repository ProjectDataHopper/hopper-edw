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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.BaseMetadataProvider;
import org.apache.hop.metadata.serializer.json.JsonMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.multi.MultiMetadataProvider;
import org.apache.hop.metadata.util.HopMetadataInstance;
import org.apache.hop.metadata.util.HopMetadataUtil;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.service.SourceModelService;

/**
 * In-process JDBC driver for free SQL against a local {@code .hsm} file, an in-memory model, or a
 * locally defined {@link SourceModelService} (requires full Hop).
 *
 * <p>URL forms:
 *
 * <ul>
 *   <li>{@code jdbc:hop-hsm:file=/path/to/model.hsm}
 *   <li>{@code jdbc:hop-hsm:/path/to/model.hsm}
 *   <li>{@code jdbc:hop-hsm:service=service-name}
 *   <li>{@code jdbc:hop-hsm:embedded/service-name}
 *   <li>{@code jdbc:hop-hsm:embedded}
 *   <li>{@code jdbc:hop-hsm:memory:model-name}
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

  private static final Map<String, SourceModel> IN_MEMORY_MODELS = new ConcurrentHashMap<>();

  public static void registerModel(String name, SourceModel model) {
    if (name != null && model != null) {
      IN_MEMORY_MODELS.put(name.trim().toLowerCase(Locale.ROOT), model);
    }
  }

  public static void deregisterModel(String name) {
    if (name != null) {
      IN_MEMORY_MODELS.remove(name.trim().toLowerCase(Locale.ROOT));
    }
  }

  public static SourceModel getRegisteredModel(String name) {
    return name != null ? IN_MEMORY_MODELS.get(name.trim().toLowerCase(Locale.ROOT)) : null;
  }

  public static void clearModels() {
    IN_MEMORY_MODELS.clear();
  }

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
      Properties props = info != null ? info : new Properties();
      ParsedUrl parsed = parseUrl(url, props);
      Variables variables = resolveVariables(props, parsed, null);
      IHopMetadataProvider metadataProvider = resolveMetadataProvider(parsed, variables, props);
      enrichVariablesFromMetadata(variables, metadataProvider);

      // 1. Direct in-memory SourceModel object in Properties info
      SourceModel inMemoryModel = null;
      if (props.get("sourceModel") instanceof SourceModel sm) {
        inMemoryModel = sm;
      } else if (props.get("model") instanceof SourceModel sm) {
        inMemoryModel = sm;
      }

      // 2. Registered in-memory model by name
      if (inMemoryModel == null && !Utils.isEmpty(parsed.memoryName())) {
        inMemoryModel = getRegisteredModel(parsed.memoryName());
        if (inMemoryModel == null) {
          throw new SQLException(
              "In-memory source model '" + parsed.memoryName() + "' is not registered");
        }
      }

      if (inMemoryModel != null) {
        String schema =
            !Utils.isEmpty(parsed.serviceName())
                ? variables.resolve(parsed.serviceName())
                : inMemoryModel.getName();
        return new HopSourceModelJdbcConnection(
            inMemoryModel, schema, variables, metadataProvider, parsed.rowLimit());
      }

      // 3. File path
      if (!Utils.isEmpty(parsed.hsmPath())) {
        String hsmPath = resolveModelFilename(parsed.hsmPath(), variables, metadataProvider);
        SourceModel model = SourceModelLoadSupport.load(hsmPath, variables, metadataProvider);
        String schema =
            !Utils.isEmpty(parsed.serviceName())
                ? variables.resolve(parsed.serviceName())
                : model.getName();
        return new HopSourceModelJdbcConnection(
            model, schema, variables, metadataProvider, parsed.rowLimit());
      }

      // 4. Named service from metadata provider
      if (!Utils.isEmpty(parsed.serviceName())) {
        String serviceName = variables.resolve(parsed.serviceName());
        IHopMetadataSerializer<SourceModelService> serializer =
            metadataProvider.getSerializer(SourceModelService.class);
        SourceModelService service = serializer.load(serviceName);
        if (service == null) {
          throw new SQLException(
              "Source model service '" + serviceName + "' not found in metadata provider");
        }
        if (!service.isEnabled()) {
          throw new SQLException("Source model service '" + serviceName + "' is disabled");
        }
        String filename =
            resolveModelFilename(service.getModelFilename(), variables, metadataProvider);
        if (Utils.isEmpty(filename)) {
          throw new SQLException(
              "Source model service '" + serviceName + "' has no model file configured");
        }
        SourceModel model = SourceModelLoadSupport.load(filename, variables, metadataProvider);
        if (Utils.isEmpty(model.getName())) {
          model.setName(service.getName());
        }
        int rowLimit = parsed.rowLimit() > 0 ? parsed.rowLimit() : service.getDefaultRowLimit();
        return new HopSourceModelJdbcConnection(
            model, service.getName(), variables, metadataProvider, rowLimit);
      }

      // 5. Embedded mode without explicit service name
      IHopMetadataSerializer<SourceModelService> serializer =
          metadataProvider.getSerializer(SourceModelService.class);
      List<SourceModelService> services = serializer.loadAll();
      List<SourceModelService> enabledServices =
          services.stream().filter(SourceModelService::isEnabled).toList();
      SourceModel defaultModel = null;
      String defaultSchema = null;
      int rowLimit = parsed.rowLimit();
      if (enabledServices.size() == 1) {
        SourceModelService service = enabledServices.get(0);
        String filename =
            resolveModelFilename(service.getModelFilename(), variables, metadataProvider);
        if (!Utils.isEmpty(filename)) {
          defaultModel = SourceModelLoadSupport.load(filename, variables, metadataProvider);
          defaultSchema = service.getName();
          if (Utils.isEmpty(defaultModel.getName())) {
            defaultModel.setName(service.getName());
          }
          if (rowLimit <= 0) {
            rowLimit = service.getDefaultRowLimit();
          }
        }
      }
      return new HopSourceModelJdbcConnection(
          defaultModel, defaultSchema, variables, metadataProvider, rowLimit);
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
      prop("service", "Source model service metadata name (schema)", false),
      prop("file", "Path to the .hsm source model", false),
      prop("memory", "Registered in-memory model name", false),
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

  private static IHopMetadataProvider resolveMetadataProvider(
      ParsedUrl parsed, Variables variables, Properties info) throws Exception {
    if (info != null && info.get("metadataProvider") instanceof IHopMetadataProvider mp) {
      return mp;
    }
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

  public static String resolveModelFilename(
      String rawFilename, IVariables variables, IHopMetadataProvider metadataProvider)
      throws SQLException {
    if (Utils.isEmpty(rawFilename)) {
      return rawFilename;
    }
    String filename = variables != null ? variables.resolve(rawFilename) : rawFilename;
    if (filename.contains("${PROJECT_HOME}")) {
      String derived = deriveProjectHome(metadataProvider, variables);
      if (!Utils.isEmpty(derived)) {
        if (variables != null) {
          variables.setVariable("PROJECT_HOME", derived);
          filename = variables.resolve(rawFilename);
        } else {
          filename = filename.replace("${PROJECT_HOME}", derived);
        }
      }
    }
    if (variables != null) {
      filename = variables.resolve(filename);
    }
    if (filename.contains("${PROJECT_HOME}")) {
      throw new SQLException(
          "Source model file path '"
              + filename
              + "' contains unresolved variable ${PROJECT_HOME}. Please ensure a Hop project is open or the PROJECT_HOME variable is set.");
    }
    return filename;
  }

  static Variables resolveVariables(
      Properties props, ParsedUrl parsed, IHopMetadataProvider metadataProvider) {
    Variables variables = new Variables();

    // 1. Initialize from default variable space
    try {
      IVariables defaultSpace = Variables.getADefaultVariableSpace();
      if (defaultSpace != null) {
        variables.initializeFrom(defaultSpace);
      }
    } catch (Throwable ignored) {
    }

    // 2. Inherit from HopGui if active (e.g. running inside Hop GUI / Database dialog / Database
    // explorer)
    try {
      org.apache.hop.ui.hopgui.HopGui gui = org.apache.hop.ui.hopgui.HopGui.getInstance();
      if (gui != null && gui.getVariables() != null) {
        variables.copyFrom(gui.getVariables());
      }
    } catch (Throwable ignored) {
    }

    // 3. Inherit from metadata provider if it carries variables
    if (metadataProvider != null) {
      enrichVariablesFromMetadata(variables, metadataProvider);
    }

    // 4. Inherit from variables passed explicitly in connection properties
    if (props != null) {
      Object passed = props.get("variables");
      if (passed instanceof IVariables v) {
        variables.copyFrom(v);
      }
      for (String name : props.stringPropertyNames()) {
        variables.setVariable(name, props.getProperty(name));
      }
    }

    return variables;
  }

  static void enrichVariablesFromMetadata(
      Variables variables, IHopMetadataProvider metadataProvider) {
    if (metadataProvider == null) {
      return;
    }
    try {
      if (metadataProvider instanceof MultiMetadataProvider mmp && mmp.getVariables() != null) {
        variables.copyFrom(mmp.getVariables());
      } else if (metadataProvider instanceof BaseMetadataProvider bmp
          && bmp.getVariables() != null) {
        variables.copyFrom(bmp.getVariables());
      }
    } catch (Throwable ignored) {
    }

    String projHome = variables.getVariable("PROJECT_HOME");
    if (Utils.isEmpty(projHome) || "${PROJECT_HOME}".equals(projHome)) {
      String derived = deriveProjectHome(metadataProvider, variables);
      if (!Utils.isEmpty(derived)) {
        variables.setVariable("PROJECT_HOME", derived);
      }
    }
  }

  static String deriveProjectHome(IHopMetadataProvider metadataProvider, IVariables variables) {
    // 1. Check system properties and environment variables
    String sysHome = System.getProperty("PROJECT_HOME");
    if (!Utils.isEmpty(sysHome)) {
      return sysHome;
    }
    String envHome = System.getenv("PROJECT_HOME");
    if (!Utils.isEmpty(envHome)) {
      return envHome;
    }

    // 2. Check HopGui instance if active
    try {
      org.apache.hop.ui.hopgui.HopGui gui = org.apache.hop.ui.hopgui.HopGui.getInstance();
      if (gui != null && gui.getVariables() != null) {
        String guiHome = gui.getVariables().getVariable("PROJECT_HOME");
        if (!Utils.isEmpty(guiHome) && !"${PROJECT_HOME}".equals(guiHome)) {
          return guiHome;
        }
      }
    } catch (Throwable ignored) {
    }

    // 3. Inspect metadataProvider base folder
    if (metadataProvider != null) {
      try {
        String baseFolder = extractBaseFolder(metadataProvider);
        if (!Utils.isEmpty(baseFolder)) {
          if (variables != null) {
            baseFolder = variables.resolve(baseFolder);
          }
          if (baseFolder.endsWith("/metadata")) {
            return baseFolder.substring(0, baseFolder.length() - "/metadata".length());
          } else if (baseFolder.endsWith("\\metadata")) {
            return baseFolder.substring(0, baseFolder.length() - "\\metadata".length());
          }
        }
      } catch (Throwable ignored) {
      }
    }

    // 4. Inspect ProjectsConfigSingleton via reflection
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      Class<?> singletonClass = null;
      try {
        singletonClass = Class.forName("org.apache.hop.projects.config.ProjectsConfigSingleton");
      } catch (ClassNotFoundException e) {
        if (cl != null) {
          singletonClass = cl.loadClass("org.apache.hop.projects.config.ProjectsConfigSingleton");
        }
      }
      if (singletonClass != null) {
        Object config = singletonClass.getMethod("getConfig").invoke(null);
        if (config != null) {
          String activeProject = null;
          try {
            activeProject = org.apache.hop.ui.core.gui.HopNamespace.getNamespace();
          } catch (Throwable ignored) {
          }
          if (Utils.isEmpty(activeProject)) {
            activeProject =
                (String) config.getClass().getMethod("getDefaultProject").invoke(config);
          }
          if (!Utils.isEmpty(activeProject)) {
            Object projectConfig =
                config
                    .getClass()
                    .getMethod("findProjectConfig", String.class)
                    .invoke(config, activeProject);
            if (projectConfig != null) {
              String home =
                  (String)
                      projectConfig.getClass().getMethod("getProjectHome").invoke(projectConfig);
              if (!Utils.isEmpty(home)) {
                return home;
              }
            }
          }
        }
      }
    } catch (Throwable ignored) {
    }

    return null;
  }

  private static String extractBaseFolder(IHopMetadataProvider metadataProvider) {
    if (metadataProvider instanceof JsonMetadataProvider jmp) {
      return jmp.getBaseFolder();
    }
    if (metadataProvider instanceof MultiMetadataProvider mmp) {
      List<IHopMetadataProvider> providers = mmp.getProviders();
      if (providers != null) {
        for (IHopMetadataProvider p : providers) {
          if (p instanceof JsonMetadataProvider jmp) {
            return jmp.getBaseFolder();
          }
        }
      }
    }
    return null;
  }

  record ParsedUrl(
      String hsmPath,
      String serviceName,
      String memoryName,
      String metadataFolder,
      int rowLimit,
      boolean isEmbedded) {}

  static ParsedUrl parseUrl(String url, Properties info) throws SQLException {
    String rest = url.substring(URL_PREFIX.length()).trim();
    String file = info.getProperty("file");
    String service = info.getProperty("service");
    if (Utils.isEmpty(service)) {
      service = info.getProperty("schema");
    }
    if (Utils.isEmpty(service)) {
      service = info.getProperty("modelName");
    }
    String memory = info.getProperty("memory");
    if (Utils.isEmpty(memory)) {
      memory = info.getProperty("model");
    }
    String metadataFolder = info.getProperty("metadataFolder");
    int rowLimit = parseInt(info.getProperty("rowLimit"), 0);

    // Support query style params after ? or ;
    String pathPart = rest;
    int delimiterIdx = -1;
    int qIdx = rest.indexOf('?');
    int sIdx = rest.indexOf(';');
    if (qIdx >= 0 && sIdx >= 0) {
      delimiterIdx = Math.min(qIdx, sIdx);
    } else if (qIdx >= 0) {
      delimiterIdx = qIdx;
    } else {
      delimiterIdx = sIdx;
    }

    if (delimiterIdx >= 0) {
      pathPart = rest.substring(0, delimiterIdx).trim();
      String queryPart = rest.substring(delimiterIdx + 1).trim();
      String[] params = queryPart.split("[;&]");
      for (String param : params) {
        int eq = param.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String key = param.substring(0, eq).trim();
        String value = param.substring(eq + 1).trim();
        if ("file".equalsIgnoreCase(key) && Utils.isEmpty(file)) {
          file = value;
        } else if (("service".equalsIgnoreCase(key)
                || "schema".equalsIgnoreCase(key)
                || "modelName".equalsIgnoreCase(key))
            && Utils.isEmpty(service)) {
          service = value;
        } else if (("memory".equalsIgnoreCase(key) || "model".equalsIgnoreCase(key))
            && Utils.isEmpty(memory)) {
          memory = value;
        } else if ("metadataFolder".equalsIgnoreCase(key)) {
          metadataFolder = value;
        } else if ("rowLimit".equalsIgnoreCase(key)) {
          rowLimit = parseInt(value, rowLimit);
        }
      }
    }

    // Inspect pathPart
    if (pathPart.startsWith("file=")) {
      if (Utils.isEmpty(file)) {
        file = pathPart.substring("file=".length()).trim();
      }
    } else if (pathPart.startsWith("service=")) {
      if (Utils.isEmpty(service)) {
        service = pathPart.substring("service=".length()).trim();
      }
    } else if (pathPart.startsWith("schema=")) {
      if (Utils.isEmpty(service)) {
        service = pathPart.substring("schema=".length()).trim();
      }
    } else if (pathPart.startsWith("modelName=")) {
      if (Utils.isEmpty(service)) {
        service = pathPart.substring("modelName=".length()).trim();
      }
    } else if (pathPart.startsWith("memory:")) {
      if (Utils.isEmpty(memory)) {
        memory = pathPart.substring("memory:".length()).trim();
      }
    } else if (pathPart.startsWith("embedded/")) {
      if (Utils.isEmpty(service)) {
        service = pathPart.substring("embedded/".length()).trim();
      }
    } else if (pathPart.equalsIgnoreCase("embedded")) {
      // embedded mode without specific service
    } else if (!pathPart.isEmpty()
        && Utils.isEmpty(file)
        && Utils.isEmpty(service)
        && Utils.isEmpty(memory)) {
      if (pathPart.endsWith(".hsm")
          || pathPart.startsWith("/")
          || pathPart.contains("/")
          || pathPart.contains("\\")) {
        file = pathPart;
      } else {
        service = pathPart;
      }
    }

    boolean hasDirectModel =
        info != null
            && (info.get("sourceModel") instanceof SourceModel
                || info.get("model") instanceof SourceModel);
    boolean isEmbedded =
        pathPart.equalsIgnoreCase("embedded")
            || pathPart.startsWith("embedded/")
            || (info != null && "embedded".equalsIgnoreCase(info.getProperty("authType")));

    if (Utils.isEmpty(file)
        && Utils.isEmpty(service)
        && Utils.isEmpty(memory)
        && !hasDirectModel
        && !isEmbedded) {
      throw new SQLException(
          "hop-hsm URL must include a file path (file=/path/to/model.hsm), service name (service=name), memory model (memory:name), or embedded mode");
    }

    return new ParsedUrl(
        file != null ? file.trim() : null,
        service != null ? service.trim() : null,
        memory != null ? memory.trim() : null,
        metadataFolder,
        rowLimit,
        isEmbedded);
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
