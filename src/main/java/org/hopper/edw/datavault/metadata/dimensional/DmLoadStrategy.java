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

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.gui.plugin.GuiWidgetGroupType;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Source load strategy for one dimensional table. Absent metadata means Initial / Reload with Hop
 * variables and no extra pipeline transforms.
 */
@Getter
@Setter
@GuiPlugin(description = "Dimensional source load strategy")
public class DmLoadStrategy {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DmLoadStrategyDialog";

  public static final String DEFAULT_SENTINEL = "1900-01-01 00:00:00.000";
  public static final String DEFAULT_MODE_VARIABLE = "DM_LOAD_MODE";
  public static final String DEFAULT_FROM_VARIABLE = "DM_LOAD_FROM";
  public static final String DEFAULT_TO_VARIABLE = "DM_LOAD_TO";
  public static final String DEFAULT_JDBC_MODE = "DmLoadMode";
  public static final String DEFAULT_JDBC_FROM = "DmLoadFrom";
  public static final String DEFAULT_JDBC_TO = "DmLoadTo";

  private static final String GROUP_BASELINE = "i18n::DmLoadStrategy.Group.Baseline";
  private static final String GROUP_VALUES = "i18n::DmLoadStrategy.Group.Values";
  private static final String GROUP_LATE = "i18n::DmLoadStrategy.Group.LateArriving";

