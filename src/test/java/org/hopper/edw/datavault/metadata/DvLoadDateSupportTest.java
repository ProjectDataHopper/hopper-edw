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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvLoadDateSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void emptyConfigUsesCurrentDate() throws HopException {
    Date before = new Date();
    Date resolved = DvLoadDateSupport.resolveLoadDate(null, new Variables());
    Date after = new Date();

    assertNotNull(resolved);
    assertFalse(resolved.before(before));
    assertFalse(resolved.after(after));
    assertFalse(DvLoadDateSupport.isConfigured(null, new Variables()));
    assertFalse(DvLoadDateSupport.isConfigured("  ", new Variables()));
  }

  private String formatLoadDate(Date date) throws HopException {
    ValueMetaDate valueMeta = new ValueMetaDate("loadDate");
    valueMeta.setConversionMask(DvLoadDateSupport.LOAD_DATE_FORMAT_MASK);
    return valueMeta.getString(date);
  }

  @Test
  void parsesConfiguredLoadDate() throws HopException {
    Date resolved = DvLoadDateSupport.resolveLoadDate("2024/01/01 00:00:00.000", new Variables());

    assertEquals("2024/01/01 00:00:00.000", formatLoadDate(resolved));
    assertTrue(DvLoadDateSupport.isConfigured("2024/01/01 00:00:00.000", new Variables()));
  }

  @Test
  void resolvesVariables() throws HopException {
    Variables variables = new Variables();
    variables.setVariable("DV_INITIAL_LOAD_DATE", "2024/01/01 00:00:00.000");

    Date resolved = DvLoadDateSupport.resolveLoadDate("${DV_INITIAL_LOAD_DATE}", variables);

    assertEquals("2024/01/01 00:00:00.000", formatLoadDate(resolved));
  }

  @Test
  void invalidFormatThrows() {
    assertThrows(
        HopException.class, () -> DvLoadDateSupport.resolveLoadDate("2024-01-01", new Variables()));
  }
}
