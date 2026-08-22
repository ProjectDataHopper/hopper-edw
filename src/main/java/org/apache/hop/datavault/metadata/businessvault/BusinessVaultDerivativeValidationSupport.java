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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.i18n.BaseMessages;

final class BusinessVaultDerivativeValidationSupport {

  private static final Class<?> PKG = BusinessVaultDerivativeValidationSupport.class;

  private BusinessVaultDerivativeValidationSupport() {}

  static void validateDerivatives(
      List<ICheckResult> remarks, IBvTable bvTable, DataVaultModel dataVaultModel) {
    if (bvTable == null || dataVaultModel == null) {
      return;
    }
    for (BvDerivativeRef derivative : bvTable.getDerivatives()) {
      if (derivative == null || Utils.isEmpty(derivative.getDvTableName())) {
        continue;
      }
      IDvTable dvTable = dataVaultModel.findTable(derivative.getDvTableName());
      if (dvTable == null) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BusinessVaultDerivativeValidationSupport.Error.MissingDvTable",
                    bvTable.getName(),
                    derivative.getDvTableName()),
                bvTable));
        continue;
      }
      if (derivative.getDvTableType() != null
          && dvTable.getTableType() != derivative.getDvTableType()) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BusinessVaultDerivativeValidationSupport.Error.DvTableTypeMismatch",
                    bvTable.getName(),
                    derivative.getDvTableName(),
                    derivative.getDvTableType(),
                    dvTable.getTableType()),
                bvTable));
      }
    }
  }
}
