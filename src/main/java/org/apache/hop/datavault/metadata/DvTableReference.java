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

package org.apache.hop.datavault.metadata;

import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
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
 * Read-only canvas reference to a Hub, Link, or Satellite table defined in another {@code .hdv}
 * file. Used to split large Data Vault models into subject-area files without duplicating metadata.
 */
@Getter
@Setter
public class DvTableReference extends DvTableBase {

  private static final Class<?> PKG = DvTableReference.class;

  @HopMetadataProperty private String referencedTableName;

  /** Optional path to another .hdv file containing the referenced table. */
  @HopMetadataProperty private String referencedModelFilename;

  /** Type of the referenced table (HUB, LINK, or SATELLITE). */
  @HopMetadataProperty(storeWithCode = true)
  private DvTableType referencedTableType;

  public DvTableReference() {
    super();
    this.tableType = DvTableType.TABLE_REFERENCE;
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
    DvReferenceValidationSupport.validateTableReference(
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
      throw new HopException(
          "Table reference '" + getName() + "' has no resolvable target table");
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