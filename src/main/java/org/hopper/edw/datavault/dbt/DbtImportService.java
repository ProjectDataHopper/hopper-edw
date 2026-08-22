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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvBusinessTable;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlSource;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlTemplateParser;
import org.hopper.edw.datavault.metadata.businessvault.IBvTable;
import org.hopper.edw.datavault.metadata.jinja.JinjaMacroDefinition;
import org.hopper.edw.datavault.metadata.jinja.JinjaMacroLibraryMeta;
import org.hopper.edw.datavault.metadata.jinja.JinjaMacroVar;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/** Applies a {@link DbtProjectScan} to Business Vault model(s) and a Jinja macro library. */
public final class DbtImportService {

  private static final Class<?> PKG = DbtImportService.class;

  private DbtImportService() {}

  public static DbtImportResult apply(DbtImportOptions options) throws HopException {
    if (options == null || options.getScan() == null) {
      throw new HopException(BaseMessages.getString(PKG, "DbtImportService.Error.MissingScan"));
    }
    DbtImportResult result = new DbtImportResult();
    DbtProjectScan scan = options.getScan();
    result.getIssues().addAll(scan.getIssues());

    List<DbtModelDraft> selected = new ArrayList<>();
    for (DbtModelDraft draft : options.getSelectedModels()) {
      if (draft == null) {
        continue;
      }
      result.getIssues().addAll(draft.getIssues());
      if (draft.isImportable()) {
        selected.add(draft);
      } else {
        result.setSkippedTables(result.getSkippedTables() + 1);
      }
    }

    if (options.isImportMacros()) {
      result.setLibrary(upsertLibrary(options, scan));
    }

    DbtImportDestination destination =
        options.getDestination() != null
            ? options.getDestination()
            : DbtImportDestination.CURRENT_MODEL;
    switch (destination) {
      case NEW_MODEL -> applyNewModel(options, selected, result);
      case SPLIT_BY_FOLDER -> applySplit(options, selected, result);
      default -> applyToModel(options.getCurrentModel(), selected, options, result);
    }
    return result;
  }

  public static String defaultLibraryName(DbtProjectScan scan) {
    String project =
        scan != null && !Utils.isEmpty(scan.getProjectName()) ? scan.getProjectName() : "dbt";
    return project + "-macros";
  }

  public static DbtImportDestination suggestDestination(int selectedCount) {
    return selectedCount > DbtImportOptions.DEFAULT_SPLIT_THRESHOLD
        ? DbtImportDestination.SPLIT_BY_FOLDER
        : DbtImportDestination.CURRENT_MODEL;
  }

  static void applyToModel(
      BusinessVaultModel model,
      List<DbtModelDraft> drafts,
      DbtImportOptions options,
      DbtImportResult result) {
    if (model == null) {
      result
          .getIssues()
          .add(DbtImportIssue.error(null, "NO_MODEL", "No current Business Vault model"));
      return;
    }
    int index = model.getTables().size();
    for (DbtModelDraft draft : drafts) {
      IBvTable existing = findExisting(model, draft);
      if (existing != null) {
        if (options.getConflictPolicy() == DbtImportConflictPolicy.REPLACE
            && existing instanceof BvBusinessTable table) {
          copyDraft(table, draft, scanSources(options.getScan()));
          result.setReplacedTables(result.getReplacedTables() + 1);
          result.setImportedTables(result.getImportedTables() + 1);
        } else {
          result.setSkippedTables(result.getSkippedTables() + 1);
          result
              .getIssues()
              .add(
                  DbtImportIssue.info(
                      draft.getName(), "SKIP_EXISTING", "Table already exists; skipped"));
        }
        continue;
      }
      BvBusinessTable table = new BvBusinessTable();
      copyDraft(table, draft, scanSources(options.getScan()));
      table.setLocation(gridLocation(index++));
      model.getTables().add(table);
      result.setImportedTables(result.getImportedTables() + 1);
    }
  }

  private static void applyNewModel(
      DbtImportOptions options, List<DbtModelDraft> drafts, DbtImportResult result)
      throws HopException {
    BusinessVaultModel model = newModelFrom(options, fileBase(options.getNewModelFilename()));
    applyToModel(model, drafts, options, result);
    String filename = resolveNewFilename(options);
    model.setFilename(filename);
    ModelXmlWriteSupport.writeModelXml(
        HopBusinessVaultFileType.XML_TAG, model, filename, options.getVariables());
    result.getWrittenModelFiles().add(filename);
  }

  private static void applySplit(
      DbtImportOptions options, List<DbtModelDraft> drafts, DbtImportResult result)
      throws HopException {
    Map<String, List<DbtModelDraft>> groups = new LinkedHashMap<>();
    for (DbtModelDraft draft : drafts) {
      String folder =
          Utils.isEmpty(draft.getFirstLevelFolder()) ? "models" : draft.getFirstLevelFolder();
      groups.computeIfAbsent(folder, key -> new ArrayList<>()).add(draft);
    }
    String outputFolder = outputFolder(options);
    for (Map.Entry<String, List<DbtModelDraft>> entry : groups.entrySet()) {
      String filename = DbtVfsSupport.join(outputFolder, safeFileName(entry.getKey()) + ".hbv");
      BusinessVaultModel model = newModelFrom(options, entry.getKey());
      model.setFilename(filename);
      applyToModel(model, entry.getValue(), options, result);
      ModelXmlWriteSupport.writeModelXml(
          HopBusinessVaultFileType.XML_TAG, model, filename, options.getVariables());
      result.getWrittenModelFiles().add(filename);
    }
  }

