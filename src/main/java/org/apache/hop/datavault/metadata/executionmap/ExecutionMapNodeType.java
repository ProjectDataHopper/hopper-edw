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
package org.apache.hop.datavault.metadata.executionmap;

import org.apache.hop.metadata.api.IEnumHasCode;

/** Vertex kind in a Hop execution map graph. */
public enum ExecutionMapNodeType implements IEnumHasCode {
  ROOT_WORKFLOW,
  WORKFLOW,
  ROOT_PIPELINE,
  PIPELINE,
  WORKFLOW_ACTION,
  DV_UPDATE,
  BV_UPDATE,
  DM_UPDATE,
  DM_PUBLISH,
  /** Update resource definition group workflow action. */
  RDG_UPDATE,
  PIPELINE_TRANSFORM,
  PIPELINE_EXECUTOR,
  WORKFLOW_EXECUTOR,
  MAPPING,
  META_INJECT,
  DATA_VAULT_MODEL,
  BUSINESS_VAULT_MODEL,
  DIMENSIONAL_MODEL,
  PIPELINE_FILE,
  GENERATED_PIPELINE,
  ORCHESTRATOR_PIPELINE,
  BULK_MASTER_WORKFLOW,
  SOURCE_DATASET,
  TARGET_DATASET;

  @Override
  public String getCode() {
    return name();
  }

  public static ExecutionMapNodeType lookupCode(String code) {
    return IEnumHasCode.lookupCode(ExecutionMapNodeType.class, code, WORKFLOW_ACTION);
  }
}
