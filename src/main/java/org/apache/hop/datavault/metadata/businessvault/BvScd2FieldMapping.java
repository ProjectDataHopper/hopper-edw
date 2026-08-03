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
package org.apache.hop.datavault.metadata.businessvault;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Maps one satellite source column to a uniquely named Business Vault SCD2 target column. */
@Getter
@Setter
@NoArgsConstructor
public class BvScd2FieldMapping {

  @HopMetadataProperty private String satelliteName;

  @HopMetadataProperty private String sourceFieldName;

  @HopMetadataProperty private String targetFieldName;

  public BvScd2FieldMapping(String satelliteName, String sourceFieldName, String targetFieldName) {
    this.satelliteName = satelliteName;
    this.sourceFieldName = sourceFieldName;
    this.targetFieldName = targetFieldName;
  }

  public BvScd2FieldMapping(BvScd2FieldMapping other) {
    if (other != null) {
      satelliteName = other.satelliteName;
      sourceFieldName = other.sourceFieldName;
      targetFieldName = other.targetFieldName;
    }
  }
}
