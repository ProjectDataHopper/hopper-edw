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
package org.hopper.edw.datavault.metadata.sourcemodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * One output column of a {@link SourceMasking} card. A blank {@link #patternName} passes the parent
 * value through. A name is a Hop {@code MaskingPattern} metadata item, not a copy of the rule.
 */
@Getter
@Setter
@NoArgsConstructor
public class SourceMaskingField {

  @HopMetadataProperty private String name;
  @HopMetadataProperty private String description;

  @HopMetadataProperty(intCodeConverter = ValueMetaBase.ValueTypeCodeConverter.class)
  private int hopType;

  @HopMetadataProperty private int length = -1;
  @HopMetadataProperty private int precision = -1;

  /** 1-based logical key position. Zero means the field is not part of the feed grain. */
  @HopMetadataProperty private int primaryKeyPosition;

  /** Name of a Hop masking pattern. Empty leaves the value unchanged. */
  @HopMetadataProperty private String patternName;

  public SourceMaskingField(String name) {
    this.name = name;
  }

  public String resolveName() {
    return name != null ? name.trim() : "";
  }

  public boolean isPrimaryKey() {
    return primaryKeyPosition > 0;
  }

  public boolean hasPattern() {
    return !Utils.isEmpty(patternName);
  }
}
