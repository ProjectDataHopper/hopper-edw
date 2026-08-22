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
import org.apache.hop.core.variables.IVariables;

/** Minimal DatabaseMeta stub for pipeline generation unit tests. */
final class TestDatabaseMeta extends DatabaseMeta {

  private final String pluginId;

  TestDatabaseMeta(String name) {
    this(name, null);
  }

  TestDatabaseMeta(String name, String pluginId) {
    super();
    setName(name);
    this.pluginId = pluginId;
  }

  @Override
  public String getPluginId() {
    return pluginId != null ? pluginId : super.getPluginId();
  }

  @Override
  public String quoteField(String field) {
    return field;
  }

  @Override
  public String getQuotedSchemaTableCombination(IVariables variables, String schema, String table) {
    if (schema == null || schema.isEmpty()) {
      return table;
    }
    return schema + "." + table;
  }
}
