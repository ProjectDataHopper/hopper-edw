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
package org.hopper.edw.datavault.lineage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.hopper.edw.catalog.model.CatalogCustomProperty;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionType;
import org.hopper.edw.catalog.model.RecordOrigin;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.hopper.edw.datavault.catalog.CatalogModelRegistrySupport;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Publishes derived {@link LineageSnapshot} content as sibling catalog records under {@code
 * hop/{project}/lineage/{layer}/{model}/…}.
 *
 * <p>Models remain the source of truth; catalog entries are a read-only projection for discovery
 * and version baselines.
 */
public final class LineageCatalogPublisher {

  private static final ObjectMapper JSON = new ObjectMapper();

  private LineageCatalogPublisher() {}

  /** Optional logging callbacks used by catalog publishers and workflow actions. */
  public interface CatalogPublishLog {
    default void logBasic(String message) {}

    default void logError(String message, Throwable throwable) {}
  }

  @Getter
  public static final class PublishResult {
    private final int lineageRecordCount;
    private final int errorCount;

    public PublishResult(int lineageRecordCount, int errorCount) {
      this.lineageRecordCount = lineageRecordCount;
      this.errorCount = errorCount;
    }

    public boolean isSuccess() {
      return errorCount == 0;
    }
  }

  public static PublishResult publish(
      String catalogConnectionName,
      LineageSnapshot snapshot,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName)
      throws HopException {
    return publish(
        catalogConnectionName, snapshot, variables, metadataProvider, workflowName, null);
  }

  public static PublishResult publish(
      String catalogConnectionName,
      LineageSnapshot snapshot,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      String workflowName,
      CatalogPublishLog log)
      throws HopException {
    if (Utils.isEmpty(catalogConnectionName)) {
      throw new HopException("Data catalog connection name is required for lineage publishing");
    }
    if (snapshot == null) {
      throw new HopException("Lineage snapshot is required for lineage publishing");
    }

    String modelBasename =
        LineageCatalogNamespaces.sanitize(
            !Utils.isEmpty(snapshot.getModelName()) ? snapshot.getModelName() : "model");
    String namespace =
        LineageCatalogNamespaces.projectLineageNamespace(
            variables, snapshot.getModelLayer(), modelBasename);
    Date updatedAt = new Date();
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();

    int published = 0;
    int errors = 0;

    // Model-level snapshot index
    try {
      RecordDefinition snapshotDef =
          toSnapshotRecord(snapshot, namespace, variables, updatedAt, workflowName);
      upsert(registry, catalogConnectionName, snapshotDef, variables, metadataProvider, updatedAt);
      published++;
      if (log != null) {
        log.logBasic("Published lineage snapshot: " + snapshotDef.getKey());
      }
    } catch (Exception e) {
      errors++;
      if (log != null) {
        log.logError("Failed to publish lineage snapshot for model '" + modelBasename + "'", e);
      }
    }

    for (TableLineage table : snapshot.getTables()) {
      if (table == null || Utils.isEmpty(table.getLogicalName())) {
        continue;
      }
      try {
        RecordDefinition definition =
            toTableRecord(table, snapshot, namespace, variables, updatedAt, workflowName);
        upsert(registry, catalogConnectionName, definition, variables, metadataProvider, updatedAt);
        published++;
        if (log != null) {
          log.logBasic("Published lineage table record: " + definition.getKey());
        }
      } catch (Exception e) {
        errors++;
        if (log != null) {
          log.logError("Failed to publish lineage for table '" + table.getLogicalName() + "'", e);
        }
      }
    }

    return new PublishResult(published, errors);
  }

