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
package org.apache.hop.datavault.metadata;

import lombok.Getter;
import lombok.Setter;

/**
 * Options controlling {@link DataVaultModel#check} behaviour.
 *
 * <p>Use {@link #forCheckRun()} for GUI/model validation so live source schema lookups reuse JDBC
 * connections and cached field metadata for the duration of the run. Always {@link #close()} (or
 * try-with-resources) those options when the run finishes.
 */
@Getter
@Setter
public class DvModelCheckOptions implements AutoCloseable {

  /**
   * When true, source field types are resolved from the physical source (e.g. live database table)
   * where supported. When false, only stored {@link SourceField} metadata is used.
   */
  private boolean detailedDataTypeChecking = true;

  /**
   * Optional per-run cache for live schema and open JDBC connections. Not a configuration flag —
   * created by {@link #forCheckRun()} / {@link #ensureCache()} and released by {@link #close()}.
   */
  private DvModelCheckCache cache;

  public static DvModelCheckOptions defaults() {
    return new DvModelCheckOptions();
  }

  /**
   * Options for an interactive or full model check: detailed type checking plus a session cache so
   * repeated live lookups do not reconnect for every table.
   */
  public static DvModelCheckOptions forCheckRun() {
    DvModelCheckOptions options = defaults();
    options.cache = new DvModelCheckCache();
    return options;
  }

  public static DvModelCheckOptions fastOnly() {
    DvModelCheckOptions options = new DvModelCheckOptions();
    options.setDetailedDataTypeChecking(false);
    return options;
  }

  /** Returns the existing cache or creates one for this options instance. */
  public DvModelCheckCache ensureCache() {
    if (cache == null) {
      cache = new DvModelCheckCache();
    }
    return cache;
  }

  @Override
  public void close() {
    if (cache != null) {
      cache.close();
      cache = null;
    }
  }
}
