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
package org.hopper.edw.datavault.metrics.live;

import java.util.Date;
import lombok.Builder;
import lombok.Value;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/** Immutable context for one orchestrated model update run. */
@Value
@Builder
public class UpdateRunLiveRunContext {
  String metricsRunId;
  String modelName;
  String modelFilename;
  String stagingFolder;
  String workflowFilename;
  String workflowName;
  String actionName;
  ILogChannel log;
  LogLevel logLevel;
  Date startedAt;

  public static UpdateRunLiveRunContext from(
      String metricsRunId,
      String modelName,
      String modelFilename,
      String stagingFolder,
      ILoggingObject parent,
      IWorkflowEngine<WorkflowMeta> parentWorkflow,
      LogLevel logLevel) {
    String actionName = parent != null ? parent.getObjectName() : null;
    ILogChannel log = parent != null ? new LogChannel(parent) : null;
    String workflowFilename = resolveWorkflowFilename(parentWorkflow);
    String workflowName = resolveWorkflowName(parentWorkflow);
    return UpdateRunLiveRunContext.builder()
        .metricsRunId(metricsRunId)
        .modelName(modelName)
        .modelFilename(modelFilename)
        .stagingFolder(stagingFolder)
        .workflowFilename(workflowFilename)
        .workflowName(workflowName)
        .actionName(actionName)
        .log(log)
        .logLevel(logLevel)
        .startedAt(new Date())
        .build();
  }

  private static String resolveWorkflowFilename(IWorkflowEngine<WorkflowMeta> parentWorkflow) {
    if (parentWorkflow == null || parentWorkflow.getWorkflowMeta() == null) {
      return null;
    }
    WorkflowMeta workflowMeta = parentWorkflow.getWorkflowMeta();
    if (!Utils.isEmpty(workflowMeta.getFilename())) {
      return workflowMeta.getFilename();
    }
    return workflowMeta.getName();
  }

  private static String resolveWorkflowName(IWorkflowEngine<WorkflowMeta> parentWorkflow) {
    if (parentWorkflow == null || parentWorkflow.getWorkflowMeta() == null) {
      return null;
    }
    return parentWorkflow.getWorkflowMeta().getName();
  }
}
