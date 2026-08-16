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
package org.apache.hop.datavault.metadata.jinja;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;

/**
 * Project-level library of Jinja macros and default {@code var()} values for Business Vault SQL
 * (issue #72).
 */
@HopMetadata(
    key = "jinja-macro-library",
    name = "i18n::JinjaMacroLibraryMeta.name",
    description = "i18n::JinjaMacroLibraryMeta.description",
    image = "jinja-macro-library.svg",
    documentationUrl = "/metadata-types/jinja-macro-library.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class JinjaMacroLibraryMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  /** Optional dbt package / project name used as a namespace hint. */
  @HopMetadataProperty private String packageName;

  @HopMetadataProperty private boolean enabled = true;

  @HopMetadataProperty(key = "var", groupKey = "vars")
  private List<JinjaMacroVar> vars = new ArrayList<>();

  @HopMetadataProperty(key = "macro", groupKey = "macros")
  private List<JinjaMacroDefinition> macros = new ArrayList<>();

  public JinjaMacroLibraryMeta() {
    super();
  }

  public JinjaMacroLibraryMeta(String name) {
    super(name);
  }

  public List<JinjaMacroVar> getVars() {
    if (vars == null) {
      vars = new ArrayList<>();
    }
    return vars;
  }

  public List<JinjaMacroDefinition> getMacros() {
    if (macros == null) {
      macros = new ArrayList<>();
    }
    return macros;
  }

  public JinjaMacroDefinition findMacro(String macroName) {
    if (Utils.isEmpty(macroName)) {
      return null;
    }
    for (JinjaMacroDefinition macro : getMacros()) {
      if (macro != null && macroName.equalsIgnoreCase(macro.getName())) {
        return macro;
      }
    }
    return null;
  }
}
