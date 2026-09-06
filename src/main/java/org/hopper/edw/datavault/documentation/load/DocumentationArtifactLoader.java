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
package org.hopper.edw.datavault.documentation.load;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.executionmap.ExecutionMapPersistence;
import org.hopper.edw.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.hopgui.file.vault.HopVaultFileType;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelLoadSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Loads EDW model files for documentation (same XML tags as hop svg). */
public final class DocumentationArtifactLoader {

  private DocumentationArtifactLoader() {}

  public static SourceModel loadSourceModel(
      String filename, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    return SourceModelLoadSupport.load(filename, variables, metadataProvider);
  }

  public static DataVaultModel loadDataVaultModel(
      String filename, IHopMetadataProvider metadataProvider) throws HopException {
    try {
      Document document = XmlHandler.loadXmlFile(filename);
      Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
      DataVaultModel model = new DataVaultModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DataVaultModel.class, model, metadataProvider);
      ModelConfigurationResolver.attach(model, metadataProvider);
      model.setFilename(filename);
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load Data Vault model " + filename, e);
    }
  }

  public static BusinessVaultModel loadBusinessVaultModel(
      String filename, IHopMetadataProvider metadataProvider) throws HopException {
    try {
      Document document = XmlHandler.loadXmlFile(filename);
      Node rootNode = XmlHandler.getSubNode(document, HopBusinessVaultFileType.XML_TAG);
      BusinessVaultModel model = new BusinessVaultModel();
      XmlMetadataUtil.deSerializeFromXml(
          rootNode, BusinessVaultModel.class, model, metadataProvider);
      ModelConfigurationResolver.attach(model, metadataProvider);
      model.setFilename(filename);
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load Business Vault model " + filename, e);
    }
  }

  public static DimensionalModel loadDimensionalModel(
      String filename, IHopMetadataProvider metadataProvider) throws HopException {
    try {
      Document document = XmlHandler.loadXmlFile(filename);
      Node rootNode = XmlHandler.getSubNode(document, HopDimensionalFileType.XML_TAG);
      DimensionalModel model = new DimensionalModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, DimensionalModel.class, model, metadataProvider);
      ModelConfigurationResolver.attach(model, metadataProvider);
      model.setFilename(filename);
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load dimensional model " + filename, e);
    }
  }

  public static ExecutionMapDocument loadExecutionMap(
      String filename, IHopMetadataProvider metadataProvider, IVariables variables)
      throws HopException {
    return ExecutionMapPersistence.load(filename, metadataProvider, variables);
  }
}
