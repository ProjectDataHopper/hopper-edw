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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.history.AuditManager;
import org.apache.hop.history.AuditState;
import org.apache.hop.history.local.LocalAuditManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataCatalogPerspectiveAuditSupportTest {

  @TempDir Path testFolder;

  @BeforeEach
  void resetAuditManager() throws HopException {
    AuditManager.getInstance()
        .setActiveAuditManager(new LocalAuditManager(testFolder.toAbsolutePath().toString()));
    AuditManager.clearEvents();
  }

  @Test
  void storeAndRetrieveGroupByNamespacePreference() {
    assertFalse(DataCatalogPerspectiveAuditSupport.retrieveGroupByNamespace());

    DataCatalogPerspectiveAuditSupport.storeGroupByNamespace(true);
    assertTrue(DataCatalogPerspectiveAuditSupport.retrieveGroupByNamespace());

    DataCatalogPerspectiveAuditSupport.storeGroupByNamespace(false);
    assertFalse(DataCatalogPerspectiveAuditSupport.retrieveGroupByNamespace());
  }

  @Test
  void retrieveGroupByNamespaceDefaultsToFalseWhenMissing() throws HopException {
    Map<String, Object> stateMap = new HashMap<>();
    AuditState auditState =
        new AuditState(DataCatalogPerspectiveAuditSupport.AUDIT_STATE_NAME, stateMap);
    AuditManager.getActive()
        .storeState(
            DataCatalogPerspectiveAuditSupport.AUDIT_GROUP,
            DataCatalogPerspectiveAuditSupport.AUDIT_TYPE,
            auditState);

    assertFalse(DataCatalogPerspectiveAuditSupport.retrieveGroupByNamespace());
  }
}
