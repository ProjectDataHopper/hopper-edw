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
package org.apache.hop.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;

/** Design-time checks for a {@link SourcePipeline}. */
public final class SourcePipelineValidationSupport {

  private static final Class<?> PKG = SourceModel.class;

  private SourcePipelineValidationSupport() {}

  public static List<ICheckResult> check(
      SourcePipeline pipeline, SourceModel model, Set<String> knownPipelineNames) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (pipeline == null) {
      return remarks;
    }

    String name = pipeline.getName();
    if (Utils.isEmpty(name)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "SourceModel.CheckResult.PipelineMissingName"),
              null));
      name = "?";
    } else if (knownPipelineNames != null && !knownPipelineNames.add(name)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.DuplicatePipelineName", name),
              null));
    }

    if (Utils.isEmpty(pipeline.getPipelineFilename())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.PipelineMissingFilename", nvl(name)),
              null));
    }
    if (Utils.isEmpty(pipeline.getOutputTransformName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.PipelineMissingTransform", nvl(name)),
              null));
    }
    if (pipeline.getFields().isEmpty()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "SourceModel.CheckResult.PipelineMissingFields", nvl(name)),
              null));
    } else {
      Set<String> fieldNames = new HashSet<>();
      for (SourceColumn field : pipeline.getFields()) {
        if (field == null || Utils.isEmpty(field.getName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG, "SourceModel.CheckResult.PipelineFieldMissingName", nvl(name)),
                  null));
          continue;
        }
        if (!fieldNames.add(field.getName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "SourceModel.CheckResult.PipelineDuplicateField",
                      nvl(name),
                      field.getName()),
                  null));
        }
      }
    }

    return remarks;
  }

  private static String nvl(String value) {
    return Utils.isEmpty(value) ? "?" : value;
  }
}
