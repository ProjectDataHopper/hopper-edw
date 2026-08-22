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

import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves Business Vault target database connections from model configuration. */
public final class BvTargetDatabaseSupport {

  private BvTargetDatabaseSupport() {}

  public static DatabaseMeta loadTargetDatabase(
      IHopMetadataProvider metadataProvider, BusinessVaultConfiguration config)
      throws HopException {
    String targetDbName = config != null ? config.getTargetDatabase() : null;
    if (Utils.isEmpty(targetDbName) || metadataProvider == null) {
      return null;
    }
    DatabaseMeta targetDatabaseMeta =
        metadataProvider.getSerializer(DatabaseMeta.class).load(targetDbName);
    if (targetDatabaseMeta == null) {
      throw new HopException("Target database connection not found in metadata: " + targetDbName);
    }
    return targetDatabaseMeta;
  }
}
