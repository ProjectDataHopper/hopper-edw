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
package org.apache.hop.datavault.metadata.coaching;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** A user-curated or derived coaching source entry stored on the model. */
@Getter
@Setter
public class CoachingSourceRef {

  @HopMetadataProperty private CoachingSourceType sourceType = CoachingSourceType.RECORD_DEFINITION;

  @HopMetadataProperty private String catalogConnection;

  @HopMetadataProperty private String recordNamespace;

  @HopMetadataProperty private String recordName;

  @HopMetadataProperty private String displayLabel;

  /** DM table name when source is auto-derived from SQL or pipeline configuration. */
  @HopMetadataProperty private String derivedFromTable;

  /** BV: DV table name when source is auto-derived from derivatives. */
  @HopMetadataProperty private String derivedDvTableName;

  @HopMetadataProperty private String derivedDvTableType;

  @HopMetadataProperty private boolean derived;

  public CoachingSourceRef() {}

  public static CoachingSourceRef forRecordDefinition(
      String catalogConnection, String recordNamespace, String recordName) {
    CoachingSourceRef ref = new CoachingSourceRef();
    ref.setSourceType(CoachingSourceType.RECORD_DEFINITION);
    ref.setCatalogConnection(catalogConnection);
    ref.setRecordNamespace(recordNamespace);
    ref.setRecordName(recordName);
    ref.setDerived(false);
    return ref;
  }

  public String identityKey() {
    if (sourceType == CoachingSourceType.RECORD_DEFINITION) {
      return "RD:"
          + nullSafe(catalogConnection)
          + ":"
          + nullSafe(recordNamespace)
          + ":"
          + nullSafe(recordName);
    }
    if (sourceType == CoachingSourceType.SQL) {
      return "SQL:" + nullSafe(derivedFromTable);
    }
    if (sourceType == CoachingSourceType.PIPELINE) {
      return "PL:" + nullSafe(derivedFromTable);
    }
    if (sourceType == CoachingSourceType.DV_DERIVATIVE) {
      return "DV:" + nullSafe(derivedDvTableName) + ":" + nullSafe(derivedDvTableType);
    }
    return "UNKNOWN";
  }

  public String resolvedDisplayLabel() {
    if (!Utils.isEmpty(displayLabel)) {
      return displayLabel;
    }
    if (sourceType == CoachingSourceType.RECORD_DEFINITION && !Utils.isEmpty(recordName)) {
      return recordName;
    }
    if (!Utils.isEmpty(derivedFromTable)) {
      return derivedFromTable;
    }
    if (!Utils.isEmpty(derivedDvTableName)) {
      return derivedDvTableName;
    }
    return identityKey();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CoachingSourceRef that)) {
      return false;
    }
    return Objects.equals(identityKey(), that.identityKey());
  }

  @Override
  public int hashCode() {
    return Objects.hash(identityKey());
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }
}
