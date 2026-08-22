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
package org.hopper.edw.catalog.hopgui.perspective.importmenu;

/**
 * Pluggable data catalog import action shown in the Data Catalog perspective import menu.
 *
 * <p>Register additional implementations with {@link
 * DataCatalogImporterRegistry#register(DataCatalogImporter)}.
 */
public interface DataCatalogImporter {

  /** Unique identifier for this importer (stable across releases). */
  String getId();

  /** i18n message key for the menu item label (resolved with the importer class as package). */
  String getLabelMessageKey();

  /** i18n message key for the menu item tooltip. */
  String getTooltipMessageKey();

  /** SVG image path for the menu item. */
  String getImage();

  /** Sort order in the import menu (lower values appear first). */
  int getOrder();

  /** Runs the import using the supplied context. */
  void execute(DataCatalogImportContext context);
}
