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
package org.apache.hop.datavault.hopgui.perspective.journey;

import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;

/** Stable tree node ids for selection restore and {@code select(group, nodeId)}. */
public final class EdwJourneyIds {

  private EdwJourneyIds() {}

  public static String group(String groupName) {
    return "group:" + Const.NVL(groupName, "");
  }

  public static String stage(EdwJourneyStage stage) {
    return "stage:" + (stage != null ? stage.id() : "");
  }

  public static String sourceModel(String storedPath) {
    return "source-model:" + normalize(storedPath);
  }

  public static String catalogFeeds() {
    return "catalog-feeds";
  }

  public static String catalogFeed(RecordDefinitionKey key) {
    if (key == null) {
      return "catalog-feed:";
    }
    return "catalog-feed:" + Const.NVL(key.getNamespace(), "") + "/" + Const.NVL(key.getName(), "");
  }

  public static String control(EdwJourneyControl control) {
    return "control:" + (control != null ? control.id() : "");
  }

  public static String catalogVersion(String tag) {
    return "catalog-version:" + Const.NVL(tag, "");
  }

  public static String model(String modelType, String storedPath) {
    return "model:" + Const.NVL(modelType, "") + ":" + normalize(storedPath);
  }

  public static String modelTable(String modelType, String storedPath, String tableName) {
    return "table:"
        + Const.NVL(modelType, "")
        + ":"
        + normalize(storedPath)
        + ":"
        + Const.NVL(tableName, "");
  }

  public static String workflow(String storedPath) {
    return "workflow:" + normalize(storedPath);
  }

  public static String workflowAction(String storedPath, String actionName) {
    return "workflow-action:" + normalize(storedPath) + ":" + Const.NVL(actionName, "");
  }

  public static String outputGroup(String kind) {
    return "outputs:" + Const.NVL(kind, "");
  }

  public static String outputFile(String kind, String storedPath) {
    return "output:" + Const.NVL(kind, "") + ":" + normalize(storedPath);
  }

  static String normalize(String storedPath) {
    if (Utils.isEmpty(storedPath)) {
      return "";
    }
    return storedPath.replace('\\', '/');
  }
}
