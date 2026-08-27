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
package org.hopper.edw.datavault.hopgui.resourcedefinition;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.hopgui.HopGui;
import org.hopper.edw.catalog.hopgui.navigation.RecordOriginNavigationSupport;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.hopper.edw.datavault.resourcedefinition.SourceUsage;
import org.hopper.edw.datavault.resourcedefinition.SourceUsageIndexBuilder;

/** Opens DV, BV, or DM model elements referenced by validation usages. */
public final class ResourceDefinitionModelNavigationSupport {

  private ResourceDefinitionModelNavigationSupport() {}

  public static void openUsage(HopGui hopGui, SourceUsage usage, IVariables variables)
      throws HopException {
    if (hopGui == null || usage == null) {
      return;
    }
    RecordOrigin origin = toOrigin(usage);
    RecordOriginNavigationSupport.navigateToOrigin(hopGui, origin, variables);
  }

  public static void openDataVaultUsage(
      HopGui hopGui, SourceUsage usage, String elementName, IVariables variables)
      throws HopException {
    if (hopGui == null || usage == null) {
      return;
    }
    RecordOrigin origin = toOrigin(usage);
    if (!Utils.isEmpty(elementName)) {
      origin.setModelElementName(elementName);
    }
    RecordOriginNavigationSupport.navigateToOrigin(hopGui, origin, variables);
  }

  private static RecordOrigin toOrigin(SourceUsage usage) {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(mapModelType(usage.modelType()));
    origin.setModelFilename(usage.modelFilename());
    origin.setModelElementName(usage.modelElementName());
    return origin;
  }

  private static String mapModelType(String usageModelType) {
    if (SourceUsageIndexBuilder.MODEL_TYPE_DATA_VAULT.equals(usageModelType)) {
      return RecordOriginNavigationSupport.MODEL_TYPE_DATA_VAULT;
    }
    if (SourceUsageIndexBuilder.MODEL_TYPE_BUSINESS_VAULT.equals(usageModelType)) {
      return RecordOriginNavigationSupport.MODEL_TYPE_BUSINESS_VAULT;
    }
    if (SourceUsageIndexBuilder.MODEL_TYPE_DIMENSIONAL.equals(usageModelType)) {
      return RecordOriginNavigationSupport.MODEL_TYPE_DIMENSIONAL;
    }
    return usageModelType;
  }
}
