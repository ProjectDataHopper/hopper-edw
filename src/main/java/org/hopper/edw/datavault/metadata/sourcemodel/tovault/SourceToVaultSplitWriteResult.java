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

/** Files written or left untouched by a split generate. */
@Getter
public class SourceToVaultSplitWriteResult {

  private final List<String> writtenFilenames = new ArrayList<>();
  private final List<String> skippedFilenames = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();
  private int hubFiles;
  private int linkFiles;
  private int referenceFiles;

  void addWritten(SourceToVaultSplitModel model) {
    writtenFilenames.add(model.getFilename());
    switch (model.getKind()) {
      case HUB -> hubFiles++;
      case LINK -> linkFiles++;
      case REFERENCE -> referenceFiles++;
    }
  }

  void addSkipped(String filename) {
    skippedFilenames.add(filename);
  }
}
