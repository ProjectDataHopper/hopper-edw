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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.pipeline.PipelineMeta;
import org.junit.jupiter.api.Test;

class DvUpdateExecutionSupportTest {

  @Test
  void freePipelinePhasesRunReferencesThenHubsLinksSatellites() {
    DvUpdateExecutionSupport.FreePipelineBuckets buckets =
        new DvUpdateExecutionSupport.FreePipelineBuckets();
    PipelineMeta ref = named("ref-a");
    PipelineMeta hub = named("hub-a");
    PipelineMeta link = named("link-b");
    PipelineMeta sat = named("sat-c");

    // Intentionally add in reverse dependency order
    buckets.add(DvTableType.SATELLITE, List.of(sat));
    buckets.add(DvTableType.LINK, List.of(link));
    buckets.add(DvTableType.HUB, List.of(hub));
    buckets.add(DvTableType.REFERENCE, List.of(ref));

    List<List<PipelineMeta>> phases = buckets.phases();
    assertEquals(4, phases.size());
    assertEquals("ref-a", phases.get(0).get(0).getName());
    assertEquals("hub-a", phases.get(1).get(0).getName());
    assertEquals("link-b", phases.get(2).get(0).getName());
    assertEquals("sat-c", phases.get(3).get(0).getName());

    List<PipelineMeta> flat = buckets.flattenedInDependencyOrder();
    assertEquals(
        List.of("ref-a", "hub-a", "link-b", "sat-c"),
        flat.stream().map(PipelineMeta::getName).toList());
    assertTrue(!buckets.isEmpty());
    assertEquals(4, buckets.size());
  }

  @Test
  void orderTablesPutsReferencesFirst() {
    DvHub hub = new DvHub("hub_customer");
    DvReferenceTable ref = new DvReferenceTable("ref_country");
    DvSatellite sat = new DvSatellite("sat_customer");
    List<IDvTable> ordered =
        DvUpdateExecutionSupport.orderTablesForPipelineExecution(List.of(sat, hub, ref));
    assertEquals(
        List.of("ref_country", "hub_customer", "sat_customer"),
        ordered.stream().map(IDvTable::getName).toList());
  }

  private static PipelineMeta named(String name) {
    PipelineMeta p = new PipelineMeta();
    p.setName(name);
    return p;
  }
}
