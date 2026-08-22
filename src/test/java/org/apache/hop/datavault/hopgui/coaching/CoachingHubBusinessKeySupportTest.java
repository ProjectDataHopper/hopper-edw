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
package org.apache.hop.datavault.hopgui.coaching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CoachingHubBusinessKeySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void populatesBusinessKeysFromPrimaryKeyMetadata() throws HopException {
    SourceField customerId = new SourceField("customer_id");
    customerId.setPrimaryKeyPosition(1);
    customerId.setSourceDataType("Integer");

    DvDatabaseSource databaseSource = new DvDatabaseSource();
    databaseSource.setFields(List.of(customerId));
    DataVaultSource source = new DataVaultSource("CRM-customer");
    source.setSource(databaseSource);

    DvHub hub = new DvHub("customer");
    CoachingHubBusinessKeySupport.populateBusinessKeysFromSource(hub, source, "CRM-customer", null);

    assertEquals(1, hub.getBusinessKeys().size());
    BusinessKey businessKey = hub.getBusinessKeys().getFirst();
    assertEquals("customer_id", businessKey.getName());
    assertEquals("customer_id", businessKey.getSourceFieldName());
    assertEquals("CRM-customer", businessKey.getRecordSourceName());
  }

  @Test
  void leavesExistingBusinessKeysUntouched() throws HopException {
    DvHub hub = new DvHub("customer");
    hub.setBusinessKeys(List.of(new BusinessKey("existing_key")));

    SourceField customerId = new SourceField("customer_id");
    customerId.setPrimaryKeyPosition(1);
    DvDatabaseSource databaseSource = new DvDatabaseSource();
    databaseSource.setFields(List.of(customerId));
    DataVaultSource source = new DataVaultSource("CRM-customer");
    source.setSource(databaseSource);

    CoachingHubBusinessKeySupport.populateBusinessKeysFromSource(hub, source, "CRM-customer", null);

    assertEquals(1, hub.getBusinessKeys().size());
    assertEquals("existing_key", hub.getBusinessKeys().getFirst().getName());
  }
}
