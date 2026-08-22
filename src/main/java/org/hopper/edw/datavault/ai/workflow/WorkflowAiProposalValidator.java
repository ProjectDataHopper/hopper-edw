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
package org.hopper.edw.datavault.ai.workflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.plugins.ActionPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.ai.HopAiProposal;
import org.hopper.edw.datavault.ai.HopAiProposalParamSupport;
import org.hopper.edw.datavault.ai.HopAiProposalValidation;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;

/** Validates AI workflow proposals against the open graph before the user applies them. */
public final class WorkflowAiProposalValidator {

  private WorkflowAiProposalValidator() {}

  public static List<HopAiProposalValidation.Result> validate(
      WorkflowMeta workflowMeta, List<HopAiProposal> proposals) {
    List<HopAiProposalValidation.Result> results = new ArrayList<>();
    if (proposals == null) {
      return results;
    }
    Set<String> reservedNames = new HashSet<>();
    for (HopAiProposal proposal : proposals) {
      results.add(validateOne(workflowMeta, proposal, reservedNames));
    }
    return results;
  }

  private static HopAiProposalValidation.Result validateOne(
      WorkflowMeta workflowMeta, HopAiProposal proposal, Set<String> reservedNames) {
    if (proposal == null || proposal.getType() == null) {
      return blocked(proposal, "Missing proposal type");
    }
    if (!proposal.isWorkflowType()) {
      return blocked(proposal, "Not a workflow proposal type: " + proposal.getType());
    }
    if (workflowMeta == null) {
      return blocked(proposal, "No workflow is open");
    }
    return switch (proposal.getType()) {
      case ADD_ACTION -> validateAddAction(workflowMeta, proposal, reservedNames);
      case DELETE_ACTION -> validateDeleteAction(workflowMeta, proposal);
      case RENAME_ACTION -> validateRenameAction(workflowMeta, proposal, reservedNames);
      case ADD_WORKFLOW_HOP -> validateAddWorkflowHop(workflowMeta, proposal, reservedNames);
      case DELETE_WORKFLOW_HOP -> validateDeleteWorkflowHop(workflowMeta, proposal);
      case SET_ACTION_LOCATION -> validateSetActionLocation(workflowMeta, proposal);
      case ADD_WORKFLOW_NOTE -> validateAddWorkflowNote(proposal);
      default -> blocked(proposal, "Unsupported proposal type");
    };
  }

  private static HopAiProposalValidation.Result validateAddAction(
      WorkflowMeta workflowMeta, HopAiProposal proposal, Set<String> reservedNames) {
    String pluginId = proposal.parameter("actionPluginId");
    String name = proposal.parameter("name");
    if (Utils.isEmpty(pluginId)) {
      return blocked(proposal, "actionPluginId is required");
    }
    if (Utils.isEmpty(name)) {
      return blocked(proposal, "name is required");
    }
    if (PluginRegistry.getInstance().findPluginWithId(ActionPluginType.class, pluginId) == null) {
      return blocked(proposal, "Unknown action plugin: " + pluginId);
    }
    if (workflowMeta.findAction(name) != null || reservedNames.contains(name.trim())) {
      return blocked(proposal, "Action name already exists: " + name);
    }
    if (!HopAiProposalParamSupport.parseLocation(proposal).isValid()) {
      return blocked(proposal, "locationX and locationY must be integers");
    }
    reservedNames.add(name.trim());
    return ok(proposal);
  }

  private static HopAiProposalValidation.Result validateDeleteAction(
      WorkflowMeta workflowMeta, HopAiProposal proposal) {
    String actionName = proposal.parameter("actionName");
    if (Utils.isEmpty(actionName)) {
      return blocked(proposal, "actionName is required");
    }
    if (workflowMeta.findAction(actionName) == null) {
      return blocked(proposal, "Action not found: " + actionName);
    }
    return ok(proposal);
  }

  private static HopAiProposalValidation.Result validateRenameAction(
      WorkflowMeta workflowMeta, HopAiProposal proposal, Set<String> reservedNames) {
    String actionName = proposal.parameter("actionName");
    String newName = proposal.parameter("newName");
    if (Utils.isEmpty(actionName)) {
      return blocked(proposal, "actionName is required");
    }
    if (Utils.isEmpty(newName)) {
      return blocked(proposal, "newName is required");
    }
    if (workflowMeta.findAction(actionName) == null) {
      return blocked(proposal, "Action not found: " + actionName);
    }
    if (!actionName.trim().equals(newName.trim())
        && (workflowMeta.findAction(newName) != null || reservedNames.contains(newName.trim()))) {
      return blocked(proposal, "Action name already exists: " + newName);
    }
    reservedNames.add(newName.trim());
    return ok(proposal);
  }

