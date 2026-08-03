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
package org.apache.hop.datavault.resourcedefinition;

import java.util.List;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;

/** Loaded models and settings resolved from a resource definition group. */
public record ValidationModels(
    ResourceDefinitionGroupMeta group,
    List<LoadedDataVaultModel> dataVaultModels,
    List<LoadedBusinessVaultModel> businessVaultModels,
    List<LoadedDimensionalModel> dimensionalModels) {

  public record LoadedDataVaultModel(DataVaultModel model, String catalogConnection) {}

  public record LoadedBusinessVaultModel(
      BusinessVaultModel model, DataVaultModel dvModel, String catalogConnection) {}

  public record LoadedDimensionalModel(DimensionalModel model, String catalogConnection) {}
}
