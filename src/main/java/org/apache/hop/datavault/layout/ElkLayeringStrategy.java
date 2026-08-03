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
package org.apache.hop.datavault.layout;

import lombok.Getter;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IEnumHasCode;
import org.apache.hop.metadata.api.IEnumHasCodeAndDescription;
import org.eclipse.elk.alg.layered.options.LayeringStrategy;

/** Layer assignment strategy for ELK layered layout. */
@Getter
public enum ElkLayeringStrategy implements IEnumHasCodeAndDescription {
  NETWORK_SIMPLEX(
      "NETWORK_SIMPLEX",
      BaseMessages.getString(ElkLayeringStrategy.class, "ElkLayeringStrategy.NetworkSimplex"),
      LayeringStrategy.NETWORK_SIMPLEX),
  LONGEST_PATH(
      "LONGEST_PATH",
      BaseMessages.getString(ElkLayeringStrategy.class, "ElkLayeringStrategy.LongestPath"),
      LayeringStrategy.LONGEST_PATH),
  LONGEST_PATH_SOURCE(
      "LONGEST_PATH_SOURCE",
      BaseMessages.getString(ElkLayeringStrategy.class, "ElkLayeringStrategy.LongestPathSource"),
      LayeringStrategy.LONGEST_PATH_SOURCE),
  COFFMAN_GRAHAM(
      "COFFMAN_GRAHAM",
      BaseMessages.getString(ElkLayeringStrategy.class, "ElkLayeringStrategy.CoffmanGraham"),
      LayeringStrategy.COFFMAN_GRAHAM),
  INTERACTIVE(
      "INTERACTIVE",
      BaseMessages.getString(ElkLayeringStrategy.class, "ElkLayeringStrategy.Interactive"),
      LayeringStrategy.INTERACTIVE),
  STRETCH_WIDTH(
      "STRETCH_WIDTH",
      BaseMessages.getString(ElkLayeringStrategy.class, "ElkLayeringStrategy.StretchWidth"),
      LayeringStrategy.STRETCH_WIDTH),
  MIN_WIDTH(
      "MIN_WIDTH",
      BaseMessages.getString(ElkLayeringStrategy.class, "ElkLayeringStrategy.MinWidth"),
      LayeringStrategy.MIN_WIDTH),
  BF_MODEL_ORDER(
      "BF_MODEL_ORDER",
      BaseMessages.getString(ElkLayeringStrategy.class, "ElkLayeringStrategy.BfModelOrder"),
      LayeringStrategy.BF_MODEL_ORDER),
  DF_MODEL_ORDER(
      "DF_MODEL_ORDER",
      BaseMessages.getString(ElkLayeringStrategy.class, "ElkLayeringStrategy.DfModelOrder"),
      LayeringStrategy.DF_MODEL_ORDER);

  private final String code;
  private final String description;
  private final LayeringStrategy elkStrategy;

  ElkLayeringStrategy(String code, String description, LayeringStrategy elkStrategy) {
    this.code = code;
    this.description = description;
    this.elkStrategy = elkStrategy;
  }

  public LayeringStrategy toElkStrategy() {
    return elkStrategy;
  }

  public static String[] getDescriptions() {
    return IEnumHasCodeAndDescription.getDescriptions(ElkLayeringStrategy.class);
  }

  public static ElkLayeringStrategy lookupDescription(String description) {
    return IEnumHasCodeAndDescription.lookupDescription(
        ElkLayeringStrategy.class, description, NETWORK_SIMPLEX);
  }

  public static ElkLayeringStrategy lookupCode(String code) {
    return IEnumHasCode.lookupCode(ElkLayeringStrategy.class, code, NETWORK_SIMPLEX);
  }
}