  private static HopAiProposalValidation.Result validateAddWorkflowHop(
      WorkflowMeta workflowMeta, HopAiProposal proposal, Set<String> reservedNames) {
    String fromName = proposal.parameter("fromAction");
    String toName = proposal.parameter("toAction");
    if (Utils.isEmpty(fromName) || Utils.isEmpty(toName)) {
      return blocked(proposal, "fromAction and toAction are required");
    }
    if (!actionExists(workflowMeta, fromName, reservedNames)) {
      return blocked(proposal, "From action not found: " + fromName);
    }
    if (!actionExists(workflowMeta, toName, reservedNames)) {
      return blocked(proposal, "To action not found: " + toName);
    }
    ActionMeta from = workflowMeta.findAction(fromName);
    ActionMeta to = workflowMeta.findAction(toName);
    if (fromName.trim().equals(toName.trim())) {
      return blocked(proposal, "Hop cannot connect an action to itself");
    }
    if (from != null && to != null && workflowMeta.findWorkflowHop(from, to) != null) {
      return warning(proposal, "Hop already exists");
    }
    if (!Utils.isEmpty(proposal.parameter("unconditional"))
        && !isYesNo(proposal.parameter("unconditional"))) {
      return blocked(proposal, "unconditional must be Y or N");
    }
    if (!Utils.isEmpty(proposal.parameter("evaluation"))
        && !isYesNo(proposal.parameter("evaluation"))) {
      return blocked(proposal, "evaluation must be Y or N");
    }
    return ok(proposal);
  }

  private static HopAiProposalValidation.Result validateDeleteWorkflowHop(
      WorkflowMeta workflowMeta, HopAiProposal proposal) {
    String fromName = proposal.parameter("fromAction");
    String toName = proposal.parameter("toAction");
    if (Utils.isEmpty(fromName) || Utils.isEmpty(toName)) {
      return blocked(proposal, "fromAction and toAction are required");
    }
    ActionMeta from = workflowMeta.findAction(fromName);
    ActionMeta to = workflowMeta.findAction(toName);
    if (from == null || to == null) {
      return blocked(proposal, "Hop endpoints not found");
    }
    if (workflowMeta.findWorkflowHop(from, to) == null) {
      return blocked(proposal, "Hop not found");
    }
    return ok(proposal);
  }

  private static HopAiProposalValidation.Result validateSetActionLocation(
      WorkflowMeta workflowMeta, HopAiProposal proposal) {
    String actionName = proposal.parameter("actionName");
    if (Utils.isEmpty(actionName)) {
      return blocked(proposal, "actionName is required");
    }
    if (workflowMeta.findAction(actionName) == null) {
      return blocked(proposal, "Action not found: " + actionName);
    }
    if (!HopAiProposalParamSupport.parseLocation(proposal).isValid()) {
      return blocked(proposal, "locationX and locationY must be integers");
    }
    return ok(proposal);
  }

  private static HopAiProposalValidation.Result validateAddWorkflowNote(HopAiProposal proposal) {
    if (Utils.isEmpty(proposal.parameter("text"))) {
      return blocked(proposal, "text is required");
    }
    if (!HopAiProposalParamSupport.parseLocation(proposal).isValid()) {
      return blocked(proposal, "locationX and locationY must be integers");
    }
    return ok(proposal);
  }

  private static HopAiProposalValidation.Result ok(HopAiProposal proposal) {
    return new HopAiProposalValidation.Result(proposal, HopAiProposalValidation.Status.OK, "");
  }

  private static HopAiProposalValidation.Result warning(HopAiProposal proposal, String message) {
    return new HopAiProposalValidation.Result(
        proposal, HopAiProposalValidation.Status.WARNING, message);
  }

  private static HopAiProposalValidation.Result blocked(HopAiProposal proposal, String message) {
    return new HopAiProposalValidation.Result(
        proposal, HopAiProposalValidation.Status.BLOCKED, message);
  }

  private static boolean isYesNo(String value) {
    String normalized = value.trim().toUpperCase();
    return "Y".equals(normalized) || "N".equals(normalized);
  }

  static boolean parseUnconditional(String value) {
    if (Utils.isEmpty(value)) {
      return false;
    }
    return "Y".equalsIgnoreCase(value.trim());
  }

  static boolean parseEvaluation(String value) {
    if (Utils.isEmpty(value)) {
      return true;
    }
    return !"N".equalsIgnoreCase(value.trim());
  }

  private static boolean actionExists(
      WorkflowMeta workflowMeta, String name, Set<String> reservedNames) {
    return workflowMeta.findAction(name) != null
        || (reservedNames != null && reservedNames.contains(name.trim()));
  }
}
