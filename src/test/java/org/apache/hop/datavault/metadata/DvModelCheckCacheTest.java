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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class DvModelCheckCacheTest {

  @Test
  void liveFieldsCacheRoundTrip() {
    try (DvModelCheckCache cache = new DvModelCheckCache()) {
      IRowMeta rowMeta = new RowMeta();
      cache.putLiveFields("db|crm|public|customer", rowMeta);
      assertSame(rowMeta, cache.getLiveFields("db|crm|public|customer"));
      assertEquals(1, cache.liveFieldsCacheSize());
    }
  }

  @Test
  void databaseLiveFieldsKeyResolvesVariables() {
    Variables variables = new Variables();
    variables.setVariable("SRC_DB", "crm");
    variables.setVariable("SRC_TABLE", "customer");
    String key =
        DvModelCheckCache.databaseLiveFieldsKey("${SRC_DB}", "public", "${SRC_TABLE}", variables);
    assertEquals("db|crm|public|customer", key);
  }

  @Test
  void forCheckRunClosesCache() {
    DvModelCheckOptions options = DvModelCheckOptions.forCheckRun();
    assertTrue(options.getCache() != null);
    options.getCache().putLiveFields("k", new RowMeta());
    options.close();
    assertNull(options.getCache());
  }

  @Test
  void closedCacheRejectsWrites() {
    DvModelCheckCache cache = new DvModelCheckCache();
    cache.close();
    assertThrows(IllegalStateException.class, () -> cache.putLiveFields("k", new RowMeta()));
  }

  @Test
  void sharedSessionIsNotClosedByDefaultFactorySemantics() {
    DvModelCheckOptions single = DvModelCheckOptions.forCheckRun();
    assertTrue(!single.isSharedSession());
    DvModelCheckOptions shared = DvModelCheckOptions.forSharedCheckSession();
    assertTrue(shared.isSharedSession());
    assertTrue(shared.getCache() != null);
    shared.close();
  }

  @Test
  void catalogSourceNamesRoundTrip() {
    try (DvModelCheckCache cache = new DvModelCheckCache()) {
      cache.putCatalogSourceNames("local-catalog", List.of("a", "b"));
      assertEquals(List.of("a", "b"), cache.getCatalogSourceNames("local-catalog"));
    }
  }
}
