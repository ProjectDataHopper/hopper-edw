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
package org.hopper.edw.datavault.workflow;

import java.nio.file.Path;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.workflow.WorkflowMeta;

/**
 * Resolves workflow action variables for referenced-object context menus.
 *
 * <p>Hop calls {@code isReferencedObjectEnabled()} on the action itself, which often lacks project
 * variables such as {@code PROJECT_HOME}. This helper merges the preferred variable scope with the
 * action scope and, when needed, derives {@code PROJECT_HOME} from the parent workflow filename.
 */
public final class WorkflowReferencedObjectVariableSupport {

  public static final String VARIABLE_PROJECT_HOME = "PROJECT_HOME";

  private WorkflowReferencedObjectVariableSupport() {}

  public static IVariables effectiveVariables(
      IVariables actionVariables, WorkflowMeta parentWorkflowMeta) {
    return effectiveVariables(actionVariables, parentWorkflowMeta, null);
  }

  public static IVariables effectiveVariables(
      IVariables actionVariables, WorkflowMeta parentWorkflowMeta, IVariables preferred) {
    Variables effective = new Variables();
    if (preferred != null) {
      effective.initializeFrom(preferred);
    } else if (actionVariables != null) {
      effective.initializeFrom(actionVariables);
    }
    enrichProjectHome(effective, actionVariables, parentWorkflowMeta);
    return effective;
  }

  static void enrichProjectHome(
      Variables effective, IVariables actionVariables, WorkflowMeta parentWorkflowMeta) {
    String resolvedProjectHome = effective.resolve("${" + VARIABLE_PROJECT_HOME + "}");
    if (!Utils.isEmpty(resolvedProjectHome) && !resolvedProjectHome.contains("${")) {
      return;
    }
    String projectHome = findProjectHomeInParentChain(actionVariables);
    if (Utils.isEmpty(projectHome) && parentWorkflowMeta != null) {
      projectHome = deriveProjectHomeFromWorkflowFilename(parentWorkflowMeta.getFilename());
    }
    if (!Utils.isEmpty(projectHome)) {
      effective.setVariable(VARIABLE_PROJECT_HOME, projectHome);
    }
  }

  static String findProjectHomeInParentChain(IVariables variables) {
    for (IVariables current = variables; current != null; current = current.getParentVariables()) {
      String projectHome = current.getVariable(VARIABLE_PROJECT_HOME);
      if (!Utils.isEmpty(projectHome)) {
        return projectHome;
      }
    }
    return null;
  }

  static String deriveProjectHomeFromWorkflowFilename(String workflowFilename) {
    if (Utils.isEmpty(workflowFilename) || workflowFilename.contains("${")) {
      return null;
    }
    try {
      Path workflowPath = Path.of(workflowFilename).normalize().toAbsolutePath();
      Path parent = workflowPath.getParent();
      if (parent != null && "workflows".equalsIgnoreCase(parent.getFileName().toString())) {
        Path projectHome = parent.getParent();
        if (projectHome != null) {
          return projectHome.toString();
        }
      }
    } catch (Exception ignored) {
      // Fall through to null.
    }
    return null;
  }
}
