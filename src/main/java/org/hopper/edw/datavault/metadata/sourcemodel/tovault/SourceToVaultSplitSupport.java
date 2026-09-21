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
package org.hopper.edw.datavault.metadata.sourcemodel.tovault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.layout.ElkGraphLayout;
import org.hopper.edw.datavault.layout.ElkLayout;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvLinkedTable;
import org.hopper.edw.datavault.metadata.DvLinkedTableSupport;
import org.hopper.edw.datavault.metadata.DvModelLoadSupport;
import org.hopper.edw.datavault.metadata.DvReferenceTable;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitModel.Kind;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitOptions.ExistingFilePolicy;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;
import org.hopper.edw.datavault.naming.EdwNamingSupport;

/**
 * Splits one applied raw-vault model into a file per hub (plus its satellites), a file per link
 * (plus link satellites and cross-model hub aliases), and a file per reference table.
 *
 * <p>{@link #partition} consumes tables out of {@code applied}. Call it after {@link
 * SourceToVaultApplySupport#apply} and do not use {@code applied} afterwards.
 */
public final class SourceToVaultSplitSupport {

  private SourceToVaultSplitSupport() {}

  /**
   * Prefix plus vault object name, passed through a type-specific {@code edw-dv-model} scheme when
   * the project has one. A prefix of {@code aw} becomes {@code aw_}.
   */
  public static String fileBaseName(
      String objectName, String namePrefix, IHopMetadataProvider metadataProvider)
      throws HopException {
    String raw = normalizePrefix(namePrefix) + (objectName == null ? "" : objectName.trim());
    String formatted =
        EdwNamingSupport.applyTypeSpecificOrFallback(
            metadataProvider, EdwNamingSchemeTypes.EDW_DV_MODEL, raw, raw);
    return sanitizeBase(formatted);
  }

  /** Trim, and append {@code _} unless the prefix already ends with {@code _} or {@code -}. */
  public static String normalizePrefix(String namePrefix) throws HopException {
    if (Utils.isEmpty(namePrefix)) {
      return "";
    }
    String prefix = namePrefix.trim();
    if (prefix.indexOf('/') >= 0
        || prefix.indexOf('\\') >= 0
        || prefix.indexOf(':') >= 0
        || prefix.contains("..")) {
      throw new HopException("Data Vault model prefix cannot contain a folder path");
    }
    if (!prefix.endsWith("_") && !prefix.endsWith("-")) {
      prefix = prefix + "_";
    }
    return prefix;
  }

  public static SourceToVaultSplitResult partition(
      DataVaultModel applied, SourceToVaultSplitOptions options, IVariables variables)
      throws HopException {
    return partition(applied, options, variables, null);
  }

  public static SourceToVaultSplitResult partition(
      DataVaultModel applied,
      SourceToVaultSplitOptions options,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (applied == null) {
      throw new HopException("Data Vault model is required");
    }
    if (options == null || Utils.isEmpty(options.getOutputFolder())) {
      throw new HopException("A folder is required to split Data Vault models");
    }
    String folder = resolveFolder(options.getOutputFolder(), variables);
    if (Utils.isEmpty(folder)) {
      throw new HopException("A folder is required to split Data Vault models");
    }

    SourceToVaultSplitResult result = new SourceToVaultSplitResult();
    List<IDvTable> snapshot = new ArrayList<>(applied.getTables());
    Map<String, IDvTable> byName = new LinkedHashMap<>();
    List<DvHub> hubs = new ArrayList<>();
    List<DvLink> links = new ArrayList<>();
    List<DvReferenceTable> references = new ArrayList<>();
    List<DvSatellite> satellites = new ArrayList<>();
    for (IDvTable table : snapshot) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      byName.put(key(table.getName()), table);
      if (table instanceof DvHub hub) {
        hubs.add(hub);
      } else if (table instanceof DvLink link) {
        links.add(link);
      } else if (table instanceof DvReferenceTable reference) {
        references.add(reference);
      } else if (table instanceof DvSatellite satellite) {
        satellites.add(satellite);
      }
    }

    Set<String> usedBaseNames = new HashSet<>();
    Map<String, SourceToVaultSplitModel> hubFiles = new LinkedHashMap<>();
    for (DvHub hub : hubs) {
      SourceToVaultSplitModel split =
          newSplit(Kind.HUB, hub.getName(), folder, options, metadataProvider, usedBaseNames, result);
      move(applied, split.getModel(), hub);
      hubFiles.put(key(hub.getName()), split);
      result.getModels().add(split);
    }
    for (DvReferenceTable reference : references) {
      SourceToVaultSplitModel split =
          newSplit(
              Kind.REFERENCE,
              reference.getName(),
              folder,
              options,
              metadataProvider,
              usedBaseNames,
              result);
      move(applied, split.getModel(), reference);
      result.getModels().add(split);
    }

