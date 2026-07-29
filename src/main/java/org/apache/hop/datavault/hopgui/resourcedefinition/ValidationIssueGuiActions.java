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
 *
 */

package org.apache.hop.datavault.hopgui.resourcedefinition;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.hopgui.perspective.DataCatalogPerspective;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.resourcedefinition.SourceUsage;
import org.apache.hop.datavault.resourcedefinition.ValidationAcknowledgementSupport;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.hopgui.HopGui;

/** Shared non-widget actions for validation issue navigation and acknowledgement. */
public final class ValidationIssueGuiActions {

  private ValidationIssueGuiActions() {}

  /**
   * Returns model usages relevant to the issue field (all usages when the issue has no field name).
   */
  public static List<SourceUsage> filterUsages(
      RecordDefinitionValidation validation, ValidationIssue issue) {
    if (validation == null || validation.usages() == null || validation.usages().isEmpty()) {
      return List.of();
    }
    String fieldName = issue != null ? issue.fieldName() : null;
    if (Utils.isEmpty(fieldName)) {
      return List.copyOf(validation.usages());
    }
    List<SourceUsage> filtered = new ArrayList<>();
    for (SourceUsage usage : validation.usages()) {
      if (usage != null && usage.mappedFields() != null && usage.mappedFields().contains(fieldName)) {
        filtered.add(usage);
      }
    }
    return filtered;
  }

  public static RecordDefinition loadDefinition(
      RecordDefinitionValidation validation,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (validation == null
        || validation.key() == null
        || Utils.isEmpty(validation.catalogConnection())) {
      return null;
    }
    return RecordDefinitionRegistry.getInstance()
        .read(validation.catalogConnection(), validation.key(), variables, metadataProvider);
  }

  public static void openSourceInCatalog(RecordDefinitionValidation validation)
      throws HopException {
    if (validation == null || validation.key() == null) {
      return;
    }
    DataCatalogPerspective perspective = DataCatalogPerspective.getInstance();
    if (perspective != null) {
      perspective.selectRecordDefinition(validation.catalogConnection(), validation.key());
    }
  }

  public static void openTargetUsage(HopGui hopGui, SourceUsage usage, IVariables variables)
      throws HopException {
    ResourceDefinitionModelNavigationSupport.openUsage(hopGui, usage, variables);
  }

  public static void acknowledge(
      RecordDefinitionValidation validation,
      ValidationIssue issue,
      String comment,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (validation == null || issue == null) {
      return;
    }
    RecordDefinition definition = loadDefinition(validation, variables, metadataProvider);
    ValidationAcknowledgementSupport.acknowledge(
        validation.catalogConnection(),
        definition,
        issue.issueId(),
        comment,
        ValidationAcknowledgementSupport.resolveAcknowledgedBy(),
        variables,
        metadataProvider);
  }

  public static void revoke(
      RecordDefinitionValidation validation,
      ValidationIssue issue,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (validation == null || issue == null) {
      return;
    }
    RecordDefinition definition = loadDefinition(validation, variables, metadataProvider);
    ValidationAcknowledgementSupport.revoke(
        validation.catalogConnection(),
        definition,
        issue.issueId(),
        variables,
        metadataProvider);
  }
}
