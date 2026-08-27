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
package org.hopper.edw.datavault.metadata;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Condition;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.ValueMetaAndData;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.abort.AbortMeta;
import org.apache.hop.pipeline.transforms.constant.ConstantField;
import org.apache.hop.pipeline.transforms.constant.ConstantMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.apache.hop.pipeline.transforms.filterrows.FilterRowsMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateKeyField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateLookupField;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateMeta;
import org.apache.hop.pipeline.transforms.insertupdate.InsertUpdateValue;
import org.apache.hop.pipeline.transforms.selectvalues.DeleteField;
import org.apache.hop.pipeline.transforms.selectvalues.SelectValuesMeta;
import org.apache.hop.pipeline.transforms.sort.SortRowsField;
import org.apache.hop.pipeline.transforms.sort.SortRowsMeta;
import org.apache.hop.pipeline.transforms.tableinput.TableInputMeta;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputField;
import org.apache.hop.pipeline.transforms.tableoutput.TableOutputMeta;
import org.hopper.edw.datavault.transform.mergerowsplus.MergeRowsPlusMeta;
import org.hopper.edw.datavault.transform.mergerowsplus.MergeRowsPlusMetaFactory;

/**
 * Optional orphan handling for Data Vault loads: seed parent hubs from child feeds, validate
 * orphan-policy configuration, and generate pipeline fragments for {@link DvOrphanPolicy}.
 */
public final class DvOrphanHandlingSupport {

  private static final Class<?> PKG = DvOrphanHandlingSupport.class;

  public static final String DEFAULT_INFERRED_RECORD_SOURCE = "INFERRED";
  public static final String DEFAULT_INFERRED_FLAG_FIELD = "is_inferred";
  public static final String DEFAULT_QUARANTINE_TABLE = "dv_orphan_quarantine";

  static final String INFER_RECORD_SOURCE_STREAM = "__infer_record_source";
  static final String INFER_LOAD_DATE_STREAM = "__infer_load_date";
  static final String INFER_FLAG_STREAM = "__infer_flag";
  static final String EXISTENCE_FLAG_FIELD = "__orphan_parent_flag";

  private static final int SPACING = 160;

  private DvOrphanHandlingSupport() {}

  public static DvOrphanPolicy resolveModelPolicy(DataVaultConfiguration config) {
    if (config == null) {
      return DvOrphanPolicy.PASS;
    }
    return config.resolveOrphanPolicy();
  }

  public static DvOrphanPolicy resolveEffective(String configured, DataVaultConfiguration config) {
    DvOrphanPolicy parsed = DvOrphanPolicy.parse(configured, DvOrphanPolicy.INHERIT);
    if (parsed == null || parsed == DvOrphanPolicy.INHERIT) {
      return resolveModelPolicy(config);
    }
    return parsed;
  }

  public static boolean allowsInferredInsert(DvHub hub) {
    if (hub == null || DvIntegrationSupport.isExternalRead(hub)) {
      return false;
    }
    return hub.isAllowInferredInsert();
  }

  /**
   * Adds {@code sourceName} as a hub record source and copies vault BK definitions with the given
   * source-field mappings when they are not already present.
   *
   * @return number of new business-key mapping rows added
   */
  public static int seedHubFromChildSource(
      DvHub hub, String sourceName, List<BusinessKeySource> mappings, IVariables variables) {
    if (hub == null || Utils.isEmpty(sourceName)) {
      return 0;
    }
    String resolvedSource = variables != null ? variables.resolve(sourceName) : sourceName;
    if (Utils.isEmpty(resolvedSource)) {
      return 0;
    }
    if (hub.getRecordSources() == null) {
      hub.setRecordSources(new ArrayList<>());
    }
    boolean haveSource = false;
    for (String existing : hub.getRecordSources()) {
      String resolved = variables != null ? variables.resolve(existing) : existing;
      if (resolvedSource.equals(resolved)) {
        haveSource = true;
        break;
      }
    }
    if (!haveSource) {
      hub.getRecordSources().add(resolvedSource);
    }

    int added = 0;
    if (hub.getBusinessKeys() == null) {
      hub.setBusinessKeys(new ArrayList<>());
    }
    for (BusinessKey vaultKey : hub.getDistinctBusinessKeys()) {
      if (vaultKey == null || Utils.isEmpty(vaultKey.getName())) {
        continue;
      }
      if (hasBusinessKeyMapping(hub, vaultKey.getName(), resolvedSource, variables)) {
        continue;
      }
      BusinessKeySource mapping = findMapping(mappings, vaultKey.getName());
      BusinessKey copy = copyBusinessKeyDefinition(vaultKey);
      copy.setRecordSourceName(resolvedSource);
      if (mapping != null) {
        List<String> parts = mapping.resolveSourceParts();
        if (parts.size() > 1) {
          copy.setSourceFieldNames(new ArrayList<>(parts));
          copy.setSourceFieldName(null);
          copy.setComposite(vaultKey.isComposite() || parts.size() > 1);
        } else if (!parts.isEmpty()) {
          copy.setSourceFieldName(parts.get(0));
          copy.setSourceFieldNames(new ArrayList<>());
        }
      } else {
        copy.setSourceFieldName(vaultKey.getName());
        copy.setSourceFieldNames(new ArrayList<>());
      }
      hub.getBusinessKeys().add(copy);
      added++;
    }
    if (added > 0 || !haveSource) {
      hub.setChanged();
    }
    return added;
  }

