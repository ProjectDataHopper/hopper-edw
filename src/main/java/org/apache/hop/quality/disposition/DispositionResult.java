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
package org.apache.hop.quality.disposition;

import lombok.Getter;

/** Outcome of applying a disposition policy to a quality report. */
@Getter
public class DispositionResult {

  private final boolean failed;
  private final int nrErrors;
  private final String summary;

  public DispositionResult(boolean failed, int nrErrors, String summary) {
    this.failed = failed;
    this.nrErrors = nrErrors;
    this.summary = summary != null ? summary : "";
  }

  public static DispositionResult pass(String summary) {
    return new DispositionResult(false, 0, summary);
  }

  public static DispositionResult fail(int nrErrors, String summary) {
    return new DispositionResult(true, Math.max(1, nrErrors), summary);
  }
}
