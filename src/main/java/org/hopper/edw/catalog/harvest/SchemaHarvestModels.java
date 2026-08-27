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
package org.hopper.edw.catalog.harvest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.hopper.edw.datavault.metadata.SourceField;

/** Immutable-ish domain types for schema metadata harvesting (observation only). */
public final class SchemaHarvestModels {

  private SchemaHarvestModels() {}

  public enum HarvestStatus {
    SUCCESS,
    PARTIAL,
    FAILED
  }

  public enum DiscoveryStatus {
    OK,
    UNAVAILABLE,
    ERROR,
    UNSUPPORTED
  }

  public enum FieldRole {
    DISCOVERED,
    EXPECTED
  }

  public enum BaselineMode {
    WORKING_CATALOG,
    CATALOG_VERSION
  }

  /** Request for one harvest run. */
  @Getter
  @Setter
  @Builder
  public static class HarvestRequest {
    private String harvestRunId;
    private String resourceGroupName;
    private String catalogConnection;
    private BaselineMode baselineMode;

    /** When baseline is {@link BaselineMode#CATALOG_VERSION}, the tag name. */
    private String baselineVersionTag;

    /** Optional record-source group filter (DV_SOURCE group field). */
    private String recordSourceGroupFilter;

    /** Optional Hop database connection name filter. */
    private String connectionNameFilter;

    /** Max concurrent source-system connections (default 8). */
    private int connectionParallelism;

    private String workflowName;
    private String workflowExecutionId;
  }

  @Getter
  @Builder
  public static class HarvestedField {
    private final FieldRole role;
    private final String fieldName;
    private final String hopType;
    private final String length;
    private final String precision;
    private final int primaryKeyPosition;
    private final String sourceDataType;

    public static HarvestedField fromSourceField(SourceField field, FieldRole role) {
      if (field == null) {
        return null;
      }
      String hopType = null;
      try {
        if (field.getHopType() >= 0) {
          hopType =
              org.apache.hop.core.row.value.ValueMetaFactory.getValueMetaName(field.getHopType());
        }
      } catch (Exception ignored) {
        hopType = String.valueOf(field.getHopType());
      }
      return HarvestedField.builder()
          .role(role)
          .fieldName(field.getName())
          .hopType(hopType)
          .length(field.getLength())
          .precision(field.getPrecision())
          .primaryKeyPosition(field.getPrimaryKeyPosition())
          .sourceDataType(field.getSourceDataType())
          .build();
    }
  }

  /** One foreign-key constraint snapshot (expected catalog contract or discovered live). */
  @Getter
  @Builder
  public static class HarvestedForeignKey {
    private final FieldRole role;
    private final String constraintName;
    private final String childSchema;
    private final String childTable;

    /** Ordered child (FK) column names, comma-joined for storage convenience. */
    private final String childColumns;

    private final String parentSchema;
    private final String parentTable;
    private final String parentColumns;

    public String signatureKey() {
      return Const.NVL(childSchema, "")
          + "."
          + Const.NVL(childTable, "")
          + ":"
          + normalizeList(childColumns)
          + "->"
          + Const.NVL(parentSchema, "")
          + "."
          + Const.NVL(parentTable, "")
          + ":"
          + normalizeList(parentColumns);
    }

    public String displayLabel() {
      String name = Utils.isEmpty(constraintName) ? "(unnamed)" : constraintName;
      return name
          + " ("
          + Const.NVL(childColumns, "")
          + " → "
          + Const.NVL(parentTable, "")
          + "("
          + Const.NVL(parentColumns, "")
          + "))";
    }

    private static String normalizeList(String columns) {
      if (Utils.isEmpty(columns)) {
        return "";
      }
      return columns.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
    }
  }

  @Getter
  @Builder
  public static class HarvestChange {
    private final String changeKind;
    private final String fieldName;
    private final String expectedDetail;
    private final String actualDetail;
    private final String severity;

    public static final String KIND_FOREIGN_KEY_ADDED = "FOREIGN_KEY_ADDED";
    public static final String KIND_FOREIGN_KEY_REMOVED = "FOREIGN_KEY_REMOVED";
    public static final String KIND_FOREIGN_KEY_CHANGED = "FOREIGN_KEY_CHANGED";

    public static HarvestChange fromFieldChange(
        RecordDefinitionSchemaDiffSupport.FieldChange change) {
      if (change == null) {
        return null;
      }
      String kind = change.kind() != null ? change.kind().name() : "CHANGED";
      String severity =
          switch (change.kind()) {
            case REMOVED, PRIMARY_KEY_CHANGED -> "BLOCKING";
            case ADDED, CHANGED -> "WARNING";
            default -> "WARNING";
          };
      return HarvestChange.builder()
          .changeKind(kind)
          .fieldName(change.fieldName())
          .expectedDetail(null)
          .actualDetail(change.details())
          .severity(severity)
          .build();
    }

    public static HarvestChange foreignKey(
        String kind,
        String fieldName,
        String expectedDetail,
        String actualDetail,
        String severity) {
      return HarvestChange.builder()
          .changeKind(kind)
          .fieldName(fieldName)
          .expectedDetail(expectedDetail)
          .actualDetail(actualDetail)
          .severity(severity)
          .build();
    }
  }

  @Getter
  @Setter
  @Builder
  public static class HarvestSubjectResult {
    private String subjectKey;
    private String catalogConnection;
    private String sourceType;
    private String databaseMetaName;
    private String schemaName;
    private String tableName;
    private DiscoveryStatus discoveryStatus;
    private boolean inSync;
    private String message;
    @Builder.Default private List<HarvestedField> fields = new ArrayList<>();
    @Builder.Default private List<HarvestedForeignKey> foreignKeys = new ArrayList<>();
    @Builder.Default private List<HarvestChange> changes = new ArrayList<>();

    public int changeCount() {
      return changes != null ? changes.size() : 0;
    }
  }

  @Getter
  @Setter
  @Builder
  public static class HarvestResult {
    private String harvestRunId;
    private Instant startedAt;
    private Instant finishedAt;
    private String resourceGroupName;
    private String catalogConnection;
    private String expectedBaseline;
    private HarvestStatus status;
    private String workflowName;
    private String workflowExecutionId;
    private String scopeSummary;
    @Builder.Default private List<HarvestSubjectResult> subjects = new ArrayList<>();
    @Builder.Default private List<String> infraErrors = new ArrayList<>();

    public int subjectCount() {
      return subjects != null ? subjects.size() : 0;
    }

    public int changeCount() {
      if (subjects == null) {
        return 0;
      }
      int total = 0;
      for (HarvestSubjectResult subject : subjects) {
        if (subject != null) {
          total += subject.changeCount();
        }
      }
      return total;
    }

    public int errorCount() {
      int errors = infraErrors != null ? infraErrors.size() : 0;
      if (subjects != null) {
        for (HarvestSubjectResult subject : subjects) {
          if (subject != null
              && (subject.getDiscoveryStatus() == DiscoveryStatus.ERROR
                  || subject.getDiscoveryStatus() == DiscoveryStatus.UNAVAILABLE)) {
            errors++;
          }
        }
      }
      return errors;
    }

    public int subjectsWithChanges() {
      if (subjects == null) {
        return 0;
      }
      int count = 0;
      for (HarvestSubjectResult subject : subjects) {
        if (subject != null && subject.changeCount() > 0) {
          count++;
        }
      }
      return count;
    }

    public List<HarvestSubjectResult> subjectsView() {
      return subjects == null ? List.of() : Collections.unmodifiableList(subjects);
    }
  }
}
