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
package org.hopper.edw.datavault.ai.pipeline;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.NotePadMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.ai.HopAiProposal;
import org.hopper.edw.datavault.ai.HopAiProposalParamSupport;
import org.hopper.edw.datavault.ai.HopAiTransformPluginSupport;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.hopgui.HopGui;

/** Previews and applies validated AI proposals to an open pipeline. */
public final class PipelineAiProposalApplier {

  private PipelineAiProposalApplier() {}

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

  public static void apply(PipelineMeta pipelineMeta, List<HopAiProposal> proposals)
      throws HopException {
    apply(pipelineMeta, proposals, null);
  }

  public static void apply(PipelineMeta pipelineMeta, List<HopAiProposal> proposals, HopGui hopGui)
      throws HopException {
    if (pipelineMeta == null) {
      throw new HopException("No pipeline is open");
    }
    if (proposals == null || proposals.isEmpty()) {
      return;
    }
    for (int i = 0; i < proposals.size(); i++) {
      boolean chainUndo = hopGui != null && i < proposals.size() - 1;
      applyOne(pipelineMeta, proposals.get(i), hopGui, chainUndo);
    }
  }

  private static void applyOne(
      PipelineMeta pipelineMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo)
      throws HopException {
    if (proposal == null || proposal.getType() == null) {
      return;
    }
    switch (proposal.getType()) {
      case ADD_TRANSFORM -> addTransform(pipelineMeta, proposal, hopGui, chainUndo);
      case DELETE_TRANSFORM -> deleteTransform(pipelineMeta, proposal, hopGui, chainUndo);
      case RENAME_TRANSFORM -> renameTransform(pipelineMeta, proposal, hopGui, chainUndo);
      case ADD_PIPELINE_HOP -> addPipelineHop(pipelineMeta, proposal, hopGui, chainUndo);
      case DELETE_PIPELINE_HOP -> deletePipelineHop(pipelineMeta, proposal, hopGui, chainUndo);
      case SET_TRANSFORM_LOCATION ->
          setTransformLocation(pipelineMeta, proposal, hopGui, chainUndo);
      case ADD_PIPELINE_NOTE -> addPipelineNote(pipelineMeta, proposal, hopGui, chainUndo);
      default -> throw new HopException("Unsupported proposal type: " + proposal.getType());
    }
  }

