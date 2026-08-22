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
package org.apache.hop.datavault.hopgui.file.lineageview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.lineageview.HopLineageViewDocument;
import org.apache.hop.datavault.lineageview.LineageViewPersistence;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HopLineageViewFileTypeTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void capabilitiesAndExtension() {
    HopLineageViewFileType fileType = new HopLineageViewFileType();
    assertEquals(".hlv", fileType.getDefaultFileExtension());
    assertEquals("true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_NEW));
    assertEquals("true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_SAVE));
    assertEquals("true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_SAVE_AS));
    assertEquals(
        "true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_EXPORT_TO_SVG));
    assertEquals("true", fileType.getCapabilities().getProperty(IHopFileType.CAPABILITY_SEARCH));
    assertTrue(fileType.supportsFile(new HopLineageViewDocument()));
    assertFalse(fileType.supportsFile(null));
  }

  @Test
  void isHandledByExtensionAndContent() throws Exception {
    HopLineageViewFileType fileType = new HopLineageViewFileType();
    assertTrue(fileType.isHandledBy("models/f_orders-upstream.hlv", false));
    assertTrue(fileType.isHandledBy("MODELS/F_ORDERS.HLV", false));
    assertFalse(fileType.isHandledBy("models/retail-pos.hdm", false));

    Path dir = Files.createTempDirectory("hlv-type");
    Path hlv = dir.resolve("view.hlv");
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setName("view");
    document.setBackendName("local");
    LineageViewPersistence.save(document, hlv.toString(), new Variables());
    assertTrue(fileType.isHandledBy(hlv.toString(), true));

    Path other = dir.resolve("not-a-view.xml");
    Files.writeString(other, "<something/>", StandardCharsets.UTF_8);
    assertFalse(fileType.isHandledBy(other.toString(), true));
  }

  @Test
  void createSearchableLoadsDocument() throws Exception {
    Path dir = Files.createTempDirectory("hlv-search");
    Path hlv = dir.resolve("search.hlv");
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setName("search-me");
    document.setBackendName("local");
    document.setLogicalTable("hub_customer");
    LineageViewPersistence.save(document, hlv.toString(), new Variables());

    HopLineageViewFileType fileType = new HopLineageViewFileType();
    var searchable = fileType.createSearchable(hlv.toString(), "project", new Variables(), null);
    assertEquals("search", searchable.getName());
    assertTrue(searchable instanceof HopGuiLineageViewSearchable);
    HopLineageViewDocument loaded = (HopLineageViewDocument) searchable.getSearchableObject();
    assertEquals("hub_customer", loaded.getLogicalTable());
  }

  @Test
  void proposedSaveNameUsesLogicalTable() {
    HopLineageViewDocument document = new HopLineageViewDocument();
    document.setLogicalTable("f_order_lines");
    assertEquals("f_order_lines", HopLineageViewFileType.proposedSaveName(document));
  }
}
