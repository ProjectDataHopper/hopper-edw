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
package org.hopper.edw.datavault.jinja;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.jinja.JinjaMacroDefinition;
import org.hopper.edw.datavault.metadata.jinja.JinjaMacroLibraryMeta;
import org.hopper.edw.datavault.metadata.jinja.JinjaMacroVar;

/** Loads enabled {@link JinjaMacroLibraryMeta} objects for a Business Vault render. */
public final class JinjaMacroLibraryLoader {

  private JinjaMacroLibraryLoader() {}

  public record LoadedLibraries(List<JinjaMacroDefinition> macros, Map<String, String> vars) {}

  public static LoadedLibraries load(
      IHopMetadataProvider metadataProvider, BusinessVaultModel bvModel) {
    List<JinjaMacroDefinition> macros = new ArrayList<>();
    Map<String, String> vars = new LinkedHashMap<>();
    if (metadataProvider == null) {
      return new LoadedLibraries(macros, vars);
    }
    try {
      IHopMetadataSerializer<JinjaMacroLibraryMeta> serializer =
          metadataProvider.getSerializer(JinjaMacroLibraryMeta.class);
      if (serializer == null) {
        return new LoadedLibraries(macros, vars);
      }
      List<String> names = serializer.listObjectNames();
      if (names == null) {
        return new LoadedLibraries(macros, vars);
      }
      List<String> scoped = scopedLibraryNames(bvModel);
      for (String name : names) {
        if (Utils.isEmpty(name)) {
          continue;
        }
        if (!scoped.isEmpty() && !containsIgnoreCase(scoped, name)) {
          continue;
        }
        JinjaMacroLibraryMeta library = serializer.load(name);
        if (library == null || !library.isEnabled()) {
          continue;
        }
        for (JinjaMacroVar var : library.getVars()) {
          if (var == null || Utils.isEmpty(var.getName())) {
            continue;
          }
          vars.putIfAbsent(var.getName(), emptyIfNull(var.getValue()));
        }
        for (JinjaMacroDefinition macro : library.getMacros()) {
          if (macro != null && !Utils.isEmpty(macro.getJinjaSource())) {
            macros.add(macro);
          }
        }
      }
    } catch (Exception ignored) {
      // Missing serializer or load failure: render without project macros.
    }
    return new LoadedLibraries(macros, BvSqlJinjaRenderSession.caseInsensitiveVars(vars));
  }

  private static String emptyIfNull(String value) {
    return value != null ? value : "";
  }

  private static List<String> scopedLibraryNames(BusinessVaultModel bvModel) {
    if (bvModel == null) {
      return List.of();
    }
    BusinessVaultConfiguration config = bvModel.getConfigurationOrDefault();
    if (config == null || config.getJinjaMacroLibraryNames() == null) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (String name : config.getJinjaMacroLibraryNames()) {
      if (!Utils.isEmpty(name)) {
        names.add(name.trim());
      }
    }
    return names;
  }

  private static boolean containsIgnoreCase(List<String> names, String candidate) {
    for (String name : names) {
      if (name != null && name.equalsIgnoreCase(candidate)) {
        return true;
      }
    }
    return candidate != null && names.contains(candidate.toLowerCase(Locale.ROOT));
  }
}
