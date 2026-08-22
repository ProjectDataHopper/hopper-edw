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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.Date;
import java.util.List;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.ICheckResultSource;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataObject;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

/** Common interface for Kimball tables on a {@link DimensionalModel} canvas. */
@HopMetadataObject(xmlKey = "tableType", objectFactory = IDmTable.DmTableFactory.class)
public interface IDmTable extends IGuiPosition, IBaseMeta, IHasName, IChanged, ICheckResultSource {

  String getName();

  void setName(String name);

  String getTableName();

  void setTableName(String tableName);

  String getDescription();

  void setDescription(String description);

  DmTableType getTableType();

  void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model);

  IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model)
      throws HopException;

  List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model,
      Date loadTimestamp)
      throws HopException;

  List<String> generateBuildDdl(
      IHopMetadataProvider metadataProvider, IVariables variables, DimensionalModel model)
      throws HopException;

  DmSourceConfiguration getSourceOrDefault();

  final class DmTableFactory implements IHopMetadataObjectFactory {

    @Override
    public Object createObject(String id, Object parentObject) throws HopException {
      DmTableType tableType = DmTableType.valueOf(id);
      return switch (tableType) {
        case DIMENSION -> new DmDimension();
        case DIMENSION_ALIAS -> new DmDimensionAlias();
        case JUNK_DIMENSION -> new DmJunkDimension();
        case RANGE_DIMENSION -> new DmRangeDimension();
        case FACT -> new DmFact();
        case FACTLESS_FACT -> new DmFactlessFact();
        case PERIODIC_SNAPSHOT_FACT -> new DmPeriodicSnapshotFact();
        case ACCUMULATING_SNAPSHOT_FACT -> new DmAccumulatingSnapshotFact();
        case BRIDGE -> new DmBridge();
        case AGGREGATE_FACT -> new DmAggregateFact();
      };
    }

    @Override
    public String getObjectId(Object object) throws HopException {
      if (!(object instanceof IDmTable dmTable)) {
        throw new HopException(
            "Object is not of class IDmTable but of " + object.getClass().getName());
      }
      DmTableType tableType = dmTable.getTableType();
      if (tableType == null) {
        throw new HopException("Dimensional table has no table type set");
      }
      return tableType.name();
    }
  }
}
