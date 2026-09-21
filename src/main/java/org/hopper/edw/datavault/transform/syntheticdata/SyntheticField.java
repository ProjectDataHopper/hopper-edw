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
package org.hopper.edw.datavault.transform.syntheticdata;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** One output column of the synthetic data transform. */
@Getter
@Setter
public class SyntheticField {

  @HopMetadataProperty(key = "name")
  private String name = "";

  /** Hop type name: String, Integer, Number, Date, Boolean. */
  @HopMetadataProperty(key = "hopType")
  private String hopType = "String";

  @HopMetadataProperty(key = "generator")
  private String generator = "CONSTANT";

  /** {@code key=value;key=value}. List values use {@code |}. */
  @HopMetadataProperty(key = "arguments")
  private String arguments = "";

  /**
   * When false the value is available to later fields but is not written to the output row. Missing
   * XML loads as true; Hop's metadata default for a boolean is false.
   */
  @HopMetadataProperty(key = "publish", defaultBoolean = true)
  private boolean publish = true;

  public SyntheticField() {}

  public SyntheticField(SyntheticField other) {
    this.name = other.name;
    this.hopType = other.hopType;
    this.generator = other.generator;
    this.arguments = other.arguments;
    this.publish = other.publish;
  }
}
