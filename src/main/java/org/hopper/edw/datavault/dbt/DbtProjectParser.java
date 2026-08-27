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
package org.hopper.edw.datavault.dbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlTemplateParser;

/** Scans a dbt-core project folder into {@link DbtProjectScan} drafts. */
public final class DbtProjectParser {

  private static final Class<?> PKG = DbtProjectParser.class;
  public static final String PROJECT_FILE = "dbt_project.yml";
  public static final String PROJECT_FILE_YAML = "dbt_project.yaml";

  private DbtProjectParser() {}

  public static String findProjectRoot(String startPath) throws HopException {
    if (Utils.isEmpty(startPath)) {
      throw new HopException(BaseMessages.getString(PKG, "DbtProjectParser.Error.MissingPath"));
    }
    String current = startPath.replace('\\', '/');
    for (int i = 0; i < 10; i++) {
      if (DbtVfsSupport.exists(DbtVfsSupport.join(current, PROJECT_FILE))) {
        return current;
      }
      if (DbtVfsSupport.exists(DbtVfsSupport.join(current, PROJECT_FILE_YAML))) {
        return current;
      }
      String parent = DbtVfsSupport.parentPath(current);
      if (Utils.isEmpty(parent) || parent.equals(current)) {
        break;
      }
      current = parent;
    }
    throw new HopException(
        BaseMessages.getString(PKG, "DbtProjectParser.Error.NoProjectFile", startPath));
  }

  public static DbtProjectScan scan(String startPath) throws HopException {
    String root = findProjectRoot(startPath);
    DbtProjectScan scan = new DbtProjectScan();
    scan.setProjectRoot(root);

    String projectFile = DbtVfsSupport.join(root, PROJECT_FILE);
    if (!DbtVfsSupport.exists(projectFile)) {
      projectFile = DbtVfsSupport.join(root, PROJECT_FILE_YAML);
    }
    Map<String, Object> projectYaml =
        DbtYamlMaps.parseYaml(DbtVfsSupport.readUtf8(projectFile), projectFile);
    scan.setProjectName(firstNonEmpty(DbtYamlMaps.childString(projectYaml, "name"), "dbt_project"));
    scan.getModelPaths()
        .addAll(orDefault(DbtYamlMaps.stringList(projectYaml.get("model-paths")), "models"));
    scan.getMacroPaths()
        .addAll(orDefault(DbtYamlMaps.stringList(projectYaml.get("macro-paths")), "macros"));
    collectVars(scan.getVars(), DbtYamlMaps.asMap(projectYaml.get("vars")));
    collectFolderDefaults(
        scan.getFolderDefaults(), DbtYamlMaps.asMap(projectYaml.get("models")), "");

    DbtSchemaYamlParser.SchemaBundle schema = new DbtSchemaYamlParser.SchemaBundle();
    for (String modelPath : scan.getModelPaths()) {
      String abs = DbtVfsSupport.join(root, modelPath);
      for (String yamlPath : listYaml(abs)) {
        try {
          DbtSchemaYamlParser.mergeFile(
              schema, DbtVfsSupport.readUtf8(yamlPath), DbtVfsSupport.relativeTo(yamlPath, root));
        } catch (HopException e) {
          scan.getIssues()
              .add(
                  DbtImportIssue.warn(
                      DbtVfsSupport.baseName(yamlPath),
                      "YAML",
                      "Could not parse " + DbtVfsSupport.relativeTo(yamlPath, root)));
        }
      }
    }
    scan.getSources().addAll(schema.sources);

    for (String modelPath : scan.getModelPaths()) {
      String abs = DbtVfsSupport.join(root, modelPath);
      for (String sqlPath : DbtVfsSupport.listFiles(abs, ".sql")) {
        scan.getModels().add(readModel(scan, schema, modelPath, sqlPath));
      }
      for (String pyPath : DbtVfsSupport.listFiles(abs, ".py")) {
        scan.getIssues()
            .add(
                DbtImportIssue.warn(
                    stripExtension(DbtVfsSupport.baseName(pyPath)),
                    "PYTHON",
                    "Python model skipped: " + DbtVfsSupport.relativeTo(pyPath, root)));
      }
    }

    for (String macroPath : scan.getMacroPaths()) {
      String abs = DbtVfsSupport.join(root, macroPath);
      for (String sqlPath : DbtVfsSupport.listFiles(abs, ".sql")) {
        String relative = DbtVfsSupport.relativeTo(sqlPath, root);
        scan.getMacros()
            .addAll(DbtMacroFileParser.parse(DbtVfsSupport.readUtf8(sqlPath), relative));
      }
    }

    String snapshots = DbtVfsSupport.join(root, "snapshots");
    if (DbtVfsSupport.exists(snapshots)) {
      List<String> snapshotSql = DbtVfsSupport.listFiles(snapshots, ".sql");
      if (!snapshotSql.isEmpty()) {
        scan.getIssues()
            .add(
                DbtImportIssue.info(
                    null, "SNAPSHOTS", snapshotSql.size() + " snapshot file(s) skipped"));
      }
    }
    return scan;
  }

