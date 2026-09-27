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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.naming.NamingSchemeKind;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;

/**
 * Shared source cutover calendar. Metadata only: Business Vault Update does not create or load a
 * table. SCD2 tables reference it by name and bind each leg with a source id.
 */
@Getter
@Setter
@NamingSchemeKind(EdwNamingSchemeTypes.BV_SOURCE_CALENDAR)
public class BvSourceCalendar extends BvTableBase {

  private static final Class<?> PKG = BvSourceCalendar.class;

  @HopMetadataProperty(key = "entry", groupKey = "entries")
  private List<BvSourceCalendarEntry> entries = new ArrayList<>();

  public BvSourceCalendar() {
    super(BvTableType.SOURCE_CALENDAR);
  }

  public List<BvSourceCalendarEntry> getEntries() {
    if (entries == null) {
      entries = new ArrayList<>();
    }
    return entries;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      BusinessVaultModel model,
      DataVaultModel dataVaultModel) {
    if (Utils.isEmpty(getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "BvSourceCalendar.CheckResult.MissingName"),
              this));
    }
    BvSourceCalendarSupport.validateEntries(remarks, this, variables);
  }
}
