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
package org.apache.hop.catalog.hopgui.perspective;

import java.util.HashMap;
import java.util.Map;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.history.AuditManager;
import org.apache.hop.history.AuditState;

/** Persists Data Catalog perspective UI state in Hop audit state. */
public final class DataCatalogPerspectiveAuditSupport {

  public static final String AUDIT_GROUP = "DataCatalog";
  public static final String AUDIT_TYPE = "Perspective";
  public static final String AUDIT_STATE_NAME = "data-catalog-perspective";
  public static final String STATE_GROUP_BY_NAMESPACE = "groupByNamespace";

  private DataCatalogPerspectiveAuditSupport() {}

  public static boolean retrieveGroupByNamespace() {
    try {
      AuditState auditState =
          AuditManager.getActive().retrieveState(AUDIT_GROUP, AUDIT_TYPE, AUDIT_STATE_NAME);
      if (auditState == null || auditState.getStateMap() == null) {
        return false;
      }
      Object value = auditState.getStateMap().get(STATE_GROUP_BY_NAMESPACE);
      if (value instanceof Boolean grouped) {
        return grouped;
      }
      return false;
    } catch (Exception e) {
      LogChannel.UI.logError("Error restoring data catalog namespace grouping preference", e);
      return false;
    }
  }

  public static void storeGroupByNamespace(boolean groupByNamespace) {
    try {
      Map<String, Object> stateMap = new HashMap<>();
      stateMap.put(STATE_GROUP_BY_NAMESPACE, groupByNamespace);
      AuditState auditState = new AuditState(AUDIT_STATE_NAME, stateMap);
      AuditManager.getActive().storeState(AUDIT_GROUP, AUDIT_TYPE, auditState);
    } catch (Exception e) {
      LogChannel.UI.logError("Error storing data catalog namespace grouping preference", e);
    }
  }
}
