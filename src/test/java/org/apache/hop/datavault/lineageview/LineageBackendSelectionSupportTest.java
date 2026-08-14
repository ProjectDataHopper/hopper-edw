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
package org.apache.hop.datavault.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.datavault.metadata.lineage.LineageBackendMeta;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LineageBackendSelectionSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void keepsCurrentName() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    save(provider, "a", true);
    save(provider, "b", true);
    assertEquals("b", LineageBackendSelectionSupport.defaultBackendName(provider, "b"));
  }

  @Test
  void usesSingleEnabledBackend() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    save(provider, "only", true);
    save(provider, "off", false);
    assertEquals("only", LineageBackendSelectionSupport.defaultBackendName(provider, null));
  }

  @Test
  void asksWhenSeveralAreEnabled() throws Exception {
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    save(provider, "a", true);
    save(provider, "b", true);
    assertNull(LineageBackendSelectionSupport.defaultBackendName(provider, null));
    assertNull(LineageBackendSelectionSupport.defaultBackendName(provider, ""));
  }

  private static void save(MemoryMetadataProvider provider, String name, boolean enabled)
      throws HopException {
    LineageBackendMeta meta = new LineageBackendMeta(name);
    meta.setEnabled(enabled);
    provider.getSerializer(LineageBackendMeta.class).save(meta);
  }
}
