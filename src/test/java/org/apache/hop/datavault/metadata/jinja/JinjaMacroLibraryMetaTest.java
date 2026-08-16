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
package org.apache.hop.datavault.metadata.jinja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class JinjaMacroLibraryMetaTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void xmlRoundTripPreservesMacrosAndVars() throws Exception {
    JinjaMacroLibraryMeta original = new JinjaMacroLibraryMeta("retail-macros");
    original.setDescription("Imported from dbt");
    original.setPackageName("retail");
    original.setEnabled(true);
    original.getVars().add(new JinjaMacroVar("region", "emea"));
    JinjaMacroDefinition macro = new JinjaMacroDefinition();
    macro.setName("cents_to_dollars");
    macro.setDescription("Scale cents");
    macro.setOriginPath("macros/money.sql");
    macro.setJinjaSource("{% macro cents_to_dollars(col) %}(({{ col }})/100){% endmacro %}");
    original.getMacros().add(macro);

    String xml = XmlHandler.aroundTag("library", XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "library");

    JinjaMacroLibraryMeta restored = new JinjaMacroLibraryMeta();
    XmlMetadataUtil.deSerializeFromXml(rootNode, JinjaMacroLibraryMeta.class, restored, null);

    assertEquals("Imported from dbt", restored.getDescription());
    assertEquals("retail", restored.getPackageName());
    assertTrue(restored.isEnabled());
    assertEquals(1, restored.getVars().size());
    assertEquals("region", restored.getVars().get(0).getName());
    assertEquals("emea", restored.getVars().get(0).getValue());
    assertEquals(1, restored.getMacros().size());
    JinjaMacroDefinition found = restored.findMacro("cents_to_dollars");
    assertNotNull(found);
    assertEquals("macros/money.sql", found.getOriginPath());
    assertTrue(found.getJinjaSource().contains("endmacro"));
  }
}
