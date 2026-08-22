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
package org.apache.hop.datavault.transform.mergerowsplus;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.transforms.mergerows.PassThroughField;

/** Builds {@link MergeRowsPlusMeta} instances for generated pipelines. */
public final class MergeRowsPlusMetaFactory {

  private MergeRowsPlusMetaFactory() {}

  public static MergeRowsPlusMeta create(
      String referenceTransform,
      String compareTransform,
      String flagField,
      List<String> keyFields,
      List<String> valueFields) {
    return create(referenceTransform, compareTransform, flagField, keyFields, valueFields, true);
  }

  public static MergeRowsPlusMeta create(
      String referenceTransform,
      String compareTransform,
      String flagField,
      List<String> keyFields,
      List<String> valueFields,
      boolean alignInputLayouts) {
    MergeRowsPlusMeta meta = new MergeRowsPlusMeta();
    meta.setReferenceTransform(referenceTransform);
    meta.setCompareTransform(compareTransform);
    meta.setFlagField(flagField);
    meta.setAlignInputLayouts(alignInputLayouts);
    if (keyFields != null) {
      meta.getKeyFields().addAll(keyFields);
    }
    if (valueFields != null) {
      meta.getValueFields().addAll(valueFields);
    }
    return meta;
  }

  public static List<String> fieldNames(String... names) {
    List<String> fields = new ArrayList<>();
    if (names == null) {
      return fields;
    }
    for (String name : names) {
      if (!Utils.isEmpty(name)) {
        fields.add(name);
      }
    }
    return fields;
  }

  public static PassThroughField passThrough(
      String sourceField, String renameTo, boolean reference) {
    return new PassThroughField(sourceField, renameTo, reference);
  }
}
