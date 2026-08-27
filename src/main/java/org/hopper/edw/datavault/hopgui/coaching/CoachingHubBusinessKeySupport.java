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
package org.hopper.edw.datavault.hopgui.coaching;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.BusinessKey;
import org.hopper.edw.datavault.metadata.DataVaultSource;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.SourceFieldPrimaryKeySupport;

/** Seeds hub business keys from catalog source primary-key metadata. */
public final class CoachingHubBusinessKeySupport {

  private CoachingHubBusinessKeySupport() {}

  public static void populateBusinessKeysFromSource(
      DvHub hub, DataVaultSource source, String sourceName, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (hub == null || source == null || Utils.isEmpty(sourceName)) {
      return;
    }
    if (!Utils.isEmpty(hub.getBusinessKeys())) {
      return;
    }

    List<SourceField> sourceFields = source.getFields(metadataProvider);
    if (!SourceFieldPrimaryKeySupport.hasPrimaryKeyMetadata(sourceFields)) {
      return;
    }

    List<BusinessKey> businessKeys =
        SourceFieldPrimaryKeySupport.businessKeysFromPrimaryKeyFields(sourceFields, sourceName);
    if (!businessKeys.isEmpty()) {
      hub.setBusinessKeys(businessKeys);
    }
  }
}
