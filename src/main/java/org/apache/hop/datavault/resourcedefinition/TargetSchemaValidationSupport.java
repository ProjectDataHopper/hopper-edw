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

package org.apache.hop.datavault.resourcedefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueKind;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.IssueSeverity;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ProposalType;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RecordDefinitionValidation;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.RemediationProposal;
import org.apache.hop.datavault.resourcedefinition.ValidationReport.ValidationIssue;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Detects DV target tables whose physical layout lags the model (DDL required). Does not invent
 * field lengths from the database for catalog remediation.
 */
public final class TargetSchemaValidationSupport {

  private static final Class<?> PKG = TargetSchemaValidationSupport.class;

  private TargetSchemaValidationSupport() {}

  public static ValidationReport enrich(
      ValidationReport report,
      ValidationModels models,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (report == null || models == null) {
      return report;
    }
    ValidationReport enriched = new ValidationReport(report.getGroupName());
    Set<String> tablesChecked = new LinkedHashSet<>();

    for (RecordDefinitionValidation existing : report.getRecordValidations()) {
      if (existing == null) {
        continue;
      }
      List<ValidationIssue> extra = new ArrayList<>();
      if (existing.usages() != null) {
        for (SourceUsage usage : existing.usages()) {
          if (usage == null
              || !SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usage.modelType())
              || Utils.isEmpty(usage.modelFilename())
              || Utils.isEmpty(usage.modelElementName())) {
            continue;
          }
          String tableKey = usage.modelFilename() + "#" + usage.modelElementName();
          if (!tablesChecked.add(tableKey)) {
            continue;
          }
          try {
            DataVaultModel model =
                ResourceDefinitionGroupResolver.loadDataVaultModel(
                    usage.modelFilename(), variables, metadataProvider);
            IDvTable table = model.findTable(usage.modelElementName());
            if (!(table instanceof DvSatellite satellite)) {
              continue;
            }
            List<String> ddl =
                satellite.generateUpdateDdl(metadataProvider, variables, model);
            if (ddl != null && !ddl.isEmpty()) {
              String preview = String.join("; ", ddl.subList(0, Math.min(3, ddl.size())));
              if (ddl.size() > 3) {
                preview = preview + "; ...";
              }
              String message =
                  BaseMessages.getString(
                      PKG,
                      "TargetSchemaValidationSupport.Issue.DdlRequired",
                      Const.NVL(satellite.getName(), usage.modelElementName()),
                      Const.NVL(usage.modelName(), usage.modelFilename()),
                      Integer.toString(ddl.size()),
                      preview);
              extra.add(
                  new ValidationIssue(
                      ValidationIssueSupport.buildIssueId(
                          IssueKind.TARGET_DDL_REQUIRED, satellite.getName(), "ddl"),
                      IssueKind.TARGET_DDL_REQUIRED,
                      IssueSeverity.WARNING,
                      null,
                      message,
                      List.of(
                          new RemediationProposal(
                              ProposalType.GENERATE_TARGET_DDL_PACKAGE,
                              BaseMessages.getString(
                                  PKG, "TargetSchemaValidationSupport.Proposal.Summary"),
                              BaseMessages.getString(
                                  PKG,
                                  "TargetSchemaValidationSupport.Proposal.Details",
                                  satellite.getName(),
                                  Integer.toString(ddl.size()))))));
            }
          } catch (HopException e) {
            extra.add(
                new ValidationIssue(
                    ValidationIssueSupport.buildIssueId(
                        IssueKind.TARGET_DDL_REQUIRED, usage.modelElementName(), "ddl-check-failed"),
                    IssueKind.TARGET_DDL_REQUIRED,
                    IssueSeverity.WARNING,
                    null,
                    BaseMessages.getString(
                        PKG,
                        "TargetSchemaValidationSupport.Issue.DdlCheckFailed",
                        Const.NVL(usage.modelElementName(), "?"),
                        Const.NVL(e.getMessage(), e.getClass().getSimpleName())),
                    List.of(
                        new RemediationProposal(
                            ProposalType.BLOCK_UPDATE_UNTIL_RESOLVED,
                            BaseMessages.getString(
                                PKG, "TargetSchemaValidationSupport.Proposal.CheckFailed.Summary"),
                            Const.NVL(e.getMessage(), "")))));
          }
        }
      }
      enriched.addRecordValidation(mergeIssues(existing, extra));
    }
    return enriched;
  }

  private static RecordDefinitionValidation mergeIssues(
      RecordDefinitionValidation existing, List<ValidationIssue> extra) {
    if (extra == null || extra.isEmpty()) {
      return existing;
    }
    List<ValidationIssue> all = new ArrayList<>(existing.allIssues());
    all.addAll(extra);
    List<ValidationIssue> visible = new ArrayList<>(existing.issues());
    visible.addAll(extra);
    boolean inSync = existing.inSync() && visible.isEmpty();
    return new RecordDefinitionValidation(
        existing.key(),
        existing.catalogConnection(),
        existing.sourceType(),
        inSync,
        existing.schemaDiff(),
        existing.usages(),
        all,
        visible,
        existing.acknowledgedIssueCount());
  }
}