  public static int seedParentHubsFromLink(
      DataVaultModel model, DvLink link, DvLink.DvLinkHubSource linkSource, IVariables variables) {
    if (model == null || link == null || linkSource == null) {
      return 0;
    }
    String sourceName = linkSource.getSourceName();
    if (Utils.isEmpty(sourceName)) {
      return 0;
    }
    int added = 0;
    if (link.getHubNames() == null) {
      return 0;
    }
    for (String hubName : link.getHubNames()) {
      DvHub hub = model.findHub(hubName);
      if (hub == null) {
        continue;
      }
      DvLink.HubSourceKeyField field =
          DvLinkHubSourceKeyFieldSupport.findHubSourceKeyFieldOrNull(linkSource, hubName);
      List<BusinessKeySource> mappings =
          field != null ? field.getSourceBusinessKeyFields() : List.of();
      added += seedHubFromChildSource(hub, sourceName, mappings, variables);
    }
    return added;
  }

  public static int seedParentHubFromSatellite(
      DataVaultModel model, DvSatellite satellite, IVariables variables) {
    if (model == null || satellite == null || Utils.isEmpty(satellite.getHubName())) {
      return 0;
    }
    DvHub hub = model.findHub(satellite.getHubName());
    if (hub == null || Utils.isEmpty(satellite.getRecordSourceName())) {
      return 0;
    }
    List<BusinessKeySource> mappings = new ArrayList<>();
    List<BusinessKey> vaultKeys = hub.getDistinctBusinessKeys();
    List<String> sourceFields = satellite.getParentKeySourceFields();
    for (int i = 0; i < vaultKeys.size(); i++) {
      BusinessKey vaultKey = vaultKeys.get(i);
      String sourceField =
          sourceFields != null && i < sourceFields.size() && !Utils.isEmpty(sourceFields.get(i))
              ? sourceFields.get(i)
              : vaultKey.getName();
      mappings.add(new BusinessKeySource(vaultKey.getName(), sourceField));
    }
    return seedHubFromChildSource(hub, satellite.getRecordSourceName(), mappings, variables);
  }