  @GuiWidgetElement(
      id = "strategy",
      order = "0100",
      type = GuiElementType.COMBO,
      label = "i18n::DmLoadStrategy.Strategy.Label",
      toolTip = "i18n::DmLoadStrategy.Strategy.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_BASELINE)
  @HopMetadataProperty(storeWithCode = true)
  private DmLoadStrategyType strategy = DmLoadStrategyType.INITIAL_RELOAD;

  @GuiWidgetElement(
      id = "baselineKind",
      order = "0200",
      type = GuiElementType.COMBO,
      label = "i18n::DmLoadStrategy.BaselineKind.Label",
      toolTip = "i18n::DmLoadStrategy.BaselineKind.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_BASELINE)
  @HopMetadataProperty(key = "baseline_kind", storeWithCode = true)
  private DmLoadBaselineKind baselineKind = DmLoadBaselineKind.TARGET_COLUMN;

  @GuiWidgetElement(
      id = "baselineColumn",
      order = "0300",
      type = GuiElementType.TEXT,
      label = "i18n::DmLoadStrategy.BaselineColumn.Label",
      toolTip = "i18n::DmLoadStrategy.BaselineColumn.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_BASELINE)
  @HopMetadataProperty(key = "baseline_column")
  private String baselineColumn;

  @GuiWidgetElement(
      id = "sentinelTimestamp",
      order = "0400",
      type = GuiElementType.TEXT,
      label = "i18n::DmLoadStrategy.Sentinel.Label",
      toolTip = "i18n::DmLoadStrategy.Sentinel.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_BASELINE)
  @HopMetadataProperty(key = "sentinel_timestamp")
  private String sentinelTimestamp = DEFAULT_SENTINEL;

  @GuiWidgetElement(
      id = "valueBinding",
      order = "1100",
      type = GuiElementType.COMBO,
      label = "i18n::DmLoadStrategy.ValueBinding.Label",
      toolTip = "i18n::DmLoadStrategy.ValueBinding.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_VALUES)
  @HopMetadataProperty(key = "value_binding", storeWithCode = true)
  private DmLoadValueBinding valueBinding = DmLoadValueBinding.VARIABLES;

  @GuiWidgetElement(
      id = "loadModeVariable",
      order = "1200",
      type = GuiElementType.TEXT,
      label = "i18n::DmLoadStrategy.LoadModeVariable.Label",
      toolTip = "i18n::DmLoadStrategy.LoadModeVariable.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_VALUES)
  @HopMetadataProperty(key = "load_mode_variable")
  private String loadModeVariable = DEFAULT_MODE_VARIABLE;

  @GuiWidgetElement(
      id = "loadFromVariable",
      order = "1300",
      type = GuiElementType.TEXT,
      label = "i18n::DmLoadStrategy.LoadFromVariable.Label",
      toolTip = "i18n::DmLoadStrategy.LoadFromVariable.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_VALUES)
  @HopMetadataProperty(key = "load_from_variable")
  private String loadFromVariable = DEFAULT_FROM_VARIABLE;

  @GuiWidgetElement(
      id = "loadToVariable",
      order = "1400",
      type = GuiElementType.TEXT,
      label = "i18n::DmLoadStrategy.LoadToVariable.Label",
      toolTip = "i18n::DmLoadStrategy.LoadToVariable.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_VALUES)
  @HopMetadataProperty(key = "load_to_variable")
  private String loadToVariable = DEFAULT_TO_VARIABLE;

  @GuiWidgetElement(
      id = "jdbcModeParameter",
      order = "1500",
      type = GuiElementType.TEXT,
      label = "i18n::DmLoadStrategy.JdbcModeParameter.Label",
      toolTip = "i18n::DmLoadStrategy.JdbcModeParameter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_VALUES)
  @HopMetadataProperty(key = "jdbc_mode_parameter")
  private String jdbcModeParameter = DEFAULT_JDBC_MODE;

  @GuiWidgetElement(
      id = "jdbcFromParameter",
      order = "1600",
      type = GuiElementType.TEXT,
      label = "i18n::DmLoadStrategy.JdbcFromParameter.Label",
      toolTip = "i18n::DmLoadStrategy.JdbcFromParameter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_VALUES)
  @HopMetadataProperty(key = "jdbc_from_parameter")
  private String jdbcFromParameter = DEFAULT_JDBC_FROM;

  @GuiWidgetElement(
      id = "jdbcToParameter",
      order = "1700",
      type = GuiElementType.TEXT,
      label = "i18n::DmLoadStrategy.JdbcToParameter.Label",
      toolTip = "i18n::DmLoadStrategy.JdbcToParameter.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_VALUES)
  @HopMetadataProperty(key = "jdbc_to_parameter")
  private String jdbcToParameter = DEFAULT_JDBC_TO;

  @GuiWidgetElement(
      id = "lateArrivingUnit",
      order = "2100",
      type = GuiElementType.COMBO,
      label = "i18n::DmLoadStrategy.LateArrivingUnit.Label",
      toolTip = "i18n::DmLoadStrategy.LateArrivingUnit.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_LATE)
  @HopMetadataProperty(key = "late_arriving_unit", storeWithCode = true)
  private DmLateArrivingUnit lateArrivingUnit = DmLateArrivingUnit.DAYS;

  @GuiWidgetElement(
      id = "lateArrivingCount",
      order = "2200",
      type = GuiElementType.TEXT,
      label = "i18n::DmLoadStrategy.LateArrivingCount.Label",
      toolTip = "i18n::DmLoadStrategy.LateArrivingCount.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID,
      groupType = GuiWidgetGroupType.BOXES,
      group = GROUP_LATE)
  @HopMetadataProperty(key = "late_arriving_count")
  private int lateArrivingCount;

  public DmLoadStrategyType resolveStrategy() {
    return strategy != null ? strategy : DmLoadStrategyType.INITIAL_RELOAD;
  }

  public DmLoadBaselineKind resolveBaselineKind() {
    return baselineKind != null ? baselineKind : DmLoadBaselineKind.TARGET_COLUMN;
  }

  public DmLoadValueBinding resolveValueBinding() {
    return valueBinding != null ? valueBinding : DmLoadValueBinding.VARIABLES;
  }

  public DmLateArrivingUnit resolveLateArrivingUnit() {
    return lateArrivingUnit != null ? lateArrivingUnit : DmLateArrivingUnit.DAYS;
  }

  public String resolveSentinel() {
    return Utils.isEmpty(sentinelTimestamp) ? DEFAULT_SENTINEL : sentinelTimestamp.trim();
  }

  public String resolveLoadModeVariable() {
    return defaultName(loadModeVariable, DEFAULT_MODE_VARIABLE);
  }

  public String resolveLoadFromVariable() {
    return defaultName(loadFromVariable, DEFAULT_FROM_VARIABLE);
  }

  public String resolveLoadToVariable() {
    return defaultName(loadToVariable, DEFAULT_TO_VARIABLE);
  }

  public String resolveJdbcModeParameter() {
    return defaultName(jdbcModeParameter, DEFAULT_JDBC_MODE);
  }

  public String resolveJdbcFromParameter() {
    return defaultName(jdbcFromParameter, DEFAULT_JDBC_FROM);
  }

  public String resolveJdbcToParameter() {
    return defaultName(jdbcToParameter, DEFAULT_JDBC_TO);
  }

  /** True when the stored element would not change today's pipelines and can be omitted. */
  public boolean isDefaultEquivalent() {
    return resolveStrategy() == DmLoadStrategyType.INITIAL_RELOAD
        && resolveBaselineKind() == DmLoadBaselineKind.TARGET_COLUMN
        && Utils.isEmpty(baselineColumn)
        && (Utils.isEmpty(sentinelTimestamp) || DEFAULT_SENTINEL.equals(sentinelTimestamp.trim()))
        && resolveValueBinding() == DmLoadValueBinding.VARIABLES
        && isDefaultName(loadModeVariable, DEFAULT_MODE_VARIABLE)
        && isDefaultName(loadFromVariable, DEFAULT_FROM_VARIABLE)
        && isDefaultName(loadToVariable, DEFAULT_TO_VARIABLE)
        && isDefaultName(jdbcModeParameter, DEFAULT_JDBC_MODE)
        && isDefaultName(jdbcFromParameter, DEFAULT_JDBC_FROM)
        && isDefaultName(jdbcToParameter, DEFAULT_JDBC_TO)
        && lateArrivingCount == 0
        && resolveLateArrivingUnit() == DmLateArrivingUnit.DAYS;
  }

  public DmLoadStrategy copy() {
    DmLoadStrategy copy = new DmLoadStrategy();
    copy.strategy = strategy;
    copy.baselineKind = baselineKind;
    copy.baselineColumn = baselineColumn;
    copy.sentinelTimestamp = sentinelTimestamp;
    copy.valueBinding = valueBinding;
    copy.loadModeVariable = loadModeVariable;
    copy.loadFromVariable = loadFromVariable;
    copy.loadToVariable = loadToVariable;
    copy.jdbcModeParameter = jdbcModeParameter;
    copy.jdbcFromParameter = jdbcFromParameter;
    copy.jdbcToParameter = jdbcToParameter;
    copy.lateArrivingUnit = lateArrivingUnit;
    copy.lateArrivingCount = lateArrivingCount;
    return copy;
  }

  private static String defaultName(String value, String fallback) {
    return Utils.isEmpty(value) ? fallback : value.trim();
  }

  private static boolean isDefaultName(String value, String fallback) {
    return Utils.isEmpty(value) || fallback.equals(value.trim());
  }
}
