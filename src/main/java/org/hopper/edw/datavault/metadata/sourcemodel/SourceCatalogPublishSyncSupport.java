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
package org.hopper.edw.datavault.metadata.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.discovery.RecordDefinitionSchemaDiffSupport;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.datavault.catalog.DvCatalogNamespaces;
import org.hopper.edw.datavault.catalog.DvSourceFieldSupport;
import org.hopper.edw.datavault.metadata.SourceField;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourceJsonCatalogPublisher;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourcePipelineCatalogPublisher;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourceQueryCatalogPublisher;
import org.hopper.edw.datavault.metadata.sourcemodel.publish.SourceTableCatalogPublisher;

/**
 * Compares a source-model card's effective layout (including data type mappings) to the published
 * catalog feed so Check / Validate can say "republish" instead of looking like live-source drift.
 */
public final class SourceCatalogPublishSyncSupport {

  private static final Class<?> PKG = SourceModel.class;
  private static final int MAX_CHANGE_DETAILS = 4;

  public enum SourceCardKind {
    TABLE,
    QUERY,
    JSON,
    PIPELINE
  }

  /** A catalog feed that already exists and no longer matches the source-model effective layout. */
  public record StalePublishedFeed(
      SourceCardKind kind, String cardName, String feedName, String details) {}

  private SourceCatalogPublishSyncSupport() {}

  /**
   * Published catalog feeds whose layout lags the current source-model cards. Never-published cards
   * are omitted so save reminders do not nag about drafts.
   */
  public static List<StalePublishedFeed> listStalePublishedFeeds(
      SourceModel model, IVariables variables, IHopMetadataProvider metadataProvider) {
    List<StalePublishedFeed> stale = new ArrayList<>();
    if (model == null
        || metadataProvider == null
        || Utils.isEmpty(resolveCatalogConnection(model, variables))) {
      return stale;
    }
    for (SourceTable table : model.getTables()) {
      addIfStale(
          stale,
          SourceCardKind.TABLE,
          table == null ? null : table.getName(),
          table == null ? null : SourceTableCatalogPublisher.resolveCatalogFeedName(table),
          table == null ? List.of() : safeTableFields(table, metadataProvider),
          model,
          variables,
          metadataProvider);
    }
    for (SourceQuery query : model.getQueries()) {
      if (query == null) {
        continue;
      }
      String feedName =
          !Utils.isEmpty(query.getPublishedCatalogName())
              ? query.getPublishedCatalogName().trim()
              : query.getName();
      addIfStale(
          stale,
          SourceCardKind.QUERY,
          query.getName(),
          feedName,
          safeQueryFields(model, query, metadataProvider),
          model,
          variables,
          metadataProvider);
    }
    for (SourceJson jsonSource : model.getJsonSources()) {
      if (jsonSource == null) {
        continue;
      }
      String feedName =
          !Utils.isEmpty(jsonSource.getPublishedCatalogName())
              ? jsonSource.getPublishedCatalogName().trim()
              : jsonSource.getName();
      addIfStale(
          stale,
          SourceCardKind.JSON,
          jsonSource.getName(),
          feedName,
          safeJsonFields(model, jsonSource, metadataProvider),
          model,
          variables,
          metadataProvider);
    }
    for (SourcePipeline pipeline : model.getPipelineSources()) {
      if (pipeline == null) {
        continue;
      }
      addIfStale(
          stale,
          SourceCardKind.PIPELINE,
          pipeline.getName(),
          pipeline.resolveCatalogSourceName(),
          safePipelineFields(pipeline, metadataProvider),
          model,
          variables,
          metadataProvider);
    }
    return stale;
  }

  private static void addIfStale(
      List<StalePublishedFeed> stale,
      SourceCardKind kind,
      String cardName,
      String feedName,
      List<SourceField> effectiveFields,
      SourceModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    CatalogCompareResult compared =
        comparePublishedFeed(model, feedName, effectiveFields, variables, metadataProvider);
    if (compared.status() != CatalogCompareStatus.STALE) {
      return;
    }
    stale.add(
        new StalePublishedFeed(
            kind, Const.NVL(cardName, "?"), feedName.trim(), Const.NVL(compared.details(), "")));
  }

  private static List<SourceField> safeTableFields(
      SourceTable table, IHopMetadataProvider metadataProvider) {
    try {
      return SourceTableCatalogPublisher.buildFieldsFromTable(table, metadataProvider);
    } catch (Exception e) {
      return List.of();
    }
  }

  private static List<SourceField> safeQueryFields(
      SourceModel model, SourceQuery query, IHopMetadataProvider metadataProvider) {
    try {
      return SourceQueryCatalogPublisher.buildFieldsFromProjection(model, query, metadataProvider);
    } catch (Exception e) {
      return List.of();
    }
  }