  static RecordDefinition toSnapshotRecord(
      LineageSnapshot snapshot,
      String namespace,
      IVariables variables,
      Date updatedAt,
      String workflowName)
      throws Exception {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(
        new RecordDefinitionKey(namespace, LineageCatalogNamespaces.SNAPSHOT_RECORD_NAME));
    definition.setType(RecordDefinitionType.UNKNOWN);
    definition.setDescription(
        "Source-to-target lineage snapshot for model "
            + nvl(snapshot.getModelName())
            + " ("
            + snapshot.getModelLayer()
            + ")");
    definition.setOrigin(buildOrigin(snapshot, null, variables, updatedAt, workflowName));
    definition.getTags().add(LineageCatalogNamespaces.TAG_LINEAGE);
    definition.getTags().add(LineageCatalogNamespaces.TAG_LINEAGE_SNAPSHOT);
    if (snapshot.getModelLayer() != null) {
      definition.getTags().add(snapshot.getModelLayer().name());
    }
    if (!Utils.isEmpty(snapshot.getModelName())) {
      definition.getTags().add(snapshot.getModelName());
    }

    Map<String, Object> compact = new LinkedHashMap<>();
    compact.put("id", snapshot.getId());
    compact.put(
        "capturedAt", snapshot.getCapturedAt() != null ? snapshot.getCapturedAt().getTime() : null);
    compact.put(
        "modelLayer", snapshot.getModelLayer() != null ? snapshot.getModelLayer().name() : null);
    compact.put("modelName", snapshot.getModelName());
    compact.put("modelFilename", snapshot.getModelFilename());
    compact.put("projectKey", snapshot.getProjectKey());
    compact.put(
        "tables",
        snapshot.getTables().stream()
            .filter(t -> t != null && !Utils.isEmpty(t.getLogicalName()))
            .map(TableLineage::getLogicalName)
            .toList());

    definition
        .getCustomProperties()
        .put(
            LineageCatalogNamespaces.PROP_LAYER,
            CatalogCustomProperty.string(
                snapshot.getModelLayer() != null ? snapshot.getModelLayer().name() : "DV"));
    definition
        .getCustomProperties()
        .put(
            LineageCatalogNamespaces.PROP_TABLE_COUNT,
            CatalogCustomProperty.string(Integer.toString(snapshot.getTables().size())));
    definition
        .getCustomProperties()
        .put(
            LineageCatalogNamespaces.PROP_SNAPSHOT_JSON,
            CatalogCustomProperty.string(JSON.writeValueAsString(compact)));
    return definition;
  }

  static RecordDefinition toTableRecord(
      TableLineage table,
      LineageSnapshot snapshot,
      String namespace,
      IVariables variables,
      Date updatedAt,
      String workflowName)
      throws Exception {
    RecordDefinition definition = new RecordDefinition();
    definition.setKey(new RecordDefinitionKey(namespace, table.getLogicalName()));
    definition.setType(RecordDefinitionType.UNKNOWN);
    definition.setDescription(
        "Source-to-target lineage for "
            + nvl(table.getTableType())
            + " "
            + nvl(table.getLogicalName()));
    definition.setOrigin(
        buildOrigin(snapshot, table.getLogicalName(), variables, updatedAt, workflowName));
    definition.getTags().add(LineageCatalogNamespaces.TAG_LINEAGE);
    if (table.getLayer() != null) {
      definition.getTags().add(table.getLayer().name());
    } else if (snapshot.getModelLayer() != null) {
      definition.getTags().add(snapshot.getModelLayer().name());
    }
    if (!Utils.isEmpty(table.getTableType())) {
      definition.getTags().add(table.getTableType());
    }
    if (!Utils.isEmpty(snapshot.getModelName())) {
      definition.getTags().add(snapshot.getModelName());
    }

    definition
        .getCustomProperties()
        .put(
            LineageCatalogNamespaces.PROP_LAYER,
            CatalogCustomProperty.string(
                table.getLayer() != null
                    ? table.getLayer().name()
                    : (snapshot.getModelLayer() != null ? snapshot.getModelLayer().name() : "DV")));
    definition
        .getCustomProperties()
        .put(
            LineageCatalogNamespaces.PROP_PHYSICAL_TABLE,
            CatalogCustomProperty.string(nvl(table.getPhysicalTableName())));
    definition
        .getCustomProperties()
        .put(
            LineageCatalogNamespaces.PROP_TABLE_TYPE,
            CatalogCustomProperty.string(nvl(table.getTableType())));
    definition
        .getCustomProperties()
        .put(
            LineageCatalogNamespaces.PROP_TABLE_REASONS_JSON,
            CatalogCustomProperty.string(
                JSON.writeValueAsString(reasonsToMaps(table.getReasons()))));
    definition
        .getCustomProperties()
        .put(
            LineageCatalogNamespaces.PROP_SOURCES_JSON,
            CatalogCustomProperty.string(
                JSON.writeValueAsString(sourcesToMaps(table.getSources()))));
    definition
        .getCustomProperties()
        .put(
            LineageCatalogNamespaces.PROP_FIELDS_JSON,
            CatalogCustomProperty.string(JSON.writeValueAsString(fieldsToMaps(table.getFields()))));
    return definition;
  }

