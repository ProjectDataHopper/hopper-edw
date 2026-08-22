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
package org.apache.hop.datavault.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;

/** Plugin-level default standard column names for new dimensional models. */
@Getter
@Setter
public class DmDefaultFieldNames {

  private String surrogateKeyField = DimensionalConfiguration.DEFAULT_DIM_KEY_FIELD;
  private String versionField = DimensionalConfiguration.DEFAULT_VERSION_FIELD;
  private String effectiveFromField = DimensionalConfiguration.DEFAULT_DATE_FROM_FIELD;
  private String effectiveToField = DimensionalConfiguration.DEFAULT_DATE_TO_FIELD;
  private String loadTimestampField = DimensionalConfiguration.DEFAULT_LOAD_DATE_FIELD;
  private String currentFlagField = DimensionalConfiguration.DEFAULT_CURRENT_FLAG_FIELD;

  public DmDefaultFieldNames() {}

  public DmDefaultFieldNames(DmDefaultFieldNames defaults) {
    if (defaults == null) {
      return;
    }
    surrogateKeyField = defaults.surrogateKeyField;
    versionField = defaults.versionField;
    effectiveFromField = defaults.effectiveFromField;
    effectiveToField = defaults.effectiveToField;
    loadTimestampField = defaults.loadTimestampField;
    currentFlagField = defaults.currentFlagField;
  }

  public void applyTo(DimensionalConfiguration configuration) {
    if (configuration == null) {
      return;
    }
    configuration.setDimKeyField(
        resolveOrDefault(surrogateKeyField, DimensionalConfiguration.DEFAULT_DIM_KEY_FIELD));
    configuration.setVersionField(
        resolveOrDefault(versionField, DimensionalConfiguration.DEFAULT_VERSION_FIELD));
    configuration.setDateFromField(
        resolveOrDefault(effectiveFromField, DimensionalConfiguration.DEFAULT_DATE_FROM_FIELD));
    configuration.setDateToField(
        resolveOrDefault(effectiveToField, DimensionalConfiguration.DEFAULT_DATE_TO_FIELD));
    configuration.setLoadDateField(
        resolveOrDefault(loadTimestampField, DimensionalConfiguration.DEFAULT_LOAD_DATE_FIELD));
    configuration.setCurrentFlagField(
        resolveOrDefault(currentFlagField, DimensionalConfiguration.DEFAULT_CURRENT_FLAG_FIELD));
  }

  private static String resolveOrDefault(String value, String defaultValue) {
    return Utils.isEmpty(value) ? defaultValue : value.trim();
  }
}
