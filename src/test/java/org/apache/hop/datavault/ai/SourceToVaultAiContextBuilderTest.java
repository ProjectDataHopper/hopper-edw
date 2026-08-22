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
package org.apache.hop.datavault.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;
import org.apache.hop.datavault.metadata.sourcemodel.tovault.SourceToVaultClassifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceToVaultAiContextBuilderTest {

  @Test
  void serializeClassificationIncludesRolesAndKinds() {
    SourceModel model = new SourceModel();
    model.setName("crm");
    model.getTables().add(table("country", pk("country_code"), col("country_name")));
    model.getTables().add(table("customer_hub", pk("customer_id"), col("country_code")));
    model.getRelationships().add(rel("customer_hub", "country", "country_code"));

    String json =
        SourceToVaultAiContextBuilder.serializeClassification(
            "crm.hsm", "crm", SourceToVaultClassifier.classify(model));

    assertTrue(json.contains("\"filename\":\"crm.hsm\""), json);
    assertTrue(json.contains("\"name\":\"crm\""), json);
    assertTrue(json.contains("\"role\":\"REFERENCE\""), json);
    assertTrue(json.contains("\"kind\":\"REFERENCE\""), json);
    assertTrue(json.contains("\"name\":\"ref_country\""), json);
    assertTrue(json.contains("\"role\":\"HUB\""), json);
  }

  @Test
  void listsSiblingHsmFiles(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("crm.hsm"), "<source-model/>");
    Files.writeString(dir.resolve("notes.txt"), "ignore");
    Files.writeString(dir.resolve("vault.hdv"), "<data-vault/>");

    List<String> found =
        SourceToVaultAiContextBuilder.listSiblingSourceModels(
            dir.resolve("vault.hdv").toAbsolutePath().toString());

    assertEquals(1, found.size());
    assertTrue(found.getFirst().toLowerCase().endsWith("crm.hsm"), found.toString());
  }

  @Test
  void advisorPromptIncludesClassificationJson() {
    DvAiContextBundle context =
        DvAiContextBundle.builder()
            .userPrompt("What hubs should I add?")
            .modelStructureJson("{\"tables\":[]}")
            .sourceClassificationJson("{\"sourceModels\":[{\"name\":\"crm\"}]}")
            .build();

    String prompt = DvAiAdvisorService.buildInitialUserPrompt(context);
    assertTrue(prompt.contains("Source-model Data Vault classification JSON"));
    assertTrue(prompt.contains("\"name\":\"crm\""));
  }

  private static SourceTable table(String name, SourceColumn... columns) {
    SourceTable table = new SourceTable(name);
    table.setCatalogSourceName(name);
    for (SourceColumn column : columns) {
      table.getColumns().add(column);
    }
    return table;
  }

  private static SourceColumn pk(String name) {
    SourceColumn column = new SourceColumn(name);
    column.setPrimaryKeyPosition(1);
    return column;
  }

  private static SourceColumn col(String name) {
    return new SourceColumn(name);
  }

  private static SourceRelationship rel(String child, String parent, String column) {
    SourceRelationship relationship = new SourceRelationship("fk_" + child + "_" + parent);
    relationship.setChildEndpointKind(SourceEndpointKind.TABLE);
    relationship.setParentEndpointKind(SourceEndpointKind.TABLE);
    relationship.setChildTableName(child);
    relationship.setParentTableName(parent);
    relationship.getChildColumns().add(column);
    relationship.getParentColumns().add(column);
    return relationship;
  }
}
