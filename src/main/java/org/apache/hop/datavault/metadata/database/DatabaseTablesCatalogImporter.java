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
package org.apache.hop.datavault.metadata.database;

import org.apache.hop.catalog.hopgui.perspective.importmenu.DataCatalogImportContext;
import org.apache.hop.catalog.hopgui.perspective.importmenu.DataCatalogImporter;

/** Imports database tables into the data catalog as record definitions. */
public final class DatabaseTablesCatalogImporter implements DataCatalogImporter {

  public static final String IMPORTER_ID = "data-catalog-import-database-tables";

  @Override
  public String getId() {
    return IMPORTER_ID;
  }

  @Override
  public String getLabelMessageKey() {
    return "DatabaseTablesCatalogImporter.Label";
  }

  @Override
  public String getTooltipMessageKey() {
    return "DatabaseTablesCatalogImporter.Tooltip";
  }

  @Override
  public String getImage() {
    return "ui/images/schema.svg";
  }

  @Override
  public int getOrder() {
    return 100;
  }

  @Override
  public void execute(DataCatalogImportContext context) {
    DvDatabaseSourceImportSupport.importDatabaseTables(
        context.getShell(),
        context.getHopGui(),
        context.getVariables(),
        context.getMetadataProvider(),
        context.getModel(),
        context.getPreferredCatalogConnectionName());
  }
}
