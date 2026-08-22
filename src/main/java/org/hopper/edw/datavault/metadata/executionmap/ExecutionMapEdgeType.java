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
package org.hopper.edw.datavault.metadata.executionmap;

import org.apache.hop.metadata.api.IEnumHasCode;

/** Directed connection between execution map nodes. */
public enum ExecutionMapEdgeType implements IEnumHasCode {
  HOP,
  REFERENCES,
  MODEL_LINK,
  GENERATES,
  EXECUTES,
  CONTAINS,
  PIPELINE_SOURCE,
  READS_FROM,
  WRITES_TO;

  @Override
  public String getCode() {
    return name();
  }

  public static ExecutionMapEdgeType lookupCode(String code) {
    return IEnumHasCode.lookupCode(ExecutionMapEdgeType.class, code, HOP);
  }
}
