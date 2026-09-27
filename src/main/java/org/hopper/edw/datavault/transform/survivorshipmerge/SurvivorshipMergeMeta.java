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
package org.hopper.edw.datavault.transform.survivorshipmerge;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.gui.plugin.GuiWidgetGroupType;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeLogic;

/** Ranked merge of pre-sorted source legs. No database I/O. */
@Getter
@Setter
@GuiPlugin
@Transform(
    id = "SurvivorshipMerge",
    image = "sortedmerge.svg",
    name = "i18n::SurvivorshipMerge.Name",
    description = "i18n::SurvivorshipMerge.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Flow",
    keywords = "i18n::SurvivorshipMerge.keyword")
public class SurvivorshipMergeMeta
    extends BaseTransformMeta<SurvivorshipMerge, SurvivorshipMergeData> {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "SurvivorshipMergeDialog";
  private static final String GROUP = "i18n::SurvivorshipMerge.Group";

  @GuiWidgetElement(
      id = "timestampField",
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::SurvivorshipMerge.Timestamp.Label",
      toolTip = "i18n::SurvivorshipMerge.Timestamp.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String timestampField;

  @GuiWidgetElement(
      id = "sourceField",
      order = "0200",
      type = GuiElementType.TEXT,
      label = "i18n::SurvivorshipMerge.Source.Label",
      toolTip = "i18n::SurvivorshipMerge.Source.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String sourceField;

  @GuiWidgetElement(
      id = "opField",
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::SurvivorshipMerge.Operation.Label",
      toolTip = "i18n::SurvivorshipMerge.Operation.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String opField;

  @HopMetadataProperty(key = "key", groupKey = "keys")
  private List<SurvivorshipMergeKey> keys = new ArrayList<>();

  @HopMetadataProperty(key = "rule", groupKey = "rules")
  private List<SurvivorshipMergeRule> rules = new ArrayList<>();

  @Override
  public void setDefault() {
    timestampField = "";
    sourceField = "";
    opField = "";
    keys = new ArrayList<>();
    rules = new ArrayList<>();
  }

  @Override
  public boolean excludeFromRowLayoutVerification() {
    return true;
  }

  @Override
  public void getFields(
      IRowMeta inputRowMeta,
      String name,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    if (info == null || info.length == 0) {
      return;
    }
    try {
      IRowMeta output = SortedSchemaMergeLogic.buildSchemaMapping(info).getOutputRowMeta();
      for (int i = 0; i < output.size(); i++) {
        output.getValueMeta(i).setOrigin(name);
      }
      inputRowMeta.clear();
      inputRowMeta.addRowMeta(output);
    } catch (HopPluginException e) {
      throw new HopTransformException("Unable to resolve survivorship output fields", e);
    }
  }
}
