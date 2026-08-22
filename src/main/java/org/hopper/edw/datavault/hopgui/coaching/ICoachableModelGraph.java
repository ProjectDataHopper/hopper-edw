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
package org.hopper.edw.datavault.hopgui.coaching;

import org.apache.hop.core.gui.Point;
import org.hopper.edw.datavault.metadata.coaching.CoachingSourceRef;
import org.hopper.edw.datavault.metadata.coaching.ICoachingModelAdapter;

public interface ICoachableModelGraph {

  ICoachingModelAdapter createCoachingModelAdapter();

  void notifyCoachModelChanged();

  /**
   * Creates a table from a coaching source at the given model location.
   *
   * @return the created table name, or {@code null} when creation failed
   */
  String createTableFromCoachSource(
      CoachingSourceRef sourceRef, String tableType, String tableName, Point location);

  void refreshCoachPanel();

  /**
   * Whether this graph can open the source-model → Data Vault review dialog for the current model.
   */
  default boolean canGenerateFromSourceModel() {
    return false;
  }

  /** Opens the source-model → Data Vault review dialog when supported. */
  default void generateFromSourceModel() {
    // Only the Data Vault canvas implements generation.
  }
}
