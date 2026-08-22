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
package org.hopper.edw.datavault.lineageview.backend;

import lombok.Builder;
import lombok.Value;

/** Non-fatal graph note. Never fails a fetch. */
@Value
@Builder
public class LineageWarning {
  public static final String SEED_ISOLATED = "SEED_ISOLATED";
  public static final String DEPTH_CLIPPED = "DEPTH_CLIPPED";
  public static final String EVENT_CAP = "EVENT_CAP";
  public static final String MISSING_FACET = "MISSING_FACET";
  public static final String LAYER_DROPPED = "LAYER_DROPPED";
  public static final String LAYER_INFERRED = "LAYER_INFERRED";

  String code;
  String message;
  String nodeId;
}
