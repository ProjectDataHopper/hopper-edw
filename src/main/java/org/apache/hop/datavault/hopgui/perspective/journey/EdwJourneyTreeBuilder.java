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
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ActionRef;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.CatalogFeed;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ModelRef;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.OutputRef;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.WorkflowRef;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneyTreeNode.Kind;
import org.apache.hop.i18n.BaseMessages;

/** Projects an {@link EdwJourneySnapshot} into a stable, ordered tree. */
public final class EdwJourneyTreeBuilder {

  private static final Class<?> PKG = EdwJourneyTreeBuilder.class;

  private EdwJourneyTreeBuilder() {}

  public static EdwJourneyTreeNode build(EdwJourneySnapshot snapshot) {
    return build(snapshot, EdwJourneyOpsOverlay.empty());
  }

  public static EdwJourneyTreeNode build(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay) {
    EdwJourneySnapshot safe = snapshot != null ? snapshot : EdwJourneySnapshot.empty();
    EdwJourneyOpsOverlay ops = overlay != null ? overlay : EdwJourneyOpsOverlay.empty();
    String groupName = Const.NVL(safe.groupName(), "");
    String groupLabel =
        Utils.isEmpty(groupName)
            ? BaseMessages.getString(PKG, "EdwJourneyTreeBuilder.Group.Empty")
            : groupName;
    groupLabel =
        EdwJourneyOpsDecorations.decorate(groupLabel, EdwJourneyOpsDecorations.load(ops.load()));

    List<EdwJourneyTreeNode> stages = new ArrayList<>();
    stages.add(sourcesStage(safe));
    stages.add(controlsStage(safe, ops));
    stages.add(
        modelStage(
            EdwJourneyStage.DATA_VAULT,
            "EdwJourneyTreeBuilder.Stage.DataVault",
            safe.dataVaultModels(),
            ops));
    stages.add(
        modelStage(
            EdwJourneyStage.BUSINESS_VAULT,
            "EdwJourneyTreeBuilder.Stage.BusinessVault",
            safe.businessVaultModels(),
            ops));
    stages.add(
        modelStage(
            EdwJourneyStage.DIMENSIONAL,
            "EdwJourneyTreeBuilder.Stage.Dimensional",
            safe.dimensionalModels(),
            ops));
    stages.add(
        EdwJourneyTreeNode.builder(
                Kind.STAGE,
                EdwJourneyIds.stage(EdwJourneyStage.TARGET_QUALITY),
                EdwJourneyOpsDecorations.decorate(
                    BaseMessages.getString(PKG, "EdwJourneyTreeBuilder.Stage.TargetQuality"),
                    EdwJourneyOpsDecorations.quality(ops.targetQuality())))
            .stage(EdwJourneyStage.TARGET_QUALITY)
            .description(
                BaseMessages.getString(
                    PKG, "EdwJourneyTreeBuilder.Stage.TargetQuality.Description"))
            .build());
    stages.add(orchestrationStage(safe));
    stages.add(outputsStage(safe));

    return EdwJourneyTreeNode.builder(Kind.GROUP, EdwJourneyIds.group(groupName), groupLabel)
        .catalogConnection(safe.catalogConnection())
        .description(safe.catalogConnection())
        .children(stages)
        .build();
  }

  private static EdwJourneyTreeNode sourcesStage(EdwJourneySnapshot snapshot) {
    List<EdwJourneyTreeNode> children = new ArrayList<>();
    for (ModelRef source : snapshot.sourceModels()) {
      children.add(modelNode(source));
    }
    List<EdwJourneyTreeNode> feeds = new ArrayList<>();
    for (CatalogFeed feed : snapshot.catalogFeeds()) {
      String name = feed.key() != null ? feed.key().getName() : "";
      feeds.add(
          EdwJourneyTreeNode.builder(Kind.CATALOG_FEED, EdwJourneyIds.catalogFeed(feed.key()), name)
              .catalogConnection(feed.catalogConnection())
              .catalogKey(feed.key())
              .storedPath(feed.originFilename())
              .modelType(feed.originModelType())
              .description(feed.key() != null ? feed.key().toString() : null)
              .build());
    }
    children.add(
        EdwJourneyTreeNode.builder(
                Kind.CATALOG_FEEDS,
                EdwJourneyIds.catalogFeeds(),
                BaseMessages.getString(PKG, "EdwJourneyTreeBuilder.CatalogFeeds"))
            .description(
                BaseMessages.getString(
                    PKG, "EdwJourneyTreeBuilder.CatalogFeeds.Count", feeds.size()))
            .children(feeds)
            .build());
    return EdwJourneyTreeNode.builder(
            Kind.STAGE,
            EdwJourneyIds.stage(EdwJourneyStage.SOURCES),
            BaseMessages.getString(PKG, "EdwJourneyTreeBuilder.Stage.Sources"))
        .stage(EdwJourneyStage.SOURCES)
        .children(children)
        .build();
  }

