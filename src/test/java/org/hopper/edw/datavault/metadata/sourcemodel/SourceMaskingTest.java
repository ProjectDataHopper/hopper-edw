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
package org.hopper.edw.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.pipeline.transforms.maskfields.MaskingPattern;
import org.apache.hop.pipeline.transforms.maskfields.MaskingStorage;
import org.apache.hop.pipeline.transforms.maskfields.MaskingToken;
import org.apache.hop.pipeline.transforms.maskfields.MaskingValueSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

class SourceMaskingTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void xmlRoundTripPreservesMaskingCard() throws Exception {
    SourceModel original = new SourceModel();
    original.setName("crm");
    SourceTable table = new SourceTable("customer");
    table.getColumns().add(new SourceColumn("customer_id"));
    original.getTables().add(table);

    SourceMasking masking = new SourceMasking("customer_masked");
    masking.setParentSourceKind(SourceMaskingParentKind.TABLE);
    masking.setParentSourceName("customer");
    SourceMaskingField id = new SourceMaskingField("customer_id");
    id.setHopType(IValueMeta.TYPE_INTEGER);
    id.setPrimaryKeyPosition(1);
    SourceMaskingField name = new SourceMaskingField("first_name");
    name.setHopType(IValueMeta.TYPE_STRING);
    name.setPatternName("First name");
    masking.getFields().add(id);
    masking.getFields().add(name);
    original.getMaskingSources().add(masking);

    String xml =
        XmlHandler.aroundTag(SourceModel.XML_TAG, XmlMetadataUtil.serializeObjectToXml(original));
    Node rootNode = XmlHandler.getSubNode(XmlHandler.loadXmlString(xml), SourceModel.XML_TAG);
    SourceModel restored = new SourceModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, SourceModel.class, restored, null);

    SourceMasking loaded = restored.findMaskingSource("customer_masked");
    assertEquals(SourceMaskingParentKind.TABLE, loaded.resolveParentSourceKind());
    assertEquals("customer", loaded.getParentSourceName());
    assertEquals(2, loaded.getFields().size());
    assertEquals("First name", loaded.getFields().get(1).getPatternName());
    assertEquals(1, loaded.getFields().get(0).getPrimaryKeyPosition());
  }

  @Test
  void checkReportsMissingParentDuplicateFieldAndUnstableKey() throws Exception {
    SourceModel model = new SourceModel();
    SourceTable table = new SourceTable("customer");
    SourceColumn id = new SourceColumn("customer_id");
    id.setHopType(IValueMeta.TYPE_INTEGER);
    id.setPrimaryKeyPosition(1);
    table.getColumns().add(id);
    model.getTables().add(table);

    SourceMasking masking = new SourceMasking("customer_masked");
    masking.setParentSourceName("missing");
    SourceMaskingField first = new SourceMaskingField("customer_id");
    first.setHopType(IValueMeta.TYPE_INTEGER);
    first.setPrimaryKeyPosition(1);
    first.setPatternName("Seq");
    SourceMaskingField second = new SourceMaskingField("customer_id");
    second.setHopType(IValueMeta.TYPE_INTEGER);
    second.setPatternName("Seq");
    masking.getFields().add(first);
    masking.getFields().add(second);
    model.getMaskingSources().add(masking);

    MaskingPattern pattern = new MaskingPattern();
    pattern.setName("Seq");
    pattern.setValueSource(MaskingValueSource.SYNTHETIC);
    pattern.setToken(MaskingToken.SEQUENCE);
    pattern.setStorage(MaskingStorage.NONE);
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(MaskingPattern.class).save(pattern);

    List<ICheckResult> remarks =
        SourceMaskingValidationSupport.check(masking, model, null, provider);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("missing")));
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("more than once")));
    assertTrue(
        remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_WARNING));
  }

  @Test
  void checkRejectsSyntheticDate() throws Exception {
    SourceModel model = new SourceModel();
    SourceMasking masking = new SourceMasking("events");
    masking.setParentSourceKind(SourceMaskingParentKind.TABLE);
    masking.setParentSourceName("events");
    SourceTable table = new SourceTable("events");
    model.getTables().add(table);
    SourceMaskingField born = new SourceMaskingField("born");
    born.setHopType(IValueMeta.TYPE_DATE);
    born.setPatternName("Born");
    masking.getFields().add(born);

    MaskingPattern pattern = new MaskingPattern();
    pattern.setName("Born");
    pattern.setValueSource(MaskingValueSource.SYNTHETIC);
    pattern.setToken(MaskingToken.SEQUENCE);
    pattern.setStorage(MaskingStorage.MEMORY);
    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    provider.getSerializer(MaskingPattern.class).save(pattern);

    List<ICheckResult> remarks =
        SourceMaskingValidationSupport.check(masking, model, null, provider);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText().contains("born")));
  }
}
