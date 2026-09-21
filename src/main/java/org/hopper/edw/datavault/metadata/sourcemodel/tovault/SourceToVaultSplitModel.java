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

import lombok.Getter;
import org.hopper.edw.datavault.metadata.DataVaultModel;

/** One generated {@code .hdv} file and the model that will be written to it. */
@Getter
public class SourceToVaultSplitModel {

  public enum Kind {
    HUB,
    LINK,
    REFERENCE
  }

  private final Kind kind;
  private final String anchorName;
  private final String baseName;
  private final String filename;
  private final DataVaultModel model;

  public SourceToVaultSplitModel(
      Kind kind, String anchorName, String baseName, String filename, DataVaultModel model) {
    this.kind = kind;
    this.anchorName = anchorName;
    this.baseName = baseName;
    this.filename = filename;
    this.model = model;
  }
}
