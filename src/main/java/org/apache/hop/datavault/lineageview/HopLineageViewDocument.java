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
package org.apache.hop.datavault.lineageview;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.base.AbstractMeta;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.IUndo;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.undo.ChangeAction;
import org.apache.hop.datavault.lineage.LineageLayer;
import org.apache.hop.datavault.lineageview.backend.LineageDirection;
import org.apache.hop.datavault.lineageview.backend.LineageGranularity;
import org.apache.hop.datavault.lineageview.backend.LineageGraphLayer;
import org.apache.hop.datavault.lineageview.backend.LineageSeedKind;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;

/** Authorable Hop Lineage View definition. The graph itself is not persisted. */
@Getter
@Setter
public class HopLineageViewDocument extends HopMetadataBase
    implements IHopMetadata, IHasFilename, IUndo {

  /**
   * Runtime open path ({@link IHasFilename}). Never serialized — loaders bind this from the VFS
   * path used to open/save.
   */
  private String filename;

  @HopMetadataProperty private String description;

  @HopMetadataProperty private String backendName;

  @HopMetadataProperty(storeWithCode = true)
  private LineageSeedKind seedKind = LineageSeedKind.MODEL_TABLE;

  @HopMetadataProperty private String datasetNamespace;
  @HopMetadataProperty private String datasetName;
  @HopMetadataProperty private String jobNamespace;
  @HopMetadataProperty private String jobName;

  /**
   * Bare {@link LineageLayer} is not {@code IEnumHasCode}. Do not set {@code storeWithCode} —
   * serializes as {@code Enum.name()}.
   */
  @HopMetadataProperty private LineageLayer modelLayer;

  @HopMetadataProperty private String modelName;
  @HopMetadataProperty private String logicalTable;
  @HopMetadataProperty private String modelFilename;
  @HopMetadataProperty private String columnName;

  @HopMetadataProperty(storeWithCode = true)
  private LineageDirection direction = LineageDirection.UPSTREAM;

  @HopMetadataProperty private int depth = 6;

  @HopMetadataProperty(storeWithCode = true)
  private LineageGranularity granularity = LineageGranularity.TABLE;

  @HopMetadataProperty private boolean includeJobs = true;
  @HopMetadataProperty private boolean includeOpsOverlay = true;

  @HopMetadataProperty(key = "layerFilter", groupKey = "layerFilters", storeWithCode = true)
  private List<LineageGraphLayer> layerFilters = new ArrayList<>();

  @HopMetadataProperty private String resourceGroup;

  public List<LineageGraphLayer> getLayerFiltersOrEmpty() {
    if (layerFilters == null) {
      layerFilters = new ArrayList<>();
    }
    return layerFilters;
  }

  /**
   * Display name is the {@code .hlv} basename when a file is open. There is no separate authorable
   * name field.
   */
  @Override
  public String getName() {
    return AbstractMeta.extractNameFromFilename(true, name, filename, ".hlv");
  }

  @Override
  public void addUndo(
      Object[] from,
      Object[] to,
      int[] pos,
      Point[] prev,
      Point[] curr,
      int typeOfChange,
      boolean nextAlso) {}

  @Override
  public int getMaxUndo() {
    return 0;
  }

  @Override
  public void setMaxUndo(int mu) {}

  @Override
  public ChangeAction previousUndo() {
    return null;
  }

  @Override
  public ChangeAction viewThisUndo() {
    return null;
  }

  @Override
  public ChangeAction viewPreviousUndo() {
    return null;
  }

  @Override
  public ChangeAction nextUndo() {
    return null;
  }

  @Override
  public ChangeAction viewNextUndo() {
    return null;
  }
}
