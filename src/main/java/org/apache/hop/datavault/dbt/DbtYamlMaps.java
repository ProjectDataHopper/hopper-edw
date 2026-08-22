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
package org.apache.hop.datavault.dbt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;

/** Lenient accessors over Jackson-parsed YAML maps. */
public final class DbtYamlMaps {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private DbtYamlMaps() {}

  public static Map<String, Object> parseYaml(String text, String origin) throws HopException {
    if (Utils.isEmpty(text)) {
      return Map.of();
    }
    try {
      Map<String, Object> map = YAML.readValue(text, new TypeReference<Map<String, Object>>() {});
      return map != null ? map : Map.of();
    } catch (Exception e) {
      throw new HopException("Unable to parse YAML: " + origin, e);
    }
  }

  public static Map<String, Object> asMap(Object value) {
    if (!(value instanceof Map<?, ?> raw)) {
      return Map.of();
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      if (entry.getKey() != null) {
        out.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    }
    return out;
  }

  public static List<Object> asList(Object value) {
    if (!(value instanceof List<?> raw)) {
      return List.of();
    }
    return new ArrayList<>(raw);
  }

  public static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  public static String childString(Map<String, Object> map, String key) {
    if (map == null) {
      return null;
    }
    return asString(map.get(key));
  }

  public static Map<String, Object> childMap(Map<String, Object> map, String key) {
    if (map == null) {
      return Map.of();
    }
    return asMap(map.get(key));
  }

  public static List<String> stringList(Object value) {
    List<String> out = new ArrayList<>();
    if (value instanceof String single && !Utils.isEmpty(single)) {
      out.add(single);
      return out;
    }
    for (Object item : asList(value)) {
      String text = asString(item);
      if (!Utils.isEmpty(text)) {
        out.add(text);
      }
    }
    return out;
  }
}
