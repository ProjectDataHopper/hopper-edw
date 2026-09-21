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
package org.hopper.edw.datavault.transform.syntheticdata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.gui.plugin.GuiWidgetGroupType;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.ITransformIOMeta;
import org.apache.hop.pipeline.transform.TransformIOMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transform.stream.IStream;
import org.apache.hop.pipeline.transform.stream.IStream.StreamType;
import org.apache.hop.pipeline.transform.stream.Stream;
import org.apache.hop.pipeline.transform.stream.StreamIcon;

/** Generates seeded synthetic rows from a field specification. */
@Getter
@Setter
@GuiPlugin
@Transform(
    id = "SyntheticData",
    image = "synthetic-data.svg",
    name = "i18n::SyntheticData.Name",
    description = "i18n::SyntheticData.Description",
    categoryDescription = "i18n:org.apache.hop.pipeline.transform:BaseTransform.Category.Input",
    keywords = "i18n::SyntheticData.keyword",
    documentationUrl = "/pipeline/transforms/syntheticdata.html")
public class SyntheticDataMeta extends BaseTransformMeta<SyntheticData, SyntheticDataData> {

  private static final Class<?> PKG = SyntheticDataMeta.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "SyntheticDataDialog";
  public static final String GROUP_GENERATION = "i18n::SyntheticData.Group.Generation";
  public static final String GROUP_DOCUMENT = "i18n::SyntheticData.Group.Document";

  public static final String WIDGET_SEED = "seed";
  public static final String WIDGET_MODE = "cardinalityMode";
  public static final String WIDGET_ROW_COUNT = "rowCount";
  public static final String WIDGET_FRACTION = "fraction";
  public static final String WIDGET_INCLUDE_FIRST = "includeFirst";
  public static final String WIDGET_MIN_ROWS = "minRows";
  public static final String WIDGET_MIN_CHILDREN = "minChildren";
  public static final String WIDGET_MAX_CHILDREN = "maxChildren";
  public static final String WIDGET_SELECTOR = "selectorField";
  public static final String WIDGET_PARENT_KEY = "parentKey";
  public static final String WIDGET_CHILD_KEY = "childKey";
  public static final String WIDGET_INCLUDE_STATUSES = "includeStatuses";
  public static final String WIDGET_FALLBACK_STATUSES = "fallbackStatuses";
  public static final String WIDGET_SPLIT_MIN = "splitMinChildren";
  public static final String WIDGET_SPLIT_P = "splitProbability";
  public static final String WIDGET_PAIR_COUNT = "pairCount";
  public static final String WIDGET_PAIR_LEFT_FIELD = "pairLeftField";
  public static final String WIDGET_PAIR_RIGHT_FIELD = "pairRightField";
  public static final String WIDGET_PAIR_LEFT_START = "pairLeftStart";
  public static final String WIDGET_PAIR_LEFT_COUNT = "pairLeftCount";
  public static final String WIDGET_PAIR_RIGHT_START = "pairRightStart";
  public static final String WIDGET_PAIR_RIGHT_COUNT = "pairRightCount";
  public static final String WIDGET_PARENTS = "parentsTransform";
  public static final String WIDGET_CHILDREN = "childrenTransform";
  public static final String WIDGET_PAIRS_LEFT = "pairsLeftTransform";
  public static final String WIDGET_PAIRS_RIGHT = "pairsRightTransform";
  public static final String WIDGET_DOCUMENT_FORMAT = "documentFormat";
  public static final String WIDGET_DOCUMENT_FILENAME = "documentFilename";
  public static final String WIDGET_DOCUMENT_ROOT = "documentRootElement";
  public static final String WIDGET_DOCUMENT_ROOT_ATTRS = "documentRootAttributes";
  public static final String WIDGET_DOCUMENT_ENCODING = "documentEncoding";

