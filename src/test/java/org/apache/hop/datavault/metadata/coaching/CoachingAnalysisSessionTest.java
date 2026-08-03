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
package org.apache.hop.datavault.metadata.coaching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CoachingAnalysisSessionTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolvesTargetsFromSingleTableScan() throws HopException {
    DataVaultModel model = new DataVaultModel();
    DvHub hub = new DvHub("hub_customer");
    hub.getRecordSources().add("CRM-customer");
    model.getTables().add(hub);
    model
        .getCoachingOrDefault()
        .addCoachingSource(
            CoachingSourceRef.forRecordDefinition(
                "local-catalog", "hop/test/sources", "CRM-customer"));
    model
        .getCoachingOrDefault()
        .addCoachingSource(
            CoachingSourceRef.forRecordDefinition(
                "local-catalog", "hop/test/sources", "CRM-order"));

    DvCoachingModelAdapter adapter = new DvCoachingModelAdapter(model, name -> {}, name -> {});
    CoachingAnalysisSession session = new CoachingAnalysisSession(adapter, new Variables(), null);

    List<CoachingSourceNode> nodes = session.resolve(true);

    assertEquals(2, nodes.size());
    assertEquals(1, nodes.getFirst().getTargetsOrEmpty().size());
    assertEquals("hub_customer", nodes.getFirst().getTargetsOrEmpty().getFirst().getTableName());
    assertTrue(nodes.get(1).getTargetsOrEmpty().isEmpty());
  }

  @Test
  void sourcesOnlySkipsValidationWork() throws HopException {
    DataVaultModel model = new DataVaultModel();
    model
        .getCoachingOrDefault()
        .addCoachingSource(
            CoachingSourceRef.forRecordDefinition(
                "local-catalog", "hop/test/sources", "CRM-customer"));

    AtomicInteger checkCalls = new AtomicInteger();
    DvCoachingModelAdapter adapter =
        new DvCoachingModelAdapter(model, name -> {}, name -> {}) {
          @Override
          public List<CoachingInsight> resolveInsightsForSource(
              CoachingSourceRef sourceRef,
              org.apache.hop.core.variables.IVariables variables,
              org.apache.hop.metadata.api.IHopMetadataProvider metadataProvider)
              throws HopException {
            checkCalls.incrementAndGet();
            return super.resolveInsightsForSource(sourceRef, variables, metadataProvider);
          }
        };

    CoachingAnalysisSession session = new CoachingAnalysisSession(adapter, new Variables(), null);
    List<CoachingSourceNode> nodes = session.resolve(false);

    assertEquals(1, nodes.size());
    assertTrue(nodes.getFirst().getTargetsOrEmpty().isEmpty());
    assertTrue(nodes.getFirst().getInsightsOrEmpty().isEmpty());
    assertEquals(0, checkCalls.get());
  }
}
