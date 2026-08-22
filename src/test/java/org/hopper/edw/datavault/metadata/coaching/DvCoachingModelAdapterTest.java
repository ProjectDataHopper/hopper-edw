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
package org.hopper.edw.datavault.metadata.coaching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvCoachingModelAdapterTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesTargetsForAttachedHubSource() {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    hub.getRecordSources().add("CRM-customer");
    model.getTables().add(hub);

    model
        .getCoachingOrDefault()
        .addCoachingSource(
            CoachingSourceRef.forRecordDefinition(
                "local-catalog", "hop/test/sources", "CRM-customer"));

    DvCoachingModelAdapter adapter = new DvCoachingModelAdapter(model, name -> {}, name -> {});
    CoachingSourceRef sourceRef = adapter.resolveCoachingSources(new Variables(), null).getFirst();
    List<CoachingTargetUsage> targets =
        adapter.resolveTargetsForSource(sourceRef, new Variables(), null);

    assertEquals(1, targets.size());
    assertEquals("hub_customer", targets.getFirst().getTableName());
    assertEquals(DvTableType.HUB.name(), targets.getFirst().getTableRole());
  }

  @Test
  void addsInsightWhenSourceUnmapped() throws HopException {
    DataVaultModel model = new DataVaultModel();
    model
        .getCoachingOrDefault()
        .addCoachingSource(
            CoachingSourceRef.forRecordDefinition(
                "local-catalog", "hop/test/sources", "CRM-customer"));
    DvCoachingModelAdapter adapter = new DvCoachingModelAdapter(model, name -> {}, name -> {});
    CoachingSourceRef sourceRef = adapter.resolveCoachingSources(new Variables(), null).getFirst();
    List<CoachingInsight> insights =
        adapter.resolveInsightsForSource(sourceRef, new Variables(), null);
    assertFalse(insights.isEmpty());
    assertTrue(insights.stream().anyMatch(insight -> insight.getMessage().contains("Generate DV")));
  }
}
