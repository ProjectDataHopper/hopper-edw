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
package org.apache.hop.datavault.ai.workflow;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.ai.HopAiActionPluginSupport;
import org.apache.hop.datavault.ai.HopAiProposal;
import org.apache.hop.datavault.ai.HopAiProposalParamSupport;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.workflow.WorkflowHopMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;

/** Previews and applies validated AI proposals to an open workflow. */
public final class WorkflowAiProposalApplier {

  private WorkflowAiProposalApplier() {}

  public static String preview(HopAiProposal proposal) {
    if (proposal == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(proposal.getDescription());
    if (!Utils.isEmpty(proposal.getId())) {
      sb.append(" [").append(proposal.getId()).append(']');
    }
    sb.append("\nType: ").append(proposal.getType());
    sb.append("\nRisk: ").append(proposal.getRiskLevel());
    if (proposal.getParameters() != null && !proposal.getParameters().isEmpty()) {
      sb.append("\nParameters:");
      proposal
          .getParameters()
          .forEach((k, v) -> sb.append("\n  ").append(k).append(" = ").append(v));
    }
    return sb.toString();
  }

  public static void apply(WorkflowMeta workflowMeta, List<HopAiProposal> proposals)
      throws HopException {
    apply(workflowMeta, proposals, null);
  }

  public static void apply(WorkflowMeta workflowMeta, List<HopAiProposal> proposals, HopGui hopGui)
      throws HopException {
    if (workflowMeta == null) {
      throw new HopException("No workflow is open");
    }
    if (proposals == null || proposals.isEmpty()) {
      return;
    }
    for (int i = 0; i < proposals.size(); i++) {
      boolean chainUndo = hopGui != null && i < proposals.size() - 1;
      applyOne(workflowMeta, proposals.get(i), hopGui, chainUndo);
    }
  }

  private static void applyOne(
      WorkflowMeta workflowMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo)
      throws HopException {
    if (proposal == null || proposal.getType() == null) {
      return;
    }
    switch (proposal.getType()) {
      case ADD_ACTION -> addAction(workflowMeta, proposal, hopGui, chainUndo);
      case DELETE_ACTION -> deleteAction(workflowMeta, proposal, hopGui, chainUndo);
      case RENAME_ACTION -> renameAction(workflowMeta, proposal, hopGui, chainUndo);
      case ADD_WORKFLOW_HOP -> addWorkflowHop(workflowMeta, proposal, hopGui, chainUndo);
      case DELETE_WORKFLOW_HOP -> deleteWorkflowHop(workflowMeta, proposal, hopGui, chainUndo);
      case SET_ACTION_LOCATION -> setActionLocation(workflowMeta, proposal, hopGui, chainUndo);
      case ADD_WORKFLOW_NOTE -> addWorkflowNote(workflowMeta, proposal, hopGui, chainUndo);
      default -> throw new HopException("Unsupported proposal type: " + proposal.getType());
    }
  }

  private static void addAction(
      WorkflowMeta workflowMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo)
      throws HopException {
    String pluginId = proposal.parameter("actionPluginId");
    String name = proposal.parameter("name");
    HopAiProposalParamSupport.Location location = HopAiProposalParamSupport.parseLocation(proposal);
    ActionMeta actionMeta = HopAiActionPluginSupport.newActionMeta(pluginId, name);
    actionMeta.setLocation(new Point(location.x(), location.y()));
    workflowMeta.addAction(actionMeta);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoNew(
          workflowMeta,
          new ActionMeta[] {actionMeta},
          new int[] {workflowMeta.indexOfAction(actionMeta)},
          chainUndo);
    }
  }

