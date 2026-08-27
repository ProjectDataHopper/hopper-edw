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
package org.hopper.edw.datavault.metadata;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.i18n.BaseMessages;

/**
 * Whole-model ownership for documenting an already-built raw Data Vault.
 *
 * <p>When {@link DataVaultConfiguration#isReadOnlyExistingVault()} is true, Hop must not generate
 * DDL or load pipelines. Model check keeps referential integrity only so Business Vault and
 * dimensional models can read the documented tables.
 */
public final class DvReadOnlyExistingVaultSupport {

  private static final Class<?> PKG = DvReadOnlyExistingVaultSupport.class;

  private DvReadOnlyExistingVaultSupport() {}

  public static boolean isReadOnly(DataVaultModel model) {
    if (model == null) {
      return false;
    }
    return isReadOnly(model.getConfigurationOrDefault());
  }

  public static boolean isReadOnly(DataVaultConfiguration configuration) {
    return configuration != null && configuration.isReadOnlyExistingVault();
  }

  /**
   * Resource definition group waves skip Data Vault Update for documentation-only models so
   * Business Vault and dimensional jobs can still run.
   */
  public static boolean skipDataVaultUpdateInResourceGroup(
      boolean dataVaultLayer, DataVaultModel model) {
    return dataVaultLayer && isReadOnly(model);
  }

  public static String refuseUpdateMessage() {
    return BaseMessages.getString(PKG, "DvReadOnlyExistingVaultSupport.Error.RefuseUpdate");
  }

  public static void refuseUpdate(DataVaultModel model) throws HopException {
    if (isReadOnly(model)) {
      throw new HopException(refuseUpdateMessage());
    }
  }
}
