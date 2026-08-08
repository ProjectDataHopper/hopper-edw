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
package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.base.IBaseMeta;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Condition;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.gui.IGuiPosition;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaBase;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.transform.dvhashkey.DvHashKeyMeta;
import org.apache.hop.datavault.transform.dvhashkey.DvHashKeyMetaFactory;
import org.apache.hop.datavault.transform.mergerowsplus.MergeRowsPlusMeta;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHasName;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.mergerows.PassThroughField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.uniquerowsbyhashset.UniqueRowsByHashSetMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.jspecify.annotations.NonNull;

/**
 * Data Vault 2.0 Link metadata definition.
 *
 * <p>A Link represents a relationship (many-to-many or association) between Hubs. The Link's hash
 * key is typically computed from the hash keys of the participating Hubs (plus any additional
 * descriptors for the relationship).
 */
@GuiPlugin
@Getter
@Setter
public class DvLink extends DvTableBase implements IDvTable, IGuiPosition, IBaseMeta, IHasName {
  private static final Class<?> PKG = DvLink.class;

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "DATAVAULT_LINK_DIALOG";

  private static final Point LOCATION_START_LINE_1 = new Point(160, 48);
  private static final Point LOCATION_START_LINE_2 = new Point(160, 160);
  private static final Point LOCATION_START_LINE_3 = new Point(160, 320);

  public static final int SPACING_WIDTH = 160;

  /**
   * Names of the Hubs that participate in this link (order can matter for hashing in some
   * implementations). These are references by metadata name (storeWithName behavior when used in a
   * model).
   */
  @HopMetadataProperty private List<String> hubNames = new ArrayList<>();

  /** Names of link satellites that describe this link (metadata references by name). */
  @HopMetadataProperty private List<String> linkSatelliteNames = new ArrayList<>();

  @HopMetadataProperty(key = "linkHubSource", groupKey = "linkHubSources")
  private List<DvLinkHubSource> linkHubSources = new ArrayList<>();

  @HopMetadataProperty(key = "linkSatelliteSource", groupKey = "linkSatelliteSources")
  private List<DvLinkSatelliteSource> linkSatelliteSources = new ArrayList<>();

  /**
   * Optional driving key(s) - used when the same Hub appears more than once in a link (e.g. "from
   * location" vs "to location" in a route). To know where these key names are sourced from, we need
   * to look in the source data.
   */
  @HopMetadataProperty private List<String> drivingKeyNames = new ArrayList<>();

  /**
   * Optional dependent child key(s) for transactional (non-historized) links. Values are stored on
   * the link table and included in the link hash so the same hub combination can appear more than
   * once (e.g. measurement timestamp, transaction id, order line number).
   */
  @HopMetadataProperty(key = "dependentChildKey", groupKey = "dependentChildKeys")
  private List<DependentChildKey> dependentChildKeys = new ArrayList<>();

