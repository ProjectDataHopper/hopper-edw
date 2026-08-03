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
package org.apache.hop.datavault.metadata.businessvault;

import java.util.List;
import org.apache.hop.datavault.metadata.DataVaultModel;

/** Execution ordering helpers for Business Vault Update workflows. */
public final class BusinessVaultUpdateExecutionSupport {

  private BusinessVaultUpdateExecutionSupport() {}

  public static boolean isPipelineExecutableTableType(BvTableType tableType) {
    return tableType == BvTableType.SCD2
        || tableType == BvTableType.PIT
        || tableType == BvTableType.BUSINESS_TABLE;
  }

  /**
   * Orders pipeline-executable tables for update: SCD2, PIT, and SQL business tables, honouring
   * {@code ref()} dependencies among business tables.
   */
  public static List<IBvTable> orderTablesForPipelineExecution(List<IBvTable> tables) {
    return orderTablesForPipelineExecution(tables, null, null);
  }

  public static List<IBvTable> orderTablesForPipelineExecution(
      List<IBvTable> tables, BusinessVaultModel bvModel, DataVaultModel dvModel) {
    return BvSqlDependencySupport.orderTablesForPipelineExecution(tables, bvModel, dvModel);
  }
}
