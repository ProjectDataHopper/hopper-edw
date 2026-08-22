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
package org.hopper.edw.datavault.dbt;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;

@Getter
@Setter
public class DbtImportOptions {

  public static final int DEFAULT_SPLIT_THRESHOLD = 80;

  private String projectRoot;
  private DbtProjectScan scan;
  private final List<DbtModelDraft> selectedModels = new ArrayList<>();
  private DbtImportDestination destination = DbtImportDestination.CURRENT_MODEL;
  private DbtImportConflictPolicy conflictPolicy = DbtImportConflictPolicy.SKIP;
  private boolean importMacros = true;
  private String libraryName;
  private String outputFolder;
  private String newModelFilename;
  private BusinessVaultModel currentModel;
  private IHopMetadataProvider metadataProvider;
  private IVariables variables;
}
