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
package org.hopper.edw.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.variables.Variables;
import org.hopper.core.AggregationMethod;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabTestModels;
import org.hopper.edw.semantic.bind.HdmSemanticSeed;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticEntityRole;
import org.hopper.edw.semantic.model.SemanticFormatSupport;
import org.hopper.edw.semantic.model.SemanticMeasure;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticModelPersistence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HdmSemanticSeedTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    ModelConfigurationTestSupport.registerTypes();
  }

  @Test
  void seedCreatesFactDimsRelationshipsAndDefaultMasks() throws Exception {
    DimensionalModel hdm = FactCrosstabTestModels.star();
    hdm.setFilename("/models/sales.hdm");
    SemanticModel model = HdmSemanticSeed.seed(hdm, new Variables(), null);

    SemanticEntity fact = model.findEntity("f_order_lines");
    assertNotNull(fact);
    assertEquals(SemanticEntityRole.FACT, fact.resolveRole());
    SemanticMeasure amount = fact.findMeasure("amount");
    assertNotNull(amount);
    assertEquals(AggregationMethod.SUM, amount.resolveAggregation());
    assertFalse(amount.getFormatMask() == null || amount.getFormatMask().isBlank());

    assertNotNull(model.findEntity("dim_customer"));
    assertNotNull(model.findEntity("dim_order_date"));
    assertNotNull(model.findEntity("dim_ship_date"));
    assertNotNull(model.findEntity("dim_order_flags"));
    assertTrue(model.relationshipsFrom("f_order_lines").size() >= 3);
    assertEquals(1, model.listFactEntities().size());
    assertEquals("f_order_lines", model.listFactEntities().get(0).getName());
  }

  @Test
  void xmlRoundTripPreservesEntities(@TempDir Path tmp) throws Exception {
    DimensionalModel hdm = FactCrosstabTestModels.star();
    hdm.setFilename("/models/sales.hdm");
    SemanticModel original = HdmSemanticSeed.seed(hdm, new Variables(), null);
    original.setName("sales-semantic");
    Path file = tmp.resolve("sales.hsl");
    SemanticModelPersistence.save(original, file.toString(), new Variables());
    assertTrue(Files.size(file) > 0);

    SemanticModel restored = SemanticModelPersistence.load(file.toString(), null, new Variables());
    assertEquals("sales-semantic", restored.getName());
    assertEquals(original.getEntities().size(), restored.getEntities().size());
    assertEquals(original.getRelationships().size(), restored.getRelationships().size());
    assertEquals(
        original.findEntity("f_order_lines").findMeasure("amount").resolveAggregation(),
        restored.findEntity("f_order_lines").findMeasure("amount").resolveAggregation());
  }

  @Test
  void defaultNumberMaskIsSet() {
    assertEquals("#,##0.00", SemanticFormatSupport.NUMBER_MASK);
  }
}
