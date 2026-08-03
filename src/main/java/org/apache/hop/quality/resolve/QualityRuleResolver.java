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
package org.apache.hop.quality.resolve;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.quality.metadata.DataQualityRuleSetMeta;
import org.apache.hop.quality.model.DataQualityRule;
import org.apache.hop.quality.model.RecordQualityRuleBinding;

/** Resolves catalog rule bindings and optional action-level rule sets into concrete rules. */
public final class QualityRuleResolver {

  private QualityRuleResolver() {}

  public static List<DataQualityRule> resolve(
      List<RecordQualityRuleBinding> bindings, IHopMetadataProvider metadataProvider)
      throws HopException {
    return resolve(bindings, metadataProvider, (String[]) null);
  }

  /**
   * Resolves catalog bindings plus optional whole rule sets selected on Measure / Evaluate actions.
   *
   * <p>A binding with {@code ruleSetName} and empty {@code ruleId} expands every enabled rule in
   * that set. Additional rule set names (action METADATA fields) expand the same way. Rules are
   * de-duplicated by id (first wins: catalog bindings before action rule sets).
   */
  public static List<DataQualityRule> resolve(
      List<RecordQualityRuleBinding> bindings,
      IHopMetadataProvider metadataProvider,
      String... additionalRuleSetNames)
      throws HopException {
    Map<String, DataQualityRule> byId = new LinkedHashMap<>();

    if (bindings != null) {
      for (RecordQualityRuleBinding binding : bindings) {
        if (binding == null || !binding.isEnabled()) {
          continue;
        }
        for (DataQualityRule rule : resolveBinding(binding, metadataProvider)) {
          putIfAbsent(byId, rule);
        }
      }
    }

    if (additionalRuleSetNames != null) {
      for (String setName : additionalRuleSetNames) {
        if (Utils.isEmpty(setName)) {
          continue;
        }
        for (DataQualityRule rule : expandRuleSet(setName.trim(), null, null, metadataProvider)) {
          putIfAbsent(byId, rule);
        }
      }
    }

    return new ArrayList<>(byId.values());
  }

  private static void putIfAbsent(Map<String, DataQualityRule> byId, DataQualityRule rule) {
    if (rule == null || !rule.isEnabled()) {
      return;
    }
    rule.ensureId();
    String id = rule.getId();
    if (Utils.isEmpty(id)) {
      byId.put("anon-" + byId.size(), rule);
      return;
    }
    byId.putIfAbsent(id, rule);
  }

  private static List<DataQualityRule> resolveBinding(
      RecordQualityRuleBinding binding, IHopMetadataProvider metadataProvider) throws HopException {
    List<DataQualityRule> out = new ArrayList<>();
    if (binding.getInlineRule() != null) {
      DataQualityRule base = binding.getInlineRule().copy();
      applyOverrides(base, binding);
      out.add(base);
      return out;
    }
    if (Utils.isEmpty(binding.getRuleSetName())) {
      return out;
    }
    if (Utils.isEmpty(binding.getRuleId())) {
      // Whole library: every enabled rule in the set.
      return expandRuleSet(
          binding.getRuleSetName(),
          binding.getSeverityOverride(),
          binding.getFieldNameOverride(),
          metadataProvider);
    }
    DataQualityRule one = resolveOneLibraryRule(binding, metadataProvider);
    if (one != null) {
      out.add(one);
    }
    return out;
  }

  private static DataQualityRule resolveOneLibraryRule(
      RecordQualityRuleBinding binding, IHopMetadataProvider metadataProvider) throws HopException {
    DataQualityRuleSetMeta set = loadRuleSet(binding.getRuleSetName(), metadataProvider);
    DataQualityRule libraryRule = set.findRule(binding.getRuleId());
    if (libraryRule == null) {
      throw new HopException(
          "Rule id '"
              + binding.getRuleId()
              + "' not found in rule set '"
              + binding.getRuleSetName()
              + "'");
    }
    DataQualityRule base = libraryRule.copy();
    applyOverrides(base, binding);
    return base;
  }

  private static List<DataQualityRule> expandRuleSet(
      String ruleSetName,
      org.apache.hop.quality.model.QualitySeverity severityOverride,
      String fieldNameOverride,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    DataQualityRuleSetMeta set = loadRuleSet(ruleSetName, metadataProvider);
    List<DataQualityRule> out = new ArrayList<>();
    for (DataQualityRule libraryRule : set.getRules()) {
      if (libraryRule == null || !libraryRule.isEnabled()) {
        continue;
      }
      DataQualityRule base = libraryRule.copy();
      if (severityOverride != null) {
        base.setSeverity(severityOverride);
      }
      if (!Utils.isEmpty(fieldNameOverride)) {
        base.setFieldName(fieldNameOverride);
      }
      base.ensureId();
      out.add(base);
    }
    return out;
  }

  private static DataQualityRuleSetMeta loadRuleSet(
      String ruleSetName, IHopMetadataProvider metadataProvider) throws HopException {
    if (metadataProvider == null) {
      throw new HopException(
          "Metadata provider is required to resolve rule set '" + ruleSetName + "'");
    }
    DataQualityRuleSetMeta set =
        metadataProvider.getSerializer(DataQualityRuleSetMeta.class).load(ruleSetName);
    if (set == null) {
      throw new HopException("Data quality rule set '" + ruleSetName + "' not found");
    }
    return set;
  }

  private static void applyOverrides(DataQualityRule base, RecordQualityRuleBinding binding) {
    if (base == null || binding == null) {
      return;
    }
    if (binding.getSeverityOverride() != null) {
      base.setSeverity(binding.getSeverityOverride());
    }
    if (!Utils.isEmpty(binding.getFieldNameOverride())) {
      base.setFieldName(binding.getFieldNameOverride());
    }
    base.ensureId();
  }
}
