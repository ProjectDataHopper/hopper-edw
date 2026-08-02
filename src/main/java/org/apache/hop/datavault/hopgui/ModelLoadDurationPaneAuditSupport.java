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
 *
 */

package org.apache.hop.datavault.hopgui;

import java.util.HashMap;
import java.util.Map;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.history.AuditManager;
import org.apache.hop.history.AuditState;

/** Persists load duration pane visibility in Hop audit state per model file. */
public final class ModelLoadDurationPaneAuditSupport {

  public static final String AUDIT_GROUP = "DataVault";
  public static final String AUDIT_TYPE = "LoadDurationPane";
  public static final String STATE_VISIBLE = "visible";

  private ModelLoadDurationPaneAuditSupport() {}

  public static boolean retrievePanelVisible(String modelFilename) {
    return retrievePanelVisible(modelFilename, true);
  }

  /**
   * @param defaultVisible value when no audit preference has been stored yet
   */
  public static boolean retrievePanelVisible(String modelFilename, boolean defaultVisible) {
    try {
      AuditState auditState =
          AuditManager.getActive()
              .retrieveState(AUDIT_GROUP, AUDIT_TYPE, auditStateName(modelFilename));
      if (auditState == null || auditState.getStateMap() == null) {
        return defaultVisible;
      }
      Object value = auditState.getStateMap().get(STATE_VISIBLE);
      if (value instanceof Boolean visible) {
        return visible;
      }
      return defaultVisible;
    } catch (Exception e) {
      LogChannel.UI.logError("Error restoring load duration pane visibility", e);
      return defaultVisible;
    }
  }

  public static void storePanelVisible(String modelFilename, boolean visible) {
    try {
      Map<String, Object> stateMap = new HashMap<>();
      stateMap.put(STATE_VISIBLE, visible);
      AuditState auditState = new AuditState(auditStateName(modelFilename), stateMap);
      AuditManager.getActive().storeState(AUDIT_GROUP, AUDIT_TYPE, auditState);
    } catch (Exception e) {
      LogChannel.UI.logError("Error storing load duration pane visibility", e);
    }
  }

  private static String auditStateName(String modelFilename) {
    return Utils.isEmpty(modelFilename) ? "<unsaved-model>" : modelFilename;
  }
}