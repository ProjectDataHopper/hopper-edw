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
package org.hopper.edw.datavault.metadata.sourcemodel.profile;

import lombok.Getter;
import lombok.Setter;

/** Options for {@link SourceRelationshipProfiler}. */
@Getter
@Setter
public class SourceRelationshipProfileOptions {

  public static final long DEFAULT_SMALL_MAX_ROWS = 100_000L;
  public static final long DEFAULT_SAMPLE_MAX_ROWS = 5_000_000L;
  public static final long DEFAULT_FULL_OUTER_MAX_ROWS = 10_000L;
  public static final int DEFAULT_SAMPLE_SIZE = 100_000;
  public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 120;

  private long smallMaxRows = DEFAULT_SMALL_MAX_ROWS;
  private long sampleMaxRows = DEFAULT_SAMPLE_MAX_ROWS;
  private long fullOuterMaxRows = DEFAULT_FULL_OUTER_MAX_ROWS;
  private int sampleSize = DEFAULT_SAMPLE_SIZE;
  private int queryTimeoutSeconds = DEFAULT_QUERY_TIMEOUT_SECONDS;
  private SourceRelationshipProfileStrategy strategy = SourceRelationshipProfileStrategy.EXACT_KEY;

  public static SourceRelationshipProfileOptions defaults() {
    return new SourceRelationshipProfileOptions();
  }
}