  private static void deleteAction(
      WorkflowMeta workflowMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    String actionName = proposal.parameter("actionName");
    ActionMeta action = workflowMeta.findAction(actionName);
    List<WorkflowHopMeta> hopsToRemove = new ArrayList<>();
    for (WorkflowHopMeta hop : workflowMeta.getWorkflowHops()) {
      if (hop.getFromAction().equals(action) || hop.getToAction().equals(action)) {
        hopsToRemove.add(hop);
      }
    }
    for (WorkflowHopMeta hop : hopsToRemove) {
      int hopIndex = workflowMeta.indexOfWorkflowHop(hop);
      if (hopGui != null) {
        hopGui.undoDelegate.addUndoDelete(
            workflowMeta, new WorkflowHopMeta[] {hop}, new int[] {hopIndex}, chainUndo);
      }
      workflowMeta.removeWorkflowHop(hop);
    }
    int actionIndex = workflowMeta.indexOfAction(action);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoDelete(
          workflowMeta, new ActionMeta[] {action}, new int[] {actionIndex}, chainUndo);
    }
    workflowMeta.removeAction(actionIndex);
  }

  private static void renameAction(
      WorkflowMeta workflowMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    ActionMeta action = workflowMeta.findAction(proposal.parameter("actionName"));
    ActionMeta before = (ActionMeta) action.clone();
    action.setName(proposal.parameter("newName"));
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoChange(
          workflowMeta,
          new ActionMeta[] {before},
          new ActionMeta[] {action},
          new int[] {workflowMeta.indexOfAction(action)},
          chainUndo);
    }
    workflowMeta.setChanged();
  }

  private static void addWorkflowHop(
      WorkflowMeta workflowMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    ActionMeta from = workflowMeta.findAction(proposal.parameter("fromAction"));
    ActionMeta to = workflowMeta.findAction(proposal.parameter("toAction"));
    WorkflowHopMeta hop = new WorkflowHopMeta(from, to);
    hop.setUnconditional(
        WorkflowAiProposalValidator.parseUnconditional(proposal.parameter("unconditional")));
    hop.setEvaluation(
        WorkflowAiProposalValidator.parseEvaluation(proposal.parameter("evaluation")));
    workflowMeta.addWorkflowHop(hop);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoNew(
          workflowMeta,
          new WorkflowHopMeta[] {hop},
          new int[] {workflowMeta.indexOfWorkflowHop(hop)},
          chainUndo);
    }
  }

  private static void deleteWorkflowHop(
      WorkflowMeta workflowMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    ActionMeta from = workflowMeta.findAction(proposal.parameter("fromAction"));
    ActionMeta to = workflowMeta.findAction(proposal.parameter("toAction"));
    WorkflowHopMeta hop = workflowMeta.findWorkflowHop(from, to);
    int hopIndex = workflowMeta.indexOfWorkflowHop(hop);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoDelete(
          workflowMeta, new WorkflowHopMeta[] {hop}, new int[] {hopIndex}, chainUndo);
    }
    workflowMeta.removeWorkflowHop(hop);
  }

  private static void setActionLocation(
      WorkflowMeta workflowMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    ActionMeta action = workflowMeta.findAction(proposal.parameter("actionName"));
    HopAiProposalParamSupport.Location location = HopAiProposalParamSupport.parseLocation(proposal);
    Point previous = new Point(action.getLocation().x, action.getLocation().y);
    action.setLocation(new Point(location.x(), location.y()));
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoPosition(
          workflowMeta,
          new ActionMeta[] {action},
          new int[] {workflowMeta.indexOfAction(action)},
          new Point[] {previous},
          new Point[] {action.getLocation()},
          chainUndo);
    }
    workflowMeta.setChanged();
  }

  private static void addWorkflowNote(
      WorkflowMeta workflowMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    HopAiProposalParamSupport.Location location = HopAiProposalParamSupport.parseLocation(proposal);
    NotePadMeta note = new NotePadMeta();
    note.setNote(proposal.parameter("text").trim());
    note.setLocation(new Point(location.x(), location.y()));
    note.width =
        HopAiProposalParamSupport.parseOptionalSize(proposal.parameter("width"), note.width);
    note.height =
        HopAiProposalParamSupport.parseOptionalSize(proposal.parameter("height"), note.height);
    workflowMeta.addNote(note);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoNew(
          workflowMeta,
          new NotePadMeta[] {note},
          new int[] {workflowMeta.indexOfNote(note)},
          chainUndo);
    }
  }
}
