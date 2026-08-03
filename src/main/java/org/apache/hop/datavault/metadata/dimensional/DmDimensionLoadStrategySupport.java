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
package org.apache.hop.datavault.metadata.dimensional;

import java.util.HashSet;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;

/** Derives dimension load strategy and display labels from per-attribute SCD policies. */
public final class DmDimensionLoadStrategySupport {

  private static final Class<?> PKG = DmDimensionLoadStrategySupport.class;

  public enum DmDimensionLoadStrategy {
    PURE_TYPE1,
    PURE_TYPE2,
    DIMENSION_LOOKUP
  }

  private DmDimensionLoadStrategySupport() {}

  public static DmScdUpdatePolicy resolveEffectivePolicy(DmDimensionAttribute attribute) {
    if (attribute == null || attribute.getScdUpdatePolicy() == null) {
      return DmScdUpdatePolicy.TYPE1;
    }
    return attribute.getScdUpdatePolicy();
  }

  public static String resolveSourceFieldName(
      DmDimensionAttribute attribute, IVariables variables) {
    if (attribute == null) {
      return null;
    }
    String source = attribute.getSourceFieldName();
    if (Utils.isEmpty(source)) {
      source = attribute.getFieldName();
    }
    return resolveFieldName(source, variables);
  }

  public static String resolveTargetFieldName(
      DmDimensionAttribute attribute, IVariables variables) {
    if (attribute == null) {
      return null;
    }
    return resolveFieldName(attribute.getFieldName(), variables);
  }

  public static DmDimensionLoadStrategy resolveLoadStrategy(DmDimension dimension) {
    if (dimension == null) {
      return DmDimensionLoadStrategy.PURE_TYPE1;
    }
    dimension.normalizeLegacyScdType();
    Set<DmScdUpdatePolicy> policies = collectEffectivePolicies(dimension);
    if (!policies.isEmpty()) {
      if (policies.size() == 1 && policies.contains(DmScdUpdatePolicy.TYPE1)) {
        return DmDimensionLoadStrategy.PURE_TYPE1;
      }
      if (policies.size() == 1 && policies.contains(DmScdUpdatePolicy.TYPE2)) {
        return DmDimensionLoadStrategy.PURE_TYPE2;
      }
      return DmDimensionLoadStrategy.DIMENSION_LOOKUP;
    }
    return resolveLegacyScdTypeStrategy(dimension.getScdType());
  }

  private static DmDimensionLoadStrategy resolveLegacyScdTypeStrategy(DmDimensionScdType legacy) {
    if (legacy == null) {
      return DmDimensionLoadStrategy.PURE_TYPE1;
    }
    return switch (legacy) {
      case TYPE2 -> DmDimensionLoadStrategy.PURE_TYPE2;
      case TYPE3, HYBRID -> DmDimensionLoadStrategy.DIMENSION_LOOKUP;
      default -> DmDimensionLoadStrategy.PURE_TYPE1;
    };
  }

  public static boolean usesVersionedLayout(DmDimension dimension) {
    DmDimensionLoadStrategy strategy = resolveLoadStrategy(dimension);
    return strategy == DmDimensionLoadStrategy.PURE_TYPE2
        || strategy == DmDimensionLoadStrategy.DIMENSION_LOOKUP;
  }

  public static boolean usesEffectivityLookup(DmDimension dimension) {
    return usesVersionedLayout(dimension);
  }

  public static boolean usesDimensionLookupUpdate(DmDimension dimension) {
    return resolveLoadStrategy(dimension) == DmDimensionLoadStrategy.DIMENSION_LOOKUP;
  }

  public static String resolveDisplayLabel(DmDimension dimension) {
    DmDimensionLoadStrategy strategy = resolveLoadStrategy(dimension);
    return switch (strategy) {
      case PURE_TYPE1 ->
          BaseMessages.getString(PKG, "DmDimensionLoadStrategySupport.Display.Type1");
      case PURE_TYPE2 ->
          BaseMessages.getString(PKG, "DmDimensionLoadStrategySupport.Display.Type2");
      case DIMENSION_LOOKUP -> resolveDimensionLookupDisplayLabel(dimension);
    };
  }

  /**
   * @deprecated Use {@link #resolveDisplayLabel(DmDimension)}; retained for AI/serialization
   *     compat.
   */
  @Deprecated
  public static DmDimensionScdType resolveDerivedScdType(DmDimension dimension) {
    DmDimensionLoadStrategy strategy = resolveLoadStrategy(dimension);
    return switch (strategy) {
      case PURE_TYPE1 -> DmDimensionScdType.TYPE1;
      case PURE_TYPE2 -> DmDimensionScdType.TYPE2;
      case DIMENSION_LOOKUP -> {
        if (isType3Only(dimension)) {
          yield DmDimensionScdType.TYPE3;
        }
        yield DmDimensionScdType.HYBRID;
      }
    };
  }

  private static String resolveDimensionLookupDisplayLabel(DmDimension dimension) {
    if (isType3Only(dimension)) {
      return BaseMessages.getString(PKG, "DmDimensionLoadStrategySupport.Display.Type3");
    }
    return BaseMessages.getString(PKG, "DmDimensionLoadStrategySupport.Display.Hybrid");
  }

  private static boolean isType3Only(DmDimension dimension) {
    Set<DmScdUpdatePolicy> policies = collectEffectivePolicies(dimension);
    if (policies.isEmpty()) {
      return false;
    }
    for (DmScdUpdatePolicy policy : policies) {
      if (policy != DmScdUpdatePolicy.TYPE3_CURRENT
          && policy != DmScdUpdatePolicy.TYPE3_PREVIOUS
          && policy != DmScdUpdatePolicy.TYPE1) {
        return false;
      }
    }
    return policies.contains(DmScdUpdatePolicy.TYPE3_CURRENT)
        || policies.contains(DmScdUpdatePolicy.TYPE3_PREVIOUS);
  }

  private static Set<DmScdUpdatePolicy> collectEffectivePolicies(DmDimension dimension) {
    Set<DmScdUpdatePolicy> policies = new HashSet<>();
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      if (attribute == null || Utils.isEmpty(attribute.getFieldName())) {
        continue;
      }
      policies.add(resolveEffectivePolicy(attribute));
    }
    return policies;
  }

  private static String resolveFieldName(String fieldName, IVariables variables) {
    if (variables != null && fieldName != null) {
      fieldName = variables.resolve(fieldName);
    }
    return fieldName;
  }
}
