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
package org.apache.hop.datavault.metadata.sourcemodel.tovault;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.jspecify.annotations.NonNull;

/** One hub, link, or satellite the classifier wants to add or reuse. */
@Getter
@Setter
public class ProposedVaultObject {

  private ProposedObjectKind kind;
  private String name;
  private String tableName;
  private String sourceTableName;
  private SourceEndpointKind sourceKind = SourceEndpointKind.TABLE;
  private String catalogSourceName;
  private String referencedTableName;
  private DvTableType referencedTableType;
  private String roleHashKeyFieldName;
  private String drivingKeyColumn;
  private boolean reuseExisting;
  private boolean included = true;

  /** Hub business-key source column names, PK order. */
  private List<String> businessKeyColumns = new ArrayList<>();

  /** Satellite descriptive column names. */
  private List<String> satelliteAttributeColumns = new ArrayList<>();

  /** Participating hub names for a link (stable order). */
  private List<String> participatingHubNames = new ArrayList<>();

  /** Link: hub name → source columns on this table that map to that hub's keys. */
  private Map<String, List<String>> hubSourceKeyColumns = new LinkedHashMap<>();

  /** Degenerate PK leftovers on a transactional link. */
  private List<String> dependentChildKeyColumns = new ArrayList<>();

  private String parentHubName;
  private String parentLinkName;

  public ProposedVaultObject() {}

  public ProposedVaultObject(ProposedObjectKind kind, String name) {
    this.kind = kind;
    this.name = name;
    this.tableName = name;
  }

  public @NonNull List<String> getBusinessKeyColumns() {
    if (businessKeyColumns == null) {
      businessKeyColumns = new ArrayList<>();
    }
    return businessKeyColumns;
  }

  public @NonNull List<String> getSatelliteAttributeColumns() {
    if (satelliteAttributeColumns == null) {
      satelliteAttributeColumns = new ArrayList<>();
    }
    return satelliteAttributeColumns;
  }

  public @NonNull List<String> getParticipatingHubNames() {
    if (participatingHubNames == null) {
      participatingHubNames = new ArrayList<>();
    }
    return participatingHubNames;
  }

  public @NonNull Map<String, List<String>> getHubSourceKeyColumns() {
    if (hubSourceKeyColumns == null) {
      hubSourceKeyColumns = new LinkedHashMap<>();
    }
    return hubSourceKeyColumns;
  }

  public @NonNull List<String> getDependentChildKeyColumns() {
    if (dependentChildKeyColumns == null) {
      dependentChildKeyColumns = new ArrayList<>();
    }
    return dependentChildKeyColumns;
  }
}
