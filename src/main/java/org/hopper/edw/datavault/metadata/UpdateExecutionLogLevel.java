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

import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.core.logging.LogLevel;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.engine.IWorkflowEngine;

/**
 * Log level for a generated update workflow or pipeline.
 *
 * <p>The workflow that started the update wins, so a run at Detailed or Row level is the level of
 * every generated engine. {@code requested} is used only when that workflow has no log level, for
 * example the Data Vault configuration execution log level on a run with no parent workflow.
 */
public final class UpdateExecutionLogLevel {

  private UpdateExecutionLogLevel() {}

  /**
   * Parent workflow, then {@code requested}, then the logging parent.
   *
   * <p>When the logging parent is an action whose own level differs from the workflow, the action
   * is aligned first. Workflow engines copy that parent level again when they start, which would
   * otherwise drop the level set on the engine.
   */
  public static LogLevel resolve(
      ILoggingObject parent, IWorkflowEngine<?> parentWorkflow, LogLevel requested) {
    LogLevel workflowLevel = levelOf(parentWorkflow);
    if (workflowLevel == null && parent instanceof ActionBase action) {
      workflowLevel = levelOf(action.getParentWorkflow());
    }
    if (workflowLevel != null
        && parent instanceof ActionBase action
        && !workflowLevel.equals(action.getLogLevel())) {
      action.setLogLevel(workflowLevel);
    }
    return choose(workflowLevel, levelOf(parent), requested);
  }

  static LogLevel choose(LogLevel parentWorkflowLevel, LogLevel parentLevel, LogLevel requested) {
    if (parentWorkflowLevel != null) {
      return parentWorkflowLevel;
    }
    if (requested != null) {
      return requested;
    }
    return parentLevel;
  }

  private static LogLevel levelOf(ILoggingObject loggingObject) {
    if (loggingObject == null) {
      return null;
    }
    return loggingObject.getLogLevel();
  }
}
