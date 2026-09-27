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
package org.hopper.edw.datavault.transform.identitylookup;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.gui.plugin.GuiWidgetGroupType;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.datavault.metadata.businessvault.BvIdentityKeys;
import org.hopper.edw.datavault.metadata.businessvault.BvIdentityUnmappedPolicy;

/** As-of replace of a raw hub hash with the durable hash from an identity map. */
@Getter
@Setter
@GuiPlugin
@Transform(
    id = "IdentityLookup",
    image = "sortedmerge.svg",
    name = "i18n::IdentityLookup.Name",
    description = "i18n::IdentityLookup.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Flow",
    keywords = "i18n::IdentityLookup.keyword")
public class IdentityLookupMeta extends BaseTransformMeta<IdentityLookup, IdentityLookupData> {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "IdentityLookupDialog";
  private static final String GROUP = "i18n::IdentityLookup.Group";

  @GuiWidgetElement(
      id = "rawKeyField",
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::IdentityLookup.RawKey.Label",
      toolTip = "i18n::IdentityLookup.RawKey.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String rawKeyField;

  @GuiWidgetElement(
      id = "timestampField",
      order = "0200",
      type = GuiElementType.TEXT,
      label = "i18n::IdentityLookup.Timestamp.Label",
      toolTip = "i18n::IdentityLookup.Timestamp.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String timestampField;

  @GuiWidgetElement(
      id = "mapTransform",
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::IdentityLookup.MapTransform.Label",
      toolTip = "i18n::IdentityLookup.MapTransform.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String mapTransform;

  @HopMetadataProperty private String mapRawField = BvIdentityKeys.HK_RAW;
  @HopMetadataProperty private String mapDurableField = BvIdentityKeys.HK_DURABLE;
  @HopMetadataProperty private String mapPreferredField = BvIdentityKeys.PREFERRED_BK;
  @HopMetadataProperty private String mapValidFromField = BvIdentityKeys.VALID_FROM;
  @HopMetadataProperty private String mapValidToField = BvIdentityKeys.VALID_TO;
  @HopMetadataProperty private String mapRuleVersionField = BvIdentityKeys.RULE_VERSION;
  @HopMetadataProperty private String rawPayloadField = BvIdentityKeys.HK_RAW;
  @HopMetadataProperty private String preferredPayloadField = BvIdentityKeys.PREFERRED_BK;
  @HopMetadataProperty private String ruleVersionPayloadField = BvIdentityKeys.MAP_RULE_VERSION;

  @GuiWidgetElement(
      id = "unmappedPolicy",
      order = "0400",
      type = GuiElementType.COMBO,
      comboValuesMethod = "unmappedPolicyValues",
      label = "i18n::IdentityLookup.Unmapped.Label",
      toolTip = "i18n::IdentityLookup.Unmapped.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String unmappedPolicy = BvIdentityUnmappedPolicy.SELF.getCode();

  public List<String> unmappedPolicyValues() {
    return List.of("self", "quarantine", "drop");
  }

  public BvIdentityUnmappedPolicy unmappedPolicyOrDefault() {
    BvIdentityUnmappedPolicy policy = BvIdentityUnmappedPolicy.lookupCode(unmappedPolicy);
    return policy != null ? policy : BvIdentityUnmappedPolicy.SELF;
  }

  @Override
  public void setDefault() {
    rawKeyField = "";
    timestampField = "";
    mapTransform = "";
    mapRawField = BvIdentityKeys.HK_RAW;
    mapDurableField = BvIdentityKeys.HK_DURABLE;
    mapPreferredField = BvIdentityKeys.PREFERRED_BK;
    mapValidFromField = BvIdentityKeys.VALID_FROM;
    mapValidToField = BvIdentityKeys.VALID_TO;
    mapRuleVersionField = BvIdentityKeys.RULE_VERSION;
    rawPayloadField = BvIdentityKeys.HK_RAW;
    preferredPayloadField = BvIdentityKeys.PREFERRED_BK;
    ruleVersionPayloadField = BvIdentityKeys.MAP_RULE_VERSION;
    unmappedPolicy = BvIdentityUnmappedPolicy.SELF.getCode();
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
    addIfMissing(inputRowMeta, rawPayloadField, rawKeyField);
    if (!Utils.isEmpty(preferredPayloadField)
        && inputRowMeta.indexOfValue(preferredPayloadField) < 0) {
      IValueMeta preferred = new ValueMetaString(preferredPayloadField);
      preferred.setOrigin(name);
      inputRowMeta.addValueMeta(preferred);
    }
    if (!Utils.isEmpty(ruleVersionPayloadField)
        && inputRowMeta.indexOfValue(ruleVersionPayloadField) < 0) {
      IValueMeta version =
          new org.apache.hop.core.row.value.ValueMetaInteger(ruleVersionPayloadField);
      version.setOrigin(name);
      inputRowMeta.addValueMeta(version);
    }
  }

  private static void addIfMissing(IRowMeta rowMeta, String fieldName, String typeSource) {
    if (Utils.isEmpty(fieldName) || rowMeta.indexOfValue(fieldName) >= 0) {
      return;
    }
    IValueMeta source = Utils.isEmpty(typeSource) ? null : rowMeta.searchValueMeta(typeSource);
    IValueMeta added = source == null ? new ValueMetaString(fieldName) : source.clone();
    added.setName(fieldName);
    rowMeta.addValueMeta(added);
  }
}
