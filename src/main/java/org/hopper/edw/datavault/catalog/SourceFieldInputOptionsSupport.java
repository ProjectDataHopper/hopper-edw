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
package org.hopper.edw.datavault.catalog;

import org.hopper.edw.catalog.model.CatalogCsvFieldOptions;
import org.hopper.edw.catalog.model.CatalogFieldConversionOptions;
import org.hopper.edw.catalog.model.CatalogSourceFieldInputOptions;
import org.hopper.edw.datavault.metadata.CsvFieldOptions;
import org.hopper.edw.datavault.metadata.SourceFieldInputOptions;
import org.hopper.edw.datavault.metadata.datatypemapping.FieldConversionOptions;

/** Maps catalog and Data Vault source field input options. */
final class SourceFieldInputOptionsSupport {

  private SourceFieldInputOptionsSupport() {}

  static CatalogSourceFieldInputOptions toCatalog(SourceFieldInputOptions options) {
    if (options == null) {
      return null;
    }
    CatalogSourceFieldInputOptions catalogOptions = new CatalogSourceFieldInputOptions();
    catalogOptions.setCsv(toCatalogCsv(options.getCsv()));
    catalogOptions.setConversion(toCatalogConversion(options.getConversion()));
    if (catalogOptions.getCsv() == null && catalogOptions.getConversion() == null) {
      return null;
    }
    return catalogOptions;
  }

  static SourceFieldInputOptions fromCatalog(CatalogSourceFieldInputOptions options) {
    if (options == null) {
      return null;
    }
    SourceFieldInputOptions sourceOptions = new SourceFieldInputOptions();
    sourceOptions.setCsv(fromCatalogCsv(options.getCsv()));
    sourceOptions.setConversion(fromCatalogConversion(options.getConversion()));
    if (sourceOptions.getCsv() == null && sourceOptions.getConversion() == null) {
      return null;
    }
    return sourceOptions;
  }

  private static CatalogCsvFieldOptions toCatalogCsv(CsvFieldOptions options) {
    if (options == null) {
      return null;
    }
    CatalogCsvFieldOptions catalogCsv = new CatalogCsvFieldOptions();
    catalogCsv.setFormat(options.getFormat());
    catalogCsv.setDecimalSymbol(options.getDecimalSymbol());
    catalogCsv.setGroupingSymbol(options.getGroupingSymbol());
    catalogCsv.setCurrencySymbol(options.getCurrencySymbol());
    return catalogCsv;
  }

  private static CsvFieldOptions fromCatalogCsv(CatalogCsvFieldOptions options) {
    if (options == null) {
      return null;
    }
    CsvFieldOptions csv = new CsvFieldOptions();
    csv.setFormat(options.getFormat());
    csv.setDecimalSymbol(options.getDecimalSymbol());
    csv.setGroupingSymbol(options.getGroupingSymbol());
    csv.setCurrencySymbol(options.getCurrencySymbol());
    return csv;
  }

  private static CatalogFieldConversionOptions toCatalogConversion(FieldConversionOptions options) {
    if (options == null || !options.hasAnyAttribute()) {
      return null;
    }
    CatalogFieldConversionOptions catalog = new CatalogFieldConversionOptions();
    catalog.setConversionMask(options.getConversionMask());
    catalog.setDecimalSymbol(options.getDecimalSymbol());
    catalog.setGroupingSymbol(options.getGroupingSymbol());
    catalog.setCurrencySymbol(options.getCurrencySymbol());
    catalog.setDateFormatLocale(options.getDateFormatLocale());
    catalog.setDateFormatTimeZone(options.getDateFormatTimeZone());
    catalog.setDateFormatLenient(options.isDateFormatLenient());
    catalog.setLenientStringToNumber(options.isLenientStringToNumber());
    catalog.setEncoding(options.getEncoding());
    catalog.setRoundingType(options.getRoundingType());
    catalog.setStorageType(options.getStorageType());
    catalog.setTrimType(options.getTrimType());
    return catalog;
  }

  private static FieldConversionOptions fromCatalogConversion(
      CatalogFieldConversionOptions options) {
    if (options == null) {
      return null;
    }
    FieldConversionOptions conversion = new FieldConversionOptions();
    conversion.setConversionMask(options.getConversionMask());
    conversion.setDecimalSymbol(options.getDecimalSymbol());
    conversion.setGroupingSymbol(options.getGroupingSymbol());
    conversion.setCurrencySymbol(options.getCurrencySymbol());
    conversion.setDateFormatLocale(options.getDateFormatLocale());
    conversion.setDateFormatTimeZone(options.getDateFormatTimeZone());
    conversion.setDateFormatLenient(options.isDateFormatLenient());
    conversion.setLenientStringToNumber(options.isLenientStringToNumber());
    conversion.setEncoding(options.getEncoding());
    conversion.setRoundingType(options.getRoundingType());
    conversion.setStorageType(options.getStorageType());
    conversion.setTrimType(options.getTrimType());
    return conversion;
  }
}
