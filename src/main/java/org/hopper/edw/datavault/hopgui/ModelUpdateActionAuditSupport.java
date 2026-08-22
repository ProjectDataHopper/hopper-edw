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
package org.hopper.edw.datavault.hopgui;

import java.util.HashMap;
import java.util.Map;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.history.AuditManager;
import org.apache.hop.history.AuditState;
import org.apache.hop.workflow.action.ActionMeta;

/** Persists model update workflow action XML in Hop audit state per model file. */
public final class ModelUpdateActionAuditSupport {

  public static final String STATE_ACTION_XML = "actionXml";

  private ModelUpdateActionAuditSupport() {}

  public static String retrieveActionXml(
      String auditGroup, String auditType, String modelFilename) {
    try {
      AuditState auditState =
          AuditManager.getActive()
              .retrieveState(auditGroup, auditType, auditStateName(modelFilename));
      if (auditState == null || auditState.getStateMap() == null) {
        return null;
      }
      Object value = auditState.getStateMap().get(STATE_ACTION_XML);
      return value instanceof String xml ? xml : null;
    } catch (Exception e) {
      LogChannel.UI.logError("Error restoring model update action settings", e);
      return null;
    }
  }

  public static void storeActionXml(
      String auditGroup, String auditType, String modelFilename, ActionMeta actionMeta) {
    try {
      Map<String, Object> stateMap = new HashMap<>();
      stateMap.put(STATE_ACTION_XML, actionMeta.getXml());
      AuditState auditState = new AuditState(auditStateName(modelFilename), stateMap);
      AuditManager.getActive().storeState(auditGroup, auditType, auditState);
    } catch (Exception e) {
      LogChannel.UI.logError("Error storing model update action settings", e);
    }
  }

  private static String auditStateName(String modelFilename) {
    return Utils.isEmpty(modelFilename) ? "<unsaved-model>" : modelFilename;
  }
}
