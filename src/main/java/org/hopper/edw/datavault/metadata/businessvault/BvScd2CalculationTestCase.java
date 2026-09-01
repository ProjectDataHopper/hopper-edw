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
package org.hopper.edw.datavault.metadata.businessvault;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Column-level SCD2 calculation test stored on the table (no database). */
@Getter
@Setter
@NoArgsConstructor
public class BvScd2CalculationTestCase {

  @HopMetadataProperty private String name;

  @HopMetadataProperty(key = "input", groupKey = "inputs")
  private List<BvScd2NamedValue> inputs = new ArrayList<>();

  @HopMetadataProperty(key = "expected", groupKey = "expected_values")
  private List<BvScd2NamedValue> expected = new ArrayList<>();

  public BvScd2CalculationTestCase(String name) {
    this.name = name;
  }

  public List<BvScd2NamedValue> getInputs() {
    if (inputs == null) {
      inputs = new ArrayList<>();
    }
    return inputs;
  }

  public List<BvScd2NamedValue> getExpected() {
    if (expected == null) {
      expected = new ArrayList<>();
    }
    return expected;
  }
}
