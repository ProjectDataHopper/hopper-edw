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
package org.hopper.edw.datavault.naming;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.naming.metadata.NamingCaseStyle;
import org.apache.hop.naming.metadata.NamingScheme;
import org.apache.hop.naming.metadata.NamingWordSeparator;

/** Creates default EDW naming schemes when the project has none of those names yet. */
public final class EdwNamingSchemeSeedSupport {

  private EdwNamingSchemeSeedSupport() {}

  public static void seedDefaults(IHopMetadataProvider provider) throws HopException {
    if (provider == null) {
      return;
    }
    IHopMetadataSerializer<NamingScheme> serializer = provider.getSerializer(NamingScheme.class);
    seed(
        serializer,
        "edw-dv-hub",
        EdwNamingSchemeTypes.DV_HUB,
        "hub_",
        "Lower-case hub names with hub_ prefix");
    seed(
        serializer,
        "edw-dv-link",
        EdwNamingSchemeTypes.DV_LINK,
        "lnk_",
        "Lower-case link names with lnk_ prefix");
    seed(
        serializer,
        "edw-dv-satellite",
        EdwNamingSchemeTypes.DV_SATELLITE,
        "sat_",
        "Lower-case satellite names with sat_ prefix");
    seed(
        serializer,
        "edw-dv-reference",
        EdwNamingSchemeTypes.DV_REFERENCE,
        "ref_",
        "Lower-case reference names with ref_ prefix");
    seed(
        serializer,
        "edw-dm-dimension",
        EdwNamingSchemeTypes.DM_DIMENSION,
        "dim_",
        "Lower-case dimension names with dim_ prefix");
    seed(
        serializer,
        "edw-dm-fact",
        EdwNamingSchemeTypes.DM_FACT,
        "fact_",
        "Lower-case fact names with fact_ prefix");
    seed(
        serializer,
        "edw-database-column",
        EdwNamingSchemeTypes.DATABASE_COLUMN,
        "",
        "Lower-case database column names");
  }

  private static void seed(
      IHopMetadataSerializer<NamingScheme> serializer,
      String name,
      String type,
      String prefix,
      String description)
      throws HopException {
    if (serializer.exists(name)) {
      return;
    }
    NamingScheme scheme = new NamingScheme(name);
    scheme.setDescription(description);
    scheme.setType(type);
    scheme.setCaseStyle(NamingCaseStyle.LOWER.getCode());
    scheme.setWordSeparator(NamingWordSeparator.UNDERSCORE.getCode());
    scheme.setPrefix(prefix);
    scheme.setSuffix("");
    scheme.setRemoveSpecialCharacters(true);
    scheme.setCollapseRepeatedSeparators(true);
    scheme.setTrimEdgeSeparators(true);
    serializer.save(scheme);
  }
}