  @GuiWidgetElement(
      id = WIDGET_SEED,
      order = "0100",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.Seed.Label",
      toolTip = "i18n::SyntheticData.Seed.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "seed")
  private String seed = "1";

  @GuiWidgetElement(
      id = WIDGET_MODE,
      order = "0200",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getCardinalityModeValues",
      variables = false,
      label = "i18n::SyntheticData.Mode.Label",
      toolTip = "i18n::SyntheticData.Mode.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "cardinalityMode")
  private String cardinalityMode = "COUNT";

  @GuiWidgetElement(
      id = WIDGET_ROW_COUNT,
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.RowCount.Label",
      toolTip = "i18n::SyntheticData.RowCount.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "rowCount")
  private String rowCount = "10";

  @GuiWidgetElement(
      id = WIDGET_FRACTION,
      order = "0400",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.Fraction.Label",
      toolTip = "i18n::SyntheticData.Fraction.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "fraction")
  private String fraction = "1";

  @GuiWidgetElement(
      id = WIDGET_INCLUDE_FIRST,
      order = "0500",
      type = GuiElementType.CHECKBOX,
      label = "i18n::SyntheticData.IncludeFirst.Label",
      toolTip = "i18n::SyntheticData.IncludeFirst.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "includeFirst", defaultBoolean = true)
  private boolean includeFirst = true;

  @GuiWidgetElement(
      id = WIDGET_MIN_ROWS,
      order = "0600",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.MinRows.Label",
      toolTip = "i18n::SyntheticData.MinRows.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "minRows")
  private String minRows = "0";

  @GuiWidgetElement(
      id = WIDGET_MIN_CHILDREN,
      order = "0700",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.MinChildren.Label",
      toolTip = "i18n::SyntheticData.MinChildren.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "minChildren")
  private String minChildren = "1";

  @GuiWidgetElement(
      id = WIDGET_MAX_CHILDREN,
      order = "0800",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.MaxChildren.Label",
      toolTip = "i18n::SyntheticData.MaxChildren.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "maxChildren")
  private String maxChildren = "1";

  @GuiWidgetElement(
      id = WIDGET_SELECTOR,
      order = "0900",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.SelectorField.Label",
      toolTip = "i18n::SyntheticData.SelectorField.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "selectorField")
  private String selectorField = "";

  @GuiWidgetElement(
      id = WIDGET_PARENT_KEY,
      order = "1000",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.ParentKey.Label",
      toolTip = "i18n::SyntheticData.ParentKey.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "parentKey")
  private String parentKey = "";

  @GuiWidgetElement(
      id = WIDGET_CHILD_KEY,
      order = "1100",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.ChildKey.Label",
      toolTip = "i18n::SyntheticData.ChildKey.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "childKey")
  private String childKey = "";

  @GuiWidgetElement(
      id = WIDGET_INCLUDE_STATUSES,
      order = "1200",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.IncludeStatuses.Label",
      toolTip = "i18n::SyntheticData.IncludeStatuses.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "includeStatuses")
  private String includeStatuses = "";

  @GuiWidgetElement(
      id = WIDGET_FALLBACK_STATUSES,
      order = "1300",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.FallbackStatuses.Label",
      toolTip = "i18n::SyntheticData.FallbackStatuses.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "fallbackStatuses")
  private String fallbackStatuses = "";

  @GuiWidgetElement(
      id = WIDGET_SPLIT_MIN,
      order = "1400",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.SplitMinChildren.Label",
      toolTip = "i18n::SyntheticData.SplitMinChildren.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "splitMinChildren")
  private String splitMinChildren = "0";

  @GuiWidgetElement(
      id = WIDGET_SPLIT_P,
      order = "1500",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.SplitProbability.Label",
      toolTip = "i18n::SyntheticData.SplitProbability.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "splitProbability")
  private String splitProbability = "0";

  @GuiWidgetElement(
      id = WIDGET_PAIR_COUNT,
      order = "1600",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.PairCount.Label",
      toolTip = "i18n::SyntheticData.PairCount.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "pairCount")
  private String pairCount = "0";

  @GuiWidgetElement(
      id = WIDGET_PAIR_LEFT_FIELD,
      order = "1700",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.PairLeftField.Label",
      toolTip = "i18n::SyntheticData.PairLeftField.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "pairLeftField")
  private String pairLeftField = "";

  @GuiWidgetElement(
      id = WIDGET_PAIR_RIGHT_FIELD,
      order = "1800",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.PairRightField.Label",
      toolTip = "i18n::SyntheticData.PairRightField.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "pairRightField")
  private String pairRightField = "";

  @GuiWidgetElement(
      id = WIDGET_PAIR_LEFT_START,
      order = "1900",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.PairLeftStart.Label",
      toolTip = "i18n::SyntheticData.PairLeftStart.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "pairLeftStart")
  private String pairLeftStart = "";

  @GuiWidgetElement(
      id = WIDGET_PAIR_LEFT_COUNT,
      order = "2000",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.PairLeftCount.Label",
      toolTip = "i18n::SyntheticData.PairLeftCount.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "pairLeftCount")
  private String pairLeftCount = "";

  @GuiWidgetElement(
      id = WIDGET_PAIR_RIGHT_START,
      order = "2100",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.PairRightStart.Label",
      toolTip = "i18n::SyntheticData.PairRightStart.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "pairRightStart")
  private String pairRightStart = "";

  @GuiWidgetElement(
      id = WIDGET_PAIR_RIGHT_COUNT,
      order = "2200",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.PairRightCount.Label",
      toolTip = "i18n::SyntheticData.PairRightCount.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "pairRightCount")
  private String pairRightCount = "";

  @GuiWidgetElement(
      id = WIDGET_PARENTS,
      order = "2300",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.ParentsTransform.Label",
      toolTip = "i18n::SyntheticData.ParentsTransform.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "parentsTransform")
  private String parentsTransform = "";

  @GuiWidgetElement(
      id = WIDGET_CHILDREN,
      order = "2400",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.ChildrenTransform.Label",
      toolTip = "i18n::SyntheticData.ChildrenTransform.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "childrenTransform")
  private String childrenTransform = "";

  @GuiWidgetElement(
      id = WIDGET_PAIRS_LEFT,
      order = "2500",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.PairsLeftTransform.Label",
      toolTip = "i18n::SyntheticData.PairsLeftTransform.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "pairsLeftTransform")
  private String pairsLeftTransform = "";

  @GuiWidgetElement(
      id = WIDGET_PAIRS_RIGHT,
      order = "2600",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.PairsRightTransform.Label",
      toolTip = "i18n::SyntheticData.PairsRightTransform.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_GENERATION,
      groupOrder = "0100")
  @HopMetadataProperty(key = "pairsRightTransform")
  private String pairsRightTransform = "";

  @GuiWidgetElement(
      id = WIDGET_DOCUMENT_FORMAT,
      order = "0100",
      type = GuiElementType.COMBO,
      comboValuesMethod = "getDocumentFormatValues",
      variables = false,
      label = "i18n::SyntheticData.DocumentFormat.Label",
      toolTip = "i18n::SyntheticData.DocumentFormat.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_DOCUMENT,
      groupOrder = "0200")
  @HopMetadataProperty(key = "documentFormat")
  private String documentFormat = "NONE";

  @GuiWidgetElement(
      id = WIDGET_DOCUMENT_FILENAME,
      order = "0200",
      type = GuiElementType.FILENAME,
      label = "i18n::SyntheticData.DocumentFilename.Label",
      toolTip = "i18n::SyntheticData.DocumentFilename.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_DOCUMENT,
      groupOrder = "0200")
  @HopMetadataProperty(key = "documentFilename")
  private String documentFilename = "";

  @GuiWidgetElement(
      id = WIDGET_DOCUMENT_ROOT,
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.DocumentRoot.Label",
      toolTip = "i18n::SyntheticData.DocumentRoot.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_DOCUMENT,
      groupOrder = "0200")
  @HopMetadataProperty(key = "documentRootElement")
  private String documentRootElement = "";

  @GuiWidgetElement(
      id = WIDGET_DOCUMENT_ROOT_ATTRS,
      order = "0400",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.DocumentRootAttributes.Label",
      toolTip = "i18n::SyntheticData.DocumentRootAttributes.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_DOCUMENT,
      groupOrder = "0200")
  @HopMetadataProperty(key = "documentRootAttributes")
  private String documentRootAttributes = "";

  @GuiWidgetElement(
      id = WIDGET_DOCUMENT_ENCODING,
      order = "0500",
      type = GuiElementType.TEXT,
      label = "i18n::SyntheticData.DocumentEncoding.Label",
      toolTip = "i18n::SyntheticData.DocumentEncoding.Tooltip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.TABS,
      group = GROUP_DOCUMENT,
      groupOrder = "0200")
  @HopMetadataProperty(key = "documentEncoding")
  private String documentEncoding = "UTF-8";

  @HopMetadataProperty(groupKey = "populations", key = "population")
  private List<SyntheticPopulation> populations = new ArrayList<>();

  @HopMetadataProperty(groupKey = "paths", key = "path")
  private List<SyntheticPath> paths = new ArrayList<>();

  @HopMetadataProperty(groupKey = "fields", key = "field")
  private List<SyntheticField> fields = new ArrayList<>();

  @HopMetadataProperty(groupKey = "overrides", key = "override")
  private List<SyntheticOverride> overrides = new ArrayList<>();

  @HopMetadataProperty(groupKey = "xmlNodes", key = "xmlNode")
  private List<SyntheticXmlNode> xmlNodes = new ArrayList<>();

  public SyntheticDataMeta() {}

  public String[] getCardinalityModeValues() {
    return new String[] {"COUNT", "COMBINE", "PER_PARENT", "PATHS", "UNIQUE_PAIRS", "HIERARCHY"};
  }

  public String[] getDocumentFormatValues() {
    return new String[] {"NONE", "XML"};
  }

  @Override
  public void setDefault() {
    seed = "1";
    cardinalityMode = "COUNT";
    rowCount = "10";
    fraction = "1";
    includeFirst = true;
    minRows = "0";
    minChildren = "1";
    maxChildren = "1";
    splitMinChildren = "0";
    splitProbability = "0";
    pairCount = "0";
    documentFormat = "NONE";
    documentEncoding = "UTF-8";
  }

  @Override
  public SyntheticDataMeta clone() {
    SyntheticDataMeta copy = (SyntheticDataMeta) super.clone();
    copy.populations = new ArrayList<>();
    for (SyntheticPopulation population : populations) {
      copy.populations.add(new SyntheticPopulation(population));
    }
    copy.paths = new ArrayList<>();
    for (SyntheticPath path : paths) {
      copy.paths.add(new SyntheticPath(path));
    }
    copy.fields = new ArrayList<>();
    for (SyntheticField field : fields) {
      copy.fields.add(new SyntheticField(field));
    }
    copy.overrides = new ArrayList<>();
    for (SyntheticOverride override : overrides) {
      copy.overrides.add(new SyntheticOverride(override));
    }
    copy.xmlNodes = new ArrayList<>();
    for (SyntheticXmlNode node : xmlNodes) {
      copy.xmlNodes.add(new SyntheticXmlNode(node));
    }
    return copy;
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String name,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    rowMeta.clear();
    for (SyntheticField field : fields) {
      if (field == null || !field.isPublish() || Utils.isEmpty(field.getName())) {
        continue;
      }
      try {
        int type = ValueMetaFactory.getIdForValueMeta(field.getHopType());
        if (type < 0) {
          type = IValueMeta.TYPE_STRING;
        }
        IValueMeta valueMeta = ValueMetaFactory.createValueMeta(field.getName(), type);
        valueMeta.setOrigin(name);
        rowMeta.addValueMeta(valueMeta);
      } catch (HopPluginException e) {
        throw new HopTransformException(
            BaseMessages.getString(PKG, "SyntheticData.Error.FieldType", field.getName()), e);
      }
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (Utils.isEmpty(seed)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SyntheticData.Check.SeedMissing"),
              transformMeta));
    }
    if (fields == null || fields.stream().noneMatch(field -> field != null && field.isPublish())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SyntheticData.Check.FieldsMissing"),
              transformMeta));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "SyntheticData.Check.FieldsOk"),
              transformMeta));
    }
    if (transformMeta.getCopies(variables) > 1) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(PKG, "SyntheticData.Check.Copies"),
              transformMeta));
    }
    String mode = cardinalityMode == null ? "" : cardinalityMode.trim().toUpperCase();
    if (("PER_PARENT".equals(mode) || "PATHS".equals(mode) || "HIERARCHY".equals(mode))
        && Utils.isEmpty(parentsTransform)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SyntheticData.Check.ParentsMissing"),
              transformMeta));
    }
    if ("XML".equalsIgnoreCase(documentFormat) && Utils.isEmpty(documentFilename)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SyntheticData.Check.DocumentFilename"),
              transformMeta));
    }
  }

  @Override
  public boolean consumesMainInput() {
    return false;
  }

  @Override
  public boolean canStartWithoutInput() {
    return true;
  }

  @Override
  public void resetTransformIoMeta() {
    // Keep the info stream slots.
  }

  @Override
  public ITransformIOMeta getTransformIOMeta() {
    ITransformIOMeta ioMeta = super.getTransformIOMeta(false);
    if (ioMeta == null || ioMeta.getInfoStreams().size() < 4) {
      ioMeta = new TransformIOMeta(true, true, true, false, false, false);
      ioMeta.addStream(
          new Stream(
              StreamType.INFO,
              null,
              BaseMessages.getString(PKG, "SyntheticData.Info.Parents"),
              StreamIcon.INFO,
              parentsTransform));
      ioMeta.addStream(
          new Stream(
              StreamType.INFO,
              null,
              BaseMessages.getString(PKG, "SyntheticData.Info.Children"),
              StreamIcon.INFO,
              childrenTransform));
      ioMeta.addStream(
          new Stream(
              StreamType.INFO,
              null,
              BaseMessages.getString(PKG, "SyntheticData.Info.PairsLeft"),
              StreamIcon.INFO,
              pairsLeftTransform));
      ioMeta.addStream(
          new Stream(
              StreamType.INFO,
              null,
              BaseMessages.getString(PKG, "SyntheticData.Info.PairsRight"),
              StreamIcon.INFO,
              pairsRightTransform));
      setTransformIOMeta(ioMeta);
    }
    return ioMeta;
  }

  @Override
  public void searchInfoAndTargetTransforms(List<TransformMeta> transforms) {
    List<IStream> infoStreams = getTransformIOMeta().getInfoStreams();
    bind(infoStreams, 0, parentsTransform, transforms);
    bind(infoStreams, 1, childrenTransform, transforms);
    bind(infoStreams, 2, pairsLeftTransform, transforms);
    bind(infoStreams, 3, pairsRightTransform, transforms);
  }

  private static void bind(
      List<IStream> infoStreams, int index, String name, List<TransformMeta> transforms) {
    if (index >= infoStreams.size()) {
      return;
    }
    IStream stream = infoStreams.get(index);
    stream.setSubject(name);
    stream.setTransformMeta(TransformMeta.findTransform(transforms, name));
  }

  @Override
  public void handleStreamSelection(IStream stream) {
    List<IStream> infoStreams = getTransformIOMeta().getInfoStreams();
    int index = infoStreams.indexOf(stream);
    if (index < 0) {
      return;
    }
    TransformMeta selected = stream.getTransformMeta();
    String name = selected == null ? "" : selected.getName();
    switch (index) {
      case 0 -> setParentsTransform(name);
      case 1 -> setChildrenTransform(name);
      case 2 -> setPairsLeftTransform(name);
      case 3 -> setPairsRightTransform(name);
      default -> {
        return;
      }
    }
    stream.setSubject(name);
  }
}
