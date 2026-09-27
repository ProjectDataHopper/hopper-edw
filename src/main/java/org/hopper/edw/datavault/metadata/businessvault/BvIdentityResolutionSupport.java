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

import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;


/** Lookup and Check model rules for {@link BvIdentityMap} used by an SCD2 table. */
public final class BvIdentityResolutionSupport {

  private BvIdentityResolutionSupport() {}

  public static BvIdentityMap find(
      BusinessVaultModel model, String mapName, IVariables variables) {
    if (model == null || Utils.isEmpty(mapName)) {
      return null;
    }
    String resolved = variables == null ? mapName.trim() : variables.resolve(mapName).trim();
    IBvTable table = model.findTable(resolved);
    if (table instanceof BvIdentityMap identityMap) {
      return identityMap;
    }
    return null;
  }

  public static BvIdentityUnmappedPolicy policyFor(BvScd2Table scd2Table, BvIdentityMap identityMap) {
    if (scd2Table != null && scd2Table.getIdentityUnmappedPolicy() != null) {
      return scd2Table.getIdentityUnmappedPolicy();
    }
    if (identityMap != null) {
      return identityMap.getUnmappedPolicyOrDefault();
    }
    return BvIdentityUnmappedPolicy.SELF;
  }

  public static void validateScd2(
      java.util.List<ICheckResult> remarks,
      BvScd2Table scd2Table,
      BusinessVaultModel model,
      IVariables variables) {
    if (scd2Table == null || Utils.isEmpty(scd2Table.getIdentityMapName())) {
      return;
    }
    String mapName =
        variables == null
            ? scd2Table.getIdentityMapName().trim()
            : variables.resolve(scd2Table.getIdentityMapName()).trim();
    BvIdentityMap identityMap = find(model, mapName, variables);
    if (identityMap == null) {
      IBvTable named = model == null ? null : model.findTable(mapName);
      String key =
          named == null
              ? "BvScd2Table.CheckResult.UnknownIdentityMap"
              : "BvScd2Table.CheckResult.IdentityMapWrongType";
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(BvScd2Table.class, key, scd2Table.getName(), mapName),
              scd2Table));
      return;
    }
    String scd2Hub = scd2Table.getParentHubName();
    String mapHub = identityMap.getParentHubName();
    if (!Utils.isEmpty(scd2Hub) && !Utils.isEmpty(mapHub) && !scd2Hub.equalsIgnoreCase(mapHub)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  BvScd2Table.class,
                  "BvScd2Table.CheckResult.IdentityMapHubMismatch",
                  scd2Table.getName(),
                  scd2Hub,
                  mapHub),
              scd2Table));
    }
    if (scd2Table.isIncrementalBuild()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  BvScd2Table.class,
                  "BvScd2Table.CheckResult.IdentityForcesFullRebuild",
                  scd2Table.getName()),
              scd2Table));
    }
    if (identityMap.isExternal()) {
      return;
    }
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_OK,
            BaseMessages.getString(
                BvScd2Table.class,
                "BvScd2Table.CheckResult.IdentityMap",
                scd2Table.getName(),
                identityMap.getName()),
            scd2Table));
  }
}
