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
package org.hopper.edw.catalog.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** CSV / delimited text format options for a catalog DV source of type CSV. */
@Getter
@Setter
@NoArgsConstructor
public class DvCsvFormatRecord {

  @HopMetadataProperty private String delimiter = ",";

  @HopMetadataProperty private String enclosure = "\"";

  @HopMetadataProperty private String escapeCharacter;

  @HopMetadataProperty private String encoding;

  @HopMetadataProperty private boolean headerPresent = true;

  @HopMetadataProperty private int headerLines = 1;

  @HopMetadataProperty private String fileFormat = "CSV";

  /** {@code TEXT_FILE_INPUT} (default) or {@code CSV_INPUT}. */
  @HopMetadataProperty private String inputTransform = "TEXT_FILE_INPUT";

  /** Optional explicit file path when not using folder + masks. */
  @HopMetadataProperty private String singleFilename;
}
