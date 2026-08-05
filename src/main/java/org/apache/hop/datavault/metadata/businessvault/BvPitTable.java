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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Point-in-time table combining a DV hub grain with satellite snapshots. */
@Getter
@Setter
public class BvPitTable extends BvTableBase {

  @HopMetadataProperty private String snapshotDateField = "snapshot_date";

  @HopMetadataProperty(inline = true)
  private BvPitSnapshotSchedule snapshotSchedule;

  public BvPitTable() {
    super(BvTableType.PIT);
  }

  public BvPitSnapshotSchedule getSnapshotScheduleOrDefault() {
    if (snapshotSchedule == null) {
      snapshotSchedule = new BvPitSnapshotSchedule();
    }
    return snapshotSchedule;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    super.check(remarks, metadataProvider, variables, model, dataVaultModel);
    BvPitValidationSupport.validate(
        remarks, this, model, dataVaultModel, metadataProvider, variables);
  }

  @Override
  public List<PipelineMeta> generateBuildPipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    return BvPitPipelineSupport.generateBuildPipelines(
        metadataProvider, variables, model, dataVaultModel, this);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel)
      throws HopException {
    if (dataVaultModel == null) {
      return null;
    }
    BusinessVaultConfiguration bvConfig = model != null ? model.getConfigurationOrDefault() : null;
    return BvPitLayoutSupport.buildTargetTableLayout(this, bvConfig, dataVaultModel, variables);
  }
}
