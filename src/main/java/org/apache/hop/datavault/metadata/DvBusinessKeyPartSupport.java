/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Shared helpers for multipartite vs composite (single-column) hub business keys.
 *
 * <p>Vault column identity remains {@link DvHub#getDistinctBusinessKeys()}. Composite keys expand
 * to multiple <em>source parts</em> for hashing and hub storage composition. Satellites and links
 * use part expansion only to compute hash keys; they do not store composed BK columns.
 */
public final class DvBusinessKeyPartSupport {

  private DvBusinessKeyPartSupport() {}

  /**
   * Descriptor of one hub vault business-key column and how many hash/source parts it contributes.
   *
   * @param vaultFieldName physical / logical hub BK column name
   * @param composite true when N parts compose into one vault column
   * @param partCount number of hash input parts (1 for non-composite; N for composite)
   * @param definition first-seen {@link BusinessKey} metadata for this name (type, composite flag)
   */
  public record VaultBusinessKey(
      String vaultFieldName, boolean composite, int partCount, BusinessKey definition) {}

  /** One hash-input part, optionally tied to a vault BK name. */
  public record HashInputPart(String vaultFieldName, String streamFieldName, int partIndex) {}

  /**
   * Vault business keys in hub order (one entry per distinct name), with composite part counts from
   * the first-seen definition (or max part count seen across multi-source rows when that is
   * larger).
   */
  public static List<VaultBusinessKey> resolveVaultBusinessKeys(DvHub hub) {
    List<VaultBusinessKey> result = new ArrayList<>();
    if (hub == null || hub.getBusinessKeys() == null) {
      return result;
    }

    Map<String, Integer> maxPartsByName = new LinkedHashMap<>();
    Map<String, BusinessKey> firstByName = new LinkedHashMap<>();
    for (BusinessKey bk : hub.getBusinessKeys()) {
      if (bk == null || Utils.isEmpty(bk.getName())) {
        continue;
      }
      firstByName.putIfAbsent(bk.getName(), bk);
      // Non-composite: one vault column / one hash part. Composite: N source parts (min 1 if
      // unmapped — validation reports missing parts separately).
      int parts = !bk.isComposite() ? 1 : Math.max(bk.sourcePartCount(), 1);
      maxPartsByName.merge(bk.getName(), parts, Math::max);
    }

    for (Map.Entry<String, BusinessKey> entry : firstByName.entrySet()) {
      BusinessKey def = entry.getValue();
      boolean composite = def.isComposite();
      int partCount = maxPartsByName.getOrDefault(entry.getKey(), 1);
      if (!composite) {
        partCount = 1;
      }
      result.add(new VaultBusinessKey(entry.getKey(), composite, partCount, def));
    }
    return result;
  }

  /** Total number of hash-input parts across all vault BKs on the hub. */
  public static int totalHashInputPartCount(DvHub hub) {
    int total = 0;
    for (VaultBusinessKey vaultKey : resolveVaultBusinessKeys(hub)) {
      total += vaultKey.partCount();
    }
    return total;
  }

  /**
   * Ordered source field names for a hub vault BK on a given record source (composite parts or
   * single field).
   */
  public static List<String> resolveSourcePartsForHubSource(
      DvHub hub, String vaultFieldName, String recordSourceName, IVariables variables) {
    if (hub == null || Utils.isEmpty(vaultFieldName)) {
      return List.of();
    }
    List<BusinessKey> forSource = hub.getBusinessKeysForSource(recordSourceName, variables);
    for (BusinessKey bk : forSource) {
      if (bk == null || Utils.isEmpty(bk.getName())) {
        continue;
      }
      String name = resolve(variables, bk.getName());
      String want = resolve(variables, vaultFieldName);
      if (Objects.equals(name, want) || vaultFieldName.equals(bk.getName())) {
        List<String> parts = bk.resolveSourceParts();
        if (!parts.isEmpty()) {
          return resolveList(variables, parts);
        }
      }
    }
    // Fall back to first distinct definition
    for (BusinessKey bk : hub.getDistinctBusinessKeys()) {
      if (bk != null && vaultFieldName.equals(bk.getName())) {
        return resolveList(variables, bk.resolveSourceParts());
      }
    }
    return List.of();
  }

  /**
   * Ordered source parts for every vault BK on a hub record source (for SQL select / file mapping).
   * Composite BKs contribute N part field names; multipartite contribute one each.
   */
  public static List<String> resolveAllSourcePartsForHubSource(
      DvHub hub, String recordSourceName, IVariables variables) {
    List<String> all = new ArrayList<>();
    if (hub == null) {
      return all;
    }
    // Preserve vault BK order from distinct names; for each, take mapping for this source
    for (VaultBusinessKey vaultKey : resolveVaultBusinessKeys(hub)) {
      List<String> parts =
          resolveSourcePartsForHubSource(
              hub, vaultKey.vaultFieldName(), recordSourceName, variables);
      if (parts.isEmpty() && !vaultKey.composite()) {
        // Legacy fallback: stream/source name equals vault name
        all.add(vaultKey.vaultFieldName());
      } else {
        all.addAll(parts);
      }
    }
    return all;
  }

  /**
   * File/source column mapping for a hub feed: multipartite renames source field → vault name;
   * composite keeps part field names on the stream (compose to vault name happens in hub pipeline).
   */
  public static void putHubSourceColumnMappings(
      Map<String, String> sourceToTarget,
      DvHub hub,
      String recordSourceName,
      IVariables variables) {
    if (sourceToTarget == null || hub == null) {
      return;
    }
    for (BusinessKey key : hub.getBusinessKeysForSource(recordSourceName, variables)) {
      if (key == null) {
        continue;
      }
      List<String> parts = key.resolveSourceParts();
      if (parts.isEmpty()) {
        continue;
      }
      if (key.isComposite()) {
        for (String part : parts) {
          String resolved = resolve(variables, part);
          if (!Utils.isEmpty(resolved)) {
            sourceToTarget.put(resolved, resolved);
          }
        }
      } else {
        String source = resolve(variables, parts.get(0));
        String target = resolve(variables, key.getName());
        if (!Utils.isEmpty(source) && !Utils.isEmpty(target)) {
          sourceToTarget.put(source, target);
        }
      }
    }
  }

  /**
   * Stream field names used for sort/unique on hub source before compose: composite → part names;
   * multipartite → vault names.
   */
  public static List<String> resolveHubSourceIdentityStreamFields(
      DvHub hub, String recordSourceName, IVariables variables) {
    List<String> fields = new ArrayList<>();
    if (hub == null) {
      return fields;
    }
    for (VaultBusinessKey vaultKey : resolveVaultBusinessKeys(hub)) {
      if (vaultKey.composite()) {
        List<String> parts =
            resolveSourcePartsForHubSource(
                hub, vaultKey.vaultFieldName(), recordSourceName, variables);
        if (parts.isEmpty()) {
          fields.add(resolve(variables, vaultKey.vaultFieldName()));
        } else {
          fields.addAll(parts);
        }
      } else {
        fields.add(resolve(variables, vaultKey.vaultFieldName()));
      }
    }
    return fields;
  }

  /**
   * Default stream field names used as hash inputs after hub source mapping (non-composed mode):
   * multipartite → vault names; composite → source part names from the given source mapping.
   */
  public static List<String> resolveHashInputStreamFieldNames(
      DvHub hub, String recordSourceName, DataVaultConfiguration config, IVariables variables) {
    boolean hashComposed = config != null && config.isHashUsesComposedBusinessKey();
    List<String> fields = new ArrayList<>();
    for (VaultBusinessKey vaultKey : resolveVaultBusinessKeys(hub)) {
      if (hashComposed || !vaultKey.composite()) {
        fields.add(resolve(variables, vaultKey.vaultFieldName()));
      } else {
        List<String> parts =
            resolveSourcePartsForHubSource(
                hub, vaultKey.vaultFieldName(), recordSourceName, variables);
        if (parts.isEmpty()) {
          // Degenerate composite: treat vault name as single hash input
          fields.add(resolve(variables, vaultKey.vaultFieldName()));
        } else {
          fields.addAll(parts);
        }
      }
    }
    return fields;
  }

  /** Whether any distinct hub business key is marked composite (single vault column from parts). */
  public static boolean hubHasCompositeBusinessKey(DvHub hub) {
    if (hub == null || hub.getBusinessKeys() == null) {
      return false;
    }
    for (BusinessKey bk : hub.getDistinctBusinessKeys()) {
      if (bk != null && bk.isComposite()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Multi-source consistency: for each vault BK name that is composite, all source-scoped mappings
   * must report the same part count. Returns empty list when consistent; otherwise messages.
   */
  public static List<String> findCompositePartCountMismatches(DvHub hub, IVariables variables) {
    List<String> errors = new ArrayList<>();
    if (hub == null || hub.getBusinessKeys() == null) {
      return errors;
    }
    Map<String, Integer> expectedParts = new LinkedHashMap<>();
    Map<String, String> firstSource = new LinkedHashMap<>();
    for (BusinessKey bk : hub.getBusinessKeys()) {
      if (bk == null || Utils.isEmpty(bk.getName()) || !bk.isComposite()) {
        continue;
      }
      int count = bk.sourcePartCount();
      Integer expected = expectedParts.get(bk.getName());
      if (expected == null) {
        expectedParts.put(bk.getName(), count);
        firstSource.put(bk.getName(), bk.getRecordSourceName());
      } else if (expected != count) {
        errors.add(
            "Composite business key '"
                + bk.getName()
                + "' has "
                + count
                + " source parts for source '"
                + nullToEmpty(bk.getRecordSourceName())
                + "' but "
                + expected
                + " for source '"
                + nullToEmpty(firstSource.get(bk.getName()))
                + "'");
      }
    }
    return errors;
  }

  /**
   * Compose the <em>stored</em> hub business-key string from ordered part values.
   *
   * <p>Uses delimiter, trim, and null placeholder from configuration. Does <b>not</b> apply hash
   * content casing, prefix, or suffix (those remain hash-only).
   *
   * @param partValues ordered part strings (nulls allowed)
   * @param config model configuration (delimiter / trim / null placeholder)
   * @param variables optional variable space for resolving config strings
   * @return composed storage value, or {@code null} when no part contributed content
   */
  public static String composeStoredBusinessKey(
      List<String> partValues, DataVaultConfiguration config, IVariables variables) {
    if (partValues == null || partValues.isEmpty()) {
      return null;
    }
    String delimiter = resolve(variables, config != null ? config.getBusinessKeyDelimiter() : null);
    if (delimiter == null) {
      delimiter = "||";
    }
    String nullPlaceholder =
        resolve(variables, config != null ? config.getNullPlaceholder() : null);
    boolean trim = config == null || config.isTrimBusinessKeys();

    StringBuilder builder = new StringBuilder();
    boolean valueAdded = false;
    for (String raw : partValues) {
      String part = raw;
      if (part == null || part.isEmpty()) {
        if (Utils.isEmpty(nullPlaceholder)) {
          continue;
        }
        part = nullPlaceholder;
      }
      if (trim) {
        part = part.trim();
      }
      if (Utils.isEmpty(part)) {
        continue;
      }
      if (valueAdded && !Utils.isEmpty(delimiter)) {
        builder.append(delimiter);
      }
      builder.append(part);
      valueAdded = true;
    }
    return valueAdded ? builder.toString() : null;
  }

  private static List<String> resolveList(IVariables variables, List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    List<String> resolved = new ArrayList<>(values.size());
    for (String value : values) {
      resolved.add(resolve(variables, value));
    }
    return resolved;
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String nullToEmpty(String value) {
    return value != null ? value : "";
  }
}
