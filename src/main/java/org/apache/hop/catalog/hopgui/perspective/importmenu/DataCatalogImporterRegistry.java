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
package org.apache.hop.catalog.hopgui.perspective.importmenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.database.DatabaseTablesCatalogImporter;
import org.apache.hop.datavault.metadata.file.CsvFileCatalogImporter;
import org.apache.hop.datavault.metadata.file.ParquetFileCatalogImporter;
import org.apache.hop.datavault.metadata.iceberg.IcebergTableCatalogImporter;

/** Registry of data catalog import actions. */
public final class DataCatalogImporterRegistry {

  private static final Map<String, DataCatalogImporter> IMPORTERS = new LinkedHashMap<>();

  static {
    register(new DatabaseTablesCatalogImporter());
    register(new CsvFileCatalogImporter());
    register(new ParquetFileCatalogImporter());
    register(new IcebergTableCatalogImporter());
  }

  private DataCatalogImporterRegistry() {}

  public static synchronized void register(DataCatalogImporter importer) {
    if (importer == null || Utils.isEmpty(importer.getId())) {
      return;
    }
    IMPORTERS.put(importer.getId(), importer);
  }

  public static synchronized void unregister(String importerId) {
    if (!Utils.isEmpty(importerId)) {
      IMPORTERS.remove(importerId);
    }
  }

  public static synchronized List<DataCatalogImporter> getImporters() {
    List<DataCatalogImporter> importers = new ArrayList<>(IMPORTERS.values());
    importers.sort(
        Comparator.comparingInt(DataCatalogImporter::getOrder)
            .thenComparing(DataCatalogImporter::getId));
    return importers;
  }
}
