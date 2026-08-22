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
package org.apache.hop.datavault.catalog;

import java.util.ArrayList;
import org.apache.hop.catalog.model.RecordDefinition;

/**
 * Shared upsert merge helpers for DV/BV/DM catalog publishers.
 *
 * <p>Model publish rebuilds a fresh {@link RecordDefinition} and full-replaces the catalog
 * document. Fields maintained outside model structure (quality bindings, validation
 * acknowledgements) must be copied from the existing document so they survive republish (K12).
 */
public final class CatalogPublishMergeSupport {

  private CatalogPublishMergeSupport() {}

  /**
   * Copies catalog fields that model publish does not reconstruct (K12 / Phase 2 target bindings).
   * Without this, a routine republish full-replaces the document and wipes qualityRules and
   * validationAcknowledgements.
   */
  public static void mergePreservedCatalogFields(
      RecordDefinition definition, RecordDefinition existing) {
    if (existing == null) {
      return;
    }
    if (existing.getQualityRules() != null && !existing.getQualityRules().isEmpty()) {
      definition.setQualityRules(new ArrayList<>(existing.getQualityRules()));
    }
    if (existing.getValidationAcknowledgements() != null
        && !existing.getValidationAcknowledgements().isEmpty()) {
      definition.setValidationAcknowledgements(
          new ArrayList<>(existing.getValidationAcknowledgements()));
    }
  }
}
