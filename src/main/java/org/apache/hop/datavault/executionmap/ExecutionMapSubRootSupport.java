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
package org.apache.hop.datavault.executionmap;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.file.IHasFilename;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionMeta;
import org.apache.hop.workflow.action.IAction;

/** Resolves crawl root artifacts from a selected workflow action or pipeline transform. */
public final class ExecutionMapSubRootSupport {

  private ExecutionMapSubRootSupport() {}

  public static String resolveFromWorkflowAction(
      ActionMeta actionMeta,
      WorkflowMeta workflowMeta,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (actionMeta == null || actionMeta.getAction() == null) {
      throw new HopException("No workflow action selected");
    }
    IAction action = actionMeta.getAction();
    return resolveReferencedArtifactPath(action, workflowMeta, variables, metadataProvider);
  }

  public static String resolveFromPipelineTransform(
      TransformMeta transformMeta,
      PipelineMeta pipelineMeta,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (transformMeta == null || transformMeta.getTransform() == null) {
      throw new HopException("No pipeline transform selected");
    }
    ITransformMeta transform = transformMeta.getTransform();
    return resolveReferencedArtifactPath(transform, pipelineMeta, variables, metadataProvider);
  }

  private static String resolveReferencedArtifactPath(
      Object referenceSource,
      Object parentMeta,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    String[] descriptions;
    boolean[] enabled;
    if (referenceSource instanceof IAction action) {
      descriptions = action.getReferencedObjectDescriptions();
      enabled = action.isReferencedObjectEnabled();
    } else if (referenceSource instanceof ITransformMeta transformMeta) {
      descriptions = transformMeta.getReferencedObjectDescriptions();
      enabled = transformMeta.isReferencedObjectEnabled();
    } else {
      throw new HopException("Selected item does not expose referenced artifacts");
    }
    if (descriptions == null || enabled == null) {
      throw new HopException("Selected item has no referenced artifacts to crawl");
    }
    for (int i = 0; i < descriptions.length; i++) {
      if (!enabled[i]) {
        continue;
      }
      Object loaded;
      try {
        if (referenceSource instanceof IAction action) {
          loaded = action.loadReferencedObject(i, metadataProvider, variables);
        } else {
          loaded =
              ((ITransformMeta) referenceSource)
                  .loadReferencedObject(i, metadataProvider, variables);
        }
      } catch (HopException e) {
        continue;
      }
      String path = artifactPath(loaded, variables);
      if (!Utils.isEmpty(path) && isCrawlableArtifact(path)) {
        return path;
      }
    }
    throw new HopException(
        "Selected item does not reference a crawlable workflow or pipeline artifact");
  }

  private static boolean isCrawlableArtifact(String path) {
    if (Utils.isEmpty(path)) {
      return false;
    }
    String lower = path.toLowerCase();
    return lower.endsWith(".hwf") || lower.endsWith(".hpl");
  }

  private static String artifactPath(Object loaded, IVariables variables) {
    if (loaded == null) {
      return null;
    }
    if (loaded instanceof IHasFilename hasFilename && !Utils.isEmpty(hasFilename.getFilename())) {
      return variables != null
          ? variables.resolve(hasFilename.getFilename())
          : hasFilename.getFilename();
    }
    if (loaded instanceof WorkflowMeta workflowMeta && !Utils.isEmpty(workflowMeta.getFilename())) {
      return variables != null
          ? variables.resolve(workflowMeta.getFilename())
          : workflowMeta.getFilename();
    }
    if (loaded instanceof PipelineMeta pipelineMeta && !Utils.isEmpty(pipelineMeta.getFilename())) {
      return variables != null
          ? variables.resolve(pipelineMeta.getFilename())
          : pipelineMeta.getFilename();
    }
    return null;
  }
}
