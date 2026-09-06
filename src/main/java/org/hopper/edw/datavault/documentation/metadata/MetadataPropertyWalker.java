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
package org.hopper.edw.datavault.documentation.metadata;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiElements;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Walks {@link HopMetadataProperty} fields and redacts secrets. */
public final class MetadataPropertyWalker {

  public static final String REDACTED = "••••";
  private static final int MAX_DEPTH = 4;

  private MetadataPropertyWalker() {}

  public static List<PropertyRow> walk(Object object) {
    List<PropertyRow> rows = new ArrayList<>();
    walk(object, "", "", 0, rows);
    return rows;
  }

  public static boolean looksSecret(String name) {
    if (name == null) {
      return false;
    }
    String n = name.toLowerCase(Locale.ROOT);
    return n.contains("password")
        || n.contains("secret")
        || n.contains("token")
        || n.contains("apikey")
        || n.contains("api_key")
        || n.endsWith("key") && n.contains("private");
  }

  public static boolean looksEncrypted(String value) {
    return value != null && value.startsWith("Encrypted ");
  }

  private static void walk(
      Object object, String namePrefix, String labelPrefix, int depth, List<PropertyRow> rows) {
    if (object == null || depth > MAX_DEPTH) {
      return;
    }
    Class<?> type = object.getClass();
    if (type.isPrimitive()
        || object instanceof Number
        || object instanceof Boolean
        || object instanceof CharSequence
        || object instanceof Enum<?>
        || object instanceof Date) {
      String name = blankToName(namePrefix);
      rows.add(new PropertyRow(name, firstNonEmpty(labelPrefix, name), "", stringify(object)));
      return;
    }
    for (Field field : allFields(type)) {
      HopMetadataProperty property = field.getAnnotation(HopMetadataProperty.class);
      if (property == null) {
        continue;
      }
      field.setAccessible(true);
      Object value;
      try {
        value = field.get(object);
      } catch (IllegalAccessException e) {
        continue;
      }
      String name = field.getName();
      String technical = namePrefix.isEmpty() ? name : namePrefix + "." + name;
      GuiCaption caption = captionOf(field);
      String leafLabel = firstNonEmpty(caption.label, name);
      String displayLabel = labelPrefix.isEmpty() ? leafLabel : labelPrefix + " / " + leafLabel;
      if (property.password() || looksSecret(name)) {
        rows.add(
            new PropertyRow(
                technical,
                displayLabel,
                caption.toolTip,
                value == null || Utils.isEmpty(String.valueOf(value)) ? "" : REDACTED));
        continue;
      }
      if (value == null) {
        continue;
      }
      if (value instanceof String string) {
        if (looksEncrypted(string)) {
          rows.add(new PropertyRow(technical, displayLabel, caption.toolTip, REDACTED));
        } else if (!string.isBlank()) {
          rows.add(new PropertyRow(technical, displayLabel, caption.toolTip, string));
        }
        continue;
      }
      if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
        rows.add(new PropertyRow(technical, displayLabel, caption.toolTip, stringify(value)));
        continue;
      }
      if (value instanceof Date date) {
        rows.add(new PropertyRow(technical, displayLabel, caption.toolTip, date.toString()));
        continue;
      }
      if (value instanceof Collection<?> collection) {
        int i = 0;
        for (Object item : collection) {
          walk(item, technical + "[" + i + "]", displayLabel + "[" + i + "]", depth + 1, rows);
          i++;
          if (i >= 50) {
            rows.add(
                new PropertyRow(
                    technical,
                    displayLabel,
                    caption.toolTip,
                    "… " + (collection.size() - 50) + " more"));
            break;
          }
        }
        continue;
      }
      if (value instanceof Map<?, ?> map) {
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          String key = stringify(entry.getKey());
          walk(
              entry.getValue(), technical + "." + key, displayLabel + " / " + key, depth + 1, rows);
          i++;
          if (i >= 50) {
            break;
          }
        }
        continue;
      }
      if (value.getClass().getName().startsWith("java.")) {
        rows.add(new PropertyRow(technical, displayLabel, caption.toolTip, stringify(value)));
        continue;
      }
      walk(value, technical, displayLabel, depth + 1, rows);
    }
  }

  private static GuiCaption captionOf(Field field) {
    GuiWidgetElement element = field.getAnnotation(GuiWidgetElement.class);
    if (element == null || element.ignored()) {
      return GuiCaption.EMPTY;
    }
    try {
      GuiElements gui = new GuiElements(element, field);
      return new GuiCaption(usableText(gui.getLabel()), usableText(gui.getToolTip()));
    } catch (Exception e) {
      return new GuiCaption(usableText(element.label()), usableText(element.toolTip()));
    }
  }

  private static String usableText(String text) {
    if (Utils.isEmpty(text) || text.startsWith(Const.I18N_PREFIX)) {
      return "";
    }
    return text;
  }

  private static String firstNonEmpty(String first, String fallback) {
    return Utils.isEmpty(first) ? fallback : first;
  }

  private static List<Field> allFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    Class<?> current = type;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        fields.add(field);
      }
      current = current.getSuperclass();
    }
    return fields;
  }

  private static String stringify(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static String blankToName(String prefix) {
    return Utils.isEmpty(prefix) ? "value" : prefix;
  }

  public record PropertyRow(String name, String label, String toolTip, String value) {
    public PropertyRow(String name, String value) {
      this(name, name, "", value);
    }
  }

  private record GuiCaption(String label, String toolTip) {
    static final GuiCaption EMPTY = new GuiCaption("", "");
  }
}
