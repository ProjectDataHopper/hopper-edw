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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvLink;

/** Validation rules for Business Vault bridge tables. */
final class BvBridgeValidationSupport {

  private static final Class<?> PKG = BvBridgeValidationSupport.class;

  private BvBridgeValidationSupport() {}

  static void validate(
      List<ICheckResult> remarks,
      BvBridge bridge,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (remarks == null || bridge == null) {
      return;
    }

    List<BvBridgeLayoutSupport.HashKeyColumn> hashKeys =
        BvBridgeLayoutSupport.listHashKeyColumns(bridge, dvModel, variables, metadataProvider);
    if (hashKeys.size() < 2) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvBridgeValidationSupport.CheckResult.NeedTwoHashKeys", bridge.getName()),
              bridge));
    }

    boolean hasSql = !Utils.isEmpty(bridge.getSqlQuery());
    DvLink link = BvBridgeLayoutSupport.resolveFirstLinkDerivative(bridge, dvModel);
    if (!hasSql && link == null) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvBridgeValidationSupport.CheckResult.NeedLinkOrSql", bridge.getName()),
              bridge));
    }

    String weightField = BvBridgeLayoutSupport.resolveWeightField(bridge, variables);
    if (!Utils.isEmpty(weightField)) {
      for (BvBridgeLayoutSupport.HashKeyColumn hashKey : hashKeys) {
        if (weightField.equalsIgnoreCase(hashKey.columnName())) {
          remarks.add(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_ERROR,
                  BaseMessages.getString(
                      PKG,
                      "BvBridgeValidationSupport.CheckResult.WeightCollides",
                      bridge.getName(),
                      weightField),
                  bridge));
          break;
        }
      }
    }

    if (dvModel == null && !hasSql) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvBridgeValidationSupport.CheckResult.MissingDvModel", bridge.getName()),
              bridge));
    }

    if (bvModel != null && Utils.isEmpty(bvModel.getConfigurationOrDefault().getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvBridgeValidationSupport.CheckResult.MissingBvTargetDatabase",
                  bridge.getName()),
              bridge));
    }
  }
}