  private static EdwJourneyTreeNode controlsStage(
      EdwJourneySnapshot snapshot, EdwJourneyOpsOverlay overlay) {
    List<EdwJourneyTreeNode> children = new ArrayList<>();
    children.add(
        controlNode(
            EdwJourneyControl.HARVEST,
            "EdwJourneyTreeBuilder.Control.Harvest",
            "EdwJourneyTreeBuilder.Control.Harvest.Description",
            EdwJourneyOpsDecorations.harvest(overlay.harvest())));
    children.add(
        controlNode(
            EdwJourneyControl.SCHEMA_GATE,
            "EdwJourneyTreeBuilder.Control.SchemaGate",
            "EdwJourneyTreeBuilder.Control.SchemaGate.Description",
            null));
    children.add(
        controlNode(
            EdwJourneyControl.SOURCE_QUALITY,
            "EdwJourneyTreeBuilder.Control.SourceQuality",
            "EdwJourneyTreeBuilder.Control.SourceQuality.Description",
            EdwJourneyOpsDecorations.quality(overlay.sourceQuality())));

    List<EdwJourneyTreeNode> versions = new ArrayList<>();
    for (String tag : snapshot.catalogVersionTags()) {
      versions.add(
          EdwJourneyTreeNode.builder(Kind.CATALOG_VERSION, EdwJourneyIds.catalogVersion(tag), tag)
              .catalogConnection(snapshot.catalogConnection())
              .build());
    }
    children.add(
        EdwJourneyTreeNode.builder(
                Kind.CONTROL,
                EdwJourneyIds.control(EdwJourneyControl.CATALOG_VERSION),
                BaseMessages.getString(PKG, "EdwJourneyTreeBuilder.Control.CatalogVersion"))
            .control(EdwJourneyControl.CATALOG_VERSION)
            .catalogConnection(snapshot.catalogConnection())
            .description(
                BaseMessages.getString(
                    PKG, "EdwJourneyTreeBuilder.Control.CatalogVersion.Description"))
            .children(versions)
            .build());
    return EdwJourneyTreeNode.builder(
            Kind.STAGE,
            EdwJourneyIds.stage(EdwJourneyStage.CONTROLS),
            BaseMessages.getString(PKG, "EdwJourneyTreeBuilder.Stage.Controls"))
        .stage(EdwJourneyStage.CONTROLS)
        .children(children)
        .build();
  }

  private static EdwJourneyTreeNode controlNode(
      EdwJourneyControl control, String labelKey, String descriptionKey, String decoration) {
    return EdwJourneyTreeNode.builder(
            Kind.CONTROL,
            EdwJourneyIds.control(control),
            EdwJourneyOpsDecorations.decorate(BaseMessages.getString(PKG, labelKey), decoration))
        .control(control)
        .description(BaseMessages.getString(PKG, descriptionKey))
        .build();
  }

  private static EdwJourneyTreeNode modelStage(
      EdwJourneyStage stage, String labelKey, List<ModelRef> models, EdwJourneyOpsOverlay overlay) {
    List<EdwJourneyTreeNode> children = new ArrayList<>();
    for (ModelRef model : models) {
      children.add(modelNode(model, overlay));
    }
    return EdwJourneyTreeNode.builder(
            Kind.STAGE, EdwJourneyIds.stage(stage), BaseMessages.getString(PKG, labelKey))
        .stage(stage)
        .children(children)
        .build();
  }

  private static EdwJourneyTreeNode modelNode(ModelRef model) {
    return modelNode(model, EdwJourneyOpsOverlay.empty());
  }

  private static EdwJourneyTreeNode modelNode(ModelRef model, EdwJourneyOpsOverlay overlay) {
    Kind kind =
        EdwJourneySnapshot.MODEL_TYPE_SOURCE.equals(model.modelType())
            ? Kind.SOURCE_MODEL
            : Kind.MODEL;
    String id =
        kind == Kind.SOURCE_MODEL
            ? EdwJourneyIds.sourceModel(model.storedPath())
            : EdwJourneyIds.model(model.modelType(), model.storedPath());
    List<EdwJourneyTreeNode> tables = new ArrayList<>();
    for (String tableName : model.tableNames()) {
      tables.add(
          EdwJourneyTreeNode.builder(
                  Kind.MODEL_TABLE,
                  EdwJourneyIds.modelTable(model.modelType(), model.storedPath(), tableName),
                  tableName)
              .storedPath(model.storedPath())
              .modelType(model.modelType())
              .tableName(tableName)
              .build());
    }
    String label =
        Utils.isEmpty(model.displayName())
            ? EdwJourneyDisplayNames.basenameWithoutExtension(model.storedPath())
            : model.displayName();
    String opsType = EdwJourneyOpsOverlayLoader.opsModelType(model.modelType());
    label =
        EdwJourneyOpsDecorations.decorate(
            label,
            EdwJourneyOpsDecorations.modelLoad(
                EdwJourneyOpsOverlayLoader.modelLoad(overlay, label, opsType)));
    return EdwJourneyTreeNode.builder(kind, id, label)
        .storedPath(model.storedPath())
        .modelType(model.modelType())
        .description(model.storedPath())
        .children(tables)
        .build();
  }

