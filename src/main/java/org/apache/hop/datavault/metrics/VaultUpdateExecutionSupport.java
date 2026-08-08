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
package org.apache.hop.datavault.metrics;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/** Reads and writes vault update execution variables used by Begin/End workflow actions. */
public final class VaultUpdateExecutionSupport {

  private static final String ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

  private VaultUpdateExecutionSupport() {}

  public static String defaultExecutionIdVariableName() {
    return DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID;
  }

  public static String defaultStartedAtVariableName() {
    return DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_STARTED_AT;
  }

  public static String resolveExecutionId(IVariables variables, String variableName) {
    if (variables == null) {
      return null;
    }
    return variables.getVariable(resolveVariableName(variableName));
  }

  public static String resolveExecutionId(
      IVariables variables,
      String variableName,
      boolean useWorkflowLogChannelId,
      String workflowLogChannelId) {
    String fromVariable = resolveExecutionId(variables, variableName);
    if (!Utils.isEmpty(fromVariable)) {
      return fromVariable;
    }
    if (useWorkflowLogChannelId && !Utils.isEmpty(workflowLogChannelId)) {
      return workflowLogChannelId;
    }
    return null;
  }

  public static String resolveWorkflowLogChannelId(IWorkflowEngine<WorkflowMeta> parentWorkflow) {
    if (parentWorkflow == null) {
      return null;
    }
    String logChannelId = parentWorkflow.getLogChannelId();
    return Utils.isEmpty(logChannelId) ? null : logChannelId;
  }

  public static Date resolveStartedAt(IVariables variables, String variableName) {
    if (variables == null) {
      return null;
    }
    String name =
        Utils.isEmpty(variableName)
            ? DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_STARTED_AT
            : variableName.trim();
    String value = variables.getVariable(name);
    if (Utils.isEmpty(value)) {
      return null;
    }
    SimpleDateFormat format = new SimpleDateFormat(ISO_8601_PATTERN, Locale.ROOT);
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    try {
      return format.parse(value);
    } catch (ParseException e) {
      return null;
    }
  }

  /**
   * Assigns the execution id on the parent workflow. Child workflows and pipelines inherit the
   * variable through normal Hop variable propagation when the workflow engine re-copies variables
   * onto each action ({@code action.copyFrom(workflow)}).
   *
   * <p>Actions that spawn child actions <em>programmatically</em> (e.g. Update Resource Definition
   * Group) must also call {@link #propagateExecutionVariables(IVariables, IVariables)} onto their
   * own variable space and each child — Hop {@code Variables.getVariable} does not walk a live
   * parent map after {@code initializeFrom}, so setting the workflow alone is not enough for those
   * children.
   */
  public static String beginExecution(
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      String executionIdVariableName,
      boolean reuseExistingExecutionId,
      boolean useWorkflowLogChannelId) {
    if (parentWorkflow == null) {
      return null;
    }
    String executionIdVariable = resolveVariableName(executionIdVariableName);
    String existing = parentWorkflow.getVariable(executionIdVariable);
    if (reuseExistingExecutionId && !Utils.isEmpty(existing)) {
      return existing;
    }

    String executionId;
    if (useWorkflowLogChannelId) {
      executionId = resolveWorkflowLogChannelId(parentWorkflow);
      if (Utils.isEmpty(executionId)) {
        return null;
      }
    } else {
      executionId = UUID.randomUUID().toString();
    }
    parentWorkflow.setVariable(executionIdVariable, executionId);
    parentWorkflow.setVariable(
        DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_STARTED_AT, formatStartedAt(new Date()));
    return executionId;
  }

  /**
   * Copies vault-update execution correlation variables from {@code from} onto {@code to}. Use after
   * {@link #beginExecution} when the receiving variables space was already initialized and will not
   * re-copy from the workflow engine.
   */
  public static void propagateExecutionVariables(IVariables from, IVariables to) {
    if (from == null || to == null || from == to) {
      return;
    }
    String executionId = from.getVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID);
    if (!Utils.isEmpty(executionId)) {
      to.setVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID, executionId);
    }
    String startedAt =
        from.getVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_STARTED_AT);
    if (!Utils.isEmpty(startedAt)) {
      to.setVariable(DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_STARTED_AT, startedAt);
    }
  }

  public static String formatStartedAt(Date value) {
    if (value == null) {
      return null;
    }
    SimpleDateFormat format = new SimpleDateFormat(ISO_8601_PATTERN, Locale.ROOT);
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    return format.format(value);
  }

  private static String resolveVariableName(String variableName) {
    return Utils.isEmpty(variableName)
        ? DvUpdateMetricsConstants.VAR_WORKFLOW_EXECUTION_ID
        : variableName.trim();
  }
}