  private static void addTransform(
      PipelineMeta pipelineMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo)
      throws HopException {
    String pluginId = proposal.parameter("transformPluginId");
    String name = proposal.parameter("name");
    HopAiProposalParamSupport.Location location = HopAiProposalParamSupport.parseLocation(proposal);
    ITransformMeta meta = HopAiTransformPluginSupport.loadTransformMeta(pluginId);
    TransformMeta transformMeta =
        HopAiTransformPluginSupport.newTransformMeta(pluginId, name, meta);
    transformMeta.setLocation(new Point(location.x(), location.y()));
    pipelineMeta.addTransform(transformMeta);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoNew(
          pipelineMeta,
          new TransformMeta[] {transformMeta},
          new int[] {pipelineMeta.indexOfTransform(transformMeta)},
          chainUndo);
    }
  }

  private static void deleteTransform(
      PipelineMeta pipelineMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo)
      throws HopException {
    String transformName = proposal.parameter("transformName");
    TransformMeta transform = pipelineMeta.findTransform(transformName);
    if (transform == null) {
      throw new HopException("Transform not found: " + transformName);
    }
    List<PipelineHopMeta> hopsToRemove = new ArrayList<>();
    for (PipelineHopMeta hop : pipelineMeta.getHops()) {
      if (hop.getFromTransform().equals(transform) || hop.getToTransform().equals(transform)) {
        hopsToRemove.add(hop);
      }
    }
    for (PipelineHopMeta hop : hopsToRemove) {
      int hopIndex = pipelineMeta.indexOfPipelineHop(hop);
      if (hopGui != null) {
        hopGui.undoDelegate.addUndoDelete(
            pipelineMeta, new PipelineHopMeta[] {hop}, new int[] {hopIndex}, chainUndo);
      }
      pipelineMeta.removePipelineHop(hop);
    }
    int transformIndex = pipelineMeta.indexOfTransform(transform);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoDelete(
          pipelineMeta, new TransformMeta[] {transform}, new int[] {transformIndex}, chainUndo);
    }
    pipelineMeta.removeTransform(transformIndex);
  }

  private static void renameTransform(
      PipelineMeta pipelineMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    TransformMeta transform = pipelineMeta.findTransform(proposal.parameter("transformName"));
    TransformMeta before = (TransformMeta) transform.clone();
    transform.setName(proposal.parameter("newName"));
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoChange(
          pipelineMeta,
          new TransformMeta[] {before},
          new TransformMeta[] {transform},
          new int[] {pipelineMeta.indexOfTransform(transform)},
          chainUndo);
    }
    pipelineMeta.setChanged();
  }

  private static void addPipelineHop(
      PipelineMeta pipelineMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    TransformMeta from = pipelineMeta.findTransform(proposal.parameter("fromTransform"));
    TransformMeta to = pipelineMeta.findTransform(proposal.parameter("toTransform"));
    PipelineHopMeta hop = new PipelineHopMeta(from, to);
    hop.setEnabled(HopAiProposalParamSupport.parseEnabled(proposal.parameter("enabled")));
    pipelineMeta.addPipelineHop(hop);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoNew(
          pipelineMeta,
          new PipelineHopMeta[] {hop},
          new int[] {pipelineMeta.indexOfPipelineHop(hop)},
          chainUndo);
    }
  }

  private static void deletePipelineHop(
      PipelineMeta pipelineMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    TransformMeta from = pipelineMeta.findTransform(proposal.parameter("fromTransform"));
    TransformMeta to = pipelineMeta.findTransform(proposal.parameter("toTransform"));
    PipelineHopMeta hop = pipelineMeta.findPipelineHop(from, to);
    int hopIndex = pipelineMeta.indexOfPipelineHop(hop);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoDelete(
          pipelineMeta, new PipelineHopMeta[] {hop}, new int[] {hopIndex}, chainUndo);
    }
    pipelineMeta.removePipelineHop(hop);
  }

  private static void setTransformLocation(
      PipelineMeta pipelineMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    TransformMeta transform = pipelineMeta.findTransform(proposal.parameter("transformName"));
    HopAiProposalParamSupport.Location location = HopAiProposalParamSupport.parseLocation(proposal);
    Point previous = new Point(transform.getLocation().x, transform.getLocation().y);
    transform.setLocation(new Point(location.x(), location.y()));
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoPosition(
          pipelineMeta,
          new TransformMeta[] {transform},
          new int[] {pipelineMeta.indexOfTransform(transform)},
          new Point[] {previous},
          new Point[] {transform.getLocation()},
          chainUndo);
    }
    pipelineMeta.setChanged();
  }

  private static void addPipelineNote(
      PipelineMeta pipelineMeta, HopAiProposal proposal, HopGui hopGui, boolean chainUndo) {
    HopAiProposalParamSupport.Location location = HopAiProposalParamSupport.parseLocation(proposal);
    NotePadMeta note = new NotePadMeta();
    note.setNote(proposal.parameter("text").trim());
    note.setLocation(new Point(location.x(), location.y()));
    note.width =
        HopAiProposalParamSupport.parseOptionalSize(proposal.parameter("width"), note.width);
    note.height =
        HopAiProposalParamSupport.parseOptionalSize(proposal.parameter("height"), note.height);
    pipelineMeta.addNote(note);
    if (hopGui != null) {
      hopGui.undoDelegate.addUndoNew(
          pipelineMeta,
          new NotePadMeta[] {note},
          new int[] {pipelineMeta.indexOfNote(note)},
          chainUndo);
    }
  }
}
