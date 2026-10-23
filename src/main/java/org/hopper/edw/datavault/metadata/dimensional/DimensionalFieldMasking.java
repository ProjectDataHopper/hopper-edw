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
package org.hopper.edw.datavault.metadata.dimensional;

import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.transforms.maskfields.MaskingPattern;
import org.apache.hop.pipeline.transforms.maskfields.MaskingStorage;

/** Masking-pattern names stored on dimensional fields, and the model checks around them. */
public final class DimensionalFieldMasking {

  private static final Class<?> PKG = DimensionalFieldMasking.class;

  private DimensionalFieldMasking() {}

  public static boolean hasPattern(String patternName) {
    return !Utils.isEmpty(patternName) && !patternName.isBlank();
  }

  public static String patternOrNull(String patternName) {
    return hasPattern(patternName) ? patternName.trim() : null;
  }

  public static int maskedCount(IDmTable table) {
    if (table instanceof DmDimension dimension) {
      return maskedDimensionCount(dimension);
    }
    if (table instanceof DmJunkDimension junk) {
      return maskedNaturalKeys(junk.getKeyFieldsOrEmpty());
    }
    if (table instanceof IDmFactLikeTable fact) {
      return maskedMeasures(fact.getMeasuresOrEmpty())
          + maskedDegenerates(fact.getDegenerateDimensionsOrEmpty());
    }
    return 0;
  }

  public static void check(
      List<ICheckResult> remarks,
      IDmTable table,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    if (remarks == null || table == null) {
      return;
    }
    if (table instanceof DmDimension dimension) {
      checkDimension(remarks, dimension, metadataProvider, variables, model);
    } else if (table instanceof DmJunkDimension junk) {
      checkPatterns(remarks, junk, metadataProvider, names(junk.getKeyFieldsOrEmpty()));
    } else if (table instanceof IDmFactLikeTable fact) {
      checkPatterns(remarks, table, metadataProvider, factPatternNames(fact));
    }
  }

  private static void checkDimension(
      List<ICheckResult> remarks,
      DmDimension dimension,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    checkPatterns(remarks, dimension, metadataProvider, dimensionPatternNames(dimension));
    if (!DmSurrogateKeySupport.usesSurrogateColumn(dimension)
        && maskedNaturalKeys(dimension.getNaturalKeysOrEmpty()) > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "DimensionalFieldMasking.CheckResult.MaskedNaturalKey",
                  dimension.getName()),
              dimension));
    }
  }

  private static void checkPatterns(
      List<ICheckResult> remarks,
      IDmTable table,
      IHopMetadataProvider metadataProvider,
      List<String> patternNames) {
    if (metadataProvider == null) {
      return;
    }
    for (String patternName : patternNames) {
      MaskingPattern pattern = load(metadataProvider, patternName);
      if (pattern == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DimensionalFieldMasking.CheckResult.MissingPattern",
                    patternName,
                    table.getName()),
                table));
        continue;
      }
      if (pattern.getStorage() == MaskingStorage.MEMORY) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_WARNING,
                BaseMessages.getString(
                    PKG,
                    "DimensionalFieldMasking.CheckResult.MemoryPattern",
                    pattern.getName(),
                    table.getName()),
                table));
      }
    }
  }

  public static MaskingPattern load(IHopMetadataProvider metadataProvider, String patternName) {
    if (metadataProvider == null || !hasPattern(patternName)) {
      return null;
    }
    try {
      return metadataProvider.getSerializer(MaskingPattern.class).load(patternName.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private static List<String> dimensionPatternNames(DmDimension dimension) {
    java.util.ArrayList<String> names = new java.util.ArrayList<>();
    names.addAll(names(dimension.getNaturalKeysOrEmpty()));
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      String pattern = attribute != null ? patternOrNull(attribute.getMaskingPatternName()) : null;
      if (pattern != null) {
        names.add(pattern);
      }
    }
    return names;
  }

  private static List<String> factPatternNames(IDmFactLikeTable fact) {
    java.util.ArrayList<String> names = new java.util.ArrayList<>();
    names.addAll(namesOfMeasures(fact.getMeasuresOrEmpty()));
    names.addAll(namesOfDegenerates(fact.getDegenerateDimensionsOrEmpty()));
    return names;
  }

  private static List<String> names(List<DmNaturalKeyField> fields) {
    java.util.ArrayList<String> names = new java.util.ArrayList<>();
    if (fields == null) {
      return names;
    }
    for (DmNaturalKeyField field : fields) {
      String pattern = field != null ? patternOrNull(field.getMaskingPatternName()) : null;
      if (pattern != null) {
        names.add(pattern);
      }
    }
    return names;
  }

  private static List<String> namesOfMeasures(List<DmFactMeasure> fields) {
    java.util.ArrayList<String> names = new java.util.ArrayList<>();
    for (DmFactMeasure field : fields) {
      String pattern = field != null ? patternOrNull(field.getMaskingPatternName()) : null;
      if (pattern != null) {
        names.add(pattern);
      }
    }
    return names;
  }

  private static List<String> namesOfDegenerates(List<DmFactDegenerateDimension> fields) {
    java.util.ArrayList<String> names = new java.util.ArrayList<>();
    for (DmFactDegenerateDimension field : fields) {
      String pattern = field != null ? patternOrNull(field.getMaskingPatternName()) : null;
      if (pattern != null) {
        names.add(pattern);
      }
    }
    return names;
  }

  private static int maskedDimensionCount(DmDimension dimension) {
    int count = maskedNaturalKeys(dimension.getNaturalKeysOrEmpty());
    for (DmDimensionAttribute attribute : dimension.getAttributesOrEmpty()) {
      if (attribute != null && hasPattern(attribute.getMaskingPatternName())) {
        count++;
        if (attribute.getScdUpdatePolicy() == DmScdUpdatePolicy.TYPE3_PREVIOUS) {
          count++;
        }
      }
    }
    return count;
  }

  private static int maskedNaturalKeys(List<DmNaturalKeyField> fields) {
    int count = 0;
    if (fields == null) {
      return 0;
    }
    for (DmNaturalKeyField field : fields) {
      if (field != null && hasPattern(field.getMaskingPatternName())) {
        count++;
      }
    }
    return count;
  }

  private static int maskedMeasures(List<DmFactMeasure> fields) {
    int count = 0;
    for (DmFactMeasure field : fields) {
      if (field != null && hasPattern(field.getMaskingPatternName())) {
        count++;
      }
    }
    return count;
  }

  private static int maskedDegenerates(List<DmFactDegenerateDimension> fields) {
    int count = 0;
    for (DmFactDegenerateDimension field : fields) {
      if (field != null && hasPattern(field.getMaskingPatternName())) {
        count++;
      }
    }
    return count;
  }
}
