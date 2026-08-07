/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata.json;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJson;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Loads a live {@link SourceJson} for a {@link DvJsonSource}. */
public final class DvJsonSourceResolver {

  private DvJsonSourceResolver() {}

  public record ResolvedJson(SourceModel model, SourceJson jsonSource) {}

  public static ResolvedJson resolve(
      DvJsonSource jsonSource, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (jsonSource == null) {
      throw new HopException("JSON source is required");
    }
    String modelPath =
        variables != null
            ? variables.resolve(jsonSource.getSourceModelFilename())
            : jsonSource.getSourceModelFilename();
    String jsonName =
        variables != null
            ? variables.resolve(jsonSource.getSourceJsonName())
            : jsonSource.getSourceJsonName();
    if (Utils.isEmpty(modelPath)) {
      throw new HopException("JSON source has no source model filename");
    }
    if (Utils.isEmpty(jsonName)) {
      throw new HopException("JSON source has no Source JSON name");
    }
    SourceModel model = SourceModelLoadSupport.load(modelPath, variables, metadataProvider);
    if (model == null) {
      throw new HopException("Unable to load source model from '" + modelPath + "'");
    }
    SourceJson sourceJson = model.findJsonSource(jsonName);
    if (sourceJson == null) {
      throw new HopException(
          "Source JSON '" + jsonName + "' not found in model '" + modelPath + "'");
    }
    return new ResolvedJson(model, sourceJson);
  }
}