  private static List<SourceField> safeJsonFields(
      SourceModel model, SourceJson jsonSource, IHopMetadataProvider metadataProvider) {
    try {
      return SourceJsonCatalogPublisher.buildFieldsFromProjection(
          model, jsonSource, metadataProvider);
    } catch (Exception e) {
      return List.of();
    }
  }

  private static List<SourceField> safePipelineFields(
      SourcePipeline pipeline, IHopMetadataProvider metadataProvider) {
    try {
      return SourcePipelineCatalogPublisher.buildFieldsFromProjection(pipeline, metadataProvider);
    } catch (Exception e) {
      return List.of();
    }
  }

  private enum CatalogCompareStatus {
    SKIPPED,
    MISSING,
    STALE,
    IN_SYNC,
    FAILED
  }

  private record CatalogCompareResult(
      CatalogCompareStatus status,
      String details,
      String error,
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff) {}

  public static List<ICheckResult> checkPipeline(
      SourceModel model,
      SourcePipeline pipeline,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (pipeline == null) {
      return List.of();
    }
    String feedName = pipeline.resolveCatalogSourceName();
    List<SourceField> effective = List.of();
    try {
      effective =
          SourcePipelineCatalogPublisher.buildFieldsFromProjection(pipeline, metadataProvider);
    } catch (Exception ignored) {
      // Structural checks already cover empty/invalid projections.
    }
    return check(
        model, "pipeline", pipeline.getName(), feedName, effective, variables, metadataProvider);
  }

  public static List<ICheckResult> checkJson(
      SourceModel model,
      SourceJson jsonSource,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (jsonSource == null) {
      return List.of();
    }
    String feedName =
        !Utils.isEmpty(jsonSource.getPublishedCatalogName())
            ? jsonSource.getPublishedCatalogName().trim()
            : jsonSource.getName();
    List<SourceField> effective = List.of();
    try {
      effective =
          SourceJsonCatalogPublisher.buildFieldsFromProjection(model, jsonSource, metadataProvider);
    } catch (Exception ignored) {
      // Structural checks already cover empty/invalid projections.
    }
    return check(
        model,
        "JSON source",
        jsonSource.getName(),
        feedName,
        effective,
        variables,
        metadataProvider);
  }

  public static List<ICheckResult> checkQuery(
      SourceModel model,
      SourceQuery query,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (query == null) {
      return List.of();
    }
    String feedName =
        !Utils.isEmpty(query.getPublishedCatalogName())
            ? query.getPublishedCatalogName().trim()
            : query.getName();
    List<SourceField> effective = List.of();
    try {
      effective =
          SourceQueryCatalogPublisher.buildFieldsFromProjection(model, query, metadataProvider);
    } catch (Exception ignored) {
      // Free-SQL / incomplete projections are reported by query checks.
    }
    return check(model, "query", query.getName(), feedName, effective, variables, metadataProvider);
  }

  public static List<ICheckResult> checkTable(
      SourceModel model,
      SourceTable table,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (table == null) {
      return List.of();
    }
    String feedName = SourceTableCatalogPublisher.resolveCatalogFeedName(table);
    List<SourceField> effective = List.of();
    try {
      effective = SourceTableCatalogPublisher.buildFieldsFromTable(table, metadataProvider);
    } catch (Exception ignored) {
      // Structural checks already cover empty tables.
    }
    return check(model, "table", table.getName(), feedName, effective, variables, metadataProvider);
  }

