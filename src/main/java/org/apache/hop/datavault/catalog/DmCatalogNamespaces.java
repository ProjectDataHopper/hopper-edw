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
package org.apache.hop.datavault.catalog;

import java.nio.file.Path;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;

/** Shared namespace helpers for dimensional model catalog record definitions. */
public final class DmCatalogNamespaces {

  private DmCatalogNamespaces() {}

  public static String projectDimensionalModelsNamespace(
      IVariables variables, DimensionalModel model) {
    return "hop/" + resolveProjectKey(variables) + "/dimensional/" + resolveModelBasename(model);
  }

  public static String resolveProjectKey(IVariables variables) {
    return DvCatalogNamespaces.resolveProjectKey(variables);
  }

  public static String resolveModelBasename(DimensionalModel model) {
    if (model == null) {
      return "model";
    }
    String name = model.getName();
    if (!Utils.isEmpty(name)) {
      return sanitizeKeySegment(name);
    }
    String filename = model.getFilename();
    if (!Utils.isEmpty(filename)) {
      Path path = Path.of(filename);
      Path fileName = path.getFileName();
      if (fileName != null) {
        String base = fileName.toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
          return sanitizeKeySegment(base.substring(0, dot));
        }
        return sanitizeKeySegment(base);
      }
    }
    return "model";
  }

  private static String sanitizeKeySegment(String value) {
    if (Utils.isEmpty(value)) {
      return "unknown";
    }
    return value.replace('\\', '_').replace('/', '_').replace(' ', '_');
  }
}
