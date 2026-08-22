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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Unit tests for {@link DvReferenceTable} metadata, factory, and check validation. */
class DvReferenceTableTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void defaults() {
    DvReferenceTable ref = new DvReferenceTable("ref_country");
    assertEquals(DvTableType.REFERENCE, ref.getTableType());
    assertEquals(DvReferenceLoadMode.FULL_REPLACE, ref.getLoadMode());
    assertNotNull(ref.getNaturalKeys());
    assertTrue(ref.getNaturalKeys().isEmpty());
    assertNotNull(ref.getAttributes());
    assertTrue(ref.getAttributes().isEmpty());
    assertNotNull(ref.getRecordSources());
    assertTrue(ref.getRecordSources().isEmpty());
  }

  @Test
  void factoryCreatesReferenceTable() throws Exception {
    Object created = new IDvTable.DvTableFactory().createObject(DvTableType.REFERENCE.name(), null);
    assertInstanceOf(DvReferenceTable.class, created);
    assertEquals(DvTableType.REFERENCE.name(), new IDvTable.DvTableFactory().getObjectId(created));
  }

  @Test
  void xmlRoundTripPreservesReferenceFields() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setName("ref-model");

    DvHub hub = new DvHub("hub_customer");
    hub.setTableName("hub_customer");
    model.getTables().add(hub);

    DvReferenceTable ref = sampleReference("ref_country");
    model.getTables().add(ref);

    String xml =
        XmlHandler.aroundTag(HopVaultFileType.XML_TAG, XmlMetadataUtil.serializeObjectToXml(model));
    assertTrue(xml.contains("REFERENCE"), () -> xml);
    assertTrue(xml.contains("FULL_REPLACE") || xml.contains("loadMode"), () -> xml);
    assertTrue(xml.contains("code") || xml.contains("naturalKeys"), () -> xml);

    Document document = XmlHandler.loadXmlString(xml);
    Node root = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel restored = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(root, DataVaultModel.class, restored, null);

    assertEquals(2, restored.getTables().size());
    DvReferenceTable restoredRef = restored.findReferenceTable("ref_country");
    assertNotNull(restoredRef);
    assertInstanceOf(DvReferenceTable.class, restoredRef);
    assertEquals(DvTableType.REFERENCE, restoredRef.getTableType());
    assertEquals(DvReferenceLoadMode.FULL_REPLACE, restoredRef.getLoadMode());
    assertEquals("ref_ami_land", restoredRef.getTableName());
    assertEquals(1, restoredRef.getNaturalKeys().size());
    assertEquals("code", restoredRef.getNaturalKeys().get(0).getName());
    assertEquals("String", restoredRef.getNaturalKeys().get(0).getDataType());
    assertEquals("CRM-country", restoredRef.getNaturalKeys().get(0).getRecordSourceName());
    assertEquals(1, restoredRef.getAttributes().size());
    assertEquals("name", restoredRef.getAttributes().get(0).getName());
    assertEquals(List.of("CRM-country"), restoredRef.getRecordSources());
    assertNotNull(restored.findHub("hub_customer"));
  }

  @Test
  void findReferenceTableIsCaseInsensitive() {
    DataVaultModel model = new DataVaultModel();
    DvReferenceTable ref = new DvReferenceTable("ref_Country");
    model.getTables().add(ref);
    assertSame(ref, model.findReferenceTable("REF_country"));
    assertNull(model.findReferenceTable("missing"));
  }

  @Test
  void checkFailsWithoutNaturalKeys() {
    DvReferenceTable ref = new DvReferenceTable("ref_empty");
    ref.setRecordSources(List.of("CRM-country"));
    List<ICheckResult> remarks = new ArrayList<>();
    ref.check(
        remarks,
        new MemoryMetadataProvider(),
        new Variables(),
        DvModelCheckOptions.fastOnly(),
        null);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().toLowerCase().contains("natural key")),
        () -> remarks.toString());
  }

  @Test
  void checkFailsWithoutRecordSourceWhenHopManaged() {
    DvReferenceTable ref = sampleReference("ref_country");
    ref.setRecordSources(new ArrayList<>());
    List<ICheckResult> remarks = new ArrayList<>();
    ref.check(
        remarks,
        new MemoryMetadataProvider(),
        new Variables(),
        DvModelCheckOptions.fastOnly(),
        null);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().toLowerCase().contains("record source")),
        () -> remarks.toString());
  }

  @Test
  void checkFailsOnDuplicateNaturalKeyAndAttributeCollision() {
    DvReferenceTable ref = new DvReferenceTable("ref_bad");
    ref.setRecordSources(List.of("src"));
    BusinessKey k1 = new BusinessKey("code");
    BusinessKey k2 = new BusinessKey("code");
    ref.setNaturalKeys(List.of(k1, k2));
    SatelliteAttribute attr = new SatelliteAttribute();
    attr.setName("code");
    ref.setAttributes(List.of(attr));

    List<ICheckResult> remarks = new ArrayList<>();
    ref.check(
        remarks,
        new MemoryMetadataProvider(),
        new Variables(),
        DvModelCheckOptions.fastOnly(),
        null);

    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().toLowerCase().contains("duplicate natural")),
        () -> remarks.toString());
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().toLowerCase().contains("collides with a natural")),
        () -> remarks.toString());
  }

  @Test
  void checkFailsWhenNaturalKeyCollidesWithLoadDate() {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    config.setLoadDateField("LOAD_DATE");
    config.setRecordSourceField("RECORD_SOURCE");

    DvReferenceTable ref = new DvReferenceTable("ref_bad_std");
    ref.setRecordSources(List.of("src"));
    BusinessKey key = new BusinessKey("LOAD_DATE");
    ref.setNaturalKeys(List.of(key));

    List<ICheckResult> remarks = new ArrayList<>();
    ref.check(
        remarks,
        new MemoryMetadataProvider(),
        new Variables(),
        DvModelCheckOptions.fastOnly(),
        model);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().toLowerCase().contains("standard")),
        () -> remarks.toString());
  }

  @Test
  void checkPassesForValidReference() {
    DataVaultModel model = new DataVaultModel();
    DvReferenceTable ref = sampleReference("ref_country");
    List<ICheckResult> remarks = new ArrayList<>();
    ref.check(
        remarks,
        new MemoryMetadataProvider(),
        new Variables(),
        DvModelCheckOptions.fastOnly(),
        model);
    assertFalse(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR),
        () -> remarks.toString());
  }

  @Test
  void loadModeLookupDefaultsToFullReplace() {
    assertEquals(DvReferenceLoadMode.FULL_REPLACE, DvReferenceLoadMode.lookupCode(null));
    assertEquals(
        DvReferenceLoadMode.DELETE_INSERT, DvReferenceLoadMode.lookupCode("DELETE_INSERT"));
  }

  @Test
  void targetTableLayoutIsNaturalKeysAttributesLoadDateRecordSource() throws Exception {
    DataVaultModel model = new DataVaultModel();
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    config.setLoadDateField("LOAD_DATE");
    config.setRecordSourceField("RECORD_SOURCE");

    DvReferenceTable ref = sampleReference("ref_country");
    IRowMeta layout =
        ref.getTargetTableLayout(new MemoryMetadataProvider(), new Variables(), model);
    assertNotNull(layout);
    assertEquals(
        List.of("code", "name", "LOAD_DATE", "RECORD_SOURCE"),
        java.util.Arrays.stream(layout.getFieldNames()).toList());
    assertFalse(
        java.util.Arrays.stream(layout.getFieldNames())
            .anyMatch(n -> n.toLowerCase().contains("hash") || n.toLowerCase().contains("hkey")));
  }

  private static DvReferenceTable sampleReference(String name) {
    DvReferenceTable ref = new DvReferenceTable(name);
    ref.setTableName("ref_ami_land");
    ref.setDescription("Country codes (reference data)");
    ref.setLoadMode(DvReferenceLoadMode.FULL_REPLACE);
    ref.setRecordSources(new ArrayList<>(List.of("CRM-country")));

    BusinessKey code = new BusinessKey("code");
    code.setDescription("Country code");
    code.setDataType("String");
    code.setLength("3");
    code.setSourceFieldName("code");
    code.setRecordSourceName("CRM-country");
    ref.setNaturalKeys(new ArrayList<>(List.of(code)));

    SatelliteAttribute nameAttr = new SatelliteAttribute();
    nameAttr.setName("name");
    nameAttr.setDescription("Country name");
    nameAttr.setDataType("String");
    nameAttr.setLength("100");
    ref.setAttributes(new ArrayList<>(List.of(nameAttr)));
    return ref;
  }
}
