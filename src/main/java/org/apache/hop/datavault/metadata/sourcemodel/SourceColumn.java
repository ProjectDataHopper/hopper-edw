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

package org.apache.hop.datavault.metadata.sourcemodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Column definition for a {@link SourceTable} on a source model canvas. */
@Getter
@Setter
@NoArgsConstructor
public class SourceColumn {

  @HopMetadataProperty private String name;
  @HopMetadataProperty private String description;
  @HopMetadataProperty private String sourceDataType;
  @HopMetadataProperty private String length;
  @HopMetadataProperty private String precision;
  /** Hop {@code ValueMetaInterface} type code. */
  @HopMetadataProperty private int hopType;
  /**
   * 1-based position in the source primary key; zero when the column is not part of the key (same
   * convention as catalog {@code SourceField}).
   */
  @HopMetadataProperty private int primaryKeyPosition;

  public SourceColumn(String name) {
    this.name = name;
  }

  public SourceColumn(SourceColumn other) {
    if (other == null) {
      return;
    }
    this.name = other.name;
    this.description = other.description;
    this.sourceDataType = other.sourceDataType;
    this.length = other.length;
    this.precision = other.precision;
    this.hopType = other.hopType;
    this.primaryKeyPosition = other.primaryKeyPosition;
  }

  public boolean isPrimaryKey() {
    return primaryKeyPosition > 0;
  }
}
