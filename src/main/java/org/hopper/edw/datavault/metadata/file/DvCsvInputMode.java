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
package org.hopper.edw.datavault.metadata.file;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;

/** How a CSV Data Vault source is read in generated pipelines. */
@Getter
public enum DvCsvInputMode implements IEnumHasCodeAndDescription {
  TEXT_FILE_INPUT(
      "TEXT_FILE_INPUT",
      BaseMessages.getString(DvCsvInputMode.class, "DvCsvInputMode.TextFileInput")),
  CSV_INPUT("CSV_INPUT", BaseMessages.getString(DvCsvInputMode.class, "DvCsvInputMode.CsvInput"));

  private final String code;
  private final String description;

  DvCsvInputMode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(DvCsvInputMode.class);
  }

  public static DvCsvInputMode lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        DvCsvInputMode.class, description, TEXT_FILE_INPUT);
  }

  public static DvCsvInputMode lookupCode(String code) {
    return IEnumHasCode.lookupCode(DvCsvInputMode.class, code, TEXT_FILE_INPUT);
  }
}
