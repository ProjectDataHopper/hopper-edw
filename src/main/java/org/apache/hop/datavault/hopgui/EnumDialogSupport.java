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
package org.apache.hop.datavault.hopgui;

import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;
import org.eclipse.swt.widgets.Combo;

/** Binds {@link IEnumHasCodeAndDescription} values to SWT combo widgets. */
public final class EnumDialogSupport {

  private EnumDialogSupport() {}

  public static <E extends Enum<E> & IEnumHasCodeAndDescription> String[] comboOptions(
      Class<E> enumClass) {
    return IEnumHasCodeAndDescription.getDescriptions(enumClass);
  }

  public static <E extends Enum<E> & IEnumHasCodeAndDescription> void populateCombo(
      Combo combo, Class<E> enumClass) {
    if (combo == null || combo.isDisposed()) {
      return;
    }
    combo.setItems(comboOptions(enumClass));
  }

  public static <E extends Enum<E> & IEnumHasCodeAndDescription> void selectCombo(
      Combo combo, E value) {
    if (combo == null || combo.isDisposed() || value == null) {
      return;
    }
    int index = combo.indexOf(value.getDescription());
    if (index >= 0) {
      combo.select(index);
    }
  }

  public static <E extends Enum<E> & IEnumHasCodeAndDescription> E readCombo(
      Combo combo, Class<E> enumClass, E defaultValue) {
    if (combo == null || combo.isDisposed()) {
      return defaultValue;
    }
    return lookupText(combo.getText(), enumClass, defaultValue);
  }

  public static <E extends Enum<E> & IEnumHasCodeAndDescription> String descriptionOf(E value) {
    return value == null ? "" : value.getDescription();
  }

  public static <E extends Enum<E> & IEnumHasCodeAndDescription> E lookupText(
      String text, Class<E> enumClass, E defaultValue) {
    if (Utils.isEmpty(text)) {
      return defaultValue;
    }
    E byDescription = IEnumHasCodeAndDescription.lookupDescription(enumClass, text, null);
    if (byDescription != null) {
      return byDescription;
    }
    return IEnumHasCode.lookupCode(enumClass, text, defaultValue);
  }
}
