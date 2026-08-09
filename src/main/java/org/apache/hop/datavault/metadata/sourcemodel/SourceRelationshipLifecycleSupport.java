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
package org.apache.hop.datavault.metadata.sourcemodel;

import org.apache.hop.core.util.Utils;

/**
 * Keeps {@link SourceRelationship} lists consistent when endpoints are renamed, deleted, or
 * already dangling after a model load.
 *
 * <p>Relationships store endpoint names as strings. Renaming a table / query / JSON / pipeline
 * without updating those strings leaves edges that fail validation, cannot be drawn (missing
 * canvas bounds), and are hard to select. Policy for this plugin: <strong>drop</strong> edges that
 * reference the old name rather than rewriting them — intentional renames of join endpoints are
 * rare enough that re-drawing the relationship is safer than guessing column renames.
 */
public final class SourceRelationshipLifecycleSupport {

  private SourceRelationshipLifecycleSupport() {}

  /**
   * Whether {@code relationship} uses {@code endpointName} as child or parent with the given kind.
   */
  public static boolean referencesEndpoint(
      SourceRelationship relationship, SourceEndpointKind kind, String endpointName) {
    if (relationship == null || Utils.isEmpty(endpointName)) {
      return false;
    }
    SourceEndpointKind resolved = kind != null ? kind : SourceEndpointKind.TABLE;
    if (endpointName.equals(relationship.getChildTableName())
        && resolved == relationship.resolveChildEndpointKind()) {
      return true;
    }
    return endpointName.equals(relationship.getParentTableName())
        && resolved == relationship.resolveParentEndpointKind();
  }

  /**
   * Remove every relationship that references {@code endpointName} as child or parent of {@code
   * kind}.
   *
   * @return number of relationships removed
   */
  public static int removeRelationshipsReferencing(
      SourceModel model, SourceEndpointKind kind, String endpointName) {
    if (model == null || Utils.isEmpty(endpointName)) {
      return 0;
    }
    int before = model.getRelationships().size();
    model
        .getRelationships()
        .removeIf(r -> referencesEndpoint(r, kind, endpointName));
    return Math.max(0, before - model.getRelationships().size());
  }

  /**
   * When an endpoint is renamed, drop relationships that still point at {@code oldName}. No-op when
   * names are equal (after trim) or blank.
   *
   * @return number of relationships removed
   */
  public static int dropRelationshipsOnRename(
      SourceModel model, SourceEndpointKind kind, String oldName, String newName) {
    if (model == null) {
      return 0;
    }
    String oldTrimmed = oldName != null ? oldName.trim() : "";
    String newTrimmed = newName != null ? newName.trim() : "";
    if (Utils.isEmpty(oldTrimmed) || oldTrimmed.equals(newTrimmed)) {
      return 0;
    }
    return removeRelationshipsReferencing(model, kind, oldTrimmed);
  }

  /**
   * Drop relationships whose child or parent endpoint no longer exists on the model (orphans after
   * rename/delete that were saved to disk).
   *
   * @return number of relationships removed
   */
  public static int removeDanglingRelationships(SourceModel model) {
    if (model == null) {
      return 0;
    }
    int before = model.getRelationships().size();
    model
        .getRelationships()
        .removeIf(
            r ->
                r == null
                    || !SourceEndpointSupport.exists(
                        model, r.resolveChildEndpointKind(), r.getChildTableName())
                    || !SourceEndpointSupport.exists(
                        model, r.resolveParentEndpointKind(), r.getParentTableName()));
    return Math.max(0, before - model.getRelationships().size());
  }
}
