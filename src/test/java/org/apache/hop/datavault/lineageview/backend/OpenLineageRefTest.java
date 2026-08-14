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
package org.apache.hop.datavault.lineageview.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenLineageRefTest {

  @Test
  void roundTripNodeIdWithSlashesInName() {
    OpenLineageRef ref =
        OpenLineageRef.builder().namespace("retail-job").name("dm/retail-pos/f_orders").build();
    assertTrue(ref.isComplete());
    assertEquals("job:retail-job:dm/retail-pos/f_orders", ref.toNodeId(LineageNodeKind.JOB));
    OpenLineageRef parsed = OpenLineageRef.fromNodeId(ref.toNodeId(LineageNodeKind.JOB));
    assertEquals("retail-job", parsed.getNamespace());
    assertEquals("dm/retail-pos/f_orders", parsed.getName());
  }
}
