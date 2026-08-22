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
package org.hopper.edw.catalog.metadata;

import java.util.List;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.spi.IDataCatalog;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.metadata.api.IHopMetadataObjectFactory;

/**
 * Instantiates {@link IDataCatalog} implementations from a persisted type id (same pattern as
 * {@link org.apache.hop.core.database.DatabaseMetaObjectFactory}).
 */
public class DataCatalogMetaObjectFactory implements IHopMetadataObjectFactory {

  @Override
  public Object createObject(String id, Object parentObject) throws HopException {
    return newCatalog(id);
  }

  @Override
  public String getObjectId(Object object) throws HopException {
    if (!(object instanceof IDataCatalog catalog)) {
      throw new HopException(
          "Object is not of class IDataCatalog but of " + object.getClass().getName());
    }
    return catalog.getPluginId();
  }

  /** Type ids for catalog implementations known to this factory. */
  public static List<String> getKnownTypeIds() {
    return List.of(FileDataCatalog.PLUGIN_ID);
  }

  /** Creates a fresh catalog implementation for the given type id. */
  public static IDataCatalog newCatalog(String id) throws HopException {
    if (FileDataCatalog.PLUGIN_ID.equals(id)) {
      return new FileDataCatalog();
    }
    throw new HopException("Unknown data catalog type id '" + id + "'");
  }
}
