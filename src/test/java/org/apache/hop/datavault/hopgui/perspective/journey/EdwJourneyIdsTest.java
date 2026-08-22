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
package org.apache.hop.datavault.hopgui.perspective.journey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.junit.jupiter.api.Test;

class EdwJourneyIdsTest {

  @Test
  void idsAreStableAcrossPathSeparators() {
    assertEquals(
        EdwJourneyIds.model("DATA_VAULT_MODEL", "${PROJECT_HOME}/models/core.hdv"),
        EdwJourneyIds.model("DATA_VAULT_MODEL", "${PROJECT_HOME}\\models\\core.hdv"));
    RecordDefinitionKey key = new RecordDefinitionKey("hop/demo/sources", "customer");
    assertEquals("catalog-feed:hop/demo/sources/customer", EdwJourneyIds.catalogFeed(key));
    assertEquals("stage:data-vault", EdwJourneyIds.stage(EdwJourneyStage.DATA_VAULT));
    assertEquals("control:harvest", EdwJourneyIds.control(EdwJourneyControl.HARVEST));
  }
}
