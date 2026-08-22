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
package org.apache.hop.datavault.metadata;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.actions.start.ActionStart;

/**
 * Builds serial multi-source update workflows so hub/link sources do not race when loading the same
 * physical table in parallel.
 */
public final class DvMultiSourceUpdateWorkflowSupport {

  public static final String PIPELINE_ACTION_ID = DvUpdateWorkflowSupport.PIPELINE_ACTION_ID;

  private DvMultiSourceUpdateWorkflowSupport() {}

  /**
   * When more than one per-source pipeline is generated, wraps them in a single success-chained
   * workflow. Returns an empty list when there is nothing to serialize (0 or 1 pipelines).
   */
  /** Creates PIPELINE workflow actions (plugin registry or test-supplied). */
  @FunctionalInterface
  public interface PipelineActionFactory {
    IAction create(String actionName) throws HopException;
  }

  public static List<WorkflowMeta> buildSerialWorkflowsIfMultiSource(
      String workflowName, List<PipelineMeta> sourcePipelines) throws HopException {
    return buildSerialWorkflowsIfMultiSource(workflowName, sourcePipelines, null);
  }

  public static List<WorkflowMeta> buildSerialWorkflowsIfMultiSource(
      String workflowName, List<PipelineMeta> sourcePipelines, PipelineActionFactory actionFactory)
      throws HopException {
    if (sourcePipelines == null || sourcePipelines.size() <= 1) {
      return List.of();
    }
    List<PipelineMeta> nonNull = new ArrayList<>();
    for (PipelineMeta pipelineMeta : sourcePipelines) {
      if (pipelineMeta != null) {
        nonNull.add(pipelineMeta);
      }
    }
    if (nonNull.size() <= 1) {
      return List.of();
    }
    return List.of(buildSerialSourceWorkflow(workflowName, nonNull, null, actionFactory));
  }

  /**
   * Builds {@code Start → pipeline1 → pipeline2 → …} with success hops only.
   *
   * <p>Pipeline action filenames are set to {@code pipelineName + .hpl} placeholders. Staging
   * rewrites them to absolute staged paths via {@link #applyStagedPipelineFilenames}.
   *
   * @param pipelineRunConfiguration optional; when non-empty, applied to each pipeline action
   */
  public static WorkflowMeta buildSerialSourceWorkflow(
      String workflowName, List<PipelineMeta> sourcePipelines, String pipelineRunConfiguration)
      throws HopException {
    return buildSerialSourceWorkflow(workflowName, sourcePipelines, pipelineRunConfiguration, null);
  }

  public static WorkflowMeta buildSerialSourceWorkflow(
      String workflowName,
      List<PipelineMeta> sourcePipelines,
      String pipelineRunConfiguration,
      PipelineActionFactory actionFactory)
      throws HopException {
    if (sourcePipelines == null || sourcePipelines.isEmpty()) {
      throw new HopException(
          "At least one source pipeline is required for a multi-source workflow");
    }

    WorkflowMeta workflowMeta = new WorkflowMeta();
    workflowMeta.setName(Utils.isEmpty(workflowName) ? "multi-source-update" : workflowName);

    ActionStart startAction = new ActionStart("Start");
    ActionMeta previousAction = new ActionMeta(startAction);
    previousAction.setLocation(50, 50);
    workflowMeta.addAction(previousAction);

    int x = 250;
    int y = 50;
    for (PipelineMeta pipelineMeta : sourcePipelines) {
      if (pipelineMeta == null || Utils.isEmpty(pipelineMeta.getName())) {
        continue;
      }
      String pipelineName = pipelineMeta.getName();
      String placeholderFilename = pipelineName + PipelineMeta.PIPELINE_EXTENSION;
      ActionMeta pipelineActionMeta =
          newPipelineActionMeta(
              "run_" + sanitizeActionName(pipelineName),
              placeholderFilename,
              pipelineRunConfiguration,
              actionFactory);
      pipelineActionMeta.setLocation(x, y);
      workflowMeta.addAction(pipelineActionMeta);
      workflowMeta.addWorkflowHop(new WorkflowHopMeta(previousAction, pipelineActionMeta));
      previousAction = pipelineActionMeta;
      x += 200;
    }

    return workflowMeta;
  }

