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
package org.hopper.edw.datavault.hopgui.ai;

import org.apache.hop.ui.hopgui.file.pipeline.HopGuiLogBrowser;
import org.apache.hop.ui.hopgui.file.pipeline.HopGuiPipelineGraph;
import org.apache.hop.ui.hopgui.file.workflow.HopGuiWorkflowGraph;

/** Reads execution log text from the active Hop GUI pipeline or workflow log panel. */
public final class HopAiLogTextSupport {

  private HopAiLogTextSupport() {}

  public static String readPipelineLog(HopGuiPipelineGraph pipelineGraph) {
    if (pipelineGraph == null || pipelineGraph.pipelineLogDelegate == null) {
      return null;
    }
    return pipelineGraph.pipelineLogDelegate.getLoggingText();
  }

  public static String readWorkflowLog(HopGuiWorkflowGraph workflowGraph) {
    if (workflowGraph == null || workflowGraph.workflowLogDelegate == null) {
      return null;
    }
    HopGuiLogBrowser logBrowser = workflowGraph.workflowLogDelegate.getLogBrowser();
    if (logBrowser == null || logBrowser.getText() == null) {
      return null;
    }
    return logBrowser.getText().getText();
  }
}
