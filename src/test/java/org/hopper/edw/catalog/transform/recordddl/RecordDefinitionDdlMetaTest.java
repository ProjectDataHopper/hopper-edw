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
package org.hopper.edw.catalog.transform.recordddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecordDefinitionDdlMetaTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void getFieldsUsesDefaultsWhenOutputFieldNamesAreBlank() throws HopException {
    RecordDefinitionDdlMeta meta = new RecordDefinitionDdlMeta();
    meta.setSelectFromInput(true);
    meta.setOutputDdlField("");
    meta.setOutputStatusField("");

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("namespace", 2));
    rowMeta.addValueMeta(ValueMetaFactory.createValueMeta("name", 2));

    meta.getFields(rowMeta, "Record Definition DDL", null, null, new Variables(), null);

    assertEquals(4, rowMeta.size());
    assertTrue(rowMeta.indexOfValue("ddl") >= 0);
    assertTrue(rowMeta.indexOfValue("ddl_status") >= 0);
  }

  @Test
  void resolveOutputFieldNamesApplyDefaults() {
    RecordDefinitionDdlMeta meta = new RecordDefinitionDdlMeta();
    meta.setOutputDdlField("");
    meta.setOutputStatusField(null);

    assertEquals("ddl", meta.resolveOutputDdlField(new Variables()));
    assertEquals("ddl_status", meta.resolveOutputStatusField(new Variables()));
  }

  @Test
  void failIfNoFieldsDefaultsToTrueAndClones() {
    RecordDefinitionDdlMeta meta = new RecordDefinitionDdlMeta();
    assertTrue(meta.isFailIfNoFields());

    meta.setFailIfNoFields(false);
    meta.setTargetTypeMappingName("postgres-target-type-rules");
    RecordDefinitionDdlMeta clone = meta.clone();
    assertEquals(false, clone.isFailIfNoFields());
    assertEquals("postgres-target-type-rules", clone.getTargetTypeMappingName());

    RecordDefinitionDdlMeta defaults = new RecordDefinitionDdlMeta();
    assertTrue(defaults.clone().isFailIfNoFields());
  }
}
