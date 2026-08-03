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
package org.apache.hop.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.CatalogSourceField;
import org.apache.hop.catalog.model.DvSourceRecord;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionType;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogVersionStoreTest {

  @TempDir Path tempDir;

  private CatalogVersionStore store;

  @BeforeEach
  void setUp() throws Exception {
    store = new CatalogVersionStore(tempDir);
  }

  @Test
  void writeAndReadSnapshot_isImmutableAfterWorkingTreeChange() throws Exception {
    RecordDefinition original = sampleDefinition("customer", "VARCHAR");
    CatalogVersionSnapshotManifest snapshot = baseSnapshot("v1.0.0", "snap-v1");
    store.writeSnapshot(snapshot, List.of(original));

    assertTrue(Files.isDirectory(tempDir.resolve(CatalogVersionStore.VERSIONS_DIRECTORY_NAME)));
    assertFalse(Files.exists(tempDir.resolve(".catalog-versions")));

    RecordDefinition loaded =
        store
            .readDefinition("snap-v1", new RecordDefinitionKey("hop/test/sources", "customer"))
            .orElseThrow();
    assertEquals("VARCHAR", loaded.getDvSource().getFields().getFirst().getSourceDataType());

    // Mutate a separate working-tree definition; snapshot must stay frozen.
    RecordDefinition mutated = sampleDefinition("customer", "INTEGER");
    assertEquals(
        "VARCHAR",
        store
            .readDefinition("snap-v1", new RecordDefinitionKey("hop/test/sources", "customer"))
            .orElseThrow()
            .getDvSource()
            .getFields()
            .getFirst()
            .getSourceDataType());
    assertEquals("INTEGER", mutated.getDvSource().getFields().getFirst().getSourceDataType());
  }

  @Test
  void manifestTracksTagsAndRejectsDuplicateSnapshotId() throws Exception {
    CatalogVersionSnapshotManifest snapshot = baseSnapshot("v2.0.0", "snap-v2");
    store.writeSnapshot(snapshot, List.of(sampleDefinition("orders", "NUMERIC")));

    CatalogVersionManifest manifest = new CatalogVersionManifest();
    CatalogVersionEntry entry =
        new CatalogVersionEntry(
            "v2.0.0",
            "snap-v2",
            snapshot.getCreatedAt(),
            "tester",
            "baseline",
            snapshot.getScope(),
            snapshot.getRecordCount(),
            snapshot.getContentHash());
    manifest.addVersion(entry);
    store.writeManifest(manifest);

    CatalogVersionManifest reloaded = store.readManifest();
    assertTrue(reloaded.hasTag("v2.0.0"));
    assertEquals("snap-v2", reloaded.findByTag("v2.0.0").orElseThrow().getSnapshotId());
    assertNotNull(reloaded.findByTag("v2.0.0").orElseThrow().getContentHash());
    assertTrue(reloaded.findByTag("v2.0.0").orElseThrow().getContentHash().startsWith("sha256:"));

    assertThrows(
        Exception.class,
        () ->
            store.writeSnapshot(
                baseSnapshot("v2.0.0-dup", "snap-v2"), List.of(sampleDefinition("x", "STRING"))));
  }

  @Test
  void fileCatalogList_excludesVersionSnapshots() throws Exception {
    // Working-tree record
    FileDataCatalog catalog = new FileDataCatalog();
    catalog.setStorageDirectory(tempDir.toString().replace('\\', '/'));
    DataCatalogMeta meta = new DataCatalogMeta("local");
    meta.setCatalog(catalog);
    catalog.connect(meta, new Variables(), new MemoryMetadataProvider());
    catalog.create(sampleDefinition("live-customer", "STRING"));

    // Snapshot with a different name under catalog-versions
    CatalogVersionSnapshotManifest snapshot = baseSnapshot("v3", "snap-v3");
    store.writeSnapshot(snapshot, List.of(sampleDefinition("snap-only", "STRING")));

    assertEquals(1, catalog.list(new RecordDefinitionQuery()).size());
    assertEquals(
        "live-customer", catalog.list(new RecordDefinitionQuery()).getFirst().getKey().getName());
    catalog.disconnect();
  }

  @Test
  void sanitizeTagForPath_stripsUnsafeCharacters() {
    assertEquals("v2.4.0", CatalogVersionStore.sanitizeTagForPath("v2.4.0"));
    assertEquals("2026-q3-release", CatalogVersionStore.sanitizeTagForPath("2026-Q3-Release"));
    assertEquals("release-notes", CatalogVersionStore.sanitizeTagForPath("Release notes"));
    assertEquals("untagged", CatalogVersionStore.sanitizeTagForPath("@@@"));
  }

  private static CatalogVersionSnapshotManifest baseSnapshot(String tag, String snapshotId) {
    CatalogVersionSnapshotManifest snapshot = new CatalogVersionSnapshotManifest();
    snapshot.setTag(tag);
    snapshot.setSnapshotId(snapshotId);
    snapshot.setCreatedAt("2026-07-14T06:00:00Z");
    snapshot.setCreatedBy("tester");
    snapshot.setDescription("test");
    snapshot.setScope(new CatalogVersionScope("group", List.of("hop/test/sources"), List.of()));
    return snapshot;
  }

  private static RecordDefinition sampleDefinition(String name, String dataType) {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/test/sources", name));
    definition.setType(RecordDefinitionType.DV_SOURCE);
    CatalogSourceField field = new CatalogSourceField();
    field.setName("id");
    field.setSourceDataType(dataType);
    field.setHopType(2);
    DvSourceRecord dvSource = new DvSourceRecord();
    dvSource.setSourceType("CSV");
    dvSource.setFields(List.of(field));
    definition.setDvSource(dvSource);
    return definition;
  }
}
