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
package org.hopper.edw.datavault.catalog.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.json.JsonMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionQuery;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.hopper.edw.datavault.catalog.DvCatalogPublisher;
import org.hopper.edw.datavault.catalog.DvSourceCatalogService;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Smoke runner for catalog publishing from a Data Vault model (same path as {@code
 * ActionDataVaultUpdate} with update data catalog enabled). Invoked from {@code
 * integration-tests/tests/catalog/publish-vault1-catalog.sh}.
 */
public final class DvCatalogPublishSmoke {

  private static final String CONNECTION_NAME = "vault-catalog";

  private DvCatalogPublishSmoke() {}

  public static void main(String[] args) throws Exception {
    String projectHome = args.length > 0 ? args[0] : "project";
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", projectHome);

    HopEnvironment.init();
    PluginRegistry registry = PluginRegistry.getInstance();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, variables, registry);

    JsonMetadataProvider metadataProvider =
        new JsonMetadataProvider(null, projectHome + "/metadata", variables);

    Path catalogRoot =
        Path.of(projectHome, "vault-catalog", "hop", "integration-tests", "models", "vault1");
    if (Files.exists(catalogRoot)) {
      Files.walk(catalogRoot)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // Best effort cleanup before smoke run.
                }
              });
    }

    DataVaultModel model =
        loadModel(projectHome + "/tests/basic/vault1.hdv", metadataProvider, variables);
    RecordDefinitionRegistry.getInstance().invalidate();

    DvCatalogPublisher.PublishResult publishResult =
        DvCatalogPublisher.publish(
            CONNECTION_NAME,
            model,
            variables,
            metadataProvider,
            "DvCatalogPublishSmoke",
            new DvCatalogPublisher.CatalogPublishLog() {
              @Override
              public void logBasic(String message) {
                System.out.println(message);
              }

              @Override
              public void logError(String message, Throwable throwable) {
                System.err.println(message);
                if (throwable != null) {
                  throwable.printStackTrace(System.err);
                }
              }
            });

    if (!publishResult.isSuccess()) {
      throw new HopException(
          "Catalog publish reported "
              + publishResult.getErrorCount()
              + " error(s) for vault1 model");
    }
    if (publishResult.getTableCount() != 7) {
      throw new HopException(
          "Expected 7 DV table record definitions, got " + publishResult.getTableCount());
    }
    if (publishResult.getSourceCount() != 0) {
      throw new HopException(
          "Expected 0 DV source record definitions in vault catalog, got "
              + publishResult.getSourceCount());
    }

    RecordDefinitionKey hubCustomerKey =
        new RecordDefinitionKey("hop/project/models/vault1", "hub_customer");
    RecordDefinition hubCustomer =
        RecordDefinitionRegistry.getInstance()
            .read(CONNECTION_NAME, hubCustomerKey, variables, metadataProvider);
    if (hubCustomer == null) {
      throw new HopException("hub_customer record definition was not published");
    }
    if (hubCustomer.getType() != RecordDefinitionType.DV_HUB) {
      throw new HopException("hub_customer type was " + hubCustomer.getType());
    }
    if (hubCustomer.getFields() == null || hubCustomer.getFields().size() < 2) {
      throw new HopException("hub_customer field layout was not published");
    }
    if (hubCustomer.getOrigin() == null
        || !"DATA_VAULT_MODEL".equals(hubCustomer.getOrigin().getModelType())
        || !"hub_customer".equals(hubCustomer.getOrigin().getModelElementName())) {
      throw new HopException("hub_customer origin metadata is incomplete");
    }
    if (hubCustomer.getPhysicalTable() == null
        || !"Vault".equals(hubCustomer.getPhysicalTable().getDatabaseMetaName())
        || !"hub_customer".equals(hubCustomer.getPhysicalTable().getTableName())) {
      throw new HopException("hub_customer physical table reference is incomplete");
    }

    long localSourceCount =
        RecordDefinitionRegistry.getInstance()
            .listAll(new RecordDefinitionQuery(), variables, metadataProvider)
            .stream()
            .filter(
                ref ->
                    ref.getKey() != null
                        && ref.getKey().getNamespace().contains("/sources")
                        && DvSourceCatalogService.DEFAULT_SOURCE_CATALOG_CONNECTION.equals(
                            ref.getCatalogConnectionName()))
            .count();
    if (localSourceCount < 1) {
      throw new HopException(
          "Expected DV source record definitions in "
              + DvSourceCatalogService.DEFAULT_SOURCE_CATALOG_CONNECTION);
    }

    System.out.println(
        "DvCatalogPublishSmoke: published "
            + publishResult.getTableCount()
            + " tables to vault-catalog ("
            + localSourceCount
            + " sources remain in local-catalog)");
  }

  private static DataVaultModel loadModel(
      String modelFile, JsonMetadataProvider metadataProvider, Variables variables)
      throws HopException {
    String resolved = variables.resolve(modelFile);
    if (Utils.isEmpty(resolved)) {
      throw new HopException("Model file path is empty");
    }
    try {
      Document document = XmlHandler.loadXmlFile(HopVfs.getFileObject(resolved, variables));
      Node rootNode = XmlHandler.getSubNode(document, "data-vault-model");
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      DataVaultModel model = new DataVaultModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, metadataProvider);
      model.setFilename(resolved);
      return model;
    } catch (Exception e) {
      throw new HopException("Unable to load Data Vault model from " + resolved, e);
    }
  }
}
