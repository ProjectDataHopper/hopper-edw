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
package org.apache.hop.datavault.metadata.sourcemodel.generate;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.sourcemodel.SourceJsonField;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;

/**
 * Discovers JsonPaths and proposes Hop types from sample JSON document strings (Issue #114).
 *
 * <p>Path discovery follows the same streaming approach as Hop {@code JsonInputDialog}. Type
 * proposals include epoch millis/seconds → Date conversion hints.
 */
public final class SourceJsonSampleSupport {

  public static final int DEFAULT_SAMPLE_DOCUMENTS = 20;

  private static final Pattern ISO_DATE =
      Pattern.compile(
          "\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:?\\d{2})?)?");

  private SourceJsonSampleSupport() {}

  /**
   * Discovers unique JsonPaths from one or more JSON document strings.
   *
   * @param jsonDocuments sample JSON payloads (null/blank skipped)
   */
  public static Set<String> discoverPaths(Iterable<String> jsonDocuments) throws Exception {
    Set<String> paths = new LinkedHashSet<>();
    if (jsonDocuments == null) {
      return paths;
    }
    for (String document : jsonDocuments) {
      if (Utils.isEmpty(document)) {
        continue;
      }
      paths.addAll(extractPaths(document));
    }
    return paths;
  }

  /**
   * Proposes {@link SourceJsonField} entries from sample documents.
   *
   * @param arrayFocusPath optional array base path (e.g. {@code $.lines[*]}); when set, only paths
   *     under that array (and non-array top-level paths) are proposed as expandable fields. Other
   *     array bases are offered as JSON-string fields for chaining.
   */
  public static List<SourceJsonField> proposeFields(
      Iterable<String> jsonDocuments, String arrayFocusPath) throws Exception {
    List<String> docs = new ArrayList<>();
    if (jsonDocuments != null) {
      for (String d : jsonDocuments) {
        if (!Utils.isEmpty(d)) {
          docs.add(d);
        }
      }
    }
    Set<String> allPaths = discoverPaths(docs);
    Map<String, TypeStats> stats = collectTypeStats(docs, allPaths);

    Set<String> arrayBases = new LinkedHashSet<>();
    for (String path : allPaths) {
      if (path != null && isArrayPath(path)) {
        arrayBases.add(SourceModel.arrayBasePath(path));
      }
    }

    String focus =
        !Utils.isEmpty(arrayFocusPath)
            ? SourceModel.arrayBasePath(arrayFocusPath)
            : (arrayBases.size() == 1 ? arrayBases.iterator().next() : null);

    List<String> selectedPaths = new ArrayList<>();
    List<String> chainAsJson = new ArrayList<>();
    for (String path : allPaths) {
      if (path == null) {
        continue;
      }
      // Skip bare array nodes; prefer leaf fields under them.
      if (path.endsWith("[*]") || path.endsWith(".*")) {
        continue;
      }
      if (!isArrayPath(path)) {
        selectedPaths.add(path);
        continue;
      }
      String base = SourceModel.arrayBasePath(path);
      if (focus == null || focus.equals(base)) {
        selectedPaths.add(path);
      } else if (!chainAsJson.contains(base)) {
        chainAsJson.add(base);
      }
    }
    Collections.sort(selectedPaths);

    List<SourceJsonField> fields = new ArrayList<>();
    Set<String> usedNames = new HashSet<>();
    for (String path : selectedPaths) {
      SourceJsonField field = new SourceJsonField();
      field.setPath(path);
      field.setName(uniqueName(leafName(path), usedNames));
      TypeStats typeStats = stats.get(path);
      applyTypeProposal(field, typeStats);
      fields.add(field);
    }
    for (String base : chainAsJson) {
      SourceJsonField field = new SourceJsonField();
      field.setPath(base);
      field.setName(uniqueName(leafName(base) + "_json", usedNames));
      field.setHopType(IValueMeta.TYPE_STRING);
      fields.add(field);
    }
    return fields;
  }

