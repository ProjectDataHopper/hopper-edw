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
package org.apache.hop.datavault.resourcedefinition;

/**
 * One physical/model column that should be widened to a catalog field length during schema
 * remediation. The catalog is never rewritten; this describes downstream model/DDL targets only.
 */
public record RemediationTargetColumn(
    String layer,
    String modelName,
    String modelFilename,
    String tableElementName,
    String physicalTableName,
    String targetFieldName,
    String sourceFieldName,
    String catalogLength,
    String confidence,
    String connectionName) {

  public static final String LAYER_DV = "DV";
  public static final String LAYER_BV = "BV";
  public static final String LAYER_DM = "DM";

  public static final String CONFIDENCE_EXPLICIT_MAP = "EXPLICIT_MAP";
  public static final String CONFIDENCE_DERIVED_VIA_BV = "DERIVED_VIA_BV";
  public static final String CONFIDENCE_SQL_TABLE = "SQL_TABLE";
}
