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
package org.apache.hop.catalog.versioning;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Scope metadata describing which catalog records a version tag covers. */
@Getter
@Setter
@NoArgsConstructor
public class CatalogVersionScope {

  private String resourceDefinitionGroup;
  private List<String> namespaces = new ArrayList<>();
  private List<String> recordKeys = new ArrayList<>();

  public CatalogVersionScope(
      String resourceDefinitionGroup, List<String> namespaces, List<String> recordKeys) {
    this.resourceDefinitionGroup = resourceDefinitionGroup;
    this.namespaces = namespaces != null ? new ArrayList<>(namespaces) : new ArrayList<>();
    this.recordKeys = recordKeys != null ? new ArrayList<>(recordKeys) : new ArrayList<>();
  }

  public List<String> getNamespaces() {
    if (namespaces == null) {
      namespaces = new ArrayList<>();
    }
    return namespaces;
  }

  public List<String> getRecordKeys() {
    if (recordKeys == null) {
      recordKeys = new ArrayList<>();
    }
    return recordKeys;
  }
}
