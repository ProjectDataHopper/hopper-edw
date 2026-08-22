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

import lombok.Getter;
import lombok.Setter;

/**
 * Options controlling {@link DataVaultModel#check} behaviour.
 *
 * <p>Use {@link #forCheckRun()} for a single model/GUI check (session cache is closed when the
 * check finishes). Use {@link #forSharedCheckSession()} when validating many models in one batch so
 * JDBC connections, live schemas, and Unicode probes are reused — then {@link #close()} once.
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

  /**
   * When true, {@link DataVaultModel#check} must not close the cache (caller owns a multi-model
   * session). Default false for single-model GUI checks.
   */
  private boolean sharedSession;

  /**
   * When true with detailed type checking, prefer DISCOVERED layouts from a schema harvest (cache
   * warm) over live JDBC for database sources. Falls back to live discovery when no harvest is
   * available.
   */
  private boolean preferHarvestForLiveFields;

  /** Optional harvest run id (empty → {@code DV_SCHEMA_HARVEST_RUN_ID} or latest for group). */
  private String harvestRunId;

  private String harvestHistoryDatabase;
  private String harvestHistorySchema;
  private String harvestCatalogConnection;

  /** Resource definition group name used when resolving the latest harvest run. */
  private String harvestResourceGroup;

  public static DvModelCheckOptions defaults() {
    return new DvModelCheckOptions();
  }

  /**
   * Options for a single interactive or full model check: detailed type checking plus a session
   * cache closed when the check finishes.
   */
  public static DvModelCheckOptions forCheckRun() {
    DvModelCheckOptions options = defaults();
    options.cache = new DvModelCheckCache();
    options.sharedSession = false;
    return options;
  }

  /**
   * Options for validating many models in one batch. Reuses JDBC connections, live table metadata,
   * catalog source-name lists, and target Unicode assessments across models. Caller must {@link
   * #close()} when the batch ends.
   */
  public static DvModelCheckOptions forSharedCheckSession() {
    DvModelCheckOptions options = forCheckRun();
    options.sharedSession = true;
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
