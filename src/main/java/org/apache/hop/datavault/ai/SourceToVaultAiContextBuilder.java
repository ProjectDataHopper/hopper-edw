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
package org.apache.hop.datavault.ai;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.ProposedVaultObject;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultClassification;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultClassifier;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultOptions;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultProposal;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Compact source-model classification JSON for Data Vault AI Help. Discovers sibling {@code .hsm}
 * files next to the open {@code .hdv} and runs the same classifier as Generate Data Vault.
 */
public final class SourceToVaultAiContextBuilder {

  private static final int MAX_SIBLING_SOURCE_MODELS = 5;

  private SourceToVaultAiContextBuilder() {}

  public static String buildSourceClassificationContext(
      DataVaultModel vault, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (vault == null || Utils.isEmpty(vault.getFilename())) {
      return "";
    }
    List<String> siblings = listSiblingSourceModels(vault.getFilename());
    if (siblings.isEmpty()) {
      return "";
    }
    StringBuilder json = new StringBuilder();
    json.append("{\"sourceModels\":[");
    int written = 0;
    for (String filename : siblings) {
      if (written >= MAX_SIBLING_SOURCE_MODELS) {
        break;
      }
      if (written > 0) {
        json.append(',');
      }
      appendSourceModelEntry(json, filename, vault, variables, metadataProvider);
      written++;
    }
    json.append("]}");
    return json.toString();
  }

  static List<String> listSiblingSourceModels(String vaultFilename) {
    List<String> files = new ArrayList<>();
    if (Utils.isEmpty(vaultFilename)) {
      return files;
    }
    try {
      FileObject vaultFile = HopVfs.getFileObject(vaultFilename);
      FileObject folder = vaultFile.getParent();
      if (folder == null || !folder.exists() || !folder.isFolder()) {
        return files;
      }
      FileObject[] children = folder.getChildren();
      if (children == null) {
        return files;
      }
      for (FileObject child : children) {
        if (child == null || !child.exists() || child.isFolder()) {
          continue;
        }
        String baseName = child.getName().getBaseName();
        if (baseName != null && baseName.toLowerCase().endsWith(SourceModel.FILE_EXTENSION)) {
          files.add(child.getName().getURI());
        }
      }
    } catch (Exception ignored) {
      // AI context is best-effort; missing folders must not fail the advisor.
    }
    files.sort(String.CASE_INSENSITIVE_ORDER);
    return files;
  }

  static String serializeClassification(
      String filename, String modelName, SourceToVaultClassification classification) {
    StringBuilder json = new StringBuilder();
    json.append("{\"filename\":").append(DvAiContextBuilder.jsonString(filename));
    json.append(",\"name\":").append(DvAiContextBuilder.jsonString(modelName));
    json.append(",\"proposals\":[");
    if (classification != null) {
      List<SourceToVaultProposal> proposals = classification.getProposals();
      for (int i = 0; i < proposals.size(); i++) {
        if (i > 0) {
          json.append(',');
        }
        appendProposal(json, proposals.get(i));
      }
    }
    json.append("]}");
    return json.toString();
  }

  private static void appendSourceModelEntry(
      StringBuilder json,
      String filename,
      DataVaultModel vault,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    try {
      SourceModel source = SourceModelLoadSupport.load(filename, variables, metadataProvider);
      SourceToVaultClassification classification =
          SourceToVaultClassifier.classify(source, null, vault, SourceToVaultOptions.defaults());
      json.append(serializeClassification(filename, source.getName(), classification));
    } catch (Exception e) {
      json.append("{\"filename\":").append(DvAiContextBuilder.jsonString(filename));
      json.append(",\"error\":").append(DvAiContextBuilder.jsonString(e.getMessage()));
      json.append('}');
    }
  }

  private static void appendProposal(StringBuilder json, SourceToVaultProposal proposal) {
    if (proposal == null) {
      json.append("null");
      return;
    }
    json.append("{\"source\":")
        .append(DvAiContextBuilder.jsonString(proposal.getSourceTableName()));
    json.append(",\"role\":")
        .append(
            DvAiContextBuilder.jsonString(
                proposal.getRole() != null ? proposal.getRole().name() : null));
    json.append(",\"confidence\":")
        .append(
            DvAiContextBuilder.jsonString(
                proposal.getConfidence() != null ? proposal.getConfidence().name() : null));
    json.append(",\"implied\":").append(proposal.isImplied());
    if (!Utils.isEmpty(proposal.getEvidence())) {
      json.append(",\"evidence\":").append(DvAiContextBuilder.jsonString(proposal.getEvidence()));
    }
    if (!Utils.isEmpty(proposal.getSkipReason())) {
      json.append(",\"skipReason\":")
          .append(DvAiContextBuilder.jsonString(proposal.getSkipReason()));
    }
    json.append(",\"objects\":[");
    List<ProposedVaultObject> objects = proposal.getObjects();
    for (int i = 0; i < objects.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      appendObject(json, objects.get(i));
    }
    json.append("]}");
  }

  private static void appendObject(StringBuilder json, ProposedVaultObject object) {
    if (object == null) {
      json.append("null");
      return;
    }
    json.append("{\"kind\":")
        .append(
            DvAiContextBuilder.jsonString(
                object.getKind() != null ? object.getKind().name() : null));
    json.append(",\"name\":").append(DvAiContextBuilder.jsonString(object.getName()));
    json.append(",\"included\":").append(object.isIncluded());
    if (object.getSourceKind() != null) {
      json.append(",\"sourceKind\":")
          .append(DvAiContextBuilder.jsonString(object.getSourceKind().name()));
    }
    appendStringArray(json, "businessKeys", object.getBusinessKeyColumns());
    appendStringArray(json, "attributes", object.getSatelliteAttributeColumns());
    appendStringArray(json, "hubs", object.getParticipatingHubNames());
    if (!Utils.isEmpty(object.getParentHubName())) {
      json.append(",\"parentHub\":")
          .append(DvAiContextBuilder.jsonString(object.getParentHubName()));
    }
    if (!Utils.isEmpty(object.getParentLinkName())) {
      json.append(",\"parentLink\":")
          .append(DvAiContextBuilder.jsonString(object.getParentLinkName()));
    }
    if (!Utils.isEmpty(object.getReferencedTableName())) {
      json.append(",\"referencedTable\":")
          .append(DvAiContextBuilder.jsonString(object.getReferencedTableName()));
    }
    json.append('}');
  }

  private static void appendStringArray(StringBuilder json, String field, List<String> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    json.append(",\"").append(field).append("\":[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append(DvAiContextBuilder.jsonString(values.get(i)));
    }
    json.append(']');
  }
}
