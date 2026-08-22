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
package org.hopper.edw.datavault.metadata.sourcemodel;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.hopper.edw.datavault.metadata.ModelConfigurationResolver;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Load {@link SourceModel} documents from VFS paths. */
public final class SourceModelLoadSupport {

  private SourceModelLoadSupport() {}

  public static SourceModel load(
      String filename, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (filename == null || filename.isBlank()) {
      throw new HopException("Source model filename is empty");
    }
    String resolved = HopVfs.normalize(variables != null ? variables.resolve(filename) : filename);
    try {
      Document document = XmlHandler.loadXmlFile(resolved);
      Node rootNode = XmlHandler.getSubNode(document, SourceModel.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      SourceModel model = new SourceModel();
      XmlMetadataUtil.deSerializeFromXml(rootNode, SourceModel.class, model, metadataProvider);
      ModelConfigurationResolver.attach(model, metadataProvider);
      // Drop edges whose endpoints no longer exist (e.g. after a rename saved without cleanup).
      // Does not mark the model dirty so open alone does not force a save; the next edit/save
      // persists the pruned list.
      SourceRelationshipLifecycleSupport.removeDanglingRelationships(model);
      model.clearChanged();
      model.setFilename(resolved);
      return model;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Error loading source model from '" + resolved + "'", e);
    }
  }
}
