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
package org.hopper.edw.datavault.dbt;

import java.util.Locale;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlMaterialization;

public final class DbtMaterializationMapper {

  private DbtMaterializationMapper() {}

  public static void apply(DbtModelDraft draft) {
    String raw = draft.getDbtMaterialized();
    if (Utils.isEmpty(raw)) {
      draft.setMaterialization(BvSqlMaterialization.VIEW);
      draft.setImportable(true);
      return;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    switch (key) {
      case "view" -> {
        draft.setMaterialization(BvSqlMaterialization.VIEW);
        draft.setImportable(true);
      }
      case "table" -> {
        draft.setMaterialization(BvSqlMaterialization.TABLE);
        draft.setImportable(true);
      }
      case "materialized_view" -> {
        draft.setMaterialization(BvSqlMaterialization.VIEW);
        draft.setImportable(true);
        draft
            .getIssues()
            .add(
                DbtImportIssue.warn(
                    draft.getName(),
                    "MATERIALIZED_VIEW",
                    "dbt materialized_view imported as VIEW"));
      }
      case "incremental" -> {
        draft.setMaterialization(BvSqlMaterialization.TABLE);
        draft.setImportable(true);
        draft
            .getIssues()
            .add(
                DbtImportIssue.warn(
                    draft.getName(),
                    "INCREMENTAL",
                    "incremental imported as full-refresh TABLE; is_incremental() is always false"));
      }
      case "ephemeral" -> {
        draft.setMaterialization(BvSqlMaterialization.VIEW);
        draft.setImportable(true);
        draft
            .getIssues()
            .add(
                DbtImportIssue.warn(
                    draft.getName(),
                    "EPHEMERAL",
                    "ephemeral imported as VIEW (dbt inlines these; Hop will not)"));
      }
      default -> {
        draft.setImportable(false);
        draft
            .getIssues()
            .add(
                DbtImportIssue.error(
                    draft.getName(),
                    "UNSUPPORTED_MATERIALIZATION",
                    "materialized='" + raw + "' is not imported"));
      }
    }
  }
}
