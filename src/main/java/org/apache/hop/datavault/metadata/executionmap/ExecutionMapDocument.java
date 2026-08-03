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
package org.apache.hop.datavault.metadata.executionmap;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.gui.IUndo;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.undo.ChangeAction;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;

/** Read-only graph of connected Hop workflows, pipelines, models, and generated loaders. */
@Getter
@Setter
public class ExecutionMapDocument extends HopMetadataBase
    implements IHopMetadata, IHasFilename, IUndo {

  /**
   * Runtime open path ({@link IHasFilename}), like {@code AbstractMeta}. Never serialized — loaders
   * bind this from the VFS path used to open/save.
   */
  private String filename;

  @HopMetadataProperty private String rootArtifactPath;

  @HopMetadataProperty(storeWithCode = true)
  private ExecutionMapRootArtifactType rootArtifactType = ExecutionMapRootArtifactType.WORKFLOW;

  @HopMetadataProperty private Date crawledAt;

  @HopMetadataProperty private String hopProject;

  @HopMetadataProperty private List<ExecutionMapNode> nodes = new ArrayList<>();

  @HopMetadataProperty private List<ExecutionMapEdge> edges = new ArrayList<>();

  @HopMetadataProperty private List<ExecutionMapArtifactSnapshot> snapshots = new ArrayList<>();

  public List<ExecutionMapNode> getNodesOrEmpty() {
    if (nodes == null) {
      nodes = new ArrayList<>();
    }
    return nodes;
  }

  public List<ExecutionMapEdge> getEdgesOrEmpty() {
    if (edges == null) {
      edges = new ArrayList<>();
    }
    return edges;
  }

  public List<ExecutionMapArtifactSnapshot> getSnapshotsOrEmpty() {
    if (snapshots == null) {
      snapshots = new ArrayList<>();
    }
    return snapshots;
  }

  public ExecutionMapNode findNodeById(String nodeId) {
    if (nodeId == null) {
      return null;
    }
    for (ExecutionMapNode node : getNodesOrEmpty()) {
      if (node != null && nodeId.equals(node.getId())) {
        return node;
      }
    }
    return null;
  }

  public Point getMaximum() {
    int maxX = 0;
    int maxY = 0;
    for (ExecutionMapNode node : getNodesOrEmpty()) {
      if (node == null || node.getLocation() == null) {
        continue;
      }
      maxX = Math.max(maxX, node.getLocation().x + 200);
      maxY = Math.max(maxY, node.getLocation().y + 80);
    }
    return new Point(Math.max(maxX, 800), Math.max(maxY, 600));
  }

  public ExecutionMapArtifactSnapshot findSnapshotById(String snapshotId) {
    if (snapshotId == null) {
      return null;
    }
    for (ExecutionMapArtifactSnapshot snapshot : getSnapshotsOrEmpty()) {
      if (snapshot != null && snapshotId.equals(snapshot.getId())) {
        return snapshot;
      }
    }
    return null;
  }

  @Override
  public void addUndo(
      Object[] from,
      Object[] to,
      int[] pos,
      Point[] prev,
      Point[] curr,
      int typeOfChange,
      boolean nextAlso) {
    // read-only document
  }

  @Override
  public int getMaxUndo() {
    return 0;
  }

  @Override
  public void setMaxUndo(int mu) {
    // read-only document
  }

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
