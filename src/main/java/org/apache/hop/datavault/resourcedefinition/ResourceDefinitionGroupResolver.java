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

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.ModelConfigurationResolver;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultDvModelResolver;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Loads DV, BV, and DM models referenced by a resource definition group. */
public final class ResourceDefinitionGroupResolver {

  private static final Class<?> PKG = ResourceDefinitionGroupResolver.class;

  private ResourceDefinitionGroupResolver() {}

  public static ValidationModels resolve(
      ResourceDefinitionGroupMeta group,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (group == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "ResourceDefinitionGroupResolver.Error.MissingGroup"));
    }

    List<ValidationModels.LoadedDataVaultModel> dvModels = new ArrayList<>();
    for (String modelFile : group.getDataVaultModelFiles()) {
      if (!Utils.isEmpty(modelFile)) {
        DataVaultModel model = loadDataVaultModel(modelFile, variables, metadataProvider);
        String catalogConnection =
            resolveGroupCatalogConnection(
                group,
                model.getConfigurationOrDefault().getDataCatalogConnection(),
                variables,
                metadataProvider);
        dvModels.add(new ValidationModels.LoadedDataVaultModel(model, catalogConnection));
      }
    }

    List<ValidationModels.LoadedBusinessVaultModel> bvModels = new ArrayList<>();
    for (String modelFile : group.getBusinessVaultModelFiles()) {
      if (!Utils.isEmpty(modelFile)) {
        BusinessVaultModel model = loadBusinessVaultModel(modelFile, variables, metadataProvider);
        DataVaultModel dvModel =
            BusinessVaultDvModelResolver.buildEffectiveDataVaultModel(
                model, variables, metadataProvider);
        String catalogConnection =
            resolveGroupCatalogConnection(group, null, variables, metadataProvider);
        bvModels.add(
            new ValidationModels.LoadedBusinessVaultModel(model, dvModel, catalogConnection));
      }
    }

    List<ValidationModels.LoadedDimensionalModel> dmModels = new ArrayList<>();
    for (String modelFile : group.getDimensionalModelFiles()) {
      if (!Utils.isEmpty(modelFile)) {
        DimensionalModel model = loadDimensionalModel(modelFile, variables, metadataProvider);
        String catalogConnection =
            resolveGroupCatalogConnection(
                group,
                model.getConfigurationOrDefault().getDataCatalogConnection(),
                variables,
                metadataProvider);
        dmModels.add(new ValidationModels.LoadedDimensionalModel(model, catalogConnection));
      }
    }

    return new ValidationModels(group, dvModels, bvModels, dmModels);
  }

  public static ResourceDefinitionGroupMeta loadGroup(
      String groupName, IHopMetadataProvider metadataProvider) throws HopException {
    if (Utils.isEmpty(groupName) || metadataProvider == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "ResourceDefinitionGroupResolver.Error.MissingGroupName"));
    }
    ResourceDefinitionGroupMeta group =
        metadataProvider.getSerializer(ResourceDefinitionGroupMeta.class).load(groupName);
    if (group == null) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "ResourceDefinitionGroupResolver.Error.GroupNotFound", groupName));
    }
    return group;
  }

  private static String resolveGroupCatalogConnection(
      ResourceDefinitionGroupMeta group,
      String modelCatalogConnection,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String configured = group != null ? group.getDataCatalogConnection() : null;
    if (Utils.isEmpty(configured)) {
      configured = modelCatalogConnection;
    }
    if (variables != null && !Utils.isEmpty(configured)) {
      configured = variables.resolve(configured);
    }
    return DvSourceCatalogService.resolvePreferredCatalogConnection(
        configured, variables, metadataProvider);
  }

  public static DataVaultModel loadDataVaultModel(
      String modelFile, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return loadModelFile(
        modelFile,
        variables,
        metadataProvider,
        HopVaultFileType.XML_TAG,
        DataVaultModel.class,
        "ResourceDefinitionGroupResolver.Error.LoadDataVaultModel");
  }

  public static BusinessVaultModel loadBusinessVaultModel(
      String modelFile, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return loadModelFile(
        modelFile,
        variables,
        metadataProvider,
        HopBusinessVaultFileType.XML_TAG,
        BusinessVaultModel.class,
        "ResourceDefinitionGroupResolver.Error.LoadBusinessVaultModel");
  }

  public static DimensionalModel loadDimensionalModel(
      String modelFile, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return loadModelFile(
        modelFile,
        variables,
        metadataProvider,
        HopDimensionalFileType.XML_TAG,
        DimensionalModel.class,
        "ResourceDefinitionGroupResolver.Error.LoadDimensionalModel");
  }

  private static <T> T loadModelFile(
      String modelFile,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String xmlTag,
      Class<T> modelClass,
      String errorMessageKey)
      throws HopException {
    try {
      String resolvedPath = HopVfs.normalize(variables.resolve(modelFile));
      Document document = XmlHandler.loadXmlFile(resolvedPath);
      Node rootNode = XmlHandler.getSubNode(document, xmlTag);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      T model = modelClass.getDeclaredConstructor().newInstance();
      XmlMetadataUtil.deSerializeFromXml(rootNode, modelClass, model, metadataProvider);
      ModelConfigurationResolver.attach(model, metadataProvider);
      if (model instanceof DataVaultModel dvModel) {
        dvModel.setFilename(resolvedPath);
        dvModel.clearChanged();
      } else if (model instanceof BusinessVaultModel bvModel) {
        bvModel.setFilename(resolvedPath);
        bvModel.clearChanged();
      } else if (model instanceof DimensionalModel dmModel) {
        dmModel.setFilename(resolvedPath);
        dmModel.clearChanged();
      }
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(BaseMessages.getString(PKG, errorMessageKey, modelFile), e);
    }
  }
}
