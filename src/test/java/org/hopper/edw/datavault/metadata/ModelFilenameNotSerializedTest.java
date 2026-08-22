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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Model open-path ({@code filename}) is runtime-only, like pipeline {@code AbstractMeta} — it must
 * not be written into {@code .hdv}/{@code .hbv}/{@code .hdm}/{@code .hem} XML.
 */
class ModelFilenameNotSerializedTest {

  private static final String HOST_PATH = "/home/other/git/hop-data-vault/models/retail-360.hdv";

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void dataVaultModelDoesNotSerializeFilename() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setName("retail-360");
    model.setFilename(HOST_PATH);

    String xml = XmlMetadataUtil.serializeObjectToXml(model);
    assertFalse(xml.contains("<filename>"), () -> "unexpected filename in XML: " + xml);
    assertFalse(xml.contains(HOST_PATH), () -> "host path leaked into XML: " + xml);
    assertTrue(xml.contains("name_sync_with_filename") || xml.contains("retail-360"));
  }

  @Test
  void businessVaultModelDoesNotSerializeFilename() throws Exception {
    BusinessVaultModel model = new BusinessVaultModel();
    model.setName("retail-sql");
    model.setFilename(HOST_PATH.replace(".hdv", ".hbv"));

    String xml = XmlMetadataUtil.serializeObjectToXml(model);
    assertFalse(xml.contains("<filename>"), () -> "unexpected filename in XML: " + xml);
    assertFalse(xml.contains("/home/other/"), () -> "host path leaked into XML: " + xml);
  }

  @Test
  void dimensionalModelDoesNotSerializeFilename() throws Exception {
    DimensionalModel model = new DimensionalModel();
    model.setName("retail-f-orders");
    model.setFilename(HOST_PATH.replace(".hdv", ".hdm"));

    String xml = XmlMetadataUtil.serializeObjectToXml(model);
    assertFalse(xml.contains("<filename>"), () -> "unexpected filename in XML: " + xml);
    assertFalse(xml.contains("/home/other/"), () -> "host path leaked into XML: " + xml);
  }

  @Test
  void executionMapDocumentDoesNotSerializeFilename() throws Exception {
    ExecutionMapDocument document = new ExecutionMapDocument();
    document.setName("run-map");
    document.setFilename("/tmp/maps/run.hem");

    String xml = XmlMetadataUtil.serializeObjectToXml(document);
    assertFalse(xml.contains("<filename>"), () -> "unexpected filename in XML: " + xml);
    assertFalse(xml.contains("/tmp/maps/"), () -> "host path leaked into XML: " + xml);
  }

  @Test
  void legacyFilenameInXmlIsIgnoredOnDeserialize() throws Exception {
    String legacyXml =
        """
        <data-vault-model>
          <filename>/home/legacy/models/old.hdv</filename>
          <name_sync_with_filename>Y</name_sync_with_filename>
          <name>old</name>
          <tables/>
        </data-vault-model>
        """;
    Document document = XmlHandler.loadXmlString(legacyXml);
    Node root = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(root, DataVaultModel.class, model, null);

    assertNull(model.getFilename(), "legacy <filename> must not be mapped without annotation");
  }

  @Test
  void legacyFilenameInBvAndDmXmlIsIgnored() throws Exception {
    String bvXml =
        """
        <business-vault-model>
          <filename>/home/legacy/models/old.hbv</filename>
          <name_sync_with_filename>Y</name_sync_with_filename>
          <name>old-bv</name>
          <tables/>
        </business-vault-model>
        """;
    Document bvDoc = XmlHandler.loadXmlString(bvXml);
    Node bvRoot = XmlHandler.getSubNode(bvDoc, HopBusinessVaultFileType.XML_TAG);
    BusinessVaultModel bv = new BusinessVaultModel();
    XmlMetadataUtil.deSerializeFromXml(bvRoot, BusinessVaultModel.class, bv, null);
    assertNull(bv.getFilename());

    String dmXml =
        """
        <dimensional-model>
          <filename>/home/legacy/models/old.hdm</filename>
          <name_sync_with_filename>Y</name_sync_with_filename>
          <name>old-dm</name>
          <tables/>
        </dimensional-model>
        """;
    Document dmDoc = XmlHandler.loadXmlString(dmXml);
    Node dmRoot = XmlHandler.getSubNode(dmDoc, HopDimensionalFileType.XML_TAG);
    DimensionalModel dm = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(dmRoot, DimensionalModel.class, dm, null);
    assertNull(dm.getFilename());
  }
}
