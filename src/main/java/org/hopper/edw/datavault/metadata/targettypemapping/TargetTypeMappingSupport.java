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
package org.hopper.edw.datavault.metadata.targettypemapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;

/** Load / auto-match a {@link TargetTypeMappingMeta} for a DDL generation pass. */
public final class TargetTypeMappingSupport {

  private static final Class<?> PKG = TargetTypeMappingMeta.class;

  /**
   * Optional variable set by update actions so {@code generateUpdateDdl} picks up an explicit
   * mapping name without extra method arguments on every table type.
   */
  public static final String VARIABLE_NAME = "DATAVAULT_TARGET_TYPE_MAPPING";

  private TargetTypeMappingSupport() {}

  /**
   * Copy a resolved explicit mapping name onto {@link #VARIABLE_NAME} when non-empty. Does not
   * clear an existing variable when the explicit name is blank.
   */
  public static void applyExplicitName(IVariables variables, String explicitName) {
    if (variables == null || Utils.isEmpty(explicitName)) {
      return;
    }
    String resolved = variables.resolve(explicitName);
    if (!Utils.isEmpty(resolved)) {
      variables.setVariable(VARIABLE_NAME, resolved);
    }
  }

  /**
   * Resolve the mapping to apply.
   *
   * <ol>
   *   <li>Explicit name (argument, then {@link #VARIABLE_NAME}) — missing metadata is an error.
   *   <li>Unique auto-match on {@code targetDatabase} vs the actual target connection name.
   *   <li>Otherwise an empty context (Hop dialect defaults).
   * </ol>
   */
  public static TargetTypeMappingContext resolve(
      String explicitName,
      DatabaseMeta targetDatabaseMeta,
      IHopMetadataProvider metadataProvider,
      IVariables variables)
      throws HopException {
    String name = firstNonEmpty(resolveName(explicitName, variables), variableName(variables));
    if (!Utils.isEmpty(name)) {
      TargetTypeMappingMeta mapping = loadRequired(name, metadataProvider);
      return new TargetTypeMappingContext(mapping, variables);
    }
    TargetTypeMappingMeta auto = autoMatch(targetDatabaseMeta, metadataProvider);
    return new TargetTypeMappingContext(auto, variables);
  }

  public static TargetTypeMappingMeta load(String name, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(name) || metadataProvider == null) {
      return null;
    }
    IHopMetadataSerializer<TargetTypeMappingMeta> serializer =
        metadataProvider.getSerializer(TargetTypeMappingMeta.class);
    if (serializer == null) {
      return null;
    }
    return serializer.load(name);
  }

  public static TargetTypeMappingMeta loadRequired(
      String name, IHopMetadataProvider metadataProvider) throws HopException {
    TargetTypeMappingMeta mapping = load(name, metadataProvider);
    if (mapping == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "TargetTypeMapping.Error.MappingNotFound", name));
    }
    return mapping;
  }

  /**
   * When exactly one mapping declares {@code targetDatabase} equal to the target connection name,
   * return it. Zero or several matches → {@code null}.
   */
  public static TargetTypeMappingMeta autoMatch(
      DatabaseMeta targetDatabaseMeta, IHopMetadataProvider metadataProvider) throws HopException {
    if (targetDatabaseMeta == null
        || Utils.isEmpty(targetDatabaseMeta.getName())
        || metadataProvider == null) {
      return null;
    }
    IHopMetadataSerializer<TargetTypeMappingMeta> serializer;
    try {
      serializer = metadataProvider.getSerializer(TargetTypeMappingMeta.class);
    } catch (Exception e) {
      return null;
    }
    if (serializer == null) {
      return null;
    }
    List<String> names;
    try {
      names = serializer.listObjectNames();
    } catch (Exception e) {
      return null;
    }
    if (names == null || names.isEmpty()) {
      return null;
    }
    String wanted = targetDatabaseMeta.getName().trim().toLowerCase(Locale.ROOT);
    List<TargetTypeMappingMeta> matches = new ArrayList<>();
    for (String mappingName : names) {
      if (Utils.isEmpty(mappingName)) {
        continue;
      }
      TargetTypeMappingMeta mapping = serializer.load(mappingName);
      if (mapping == null || Utils.isEmpty(mapping.getTargetDatabase())) {
        continue;
      }
      if (wanted.equals(mapping.getTargetDatabase().trim().toLowerCase(Locale.ROOT))) {
        matches.add(mapping);
      }
    }
    if (matches.size() == 1) {
      return matches.get(0);
    }
    return null;
  }

  public static List<TargetTypeMappingMeta> listAll(IHopMetadataProvider metadataProvider)
      throws HopException {
    List<TargetTypeMappingMeta> result = new ArrayList<>();
    if (metadataProvider == null) {
      return result;
    }
    IHopMetadataSerializer<TargetTypeMappingMeta> serializer =
        metadataProvider.getSerializer(TargetTypeMappingMeta.class);
    if (serializer == null) {
      return result;
    }
    for (String name : serializer.listObjectNames()) {
      TargetTypeMappingMeta mapping = serializer.load(name);
      if (mapping != null) {
        result.add(mapping);
      }
    }
    return result;
  }

  private static String resolveName(String explicitName, IVariables variables) {
    if (Utils.isEmpty(explicitName)) {
      return "";
    }
    return variables != null ? Const.NVL(variables.resolve(explicitName), "") : explicitName;
  }

  private static String variableName(IVariables variables) {
    if (variables == null) {
      return "";
    }
    return Const.NVL(variables.getVariable(VARIABLE_NAME), "");
  }

  private static String firstNonEmpty(String... values) {
    if (values == null) {
      return "";
    }
    for (String value : values) {
      if (!Utils.isEmpty(value)) {
        return value.trim();
      }
    }
    return "";
  }
}