  private static RecordOrigin buildOrigin(
      LineageSnapshot snapshot,
      String elementName,
      IVariables variables,
      Date updatedAt,
      String workflowName) {
    RecordOrigin origin = new RecordOrigin();
    origin.setModelType(
        snapshot.getModelLayer() != null
            ? snapshot.getModelLayer().name() + "_LINEAGE"
            : "LINEAGE");
    origin.setModelName(snapshot.getModelName());
    origin.setModelFilename(
        CatalogModelRegistrySupport.portableModelPath(snapshot.getModelFilename(), variables));
    origin.setModelElementName(elementName);
    origin.setHopProject(DvCatalogNamespaces.resolveProjectKey(variables));
    origin.setUpdatedAt(updatedAt);
    origin.setLastWorkflow(workflowName);
    if (origin.getCreatedAt() == null) {
      origin.setCreatedAt(updatedAt);
    }
    return origin;
  }

  private static void upsert(
      RecordDefinitionRegistry registry,
      String catalogConnectionName,
      RecordDefinition definition,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Date updatedAt)
      throws HopException {
    definition.validate();
    RecordDefinition existing =
        registry.read(catalogConnectionName, definition.getKey(), variables, metadataProvider);
    if (definition.getOrigin() != null
        && existing != null
        && existing.getOrigin() != null
        && existing.getOrigin().getCreatedAt() != null) {
      definition.getOrigin().setCreatedAt(existing.getOrigin().getCreatedAt());
    }
    // Lineage is fully derived — full replace is intentional (no quality-rule merge needed).
    registry.upsert(catalogConnectionName, definition, variables, metadataProvider);
  }

  private static List<Map<String, Object>> reasonsToMaps(List<LineageReason> reasons) {
    List<Map<String, Object>> list = new ArrayList<>();
    if (reasons == null) {
      return list;
    }
    for (LineageReason reason : reasons) {
      if (reason == null) {
        continue;
      }
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("code", reason.getCode() != null ? reason.getCode().name() : null);
      map.put("message", reason.getMessage());
      map.put("confidence", reason.getConfidence() != null ? reason.getConfidence().name() : null);
      map.put("evidence", reason.getEvidence());
      list.add(map);
    }
    return list;
  }

  private static List<Map<String, Object>> sourcesToMaps(List<TableSourceRef> sources) {
    List<Map<String, Object>> list = new ArrayList<>();
    if (sources == null) {
      return list;
    }
    for (TableSourceRef source : sources) {
      if (source == null) {
        continue;
      }
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("kind", source.getKind() != null ? source.getKind().name() : null);
      map.put("name", source.getName());
      map.put("catalogKey", source.getCatalogKey());
      map.put("physicalRef", source.getPhysicalRef());
      map.put("role", source.getRole() != null ? source.getRole().name() : null);
      list.add(map);
    }
    return list;
  }

  private static List<Map<String, Object>> fieldsToMaps(List<FieldLineage> fields) {
    List<Map<String, Object>> list = new ArrayList<>();
    if (fields == null) {
      return list;
    }
    for (FieldLineage field : fields) {
      if (field == null) {
        continue;
      }
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("targetFieldName", field.getTargetFieldName());
      map.put("dataType", field.getDataType());
      map.put("length", field.getLength());
      map.put("precision", field.getPrecision());
      map.put("technical", field.isTechnical());
      List<Map<String, Object>> contributions = new ArrayList<>();
      for (FieldContribution contribution : field.getContributions()) {
        if (contribution == null) {
          continue;
        }
        Map<String, Object> c = new LinkedHashMap<>();
        c.put(
            "sourceKind",
            contribution.getSourceKind() != null ? contribution.getSourceKind().name() : null);
        c.put("sourceName", contribution.getSourceName());
        c.put("sourceCatalogKey", contribution.getSourceCatalogKey());
        c.put("sourceFieldName", contribution.getSourceFieldName());
        c.put(
            "transform",
            contribution.getTransform() != null ? contribution.getTransform().name() : null);
        c.put("reasons", reasonsToMaps(contribution.getReasons()));
        contributions.add(c);
      }
      map.put("contributions", contributions);
      list.add(map);
    }
    return list;
  }

  private static String nvl(String value) {
    return value != null ? value : "";
  }
}
