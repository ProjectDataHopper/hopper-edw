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
package org.apache.hop.catalog.impl.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecordDefinitionDocumentRowMetaTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void toleratesMinimalHandAuthoredRowMetaXml() throws Exception {
    RecordDefinitionDocument document = new RecordDefinitionDocument();
    document.setNamespace("hop/test/operations");
    document.setName("load_run");
    document.setType("PHYSICAL_TABLE");
    document.setRowMetaXml(
        "<row-meta><value-meta><type>Integer</type><name>error_count</name></value-meta></row-meta>");

    RecordDefinition definition = document.toRecordDefinition();
    assertNotNull(definition.getFields());
    assertEquals(1, definition.getFields().size());
  }
}
