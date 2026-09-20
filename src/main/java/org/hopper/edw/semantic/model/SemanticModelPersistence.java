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
package org.hopper.edw.semantic.model;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.hopper.edw.datavault.metadata.ModelXmlWriteSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Load / save `.hsl` documents via Hop VFS. */
public final class SemanticModelPersistence {

  private SemanticModelPersistence() {}

  public static SemanticModel load(
      String filename, IHopMetadataProvider metadataProvider, IVariables variables)
      throws HopException {
    try {
      Document document = XmlHandler.loadXmlFile(filename);
      Node rootNode = XmlHandler.getSubNode(document, SemanticModel.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      SemanticModel model = new SemanticModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, SemanticModel.class, model, metadataProvider);
      model.setFilename(filename);
      model.clearChanged();
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load semantic model from " + filename, e);
    }
  }

  public static void save(SemanticModel model, String filename, IVariables variables)
      throws HopException {
    if (model == null) {
      throw new HopException("Semantic model is required");
    }
    ModelXmlWriteSupport.writeModelXml(SemanticModel.XML_TAG, model, filename, variables);
    model.setFilename(filename);
    model.clearChanged();
  }
}