  public static void checkLink(
      DvLink link,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      List<ICheckResult> remarks) {
    if (link == null || remarks == null) {
      return;
    }
    DvOrphanPolicy policy = resolveEffective(link.getOrphanPolicy(), config);
    if (policy == DvOrphanPolicy.SENTINEL
        && (config == null || !config.isGenerateUnknownRecord())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DvOrphanHandling.CheckResult.SentinelRequiresUnknown", link.getName()),
              link));
    }
    if (policy == DvOrphanPolicy.QUARANTINE
        && (config == null || Utils.isEmpty(config.resolveQuarantineTableName(variables)))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DvOrphanHandling.CheckResult.QuarantineRequiresTable", link.getName()),
              link));
    }
    if (policy != DvOrphanPolicy.INFER || link.getHubNames() == null) {
      return;
    }
    for (String hubName : link.getHubNames()) {
      DvHub hub = model != null ? model.findHub(hubName) : null;
      if (hub != null && !allowsInferredInsert(hub)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "DvOrphanHandling.CheckResult.InferNotAllowed",
                    link.getName(),
                    hub.getName()),
                link));
      }
    }
  }

  public static void checkSatellite(
      DvSatellite satellite,
      DataVaultModel model,
      DataVaultConfiguration config,
      IVariables variables,
      List<ICheckResult> remarks) {
    if (satellite == null || remarks == null || Utils.isEmpty(satellite.getHubName())) {
      return;
    }
    DvOrphanPolicy policy = resolveEffective(satellite.getOrphanPolicy(), config);
    DvHub hub = model != null ? model.findHub(satellite.getHubName()) : null;
    if (hub == null) {
      return;
    }
    if (policy == DvOrphanPolicy.INFER && !allowsInferredInsert(hub)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "DvOrphanHandling.CheckResult.InferNotAllowed",
                  satellite.getName(),
                  hub.getName()),
              satellite));
    }
    if (policy == DvOrphanPolicy.SENTINEL
        && (config == null || !config.isGenerateUnknownRecord())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DvOrphanHandling.CheckResult.SentinelRequiresUnknown", satellite.getName()),
              satellite));
    }
    if (policy == DvOrphanPolicy.QUARANTINE
        && (config == null || Utils.isEmpty(config.resolveQuarantineTableName(variables)))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DvOrphanHandling.CheckResult.QuarantineRequiresTable", satellite.getName()),
              satellite));
    }
  }

  public static boolean modelUsesQuarantine(DataVaultModel model) {
    if (model == null) {
      return false;
    }
    DataVaultConfiguration config = model.getConfigurationOrDefault();
    if (resolveModelPolicy(config) == DvOrphanPolicy.QUARANTINE) {
      return true;
    }
    if (model.getTables() == null) {
      return false;
    }
    for (IDvTable table : model.getTables()) {
      if (table instanceof DvLink link
          && resolveEffective(link.getOrphanPolicy(), config) == DvOrphanPolicy.QUARANTINE) {
        return true;
      }
      if (table instanceof DvSatellite sat
          && resolveEffective(sat.getOrphanPolicy(), config) == DvOrphanPolicy.QUARANTINE) {
        return true;
      }
    }
    return false;
  }

  /**
   * Inserts orphan-handling transforms after parent hashes have been calculated and before the
   * child identity (link hash / satellite select) is built.
   */
  public static TransformMeta applyAfterParentHashes(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<ParentOrphanContext> parents,
      DataVaultConfiguration config,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      Date loadDate,
      String childTableName,
      String recordSourceName,
      Point location)
      throws HopException {
    if (pipelineMeta == null || predecessor == null || parents == null || parents.isEmpty()) {
      return predecessor;
    }
    TransformMeta current = predecessor;
    int index = 0;
    for (ParentOrphanContext parent : parents) {
      if (parent == null || parent.policy == null || !parent.policy.changesRuntimeGraph()) {
        continue;
      }
      current =
          applyForParent(
              pipelineMeta,
              current,
              parent,
              config,
              model,
              metadataProvider,
              variables,
              loadDate,
              childTableName,
              recordSourceName,
              location,
              index++);
    }
    return current;
  }

  public static void appendInferredFlagToHubLayout(
      IRowMeta rowMeta, DataVaultConfiguration config, IVariables variables) {
    if (rowMeta == null || config == null || !config.isStoreInferredFlag()) {
      return;
    }
    String field = config.resolveInferredFlagField(variables);
    if (Utils.isEmpty(field) || rowMeta.searchValueMeta(field) != null) {
      return;
    }
    rowMeta.addValueMeta(new ValueMetaBoolean(field));
  }

  public static IRowMeta quarantineLayout() {
    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaTimestamp("load_date"));
    rowMeta.addValueMeta(stringField("model_name", 200));
    rowMeta.addValueMeta(stringField("child_table", 200));
    rowMeta.addValueMeta(stringField("parent_table", 200));
    rowMeta.addValueMeta(stringField("policy", 40));
    rowMeta.addValueMeta(stringField("reason", 40));
    rowMeta.addValueMeta(stringField("business_key", 1000));
    rowMeta.addValueMeta(stringField("parent_hash", 256));
    rowMeta.addValueMeta(stringField("record_source", 200));
    return rowMeta;
  }

  public static void ensureQuarantineTable(
      DatabaseMeta databaseMeta,
      IVariables variables,
      ILoggingObject loggingObject,
      String tableName)
      throws HopException {
    if (databaseMeta == null || Utils.isEmpty(tableName)) {
      return;
    }
    try (Database db = new Database(loggingObject, variables, databaseMeta)) {
      db.connect();
      if (db.checkTableExists(null, tableName)) {
        return;
      }
      IRowMeta layout = quarantineLayout();
      String ddl = db.getDDL(tableName, layout, null, false, null, true);
      if (!Utils.isEmpty(ddl)) {
        db.execStatements(ddl);
      }
    } catch (Exception e) {
      throw new HopException("Unable to create orphan quarantine table " + tableName, e);
    }
  }

  public static final class ParentOrphanContext {
    public final DvHub hub;
    public final DvOrphanPolicy policy;
    public final String hashFieldName;
    public final List<String> sourceBkFields;
    public final List<BusinessKeySource> mappings;

    public ParentOrphanContext(
        DvHub hub,
        DvOrphanPolicy policy,
        String hashFieldName,
        List<String> sourceBkFields,
        List<BusinessKeySource> mappings) {
      this.hub = hub;
      this.policy = policy;
      this.hashFieldName = hashFieldName;
      this.sourceBkFields = sourceBkFields != null ? sourceBkFields : List.of();
      this.mappings = mappings != null ? mappings : List.of();
    }
  }

  private static TransformMeta applyForParent(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      ParentOrphanContext parent,
      DataVaultConfiguration config,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      Date loadDate,
      String childTableName,
      String recordSourceName,
      Point location,
      int index)
      throws HopException {
    Point loc = location != null ? location : new Point(160, 480);
    int x = loc.x + index * SPACING;
    int y = loc.y;

    String prefix = sanitize(parent.hub.getName()) + "_" + index;
    TransformMeta filter =
        addNullKeyFilter(pipelineMeta, predecessor, parent.sourceBkFields, prefix, x, y);
    TransformMeta continueDummy =
        addDummy(pipelineMeta, "orphan_continue_" + prefix, x + 4 * SPACING, y);

    TransformMeta nullStart =
        addDummy(pipelineMeta, "orphan_null_" + prefix, x + SPACING, y + SPACING);
    TransformMeta presentStart = addDummy(pipelineMeta, "orphan_present_" + prefix, x + SPACING, y);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(filter, nullStart));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(filter, presentStart));
    wireFilter(filter, nullStart, presentStart);

    TransformMeta nullEnd =
        handleNullKeys(
            pipelineMeta,
            nullStart,
            parent,
            config,
            variables,
            loadDate,
            childTableName,
            recordSourceName,
            model,
            prefix,
            x,
            y + SPACING);
    boolean rejoinNull =
        parent.policy == DvOrphanPolicy.SENTINEL
            || (parent.policy == DvOrphanPolicy.INFER
                && config != null
                && config.isGenerateUnknownRecord());
    if (nullEnd != null && rejoinNull) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(nullEnd, continueDummy));
    }

    TransformMeta presentEnd =
        handlePresentKeys(
            pipelineMeta,
            presentStart,
            parent,
            config,
            model,
            metadataProvider,
            variables,
            loadDate,
            childTableName,
            recordSourceName,
            prefix,
            x + SPACING,
            y);
    if (presentEnd != null) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(presentEnd, continueDummy));
    }
    return continueDummy;
  }

  private static TransformMeta handleNullKeys(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      ParentOrphanContext parent,
      DataVaultConfiguration config,
      IVariables variables,
      Date loadDate,
      String childTableName,
      String recordSourceName,
      DataVaultModel model,
      String prefix,
      int x,
      int y)
      throws HopException {
    DvOrphanPolicy policy = parent.policy;
    if (policy == DvOrphanPolicy.INFER || policy == DvOrphanPolicy.SENTINEL) {
      if (config == null || !config.isGenerateUnknownRecord()) {
        if (policy == DvOrphanPolicy.INFER) {
          return addQuarantineOutput(
              pipelineMeta,
              predecessor,
              parent,
              config,
              variables,
              loadDate,
              childTableName,
              recordSourceName,
              model,
              "NULL_KEY",
              prefix + "_null",
              x + SPACING,
              y);
        }
        throw new HopException(
            "Orphan policy SENTINEL requires Generate unknown record on the model");
      }
      return addSentinelRemap(
          pipelineMeta, predecessor, parent, config, variables, prefix, x + SPACING, y);
    }
    if (policy == DvOrphanPolicy.QUARANTINE) {
      return addQuarantineOutput(
          pipelineMeta,
          predecessor,
          parent,
          config,
          variables,
          loadDate,
          childTableName,
          recordSourceName,
          model,
          "NULL_KEY",
          prefix + "_null",
          x + SPACING,
          y);
    }
    if (policy == DvOrphanPolicy.FAIL) {
      TransformMeta abort = addAbort(pipelineMeta, "fail_null_" + prefix, x + SPACING, y);
      if (predecessor != null) {
        pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, abort));
      }
      return abort;
    }
    if (predecessor != null) {
      return predecessor;
    }
    return addDummy(pipelineMeta, "orphan_null_pass_" + prefix, x + SPACING, y);
  }

  private static TransformMeta handlePresentKeys(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      ParentOrphanContext parent,
      DataVaultConfiguration config,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      Date loadDate,
      String childTableName,
      String recordSourceName,
      String prefix,
      int x,
      int y)
      throws HopException {
    if (parent.policy == DvOrphanPolicy.INFER) {
      if (!allowsInferredInsert(parent.hub)) {
        throw new HopException("Orphan policy INFER is not allowed on hub " + parent.hub.getName());
      }
      return addInferInsert(
          pipelineMeta,
          predecessor,
          parent,
          config,
          model,
          metadataProvider,
          variables,
          loadDate,
          prefix,
          x,
          y);
    }
    if (parent.policy == DvOrphanPolicy.SENTINEL) {
      return addMissingParentSentinel(
          pipelineMeta,
          predecessor,
          parent,
          config,
          model,
          metadataProvider,
          variables,
          prefix,
          x,
          y);
    }
    if (parent.policy == DvOrphanPolicy.QUARANTINE || parent.policy == DvOrphanPolicy.FAIL) {
      return addMissingParentDisposition(
          pipelineMeta,
          predecessor,
          parent,
          config,
          model,
          metadataProvider,
          variables,
          loadDate,
          childTableName,
          recordSourceName,
          prefix,
          x,
          y);
    }
    return predecessor;
  }

  private static TransformMeta addInferInsert(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      ParentOrphanContext parent,
      DataVaultConfiguration config,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      Date loadDate,
      String prefix,
      int x,
      int y)
      throws HopException {
    DatabaseMeta targetDb = loadTargetDatabase(metadataProvider, config);
    if (targetDb == null) {
      throw new HopException(
          "Orphan policy INFER requires a target database on the Data Vault model");
    }
    TransformMeta constants =
        addInferConstants(pipelineMeta, predecessor, config, variables, loadDate, prefix, x, y);
    InsertUpdateMeta insertMeta = new InsertUpdateMeta();
    insertMeta.setConnection(config.getTargetDatabase());
    insertMeta.setCommitSize(
        config != null ? config.resolveTargetTableCommitSize(variables) : "1000");
    insertMeta.setUpdateBypassed(true);
    InsertUpdateLookupField lookup = new InsertUpdateLookupField();
    String tableName =
        !Utils.isEmpty(parent.hub.getTableName())
            ? parent.hub.getTableName()
            : parent.hub.getName();
    lookup.setTableName(tableName);
    lookup
        .getLookupKeys()
        .add(new InsertUpdateKeyField(parent.hashFieldName, parent.hashFieldName, "="));
    List<InsertUpdateValue> values = new ArrayList<>();
    values.add(new InsertUpdateValue(parent.hashFieldName, parent.hashFieldName, false));
    for (BusinessKey vaultKey : parent.hub.getDistinctBusinessKeys()) {
      String vaultName =
          variables != null ? variables.resolve(vaultKey.getName()) : vaultKey.getName();
      String streamField = resolveStreamFieldForVaultKey(vaultName, parent);
      values.add(new InsertUpdateValue(vaultName, streamField, false));
    }
    String rsColumn = resolveHubRecordSourceColumn(parent.hub, config, variables);
    values.add(new InsertUpdateValue(rsColumn, INFER_RECORD_SOURCE_STREAM, false));
    String loadDateColumn = resolveLoadDateColumn(config, variables);
    values.add(new InsertUpdateValue(loadDateColumn, INFER_LOAD_DATE_STREAM, false));
    if (config != null && config.isStoreInferredFlag()) {
      values.add(
          new InsertUpdateValue(
              config.resolveInferredFlagField(variables), INFER_FLAG_STREAM, false));
    }
    lookup.setValueFields(values);
    insertMeta.setInsertUpdateLookupField(lookup);

    TransformMeta tm = new TransformMeta("InsertUpdate", "infer_hub_" + prefix, insertMeta);
    tm.setLocation(new Point(x + SPACING, y));
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(constants, tm));
    return tm;
  }

  private static TransformMeta addMissingParentSentinel(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      ParentOrphanContext parent,
      DataVaultConfiguration config,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      String prefix,
      int x,
      int y)
      throws HopException {
    ExistenceSplit split =
        addExistenceSplit(
            pipelineMeta, predecessor, parent, config, metadataProvider, variables, prefix, x, y);
    TransformMeta remap =
        addSentinelRemap(
            pipelineMeta,
            split.missing,
            parent,
            config,
            variables,
            prefix + "_miss",
            x + 3 * SPACING,
            y + SPACING);
    TransformMeta dummy = addDummy(pipelineMeta, "sentinel_join_" + prefix, x + 4 * SPACING, y);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(split.found, dummy));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(remap, dummy));
    return dummy;
  }

  private static TransformMeta addMissingParentDisposition(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      ParentOrphanContext parent,
      DataVaultConfiguration config,
      DataVaultModel model,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      Date loadDate,
      String childTableName,
      String recordSourceName,
      String prefix,
      int x,
      int y)
      throws HopException {
    ExistenceSplit split =
        addExistenceSplit(
            pipelineMeta, predecessor, parent, config, metadataProvider, variables, prefix, x, y);
    TransformMeta missingEnd;
    if (parent.policy == DvOrphanPolicy.FAIL) {
      missingEnd = addAbort(pipelineMeta, "fail_missing_" + prefix, x + 3 * SPACING, y + SPACING);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(split.missing, missingEnd));
    } else {
      missingEnd =
          addQuarantineOutput(
              pipelineMeta,
              split.missing,
              parent,
              config,
              variables,
              loadDate,
              childTableName,
              recordSourceName,
              model,
              "MISSING_PARENT",
              prefix + "_miss",
              x + 3 * SPACING,
              y + SPACING);
    }
    TransformMeta dummy = addDummy(pipelineMeta, "disp_join_" + prefix, x + 4 * SPACING, y);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(split.found, dummy));
    // Quarantine / abort consume missing rows; found rows continue.
    if (missingEnd != null && parent.policy == DvOrphanPolicy.FAIL) {
      // abort has no outgoing hop
    }
    return dummy;
  }

  private static ExistenceSplit addExistenceSplit(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      ParentOrphanContext parent,
      DataVaultConfiguration config,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      String prefix,
      int x,
      int y)
      throws HopException {
    DatabaseMeta targetDb = loadTargetDatabase(metadataProvider, config);
    if (targetDb == null) {
      throw new HopException("Orphan parent lookup requires a target database");
    }
    TransformMeta sort =
        addSort(
            pipelineMeta,
            predecessor,
            parent.hashFieldName,
            "sort_child_" + prefix,
            config,
            variables,
            x,
            y);
    String tableName =
        !Utils.isEmpty(parent.hub.getTableName())
            ? parent.hub.getTableName()
            : parent.hub.getName();
    String quotedHash = targetDb.quoteField(parent.hashFieldName);
    String quotedTable = targetDb.getQuotedSchemaTableCombination(variables, null, tableName);
    TableInputMeta tableInputMeta = new TableInputMeta();
    tableInputMeta.setConnection(config.getTargetDatabase());
    tableInputMeta.setSql(
        "SELECT " + quotedHash + " FROM " + quotedTable + " ORDER BY " + quotedHash);
    TransformMeta hubInput = new TransformMeta("TableInput", "hub_keys_" + prefix, tableInputMeta);
    hubInput.setLocation(new Point(x, y + SPACING));
    pipelineMeta.addTransform(hubInput);

    MergeRowsPlusMeta mergeMeta =
        MergeRowsPlusMetaFactory.create(
            hubInput.getName(),
            sort.getName(),
            EXISTENCE_FLAG_FIELD,
            List.of(parent.hashFieldName),
            List.of());
    TransformMeta merge = new TransformMeta("MergeRowsPlus", "lookup_hub_" + prefix, mergeMeta);
    merge.setLocation(new Point(x + SPACING, y));
    pipelineMeta.addTransform(merge);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(hubInput, merge));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(sort, merge));

    TransformMeta filterNew =
        addFlagFilter(
            pipelineMeta, merge, "new", "filter_missing_" + prefix, x + 2 * SPACING, y + SPACING);
    TransformMeta filterFound =
        addFlagFilter(
            pipelineMeta, merge, "identical", "filter_found_" + prefix, x + 2 * SPACING, y);
    // MergeRows has a single output; hop it to both filters and let each keep matching rows.
    pipelineMeta.addPipelineHop(new PipelineHopMeta(merge, filterNew));
    pipelineMeta.addPipelineHop(new PipelineHopMeta(merge, filterFound));
    return new ExistenceSplit(filterFound, filterNew);
  }

  private static TransformMeta addSentinelRemap(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      ParentOrphanContext parent,
      DataVaultConfiguration config,
      IVariables variables,
      String prefix,
      int x,
      int y)
      throws HopException {
    TransformMeta from = predecessor;
    if (from != null) {
      SelectValuesMeta selectMeta = new SelectValuesMeta();
      DeleteField deleteField = new DeleteField();
      deleteField.setName(parent.hashFieldName);
      selectMeta.getSelectOption().getDeleteName().add(deleteField);
      TransformMeta delete = new TransformMeta("SelectValues", "drop_hk_" + prefix, selectMeta);
      delete.setLocation(new Point(x, y));
      pipelineMeta.addTransform(delete);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(from, delete));
      from = delete;
    }
    String unknownHash =
        Const.NVL(config.getUnknownHashKeyValue(), "00000000000000000000000000000000");
    if (variables != null) {
      unknownHash = variables.resolve(unknownHash);
    }
    ConstantMeta constantMeta = new ConstantMeta();
    constantMeta.getFields().add(new ConstantField(parent.hashFieldName, "String", unknownHash));
    TransformMeta constant = new TransformMeta("Constant", "unknown_hk_" + prefix, constantMeta);
    constant.setLocation(new Point(x + SPACING, y));
    pipelineMeta.addTransform(constant);
    if (from != null) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(from, constant));
    }
    return constant;
  }

  private static TransformMeta addQuarantineOutput(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      ParentOrphanContext parent,
      DataVaultConfiguration config,
      IVariables variables,
      Date loadDate,
      String childTableName,
      String recordSourceName,
      DataVaultModel model,
      String reason,
      String prefix,
      int x,
      int y)
      throws HopException {
    String table =
        config != null ? config.resolveQuarantineTableName(variables) : DEFAULT_QUARANTINE_TABLE;
    if (Utils.isEmpty(table)) {
      throw new HopException("Orphan policy QUARANTINE requires a quarantine table name");
    }
    TransformMeta from = predecessor;
    ConstantMeta constantMeta = new ConstantMeta();
    String loadDateField = "load_date";
    if (loadDate != null) {
      ValueMetaDate valueMeta = new ValueMetaDate("ld");
      valueMeta.setConversionMask("yyyy/MM/dd HH:mm:ss.SSS");
      try {
        ConstantField cf =
            new ConstantField(loadDateField, "Timestamp", valueMeta.getString(loadDate));
        cf.setFieldFormat(valueMeta.getConversionMask());
        constantMeta.getFields().add(cf);
      } catch (Exception e) {
        constantMeta
            .getFields()
            .add(new ConstantField(loadDateField, "String", loadDate.toString()));
      }
    }
    constantMeta
        .getFields()
        .add(
            new ConstantField(
                "model_name", "String", model != null ? Const.NVL(model.getName(), "") : ""));
    constantMeta
        .getFields()
        .add(new ConstantField("child_table", "String", Const.NVL(childTableName, "")));
    constantMeta
        .getFields()
        .add(
            new ConstantField(
                "parent_table", "String", parent.hub != null ? parent.hub.getName() : ""));
    constantMeta
        .getFields()
        .add(
            new ConstantField(
                "policy", "String", parent.policy != null ? parent.policy.name() : ""));
    constantMeta.getFields().add(new ConstantField("reason", "String", reason));
    constantMeta
        .getFields()
        .add(new ConstantField("record_source", "String", Const.NVL(recordSourceName, "")));
    TransformMeta constants = new TransformMeta("Constant", "q_const_" + prefix, constantMeta);
    constants.setLocation(new Point(x, y));
    pipelineMeta.addTransform(constants);
    if (from != null) {
      pipelineMeta.addPipelineHop(new PipelineHopMeta(from, constants));
    }

    TableOutputMeta outputMeta = new TableOutputMeta();
    outputMeta.setConnection(config.getTargetDatabase());
    outputMeta.setTableName(table);
    outputMeta.setSpecifyFields(true);
    List<TableOutputField> fields = new ArrayList<>();
    fields.add(tableField("load_date", "load_date"));
    fields.add(tableField("model_name", "model_name"));
    fields.add(tableField("child_table", "child_table"));
    fields.add(tableField("parent_table", "parent_table"));
    fields.add(tableField("policy", "policy"));
    fields.add(tableField("reason", "reason"));
    String bkStream =
        parent.sourceBkFields.isEmpty() ? parent.hashFieldName : parent.sourceBkFields.get(0);
    fields.add(tableField("business_key", bkStream));
    fields.add(tableField("parent_hash", parent.hashFieldName));
    fields.add(tableField("record_source", "record_source"));
    outputMeta.setFields(fields);
    TransformMeta output = new TransformMeta("TableOutput", "quarantine_" + prefix, outputMeta);
    output.setLocation(new Point(x + SPACING, y));
    pipelineMeta.addTransform(output);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(constants, output));
    return output;
  }

  private static TableOutputField tableField(String column, String stream) {
    TableOutputField field = new TableOutputField();
    field.setFieldDatabase(column);
    field.setFieldStream(stream);
    return field;
  }

  private static TransformMeta addInferConstants(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      DataVaultConfiguration config,
      IVariables variables,
      Date loadDate,
      String prefix,
      int x,
      int y)
      throws HopException {
    ConstantMeta constantMeta = new ConstantMeta();
    String inferredRs =
        config != null
            ? config.resolveInferredRecordSource(variables)
            : DEFAULT_INFERRED_RECORD_SOURCE;
    constantMeta
        .getFields()
        .add(new ConstantField(INFER_RECORD_SOURCE_STREAM, "String", inferredRs));
    if (loadDate != null) {
      ValueMetaDate valueMeta = new ValueMetaDate("ld");
      valueMeta.setConversionMask("yyyy/MM/dd HH:mm:ss.SSS");
      try {
        ConstantField cf =
            new ConstantField(INFER_LOAD_DATE_STREAM, "Timestamp", valueMeta.getString(loadDate));
        cf.setFieldFormat(valueMeta.getConversionMask());
        constantMeta.getFields().add(cf);
      } catch (Exception e) {
        constantMeta
            .getFields()
            .add(new ConstantField(INFER_LOAD_DATE_STREAM, "String", loadDate.toString()));
      }
    }
    if (config != null && config.isStoreInferredFlag()) {
      constantMeta.getFields().add(new ConstantField(INFER_FLAG_STREAM, "Boolean", "Y"));
    }
    TransformMeta tm = new TransformMeta("Constant", "infer_const_" + prefix, constantMeta);
    tm.setLocation(new Point(x, y));
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addNullKeyFilter(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      List<String> sourceBkFields,
      String prefix,
      int x,
      int y)
      throws HopException {
    FilterRowsMeta filterMeta = new FilterRowsMeta();
    filterMeta.setCondition(buildNullKeyCondition(sourceBkFields));
    TransformMeta tm = new TransformMeta("FilterRows", "filter_null_" + prefix, filterMeta);
    tm.setLocation(new Point(x, y));
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  static Condition buildNullKeyCondition(List<String> sourceBkFields) throws HopException {
    try {
      if (sourceBkFields == null || sourceBkFields.isEmpty()) {
        return new Condition();
      }
      Condition first = new Condition(sourceBkFields.get(0), Condition.Function.NULL, null, null);
      if (sourceBkFields.size() == 1) {
        return first;
      }
      Condition root = new Condition();
      root.addCondition(first);
      for (int i = 1; i < sourceBkFields.size(); i++) {
        Condition next =
            new Condition(
                Condition.Operator.OR, sourceBkFields.get(i), Condition.Function.NULL, null, null);
        root.addCondition(next);
      }
      return root;
    } catch (HopValueException e) {
      throw new HopException("Unable to build null business-key filter", e);
    }
  }

  private static TransformMeta addFlagFilter(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      String flagValue,
      String name,
      int x,
      int y)
      throws HopException {
    FilterRowsMeta filterMeta = new FilterRowsMeta();
    try {
      filterMeta.setCondition(
          new Condition(
              EXISTENCE_FLAG_FIELD,
              Condition.Function.EQUAL,
              null,
              new ValueMetaAndData(new ValueMetaString("static"), flagValue)));
    } catch (HopValueException e) {
      throw new HopException("Unable to build existence flag filter", e);
    }
    TransformMeta tm = new TransformMeta("FilterRows", name, filterMeta);
    tm.setLocation(new Point(x, y));
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private static TransformMeta addSort(
      PipelineMeta pipelineMeta,
      TransformMeta predecessor,
      String field,
      String name,
      DataVaultConfiguration config,
      IVariables variables,
      int x,
      int y) {
    SortRowsMeta sortMeta = new SortRowsMeta();
    SortRowsField sortField = new SortRowsField();
    sortField.setFieldName(field);
    sortField.setAscending(true);
    sortMeta.getSortFields().add(sortField);
    DvSortRowsSupport.applyConfiguration(sortMeta, config, variables);
    TransformMeta tm = new TransformMeta("SortRows", name, sortMeta);
    tm.setLocation(new Point(x, y));
    pipelineMeta.addTransform(tm);
    pipelineMeta.addPipelineHop(new PipelineHopMeta(predecessor, tm));
    return tm;
  }

  private static TransformMeta addDummy(PipelineMeta pipelineMeta, String name, int x, int y) {
    TransformMeta tm = new TransformMeta("Dummy", name, new DummyMeta());
    tm.setLocation(new Point(x, y));
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private static TransformMeta addAbort(PipelineMeta pipelineMeta, String name, int x, int y) {
    AbortMeta abortMeta = new AbortMeta();
    abortMeta.setRowThreshold("0");
    abortMeta.setAbortOption(AbortMeta.AbortOption.ABORT_WITH_ERROR);
    abortMeta.setMessage("Orphan handling policy FAIL: incomplete parent identity");
    TransformMeta tm = new TransformMeta("Abort", name, abortMeta);
    tm.setLocation(new Point(x, y));
    pipelineMeta.addTransform(tm);
    return tm;
  }

  private static void wireFilter(
      TransformMeta filter, TransformMeta trueTarget, TransformMeta falseTarget) {
    if (!(filter.getTransform() instanceof FilterRowsMeta filterMeta)) {
      return;
    }
    if (trueTarget != null) {
      filterMeta.setTrueTransformName(trueTarget.getName());
    }
    if (falseTarget != null) {
      filterMeta.setFalseTransformName(falseTarget.getName());
    }
  }

  private static boolean hasBusinessKeyMapping(
      DvHub hub, String vaultName, String sourceName, IVariables variables) {
    if (hub.getBusinessKeys() == null) {
      return false;
    }
    for (BusinessKey key : hub.getBusinessKeys()) {
      if (key == null || !vaultName.equals(key.getName())) {
        continue;
      }
      String keySource =
          variables != null
              ? variables.resolve(key.getRecordSourceName())
              : key.getRecordSourceName();
      if (Utils.isEmpty(keySource) || sourceName.equals(keySource)) {
        return true;
      }
    }
    return false;
  }

  private static BusinessKeySource findMapping(List<BusinessKeySource> mappings, String vaultName) {
    if (mappings == null) {
      return null;
    }
    for (BusinessKeySource mapping : mappings) {
      if (mapping != null && vaultName.equals(mapping.getBusinessKeyField())) {
        return mapping;
      }
    }
    return null;
  }

  private static BusinessKey copyBusinessKeyDefinition(BusinessKey source) {
    BusinessKey copy = new BusinessKey(source.getName());
    copy.setDescription(source.getDescription());
    copy.setDataType(source.getDataType());
    copy.setLength(source.getLength());
    copy.setPrecision(source.getPrecision());
    copy.setComposite(source.isComposite());
    return copy;
  }

  private static String resolveStreamFieldForVaultKey(
      String vaultName, ParentOrphanContext parent) {
    BusinessKeySource mapping = findMapping(parent.mappings, vaultName);
    if (mapping != null && !mapping.resolveSourceParts().isEmpty()) {
      return mapping.resolveSourceParts().get(0);
    }
    for (String field : parent.sourceBkFields) {
      if (vaultName.equals(field)) {
        return field;
      }
    }
    return parent.sourceBkFields.isEmpty() ? vaultName : parent.sourceBkFields.get(0);
  }

  private static String resolveHubRecordSourceColumn(
      DvHub hub, DataVaultConfiguration config, IVariables variables) {
    String name = hub != null ? hub.getRecordSourceFieldName() : null;
    if (Utils.isEmpty(name) && config != null) {
      name = config.getRecordSourceField();
    }
    if (variables != null && !Utils.isEmpty(name)) {
      name = variables.resolve(name);
    }
    return Utils.isEmpty(name) ? "RECORD_SOURCE" : name;
  }

  private static String resolveLoadDateColumn(DataVaultConfiguration config, IVariables variables) {
    String name = config != null ? config.getLoadDateField() : null;
    if (variables != null && !Utils.isEmpty(name)) {
      name = variables.resolve(name);
    }
    return Utils.isEmpty(name) ? "LOAD_DATE" : name;
  }

  private static DatabaseMeta loadTargetDatabase(
      IHopMetadataProvider metadataProvider, DataVaultConfiguration config) throws HopException {
    return DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, config);
  }

  private static IValueMeta stringField(String name, int length) {
    IValueMeta meta = new ValueMetaString(name);
    meta.setLength(length);
    return meta;
  }

  private static String sanitize(String name) {
    if (Utils.isEmpty(name)) {
      return "hub";
    }
    return name.replaceAll("[^A-Za-z0-9_]", "_");
  }

  private static final class ExistenceSplit {
    private final TransformMeta found;
    private final TransformMeta missing;

    private ExistenceSplit(TransformMeta found, TransformMeta missing) {
      this.found = found;
      this.missing = missing;
    }
  }
}
