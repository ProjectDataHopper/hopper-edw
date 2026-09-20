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
package org.hopper.edw.datavault.presentation.fact;

import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.hopper.core.HDatabaseConnection;

/** Copies a Hop {@link DatabaseMeta} into an isolated Hopper presentation connection. */
public final class FactCrosstabConnectionSupport {

  public static final String CONNECTION_NAME = "edw-target";

  private FactCrosstabConnectionSupport() {}

  public static HDatabaseConnection fromDatabaseMeta(
      DatabaseMeta databaseMeta, IVariables variables) throws HopException {
    if (databaseMeta == null) {
      throw new HopException("Target database connection is required");
    }
    String pluginId = Const.NVL(databaseMeta.getPluginId(), "");
    if (pluginId.isBlank()) {
      throw new HopException(
          "Target database '" + databaseMeta.getName() + "' has no database type");
    }
    return new HDatabaseConnection(
        CONNECTION_NAME,
        pluginId,
        resolve(databaseMeta.getHostname(), variables),
        resolve(databaseMeta.getPort(), variables),
        resolve(databaseMeta.getDatabaseName(), variables),
        resolve(databaseMeta.getUsername(), variables),
        resolvePassword(databaseMeta.getPassword(), variables));
  }

  /**
   * Same order as Hop {@code Database.connect}: variable substitution, then optional Encrypted
   * unwrap. The isolated presentation catalog has no Hop GUI environment, so '${DB_PASSWORD}' must
   * be a concrete password here.
   */
  static String resolvePassword(String password, IVariables variables) {
    String resolved = resolve(password, variables);
    if (resolved.isEmpty()) {
      return "";
    }
    return Encr.decryptPasswordOptionallyEncrypted(resolved);
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return "";
    }
    return variables != null ? variables.resolve(value) : value;
  }
}
