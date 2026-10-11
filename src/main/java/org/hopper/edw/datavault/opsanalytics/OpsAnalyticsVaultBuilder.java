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
package org.hopper.edw.datavault.opsanalytics;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultApplySupport;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassification;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultClassifier;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitOptions;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitResult;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitSupport;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitWriteResult;

/** Classifies and writes the operational analytics raw vault from a generated source model. */
public final class OpsAnalyticsVaultBuilder {

  /** File prefix. Split support stores this as {@code ops_}. */
  public static final String FILE_PREFIX = "ops";

  private OpsAnalyticsVaultBuilder() {}

  /** Classifier options for this profile. Default Generate Data Vault leaves the flag off. */
  public static SourceToVaultOptions options() {
    SourceToVaultOptions options = SourceToVaultOptions.defaults();
    options.setSingleParentCompositeKeyAsLink(true);
    return options;
  }

  public static DataVaultModel apply(
      SourceModel sourceModel, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    DataVaultModel vault = new DataVaultModel();
    vault.setName(OpsAnalyticsSourceModelBuilder.MODEL_NAME);
    SourceToVaultOptions options = options();
    SourceToVaultClassification classification =
        SourceToVaultClassifier.classify(sourceModel, null, null, options);
    SourceToVaultApplySupport.apply(
        sourceModel, vault, classification, false, variables, metadataProvider, options);
    return vault;
  }

  public static SourceToVaultSplitWriteResult writeSplit(
      DataVaultModel vault,
      String outputFolder,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    SourceToVaultSplitOptions splitOptions = new SourceToVaultSplitOptions();
    splitOptions.setOutputFolder(outputFolder);
    splitOptions.setNamePrefix(FILE_PREFIX);
    splitOptions.setExistingFilePolicy(SourceToVaultSplitOptions.ExistingFilePolicy.REPLACE);
    SourceToVaultSplitResult partitioned =
        SourceToVaultSplitSupport.partition(vault, splitOptions, variables, metadataProvider);
    return SourceToVaultSplitSupport.write(
        partitioned, SourceToVaultSplitOptions.ExistingFilePolicy.REPLACE, variables);
  }
}
