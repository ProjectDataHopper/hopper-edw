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
package org.hopper.edw.datavault.transform.identitymapassign;

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
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.datavault.metadata.HashAlgorithm;
import org.hopper.edw.datavault.metadata.HashKeyDataType;
import org.hopper.edw.datavault.metadata.businessvault.BvIdentityKeys;
import org.hopper.edw.datavault.transform.dvhashkey.DvHashKeyLogic;

/** Builds identity-map upsert rows from a same-as link and the map rows already stored. */
@Getter
@Setter
@GuiPlugin
@Transform(
    id = "IdentityMapAssign",
    image = "sortedmerge.svg",
    name = "i18n::IdentityMapAssign.Name",
    description = "i18n::IdentityMapAssign.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Flow",
    keywords = "i18n::IdentityMapAssign.keyword")
public class IdentityMapAssignMeta
    extends BaseTransformMeta<IdentityMapAssign, IdentityMapAssignData> {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "IdentityMapAssignDialog";
  private static final String GROUP = "i18n::IdentityMapAssign.Group";

  @GuiWidgetElement(
      id = "edgeTransform",
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::IdentityMapAssign.EdgeTransform.Label",
      toolTip = "i18n::IdentityMapAssign.EdgeTransform.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String edgeTransform;

  @GuiWidgetElement(
      id = "existingTransform",
      order = "0200",
      type = GuiElementType.TEXT,
      label = "i18n::IdentityMapAssign.ExistingTransform.Label",
      toolTip = "i18n::IdentityMapAssign.ExistingTransform.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP)
  @HopMetadataProperty
  private String existingTransform;

  @HopMetadataProperty private String masterField;
  @HopMetadataProperty private String duplicateField;
  @HopMetadataProperty private String preferredField;
  @HopMetadataProperty private String mdmField;
  @HopMetadataProperty private String edgeFromField;
  @HopMetadataProperty private String edgeToField;
  @HopMetadataProperty private String hashAlgorithm = "MD5";
  @HopMetadataProperty private String hashKeyDataType = "HEX";
  @HopMetadataProperty private String recordSource = "identity-map";

  @Override
  public void setDefault() {
    edgeTransform = "";
    existingTransform = "";
    hashAlgorithm = "MD5";
    hashKeyDataType = "HEX";
    recordSource = "identity-map";
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
    inputRowMeta.clear();
    HashKeyDataType type = HashKeyDataType.lookupCode(hashKeyDataType);
    HashAlgorithm algorithm = HashAlgorithm.lookupCode(hashAlgorithm);
    addHash(inputRowMeta, BvIdentityKeys.HK_RAW, algorithm, type);
    addHash(inputRowMeta, BvIdentityKeys.HK_DURABLE, algorithm, type);
    inputRowMeta.addValueMeta(new ValueMetaString(BvIdentityKeys.PREFERRED_BK));
    addHash(inputRowMeta, BvIdentityKeys.HK_MASTER, algorithm, type);
    inputRowMeta.addValueMeta(new ValueMetaTimestamp(BvIdentityKeys.VALID_FROM));
    inputRowMeta.addValueMeta(new ValueMetaTimestamp(BvIdentityKeys.VALID_TO));
    inputRowMeta.addValueMeta(new ValueMetaString(BvIdentityKeys.RECORD_SOURCE));
    inputRowMeta.addValueMeta(new ValueMetaInteger(BvIdentityKeys.RULE_VERSION));
  }

  private static void addHash(
      IRowMeta rowMeta, String fieldName, HashAlgorithm algorithm, HashKeyDataType type)
      throws HopTransformException {
    try {
      IValueMeta hash =
          ValueMetaFactory.createValueMeta(fieldName, DvHashKeyLogic.resultValueMetaType(type));
      hash.setLength(DvHashKeyLogic.resultValueMetaLength(algorithm, type));
      rowMeta.addValueMeta(hash);
    } catch (org.apache.hop.core.exception.HopPluginException e) {
      throw new HopTransformException("Unable to create identity hash field " + fieldName, e);
    }
  }
}