  private static BusinessVaultModel newModelFrom(DbtImportOptions options, String name) {
    BusinessVaultModel model = new BusinessVaultModel();
    model.setName(name);
    if (options.getCurrentModel() != null) {
      model.setConfigurationName(options.getCurrentModel().getConfigurationName());
      if (options.getCurrentModel().getConfiguration() != null) {
        model.setConfiguration(options.getCurrentModel().getConfiguration());
      }
    }
    return model;
  }

  static void copyDraft(BvBusinessTable table, DbtModelDraft draft, List<DbtSourceTable> sources) {
    table.setName(draft.getName());
    table.setTableName(
        !Utils.isEmpty(draft.getTableName()) ? draft.getTableName() : draft.getName());
    table.setDescription(draft.getDescription());
    table.setSqlQuery(draft.getSqlQuery());
    table.setSchemaName(draft.getSchemaName());
    table.setOriginDbtPath(draft.getOriginRelativePath());
    table.setMaterialization(draft.getMaterialization());
    table.getColumnNotes().clear();
    table.getColumnNotes().addAll(draft.getColumnNotes());
    table.getSources().clear();
    for (BvSqlSource usage : BvSqlTemplateParser.extractSourceUsages(draft.getSqlQuery())) {
      DbtSourceTable match = findSource(sources, usage.getSourceName(), usage.getTableName());
      BvSqlSource source = new BvSqlSource(usage.getSourceName(), usage.getTableName());
      if (match != null) {
        source.setSchemaName(match.getSchemaName());
        source.setDescription(match.getDescription());
      }
      table.getSources().add(source);
    }
  }

  private static JinjaMacroLibraryMeta upsertLibrary(DbtImportOptions options, DbtProjectScan scan)
      throws HopException {
    String name =
        !Utils.isEmpty(options.getLibraryName())
            ? options.getLibraryName()
            : defaultLibraryName(scan);
    JinjaMacroLibraryMeta library = new JinjaMacroLibraryMeta(name);
    library.setDescription("Imported from dbt project " + scan.getProjectName());
    library.setPackageName(scan.getProjectName());
    library.setEnabled(true);
    for (Map.Entry<String, String> entry : scan.getVars().entrySet()) {
      library.getVars().add(new JinjaMacroVar(entry.getKey(), entry.getValue()));
    }
    for (DbtMacroDraft macro : scan.getMacros()) {
      JinjaMacroDefinition definition = new JinjaMacroDefinition();
      definition.setName(macro.getName());
      definition.setJinjaSource(macro.getJinjaSource());
      definition.setOriginPath(macro.getOriginRelativePath());
      library.getMacros().add(definition);
    }
    IHopMetadataProvider provider = options.getMetadataProvider();
    if (provider != null) {
      IHopMetadataSerializer<JinjaMacroLibraryMeta> serializer =
          provider.getSerializer(JinjaMacroLibraryMeta.class);
      serializer.save(library);
    }
    return library;
  }

  private static IBvTable findExisting(BusinessVaultModel model, DbtModelDraft draft) {
    IBvTable byName = model.findTable(draft.getName());
    if (byName != null) {
      return byName;
    }
    String physical = !Utils.isEmpty(draft.getTableName()) ? draft.getTableName() : draft.getName();
    for (IBvTable table : model.getTables()) {
      if (table == null) {
        continue;
      }
      if (physical.equalsIgnoreCase(table.getName())
          || physical.equalsIgnoreCase(table.getTableName())) {
        return table;
      }
    }
    return null;
  }

  private static DbtSourceTable findSource(
      List<DbtSourceTable> sources, String sourceName, String tableName) {
    if (sources == null) {
      return null;
    }
    for (DbtSourceTable source : sources) {
      if (source != null
          && sourceName.equalsIgnoreCase(source.getSourceName())
          && tableName.equalsIgnoreCase(source.getTableName())) {
        return source;
      }
    }
    return null;
  }

  private static List<DbtSourceTable> scanSources(DbtProjectScan scan) {
    return scan != null ? scan.getSources() : List.of();
  }

  static Point gridLocation(int index) {
    int x = 80 + (index % 6) * 180;
    int y = 80 + (index / 6) * 110;
    return new Point(x, y);
  }

  private static String outputFolder(DbtImportOptions options) {
    if (!Utils.isEmpty(options.getOutputFolder())) {
      return options.getOutputFolder();
    }
    if (options.getCurrentModel() != null
        && !Utils.isEmpty(options.getCurrentModel().getFilename())) {
      return DbtVfsSupport.parentPath(options.getCurrentModel().getFilename());
    }
    return options.getProjectRoot();
  }

  private static String resolveNewFilename(DbtImportOptions options) {
    if (!Utils.isEmpty(options.getNewModelFilename())) {
      String name = options.getNewModelFilename();
      if (!name.toLowerCase(Locale.ROOT).endsWith(".hbv")) {
        name = name + ".hbv";
      }
      if (name.contains("/") || name.contains("\\")) {
        return name;
      }
      return DbtVfsSupport.join(outputFolder(options), name);
    }
    String project =
        options.getScan() != null && !Utils.isEmpty(options.getScan().getProjectName())
            ? options.getScan().getProjectName()
            : "dbt-import";
    return DbtVfsSupport.join(outputFolder(options), safeFileName(project) + ".hbv");
  }

  private static String fileBase(String filename) {
    if (Utils.isEmpty(filename)) {
      return "dbt-import";
    }
    String base = DbtVfsSupport.baseName(filename);
    int dot = base.lastIndexOf('.');
    return dot > 0 ? base.substring(0, dot) : base;
  }

  private static String safeFileName(String name) {
    if (Utils.isEmpty(name)) {
      return "models";
    }
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
