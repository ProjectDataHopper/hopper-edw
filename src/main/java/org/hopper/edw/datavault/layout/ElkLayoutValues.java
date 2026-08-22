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
package org.hopper.edw.datavault.layout;

import org.hopper.edw.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;
import org.eclipse.swt.widgets.Combo;

/** Shared parsing and enum helpers for {@link ElkLayout} editors. */
public final class ElkLayoutValues {

  private ElkLayoutValues() {}

  public static int parseNonNegativeInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(0, parsed);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public static int parsePositiveInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public static <E extends Enum<E> & IEnumHasCodeAndDescription> E parseEnum(
      String value, Class<E> enumClass) {
    E defaultValue = enumClass.getEnumConstants()[0];
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    E byDescription = IEnumHasCodeAndDescription.lookupDescription(enumClass, value.trim(), null);
    if (byDescription != null) {
      return byDescription;
    }
    return IEnumHasCode.lookupCode(enumClass, value.trim(), defaultValue);
  }

  public static <E extends Enum<E> & IEnumHasCodeAndDescription> void populateEnumCombo(
      Combo combo, Class<E> enumClass) {
    EnumDialogSupport.populateCombo(combo, enumClass);
  }

  public static <E extends Enum<E> & IEnumHasCodeAndDescription> void selectEnumCombo(
      Combo combo, E value) {
    if (value == null) {
      combo.select(0);
      return;
    }
    EnumDialogSupport.selectCombo(combo, value);
  }

  /** Reads the selected enum constant from a combo, falling back to the first constant. */
  public static <E extends Enum<E> & IEnumHasCodeAndDescription> E getSelectedEnum(
      Combo combo, Class<E> enumClass) {
    return EnumDialogSupport.readCombo(combo, enumClass, enumClass.getEnumConstants()[0]);
  }
}
