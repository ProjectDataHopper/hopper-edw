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
package org.apache.hop.catalog.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RowMetaCatalogSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  private static final String MINIMAL_LOAD_RUN_XML =
      "<row-meta><value-meta><type>String</type><name>run_id</name><length>64</length></value-meta>"
          + "<value-meta><type>Date</type><name>started_at</name></value-meta>"
          + "<value-meta><type>Integer</type><name>error_count</name></value-meta></row-meta>";

  @Test
  void parsesHandAuthoredOperationsRowMetaXml() throws Exception {
    IRowMeta rowMeta = RowMetaCatalogSupport.fromXml(MINIMAL_LOAD_RUN_XML);
    assertNotNull(rowMeta);
    assertEquals(3, rowMeta.size());
    assertEquals(ValueMetaString.TYPE_STRING, rowMeta.getValueMeta(0).getType());
    assertEquals(ValueMetaInteger.TYPE_INTEGER, rowMeta.getValueMeta(2).getType());
  }
}
