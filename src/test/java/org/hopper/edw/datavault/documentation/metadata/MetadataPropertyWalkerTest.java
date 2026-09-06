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
package org.hopper.edw.datavault.documentation.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.edw.datavault.documentation.metadata.MetadataPropertyWalker.PropertyRow;
import org.junit.jupiter.api.Test;

class MetadataPropertyWalkerTest {

  @Test
  void redactsPasswordFields() {
    Sample sample = new Sample();
    sample.name = "Vault";
    sample.password = "s3cret";
    sample.token = "abc";
    List<PropertyRow> rows = MetadataPropertyWalker.walk(sample);
    assertTrue(rows.stream().anyMatch(r -> "name".equals(r.name()) && "Vault".equals(r.value())));
    assertTrue(
        rows.stream()
            .anyMatch(
                r ->
                    "password".equals(r.name())
                        && MetadataPropertyWalker.REDACTED.equals(r.value())));
    assertTrue(
        rows.stream()
            .anyMatch(
                r ->
                    "token".equals(r.name()) && MetadataPropertyWalker.REDACTED.equals(r.value())));
  }

  @Test
  void usesGuiWidgetLabelAndTooltip() {
    Labeled labeled = new Labeled();
    labeled.displayName = "Retail vault";
    labeled.hostName = "db.example.com";
    labeled.translatedHost = "db.example.com";
    List<PropertyRow> rows = MetadataPropertyWalker.walk(labeled);
    PropertyRow nameRow =
        rows.stream().filter(r -> "displayName".equals(r.name())).findFirst().orElseThrow();
    assertEquals("Display name", nameRow.label());
    assertEquals("Human-readable name of this connection", nameRow.toolTip());
    assertEquals("Retail vault", nameRow.value());
    PropertyRow hostRow =
        rows.stream().filter(r -> "hostName".equals(r.name())).findFirst().orElseThrow();
    assertEquals("hostName", hostRow.label());
    assertEquals("", hostRow.toolTip());
    PropertyRow translated =
        rows.stream().filter(r -> "translatedHost".equals(r.name())).findFirst().orElseThrow();
    assertEquals("Host name", translated.label());
    assertEquals("Database server host", translated.toolTip());
  }

  @Test
  void secretNameDetection() {
    assertTrue(MetadataPropertyWalker.looksSecret("dbPassword"));
    assertTrue(MetadataPropertyWalker.looksEncrypted("Encrypted 123"));
    assertEquals(false, MetadataPropertyWalker.looksSecret("hostname"));
  }

  static class Sample {
    @HopMetadataProperty String name;

    @HopMetadataProperty(password = true)
    String password;

    @HopMetadataProperty String token;
  }

  static class Labeled {
    @HopMetadataProperty
    @GuiWidgetElement(
        id = "display-name",
        type = GuiElementType.TEXT,
        label = "Display name",
        toolTip = "Human-readable name of this connection")
    String displayName;

    @HopMetadataProperty String hostName;

    @HopMetadataProperty
    @GuiWidgetElement(
        id = "host",
        type = GuiElementType.TEXT,
        label = "i18n::Labeled.Host.Label",
        toolTip = "i18n::Labeled.Host.ToolTip")
    String translatedHost;
  }
}
