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

import lombok.Getter;
import lombok.Setter;

/** How a Data Vault source supplies the record source value for target loads. */
@Getter
@Setter
public class RecordSourceIndicatorOptions {

  public enum Mode {
    /** Fixed string written to the record source column (e.g. CRM). */
    STATIC,
    /** Value taken from a column in the source feed. */
    FIELD,
    /** Auto-detect a record source column per table; fall back to static when absent. */
    FIELD_OR_STATIC
  }

  private Mode mode = Mode.STATIC;
  private String staticValue;
  private String fieldName;

  public static RecordSourceIndicatorOptions staticValue(String value) {
    RecordSourceIndicatorOptions options = new RecordSourceIndicatorOptions();
    options.setMode(Mode.STATIC);
    options.setStaticValue(value);
    return options;
  }

  public static RecordSourceIndicatorOptions fieldName(String fieldName) {
    RecordSourceIndicatorOptions options = new RecordSourceIndicatorOptions();
    options.setMode(Mode.FIELD);
    options.setFieldName(fieldName);
    return options;
  }

  public static RecordSourceIndicatorOptions fieldOrStatic(String staticFallback) {
    RecordSourceIndicatorOptions options = new RecordSourceIndicatorOptions();
    options.setMode(Mode.FIELD_OR_STATIC);
    options.setStaticValue(staticFallback);
    return options;
  }
}