    Set<DvLinkedTable> placedAliases = new HashSet<>();
    for (DvLink link : links) {
      SourceToVaultSplitModel split =
          newSplit(
              Kind.LINK, link.getName(), folder, options, metadataProvider, usedBaseNames, result);
      move(applied, split.getModel(), link);
      List<String> participants = link.getHubNames() != null ? link.getHubNames() : List.of();
      for (String participant : participants) {
        placeParticipant(
            applied, split, participant, byName, hubFiles, placedAliases, variables, result);
      }
      for (DvSatellite satellite : satellites) {
        if (!Utils.isEmpty(satellite.getLinkName())
            && link.getName().equalsIgnoreCase(satellite.getLinkName())) {
          move(applied, split.getModel(), satellite);
        }
      }
      result.getModels().add(split);
    }

    for (DvSatellite satellite : satellites) {
      if (!Utils.isEmpty(satellite.getLinkName())) {
        if (applied.getTables().contains(satellite)) {
          result
              .getWarnings()
              .add(
                  "Dropped satellite "
                      + satellite.getName()
                      + ": link "
                      + satellite.getLinkName()
                      + " is missing");
          applied.getTables().remove(satellite);
        }
        continue;
      }
      if (Utils.isEmpty(satellite.getHubName())) {
        result.getWarnings().add("Dropped satellite " + satellite.getName() + ": no parent");
        applied.getTables().remove(satellite);
        continue;
      }
      SourceToVaultSplitModel home = hubFiles.get(key(satellite.getHubName()));
      if (home == null) {
        result
            .getWarnings()
            .add(
                "Dropped satellite "
                    + satellite.getName()
                    + ": hub "
                    + satellite.getHubName()
                    + " is missing");
        applied.getTables().remove(satellite);
        continue;
      }
      move(applied, home.getModel(), satellite);
    }

