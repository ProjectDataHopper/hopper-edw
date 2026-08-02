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

package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.pipeline.PipelineMeta;
import org.junit.jupiter.api.Test;

class DvUpdateExecutionSupportTest {

  @Test
  void freePipelinePhasesRunHubsThenLinksThenSatellites() {
    DvUpdateExecutionSupport.FreePipelineBuckets buckets =
        new DvUpdateExecutionSupport.FreePipelineBuckets();
    PipelineMeta hub = named("hub-a");
    PipelineMeta link = named("link-b");
    PipelineMeta sat = named("sat-c");

    // Intentionally add in reverse dependency order
    buckets.add(DvTableType.SATELLITE, List.of(sat));
    buckets.add(DvTableType.LINK, List.of(link));
    buckets.add(DvTableType.HUB, List.of(hub));

    List<List<PipelineMeta>> phases = buckets.phases();
    assertEquals(3, phases.size());
    assertEquals("hub-a", phases.get(0).get(0).getName());
    assertEquals("link-b", phases.get(1).get(0).getName());
    assertEquals("sat-c", phases.get(2).get(0).getName());

    List<PipelineMeta> flat = buckets.flattenedInDependencyOrder();
    assertEquals(List.of("hub-a", "link-b", "sat-c"), flat.stream().map(PipelineMeta::getName).toList());
    assertTrue(!buckets.isEmpty());
    assertEquals(3, buckets.size());
  }

  private static PipelineMeta named(String name) {
    PipelineMeta p = new PipelineMeta();
    p.setName(name);
    return p;
  }
}
