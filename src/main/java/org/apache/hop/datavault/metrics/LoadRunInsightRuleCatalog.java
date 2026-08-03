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
package org.apache.hop.datavault.metrics;

/** Compact catalog of load-run insight codes for AI advisory context. */
public final class LoadRunInsightRuleCatalog {

  private LoadRunInsightRuleCatalog() {}

  public static String toJson() {
    return """
        {"rules":[\
        {"code":"DIM_LOOKUP_PRELOAD_CANDIDATE","tuning":["preloadLookupCache on fact dimension role"]},\
        {"code":"HIGH_TARGET_READ_RATIO","tuning":["CDC/filter path","target read scope"]},\
        {"code":"SORT_MEMORY_RISK","tuning":["sortRowsSize","JVM heap","parallelPipelineCopies"]},\
        {"code":"BULK_LOAD_USED","tuning":["targetLoadMode","bulkLoadStagingFolder","capacity planning"]},\
        {"code":"HIGH_TRANSFORM_DURATION","tuning":["targetTableParallelCopies","sortRowsSize","pipeline parallelism"]}\
        ]}""";
  }
}
