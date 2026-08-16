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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.xp.RegisterModelConfigurationMetadataExtensionPoint;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class ModelConfigurationResolverTest {

  private MemoryMetadataProvider metadataProvider;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterModelConfigurationMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() {
    metadataProvider = new MemoryMetadataProvider();
  }

  @Test
  void inlineConfigurationIsUsedWhenNameIsEmpty() {
    DataVaultModel model = new DataVaultModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    ModelConfigurationResolver.attach(model, metadataProvider);
    assertEquals("Vault", model.getConfigurationOrDefault().getTargetDatabase());
  }

  @Test
  void namedConfigurationWinsOverInline() throws Exception {
    DataVaultConfiguration shared = new DataVaultConfiguration();
    shared.setName("data-vault");
    shared.setTargetDatabase("SharedVault");
    shared.setHashAlgorithm("SHA256");
    metadataProvider.getSerializer(DataVaultConfiguration.class).save(shared);

    DataVaultModel model = new DataVaultModel();
    model.setConfigurationName("data-vault");
    model.getConfigurationOrDefault().setTargetDatabase("InlineVault");
    ModelConfigurationResolver.attach(model, metadataProvider);

    assertEquals("SharedVault", model.getConfigurationOrDefault().getTargetDatabase());
    assertEquals("SHA256", model.getConfigurationOrDefault().getHashAlgorithm());
  }

  @Test
  void namedConfigurationFallsBackToInlineWithoutProvider() {
    DataVaultModel model = new DataVaultModel();
    model.setConfigurationName("missing");
    model.getConfigurationOrDefault().setTargetDatabase("InlineVault");
    assertEquals("InlineVault", model.getConfigurationOrDefault().getTargetDatabase());
  }

  @Test
  void modelCheckErrorsWhenNamedConfigurationIsMissing() {
    DataVaultModel model = new DataVaultModel();
    model.setConfigurationName("does-not-exist");
    List<ICheckResult> remarks = model.check(metadataProvider, new Variables());
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_ERROR
                        && r.getText() != null
                        && r.getText().contains("does-not-exist")));
  }

  @Test
  void modelCheckWarnsWhenInlineIsIgnored() throws Exception {
    DataVaultConfiguration shared = new DataVaultConfiguration();
    shared.setName("data-vault");
    shared.setTargetDatabase("Vault");
    metadataProvider.getSerializer(DataVaultConfiguration.class).save(shared);

    DataVaultModel model = new DataVaultModel();
    model.setConfigurationName("data-vault");
    model.getConfigurationOrDefault().setTargetDatabase("InlineVault");
    List<ICheckResult> remarks = model.check(metadataProvider, new Variables());
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_WARNING
                        && r.getText() != null
                        && r.getText().contains("data-vault")));
  }

  @Test
  void saveOmitsInlineConfigurationWhenNameIsSet() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.setConfigurationName("data-vault");
    model.getConfigurationOrDefault().setTargetDatabase("ShouldNotBeWritten");

    String xml = ModelXmlWriteSupport.formatModelXml(HopVaultFileType.XML_TAG, model, null);
    assertTrue(xml.contains("<configurationName>data-vault</configurationName>"));
    assertFalse(xml.contains("<targetDatabase>ShouldNotBeWritten</targetDatabase>"));
    assertEquals("ShouldNotBeWritten", model.getConfigurationOrDefault().getTargetDatabase());
  }

  @Test
  void saveKeepsInlineConfigurationWhenNameIsEmpty() throws Exception {
    DataVaultModel model = new DataVaultModel();
    model.getConfigurationOrDefault().setTargetDatabase("Vault");
    String xml = ModelXmlWriteSupport.formatModelXml(HopVaultFileType.XML_TAG, model, null);
    assertTrue(xml.contains("<targetDatabase>Vault</targetDatabase>"));
    assertFalse(xml.contains("<configurationName>"));
  }

  @Test
  void applyDefaultNameUsesStandardNameWhenPresent() throws Exception {
    DataVaultConfiguration shared = new DataVaultConfiguration();
    shared.setName(ModelConfigurationResolver.DEFAULT_DATA_VAULT_NAME);
    shared.setTargetDatabase("Vault");
    metadataProvider.getSerializer(DataVaultConfiguration.class).save(shared);

    DataVaultModel model = new DataVaultModel();
    ModelConfigurationResolver.applyDefaultNameIfPresent(model, metadataProvider);
    assertEquals(ModelConfigurationResolver.DEFAULT_DATA_VAULT_NAME, model.getConfigurationName());
  }

  @Test
  void applyDefaultNameUsesOnlyExistingObject() throws Exception {
    DataVaultConfiguration shared = new DataVaultConfiguration();
    shared.setName("retail");
    shared.setTargetDatabase("Vault");
    metadataProvider.getSerializer(DataVaultConfiguration.class).save(shared);

    DataVaultModel model = new DataVaultModel();
    ModelConfigurationResolver.applyDefaultNameIfPresent(model, metadataProvider);
    assertEquals("retail", model.getConfigurationName());
  }

  @Test
  void extractClonesInlineWithoutKeepingTheName() {
    DataVaultConfiguration original = new DataVaultConfiguration();
    original.setName("inline");
    original.setTargetDatabase("Vault");
    DataVaultConfiguration copy = ModelConfigurationExtractSupport.clone(original);
    assertNull(copy.getName());
    assertEquals("Vault", copy.getTargetDatabase());
    assertEquals("inline", original.getName());
  }

  @Test
  void namedConfigurationRoundTripsThroughXml() throws Exception {
    DataVaultConfiguration shared = new DataVaultConfiguration();
    shared.setName("data-vault");
    shared.setTargetDatabase("SharedVault");
    metadataProvider.getSerializer(DataVaultConfiguration.class).save(shared);

    DataVaultModel original = new DataVaultModel();
    original.setConfigurationName("data-vault");
    original.getConfigurationOrDefault().setTargetDatabase("InlineIgnored");

    String xml = ModelXmlWriteSupport.formatModelXml(HopVaultFileType.XML_TAG, original, null);
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
    DataVaultModel restored = new DataVaultModel();
    XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, restored, metadataProvider);
    ModelConfigurationResolver.attach(restored, metadataProvider);

    assertEquals("data-vault", restored.getConfigurationName());
    assertNull(restored.getConfiguration(), "named models must not restore an empty inline copy");
    assertEquals("SharedVault", restored.getConfigurationOrDefault().getTargetDatabase());
  }
}