  /**
   * Optional name for the link's surrogate hash key column. If empty, defaults to the link name +
   * "_LK" via {@link #resolveLinkHashKeyFieldName()}.
   */
  @GuiWidgetElement(
      order = "0650",
      type = GuiElementType.TEXT,
      label = "i18n::DvLink.LinkHashKeyFieldName.Label",
      toolTip = "i18n::DvLink.LinkHashKeyFieldName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String linkHashKeyFieldName;

  /**
   * Effective link hash key column name used for DDL and generated pipelines.
   *
   * <p>When {@link #linkHashKeyFieldName} is blank, defaults to {@code getName() + "_LK"}. Callers
   * that build SQL or transforms must use this method (or the variables overload) — never the raw
   * field alone, or pipeline generation fails at runtime while model check only logs a comment.
   */
  public String resolveLinkHashKeyFieldName() {
    if (!Utils.isEmpty(linkHashKeyFieldName)) {
      return linkHashKeyFieldName.trim();
    }
    if (!Utils.isEmpty(getName())) {
      return getName() + "_LK";
    }
    return "";
  }

  /** {@link #resolveLinkHashKeyFieldName()} with optional variable substitution. */
  public String resolveLinkHashKeyFieldName(IVariables variables) {
    String name = resolveLinkHashKeyFieldName();
    if (variables != null && !Utils.isEmpty(name)) {
      return variables.resolve(name);
    }
    return name;
  }

  /**
   * Per-hub source field mappings for computing the hub hashes from this link's record source. Used
   * when the link source uses different column names than the hub's business key names (e.g.
   * customer_fk instead of customer_id, or _id / _fk conventions).
   */
  @HopMetadataProperty(key = "hubSourceKeyField", groupKey = "hubSourceKeyFields")
  private List<HubSourceKeyField> hubSourceKeyFields = new ArrayList<>();

  /** Whether this link carries additional relationship attributes (i.e. has its own satellite). */
  @GuiWidgetElement(
      order = "0700",
      type = GuiElementType.CHECKBOX,
      label = "i18n::DvLink.HasDescriptiveAttributes.Label",
      toolTip = "i18n::DvLink.HasDescriptiveAttributes.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private boolean hasDescriptiveAttributes;

  /**
   * Optional per-link name for the record source column (overrides the global one from
   * DataVaultConfiguration). Supports variables.
   */
  @GuiWidgetElement(
      order = "0510",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::DvLink.RecordSourceFieldName.Label",
      toolTip = "i18n::DvLink.RecordSourceFieldName.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String recordSourceFieldName;

  public DvLink() {
    super();
    this.tableType = DvTableType.LINK;
  }

  public DvLink(String name) {
    super(name);
    this.tableType = DvTableType.LINK;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DvModelCheckOptions options,
      DataVaultModel model) {
    super.check(remarks, metadataProvider, variables, options, model);
    if (Utils.isEmpty(hubNames) || hubNames.size() < 2) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvLink.CheckResult.NotEnoughHubs"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvLink.CheckResult.ConnectedToHubs", hubNames.size()),
              this));
      // Distinct role hash columns when the same physical hub is role-played via aliases.
      Set<String> roleHashColumns = new LinkedHashSet<>();
      Map<String, String> columnToParticipant = new LinkedHashMap<>();
      for (String hubName : hubNames) {
        if (Utils.isEmpty(hubName)) {
          continue;
        }
        DvHub hub = model != null ? model.findHub(hubName, variables, metadataProvider) : null;
        if (hub == null) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(PKG, "DvLink.CheckResult.HubNotFound", hubName),
                  this));
          continue;
        }
        String roleColumn =
            DvTableResolutionSupport.resolveParticipatingHubHashColumn(
                model, hubName, variables, metadataProvider);
        if (Utils.isEmpty(roleColumn)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(PKG, "DvLink.CheckResult.MissingRoleHashColumn", hubName),
                  this));
          continue;
        }
        String normalized = variables != null ? variables.resolve(roleColumn) : roleColumn;
        if (columnToParticipant.containsKey(normalized.toLowerCase())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "DvLink.CheckResult.DuplicateRoleHashColumn",
                      normalized,
                      columnToParticipant.get(normalized.toLowerCase()),
                      hubName),
                  this));
        } else {
          columnToParticipant.put(normalized.toLowerCase(), hubName);
          roleHashColumns.add(normalized);
        }
      }
      if (roleHashColumns.size() >= 2) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                BaseMessages.getString(
                    PKG, "DvLink.CheckResult.DistinctRoleHashColumns", roleHashColumns.size()),
                this));
      }
    }

    String resolvedLinkHash = resolveLinkHashKeyFieldName(variables);
    if (Utils.isEmpty(resolvedLinkHash)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DvLink.CheckResult.MissingResolvableLinkHashKey"),
              this));
    } else if (Utils.isEmpty(linkHashKeyFieldName)) {
      // Blank stored name is valid only because resolveLinkHashKeyFieldName() defaults to name_LK.
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(
                  PKG, "DvLink.CheckResult.NoLinkHashKeyFieldName", resolvedLinkHash),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvLink.CheckResult.HasLinkHashKeyFieldName", resolvedLinkHash),
              this));
    }

    if (Utils.isEmpty(drivingKeyNames)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvLink.CheckResult.NoDrivingKeys"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvLink.CheckResult.HasDrivingKeys", drivingKeyNames.size()),
              this));
      for (String dk : drivingKeyNames) {
        if (Utils.isEmpty(dk)) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(PKG, "DvLink.CheckResult.DrivingKeyNoName"),
                  this));
        }
      }
    }

    if (Utils.isEmpty(dependentChildKeys)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvLink.CheckResult.NoDependentChildKeys"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvLink.CheckResult.HasDependentChildKeys", dependentChildKeys.size()),
              this));
      for (DependentChildKey dck : dependentChildKeys) {
        if (dck == null || Utils.isEmpty(dck.getName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(PKG, "DvLink.CheckResult.DependentChildKeyNoName"),
                  this));
        } else if (Utils.isEmpty(dck.resolveSourceFieldName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG, "DvLink.CheckResult.DependentChildKeyNoSourceField", dck.getName()),
                  this));
        }
      }
    }

    if (hasDescriptiveAttributes) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_COMMENT,
              BaseMessages.getString(PKG, "DvLink.CheckResult.HasDescriptiveAttributes"),
              this));
    }

    if (linkSatelliteNames != null && !linkSatelliteNames.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvLink.CheckResult.HasLinkSatellites", linkSatelliteNames.size()),
              this));
    }

    // Hub record source validation
    if (!DvIntegrationSupport.relaxesSourceValidation(this)
        && !DvIntegrationSupport.isCustomPipelines(this)) {
      if (linkHubSources == null || linkHubSources.isEmpty()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(PKG, "DvLink.CheckResult.NoLinkHubSources"),
                this));
      } else {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                BaseMessages.getString(
                    PKG, "DvLink.CheckResult.HasLinkHubSources", linkHubSources.size()),
                this));
        for (DvLinkHubSource ls : linkHubSources) {
          if (ls == null || Utils.isEmpty(ls.getSourceName())) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    BaseMessages.getString(PKG, "DvLink.CheckResult.LinkHubSourceNoName"),
                    this));
          }
        }
      }
    }
    if (linkHubSources != null && !linkHubSources.isEmpty()) {
      for (DvLinkHubSource ls : linkHubSources) {
        if (ls != null && !Utils.isEmpty(ls.getSourceName())) {
          DataVaultSource source;
          List<SourceField> availableSourceFields;
          try {
            source = ls.resolveSource(variables, metadataProvider, model);
            availableSourceFields = source.getFields(metadataProvider);
          } catch (HopException e) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    "Error loading fields from record source '"
                        + ls.getSourceName()
                        + "': "
                        + e.getMessage(),
                    this));
            continue;
          }
          if (availableSourceFields == null) {
            availableSourceFields = new ArrayList<>();
          }
          // loop over the available hubSourceKeyFields and check the source fields
          if (ls.getHubSourceKeyFields() != null) {
            for (HubSourceKeyField hkf : ls.getHubSourceKeyFields()) {
              if (hkf == null) continue;
              // check sourceBusinessKeyFields
              if (hkf.getSourceBusinessKeyFields() != null) {
                for (BusinessKeySource bks : hkf.getSourceBusinessKeyFields()) {
                  if (bks != null && !Utils.isEmpty(bks.getSourceFieldName())) {
                    boolean found = false;
                    for (SourceField sf : availableSourceFields) {
                      if (bks.getSourceFieldName().equals(sf.getName())) {
                        found = true;
                        break;
                      }
                    }
                    if (!found) {
                      remarks.add(
                          new CheckResult(
                              ICheckResult.TYPE_RESULT_ERROR,
                              "Source field '"
                                  + bks.getSourceFieldName()
                                  + "' (for business key '"
                                  + bks.getBusinessKeyField()
                                  + "') in hub '"
                                  + hkf.getHubName()
                                  + "' not available in record source '"
                                  + source.getName()
                                  + "'",
                              this));
                    }
                  }
                }
              }
              // check drivingKeySources
              if (hkf.getDrivingKeySources() != null) {
                for (DrivingKeySource dks : hkf.getDrivingKeySources()) {
                  if (dks != null && !Utils.isEmpty(dks.getSourceField())) {
                    boolean found = false;
                    for (SourceField sf : availableSourceFields) {
                      if (dks.getSourceField().equals(sf.getName())) {
                        found = true;
                        break;
                      }
                    }
                    if (!found) {
                      remarks.add(
                          new CheckResult(
                              ICheckResult.TYPE_RESULT_ERROR,
                              "Source field '"
                                  + dks.getSourceField()
                                  + "' (for driving key '"
                                  + dks.getDrivingKey()
                                  + "') in hub '"
                                  + hkf.getHubName()
                                  + "' not available in record source '"
                                  + source.getName()
                                  + "'",
                              this));
                    }
                  }
                }
              }
            }
          }
          // Dependent child keys are link-level; verify their source fields exist on this record
          // source.
          for (DependentChildKey dck : listDependentChildKeys()) {
            if (dck == null) {
              continue;
            }
            String dckSource = dck.resolveSourceFieldName();
            if (Utils.isEmpty(dckSource)) {
              continue;
            }
            boolean found = false;
            for (SourceField sf : availableSourceFields) {
              if (dckSource.equals(sf.getName())) {
                found = true;
                break;
              }
            }
            if (!found) {
              remarks.add(
                  new CheckResult(
                      ICheckResult.TYPE_RESULT_ERROR,
                      "Source field '"
                          + dckSource
                          + "' (for dependent child key '"
                          + dck.getName()
                          + "') not available in record source '"
                          + source.getName()
                          + "'",
                      this));
            }
          }
        }
      }
    }

    if (linkSatelliteSources != null && !linkSatelliteSources.isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvLink.CheckResult.HasLinkSatelliteSources", linkSatelliteSources.size()),
              this));
      for (DvLinkSatelliteSource ls : linkSatelliteSources) {
        if (ls != null && !Utils.isEmpty(ls.getSourceName())) {
          DataVaultSource source;
          List<SourceField> availableSourceFields;
          try {
            source = ls.resolveSource(variables, metadataProvider, model);
            availableSourceFields = source.getFields(metadataProvider);
          } catch (HopException e) {
            remarks.add(
                new CheckResult(
                    ICheckResult.TYPE_RESULT_ERROR,
                    "Error loading fields from record source '"
                        + ls.getSourceName()
                        + "': "
                        + e.getMessage(),
                    this));
            continue;
          }
          if (availableSourceFields == null) {
            availableSourceFields = new ArrayList<>();
          }
          if (ls.getSatelliteSourceKeyFields() != null) {
            for (SatelliteSourceKeyField skf : ls.getSatelliteSourceKeyFields()) {
              if (skf == null) continue;
              if (skf.getAttributeSources() != null) {
                for (AttributeSource as : skf.getAttributeSources()) {
                  if (as != null && !Utils.isEmpty(as.getSourceFieldName())) {
                    boolean found = false;
                    for (SourceField sf : availableSourceFields) {
                      if (as.getSourceFieldName().equals(sf.getName())) {
                        found = true;
                        break;
                      }
                    }
                    if (!found) {
                      remarks.add(
                          new CheckResult(
                              ICheckResult.TYPE_RESULT_ERROR,
                              "Source field '"
                                  + as.getSourceFieldName()
                                  + "' (for attribute '"
                                  + as.getAttributeField()
                                  + "') in link satellite '"
                                  + skf.getSatelliteName()
                                  + "' not available in record source '"
                                  + source.getName()
                                  + "'",
                              this));
                    }
                  }
                }
              }
              if (skf.getDrivingKeySources() != null) {
                for (DrivingKeySource dks : skf.getDrivingKeySources()) {
                  if (dks != null && !Utils.isEmpty(dks.getSourceField())) {
                    boolean found = false;
                    for (SourceField sf : availableSourceFields) {
                      if (dks.getSourceField().equals(sf.getName())) {
                        found = true;
                        break;
                      }
                    }
                    if (!found) {
                      remarks.add(
                          new CheckResult(
                              ICheckResult.TYPE_RESULT_ERROR,
                              "Source field '"
                                  + dks.getSourceField()
                                  + "' (for driving key '"
                                  + dks.getDrivingKey()
                                  + "') in link satellite '"
                                  + skf.getSatelliteName()
                                  + "' not available in record source '"
                                  + source.getName()
                                  + "'",
                              this));
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    if (Utils.isEmpty(recordSourceFieldName)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(PKG, "DvLink.CheckResult.NoRecordSourceFieldName"),
              this));
    } else {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG, "DvLink.CheckResult.HasRecordSourceFieldName", recordSourceFieldName),
              this));
    }

    if (!DvIntegrationSupport.relaxesSourceValidation(this)
        && metadataProvider != null
        && options != null
        && model != null) {
      DvFieldMappingValidationSupport.validateLinkHubKeyFields(
          this, model, options, metadataProvider, variables, this, remarks);
      DvFieldMappingValidationSupport.validateLinkRecordSourceFields(
          this, model, options, metadataProvider, variables, this, remarks);
    }
  }

  @Override
  public List<PipelineMeta> generateUpdatePipelines(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      String recordSourceGroup)
      throws HopException {
    try {
      if (metadataProvider == null || model == null) {
        return Collections.emptyList();
      }

      if (DvIntegrationSupport.isExternalRead(this)) {
        return Collections.emptyList();
      }
      if (DvIntegrationSupport.isCustomPipelines(this)) {
        return DvIntegrationSupport.loadCustomUpdatePipelines(this, metadataProvider, variables);
      }

      if (linkHubSources == null || linkHubSources.isEmpty()) {
        if (!Utils.isEmpty(recordSourceGroup)) {
          return Collections.emptyList();
        }
        throw new HopException(
            BaseMessages.getString(PKG, "DvLink.Error.NoLinkHubSources", getName()));
      }

      List<PipelineMeta> result = new ArrayList<>();

      for (DvLinkHubSource linkSource : linkHubSources) {
        DataVaultSource source = linkSource.resolveSource(variables, metadataProvider, model);
        if (source != null && !source.matchesRecordSourceGroup(recordSourceGroup, variables)) {
          continue;
        }

        LinkUpdateContext ctx =
            LinkUpdateContext.create(metadataProvider, variables, model, this, linkSource);

        PipelineMeta pipelineMeta = new PipelineMeta();
        String baseName = ctx.pipelineName;
        String srcName = linkSource.getSourceName();
        pipelineMeta.setName(baseName + "_" + srcName);
        GeneratedPipelineMetadataSupport.stampDvLinkPipeline(
            pipelineMeta, model, this, ctx.targetTableName, srcName);

        TransformMeta sourceInputTransform = addSourceTableInput(ctx, pipelineMeta, linkSource);
        if (sourceInputTransform != null) {
          String sourceConnection =
              ctx.dvSource
                      instanceof
                      org.apache.hop.datavault.metadata.database.DvDatabaseSource dbSource
                  ? dbSource.getDatabaseName()
                  : null;
          GeneratedPipelineMetadataSupport.stampSourceRead(sourceInputTransform, sourceConnection);
        }
        TransformMeta predecessorTransform = sourceInputTransform;

        // Compute hub hashes for each participating hub/alias (from their BKs in the source).
        // Role aliases use distinct output field names so the same physical hub can appear twice.
        List<String> hubHashNames = new ArrayList<>();
        int index = 0;
        for (String hubName : hubNames) {
          DvHub hub = model.findHub(hubName, variables, metadataProvider);
          if (hub == null) {
            throw new HopException(
                "Unable to find hub '" + hubName + "' in the model for Link table " + getName());
          }

          String hubHashName =
              DvTableResolutionSupport.resolveParticipatingHubHashColumn(
                  model, hubName, variables, metadataProvider);
          if (Utils.isEmpty(hubHashName)) {
            throw new HopException(
                "Unable to resolve hash key column for participating hub '"
                    + hubName
                    + "' on link "
                    + getName());
          }
          hubHashName = variables.resolve(hubHashName);
          hubHashNames.add(hubHashName);
          List<String> hubBkFields =
              resolveHubSourceBusinessKeyFields(linkSource, hubName, hub, variables);
          TransformMeta checkSumTransform =
              addDvHashKeyForFields(
                  pipelineMeta,
                  predecessorTransform,
                  hubBkFields,
                  hubHashName,
                  ctx.config,
                  index++);
          pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessorTransform, checkSumTransform));
          predecessorTransform = checkSumTransform;
        }

        // Final checksum for the Link Hash itself: hub hashes + dependent child key values
        // (source field names still in the stream at this point).
        List<String> linkHashInputFields = new ArrayList<>(hubHashNames);
        linkHashInputFields.addAll(resolveDependentChildSourceFieldNames(ctx.variables));
        String linkHashKey = resolveLinkHashKeyFieldName(variables);
        if (Utils.isEmpty(linkHashKey)) {
          throw new HopException(
              BaseMessages.getString(PKG, "DvLink.Error.MissingLinkHashKeyFieldName", getName()));
        }

        TransformMeta linkHashCalc =
            addDvHashKeyForFields(
                pipelineMeta,
                predecessorTransform,
                linkHashInputFields,
                linkHashKey,
                ctx.config,
                index);
        pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessorTransform, linkHashCalc));
        predecessorTransform = linkHashCalc;
        if (linkHashCalc != null) {
          GeneratedPipelineMetadataSupport.stampHashKey(
              linkHashCalc, "link", getName(), ctx.targetTableName);
        }

        TransformMeta selectTransform =
            addSourceSelectRows(ctx, pipelineMeta, predecessorTransform, linkHashKey);

        // Target side: prefer SQL ORDER BY with a hop-compatible binary/C collation when we are
        // certain it matches Hop SortRows (STRING/HEX). Linguistic DB collations can disagree on
        // decimal-dash keys (e.g. "0-100-..." vs "0-10-...") and desynchronize MergeRows → PK
        // violations. When uncertain, SortRows the target leg. Source always SortRows (hash in
        // Hop).
        DvHashKeyOrderStrategySupport.TargetHashOrderPlan targetOrderPlan =
            resolveTargetHashOrderPlan(ctx, linkHashKey);
        TransformMeta targetInputTransform =
            addTargetTableInput(ctx, pipelineMeta, targetOrderPlan, linkHashKey);
        if (targetInputTransform != null) {
          GeneratedPipelineMetadataSupport.stampTargetRead(
              targetInputTransform, "link", getName(), ctx.targetTableName, ctx.targetDbName);
        }
        TransformMeta targetMergeInput = targetInputTransform;
        if (targetOrderPlan.useHopSortRows()) {
          targetMergeInput =
              addSortRows(
                  ctx,
                  pipelineMeta,
                  targetInputTransform,
                  linkHashKey,
                  "sort_target_" + linkHashKey,
                  LOCATION_START_LINE_3.x + SPACING_WIDTH,
                  LOCATION_START_LINE_3.y);
        }

        TransformMeta sortTransform =
            addSortRows(
                ctx,
                pipelineMeta,
                selectTransform,
                linkHashKey,
                "sort_" + linkHashKey,
                LOCATION_START_LINE_2.x + SPACING_WIDTH * (hubNames.size() + 3),
                LOCATION_START_LINE_2.y);
        if (sortTransform != null) {
          GeneratedPipelineMetadataSupport.stampSort(
              sortTransform, "link", getName(), ctx.targetTableName);
        }

        // Collapse identical link hashes before CDC merge (source SQL DISTINCT covers DB sources;
        // this also covers file sources and any post-hash duplicates).
        TransformMeta uniqueTransform =
            addUniqueLinkHashRows(pipelineMeta, sortTransform, linkHashKey);
        TransformMeta mergeInput = uniqueTransform != null ? uniqueTransform : sortTransform;

        TransformMeta mergeTransform =
            addMergeRows(
                ctx,
                pipelineMeta,
                mergeInput,
                targetMergeInput != null ? targetMergeInput : targetInputTransform,
                linkHashKey);
        if (mergeTransform != null) {
          GeneratedPipelineMetadataSupport.stampCdcMerge(
              mergeTransform, "link", getName(), ctx.targetTableName, ctx.targetDbName);
        }

        TransformMeta filterTransform = addFilterNewRows(pipelineMeta, mergeTransform);
        if (filterTransform != null) {
          GeneratedPipelineMetadataSupport.stampFilter(
              filterTransform, "link", getName(), ctx.targetTableName);
        }
        TransformMeta constantTransform =
            addConstantForLoadDate(ctx, pipelineMeta, loadDate, filterTransform);
        constantTransform = addConstantForLoadCycleId(ctx, pipelineMeta, constantTransform);

        IRowMeta targetLayout = getTargetTableLayout(metadataProvider, variables, model);

        DvTargetLoadSupport.TargetLoadResult writeResult =
            DvTargetLoadSupport.addTargetLoad(
                buildTargetLoadContext(ctx, pipelineMeta.getName()),
                pipelineMeta,
                targetLayout,
                constantTransform,
                Set.of("flag"));
        if (writeResult != null && writeResult.transformMeta != null) {
          GeneratedPipelineMetadataSupport.stampWriteTarget(
              writeResult.transformMeta, "link", getName(), ctx.targetTableName, ctx.targetDbName);
        }

        result.add(pipelineMeta);
      }

      if (result.isEmpty() && Utils.isEmpty(recordSourceGroup)) {
        throw new HopException(
            BaseMessages.getString(PKG, "DvLink.Error.NoLinkHubSources", getName()));
      }

      DvGeneratedPipelineSupport.applyLayout(result);
      return result;
    } catch (Exception e) {
      throw new HopException("Error generating update pipeline for Link target " + getName(), e);
    }
  }

  @Override
  public List<WorkflowMeta> generateUpdateWorkflows(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      String recordSourceGroup)
      throws HopException {
    List<PipelineMeta> pipelines =
        generateUpdatePipelines(metadataProvider, variables, model, loadDate, recordSourceGroup);
    if (pipelines == null || pipelines.size() <= 1) {
      return List.of();
    }
    DataVaultConfiguration config =
        model != null ? model.getConfigurationOrDefault() : new DataVaultConfiguration();
    String prefix =
        config != null && !Utils.isEmpty(config.getLinkPipelineNamePrefix())
            ? config.getLinkPipelineNamePrefix()
            : DataVaultConfiguration.DEFAULT_LINK_PIPELINE_NAME_PREFIX;
    String workflowName = DvMultiSourceUpdateWorkflowSupport.defaultWorkflowName(this, prefix);
    return DvMultiSourceUpdateWorkflowSupport.buildSerialWorkflowsIfMultiSource(
        workflowName, pipelines);
  }

  @Override
  public IRowMeta getTargetTableLayout(
      IHopMetadataProvider metadataProvider, IVariables variables, DataVaultModel model)
      throws HopException {
    if (metadataProvider == null || model == null) {
      return null;
    }

    IRowMeta rowMeta = new RowMeta();

    try {
      DataVaultConfiguration config = model.getConfigurationOrDefault();

      // 1. The link's own hash key (LHK)
      String linkHashName = resolveLinkHashKeyFieldName(variables);
      if (Utils.isEmpty(linkHashName)) {
        throw new HopException(
            BaseMessages.getString(PKG, "DvLink.Error.MissingLinkHashKeyFieldName", getName()));
      }
      HashKeyDataType hdt = config.resolveHashKeyDataType();
      HashAlgorithm algo = config.resolveHashAlgorithm();
      if (algo == null) algo = HashAlgorithm.MD5;
      int digestBytes = algo.getDigestLength();

      IValueMeta linkHashMeta;
      if (hdt == HashKeyDataType.BINARY) {
        linkHashMeta = new ValueMetaBinary(linkHashName);
        linkHashMeta.setLength(digestBytes);
      } else if (hdt == HashKeyDataType.HEX) {
        linkHashMeta = new ValueMetaString(linkHashName);
        linkHashMeta.setLength(digestBytes * 2);
      } else {
        int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
        linkHashMeta = new ValueMetaString(linkHashName);
        linkHashMeta.setLength(stringMax);
      }
      rowMeta.addValueMeta(linkHashMeta);

      // 2. The participating hub hash keys (in the order defined in the link).
      // Hub aliases may contribute role-specific column names (same physical hub twice).
      for (String hubName : hubNames) {
        DvHub hub = model.findHub(hubName, variables, metadataProvider);
        if (hub == null) {
          throw new HopException("Linked hub not found: " + hubName + " for link " + getName());
        }
        String hubHashCol =
            DvTableResolutionSupport.resolveParticipatingHubHashColumn(
                model, hubName, variables, metadataProvider);
        if (Utils.isEmpty(hubHashCol)) {
          throw new HopException(
              "Unable to resolve hash key column for participating hub '"
                  + hubName
                  + "' on link "
                  + getName());
        }
        // Same type/length as a normal hub hash
        IValueMeta hubHashMeta;
        if (hdt == HashKeyDataType.BINARY) {
          hubHashMeta = new ValueMetaBinary(hubHashCol);
          hubHashMeta.setLength(digestBytes);
        } else if (hdt == HashKeyDataType.HEX) {
          hubHashMeta = new ValueMetaString(hubHashCol);
          hubHashMeta.setLength(digestBytes * 2);
        } else {
          int stringMax = digestBytes * 3 + (digestBytes > 0 ? digestBytes - 1 : 0);
          hubHashMeta = new ValueMetaString(hubHashCol);
          hubHashMeta.setLength(stringMax);
        }
        rowMeta.addValueMeta(hubHashMeta);
      }

      // 3. Dependent child keys (transactional link identity columns)
      for (DependentChildKey dck : listDependentChildKeys()) {
        rowMeta.addValueMeta(createValueMetaFromDependentChildKey(dck, variables));
      }

      // 4. Record source (per-link name if specified, else from config; supports variables)
      String rsFieldName =
          DvSourceFieldMappingSupport.resolveRecordSourceFieldName(config, this, variables);
      String lengthString =
          !Utils.isEmpty(config.getRecordSourceFieldLength())
              ? config.getRecordSourceFieldLength()
              : "100";
      lengthString = variables.resolve(lengthString);
      int rsLength = Const.toInt(lengthString, 100);
      IValueMeta rsMeta = new ValueMetaString(rsFieldName);
      rsMeta.setLength(rsLength);
      rowMeta.addValueMeta(rsMeta);

      // 5. Load date
      String loadDateField = config.getLoadDateField();
      if (Utils.isEmpty(loadDateField)) loadDateField = "LOAD_DATE";
      IValueMeta loadMeta = new ValueMetaTimestamp(loadDateField);
      rowMeta.addValueMeta(loadMeta);

      DvLoadCycleSupport.appendToLayout(
          rowMeta, config.isStoreLoadCycleId(), config.getLoadCycleIdField(), variables);

      return rowMeta;
    } catch (Exception e) {
      throw new HopException("Error building target table layout for link " + getName(), e);
    }
  }

  // --- Helper methods and context (modeled after DvSatellite) ---

  private TransformMeta addSourceTableInput(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta, DvLinkHubSource linkSource)
      throws HopException {

    DvSourcePipelineBuilder builder =
        DvSourcePipelineBuilderFactory.forLink(
            ctx.variables,
            ctx.metadataProvider,
            ctx.model,
            pipelineMeta,
            ctx.dataVaultSource,
            ctx.dvSource,
            this,
            new Point(LOCATION_START_LINE_2.x, LOCATION_START_LINE_2.y));
    if (builder instanceof DvDatabaseLinkSourcePipelineBuilder dbBuilder) {
      dbBuilder.setDvLinkHubSource(linkSource);
    } else if (builder
        instanceof
        org.apache.hop.datavault.metadata.file.DvCsvLinkSourcePipelineBuilder csvBuilder) {
      csvBuilder.setDvLinkHubSource(linkSource);
    }
    builder.build();
    return builder.getResultTransform();
  }

  private TransformMeta addDvHashKeyForFields(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<String> inputFieldNames,
      String resultFieldName,
      DataVaultConfiguration config,
      int index) {
    DvHashKeyMeta hashKeyMeta =
        DvHashKeyMetaFactory.create(config, inputFieldNames, resultFieldName);

    TransformMeta tm = new TransformMeta("DvHashKey", "calc_" + resultFieldName, hashKeyMeta);
    // Place progressively to the right
    Point loc =
        new Point(LOCATION_START_LINE_2.x + (index + 1) * SPACING_WIDTH, LOCATION_START_LINE_2.y);
    tm.setLocation(loc);
    pipelineMeta.addTransform(tm);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));

    return tm;
  }

  private TransformMeta addSourceSelectRows(
      LinkUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      String linkHashKey)
      throws HopException {

    SelectValuesMeta selectMeta = new SelectValuesMeta();
    List<SelectField> selectFields = selectMeta.getSelectOption().getSelectFields();

    // Select the link table hash key (always use the resolved name; blank stored values default
    // to name_LK via resolveLinkHashKeyFieldName).
    //
    String hashKeyField =
        !Utils.isEmpty(linkHashKey) ? linkHashKey : resolveLinkHashKeyFieldName(ctx.variables);
    if (StringUtils.isNotEmpty(hashKeyField)) {
      SelectField selectField = new SelectField();
      selectField.setName(hashKeyField);
      selectFields.add(selectField);
    } else {
      throw new HopException(
          BaseMessages.getString(PKG, "DvLink.Error.MissingLinkHashKeyFieldName", getName()));
    }

    // We want to keep only the hash keys from the hubs/aliases and the one from the link itself.
    //
    for (String hubName : hubNames) {
      String hubHashCol =
          DvTableResolutionSupport.resolveParticipatingHubHashColumn(
              ctx.model, hubName, ctx.variables, ctx.metadataProvider);
      if (Utils.isEmpty(hubHashCol)) {
        throw new HopException(
            "Unable to resolve hash key column for participating hub '"
                + hubName
                + "' on link "
                + getName());
      }
      SelectField selectField = new SelectField();
      selectField.setName(ctx.variables.resolve(hubHashCol));
      selectFields.add(selectField);
    }

    // The driving keys need to be renamed to their target table name
    //
    for (String drivingKeyName : drivingKeyNames) {
      // Look up the source name for the record source in the context.
      String drivingKeySourceField = findSourceFieldOfDrivingKey(ctx, drivingKeyName);
      if (drivingKeySourceField == null) {
        throw new HopException(
            "Unable to find a source field for driving key "
                + drivingKeyName
                + " in Link "
                + getName()
                + " when building a pipeline to handle source "
                + ctx.dataVaultSource.getName());
      }
      SelectField selectField = new SelectField();
      selectField.setName(drivingKeySourceField);
      selectField.setRename(ctx.variables.resolve(drivingKeyName));
      selectFields.add(selectField);
    }

    // Dependent child keys: rename source field → target column name for storage
    //
    for (DependentChildKey dck : listDependentChildKeys()) {
      String sourceField = ctx.variables.resolve(Const.NVL(dck.resolveSourceFieldName(), ""));
      String targetField = ctx.variables.resolve(Const.NVL(dck.getName(), ""));
      if (Utils.isEmpty(sourceField) || Utils.isEmpty(targetField)) {
        throw new HopException(
            "Dependent child key on link "
                + getName()
                + " is missing a name or source field when building a pipeline for source "
                + ctx.dataVaultSource.getName());
      }
      SelectField selectField = new SelectField();
      selectField.setName(sourceField);
      if (!sourceField.equals(targetField)) {
        selectField.setRename(targetField);
      }
      selectFields.add(selectField);
    }

    // Also keep the record source
    //
    SelectField sourceField = new SelectField();
    sourceField.setName(findRecordSourceFieldName(ctx));
    selectFields.add(sourceField);

    TransformMeta tm = new TransformMeta("SelectValues", "select_values", selectMeta);
    // Place it on the compare flow after the last checksum, before the sort
    Point loc =
        new Point(
            LOCATION_START_LINE_2.x + SPACING_WIDTH * (hubNames.size() + 2),
            LOCATION_START_LINE_2.y);
    tm.setLocation(loc);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));

    return tm;
  }

  private String findSourceFieldOfDrivingKey(LinkUpdateContext ctx, String drivingKeyName) {
    for (DvLinkHubSource linkSource : linkHubSources) {
      if (Const.NVL(linkSource.getSourceName(), "")
          .equalsIgnoreCase(Const.NVL(ctx.dataVaultSource.getName(), ""))) {
        // This is the source we're loading in this pipeline
        // See if we have the source for the driving key.
        for (HubSourceKeyField hubSourceKeyField : linkSource.hubSourceKeyFields) {
          for (DrivingKeySource drivingKeySource : hubSourceKeyField.getDrivingKeySources()) {
            if (drivingKeyName.equalsIgnoreCase(drivingKeySource.getDrivingKey())) {
              return drivingKeySource.getSourceField();
            }
          }
        }
      }
    }
    return null;
  }

  /** Non-null list of configured dependent child keys (empty when none). */
  private List<DependentChildKey> listDependentChildKeys() {
    if (dependentChildKeys == null || dependentChildKeys.isEmpty()) {
      return Collections.emptyList();
    }
    return dependentChildKeys;
  }

  /**
   * Source field names for dependent child keys, in model order. Used when loading the link and
   * when loading link satellites so both compute the same link hash (hub hashes + DCK values).
   */
  public List<String> resolveDependentChildSourceFieldNames(IVariables variables)
      throws HopException {
    List<String> names = new ArrayList<>();
    for (DependentChildKey dck : listDependentChildKeys()) {
      if (dck == null) {
        continue;
      }
      String sourceField =
          variables != null
              ? variables.resolve(Const.NVL(dck.resolveSourceFieldName(), ""))
              : Const.NVL(dck.resolveSourceFieldName(), "");
      if (Utils.isEmpty(sourceField)) {
        throw new HopException(
            "Dependent child key '"
                + dck.getName()
                + "' on link "
                + getName()
                + " has no source field name");
      }
      names.add(sourceField);
    }
    return names;
  }

  private IValueMeta createValueMetaFromDependentChildKey(
      DependentChildKey dck, IVariables variables) throws HopException {
    String name = variables.resolve(Const.NVL(dck.getName(), ""));
    if (Utils.isEmpty(name)) {
      throw new HopException(
          "Dependent child key on link " + getName() + " has no target column name");
    }
    String dt = dck.getDataType();
    if (!Utils.isEmpty(dt)) {
      dt = variables.resolve(dt);
    }
    int typeId = IValueMeta.TYPE_STRING;
    if (!Utils.isEmpty(dt)) {
      typeId = ValueMetaFactory.getIdForValueMeta(dt);
      if (typeId <= 0) {
        typeId = IValueMeta.TYPE_STRING;
      }
    }
    try {
      IValueMeta vm = ValueMetaFactory.createValueMeta(name, typeId);
      String lengthStr = dck.getLength();
      if (!Utils.isEmpty(lengthStr)) {
        lengthStr = variables.resolve(lengthStr);
      }
      String precisionStr = dck.getPrecision();
      if (!Utils.isEmpty(precisionStr)) {
        precisionStr = variables.resolve(precisionStr);
      }
      vm.setLength(Const.toInt(lengthStr, -1));
      vm.setPrecision(Const.toInt(precisionStr, -1));
      return vm;
    } catch (org.apache.hop.core.exception.HopPluginException e) {
      throw new HopException(
          "Error creating value meta for dependent child key " + name + " on link " + getName(), e);
    }
  }

  private List<String> resolveHubSourceBusinessKeyFields(
      DvLinkHubSource linkSource, String hubName, DvHub hub, IVariables variables)
      throws HopException {
    return DvLinkHubSourceKeyFieldSupport.resolveSourceFieldNames(
        linkSource, hubName, hub, variables);
  }

  private DvHashKeyOrderStrategySupport.TargetHashOrderPlan resolveTargetHashOrderPlan(
      LinkUpdateContext ctx, String hashFieldName) {
    String quoted =
        ctx.targetDatabaseMeta != null
            ? ctx.targetDatabaseMeta.quoteField(hashFieldName)
            : hashFieldName;
    return DvHashKeyOrderStrategySupport.resolve(
        ctx.targetDatabaseMeta, ctx.config, ctx.variables, quoted, ctx.hashOrderSession, true);
  }

  private TransformMeta addTargetTableInput(
      LinkUpdateContext ctx,
      PipelineMeta pipelineMeta,
      DvHashKeyOrderStrategySupport.TargetHashOrderPlan orderPlan,
      String linkHashKey)
      throws HopException {
    if (ctx.targetDatabaseMeta == null) {
      return null;
    }

    TableInputMeta targetTableInputMeta = new TableInputMeta();
    targetTableInputMeta.setConnection(ctx.targetDbName);

    String hashKeyField =
        !Utils.isEmpty(linkHashKey) ? linkHashKey : resolveLinkHashKeyFieldName(ctx.variables);
    if (Utils.isEmpty(hashKeyField)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvLink.Error.MissingLinkHashKeyFieldName", getName()));
    }
    String quotedLinkHash = ctx.targetDatabaseMeta.quoteField(hashKeyField);

    // DISTINCT so CDC merge never sees duplicate LHKs from a dirty target (pre-PK loads).
    StringBuilder sql = new StringBuilder("SELECT DISTINCT ");
    sql.append(quotedLinkHash);

    // Also select the hub hash columns that are stored in the link (for completeness / potential
    // value compare). Role aliases contribute their role column names.
    //
    for (String hubName : hubNames) {
      String hubHashCol =
          DvTableResolutionSupport.resolveParticipatingHubHashColumn(
              ctx.model, hubName, ctx.variables, ctx.metadataProvider);
      if (Utils.isEmpty(hubHashCol)) {
        throw new HopException(
            "Unable to resolve hash key column for participating hub '"
                + hubName
                + "' on link "
                + getName());
      }
      sql.append(", ").append(ctx.targetDatabaseMeta.quoteField(ctx.variables.resolve(hubHashCol)));
    }

    // The name of the source is described in the Link itself
    //
    String rsFieldName = findRecordSourceFieldName(ctx);
    sql.append(", ")
        .append(DvSqlSupport.typedNullString(ctx.targetDatabaseMeta, ctx.variables, ctx.config))
        .append(" AS ")
        .append(ctx.targetDatabaseMeta.quoteField(rsFieldName));

    sql.append(" FROM ");
    sql.append(
        ctx.targetDatabaseMeta.getQuotedSchemaTableCombination(
            ctx.variables, null, ctx.targetTableName));

    // SQL ORDER BY with hop-compatible COLLATE when certain; else unordered + Hop SortRows.
    DvHashKeyOrderStrategySupport.TargetHashOrderPlan plan =
        orderPlan != null ? orderPlan : DvHashKeyOrderStrategySupport.hopSortFallback();
    String finalSql = DvHashKeyOrderStrategySupport.applyToDistinctSelect(sql.toString(), plan);

    DvSqlSupport.assignDisplaySql(targetTableInputMeta, finalSql);

    String transformName = "target " + ctx.targetDatabaseMeta.getName() + "." + ctx.targetTableName;

    TransformMeta tm = new TransformMeta("TableInput", transformName, targetTableInputMeta);
    tm.setLocation(LOCATION_START_LINE_3);
    pipelineMeta.addTransform(tm);

    return tm;
  }

  private @NonNull String findRecordSourceFieldName(LinkUpdateContext ctx) throws HopException {
    return DvSourceFieldMappingSupport.resolveRecordSourceFieldName(
        ctx.config, this, ctx.variables);
  }

  private TransformMeta addMergeRows(
      LinkUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta compareTransform,
      TransformMeta referenceTransform,
      String linkHashKey)
      throws HopException {
    if (referenceTransform == null || compareTransform == null) {
      return null;
    }

    String hashKeyField =
        !Utils.isEmpty(linkHashKey) ? linkHashKey : resolveLinkHashKeyFieldName(ctx.variables);
    if (Utils.isEmpty(hashKeyField)) {
      throw new HopException(
          BaseMessages.getString(PKG, "DvLink.Error.MissingLinkHashKeyFieldName", getName()));
    }

    MergeRowsPlusMeta mergeRowsMeta = new MergeRowsPlusMeta();
    mergeRowsMeta.setReferenceTransform(referenceTransform.getName());
    mergeRowsMeta.setCompareTransform(compareTransform.getName());
    mergeRowsMeta.setFlagField("flag");

    // We merge on the hash key of this table.
    //
    List<String> keyFields = new ArrayList<>();
    keyFields.add(hashKeyField);
    mergeRowsMeta.setKeyFields(keyFields);

    // We pass through the source business hash keys, the driving keys, and the record source field
    // from the "compare"
    // data stream.
    //
    // The link table hash key
    mergeRowsMeta.getPassThroughFields().add(new PassThroughField(hashKeyField, null, false));

    // The hash keys of the hubs / role aliases
    for (String hubName : hubNames) {
      String hubHashCol =
          DvTableResolutionSupport.resolveParticipatingHubHashColumn(
              ctx.model, hubName, ctx.variables, ctx.metadataProvider);
      if (Utils.isEmpty(hubHashCol)) {
        throw new HopException(
            "Unable to resolve hash key column for participating hub '"
                + hubName
                + "' on link "
                + getName());
      }
      mergeRowsMeta
          .getPassThroughFields()
          .add(new PassThroughField(ctx.variables.resolve(hubHashCol), null, false));
    }

    for (String drivingKeyName : drivingKeyNames) {
      mergeRowsMeta.getPassThroughFields().add(new PassThroughField(drivingKeyName, null, false));
    }

    for (DependentChildKey dck : listDependentChildKeys()) {
      String targetField = ctx.variables.resolve(Const.NVL(dck.getName(), ""));
      if (!Utils.isEmpty(targetField)) {
        mergeRowsMeta.getPassThroughFields().add(new PassThroughField(targetField, null, false));
      }
    }

    // We also pass through the record source
    //
    String rsFieldName = findRecordSourceFieldName(ctx);
    mergeRowsMeta.getPassThroughFields().add(new PassThroughField(rsFieldName, null, false));

    TransformMeta tm = new TransformMeta("MergeRowsPlus", "merge_diff", mergeRowsMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(referenceTransform, tm));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(compareTransform, tm));

    return tm;
  }

  private TransformMeta addFilterNewRows(PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    if (predecessor == null) {
      return null;
    }

    FilterRowsMeta filterRowsMeta = new FilterRowsMeta();
    try {
      filterRowsMeta
          .getCompare()
          .setCondition(
              new Condition(
                  "flag",
                  Condition.Function.EQUAL,
                  null,
                  new ValueMetaAndData(new ValueMetaString("static"), "new")));
    } catch (HopValueException e) {
      throw new HopException("Error creating 'new rows' filter condition", e);
    }

    TransformMeta tm = new TransformMeta("FilterRows", "filter_new", filterRowsMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + 3 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addSortRows(
      LinkUpdateContext ctx,
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      String sortFieldName,
      String transformName,
      int locationX,
      int locationY) {
    if (predecessor == null || Utils.isEmpty(sortFieldName)) {
      return predecessor;
    }
    SortRowsMeta sortRowsMeta = new SortRowsMeta();
    SortRowsField sf = new SortRowsField();
    sf.setFieldName(sortFieldName);
    sf.setAscending(true);
    // Must match on source and target streams — case-sensitive Java order, not DB collation.
    sf.setCaseSensitive(true);
    sortRowsMeta.getSortFields().add(sf);
    DvSortRowsSupport.applyConfiguration(sortRowsMeta, ctx.config, ctx.variables);

    TransformMeta tm =
        new TransformMeta(
            "SortRows",
            Utils.isEmpty(transformName) ? "sort_" + sortFieldName : transformName,
            sortRowsMeta);
    tm.setLocation(new Point(locationX, locationY));
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addUniqueLinkHashRows(
      PipelineMeta pipelineMeta, TransformMeta predecessor, String linkHashFieldName) {
    if (predecessor == null || Utils.isEmpty(linkHashFieldName)) {
      return predecessor;
    }
    UniqueRowsByHashSetMeta uniqueMeta = new UniqueRowsByHashSetMeta();
    List<UniqueRowsByHashSetMeta.CompareField> compareFields = new ArrayList<>();
    UniqueRowsByHashSetMeta.CompareField compareField = new UniqueRowsByHashSetMeta.CompareField();
    compareField.setName(linkHashFieldName);
    compareFields.add(compareField);
    uniqueMeta.setCompareFields(compareFields);

    TransformMeta tm =
        new TransformMeta("UniqueRowsByHashSet", "distinct_" + linkHashFieldName, uniqueMeta);
    tm.setLocation(predecessor.getLocation().x + SPACING_WIDTH, predecessor.getLocation().y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addConstantForLoadDate(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta, Date loadDate, TransformMeta predecessor)
      throws HopException {
    String loadDateField = "LOAD_DATE";
    if (ctx.config != null && !Utils.isEmpty(ctx.config.getLoadDateField())) {
      loadDateField = ctx.config.getLoadDateField();
    }
    if (loadDate == null) {
      throw new HopException("Please provide a load date when updating a data vault.");
    }

    ValueMetaDate valueMeta = new ValueMetaDate("ld");
    valueMeta.setConversionMask(ValueMetaBase.DEFAULT_DATE_FORMAT_MASK);
    String string = valueMeta.getString(loadDate);

    ConstantMeta constantMeta = new ConstantMeta();
    ConstantField cf = new ConstantField(loadDateField, "Date", string);
    cf.setFieldFormat(valueMeta.getConversionMask());
    constantMeta.getFields().add(cf);

    TransformMeta tm = new TransformMeta("Constant", "add_" + loadDateField, constantMeta);
    tm.setLocation(LOCATION_START_LINE_3.x + 4 * SPACING_WIDTH, LOCATION_START_LINE_3.y);
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private TransformMeta addConstantForLoadCycleId(
      LinkUpdateContext ctx, PipelineMeta pipelineMeta, TransformMeta predecessor)
      throws HopException {
    if (ctx.config == null || !ctx.config.isStoreLoadCycleId()) {
      return predecessor;
    }
    return DvLoadCycleSupport.addConstantForLoadCycleId(
        pipelineMeta,
        predecessor,
        true,
        ctx.config.getLoadCycleIdField(),
        ctx.variables,
        null,
        new Point(LOCATION_START_LINE_3.x + 5 * SPACING_WIDTH, LOCATION_START_LINE_3.y));
  }

  private DvTargetLoadSupport.TargetLoadContext buildTargetLoadContext(
      LinkUpdateContext ctx, String pipelineName) {
    String tableName = ctx.targetTableName;
    if (Utils.isEmpty(tableName)) {
      tableName = getName();
    }
    return new DvTargetLoadSupport.TargetLoadContext(
        ctx.config,
        ctx.variables,
        ctx.targetDatabaseMeta,
        ctx.targetDbName,
        tableName,
        pipelineName,
        ctx.model != null ? ctx.model.getName() : null,
        LOCATION_START_LINE_3.x + 5 * SPACING_WIDTH,
        LOCATION_START_LINE_3.y);
  }

  // The main addSortRows is defined above and used for both the link hash sort and the helper.

  @Override
  public int ensureSpecialRecords(
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DataVaultModel model,
      Date loadDate,
      ILoggingObject loggingObject)
      throws HopException {
    if (DvIntegrationSupport.shouldSkipSentinelRows(this)) {
      return 0;
    }
    return DvSpecialRecordSupport.ensureLinkSpecialRecords(
        this, metadataProvider, variables, model, loadDate, loggingObject);
  }

  /**
   * Represents the source key field mappings for one participating hub in a link. Allows mapping
   * the business key fields from the link's record source to the hub's business keys, supporting
   * different naming conventions (e.g. _fk vs _id).
   */
  @Getter
  @Setter
  public static class HubSourceKeyField {
    /** The name of the hub (matches an entry in hubNames). */
    @HopMetadataProperty private String hubName;

    /**
     * The list of source field names in the link's record source that correspond to this hub's
     * business keys.
     */
    @HopMetadataProperty(key = "businessKeySource", groupKey = "businessKeySources")
    private List<BusinessKeySource> sourceBusinessKeyFields;

    /** We need to know the sources of the driving key fields in the target table. */
    @HopMetadataProperty(key = "drivingKeySource", groupKey = "drivingKeySources")
    private List<DrivingKeySource> drivingKeySources;

    public HubSourceKeyField() {
      sourceBusinessKeyFields = new ArrayList<>();
      drivingKeySources = new ArrayList<>();
    }
  }

  /** Context for Link update pipeline generation. */
  private static class LinkUpdateContext {
    final DvLink link;
    final DataVaultModel model;
    final IHopMetadataProvider metadataProvider;
    final IVariables variables;

    final DataVaultConfiguration config;

    final DataVaultSource dataVaultSource;
    final IDvSource dvSource;

    final DatabaseMeta targetDatabaseMeta;
    final String targetDbName;
    final String targetTableName;
    final String pipelineName;

    /** Cache hop-compatible ORDER BY probes for this link update generation. */
    final DvHashKeyOrderStrategySupport.Session hashOrderSession =
        new DvHashKeyOrderStrategySupport.Session();

    LinkUpdateContext(
        DvLink link,
        DataVaultModel model,
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultConfiguration config,
        DataVaultSource dataVaultSource,
        DatabaseMeta targetDatabaseMeta,
        String targetDbName,
        String targetTableName,
        String pipelineName)
        throws HopException {
      this.link = link;
      this.model = model;
      this.metadataProvider = metadataProvider;
      this.variables = variables;
      this.config = config;
      this.dataVaultSource = dataVaultSource;
      this.targetDatabaseMeta = targetDatabaseMeta;
      this.targetDbName = targetDbName;
      this.targetTableName = targetTableName;
      this.pipelineName = pipelineName;

      this.dvSource = dataVaultSource.getDvSource(metadataProvider);
    }

    static LinkUpdateContext create(
        IHopMetadataProvider metadataProvider,
        IVariables variables,
        DataVaultModel model,
        DvLink link,
        DvLinkHubSource linkSource)
        throws HopException {
      if (metadataProvider == null || model == null || link == null) {
        return null;
      }

      DataVaultConfiguration config = model.getConfigurationOrDefault();

      String recordSourceField = "RECORD_SOURCE";
      if (config != null && !Utils.isEmpty(config.getRecordSourceField())) {
        recordSourceField = config.getRecordSourceField();
      }

      // Target DB
      DatabaseMeta targetDatabaseMeta = null;
      String targetDbName = (config != null) ? config.getTargetDatabase() : null;
      if (!Utils.isEmpty(targetDbName)) {
        targetDatabaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(targetDbName);
        if (targetDatabaseMeta == null) {
          throw new HopException(
              "Target database connection not found in metadata: " + targetDbName);
        }
      }

      String targetTableName =
          !Utils.isEmpty(link.getTableName()) ? link.getTableName() : link.getName();

      if (linkSource == null) {
        throw new HopException("Please provide a link source to create a DV link");
      }

      // Use per-source data if provided (new multi-source model), else fall back to top-level
      // legacy fields
      DataVaultSource effectiveSource =
          linkSource.resolveSource(variables, metadataProvider, model);
      if (effectiveSource == null) {
        throw new HopException("Please provide a valid record source in Link " + link.getName());
      }

      String pipelineName =
          config.buildLinkPipelineName(variables, targetTableName, effectiveSource.getName());

      return new LinkUpdateContext(
          link,
          model,
          metadataProvider,
          variables,
          config,
          effectiveSource,
          targetDatabaseMeta,
          targetDbName,
          targetTableName,
          pipelineName);
    }
  }

  @Getter
  @Setter
  public static class DvLinkHubSource {
    /** The Data Vault Source (record source) name for this link feed. */
    @HopMetadataProperty private String source;

    /**
     * Per-hub source business key field mappings for this specific source. Tells the system, for
     * each participating hub, which columns in *this* source correspond to the hub's business keys
     * (order must match the hub's business keys).
     */
    @HopMetadataProperty(key = "hubSourceKeyField", groupKey = "hubSourceKeyFields")
    private List<HubSourceKeyField> hubSourceKeyFields;

    public DvLinkHubSource() {
      hubSourceKeyFields = new ArrayList<>();
    }

    public String getSourceName() {
      return source;
    }

    public void setSourceName(String sourceName) {
      this.source = sourceName;
    }

    public DataVaultSource resolveSource(
        IVariables variables, IHopMetadataProvider metadataProvider, DataVaultModel model)
        throws HopException {
      if (Utils.isEmpty(source)) {
        return null;
      }
      return DvSourceCatalogService.resolveSource(source, model, variables, metadataProvider);
    }
  }

  @Getter
  @Setter
  public static class DvLinkSatelliteSource {
    /** The Data Vault Source (record source) name feeding a link satellite. */
    @HopMetadataProperty private String source;

    /**
     * Per-satellite source attribute field mappings for this specific source. Tells the system, for
     * each participating link satellite, which columns in *this* source correspond to the
     * satellite's attributes (and driving keys when multi-active).
     */
    @HopMetadataProperty(key = "satelliteSourceKeyField", groupKey = "satelliteSourceKeyFields")
    private List<SatelliteSourceKeyField> satelliteSourceKeyFields;

    public DvLinkSatelliteSource() {
      satelliteSourceKeyFields = new ArrayList<>();
    }

    public String getSourceName() {
      return source;
    }

    public void setSourceName(String sourceName) {
      this.source = sourceName;
    }

    public DataVaultSource resolveSource(
        IVariables variables, IHopMetadataProvider metadataProvider, DataVaultModel model)
        throws HopException {
      if (Utils.isEmpty(source)) {
        return null;
      }
      return DvSourceCatalogService.resolveSource(source, model, variables, metadataProvider);
    }
  }

  /**
   * Represents the source field mappings for one participating link satellite. Allows mapping
   * attribute fields from the link's record source to the satellite's attributes, supporting
   * different naming conventions in the source system.
   */
  @Getter
  @Setter
  public static class SatelliteSourceKeyField {
    /** The name of the link satellite (matches an entry in linkSatelliteNames). */
    @HopMetadataProperty private String satelliteName;

    /** Source columns that correspond to this satellite's attribute fields. */
    @HopMetadataProperty(key = "attributeSource", groupKey = "attributeSources")
    private List<AttributeSource> attributeSources;

    /** Source columns that supply driving keys for multi-active link satellites. */
    @HopMetadataProperty(key = "drivingKeySource", groupKey = "drivingKeySources")
    private List<DrivingKeySource> drivingKeySources;

    public SatelliteSourceKeyField() {
      attributeSources = new ArrayList<>();
      drivingKeySources = new ArrayList<>();
    }
  }
}
