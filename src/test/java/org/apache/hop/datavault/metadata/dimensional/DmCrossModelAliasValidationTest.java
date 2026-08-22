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
package org.apache.hop.datavault.metadata.dimensional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class DmCrossModelAliasValidationTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @BeforeEach
  void clearModelCache() {
    DmModelLoadSupport.clearCache();
  }

  @Test
  void validExternalAliasPassesCheck() throws Exception {
    DimensionalModel model = loadCrossModelAliasFixture();
    model.setFilename(
        Path.of("integration-tests/tests/basic/cross-model-alias.hdm")
            .toAbsolutePath()
            .normalize()
            .toString());
    DmDimensionAlias alias = (DmDimensionAlias) model.findTable("dim_customer");

    List<ICheckResult> remarks = new ArrayList<>();
    DmValidationSupport.validateDimensionAlias(remarks, alias, model, null, new Variables());

    assertFalse(hasError(remarks));
  }

  @Test
  void missingExternalModelReportsError() {
    DimensionalModel model = new DimensionalModel();
    model.setFilename("/tmp/subject-area.hdm");
    DmDimensionAlias alias = new DmDimensionAlias();
    alias.setName("dim_customer");
    alias.setReferencedDimensionName("dim_customer");
    alias.setReferencedModelFilename("missing-shared.hdm");
    model.getTables().add(alias);

    List<ICheckResult> remarks = new ArrayList<>();
    DmValidationSupport.validateDimensionAlias(remarks, alias, model, null, new Variables());

    assertTrue(hasError(remarks));
  }

  @Test
  void circularExternalReferenceReportsError() throws Exception {
    Path tempDir = Files.createTempDirectory("dm-cross-model-alias");
    Path sharedPath = tempDir.resolve("shared.hdm");
    Path subjectPath = tempDir.resolve("subject.hdm");
    Files.writeString(
        sharedPath,
        """
        <dimensional-model>
          <configuration>
            <targetDatabase>Vault</targetDatabase>
            <dimKeyField>dim_key</dimKeyField>
            <loadDateField>load_dt</loadDateField>
          </configuration>
          <tables>
            <table>
              <referencedDimensionName>fact_orders</referencedDimensionName>
              <referencedModelFilename>subject.hdm</referencedModelFilename>
              <tableType>DIMENSION_ALIAS</tableType>
              <name>dim_orders</name>
            </table>
            <table>
              <sourceSql>SELECT customer_id FROM stg_customer</sourceSql>
              <scdType>TYPE1</scdType>
              <natural_keys><natural_key><fieldName>customer_id</fieldName></natural_key></natural_keys>
              <attributes/>
              <tableName>d_customer</tableName>
              <tableType>DIMENSION</tableType>
              <name>dim_customer</name>
            </table>
          </tables>
          <name>shared</name>
        </dimensional-model>
        """);
    Files.writeString(
        subjectPath,
        """
        <dimensional-model>
          <configuration>
            <targetDatabase>Vault</targetDatabase>
            <dimKeyField>dim_key</dimKeyField>
            <loadDateField>load_dt</loadDateField>
          </configuration>
          <tables>
            <table>
              <referencedDimensionName>dim_customer</referencedDimensionName>
              <referencedModelFilename>shared.hdm</referencedModelFilename>
              <tableName>d_customer</tableName>
              <tableType>DIMENSION_ALIAS</tableType>
              <name>dim_customer</name>
            </table>
          </tables>
          <name>subject</name>
        </dimensional-model>
        """);

    DimensionalModel subjectModel = loadFixture(subjectPath.toString());
    subjectModel.setFilename(subjectPath.toString());
    DmDimensionAlias alias = (DmDimensionAlias) subjectModel.findTable("dim_customer");

    List<ICheckResult> remarks = new ArrayList<>();
    DmValidationSupport.validateDimensionAlias(remarks, alias, subjectModel, null, new Variables());

    assertTrue(
        remarks.stream()
            .anyMatch(
                remark ->
                    remark.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && remark.getText().contains("circular reference")));
  }

  private static boolean hasError(List<ICheckResult> remarks) {
    return remarks.stream().anyMatch(remark -> remark.getType() == ICheckResult.TYPE_RESULT_ERROR);
  }

  private static DimensionalModel loadCrossModelAliasFixture() throws Exception {
    return loadFixture("integration-tests/tests/basic/cross-model-alias.hdm");
  }

  private static DimensionalModel loadFixture(String path) throws Exception {
    Path fixture = Path.of(path).toAbsolutePath().normalize();
    Document document = XmlHandler.loadXmlFile(fixture.toFile());
    Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
    DimensionalModel model = new DimensionalModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, null);
    return model;
  }
}
