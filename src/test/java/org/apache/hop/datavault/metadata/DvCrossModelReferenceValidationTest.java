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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.vault.DvLinkedTableNavigationSupport;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DvCrossModelReferenceValidationTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void clearModelCache() {
    DvModelLoadSupport.clearCache();
  }

  @Test
  void validExternalReferencePassesCheck() throws Exception {
    DataVaultModel model = loadSubjectFixture();
    model.setFilename(
        Path.of("integration-tests/tests/basic/cross-model-dv-subject.hdv")
            .toAbsolutePath()
            .normalize()
            .toString());
    DvLinkedTable reference = (DvLinkedTable) model.findTable("hub_customer");

    List<ICheckResult> remarks = new ArrayList<>();
    DvLinkedTableValidationSupport.validateLinkedTable(
        remarks, reference, model, null, new Variables());

    assertFalse(hasError(remarks));
  }

  @Test
  void linkResolvesAllReferencedHubs() throws Exception {
    DataVaultModel model = loadSubjectFixture();
    model.setFilename(
        Path.of("integration-tests/tests/basic/cross-model-dv-subject.hdv")
            .toAbsolutePath()
            .normalize()
            .toString());
    Variables variables = new Variables();
    DvLink link = model.findLink("lnk_customer_order");

    assertNotNull(link);
    for (String hubName : link.getHubNames()) {
      assertNotNull(
          DvTableResolutionSupport.resolveHub(model, hubName, variables, null),
          "Expected hub '" + hubName + "' to resolve through cross-model reference");
    }
  }

  @Test
  void resolveHubFindsExternalHubThroughReference() throws Exception {
    DataVaultModel model = loadSubjectFixture();
    model.setFilename(
        Path.of("integration-tests/tests/basic/cross-model-dv-subject.hdv")
            .toAbsolutePath()
            .normalize()
            .toString());
    Variables variables = new Variables();

    DvHub hub = DvTableResolutionSupport.resolveHub(model, "hub_customer", variables, null);

    assertNotNull(hub);
    assertTrue(hub.getBusinessKeys() != null && !hub.getBusinessKeys().isEmpty());
    assertTrue("customer_hk".equals(hub.getHashKeyFieldName()));
  }

  @Test
  void canNavigateToExternalSourceTable() throws Exception {
    DataVaultModel model = loadSubjectFixture();
    model.setFilename(
        Path.of("integration-tests/tests/basic/cross-model-dv-subject.hdv")
            .toAbsolutePath()
            .normalize()
            .toString());
    DvLinkedTable reference = (DvLinkedTable) model.findTable("hub_customer");
    Variables variables = new Variables();

    assertTrue(
        DvLinkedTableNavigationSupport.canNavigateToSourceTable(model, reference, variables, null));
  }

  @Test
  void missingExternalModelReportsError() {
    DataVaultModel model = new DataVaultModel();
    model.setFilename("/tmp/subject-area.hdv");
    DvLinkedTable reference = new DvLinkedTable();
    reference.setName("hub_customer");
    reference.setReferencedTableName("hub_customer");
    reference.setReferencedModelFilename("missing-shared.hdv");
    reference.setReferencedTableType(DvTableType.HUB);
    model.getTables().add(reference);

    List<ICheckResult> remarks = new ArrayList<>();
    DvLinkedTableValidationSupport.validateLinkedTable(
        remarks, reference, model, null, new Variables());

    assertTrue(hasError(remarks));
  }

  private static boolean hasError(List<ICheckResult> remarks) {
    return remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  private static DataVaultModel loadSubjectFixture() throws Exception {
    Path fixture =
        Path.of("integration-tests/tests/basic/cross-model-dv-subject.hdv")
            .toAbsolutePath()
            .normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toString());
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel model = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, null);
    return model;
  }
}
