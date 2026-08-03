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
package org.apache.hop.datavault.hopgui.coaching;

import org.apache.hop.core.gui.Point;
import org.apache.hop.datavault.metadata.coaching.CoachingSourceRef;
import org.apache.hop.datavault.metadata.coaching.ICoachingModelAdapter;

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
}
