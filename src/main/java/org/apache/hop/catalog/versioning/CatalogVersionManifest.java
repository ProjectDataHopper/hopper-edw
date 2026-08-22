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
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Root manifest listing all catalog version tags for a file catalog storage root. */
@Getter
@Setter
@NoArgsConstructor
public class CatalogVersionManifest {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  private int schemaVersion = CURRENT_SCHEMA_VERSION;
  private List<CatalogVersionEntry> versions = new ArrayList<>();

  public List<CatalogVersionEntry> getVersions() {
    if (versions == null) {
      versions = new ArrayList<>();
    }
    return versions;
  }

  public Optional<CatalogVersionEntry> findByTag(String tag) {
    if (tag == null || tag.isBlank()) {
      return Optional.empty();
    }
    String needle = tag.trim();
    for (CatalogVersionEntry entry : getVersions()) {
      if (entry != null && needle.equals(entry.getTag())) {
        return Optional.of(entry);
      }
    }
    return Optional.empty();
  }

  public boolean hasTag(String tag) {
    return findByTag(tag).isPresent();
  }

  public void addVersion(CatalogVersionEntry entry) {
    if (entry != null) {
      getVersions().add(entry);
    }
  }
}
