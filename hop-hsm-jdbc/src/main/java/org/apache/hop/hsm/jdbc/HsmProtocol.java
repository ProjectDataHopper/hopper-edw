/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.hsm.jdbc;

/** Wire protocol constants (must match hop-datavault SourceModelDataProtocol). */
public final class HsmProtocol {

  public static final String JDBC_PREFIX = "jdbc:hop-hsm:";
  public static final String DEFAULT_PATH = "/hop/sourceModelData";

  /** JDBC schema = Source model service name. Preferred over legacy modelName. */
  public static final String PARAM_SCHEMA = "schema";

  /** @deprecated use {@link #PARAM_SCHEMA} */
  public static final String PARAM_MODEL_NAME = "modelName";

  public static final String PARAM_SQL = "sql";
  public static final String PARAM_ROW_LIMIT = "rowLimit";
  public static final String PARAM_ACTION = "action";
  public static final String PARAM_TABLE = "table";

  public static final String ACTION_QUERY = "query";
  public static final String ACTION_TABLES = "tables";
  public static final String ACTION_COLUMNS = "columns";
  public static final String ACTION_SCHEMAS = "schemas";
  public static final String ACTION_PING = "ping";

  private HsmProtocol() {}
}