  /** Lists distinct array base paths found in the sample documents. */
  public static List<String> discoverArrayBases(Iterable<String> jsonDocuments) throws Exception {
    Set<String> bases = new LinkedHashSet<>();
    for (String path : discoverPaths(jsonDocuments)) {
      if (path != null && isArrayPath(path)) {
        bases.add(SourceModel.arrayBasePath(path));
      }
    }
    List<String> list = new ArrayList<>(bases);
    Collections.sort(list);
    return list;
  }

  private static boolean isArrayPath(String path) {
    return path != null && (path.contains("[*]") || path.contains(".*"));
  }

  static Set<String> extractPaths(String json) throws Exception {
    Set<String> paths = new HashSet<>();
    LinkedList<String> currentPath = new LinkedList<>();
    JsonFactory jsonFactory = new MappingJsonFactory();
    try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        JsonParser parser = jsonFactory.createParser(in)) {
      while (parser.nextToken() != null) {
        JsonToken jsonToken = parser.currentToken();
        String name = parser.currentName();
        switch (jsonToken) {
          case START_OBJECT -> currentPath.push(name);
          case END_OBJECT -> {
            if (!currentPath.isEmpty()) {
              currentPath.pop();
            }
          }
          case START_ARRAY -> {
            currentPath.push(name);
            currentPath.push("*");
          }
          case END_ARRAY -> {
            if (!currentPath.isEmpty()) {
              currentPath.pop();
            }
            if (!currentPath.isEmpty()) {
              currentPath.pop();
            }
          }
          case FIELD_NAME -> {
            currentPath.push(name != null && name.contains(" ") ? "['" + name + "']" : name);
            addToPaths(paths, currentPath);
            currentPath.pop();
          }
          default -> {
            // ignore value tokens
          }
        }
        if (currentPath.size() > 100) {
          throw new IllegalStateException("Path too long while sampling JSON");
        }
      }
    }
    return paths;
  }

  private static void addToPaths(Set<String> paths, LinkedList<String> currentPath) {
    StringBuilder path = new StringBuilder("$");
    Iterator<String> iterator = currentPath.descendingIterator();
    while (iterator.hasNext()) {
      String string = iterator.next();
      if (string != null) {
        if (string.contains(".")) {
          if (!path.toString().endsWith(".")) {
            path.append(".");
          }
          path.append("['").append(string).append("']");
        } else {
          path.append(".").append(string);
        }
      }
    }
    paths.add(path.toString());
  }

  private static Map<String, TypeStats> collectTypeStats(List<String> docs, Set<String> paths) {
    Map<String, TypeStats> stats = new HashMap<>();
    for (String path : paths) {
      stats.put(path, new TypeStats());
    }
    // Lightweight inference: parse each document as a tree once via Jackson streaming values
    // sampled from raw text heuristics per path is expensive; use simple leaf value scanning
    // by re-parsing with a path-aware walk.
    for (String doc : docs) {
      try {
        walkValues(doc, stats);
      } catch (Exception ignored) {
        // skip bad documents
      }
    }
    return stats;
  }

  private static void walkValues(String json, Map<String, TypeStats> stats) throws Exception {
    LinkedList<String> currentPath = new LinkedList<>();
    JsonFactory jsonFactory = new MappingJsonFactory();
    try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        JsonParser parser = jsonFactory.createParser(in)) {
      while (parser.nextToken() != null) {
        JsonToken token = parser.currentToken();
        String name = parser.currentName();
        switch (token) {
          case START_OBJECT -> currentPath.push(name);
          case END_OBJECT -> {
            if (!currentPath.isEmpty()) {
              currentPath.pop();
            }
          }
          case START_ARRAY -> {
            currentPath.push(name);
            currentPath.push("*");
          }
          case END_ARRAY -> {
            if (!currentPath.isEmpty()) {
              currentPath.pop();
            }
            if (!currentPath.isEmpty()) {
              currentPath.pop();
            }
          }
          case VALUE_STRING,
              VALUE_NUMBER_INT,
              VALUE_NUMBER_FLOAT,
              VALUE_TRUE,
              VALUE_FALSE,
              VALUE_NULL -> {
            String path = pathString(currentPath, name);
            TypeStats typeStats = stats.get(path);
            if (typeStats != null) {
              typeStats.observe(token, parser);
            }
          }
          default -> {
            // ignore
          }
        }
      }
    }
  }

  private static String pathString(LinkedList<String> currentPath, String fieldName) {
    LinkedList<String> copy = new LinkedList<>(currentPath);
    if (fieldName != null) {
      copy.push(fieldName.contains(" ") ? "['" + fieldName + "']" : fieldName);
    }
    StringBuilder path = new StringBuilder("$");
    Iterator<String> iterator = copy.descendingIterator();
    while (iterator.hasNext()) {
      String string = iterator.next();
      if (string != null) {
        if (string.contains(".")) {
          if (!path.toString().endsWith(".")) {
            path.append(".");
          }
          path.append("['").append(string).append("']");
        } else {
          path.append(".").append(string);
        }
      }
    }
    return path.toString();
  }

  private static void applyTypeProposal(SourceJsonField field, TypeStats stats) {
    if (stats == null || stats.samples == 0) {
      field.setHopType(IValueMeta.TYPE_STRING);
      return;
    }
    if (stats.booleans > 0 && stats.booleans == stats.samples) {
      field.setHopType(IValueMeta.TYPE_BOOLEAN);
      return;
    }
    if (stats.numbers > 0 && stats.numbers == stats.samples) {
      if (stats.floats > 0) {
        field.setHopType(IValueMeta.TYPE_NUMBER);
      } else if (stats.looksLikeEpochMillis) {
        field.setHopType(IValueMeta.TYPE_DATE);
        // JsonInput date conversion from epoch typically needs a custom format; leave format
        // empty and document that Select Values / Calculator can convert if needed.
      } else if (stats.looksLikeEpochSeconds) {
        field.setHopType(IValueMeta.TYPE_DATE);
      } else {
        field.setHopType(IValueMeta.TYPE_INTEGER);
      }
      return;
    }
    if (stats.isoDates > 0 && stats.isoDates == stats.strings) {
      field.setHopType(IValueMeta.TYPE_TIMESTAMP);
      field.setFormat("yyyy-MM-dd'T'HH:mm:ss");
      return;
    }
    field.setHopType(IValueMeta.TYPE_STRING);
  }

  private static String leafName(String path) {
    if (Utils.isEmpty(path)) {
      return "field";
    }
    String cleaned = path.replace("[*]", "").replace("['", "").replace("']", "");
    int lastDot = cleaned.lastIndexOf('.');
    String leaf = lastDot >= 0 ? cleaned.substring(lastDot + 1) : cleaned;
    leaf = leaf.replace("$", "").replace("[", "").replace("]", "");
    return Utils.isEmpty(leaf) ? "field" : leaf;
  }

  private static String uniqueName(String base, Set<String> used) {
    String name = base;
    int i = 2;
    while (!used.add(name)) {
      name = base + i;
      i++;
    }
    return name;
  }

  private static final class TypeStats {
    int samples;
    int strings;
    int numbers;
    int floats;
    int booleans;
    int isoDates;
    boolean looksLikeEpochMillis;
    boolean looksLikeEpochSeconds;
    final List<String> stringSamples = new ArrayList<>();

    void observe(JsonToken token, JsonParser parser) throws Exception {
      samples++;
      switch (token) {
        case VALUE_TRUE, VALUE_FALSE -> booleans++;
        case VALUE_NUMBER_INT -> {
          numbers++;
          long v = parser.getLongValue();
          // Heuristic: 13-digit epoch millis ~ year 2001–2286; 10-digit seconds ~ 2001–2286.
          if (v >= 1_000_000_000_000L && v < 10_000_000_000_000L) {
            looksLikeEpochMillis = true;
          } else if (v >= 1_000_000_000L && v < 10_000_000_000L) {
            looksLikeEpochSeconds = true;
          }
        }
        case VALUE_NUMBER_FLOAT -> {
          numbers++;
          floats++;
        }
        case VALUE_STRING -> {
          strings++;
          String s = parser.getText();
          if (stringSamples.size() < 5) {
            stringSamples.add(s);
          }
          if (s != null && ISO_DATE.matcher(s).matches()) {
            isoDates++;
          }
        }
        default -> {
          // null etc.
        }
      }
    }
  }
}
