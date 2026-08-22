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
package org.apache.hop.datavault.lineage;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One upstream contribution to a target field (multi-source hubs may have several). */
@Getter
@Setter
public class FieldContribution {

  private TableSourceKind sourceKind = TableSourceKind.DV_SOURCE;
  private String sourceName;
  private String sourceCatalogKey;
  private String sourceFieldName;
  private FieldTransform transform = FieldTransform.IDENTITY;
  private final List<LineageReason> reasons = new ArrayList<>();

  public FieldContribution addReason(LineageReason reason) {
    if (reason != null) {
      reasons.add(reason);
    }
    return this;
  }
}
