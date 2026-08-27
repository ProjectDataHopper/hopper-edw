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
package org.hopper.edw.datavault.resourcedefinition;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;

/** Persists DV, BV, and DM models referenced by resource definition validation. */
public final class ResourceDefinitionModelPersistenceSupport {

  private static final Class<?> PKG = ResourceDefinitionModelPersistenceSupport.class;

  private ResourceDefinitionModelPersistenceSupport() {}

  public static void saveDataVaultModel(
      DataVaultModel model, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    saveModel(
        model, model != null ? model.getFilename() : null, HopVaultFileType.XML_TAG, variables);
  }

  public static void saveBusinessVaultModel(
      BusinessVaultModel model, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    saveModel(
        model,
        model != null ? model.getFilename() : null,
        HopBusinessVaultFileType.XML_TAG,
        variables);
  }

  public static void saveDimensionalModel(
      DimensionalModel model, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    saveModel(
        model,
        model != null ? model.getFilename() : null,
        HopDimensionalFileType.XML_TAG,
        variables);
  }

  private static void saveModel(Object model, String filename, String xmlTag, IVariables variables)
      throws HopException {
    if (model == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "ResourceDefinitionModelPersistenceSupport.Error.MissingModel"));
    }
    if (Utils.isEmpty(filename)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "ResourceDefinitionModelPersistenceSupport.Error.MissingFilename"));
    }
    try {
      String resolvedFilename = HopVfs.normalize(variables.resolve(filename));
      ModelXmlWriteSupport.writeModelXml(xmlTag, model, resolvedFilename, variables);
      if (model instanceof DataVaultModel dvModel) {
        dvModel.clearChanged();
      } else if (model instanceof BusinessVaultModel bvModel) {
        bvModel.clearChanged();
      } else if (model instanceof DimensionalModel dmModel) {
        dmModel.clearChanged();
      }
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "ResourceDefinitionModelPersistenceSupport.Error.SaveFailed", filename),
          e);
    }
  }
}
