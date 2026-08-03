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
package org.apache.hop.catalog.spi;

import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.metadata.DataCatalogMetaObjectFactory;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Creates connected {@link IDataCatalog} instances from {@link DataCatalogMeta} connections using
 * {@link DataCatalogMetaObjectFactory} (no Hop plugin type registration required).
 */
public final class DataCatalogPluginFactory {

  private DataCatalogPluginFactory() {}

  public static IDataCatalog createConnected(
      DataCatalogMeta meta, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    IDataCatalog template = meta.getCatalogOrDefault();
    if (Utils.isEmpty(template.getPluginId())) {
      throw new HopException(
          "Data catalog connection '" + meta.getName() + "' has no catalog type id configured");
    }
    IDataCatalog instance = newInstance(template);
    instance.connect(meta, variables, metadataProvider);
    return instance;
  }

  public static IDataCatalog newInstance(IDataCatalog template) throws HopException {
    IDataCatalog instance = DataCatalogMetaObjectFactory.newCatalog(template.getPluginId());
    copySettings(template, instance);
    return instance;
  }

  private static void copySettings(IDataCatalog template, IDataCatalog instance) {
    instance.setPluginId(template.getPluginId());
    if (template instanceof FileDataCatalog fileTemplate
        && instance instanceof FileDataCatalog fileInstance) {
      fileInstance.setStorageDirectory(fileTemplate.getStorageDirectory());
    }
  }
}
