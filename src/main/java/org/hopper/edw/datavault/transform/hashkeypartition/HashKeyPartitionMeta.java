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
package org.hopper.edw.datavault.transform.hashkeypartition;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.gui.plugin.GuiWidgetGroupType;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.pipeline.transform.BaseTransformMeta;

/** Keeps rows whose hash key falls in ${PARTITION_NUMBER} of ${PARTITION_COUNT}. */
@Getter
@Setter
@GuiPlugin
@Transform(
    id = "HashKeyPartition",
    image = "sortedmerge.svg",
    name = "i18n::HashKeyPartition.Name",
    description = "i18n::HashKeyPartition.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Flow",
    keywords = "i18n::HashKeyPartition.keyword")
public class HashKeyPartitionMeta
    extends BaseTransformMeta<HashKeyPartition, HashKeyPartitionData> {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "HashKeyPartitionDialog";

  @GuiWidgetElement(
      id = "keyField",
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::HashKeyPartition.Key.Label",
      toolTip = "i18n::HashKeyPartition.Key.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = "i18n::HashKeyPartition.Group")
  @HopMetadataProperty
  private String keyField;

  @HopMetadataProperty private String hashKeyDataType = "HEX";

  @Override
  public void setDefault() {
    keyField = "";
    hashKeyDataType = "HEX";
  }
}
