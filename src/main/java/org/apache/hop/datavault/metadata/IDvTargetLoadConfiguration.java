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
package org.apache.hop.datavault.metadata;

import org.apache.hop.core.variables.IVariables;

/**
 * Target load and generated-artifact settings shared by DV, Business Vault, and dimensional models.
 */
public interface IDvTargetLoadConfiguration {

  String getTargetDatabase();

  DvTargetLoadMode resolveTargetLoadMode();

  String resolveTargetTableCommitSize(IVariables variables);

  String resolveTargetTableParallelCopies(IVariables variables);

  String resolveBulkLoadStagingFolder(IVariables variables, String modelName);

  String resolveBulkLoadDelimiter(IVariables variables);

  String resolveBulkLoadEnclosure(IVariables variables);

  String resolveBulkLoadEncoding(IVariables variables);

  boolean isBulkLoadLocalFileRequired();

  String getGeneratedPipelineFolder();

  String resolveGeneratedWorkflowName(IVariables variables, String modelName);
}
