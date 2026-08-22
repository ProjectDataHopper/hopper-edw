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
package org.hopper.edw.datavault.hopgui.perspective.journey;

import java.util.List;
import org.hopper.edw.catalog.model.RecordDefinitionKey;

/** One item in the EDW Journey tree. */
public record EdwJourneyTreeNode(
    Kind kind,
    String id,
    String label,
    String description,
    EdwJourneyStage stage,
    EdwJourneyControl control,
    String storedPath,
    String modelType,
    String catalogConnection,
    RecordDefinitionKey catalogKey,
    String tableName,
    String actionType,
    List<EdwJourneyTreeNode> children) {

  public enum Kind {
    GROUP,
    STAGE,
    SOURCE_MODEL,
    CATALOG_FEEDS,
    CATALOG_FEED,
    CONTROL,
    CATALOG_VERSION,
    MODEL,
    MODEL_TABLE,
    WORKFLOW,
    WORKFLOW_ACTION,
    OUTPUT_GROUP,
    OUTPUT_FILE
  }

  public EdwJourneyTreeNode {
    children = children != null ? List.copyOf(children) : List.of();
  }

  public static Builder builder(Kind kind, String id, String label) {
    return new Builder(kind, id, label);
  }

  public static final class Builder {
    private final Kind kind;
    private final String id;
    private final String label;
    private String description;
    private EdwJourneyStage stage;
    private EdwJourneyControl control;
    private String storedPath;
    private String modelType;
    private String catalogConnection;
    private RecordDefinitionKey catalogKey;
    private String tableName;
    private String actionType;
    private List<EdwJourneyTreeNode> children = List.of();

    private Builder(Kind kind, String id, String label) {
      this.kind = kind;
      this.id = id;
      this.label = label;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder stage(EdwJourneyStage stage) {
      this.stage = stage;
      return this;
    }

    public Builder control(EdwJourneyControl control) {
      this.control = control;
      return this;
    }

    public Builder storedPath(String storedPath) {
      this.storedPath = storedPath;
      return this;
    }

    public Builder modelType(String modelType) {
      this.modelType = modelType;
      return this;
    }

    public Builder catalogConnection(String catalogConnection) {
      this.catalogConnection = catalogConnection;
      return this;
    }

    public Builder catalogKey(RecordDefinitionKey catalogKey) {
      this.catalogKey = catalogKey;
      return this;
    }

    public Builder tableName(String tableName) {
      this.tableName = tableName;
      return this;
    }

    public Builder actionType(String actionType) {
      this.actionType = actionType;
      return this;
    }

    public Builder children(List<EdwJourneyTreeNode> children) {
      this.children = children;
      return this;
    }

    public EdwJourneyTreeNode build() {
      return new EdwJourneyTreeNode(
          kind,
          id,
          label,
          description,
          stage,
          control,
          storedPath,
          modelType,
          catalogConnection,
          catalogKey,
          tableName,
          actionType,
          children);
    }
  }
}