  /** Default multi-source workflow name for a hub or link table. */
  public static String defaultWorkflowName(IDvTable table, String pipelineNamePrefix) {
    String tablePart = table != null && !Utils.isEmpty(table.getName()) ? table.getName() : "table";
    String prefix = Utils.isEmpty(pipelineNamePrefix) ? "" : pipelineNamePrefix;
    return prefix + tablePart + "-multi-source";
  }

  /**
   * Collects pipeline basenames referenced by PIPELINE actions in the workflow (e.g. {@code
   * hub-x_src.hpl}).
   */
  public static Set<String> collectReferencedPipelineFilenames(WorkflowMeta workflowMeta) {
    Set<String> names = new LinkedHashSet<>();
    if (workflowMeta == null || workflowMeta.getActions() == null) {
      return names;
    }
    for (ActionMeta actionMeta : workflowMeta.getActions()) {
      if (actionMeta == null || actionMeta.getAction() == null) {
        continue;
      }
      IAction action = actionMeta.getAction();
      if (!PIPELINE_ACTION_ID.equalsIgnoreCase(action.getPluginId())) {
        continue;
      }
      String filename = readFilename(action);
      if (!Utils.isEmpty(filename)) {
        names.add(basename(filename));
      }
    }
    return names;
  }

  /**
   * Sets each PIPELINE action filename from a map of pipeline basename → staged absolute path. Map
   * keys may be with or without the {@code .hpl} extension.
   */
  public static void applyStagedPipelineFilenames(
      WorkflowMeta workflowMeta, Map<String, String> stagedPathByPipelineBasename)
      throws HopException {
    if (workflowMeta == null
        || stagedPathByPipelineBasename == null
        || stagedPathByPipelineBasename.isEmpty()) {
      return;
    }
    for (ActionMeta actionMeta : workflowMeta.getActions()) {
      if (actionMeta == null || actionMeta.getAction() == null) {
        continue;
      }
      IAction action = actionMeta.getAction();
      if (!PIPELINE_ACTION_ID.equalsIgnoreCase(action.getPluginId())) {
        continue;
      }
      String current = readFilename(action);
      if (Utils.isEmpty(current)) {
        continue;
      }
      String key = basename(current);
      String staged = lookupStagedPath(stagedPathByPipelineBasename, key);
      if (!Utils.isEmpty(staged)) {
        DvBulkLoadActionSupport.invoke(action, "setFilename", String.class, staged);
      }
    }
  }

  /** Applies the pipeline run configuration to every PIPELINE action in the workflow. */
  public static void applyPipelineRunConfiguration(
      WorkflowMeta workflowMeta, String pipelineRunConfiguration) throws HopException {
    if (workflowMeta == null || Utils.isEmpty(pipelineRunConfiguration)) {
      return;
    }
    for (ActionMeta actionMeta : workflowMeta.getActions()) {
      if (actionMeta == null || actionMeta.getAction() == null) {
        continue;
      }
      IAction action = actionMeta.getAction();
      if (!PIPELINE_ACTION_ID.equalsIgnoreCase(action.getPluginId())) {
        continue;
      }
      DvBulkLoadActionSupport.invoke(
          action, "setRunConfiguration", String.class, pipelineRunConfiguration);
    }
  }

  /**
   * Partitions generated artifacts for a table: when multi-source workflows exist, nested pipelines
   * are excluded from the free parallel list.
   */
  public record UpdateArtifacts(
      List<PipelineMeta> freePipelines,
      List<PipelineMeta> nestedPipelines,
      List<WorkflowMeta> multiSourceWorkflows) {

    public static UpdateArtifacts of(List<PipelineMeta> pipelines, List<WorkflowMeta> workflows) {
      List<PipelineMeta> pipelineList = pipelines == null ? List.of() : new ArrayList<>(pipelines);
      List<WorkflowMeta> workflowList = workflows == null ? List.of() : new ArrayList<>(workflows);
      if (workflowList.isEmpty()) {
        return new UpdateArtifacts(List.copyOf(pipelineList), List.of(), List.of());
      }

      Set<String> nestedNames = new LinkedHashSet<>();
      for (WorkflowMeta workflowMeta : workflowList) {
        for (String filename : collectReferencedPipelineFilenames(workflowMeta)) {
          nestedNames.add(stripPipelineExtension(filename));
        }
      }

      List<PipelineMeta> free = new ArrayList<>();
      List<PipelineMeta> nested = new ArrayList<>();
      for (PipelineMeta pipelineMeta : pipelineList) {
        if (pipelineMeta == null || Utils.isEmpty(pipelineMeta.getName())) {
          continue;
        }
        if (nestedNames.contains(pipelineMeta.getName())) {
          nested.add(pipelineMeta);
        } else {
          free.add(pipelineMeta);
        }
      }
      // If name matching failed but workflows exist, treat all pipelines as nested (safe default).
      if (nested.isEmpty() && !pipelineList.isEmpty()) {
        for (PipelineMeta pipelineMeta : pipelineList) {
          if (pipelineMeta != null) {
            nested.add(pipelineMeta);
          }
        }
        free.clear();
      }
      return new UpdateArtifacts(List.copyOf(free), List.copyOf(nested), List.copyOf(workflowList));
    }
  }

