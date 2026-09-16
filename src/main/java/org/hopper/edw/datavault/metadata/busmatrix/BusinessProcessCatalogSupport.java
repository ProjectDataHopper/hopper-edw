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
package org.hopper.edw.datavault.metadata.busmatrix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.metadata.dimensional.DmBusinessProcessRef;

/** Loads and queries {@link BusinessProcessCatalogMeta} objects for combos and validation. */
public final class BusinessProcessCatalogSupport {

  public enum TermLevel {
    DOMAIN,
    LEVEL1,
    LEVEL2,
    LEVEL3
  }

  private BusinessProcessCatalogSupport() {}

  public static ResolvedCatalog load(
      IHopMetadataProvider metadataProvider,
      ResourceDefinitionGroupMeta group,
      IVariables variables) {
    String preferred = group != null ? group.getBusinessProcessCatalog() : null;
    if (variables != null && !Utils.isEmpty(preferred)) {
      preferred = variables.resolve(preferred);
    }
    return load(metadataProvider, preferred);
  }

  public static ResolvedCatalog load(IHopMetadataProvider metadataProvider, String preferredName) {
    ResolvedCatalog resolved = new ResolvedCatalog();
    if (metadataProvider == null) {
      return resolved;
    }
    try {
      IHopMetadataSerializer<BusinessProcessCatalogMeta> serializer =
          metadataProvider.getSerializer(BusinessProcessCatalogMeta.class);
      if (serializer == null) {
        return resolved;
      }
      if (!Utils.isEmpty(preferredName)) {
        BusinessProcessCatalogMeta named = serializer.load(preferredName);
        if (named != null) {
          resolved.add(named);
          resolved.catalogNames.add(named.getName());
          return resolved;
        }
        resolved.warnings.add("Business process catalog '" + preferredName + "' was not found.");
      }
      List<String> names = serializer.listObjectNames();
      if (names == null) {
        return resolved;
      }
      for (String name : names) {
        if (Utils.isEmpty(name)) {
          continue;
        }
        BusinessProcessCatalogMeta catalog = serializer.load(name);
        if (catalog != null) {
          resolved.add(catalog);
          resolved.catalogNames.add(catalog.getName());
        }
      }
    } catch (Exception e) {
      resolved.warnings.add("Unable to load business process catalogs: " + e.getMessage());
    }
    return resolved;
  }

  public static final class ResolvedCatalog {
    private final Map<String, BusinessProcessTerm> domains = new LinkedHashMap<>();
    private final Map<String, BusinessProcessTerm> level1 = new LinkedHashMap<>();
    private final Map<String, BusinessProcessTerm> level2 = new LinkedHashMap<>();
    private final Map<String, BusinessProcessTerm> level3 = new LinkedHashMap<>();
    private final List<String> catalogNames = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    void add(BusinessProcessCatalogMeta catalog) {
      if (catalog == null) {
        return;
      }
      addTerms(domains, catalog.getDomains());
      addTerms(level1, catalog.getLevel1());
      addTerms(level2, catalog.getLevel2());
      addTerms(level3, catalog.getLevel3());
    }

    private void addTerms(
        Map<String, BusinessProcessTerm> target, List<BusinessProcessTerm> terms) {
      if (terms == null) {
        return;
      }
      for (BusinessProcessTerm term : terms) {
        if (term == null || Utils.isEmpty(term.getName())) {
          continue;
        }
        String key = term.getName();
        if (target.containsKey(key) && !sameParent(target.get(key), term)) {
          warnings.add("Duplicate business process term '" + key + "'.");
        }
        target.putIfAbsent(key, term);
      }
    }

    private static boolean sameParent(BusinessProcessTerm left, BusinessProcessTerm right) {
      String a = left != null ? ConstNvl(left.getParentName()) : "";
      String b = right != null ? ConstNvl(right.getParentName()) : "";
      return a.equals(b);
    }

    private static String ConstNvl(String value) {
      return value != null ? value : "";
    }

    public boolean isEmpty() {
      return domains.isEmpty() && level1.isEmpty() && level2.isEmpty() && level3.isEmpty();
    }

    public List<String> catalogNames() {
      return List.copyOf(catalogNames);
    }

    public List<String> warnings() {
      return List.copyOf(warnings);
    }

    public String[] names(TermLevel level) {
      return names(level, null, null);
    }

    /**
     * Names at {@code level}. When {@code parentName} is set, only children of that parent are
     * returned (plus {@code current} so an existing value stays visible).
     */
    public String[] names(TermLevel level, String parentName, String current) {
      Map<String, BusinessProcessTerm> terms = terms(level);
      List<String> names = new ArrayList<>();
      names.add("");
      boolean filterParent = !Utils.isEmpty(parentName) && level != TermLevel.DOMAIN;
      for (BusinessProcessTerm term : terms.values()) {
        if (filterParent && !parentName.equals(term.getParentName())) {
          continue;
        }
        names.add(term.getName());
      }
      if (!Utils.isEmpty(current) && !names.contains(current)) {
        names.add(current);
      }
      return names.toArray(String[]::new);
    }

    public BusinessProcessTerm find(TermLevel level, String name) {
      if (Utils.isEmpty(name)) {
        return null;
      }
      return terms(level).get(name);
    }

    public List<String> validate(DmBusinessProcessRef ref) {
      List<String> issues = new ArrayList<>();
      if (ref == null || ref.isEmpty() || isEmpty()) {
        return issues;
      }
      validateTerm(issues, TermLevel.DOMAIN, ref.getBusiness(), null);
      validateTerm(issues, TermLevel.LEVEL1, ref.getLevel1(), ref.getBusiness());
      validateTerm(issues, TermLevel.LEVEL2, ref.getLevel2(), ref.getLevel1());
      validateTerm(issues, TermLevel.LEVEL3, ref.getLevel3(), ref.getLevel2());
      return issues;
    }

    private void validateTerm(
        List<String> issues, TermLevel level, String name, String expectedParent) {
      if (Utils.isEmpty(name)) {
        return;
      }
      BusinessProcessTerm term = find(level, name);
      if (term == null) {
        issues.add(level.name() + ":" + name);
        return;
      }
      if (!Utils.isEmpty(expectedParent)
          && !Utils.isEmpty(term.getParentName())
          && !expectedParent.equals(term.getParentName())) {
        issues.add(level.name() + ":" + name);
      }
    }

    private Map<String, BusinessProcessTerm> terms(TermLevel level) {
      return switch (level) {
        case DOMAIN -> domains;
        case LEVEL1 -> level1;
        case LEVEL2 -> level2;
        case LEVEL3 -> level3;
      };
    }
  }
}
