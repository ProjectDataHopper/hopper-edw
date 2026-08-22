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
package org.hopper.edw.datavault.hopgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.hopper.edw.datavault.catalog.RecordSourceIndicatorSupport;
import org.hopper.edw.datavault.metadata.DvIntegrationMode;
import org.hopper.edw.datavault.metadata.DvSourceDeliveryType;
import org.hopper.edw.datavault.metadata.HashAlgorithm;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2BuildMode;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionScdType;
import org.junit.jupiter.api.Test;

class EnumDialogSupportTest {

  @Test
  void lookupTextResolvesBvScd2BuildModeByDescriptionAndCode() {
    assertSame(
        BvScd2BuildMode.INCREMENTAL,
        EnumDialogSupport.lookupText(
            BvScd2BuildMode.INCREMENTAL.getDescription(), BvScd2BuildMode.class, null));
    assertSame(
        BvScd2BuildMode.FULL_REBUILD,
        EnumDialogSupport.lookupText("FULL_REBUILD", BvScd2BuildMode.class, null));
  }

  @Test
  void lookupTextResolvesDescriptionAndLegacyCode() {
    DmDimensionScdType byDescription =
        EnumDialogSupport.lookupText(
            DmDimensionScdType.TYPE2.getDescription(), DmDimensionScdType.class, null);
    assertSame(DmDimensionScdType.TYPE2, byDescription);

    DmDimensionScdType byCode =
        EnumDialogSupport.lookupText("TYPE2", DmDimensionScdType.class, null);
    assertSame(DmDimensionScdType.TYPE2, byCode);
  }

  @Test
  void getCodeMatchesEnumNameForBackwardCompatibility() {
    for (HashAlgorithm algorithm : HashAlgorithm.values()) {
      assertEquals(algorithm.name(), algorithm.getCode());
    }
    for (DvIntegrationMode mode : DvIntegrationMode.values()) {
      assertEquals(mode.name(), mode.getCode());
    }
  }

  @Test
  void deliveryTypeParsesDescriptionOrStoredCode() {
    DvSourceDeliveryType fromDescription =
        RecordSourceIndicatorSupport.parseDeliveryType(
            DvSourceDeliveryType.FULL_SNAPSHOT.getDescription());
    assertSame(DvSourceDeliveryType.FULL_SNAPSHOT, fromDescription);
    assertEquals(
        DvSourceDeliveryType.FULL_SNAPSHOT.getDescription(),
        RecordSourceIndicatorSupport.deliveryTypeLabel(fromDescription));

    DvSourceDeliveryType fromChangesCode =
        RecordSourceIndicatorSupport.parseDeliveryType("CHANGES_ONLY");
    assertSame(DvSourceDeliveryType.CHANGES_ONLY, fromChangesCode);

    // Stored codes must not fall through lookupDescription's CHANGES_ONLY default.
    DvSourceDeliveryType fromFullSnapshotCode =
        RecordSourceIndicatorSupport.parseDeliveryType("FULL_SNAPSHOT");
    assertSame(DvSourceDeliveryType.FULL_SNAPSHOT, fromFullSnapshotCode);
    assertSame(
        DvSourceDeliveryType.FULL_SNAPSHOT,
        RecordSourceIndicatorSupport.parseDeliveryType("full_snapshot"));
  }
}