  static List<ICheckResult> remarksForDiff(
      String sourceKind,
      String cardName,
      String feedName,
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff) {
    List<ICheckResult> remarks = new ArrayList<>();
    if (diff == null || !diff.hasChanges()) {
      return remarks;
    }
    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_ERROR,
            BaseMessages.getString(
                PKG,
                "SourceModel.CheckResult.CatalogStale",
                Const.NVL(feedName, "?"),
                Const.NVL(sourceKind, "source"),
                Const.NVL(cardName, "?"),
                summarize(diff)),
            null));
    return remarks;
  }

  static List<ICheckResult> remarksForMissingFeed(
      String sourceKind, String cardName, String feedName) {
    return List.of(
        new CheckResult(
            ICheckResult.TYPE_RESULT_WARNING,
            BaseMessages.getString(
                PKG,
                "SourceModel.CheckResult.CatalogNotPublished",
                Const.NVL(sourceKind, "source"),
                Const.NVL(cardName, "?"),
                Const.NVL(feedName, "?")),
            null));
  }

  private static List<ICheckResult> check(
      SourceModel model,
      String sourceKind,
      String cardName,
      String catalogFeedName,
      List<SourceField> effectiveFields,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    CatalogCompareResult compared =
        comparePublishedFeed(model, catalogFeedName, effectiveFields, variables, metadataProvider);
    return switch (compared.status()) {
      case SKIPPED, IN_SYNC -> List.of();
      case MISSING -> remarksForMissingFeed(sourceKind, cardName, catalogFeedName);
      case STALE -> remarksForDiff(sourceKind, cardName, catalogFeedName, compared.diff());
      case FAILED ->
          List.of(
              new CheckResult(
                  ICheckResult.TYPE_RESULT_WARNING,
                  BaseMessages.getString(
                      PKG,
                      "SourceModel.CheckResult.CatalogCompareFailed",
                      Const.NVL(sourceKind, "source"),
                      Const.NVL(cardName, "?"),
                      Const.NVL(catalogFeedName, "?"),
                      Const.NVL(compared.error(), "compare failed")),
                  null));
    };
  }

  private static CatalogCompareResult comparePublishedFeed(
      SourceModel model,
      String catalogFeedName,
      List<SourceField> effectiveFields,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (Utils.isEmpty(catalogFeedName)
        || effectiveFields == null
        || effectiveFields.isEmpty()
        || metadataProvider == null) {
      return new CatalogCompareResult(CatalogCompareStatus.SKIPPED, null, null, null);
    }
    String catalogConnection = resolveCatalogConnection(model, variables);
    if (Utils.isEmpty(catalogConnection)) {
      return new CatalogCompareResult(CatalogCompareStatus.SKIPPED, null, null, null);
    }
    String namespace = resolveNamespace(model, variables);
    RecordDefinitionKey key = new RecordDefinitionKey(namespace, catalogFeedName.trim());
    RecordDefinition definition;
    try {
      definition =
          RecordDefinitionRegistry.getInstance()
              .read(catalogConnection, key, variables, metadataProvider);
    } catch (Exception e) {
      return new CatalogCompareResult(
          CatalogCompareStatus.FAILED,
          null,
          Const.NVL(e.getMessage(), e.getClass().getSimpleName()),
          null);
    }
    if (definition == null) {
      return new CatalogCompareResult(CatalogCompareStatus.MISSING, null, null, null);
    }
    try {
      List<SourceField> catalogFields = DvSourceFieldSupport.sourceFieldsFromDefinition(definition);
      RecordDefinitionSchemaDiffSupport.SchemaDiff diff =
          RecordDefinitionSchemaDiffSupport.diff(catalogFields, effectiveFields);
      if (diff == null || !diff.hasChanges()) {
        return new CatalogCompareResult(CatalogCompareStatus.IN_SYNC, null, null, diff);
      }
      return new CatalogCompareResult(CatalogCompareStatus.STALE, summarize(diff), null, diff);
    } catch (Exception e) {
      return new CatalogCompareResult(
          CatalogCompareStatus.FAILED,
          null,
          Const.NVL(e.getMessage(), e.getClass().getSimpleName()),
          null);
    }
  }

  private static String resolveCatalogConnection(SourceModel model, IVariables variables) {
    if (model == null || model.getConfigurationOrDefault() == null) {
      return null;
    }
    String connection = model.getConfigurationOrDefault().getCatalogConnection();
    if (Utils.isEmpty(connection)) {
      return null;
    }
    return variables != null ? variables.resolve(connection) : connection.trim();
  }

  private static String resolveNamespace(SourceModel model, IVariables variables) {
    if (model != null && model.getConfigurationOrDefault() != null) {
      String configured = model.getConfigurationOrDefault().getCatalogNamespace();
      if (!Utils.isEmpty(configured)) {
        return variables != null ? variables.resolve(configured) : configured.trim();
      }
    }
    return DvCatalogNamespaces.projectSourcesNamespace(variables);
  }

  static String summarize(RecordDefinitionSchemaDiffSupport.SchemaDiff diff) {
    if (diff == null || !diff.hasChanges()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    int shown = 0;
    for (RecordDefinitionSchemaDiffSupport.FieldChange change : diff.changes()) {
      if (change == null) {
        continue;
      }
      if (shown >= MAX_CHANGE_DETAILS) {
        int remaining = diff.changes().size() - shown;
        if (remaining > 0) {
          sb.append("; +").append(remaining).append(" more");
        }
        break;
      }
      if (shown > 0) {
        sb.append("; ");
      }
      if (change.kind() == RecordDefinitionSchemaDiffSupport.ChangeKind.CHANGED) {
        sb.append(Const.NVL(change.fieldName(), "?"))
            .append(" (")
            .append(Const.NVL(change.details(), "changed"))
            .append(')');
      } else {
        sb.append(change.kind()).append(' ').append(Const.NVL(change.fieldName(), ""));
      }
      shown++;
    }
    return sb.toString();
  }
}