    for (IDvTable leftover : new ArrayList<>(applied.getTables())) {
      if (leftover instanceof DvLinkedTable alias) {
        result.getWarnings().add("Dropped unused hub alias " + alias.getName());
      } else if (leftover != null) {
        result
            .getWarnings()
            .add("Dropped " + leftover.getName() + ": no hub, link, or reference file");
      }
      applied.getTables().remove(leftover);
    }
    return result;
  }

  public static SourceToVaultSplitWriteResult write(
      SourceToVaultSplitResult split, ExistingFilePolicy policy, IVariables variables)
      throws HopException {
    SourceToVaultSplitWriteResult written = new SourceToVaultSplitWriteResult();
    if (split == null) {
      return written;
    }
    written.getWarnings().addAll(split.getWarnings());
    ExistingFilePolicy effective = policy != null ? policy : ExistingFilePolicy.SKIP;
    for (SourceToVaultSplitModel model : split.getModels()) {
      if (model == null || model.getModel() == null || Utils.isEmpty(model.getFilename())) {
        continue;
      }
      if (fileExists(model.getFilename()) && effective == ExistingFilePolicy.SKIP) {
        written.addSkipped(model.getFilename());
        continue;
      }
      ensureParentFolder(model.getFilename());
      layoutQuietly(model.getModel(), written.getWarnings());
      model.getModel().setFilename(model.getFilename());
      ModelXmlWriteSupport.writeModelXml(
          HopVaultFileType.XML_TAG, model.getModel(), model.getFilename(), variables);
      written.addWritten(model);
    }
    return written;
  }

  private static void placeParticipant(
      DataVaultModel applied,
      SourceToVaultSplitModel linkSplit,
      String participant,
      Map<String, IDvTable> byName,
      Map<String, SourceToVaultSplitModel> hubFiles,
      Set<DvLinkedTable> placedAliases,
      IVariables variables,
      SourceToVaultSplitResult result)
      throws HopException {
    if (Utils.isEmpty(participant)) {
      return;
    }
    if (linkSplit.getModel().findTable(participant) != null) {
      return;
    }
    IDvTable table = byName.get(key(participant));
    if (table instanceof DvHub hub) {
      SourceToVaultSplitModel hubSplit = hubFiles.get(key(hub.getName()));
      if (hubSplit == null) {
        result
            .getWarnings()
            .add("Link " + linkSplit.getAnchorName() + " is missing hub " + participant);
        return;
      }
      String stored = storedPath(hubSplit.getFilename(), linkSplit.getFilename(), variables, result);
      DvLinkedTable reference = DvLinkedTableSupport.createReference(hub, stored, null);
      if (reference == null) {
        result
            .getWarnings()
            .add("Link " + linkSplit.getAnchorName() + " could not alias hub " + hub.getName());
        return;
      }
      linkSplit.getModel().getTables().add(reference);
      return;
    }
    if (table instanceof DvLinkedTable alias) {
      String hubName =
          !Utils.isEmpty(alias.getReferencedTableName())
              ? alias.getReferencedTableName()
              : alias.getName();
      SourceToVaultSplitModel hubSplit = hubFiles.get(key(hubName));
      if (hubSplit == null) {
        result
            .getWarnings()
            .add(
                "Link "
                    + linkSplit.getAnchorName()
                    + " is missing the hub file for alias "
                    + alias.getName());
        return;
      }
      String stored = storedPath(hubSplit.getFilename(), linkSplit.getFilename(), variables, result);
      DvLinkedTable card = alias;
      if (placedAliases.contains(alias)) {
        card = copyAlias(alias);
      } else {
        placedAliases.add(alias);
        applied.getTables().remove(alias);
      }
      card.setReferencedModelFilename(stored);
      IDvTable hub = byName.get(key(hubName));
      if (hub != null && !Utils.isEmpty(hub.getTableName())) {
        card.setTableName(hub.getTableName());
      }
      linkSplit.getModel().getTables().add(card);
      return;
    }
    result
        .getWarnings()
        .add("Link " + linkSplit.getAnchorName() + " is missing hub " + participant);
  }

  private static String storedPath(
      String targetFilename,
      String referringFilename,
      IVariables variables,
      SourceToVaultSplitResult result) {
    try {
      String stored =
          DvModelLoadSupport.toStoredModelPath(targetFilename, referringFilename, variables);
      if (!Utils.isEmpty(stored)) {
        return stored;
      }
    } catch (HopException e) {
      result
          .getWarnings()
          .add("Could not make a portable path for " + targetFilename + ": " + e.getMessage());
    }
    return targetFilename;
  }

  private static DvLinkedTable copyAlias(DvLinkedTable alias) {
    DvLinkedTable copy = new DvLinkedTable();
    copy.setName(alias.getName());
    copy.setTableName(alias.getTableName());
    copy.setReferencedTableName(alias.getReferencedTableName());
    copy.setReferencedTableType(alias.getReferencedTableType());
    copy.setHashKeyFieldName(alias.getHashKeyFieldName());
    copy.setDescription(alias.getDescription());
    return copy;
  }

  private static SourceToVaultSplitModel newSplit(
      Kind kind,
      String anchorName,
      String folder,
      SourceToVaultSplitOptions options,
      IHopMetadataProvider metadataProvider,
      Set<String> usedBaseNames,
      SourceToVaultSplitResult result)
      throws HopException {
    String base = fileBaseName(anchorName, options.getNamePrefix(), metadataProvider);
    String unique = base;
    int suffix = 2;
    while (!usedBaseNames.add(unique.toLowerCase(Locale.ROOT))) {
      unique = base + "_" + suffix++;
    }
    if (!unique.equals(base)) {
      result.getWarnings().add("File name " + base + " already used; wrote " + unique);
    }
    String filename = joinPath(folder, unique);
    DataVaultModel model = new DataVaultModel();
    model.setNameSynchronizedWithFilename(true);
    model.setName(unique);
    model.setFilename(filename);
    model.setDescription(describe(kind, anchorName));
    ModelConfigurationResolver.attach(model, metadataProvider);
    ModelConfigurationResolver.applyDefaultNameIfPresent(model, metadataProvider);
    return new SourceToVaultSplitModel(kind, anchorName, unique, filename, model);
  }

  private static String describe(Kind kind, String anchorName) {
    return switch (kind) {
      case HUB -> "Generated hub " + anchorName + " and its satellites.";
      case LINK -> "Generated link " + anchorName + ", its link satellites, and linked hubs.";
      case REFERENCE -> "Generated reference table " + anchorName + ".";
    };
  }

  private static void move(DataVaultModel from, DataVaultModel to, IDvTable table) {
    if (table == null || to == null) {
      return;
    }
    if (from != null) {
      from.getTables().remove(table);
    }
    if (!to.getTables().contains(table)) {
      to.getTables().add(table);
    }
  }

  private static boolean fileExists(String filename) throws HopException {
    try {
      return HopVfs.getFileObject(filename).exists();
    } catch (Exception e) {
      throw new HopException("Unable to check Data Vault model file '" + filename + "'", e);
    }
  }

  private static void ensureParentFolder(String filename) throws HopException {
    try {
      FileObject file = HopVfs.getFileObject(filename);
      FileObject parent = file.getParent();
      if (parent != null && !parent.exists()) {
        parent.createFolder();
      }
    } catch (Exception e) {
      throw new HopException("Unable to create folder for '" + filename + "'", e);
    }
  }

  private static void layoutQuietly(DataVaultModel model, List<String> warnings) {
    try {
      ElkGraphLayout.fromDataVaultModel(model).layout(ElkLayout.createDefault());
    } catch (Exception e) {
      String name = model != null ? model.getName() : "";
      warnings.add("Layout skipped for " + name + ": " + e.getMessage());
    }
  }

  private static String resolveFolder(String folder, IVariables variables) {
    if (variables == null || Utils.isEmpty(folder)) {
      return folder;
    }
    return variables.resolve(folder);
  }

  static String joinPath(String folder, String baseName) {
    String normalized = folder.replace('\\', '/');
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized + "/" + baseName + HopVaultFileType.VAULT_FILE_EXTENSION;
  }

  private static String sanitizeBase(String base) {
    String cleaned =
        (base == null ? "" : base).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    while (cleaned.endsWith(".hdv") || cleaned.endsWith(".HDV")) {
      cleaned = cleaned.substring(0, cleaned.length() - 4).trim();
    }
    return cleaned.isEmpty() ? "data-vault-model" : cleaned;
  }

  private static String key(String name) {
    return name == null ? "" : name.toLowerCase(Locale.ROOT);
  }
}