  private static DbtModelDraft readModel(
      DbtProjectScan scan,
      DbtSchemaYamlParser.SchemaBundle schema,
      String modelPath,
      String sqlPath)
      throws HopException {
    String relative = DbtVfsSupport.relativeTo(sqlPath, scan.getProjectRoot());
    String name = stripExtension(DbtVfsSupport.baseName(sqlPath));
    DbtModelDraft yaml = schema.modelsByName.get(name.toLowerCase(Locale.ROOT));
    DbtModelDraft draft = new DbtModelDraft();
    draft.setName(name);
    draft.setTableName(name);
    draft.setOriginRelativePath(relative);
    draft.setOriginAbsolutePath(sqlPath);
    draft.setFirstLevelFolder(firstLevelFolder(relative, modelPath));
    draft.setSqlQuery(DbtVfsSupport.readUtf8(sqlPath));

    if (yaml != null) {
      if (!Utils.isEmpty(yaml.getDescription())) {
        draft.setDescription(yaml.getDescription());
      }
      if (!Utils.isEmpty(yaml.getTableName())) {
        draft.setTableName(yaml.getTableName());
      }
      if (!Utils.isEmpty(yaml.getSchemaName())) {
        draft.setSchemaName(yaml.getSchemaName());
      }
      if (!Utils.isEmpty(yaml.getDbtMaterialized())) {
        draft.setDbtMaterialized(yaml.getDbtMaterialized());
      }
      draft.getColumnNotes().addAll(yaml.getColumnNotes());
    }

    DbtFolderDefaults folder = folderDefaultsFor(scan, draft.getFirstLevelFolder(), relative);
    if (folder != null) {
      if (Utils.isEmpty(draft.getDbtMaterialized()) && !Utils.isEmpty(folder.getMaterialized())) {
        draft.setDbtMaterialized(folder.getMaterialized());
      }
      if (Utils.isEmpty(draft.getSchemaName()) && !Utils.isEmpty(folder.getSchema())) {
        draft.setSchemaName(folder.getSchema());
      }
    }

    DbtSqlConfigParser.SqlConfig sqlConfig = DbtSqlConfigParser.parse(draft.getSqlQuery());
    if (!Utils.isEmpty(sqlConfig.getMaterialized())) {
      draft.setDbtMaterialized(sqlConfig.getMaterialized());
    }
    if (!Utils.isEmpty(sqlConfig.getAlias())) {
      draft.setTableName(sqlConfig.getAlias());
    }
    if (!Utils.isEmpty(sqlConfig.getSchema())) {
      draft.setSchemaName(sqlConfig.getSchema());
    }

    DbtMaterializationMapper.apply(draft);
    detectPackageMacros(draft);
    return draft;
  }

  private static void detectPackageMacros(DbtModelDraft draft) {
    String sql = draft.getSqlQuery();
    if (Utils.isEmpty(sql)) {
      return;
    }
    if (sql.contains("dbt_utils.")
        || sql.contains("dbt_utils(")
        || sql.contains("adapter.dispatch")
        || sql.contains("run_query(")) {
      draft
          .getIssues()
          .add(
              DbtImportIssue.warn(
                  draft.getName(),
                  "PACKAGE_MACRO",
                  "SQL calls dbt package / run_query / adapter.dispatch — Check model after import"));
    }
    // unused but documents that source() is preserved
    BvSqlTemplateParser.extractSourceUsages(sql);
  }

