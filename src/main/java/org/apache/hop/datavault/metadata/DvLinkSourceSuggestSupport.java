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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.util.Utils;

/**
 * Suggests link hub-source business-key mappings from a catalog feed's field names to participating
 * hubs' business keys (name matching).
 *
 * <p>Typical case: flat pipeline source {@code asn-package-lines} with {@code order_id}, {@code
 * product_id}, … and hubs already defined with those BK names — identity maps that users otherwise
 * enter by hand in the link dialog.
 */
public final class DvLinkSourceSuggestSupport {

  public enum MatchKind {
    EXACT,
    ALIAS,
    MISSING
  }

  /**
   * One proposed BK mapping for review UI.
   *
   * @param hubName participating hub
   * @param businessKeyField hub vault BK field name
   * @param sourceFieldName proposed feed column (null when missing)
   * @param matchKind how the match was found
   */
  public record ProposedMapping(
      String hubName, String businessKeyField, String sourceFieldName, MatchKind matchKind) {}

  /**
   * Result of suggesting maps for one catalog source against a set of hubs.
   *
   * @param sourceName catalog DV source name
   * @param proposedHubSource draft {@link DvLink.DvLinkHubSource} (only EXACT/ALIAS maps filled)
   * @param mappings all proposals including MISSING for review
   * @param suggestedHubNames hubs that are fully coverable by the feed (for optional hub pick list)
   */
  public record SuggestResult(
      String sourceName,
      DvLink.DvLinkHubSource proposedHubSource,
      List<ProposedMapping> mappings,
      List<String> suggestedHubNames) {

    public int mappedCount() {
      int n = 0;
      for (ProposedMapping m : mappings) {
        if (m != null && m.matchKind() != MatchKind.MISSING) {
          n++;
        }
      }
      return n;
    }

    public int missingCount() {
      int n = 0;
      for (ProposedMapping m : mappings) {
        if (m != null && m.matchKind() == MatchKind.MISSING) {
          n++;
        }
      }
      return n;
    }
  }

  private DvLinkSourceSuggestSupport() {}

  /**
   * Suggest hub BK → source field mappings for {@code sourceName} against {@code hubs}.
   *
   * @param sourceName catalog feed name (e.g. asn-package-lines)
   * @param sourceFieldNames field names available on that feed
   * @param hubs hubs to map (typically link participating hubs)
   */
  public static SuggestResult suggestHubSourceMappings(
      String sourceName, List<String> sourceFieldNames, List<DvHub> hubs) {
    String feed = ConstNvl(sourceName).trim();
    List<String> fields = normalizeFieldList(sourceFieldNames);
    Map<String, String> fieldByLower = indexByLower(fields);

    DvLink.DvLinkHubSource hubSource = new DvLink.DvLinkHubSource();
    hubSource.setSourceName(feed);

    List<ProposedMapping> mappings = new ArrayList<>();
    List<String> fullyCoveredHubs = new ArrayList<>();

    if (hubs == null) {
      return new SuggestResult(feed, hubSource, mappings, fullyCoveredHubs);
    }

    for (DvHub hub : hubs) {
      if (hub == null || Utils.isEmpty(hub.getName())) {
        continue;
      }
      List<DvBusinessKeyPartSupport.VaultBusinessKey> vaultKeys =
          DvBusinessKeyPartSupport.resolveVaultBusinessKeys(hub);
      if (vaultKeys.isEmpty()) {
        continue;
      }

      DvLink.HubSourceKeyField hubKeyField = new DvLink.HubSourceKeyField();
      hubKeyField.setHubName(hub.getName());
      boolean allMapped = true;

      for (DvBusinessKeyPartSupport.VaultBusinessKey vaultKey : vaultKeys) {
        if (vaultKey == null || Utils.isEmpty(vaultKey.vaultFieldName())) {
          continue;
        }
        if (vaultKey.composite()) {
          // Composite: try to map each source part by name from the hub definition first.
          BusinessKey def = vaultKey.definition();
          List<String> partNames =
              def != null ? def.resolveSourceParts() : List.of(vaultKey.vaultFieldName());
          if (partNames.isEmpty()) {
            partNames = List.of(vaultKey.vaultFieldName());
          }
          List<String> mappedParts = new ArrayList<>();
          MatchKind worst = MatchKind.EXACT;
          for (String part : partNames) {
            Match match = matchField(part, fieldByLower, fields);
            if (match.kind() == MatchKind.MISSING) {
              allMapped = false;
              worst = MatchKind.MISSING;
            } else {
              mappedParts.add(match.sourceField());
              if (match.kind() == MatchKind.ALIAS && worst != MatchKind.MISSING) {
                worst = MatchKind.ALIAS;
              }
            }
          }
          String displaySource = mappedParts.isEmpty() ? null : String.join(", ", mappedParts);
          mappings.add(
              new ProposedMapping(hub.getName(), vaultKey.vaultFieldName(), displaySource, worst));
          if (worst != MatchKind.MISSING && !mappedParts.isEmpty()) {
            BusinessKeySource bks = new BusinessKeySource();
            bks.setBusinessKeyField(vaultKey.vaultFieldName());
            if (mappedParts.size() == 1) {
              bks.setSourceFieldName(mappedParts.get(0));
            } else {
              bks.setSourceFieldNames(new ArrayList<>(mappedParts));
              bks.setSourceFieldName(mappedParts.get(0));
            }
            hubKeyField.getSourceBusinessKeyFields().add(bks);
          }
        } else {
          Match match = matchField(vaultKey.vaultFieldName(), fieldByLower, fields);
          mappings.add(
              new ProposedMapping(
                  hub.getName(), vaultKey.vaultFieldName(), match.sourceField(), match.kind()));
          if (match.kind() == MatchKind.MISSING) {
            allMapped = false;
          } else {
            hubKeyField
                .getSourceBusinessKeyFields()
                .add(new BusinessKeySource(vaultKey.vaultFieldName(), match.sourceField()));
          }
        }
      }

      if (!hubKeyField.getSourceBusinessKeyFields().isEmpty()) {
        hubSource.getHubSourceKeyFields().add(hubKeyField);
      }
      if (allMapped && !vaultKeys.isEmpty()) {
        fullyCoveredHubs.add(hub.getName());
      }
    }

    return new SuggestResult(feed, hubSource, mappings, fullyCoveredHubs);
  }

