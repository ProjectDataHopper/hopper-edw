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
package org.hopper.edw.datavault.lineageview;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;
import org.hopper.edw.datavault.hopgui.file.lineageview.HopLineageViewFileType;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Loads and saves {@code .hlv} view-definition files. */
public final class LineageViewPersistence {

  private LineageViewPersistence() {}

  public static HopLineageViewDocument load(
      String filename, IHopMetadataProvider metadataProvider, IVariables variables)
      throws HopException {
    try {
      Document xml = XmlHandler.loadXmlFile(filename);
      Node rootNode = XmlHandler.getSubNode(xml, HopLineageViewFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = xml.getDocumentElement();
      }
      HopLineageViewDocument document = new HopLineageViewDocument();
      XmlMetadataUtil.deSerializeFromXml(
          rootNode, HopLineageViewDocument.class, document, metadataProvider);
      document.setFilename(filename);
      return document;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load lineage view file: " + filename, e);
    }
  }

  public static void save(HopLineageViewDocument document, String filename, IVariables variables)
      throws HopException {
    if (document == null) {
      throw new HopException("No lineage view document to save");
    }
    try {
      document.setFilename(filename);
      document.setName(document.getName());
      document.setModelFilename(
          CatalogModelRegistrySupport.portableModelPath(document.getModelFilename(), variables));
      ModelXmlWriteSupport.writeModelXml(
          HopLineageViewFileType.XML_TAG, document, filename, variables);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to save lineage view file: " + filename, e);
    }
  }
}
