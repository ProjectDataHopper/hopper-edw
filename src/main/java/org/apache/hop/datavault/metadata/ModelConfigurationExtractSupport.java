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
package org.apache.hop.datavault.metadata;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelConfiguration;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.metadata.MetadataManager;
import org.apache.hop.ui.hopgui.HopGui;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Copies an embedded model configuration into a named project metadata object. */
public final class ModelConfigurationExtractSupport {

  private static final Class<?> PKG = ModelConfigurationExtractSupport.class;

  private ModelConfigurationExtractSupport() {}

  public static boolean extract(HopGui hopGui, DataVaultModel model) {
    if (hopGui == null || model == null) {
      return false;
    }
    DataVaultConfiguration created =
        extractMetadata(
            hopGui, DataVaultConfiguration.class, clone(model.getConfigurationOrDefault()));
    if (created == null || Utils.isEmpty(created.getName())) {
      return false;
    }
    model.setConfigurationName(created.getName());
    model.setConfiguration(null);
    model.setChanged();
    return true;
  }

  public static boolean extract(HopGui hopGui, SourceModel model) {
    if (hopGui == null || model == null) {
      return false;
    }
    SourceModelConfiguration created =
        extractMetadata(
            hopGui, SourceModelConfiguration.class, clone(model.getConfigurationOrDefault()));
    if (created == null || Utils.isEmpty(created.getName())) {
      return false;
    }
    model.setConfigurationName(created.getName());
    model.setConfiguration(null);
    model.setChanged();
    return true;
  }

  public static boolean extract(HopGui hopGui, BusinessVaultModel model) {
    if (hopGui == null || model == null) {
      return false;
    }
    BusinessVaultConfiguration created =
        extractMetadata(
            hopGui, BusinessVaultConfiguration.class, clone(model.getConfigurationOrDefault()));
    if (created == null || Utils.isEmpty(created.getName())) {
      return false;
    }
    model.setConfigurationName(created.getName());
    model.setConfiguration(null);
    model.setChanged();
    return true;
  }

  public static boolean extract(HopGui hopGui, DimensionalModel model) {
    if (hopGui == null || model == null) {
      return false;
    }
    DimensionalConfiguration created =
        extractMetadata(
            hopGui, DimensionalConfiguration.class, clone(model.getConfigurationOrDefault()));
    if (created == null || Utils.isEmpty(created.getName())) {
      return false;
    }
    model.setConfigurationName(created.getName());
    model.setConfiguration(null);
    model.setChanged();
    return true;
  }

  private static <T extends IHopMetadata> T extractMetadata(HopGui hopGui, Class<T> type, T seed) {
    try {
      MetadataManager<T> manager =
          new MetadataManager<>(
              hopGui.getVariables(), hopGui.getMetadataProvider(), type, hopGui.getShell());
      return manager.newMetadata(seed);
    } catch (Exception e) {
      new ErrorDialog(
          hopGui.getShell(),
          BaseMessages.getString(PKG, "ModelConfigurationExtractSupport.Error.Title"),
          BaseMessages.getString(PKG, "ModelConfigurationExtractSupport.Error.Message"),
          e);
      return null;
    }
  }

  static <T> T clone(T source) {
    if (source == null) {
      return null;
    }
    try {
      @SuppressWarnings("unchecked")
      Class<T> type = (Class<T>) source.getClass();
      String xml = XmlHandler.aroundTag("clone", XmlMetadataUtil.serializeObjectToXml(source));
      Document document = XmlHandler.loadXmlString(xml);
      Node root = XmlHandler.getSubNode(document, "clone");
      T copy = type.getDeclaredConstructor().newInstance();
      XmlMetadataUtil.deSerializeFromXml(root, type, copy, null);
      if (copy instanceof IHopMetadata metadata) {
        metadata.setName(null);
      }
      return copy;
    } catch (HopException | ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to clone model configuration", e);
    }
  }
}
