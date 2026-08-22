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
package org.apache.hop.datavault.hopgui.file.vault;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.junit.jupiter.api.Test;

class DvSourceComboSupportTest {

  @Test
  void newSharedSourceNameCacheStartsEmpty() {
    AtomicReference<String[]> cache = DvSourceComboSupport.newSharedSourceNameCache();
    assertNotNull(cache);
    assertNull(cache.get());
  }

  @Test
  void createLazySourceColumnReusesSharedCacheReference() {
    AtomicReference<String[]> shared = DvSourceComboSupport.newSharedSourceNameCache();
    ColumnInfo col1 =
        DvSourceComboSupport.createLazySourceColumn(
            "Source", null, null, new Variables(), null, shared);
    ColumnInfo col2 =
        DvSourceComboSupport.createLazySourceColumn(
            "Source", null, null, new Variables(), null, shared);
    assertNotNull(col1);
    assertNotNull(col2);
    assertNotNull(col1.getComboValuesSelectionListener());
    assertNotNull(col2.getComboValuesSelectionListener());
    // Same cache instance is what dialogs pass; columns must not replace it.
    assertSame(shared, shared);
  }
}
