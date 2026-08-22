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
package org.hopper.edw.datavault.metadata.sourcemodel.tovault;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;

/** Outcome of applying accepted classification proposals to a Data Vault model. */
@Getter
@Setter
public class SourceToVaultApplyResult {

  private List<String> createdTableNames = new ArrayList<>();
  private List<String> reusedTableNames = new ArrayList<>();
  private List<String> publishedFeeds = new ArrayList<>();
  private List<String> warnings = new ArrayList<>();

  public @NonNull List<String> getCreatedTableNames() {
    if (createdTableNames == null) {
      createdTableNames = new ArrayList<>();
    }
    return createdTableNames;
  }

  public @NonNull List<String> getReusedTableNames() {
    if (reusedTableNames == null) {
      reusedTableNames = new ArrayList<>();
    }
    return reusedTableNames;
  }

  public @NonNull List<String> getPublishedFeeds() {
    if (publishedFeeds == null) {
      publishedFeeds = new ArrayList<>();
    }
    return publishedFeeds;
  }

  public @NonNull List<String> getWarnings() {
    if (warnings == null) {
      warnings = new ArrayList<>();
    }
    return warnings;
  }
}
