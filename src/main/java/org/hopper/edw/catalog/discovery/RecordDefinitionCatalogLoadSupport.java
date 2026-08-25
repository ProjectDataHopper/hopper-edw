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
package org.hopper.edw.catalog.discovery;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionQuery;
import org.hopper.edw.catalog.model.RecordDefinitionRef;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.spi.IDataCatalog;

/**
 * Working-tree catalog load used by Get Record Definition Names and Record Definition DDL.
 *
 * <p>Never lists {@code catalog-versions/} snapshots. A missing catalog connection fails; an empty
 * working tree is a logged warning unless {@code failIfNoDefinitions} is set.
 */
public final class RecordDefinitionCatalogLoadSupport {

  private static final Class<?> PKG = RecordDefinitionCatalogLoadSupport.class;

  private RecordDefinitionCatalogLoadSupport() {}

  public static RecordDefinitionLoadResult loadDefinitions(
      String connectionName,
      String namespace,
      String name,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(connectionName)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "RecordDefinitionCatalogLoadSupport.Error.ConnectionMissing"));
    }
    RecordDefinitionRegistry registry = RecordDefinitionRegistry.getInstance();
    IDataCatalog catalog =
        registry.requireConnectedCatalog(connectionName, variables, metadataProvider);

    List<RecordDefinition> definitions = new ArrayList<>();
    int refCount;
    int skippedNullReads = 0;
    if (CatalogDiscoverySupport.isSingleKeyLookup(namespace, name)) {
      RecordDefinition definition =
          registry.read(
              connectionName,
              new RecordDefinitionKey(Const.NVL(namespace, ""), name),
              variables,
              metadataProvider);
      refCount = definition != null ? 1 : 0;
      if (definition != null) {
        definitions.add(definition);
      } else {
        skippedNullReads = 1;
      }
    } else {
      RecordDefinitionQuery query = CatalogDiscoverySupport.listQuery(namespace, name);
      List<RecordDefinitionRef> refs =
          registry.list(connectionName, query, variables, metadataProvider);
      refCount = refs.size();
      for (RecordDefinitionRef ref : refs) {
        if (ref == null || ref.getKey() == null) {
          skippedNullReads++;
          continue;
        }
        RecordDefinition definition =
            registry.read(connectionName, ref.getKey(), variables, metadataProvider);
        if (definition != null) {
          definitions.add(definition);
        } else {
          skippedNullReads++;
        }
      }
    }

    return RecordDefinitionLoadResult.builder()
        .definitions(definitions)
        .connectionName(connectionName)
        .namespace(namespace)
        .name(name)
        .locationDescription(catalog.describeLocation())
        .refCount(refCount)
        .skippedNullReads(skippedNullReads)
        .skippedUnreadable(catalog.getLastSkippedUnreadable())
        .versionSnapshotsPresent(catalog.hasVersionSnapshots())
        .build();
  }

  public static void emitLogs(ILogChannel log, RecordDefinitionLoadResult result) {
    if (log == null || result == null) {
      return;
    }
    log.logBasic(
        BaseMessages.getString(
            PKG,
            "RecordDefinitionCatalogLoadSupport.Log.Listed",
            Const.NVL(result.getConnectionName(), ""),
            Const.NVL(result.getLocationDescription(), ""),
            result.getDefinitions().size(),
            result.getRefCount(),
            Const.NVL(result.getNamespace(), ""),
            Const.NVL(result.getName(), ""),
            result.getSkippedUnreadable(),
            result.getSkippedNullReads()));
    if (result.getDefinitions().isEmpty()) {
      log.logMinimal(emptyWorkingTreeMessage(result));
    }
  }

  public static void throwIfEmpty(RecordDefinitionLoadResult result, boolean failIfNoDefinitions)
      throws HopException {
    if (failIfNoDefinitions && (result == null || result.getDefinitions().isEmpty())) {
      throw new HopException(emptyWorkingTreeMessage(result));
    }
  }

  public static String emptyWorkingTreeMessage(RecordDefinitionLoadResult result) {
    if (result == null) {
      return BaseMessages.getString(PKG, "RecordDefinitionCatalogLoadSupport.Error.EmptyUnknown");
    }
    String key =
        result.isVersionSnapshotsPresent()
            ? "RecordDefinitionCatalogLoadSupport.Error.EmptyWithVersions"
            : "RecordDefinitionCatalogLoadSupport.Error.Empty";
    return BaseMessages.getString(
        PKG,
        key,
        Const.NVL(result.getConnectionName(), ""),
        Const.NVL(result.getLocationDescription(), ""));
  }
}
