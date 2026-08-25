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
package org.hopper.edw.catalog.discovery;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hopper.edw.catalog.model.RecordDefinitionRef;

/** Diagnostic view of a data catalog connection used by Verify, dialogs, and preview. */
@Getter
@Setter
@NoArgsConstructor
public class CatalogDiscoverySnapshot {

  private String connectionName;
  private boolean connectionFound;
  private boolean enabled = true;
  private String pluginId;
  private String storageDirectory;
  private String resolvedStorageDirectory;
  private boolean storageDirectoryExists;
  private int workingTreeCount;
  private int skippedUnreadable;
  private boolean versionSnapshotsPresent;
  private String errorMessage;
  private List<RecordDefinitionRef> refs = new ArrayList<>();
}
