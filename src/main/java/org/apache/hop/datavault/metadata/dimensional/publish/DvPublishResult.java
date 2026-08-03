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
package org.apache.hop.datavault.metadata.dimensional.publish;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;

/** Result of publishing a Data Vault model to a draft dimensional model. */
@Getter
public final class DvPublishResult {

  private final DimensionalModel dimensionalModel;
  private final List<String> warnings;

  public DvPublishResult(DimensionalModel dimensionalModel, List<String> warnings) {
    this.dimensionalModel = dimensionalModel;
    this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
  }

  public List<String> getWarningsOrEmpty() {
    return warnings != null ? warnings : List.of();
  }

  public static DvPublishResult empty() {
    return new DvPublishResult(new DimensionalModel(), new ArrayList<>());
  }
}
