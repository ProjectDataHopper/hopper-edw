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
package org.hopper.edw.datavault.metadata.masking;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceMasking;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;

/** Loads a live {@link SourceMasking} for a {@link DvMaskingSource}. */
public final class DvMaskingSourceResolver {

  private DvMaskingSourceResolver() {}

  public record ResolvedMasking(SourceModel model, SourceMasking maskingSource) {}

  public static ResolvedMasking resolve(
      DvMaskingSource maskingSource, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (maskingSource == null) {
      throw new HopException("Masking source is required");
    }
    String modelPath =
        variables != null
            ? variables.resolve(maskingSource.getSourceModelFilename())
            : maskingSource.getSourceModelFilename();
    String maskingName =
        variables != null
            ? variables.resolve(maskingSource.getSourceMaskingName())
            : maskingSource.getSourceMaskingName();
    if (Utils.isEmpty(modelPath)) {
      throw new HopException("Masking source has no source model filename");
    }
    if (Utils.isEmpty(maskingName)) {
      throw new HopException("Masking source has no Source masking name");
    }
    SourceModel model = SourceModelLoadSupport.load(modelPath, variables, metadataProvider);
    if (model == null) {
      throw new HopException("Unable to load source model from '" + modelPath + "'");
    }
    SourceMasking sourceMasking = model.findMaskingSource(maskingName);
    if (sourceMasking == null) {
      throw new HopException(
          "Source masking '" + maskingName + "' not found in model '" + modelPath + "'");
    }
    return new ResolvedMasking(model, sourceMasking);
  }
}
