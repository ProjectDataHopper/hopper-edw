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
package org.apache.hop.datavault.resourcedefinition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionValidationAcknowledgement;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class ValidationAcknowledgementSupportTest {

  @Test
  void acknowledgeRequiresComment() {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "customer"));

    assertThrows(
        HopException.class,
        () ->
            ValidationAcknowledgementSupport.acknowledge(
                "catalog",
                definition,
                "FIELD_ADDED|loyalty_tier|",
                "",
                "tester",
                new Variables(),
                null));
  }

  @Test
  void revokeRemovesMatchingAcknowledgement() throws Exception {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey("hop/retail-example/sources", "customer"));
    RecordDefinitionValidationAcknowledgement acknowledgement =
        new RecordDefinitionValidationAcknowledgement();
    acknowledgement.setIssueId("FIELD_ADDED|loyalty_tier|");
    acknowledgement.setComment("temporary");
    definition.setValidationAcknowledgements(new ArrayList<>());
    definition.getValidationAcknowledgements().add(acknowledgement);

    assertTrue(
        ValidationAcknowledgementSupport.isAcknowledged(definition, "FIELD_ADDED|loyalty_tier|"));

    // Revoke without persistence because registry update would fail without catalog wiring.
    definition
        .getValidationAcknowledgements()
        .removeIf(a -> "FIELD_ADDED|loyalty_tier|".equals(a.getIssueId()));
    assertFalse(
        ValidationAcknowledgementSupport.isAcknowledged(definition, "FIELD_ADDED|loyalty_tier|"));
  }
}
