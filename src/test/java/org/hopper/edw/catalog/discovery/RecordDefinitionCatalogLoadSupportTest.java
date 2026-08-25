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
package org.hopper.edw.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.xp.RegisterDataCatalogMetadataExtensionPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordDefinitionCatalogLoadSupportTest {

  @TempDir Path tempDir;

  private Variables variables;
  private MemoryMetadataProvider metadataProvider;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
    new RegisterDataCatalogMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  @BeforeEach
  void setUp() throws HopException {
    variables = new Variables();
    metadataProvider = new MemoryMetadataProvider();
    DataCatalogMeta catalog = new DataCatalogMeta();
    catalog.setName("local-catalog");
    catalog.setEnabled(true);
    FileDataCatalog fileCatalog = new FileDataCatalog();
    fileCatalog.setStorageDirectory(tempDir.resolve("edw-catalog").toString().replace('\\', '/'));
    catalog.setCatalog(fileCatalog);
    metadataProvider.getSerializer(DataCatalogMeta.class).save(catalog);
    RecordDefinitionRegistry.getInstance().invalidate();
  }

  @Test
  void loadDefinitions_listsWorkingTreeForConnection() throws Exception {
    RecordDefinitionRegistry.getInstance()
        .create(
            "local-catalog",
            source("hop/retail-example/sources", "E2E-a"),
            variables,
            metadataProvider);
    RecordDefinitionRegistry.getInstance()
        .create("local-catalog", source("hop/other/sources", "other"), variables, metadataProvider);

    RecordDefinitionLoadResult all =
        RecordDefinitionCatalogLoadSupport.loadDefinitions(
            "local-catalog", "", "", variables, metadataProvider);
    assertEquals(2, all.getDefinitions().size());

    RecordDefinitionLoadResult prefixed =
        RecordDefinitionCatalogLoadSupport.loadDefinitions(
            "local-catalog", "hop/retail-example/sources", "", variables, metadataProvider);
    assertEquals(1, prefixed.getDefinitions().size());
    assertEquals("E2E-a", prefixed.getDefinitions().get(0).getKey().getName());
  }

  @Test
  void loadDefinitions_missingConnectionThrows() {
    HopException error =
        assertThrows(
            HopException.class,
            () ->
                RecordDefinitionCatalogLoadSupport.loadDefinitions(
                    "missing-catalog", "", "", variables, metadataProvider));
    assertTrue(error.getMessage() != null && error.getMessage().contains("missing-catalog"));
  }

  @Test
  void throwIfEmpty_failsWhenEnabled() throws Exception {
    RecordDefinitionLoadResult empty =
        RecordDefinitionCatalogLoadSupport.loadDefinitions(
            "local-catalog", "", "", variables, metadataProvider);
    assertTrue(empty.getDefinitions().isEmpty());
    RecordDefinitionCatalogLoadSupport.throwIfEmpty(empty, false);
    HopException error =
        assertThrows(
            HopException.class, () -> RecordDefinitionCatalogLoadSupport.throwIfEmpty(empty, true));
    assertTrue(error.getMessage() != null && error.getMessage().contains("local-catalog"));
  }

  @Test
  void inspectConnection_emptyProviderReportsNotFound() {
    CatalogDiscoverySnapshot snapshot =
        CatalogDiscoverySupport.inspectConnection(
            "local-catalog", null, null, variables, new MemoryMetadataProvider());
    assertFalse(snapshot.isConnectionFound());
    assertTrue(
        snapshot.getErrorMessage() != null && snapshot.getErrorMessage().contains("local-catalog"));
  }

  @Test
  void addCheckRemarks_warnsWhenWorkingTreeEmpty() throws Exception {
    List<ICheckResult> remarks = new ArrayList<>();
    CatalogDiscoverySupport.addCheckRemarks(
        remarks,
        new TransformMeta(),
        "local-catalog",
        null,
        null,
        false,
        false,
        variables,
        metadataProvider);
    assertTrue(
        remarks.stream()
            .anyMatch(
                r ->
                    r.getType() == ICheckResult.TYPE_RESULT_WARNING
                        && r.getText() != null
                        && r.getText().contains("working-tree")));
  }

  @Test
  void addCheckRemarks_errorsWhenFailIfNoneAndEmpty() {
    List<ICheckResult> remarks = new ArrayList<>();
    CatalogDiscoverySupport.addCheckRemarks(
        remarks,
        new TransformMeta(),
        "local-catalog",
        null,
        null,
        false,
        true,
        variables,
        metadataProvider);
    assertTrue(remarks.stream().anyMatch(r -> r.getType() == ICheckResult.TYPE_RESULT_ERROR));
  }

  @Test
  void listQuery_namespaceOnlySetsPrefix() {
    assertEquals(
        "hop/retail-example/sources",
        CatalogDiscoverySupport.listQuery("hop/retail-example/sources", "").getNamespacePrefix());
    assertTrue(
        CatalogDiscoverySupport.listQuery("", "").getNamespacePrefix() == null
            || CatalogDiscoverySupport.listQuery("", "").getNamespacePrefix().isEmpty());
    assertTrue(CatalogDiscoverySupport.isSingleKeyLookup("ns", "name"));
    assertFalse(CatalogDiscoverySupport.isSingleKeyLookup("ns", ""));
  }

  @Test
  void skippedUnreadableJsonIsCounted() throws Exception {
    Path root = tempDir.resolve("edw-catalog");
    Files.createDirectories(root);
    Files.writeString(root.resolve("not-a-record.json"), "{not-json");

    RecordDefinitionLoadResult result =
        RecordDefinitionCatalogLoadSupport.loadDefinitions(
            "local-catalog", "", "", variables, metadataProvider);
    assertEquals(0, result.getDefinitions().size());
    assertTrue(result.getSkippedUnreadable() >= 1);
  }

  private static RecordDefinition source(String namespace, String name) {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, name));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    definition.setFields(new RowMeta());
    return definition;
  }
}