  /** Builds basename → staged path after nested pipelines have been written. */
  public static Map<String, String> mapStagedPipelinePaths(List<PipelineMeta> nestedPipelines) {
    Map<String, String> map = new LinkedHashMap<>();
    if (nestedPipelines == null) {
      return map;
    }
    for (PipelineMeta pipelineMeta : nestedPipelines) {
      if (pipelineMeta == null || Utils.isEmpty(pipelineMeta.getName())) {
        continue;
      }
      String path = pipelineMeta.getFilename();
      if (Utils.isEmpty(path)) {
        path = pipelineMeta.getName() + PipelineMeta.PIPELINE_EXTENSION;
      }
      map.put(pipelineMeta.getName(), path);
      map.put(pipelineMeta.getName() + PipelineMeta.PIPELINE_EXTENSION, path);
      map.put(basename(path), path);
    }
    return map;
  }

  private static String lookupStagedPath(Map<String, String> map, String key) {
    if (map.containsKey(key)) {
      return map.get(key);
    }
    String withoutExt = stripPipelineExtension(key);
    if (map.containsKey(withoutExt)) {
      return map.get(withoutExt);
    }
    return map.get(withoutExt + PipelineMeta.PIPELINE_EXTENSION);
  }

  /** Creates a PIPELINE workflow action with optional run configuration. */
  public static ActionMeta newPipelineActionMeta(
      String actionName,
      String pipelineFilename,
      String pipelineRunConfiguration,
      PipelineActionFactory actionFactory)
      throws HopException {
    IAction action =
        actionFactory != null
            ? actionFactory.create(actionName)
            : DvBulkLoadActionSupport.newConfiguredAction(PIPELINE_ACTION_ID, actionName);
    DvBulkLoadActionSupport.invoke(action, "setFilename", String.class, pipelineFilename);
    if (!Utils.isEmpty(pipelineRunConfiguration)) {
      DvBulkLoadActionSupport.invoke(
          action, "setRunConfiguration", String.class, pipelineRunConfiguration);
    }
    try {
      DvBulkLoadActionSupport.invoke(action, "setWaitingToFinish", boolean.class, true);
      DvBulkLoadActionSupport.invoke(action, "setClearResultRows", boolean.class, false);
      DvBulkLoadActionSupport.invoke(action, "setClearResultFiles", boolean.class, false);
    } catch (HopException ignored) {
      // Test stubs may not implement every ActionPipeline setter.
    }
    return new ActionMeta(action);
  }

  private static String readFilename(IAction action) {
    if (action == null) {
      return null;
    }
    try {
      Method method = action.getClass().getMethod("getFilename");
      Object value = method.invoke(action);
      return value != null ? value.toString() : null;
    } catch (Exception e) {
      try {
        Method method = action.getClass().getMethod("getFileName");
        Object value = method.invoke(action);
        return value != null ? value.toString() : null;
      } catch (Exception ignored) {
        return null;
      }
    }
  }

  private static String basename(String path) {
    if (Utils.isEmpty(path)) {
      return path;
    }
    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return slash >= 0 ? path.substring(slash + 1) : path;
  }

  private static String stripPipelineExtension(String name) {
    if (Utils.isEmpty(name)) {
      return name;
    }
    if (name.regionMatches(
        true,
        name.length() - PipelineMeta.PIPELINE_EXTENSION.length(),
        PipelineMeta.PIPELINE_EXTENSION,
        0,
        PipelineMeta.PIPELINE_EXTENSION.length())) {
      return name.substring(0, name.length() - PipelineMeta.PIPELINE_EXTENSION.length());
    }
    return name;
  }

  private static String sanitizeActionName(String name) {
    if (Utils.isEmpty(name)) {
      return "pipeline";
    }
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
