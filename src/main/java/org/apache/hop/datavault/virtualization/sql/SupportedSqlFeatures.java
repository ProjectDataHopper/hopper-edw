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
package org.apache.hop.datavault.virtualization.sql;

/** Allow-list for free SQL against source models (phase A + B). */
public final class SupportedSqlFeatures {

  private SupportedSqlFeatures() {}

  public static final String SUMMARY =
      "Supported: SELECT columns/*, INNER/LEFT/RIGHT/FULL JOIN, WHERE comparisons "
          + "(AND/OR, IS NULL, IN, BETWEEN), ORDER BY, LIMIT/FETCH, "
          + "GROUP BY with COUNT/SUM/MIN/MAX/AVG, "
          + "simple residual expressions (+ - * / and COALESCE/NVL), "
          + "source tables, Source JSON, and Source Pipeline feeds as tables. "
          + "Full DB pushdown when all tables are DATABASE on one connection "
          + "(then dialect SQL may include CASE and more). "
          + "Not yet: subqueries/CTEs, window functions, residual CASE WHEN, DML.";
}
