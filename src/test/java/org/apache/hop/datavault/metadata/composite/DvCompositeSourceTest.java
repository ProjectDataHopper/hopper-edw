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
package org.apache.hop.datavault.metadata.composite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.DvSourceType;
import org.apache.hop.datavault.metadata.IDvSource;
import org.apache.hop.datavault.metadata.sourcemodel.SourceQueryGenerationMode;
import org.junit.jupiter.api.Test;

class DvCompositeSourceTest {

  @Test
  void factoryCreatesCompositeSource() throws Exception {
    IDvSource source = (IDvSource) new IDvSource.DvSourceFactory().createObject("COMPOSITE", null);
    assertInstanceOf(DvCompositeSource.class, source);
    assertEquals(DvSourceType.COMPOSITE, source.getSourceType());
  }

  @Test
  void resolveFallsBackToCachedSql() throws Exception {
    DvCompositeSource composite = new DvCompositeSource();
    composite.setGeneratedSql("SELECT 1 AS id");
    // No model file → cache path.
    DvCompositeSourceResolver.ResolvedComposite resolved =
        DvCompositeSourceResolver.resolve(composite, new Variables(), null);
    assertEquals(SourceQueryGenerationMode.SQL, resolved.effectiveMode());
    assertTrue(resolved.usedCachedSql());
    assertEquals("SELECT 1 AS id", resolved.sql());
  }

  @Test
  void resolveRequiresModelOrCache() {
    DvCompositeSource composite = new DvCompositeSource();
    HopException ex =
        assertThrows(
            HopException.class,
            () -> DvCompositeSourceResolver.resolve(composite, new Variables(), null));
    assertTrue(
        ex.getMessage().toLowerCase().contains("missing") || ex.getMessage().contains("cache"));
  }
}
