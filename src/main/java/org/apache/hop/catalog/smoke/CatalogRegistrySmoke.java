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
package org.apache.hop.catalog.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.catalog.metadata.DataCatalogMetaObjectFactory;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.json.JsonMetadataProvider;

/**
 * Manual smoke runner for catalog CRUD. Invoked from {@code
 * integration-tests/tests/catalog/smoke-catalog.sh}.
 */
public final class CatalogRegistrySmoke {

  private CatalogRegistrySmoke() {}

  public static void main(String[] args) throws Exception {
    String projectHome = args.length > 0 ? args[0] : "project";
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", projectHome);

    HopEnvironment.init();
    PluginRegistry registry = PluginRegistry.getInstance();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, variables, registry);

    if (DataCatalogMetaObjectFactory.newCatalog("FILE") == null) {
      throw new HopException("FILE data catalog implementation was not available");
    }

    JsonMetadataProvider metadataProvider =
        new JsonMetadataProvider(null, projectHome + "/metadata", variables);

    Path catalogData = Path.of(projectHome, "catalog-data", "hop", "smoke-test");
    if (Files.exists(catalogData)) {
      Files.walk(catalogData)
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

    RecordDefinitionRegistry recordRegistry = RecordDefinitionRegistry.getInstance();
    recordRegistry.invalidate();

    RecordDefinition definition = buildSampleDefinition();
    String connectionName = "local-catalog";

    recordRegistry.create(connectionName, definition, variables, metadataProvider);

    RecordDefinition loaded =
        recordRegistry.read(connectionName, definition.getKey(), variables, metadataProvider);
    if (loaded == null || loaded.getFields() == null || loaded.getFields().size() != 2) {
      throw new HopException("Read back record definition failed");
    }

    loaded.setDescription("Updated by smoke test");
    recordRegistry.update(connectionName, loaded, variables, metadataProvider);

    List<RecordDefinitionRef> refs =
        recordRegistry.listAll(new RecordDefinitionQuery(), variables, metadataProvider);
    if (refs.stream().noneMatch(ref -> "smoke_record".equals(ref.getKey().getName()))) {
      throw new HopException("List did not return smoke record");
    }

    recordRegistry.delete(connectionName, definition.getKey(), variables, metadataProvider);

    RecordDefinition afterDelete =
        recordRegistry.read(connectionName, definition.getKey(), variables, metadataProvider);
    if (afterDelete != null) {
      throw new HopException("Record definition was not deleted");
    }

    System.out.println("CatalogRegistrySmoke: CRUD round-trip OK");
  }

  private static RecordDefinition buildSampleDefinition() throws HopException {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("customer_id"));
    rowMeta.addValueMeta(new ValueMetaString("name"));

    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/smoke-test", "smoke_record"));
    definition.setType(RecordDefinitionType.DV_HUB);
    definition.setDescription("Smoke test hub record");
    definition.setFields(rowMeta);
    definition.getTags().add("smoke-test");
    return definition;
  }
}