  private static EdwJourneyTreeNode orchestrationStage(EdwJourneySnapshot snapshot) {
    List<EdwJourneyTreeNode> children = new ArrayList<>();
    for (WorkflowRef workflow : snapshot.workflows()) {
      List<EdwJourneyTreeNode> actions = new ArrayList<>();
      for (ActionRef action : workflow.actions()) {
        String actionLabel =
            Utils.isEmpty(action.name()) ? Const.NVL(action.type(), "") : action.name();
        actions.add(
            EdwJourneyTreeNode.builder(
                    Kind.WORKFLOW_ACTION,
                    EdwJourneyIds.workflowAction(workflow.storedPath(), action.name()),
                    actionLabel)
                .storedPath(workflow.storedPath())
                .actionType(action.type())
                .description(action.type())
                .build());
      }
      String label =
          Utils.isEmpty(workflow.workflowName())
              ? EdwJourneyDisplayNames.basenameWithoutExtension(workflow.storedPath())
              : workflow.workflowName();
      children.add(
          EdwJourneyTreeNode.builder(
                  Kind.WORKFLOW, EdwJourneyIds.workflow(workflow.storedPath()), label)
              .storedPath(workflow.storedPath())
              .description(workflow.storedPath())
              .children(actions)
              .build());
    }
    return EdwJourneyTreeNode.builder(
            Kind.STAGE,
            EdwJourneyIds.stage(EdwJourneyStage.ORCHESTRATION),
            BaseMessages.getString(PKG, "EdwJourneyTreeBuilder.Stage.Orchestration"))
        .stage(EdwJourneyStage.ORCHESTRATION)
        .children(children)
        .build();
  }

  private static EdwJourneyTreeNode outputsStage(EdwJourneySnapshot snapshot) {
    List<EdwJourneyTreeNode> children = new ArrayList<>();
    children.add(
        outputGroup(
            EdwJourneySnapshot.OUTPUT_REPORTS,
            "EdwJourneyTreeBuilder.Outputs.Reports",
            snapshot.reports()));
    children.add(
        outputGroup(
            EdwJourneySnapshot.OUTPUT_EXECUTION_MAPS,
            "EdwJourneyTreeBuilder.Outputs.ExecutionMaps",
            snapshot.executionMaps()));
    children.add(
        outputGroup(
            EdwJourneySnapshot.OUTPUT_LINEAGE_VIEWS,
            "EdwJourneyTreeBuilder.Outputs.LineageViews",
            snapshot.lineageViews()));
    return EdwJourneyTreeNode.builder(
            Kind.STAGE,
            EdwJourneyIds.stage(EdwJourneyStage.OUTPUTS),
            BaseMessages.getString(PKG, "EdwJourneyTreeBuilder.Stage.Outputs"))
        .stage(EdwJourneyStage.OUTPUTS)
        .children(children)
        .build();
  }

  private static EdwJourneyTreeNode outputGroup(
      String kind, String labelKey, List<OutputRef> files) {
    List<EdwJourneyTreeNode> children = new ArrayList<>();
    for (OutputRef file : files) {
      String label =
          Utils.isEmpty(file.displayName())
              ? EdwJourneyDisplayNames.basename(file.storedPath())
              : file.displayName();
      children.add(
          EdwJourneyTreeNode.builder(
                  Kind.OUTPUT_FILE, EdwJourneyIds.outputFile(kind, file.storedPath()), label)
              .storedPath(file.storedPath())
              .modelType(kind)
              .stage(EdwJourneyStage.OUTPUTS)
              .description(file.storedPath())
              .build());
    }
    return EdwJourneyTreeNode.builder(
            Kind.OUTPUT_GROUP,
            EdwJourneyIds.outputGroup(kind),
            BaseMessages.getString(PKG, labelKey))
        .modelType(kind)
        .stage(EdwJourneyStage.OUTPUTS)
        .children(children)
        .build();
  }
}
