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
package org.hopper.edw.datavault.hopgui.metrics;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.workflow.WorkflowPainter;

/** Paints the live model-update badge on executing workflow actions. */
@ExtensionPoint(
    id = "UpdateRunLiveWorkflowPainterExtensionPoint",
    extensionPointId = "WorkflowPainterEnd",
    description = "Draws live update status on executing DV/BV/DM update workflow actions")
public class UpdateRunLiveWorkflowPainterExtensionPoint
    implements IExtensionPoint<WorkflowPainter> {

  @Override
  public void callExtensionPoint(ILogChannel log, IVariables variables, WorkflowPainter painter)
      throws HopException {
    UpdateRunLiveWorkflowPaintSupport.paintWorkflowEnd(painter);
  }
}
