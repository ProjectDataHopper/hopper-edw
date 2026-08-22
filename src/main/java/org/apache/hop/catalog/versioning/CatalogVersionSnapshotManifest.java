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

/** Per-snapshot metadata stored next to frozen record definition JSON files. */
@Getter
@Setter
@NoArgsConstructor
public class CatalogVersionSnapshotManifest {

  private String tag;
  private String snapshotId;
  private String createdAt;
  private String createdBy;
  private String description;
  private CatalogVersionScope scope;
  private int recordCount;
  private String contentHash;
  private List<String> recordRelativePaths = new ArrayList<>();

  public List<String> getRecordRelativePaths() {
    if (recordRelativePaths == null) {
      recordRelativePaths = new ArrayList<>();
    }
    return recordRelativePaths;
  }
}