  private static DbtFolderDefaults folderDefaultsFor(
      DbtProjectScan scan, String firstLevel, String relativePath) {
    if (scan.getFolderDefaults().isEmpty()) {
      return null;
    }
    List<String> keys = new ArrayList<>();
    if (!Utils.isEmpty(firstLevel)) {
      keys.add(firstLevel.toLowerCase(Locale.ROOT));
      if (!Utils.isEmpty(scan.getProjectName())) {
        keys.add(
            scan.getProjectName().toLowerCase(Locale.ROOT)
                + "/"
                + firstLevel.toLowerCase(Locale.ROOT));
      }
    }
    keys.add("");
    if (!Utils.isEmpty(scan.getProjectName())) {
      keys.add(scan.getProjectName().toLowerCase(Locale.ROOT));
    }
    for (String key : keys) {
      DbtFolderDefaults defaults = scan.getFolderDefaults().get(key);
      if (defaults != null) {
        return defaults;
      }
    }
    return null;
  }

  static String firstLevelFolder(String relativePath, String modelPath) {
    if (Utils.isEmpty(relativePath)) {
      return "";
    }
    String norm = relativePath.replace('\\', '/');
    String prefix = modelPath != null ? modelPath.replace('\\', '/') : "models";
    if (norm.startsWith(prefix + "/")) {
      norm = norm.substring(prefix.length() + 1);
    } else if (norm.startsWith("models/")) {
      norm = norm.substring("models/".length());
    }
    int slash = norm.indexOf('/');
    return slash > 0 ? norm.substring(0, slash) : "";
  }

  private static void collectVars(Map<String, String> target, Map<String, Object> vars) {
    for (Map.Entry<String, Object> entry : vars.entrySet()) {
      if (entry.getValue() instanceof Map<?, ?>) {
        collectVars(target, DbtYamlMaps.asMap(entry.getValue()));
      } else if (!Utils.isEmpty(entry.getKey())) {
        target.putIfAbsent(entry.getKey(), DbtYamlMaps.asString(entry.getValue()));
      }
    }
  }

  private static void collectFolderDefaults(
      Map<String, DbtFolderDefaults> target, Map<String, Object> node, String path) {
    if (node == null || node.isEmpty()) {
      return;
    }
    DbtFolderDefaults defaults = target.computeIfAbsent(path, key -> new DbtFolderDefaults());
    String materialized = DbtYamlMaps.childString(node, "+materialized");
    if (!Utils.isEmpty(materialized)) {
      defaults.setMaterialized(materialized);
    }
    String schema = DbtYamlMaps.childString(node, "+schema");
    if (!Utils.isEmpty(schema)) {
      defaults.setSchema(schema);
    }
    for (Map.Entry<String, Object> entry : node.entrySet()) {
      if (entry.getKey() == null || entry.getKey().startsWith("+")) {
        continue;
      }
      if (entry.getValue() instanceof Map<?, ?>) {
        String childPath =
            Utils.isEmpty(path)
                ? entry.getKey().toLowerCase(Locale.ROOT)
                : path + "/" + entry.getKey().toLowerCase(Locale.ROOT);
        collectFolderDefaults(target, DbtYamlMaps.asMap(entry.getValue()), childPath);
      }
    }
  }

  private static List<String> listYaml(String root) throws HopException {
    List<String> files = new ArrayList<>();
    files.addAll(DbtVfsSupport.listFiles(root, ".yml"));
    files.addAll(DbtVfsSupport.listFiles(root, ".yaml"));
    return files;
  }

  private static List<String> orDefault(List<String> values, String fallback) {
    if (values == null || values.isEmpty()) {
      return List.of(fallback);
    }
    return values;
  }

  private static String firstNonEmpty(String first, String fallback) {
    return Utils.isEmpty(first) ? fallback : first;
  }

  private static String stripExtension(String name) {
    if (Utils.isEmpty(name)) {
      return name;
    }
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }
}
