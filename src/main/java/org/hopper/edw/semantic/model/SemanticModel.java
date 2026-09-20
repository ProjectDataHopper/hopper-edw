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
package org.hopper.edw.semantic.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.changed.IChanged;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadata;

/** Consumption semantic layer: logical entities over a warehouse star (or other bindings). */
@Getter
@Setter
public class SemanticModel extends HopMetadataBase
    implements IHopMetadata, IHasName, IHasFilename, IChanged {

  public static final String XML_TAG = "semantic-model";
  public static final String FILE_EXTENSION = ".hsl";

  private String filename;

  @HopMetadataProperty private String description;

  /**
   * Path to the `.hdm` this layer is bound to. Several `.hsl` files may point at the same
   * dimensional model.
   */
  @HopMetadataProperty private String dimensionalModelFilename;

  @HopMetadataProperty private String targetDatabase;

  @HopMetadataProperty(key = "entity", groupKey = "entities")
  private List<SemanticEntity> entities = new ArrayList<>();

  @HopMetadataProperty(key = "relationship", groupKey = "relationships")
  private List<SemanticRelationship> relationships = new ArrayList<>();

  @HopMetadataProperty(key = "selection", groupKey = "selections")
  private List<SemanticSelection> selections = new ArrayList<>();

  private final ChangedFlag changedFlag = new ChangedFlag();

  public SemanticEntity findEntity(String entityName) {
    if (Utils.isEmpty(entityName) || entities == null) {
      return null;
    }
    for (SemanticEntity entity : entities) {
      if (entity != null && entityName.equals(entity.getName())) {
        return entity;
      }
    }
    return null;
  }

  public List<SemanticEntity> listFactEntities() {
    List<SemanticEntity> facts = new ArrayList<>();
    if (entities == null) {
      return facts;
    }
    for (SemanticEntity entity : entities) {
      if (entity != null && entity.resolveRole().isFact()) {
        facts.add(entity);
      }
    }
    return facts;
  }

  public SemanticSelection findSelection(String selectionName) {
    if (Utils.isEmpty(selectionName) || selections == null) {
      return null;
    }
    for (SemanticSelection selection : selections) {
      if (selection != null && selectionName.equals(selection.getName())) {
        return selection;
      }
    }
    return null;
  }

  public List<SemanticRelationship> relationshipsFrom(String entityName) {
    List<SemanticRelationship> found = new ArrayList<>();
    if (Utils.isEmpty(entityName) || relationships == null) {
      return found;
    }
    for (SemanticRelationship relationship : relationships) {
      if (relationship != null && entityName.equals(relationship.getFromEntity())) {
        found.add(relationship);
      }
    }
    return found;
  }

  public void putSelection(SemanticSelection selection) {
    if (selection == null || Utils.isEmpty(selection.getName())) {
      return;
    }
    if (selections == null) {
      selections = new ArrayList<>();
    }
    for (int i = 0; i < selections.size(); i++) {
      SemanticSelection existing = selections.get(i);
      if (existing != null && selection.getName().equals(existing.getName())) {
        selections.set(i, selection);
        setChanged();
        return;
      }
    }
    selections.add(selection);
    setChanged();
  }

  @Override
  public boolean hasChanged() {
    return changedFlag.hasChanged();
  }

  @Override
  public void setChanged(boolean changed) {
    changedFlag.setChanged(changed);
  }

  @Override
  public void setChanged() {
    changedFlag.setChanged();
  }

  @Override
  public void clearChanged() {
    changedFlag.clearChanged();
  }
}
