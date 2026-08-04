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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

/** Dual-read / normalize coverage for TABLE_REFERENCE → LINKED_TABLE rename. */
class DvLinkedTableTypeDualReadTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void parsePersistedMapsLegacyAndCurrent() {
    assertEquals(DvTableType.LINKED_TABLE, DvTableType.parsePersisted("TABLE_REFERENCE"));
    assertEquals(DvTableType.LINKED_TABLE, DvTableType.parsePersisted("LINKED_TABLE"));
    assertTrue(DvTableType.isLinkedTableCode("TABLE_REFERENCE"));
    assertTrue(DvTableType.isLinkedTableCode("LINKED_TABLE"));
  }

  @Test
  void setTableTypeNormalizesLegacyConstant() {
    DvLinkedTable table = new DvLinkedTable();
    table.setTableType(DvTableType.TABLE_REFERENCE);
    assertEquals(DvTableType.LINKED_TABLE, table.getTableType());
  }

  @Test
  void factoryAcceptsBothTypeIds() throws Exception {
    IDvTable.DvTableFactory factory = new IDvTable.DvTableFactory();
    assertInstanceOf(DvLinkedTable.class, factory.createObject("LINKED_TABLE", null));
    assertInstanceOf(DvLinkedTable.class, factory.createObject("TABLE_REFERENCE", null));
    assertEquals(
        "LINKED_TABLE", factory.getObjectId(factory.createObject("TABLE_REFERENCE", null)));
  }

  @Test
  void deserializeLegacyTableTypeXml() throws Exception {
    String xml =
        """
        <table>
          <referencedTableName>hub_customer</referencedTableName>
          <referencedTableType>HUB</referencedTableType>
          <tableName>hub_customer</tableName>
          <name>hub_customer</name>
          <tableType>TABLE_REFERENCE</tableType>
          <integrationMode>HOP_MANAGED</integrationMode>
          <xloc>10</xloc>
          <yloc>20</yloc>
        </table>
        """;
    // Polymorphic path via DataVaultModel list would use factory; here deserialize the concrete
    // class after factory would have chosen DvLinkedTable for TABLE_REFERENCE.
    Node node = org.apache.hop.core.xml.XmlHandler.loadXmlString(xml, "table");
    DvLinkedTable table = XmlMetadataUtil.deSerializeFromXml(node, DvLinkedTable.class, null);
    assertEquals("hub_customer", table.getName());
    // setTableType normalizes legacy TABLE_REFERENCE from the property field
    assertEquals(DvTableType.LINKED_TABLE, table.getTableType());
    assertEquals(DvTableType.HUB, table.getReferencedTableType());
  }
}
