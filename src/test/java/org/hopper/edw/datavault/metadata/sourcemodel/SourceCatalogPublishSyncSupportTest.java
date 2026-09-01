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
package org.hopper.edw.datavault.metadata.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.hopper.edw.datavault.metadata.SourceField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SourceCatalogPublishSyncSupportTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void staleCatalogIsAnErrorThatAsksToPublish() {
    SourceField catalog = stringField("asn_id", "2000");
    SourceField model = stringField("asn_id", "7");
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(List.of(catalog), List.of(model));

    List<ICheckResult> remarks =
        SourceCatalogPublishSyncSupport.remarksForDiff(
            "pipeline", "asn-package-lines", "asn-package-lines", diff);

    assertEquals(1, remarks.size());
    assertEquals(ICheckResult.TYPE_RESULT_ERROR, remarks.get(0).getType());
    String text = remarks.get(0).getText();
    assertTrue(text.contains("stale"), text);
    assertTrue(text.contains("asn_id"), text);
    assertTrue(text.toLowerCase().contains("publish"), text);
  }

  @Test
  void missingCatalogFeedIsAWarningToPublish() {
    List<ICheckResult> remarks =
        SourceCatalogPublishSyncSupport.remarksForMissingFeed(
            "pipeline", "asn-package-lines", "asn-package-lines");
    assertEquals(1, remarks.size());
    assertEquals(ICheckResult.TYPE_RESULT_WARNING, remarks.get(0).getType());
    assertTrue(remarks.get(0).getText().toLowerCase().contains("publish"));
  }

  @Test
  void listStalePublishedFeedsIsEmptyWithoutCatalogConnection() {
    SourceModel model = new SourceModel();
    model.setName("crm");
    SourcePipeline pipeline = new SourcePipeline("asn-package-lines");
    pipeline.getFields().add(new SourceColumn("asn_id"));
    model.getPipelineSources().add(pipeline);

    assertTrue(
        SourceCatalogPublishSyncSupport.listStalePublishedFeeds(model, new Variables(), null)
            .isEmpty());
    assertTrue(
        SourceCatalogPublishSyncSupport.listStalePublishedFeeds(
                model, new Variables(), new MemoryMetadataProvider())
            .isEmpty());
  }

  @Test
  void matchingLayoutsProduceNoRemark() {
    SourceField field = stringField("asn_id", "7");
    RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
        RecordDefinitionSchemaDiffSupport.diff(List.of(field), List.of(field));
    assertTrue(
        SourceCatalogPublishSyncSupport.remarksForDiff(
                "pipeline", "asn-package-lines", "asn-package-lines", diff)
            .isEmpty());
  }

  private static SourceField stringField(String name, String length) {
    SourceField field = new SourceField(name);
    field.setHopType(IValueMeta.TYPE_STRING);
    field.setLength(length);
    return field;
  }
}
