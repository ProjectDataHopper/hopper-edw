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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvSatellite;
import org.apache.hop.datavault.metadata.DvSpecialRecordSupport;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Validation rules for Business Vault PIT tables. */
final class BvPitValidationSupport {

  private static final Class<?> PKG = BvPitValidationSupport.class;

  private BvPitValidationSupport() {}

  static void validate(
      List<ICheckResult> remarks,
      BvPitTable pitTable,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      IHopMetadataProvider metadataProvider,
      IVariables variables) {
    if (pitTable == null) {
      return;
    }

    validateSnapshotDateField(remarks, pitTable, variables);
    validateDerivatives(remarks, pitTable, dvModel);
    validateSchedule(remarks, pitTable, variables);

    if (dvModel != null) {
      validateTargetDatabases(remarks, metadataProvider, bvModel, dvModel, pitTable);
      validateUnlistedHubSatellites(remarks, pitTable, dvModel);
    }
  }

  static void validateTargetDatabases(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvPitTable pitTable) {
    if (remarks == null || pitTable == null) {
      return;
    }
    BusinessVaultConfiguration bvConfig =
        bvModel != null ? bvModel.getConfigurationOrDefault() : new BusinessVaultConfiguration();
    DataVaultConfiguration dvConfig =
        dvModel != null ? dvModel.getConfigurationOrDefault() : new DataVaultConfiguration();

    if (Utils.isEmpty(dvConfig.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvPitValidationSupport.CheckResult.MissingDvTargetDatabase",
                  pitTable.getName()),
              pitTable));
    } else if (metadataProvider != null) {
      try {
        DvSpecialRecordSupport.loadTargetDatabase(metadataProvider, dvConfig);
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), pitTable));
      }
    }

    if (Utils.isEmpty(bvConfig.getTargetDatabase())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG,
                  "BvPitValidationSupport.CheckResult.MissingBvTargetDatabase",
                  pitTable.getName()),
              pitTable));
    } else if (metadataProvider != null) {
      try {
        BvTargetDatabaseSupport.loadTargetDatabase(metadataProvider, bvConfig);
      } catch (HopException e) {
        remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), pitTable));
      }
    }
  }

  private static void validateSnapshotDateField(
      List<ICheckResult> remarks, BvPitTable pitTable, IVariables variables) {
    String snapshotField = BvPitLayoutSupport.resolveSnapshotDateField(pitTable, variables);
    if (Utils.isEmpty(snapshotField)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvPitValidationSupport.Error.MissingSnapshotDateField", pitTable.getName()),
              pitTable));
    }
  }

  private static void validateDerivatives(
      List<ICheckResult> remarks, BvPitTable pitTable, DataVaultModel dvModel) {
    int hubCount = 0;
    String hubDerivativeName = null;
    int satelliteCount = 0;

    for (BvDerivativeRef derivative : pitTable.getDerivatives()) {
      if (derivative == null || derivative.getDvTableType() == null) {
        continue;
      }
      if (derivative.getDvTableType() == DvTableType.HUB) {
        hubCount++;
        hubDerivativeName = derivative.getDvTableName();
      } else if (derivative.getDvTableType() == DvTableType.SATELLITE) {
        satelliteCount++;
      }
    }

    if (hubCount == 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvPitTable.CheckResult.MissingHubDerivative", pitTable.getName()),
              pitTable));
    } else if (hubCount > 1) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvPitValidationSupport.Error.MultipleHubDerivatives", pitTable.getName()),
              pitTable));
    }

    if (satelliteCount == 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvPitTable.CheckResult.MissingSatelliteDerivative", pitTable.getName()),
              pitTable));
      return;
    }

    if (hubCount != 1 || dvModel == null || Utils.isEmpty(hubDerivativeName)) {
      return;
    }

    DvHub hub = BvPitLayoutSupport.resolveHubDerivative(pitTable, dvModel);
    if (hub == null) {
      return;
    }

    List<DvSatellite> satellites =
        BvPitLayoutSupport.resolveSatelliteDerivatives(pitTable, dvModel);
    BvPitSnapshotSchedule schedule = pitTable.getSnapshotScheduleOrDefault();
    Set<String> pointerNames = new HashSet<>();

    for (DvSatellite satellite : satellites) {
      if (!Utils.isEmpty(satellite.getLinkName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvPitValidationSupport.Error.LinkSatelliteNotSupported",
                    pitTable.getName(),
                    satellite.getName()),
                pitTable));
        continue;
      }
      if (Utils.isEmpty(satellite.getHubName())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvPitValidationSupport.Error.UnparentedSatellite",
                    pitTable.getName(),
                    satellite.getName()),
                pitTable));
        continue;
      }
      boolean matchesHub =
          hubDerivativeName.equalsIgnoreCase(satellite.getHubName())
              || hub.getName().equalsIgnoreCase(satellite.getHubName());
      if (!matchesHub) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvPitValidationSupport.Error.SatelliteHubMismatch",
                    pitTable.getName(),
                    satellite.getName(),
                    hubDerivativeName),
                pitTable));
      }

      String pointerName =
          BvPitLayoutSupport.resolveSatellitePointerColumnName(satellite, schedule, null);
      if (!pointerNames.add(pointerName)) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_ERROR,
                BaseMessages.getString(
                    PKG,
                    "BvPitValidationSupport.Error.DuplicatePointerColumn",
                    pitTable.getName(),
                    pointerName),
                pitTable));
      }
    }
  }

  private static void validateSchedule(
      List<ICheckResult> remarks, BvPitTable pitTable, IVariables variables) {
    BvPitSnapshotSchedule schedule = pitTable.getSnapshotScheduleOrDefault();

    if (schedule.getCadence() != null && schedule.getCadence() != BvPitCadence.DAILY) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "BvPitValidationSupport.Warning.NonDailyCadenceNotImplemented",
                  pitTable.getName(),
                  schedule.getCadence().name()),
              pitTable));
    }

    if (schedule.getHorizonDays() < 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvPitValidationSupport.Error.NegativeHorizonDays", pitTable.getName()),
              pitTable));
    }

    if (schedule.getRangeStart() == BvPitRangeStart.FIXED_DATE
        && Utils.isEmpty(resolve(variables, schedule.getRangeStartFixed()))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvPitValidationSupport.Error.MissingRangeStartFixed", pitTable.getName()),
              pitTable));
    }

    if (schedule.getRangeEnd() == BvPitRangeEnd.FIXED_DATE
        && Utils.isEmpty(resolve(variables, schedule.getRangeEndFixed()))) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvPitValidationSupport.Error.MissingRangeEndFixed", pitTable.getName()),
              pitTable));
    }

    String suffix = resolve(variables, schedule.getSatellitePointerSuffix());
    if (Utils.isEmpty(suffix)) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "BvPitValidationSupport.Error.MissingPointerSuffix", pitTable.getName()),
              pitTable));
    }
  }

  private static void validateUnlistedHubSatellites(
      List<ICheckResult> remarks, BvPitTable pitTable, DataVaultModel dvModel) {
    DvHub hub = BvPitLayoutSupport.resolveHubDerivative(pitTable, dvModel);
    if (hub == null) {
      return;
    }
    String hubName = hub.getName();
    Set<String> listedSatellites = new HashSet<>();
    for (BvDerivativeRef derivative : pitTable.getDerivatives()) {
      if (derivative != null
          && derivative.getDvTableType() == DvTableType.SATELLITE
          && !Utils.isEmpty(derivative.getDvTableName())) {
        listedSatellites.add(derivative.getDvTableName().toLowerCase());
      }
    }

    for (var table : dvModel.getTables()) {
      if (!(table instanceof DvSatellite satellite)
          || Utils.isEmpty(satellite.getName())
          || !hubName.equalsIgnoreCase(satellite.getHubName())) {
        continue;
      }
      if (!listedSatellites.contains(satellite.getName().toLowerCase())) {
        remarks.add(
            new CheckResult(
                ICheckResult.TYPE_RESULT_OK,
                BaseMessages.getString(
                    PKG,
                    "BvPitValidationSupport.Info.UnlistedHubSatellite",
                    pitTable.getName(),
                    satellite.getName(),
                    hubName),
                pitTable));
      }
    }
  }

  private static String resolve(IVariables variables, String value) {
    if (variables != null) {
      return variables.resolve(value);
    }
    return value;
  }
}
