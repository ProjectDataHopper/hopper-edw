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
package org.hopper.edw.datavault.metadata.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.lineageview.backend.FileFolderLineageQueryService;
import org.hopper.edw.datavault.lineageview.backend.ILineageQueryService;
import org.hopper.edw.datavault.lineageview.backend.LineageBackendKind;
import org.hopper.edw.datavault.lineageview.backend.LineageQueryServiceFactory;
import org.hopper.edw.datavault.lineageview.backend.LocalModelsLineageQueryService;
import org.hopper.edw.datavault.lineageview.backend.marquez.MarquezLineageQueryService;
import org.junit.jupiter.api.Test;

class LineageBackendSettingsFactoryTest {

  @Test
  void knownTypesRoundTrip() throws Exception {
    assertEquals(3, LineageBackendSettingsFactory.getKnownTypeIds().size());
    assertInstanceOf(
        MarquezBackendSettings.class, LineageBackendSettingsFactory.newSettings("MARQUEZ"));
    assertInstanceOf(
        FileFolderBackendSettings.class, LineageBackendSettingsFactory.newSettings("FILE_FOLDER"));
    assertInstanceOf(
        LocalModelsBackendSettings.class,
        LineageBackendSettingsFactory.newSettings("LOCAL_MODELS"));
    assertInstanceOf(MarquezBackendSettings.class, LineageBackendSettingsFactory.newSettings(null));
  }

  @Test
  void unknownTypeThrows() {
    assertThrows(HopException.class, () -> LineageBackendSettingsFactory.newSettings("COLLIBRA"));
  }

  @Test
  void factoryOpensMatchingService() throws Exception {
    LineageBackendMeta marquez = new LineageBackendMeta("m");
    MarquezBackendSettings ms = new MarquezBackendSettings();
    ms.setBaseUrl("http://localhost:5001");
    marquez.setSettings(ms);
    try (ILineageQueryService service =
        LineageQueryServiceFactory.open(marquez, null, null, null)) {
      assertEquals(LineageBackendKind.MARQUEZ, service.kind());
      assertInstanceOf(MarquezLineageQueryService.class, service);
    }

    LineageBackendMeta folder = new LineageBackendMeta("f");
    FileFolderBackendSettings fs = new FileFolderBackendSettings();
    fs.setFolder("/tmp/ol");
    folder.setSettings(fs);
    try (ILineageQueryService service = LineageQueryServiceFactory.open(folder, null, null, null)) {
      assertEquals(LineageBackendKind.FILE_FOLDER, service.kind());
      assertInstanceOf(FileFolderLineageQueryService.class, service);
    }

    LineageBackendMeta local = new LineageBackendMeta("l");
    local.setSettings(new LocalModelsBackendSettings());
    try (ILineageQueryService service = LineageQueryServiceFactory.open(local, null, null, null)) {
      assertEquals(LineageBackendKind.LOCAL_MODELS, service.kind());
      assertInstanceOf(LocalModelsLineageQueryService.class, service);
    }
  }

  @Test
  void parentIdSwitchCoversAllTypes() throws Exception {
    assertTrue(
        LineageBackendMetaEditor.getGuiPluginElementParentId(new MarquezBackendSettings())
            .contains("Marquez"));
    assertTrue(
        LineageBackendMetaEditor.getGuiPluginElementParentId(new FileFolderBackendSettings())
            .contains("FileFolder"));
    assertTrue(
        LineageBackendMetaEditor.getGuiPluginElementParentId(new LocalModelsBackendSettings())
            .contains("LocalModels"));
  }
}
