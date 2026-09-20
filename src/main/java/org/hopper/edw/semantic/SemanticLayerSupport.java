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
package org.hopper.edw.semantic;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmModelLoadSupport;
import org.hopper.edw.semantic.bind.HdmSemanticSeed;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticModelPersistence;

/** Load a bound `.hdm` or seed a semantic layer from one. */
public final class SemanticLayerSupport {

  private SemanticLayerSupport() {}

  public static DimensionalModel loadBoundDimensionalModel(
      SemanticModel semantic, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (semantic == null || Utils.isEmpty(semantic.getDimensionalModelFilename())) {
      throw new HopException("Select a dimensional model for this semantic layer first");
    }
    return DmModelLoadSupport.loadDimensionalModel(
        semantic.getDimensionalModelFilename(),
        semantic.getFilename(),
        variables,
        metadataProvider);
  }

  public static SemanticModel resolveOrSeed(
      DimensionalModel dimensionalModel,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (dimensionalModel == null) {
      throw new HopException("Dimensional model is required");
    }
    String filename = dimensionalModel.getSemanticModelFilename();
    if (Utils.isEmpty(filename)) {
      filename = defaultSemanticFilename(dimensionalModel.getFilename());
    }
    if (!Utils.isEmpty(filename) && HopVfs.fileExists(filename)) {
      return SemanticModelPersistence.load(filename, metadataProvider, variables);
    }
    return HdmSemanticSeed.seed(dimensionalModel, variables, metadataProvider);
  }

  public static String defaultSemanticFilename(String dimensionalFilename) {
    if (Utils.isEmpty(dimensionalFilename)) {
      return null;
    }
    String lower = dimensionalFilename.toLowerCase();
    if (lower.endsWith(".hdm")) {
      return dimensionalFilename.substring(0, dimensionalFilename.length() - 4)
          + SemanticModel.FILE_EXTENSION;
    }
    return dimensionalFilename + SemanticModel.FILE_EXTENSION;
  }
}
