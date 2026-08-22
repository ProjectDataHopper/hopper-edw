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
package org.hopper.edw.datavault.metadata;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.workflow.WorkflowMeta;

/**
 * Read-only canvas reference or role-playing alias for a Hub, Link, or Satellite.
 *
 * <p>Cross-model references point at a table in another {@code .hdv} file (optional {@link
 * #referencedModelFilename}). Same-model hub aliases leave the path empty and may use a distinct
 * canvas name plus {@link #hashKeyFieldName} so a link can participate the same physical hub more
 * than once with different roles and source mappings.
 */
@Getter
@Setter
public class DvLinkedTable extends DvTableBase {

  private static final Class<?> PKG = DvLinkedTable.class;

  @HopMetadataProperty private String referencedTableName;

  /** Optional path to another .hdv file containing the referenced table. */
  @HopMetadataProperty private String referencedModelFilename;

  /** Type of the referenced table (HUB, LINK, or SATELLITE). */
  @HopMetadataProperty(storeWithCode = true)
  private DvTableType referencedTableType;

  /**
   * Optional role-specific hash key column name used when this reference/alias participates in a
   * link. Required for role-playing the same physical hub more than once (e.g. {@code
   * primary_rep_hk} / {@code secondary_rep_hk}). When empty, the physical hub's hash key field name
   * is used (or a name derived from this alias).
   */
  @HopMetadataProperty private String hashKeyFieldName;

  public DvLinkedTable() {
    super();
    this.tableType = DvTableType.LINKED_TABLE;
  }

  public void syncPhysicalTableName(
      DataVaultModel model, IVariables variables, IHopMetadataProvider metadataProvider) {
    IDvTable target =
        DvTableResolutionSupport.resolveReferenceTarget(model, this, variables, metadataProvider);
    if (target != null && !Utils.isEmpty(target.getTableName())) {
      setTableName(target.getTableName());
    } else if (target != null && !Utils.isEmpty(target.getName())) {
      setTableName(target.getName());
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DvModelCheckOptions options,
      DataVaultModel model) {
    if (Utils.isEmpty(getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvTableBase.CheckResult.NoName"),
              this));
    }
    syncPhysicalTableName(model, variables, metadataProvider);
    DvLinkedTableValidationSupport.validateLinkedTable(
        remarks, this, model, metadataProvider, variables);
    if (Utils.isEmpty(getTableName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvTableBase.CheckResult.NoTableName"),
              this));
    }
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException {
    IDvTable target =
        DvTableResolutionSupport.resolveReferenceTarget(model, this, variables, metadataProvider);
    if (target == null) {
      throw new HopException("Linked table '" + getName() + "' has no resolvable target table");
    }
    return target.getTargetTableLayout(metadataProvider, variables, model);
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      String recordSourceGroup)
      throws HopException {
    return List.of();
  }

  @Override
  public List<WorkflowMeta> generateUpdateWorkflows(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      String recordSourceGroup)
      throws HopException {
    return List.of();
  }

  @Override
  public List<String> generateUpdateDdl(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException {
    return List.of();
  }

  @Override
  public int ensureSpecialRecords(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      ILoggingObject loggingObject)
      throws HopException {
    return 0;
  }
}
