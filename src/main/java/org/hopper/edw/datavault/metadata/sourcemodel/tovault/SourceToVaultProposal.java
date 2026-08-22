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

/** Classification of one source table (or an implied parent hub) into one or more vault objects. */
@Getter
@Setter
public class SourceToVaultProposal {

  private String sourceTableName;
  private SourceTableRole role = SourceTableRole.SKIP;
  private List<ProposedVaultObject> objects = new ArrayList<>();
  private ClassificationConfidence confidence = ClassificationConfidence.MEDIUM;
  private String evidence;
  private boolean included = true;
  private boolean implied;
  private String skipReason;

  public @NonNull List<ProposedVaultObject> getObjects() {
    if (objects == null) {
      objects = new ArrayList<>();
    }
    return objects;
  }

  public ProposedVaultObject firstOfKind(ProposedObjectKind kind) {
    if (kind == null) {
      return null;
    }
    for (ProposedVaultObject object : getObjects()) {
      if (object != null && kind == object.getKind()) {
        return object;
      }
    }
    return null;
  }
}