  /**
   * Hubs from {@code model} whose business keys are all present as feed fields (exact/alias).
   * Useful to propose participating hubs before mapping.
   */
  public static List<String> suggestParticipatingHubNames(
      DataVaultModel model, List<String> sourceFieldNames) {
    List<String> names = new ArrayList<>();
    if (model == null) {
      return names;
    }
    List<DvHub> hubs = new ArrayList<>();
    for (IDvTable table : model.getTables()) {
      if (table instanceof DvHub hub) {
        hubs.add(hub);
      }
    }
    SuggestResult result = suggestHubSourceMappings("", sourceFieldNames, hubs);
    return new ArrayList<>(result.suggestedHubNames());
  }

  /**
   * Merge a proposed hub source into the working list.
   *
   * @param emptyOnly when true, only fill hubs that have no existing BK maps; never remove hubs
   */
  public static void mergeSuggestedHubSource(
      List<DvLink.DvLinkHubSource> working, DvLink.DvLinkHubSource proposed, boolean emptyOnly) {
    if (working == null || proposed == null || Utils.isEmpty(proposed.getSourceName())) {
      return;
    }
    DvLink.DvLinkHubSource existing = null;
    for (DvLink.DvLinkHubSource hs : working) {
      if (hs != null && proposed.getSourceName().equals(hs.getSourceName())) {
        existing = hs;
        break;
      }
    }
    if (existing == null) {
      working.add(proposed);
      return;
    }
    if (existing.getHubSourceKeyFields() == null) {
      existing.setHubSourceKeyFields(new ArrayList<>());
    }
    for (DvLink.HubSourceKeyField proposedHub : proposed.getHubSourceKeyFields()) {
      if (proposedHub == null || Utils.isEmpty(proposedHub.getHubName())) {
        continue;
      }
      DvLink.HubSourceKeyField current = null;
      for (DvLink.HubSourceKeyField h : existing.getHubSourceKeyFields()) {
        if (h != null && proposedHub.getHubName().equals(h.getHubName())) {
          current = h;
          break;
        }
      }
      if (current == null) {
        existing.getHubSourceKeyFields().add(proposedHub);
        continue;
      }
      if (emptyOnly
          && current.getSourceBusinessKeyFields() != null
          && !current.getSourceBusinessKeyFields().isEmpty()) {
        continue;
      }
      current.setSourceBusinessKeyFields(
          new ArrayList<>(
              proposedHub.getSourceBusinessKeyFields() != null
                  ? proposedHub.getSourceBusinessKeyFields()
                  : List.of()));
    }
  }

  private record Match(String sourceField, MatchKind kind) {}

  private static Match matchField(
      String businessKeyName, Map<String, String> fieldByLower, List<String> fieldsInOrder) {
    if (Utils.isEmpty(businessKeyName)) {
      return new Match(null, MatchKind.MISSING);
    }
    String lower = businessKeyName.trim().toLowerCase(Locale.ROOT);
    String exact = fieldByLower.get(lower);
    if (exact != null) {
      return new Match(exact, MatchKind.EXACT);
    }
    // Common aliases: strip trailing _id / id and compare stems; or match bk without hub_ prefix.
    String stem = stripIdSuffix(lower);
    for (String field : fieldsInOrder) {
      if (field == null) {
        continue;
      }
      String fLower = field.toLowerCase(Locale.ROOT);
      if (stripIdSuffix(fLower).equals(stem)) {
        return new Match(field, MatchKind.ALIAS);
      }
    }
    return new Match(null, MatchKind.MISSING);
  }

  private static String stripIdSuffix(String lowerName) {
    if (lowerName.endsWith("_id") && lowerName.length() > 3) {
      return lowerName.substring(0, lowerName.length() - 3);
    }
    if (lowerName.endsWith("id") && lowerName.length() > 2) {
      return lowerName.substring(0, lowerName.length() - 2);
    }
    return lowerName;
  }

  private static List<String> normalizeFieldList(List<String> sourceFieldNames) {
    Set<String> ordered = new LinkedHashSet<>();
    if (sourceFieldNames != null) {
      for (String name : sourceFieldNames) {
        if (!Utils.isEmpty(name)) {
          ordered.add(name.trim());
        }
      }
    }
    return new ArrayList<>(ordered);
  }

  private static Map<String, String> indexByLower(List<String> fields) {
    Map<String, String> map = new LinkedHashMap<>();
    for (String field : fields) {
      map.putIfAbsent(field.toLowerCase(Locale.ROOT), field);
    }
    return map;
  }

  private static String ConstNvl(String value) {
    return value == null ? "" : value;
  }
}
