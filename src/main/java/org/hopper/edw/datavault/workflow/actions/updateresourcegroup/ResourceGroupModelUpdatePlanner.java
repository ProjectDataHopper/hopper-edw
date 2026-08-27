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
package org.hopper.edw.datavault.workflow.actions.updateresourcegroup;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;

/**
 * Builds the ordered list of model update jobs for a resource definition group.
 *
 * <p>Layer order is fixed: Data Vault → Business Vault → Dimensional. Within each layer, order
 * follows the list order on the resource definition group metadata.
 */
public final class ResourceGroupModelUpdatePlanner {

  public enum ModelLayer {
    DATA_VAULT,
    BUSINESS_VAULT,
    DIMENSIONAL
  }

  public record ModelUpdateJob(ModelLayer layer, String modelFile) {
    public ModelUpdateJob {
      if (layer == null) {
        throw new IllegalArgumentException("layer is required");
      }
      if (Utils.isEmpty(modelFile)) {
        throw new IllegalArgumentException("modelFile is required");
      }
    }
  }

  private ResourceGroupModelUpdatePlanner() {}

  public static List<ModelUpdateJob> plan(
      ResourceDefinitionGroupMeta group,
      boolean includeDataVault,
      boolean includeBusinessVault,
      boolean includeDimensional,
      IVariables variables) {
    List<ModelUpdateJob> jobs = new ArrayList<>();
    if (group == null) {
      return jobs;
    }
    if (includeDataVault) {
      appendLayer(jobs, ModelLayer.DATA_VAULT, group.getDataVaultModelFiles(), variables);
    }
    if (includeBusinessVault) {
      appendLayer(jobs, ModelLayer.BUSINESS_VAULT, group.getBusinessVaultModelFiles(), variables);
    }
    if (includeDimensional) {
      appendLayer(jobs, ModelLayer.DIMENSIONAL, group.getDimensionalModelFiles(), variables);
    }
    return jobs;
  }

  private static void appendLayer(
      List<ModelUpdateJob> jobs, ModelLayer layer, List<String> modelFiles, IVariables variables) {
    if (modelFiles == null) {
      return;
    }
    for (String raw : modelFiles) {
      if (Utils.isEmpty(raw)) {
        continue;
      }
      String path = variables != null ? variables.resolve(raw.trim()) : raw.trim();
      if (!Utils.isEmpty(path)) {
        jobs.add(new ModelUpdateJob(layer, path));
      }
    }
  }
}
