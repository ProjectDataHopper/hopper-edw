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
package org.hopper.edw.datavault.hopgui.perspective.journey.panel;

/** One engineer dashboard under the architecture poster. */
public enum EdwJourneyPanelId {
  PROJECT_PULSE,
  SOURCE_DRIFT,
  HARVEST_WATCH,
  GATE_DESK,
  WAVE_BOARD,
  RAW_VAULT,
  BUSINESS_VAULT,
  DIMENSIONAL,
  CONTRACT,
  LINEAGE_OPS,
  NEXT_MOVE,
  CONSUMPTION;

  public static EdwJourneyPanelId forBlock(String blockId) {
    if (blockId == null) {
      return PROJECT_PULSE;
    }
    return switch (blockId) {
      case "landing", "source-models" -> SOURCE_DRIFT;
      case "harvester" -> HARVEST_WATCH;
      case "gates" -> GATE_DESK;
      case "publish", "hop-engine" -> WAVE_BOARD;
      case "dv-models", "dv-db" -> RAW_VAULT;
      case "bv-models", "bv-db" -> BUSINESS_VAULT;
      case "dm-models", "dm-db" -> DIMENSIONAL;
      case "catalog" -> CONTRACT;
      case "ops", "lineage-view" -> LINEAGE_OPS;
      case "coach" -> NEXT_MOVE;
      case "presentations", "jdbc-clients", "hop-jdbc", "clients" -> CONSUMPTION;
      default -> PROJECT_PULSE;
    };
  }

  public static EdwJourneyPanelId fromWire(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    try {
      return EdwJourneyPanelId.valueOf(id.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
