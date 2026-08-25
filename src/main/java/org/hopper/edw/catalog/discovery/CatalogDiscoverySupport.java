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

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionQuery;
import org.hopper.edw.catalog.model.RecordDefinitionRef;
import org.hopper.edw.catalog.registry.RecordDefinitionRegistry;
import org.hopper.edw.catalog.spi.DataCatalogPluginFactory;
import org.hopper.edw.catalog.spi.IDataCatalog;

/**
 * Shared catalog discovery for Verify remarks, transform dialogs, and the data-catalog metadata
 * editor. Lists the <em>working tree</em> only; version snapshots are reported separately.
 */
public final class CatalogDiscoverySupport {

  private static final Class<?> PKG = CatalogDiscoverySupport.class;

  private CatalogDiscoverySupport() {}

  /**
   * Empty namespace+name lists the whole working tree. Namespace only scopes to that prefix. Name
   * set (with or without namespace) is a single-key lookup.
   */
  public static RecordDefinitionQuery listQuery(String namespace, String name) {
    RecordDefinitionQuery query = new RecordDefinitionQuery();
    if (!Utils.isEmpty(namespace) && Utils.isEmpty(name)) {
      query.setNamespacePrefix(namespace);
    }
    return query;
  }

  public static boolean isSingleKeyLookup(String namespace, String name) {
    return !Utils.isEmpty(name);
  }

  public static CatalogDiscoverySnapshot inspectConnection(
      String connectionName,
      String namespace,
      String name,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    return inspectConnection(connectionName, namespace, name, variables, metadataProvider, true);
  }

  public static CatalogDiscoverySnapshot inspectConnection(
      String connectionName,
      String namespace,
      String name,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean listRecords) {
    CatalogDiscoverySnapshot snapshot = new CatalogDiscoverySnapshot();
    snapshot.setConnectionName(connectionName);
    if (Utils.isEmpty(connectionName)) {
      snapshot.setConnectionFound(false);
      snapshot.setErrorMessage(
          BaseMessages.getString(PKG, "CatalogDiscoverySupport.Error.ConnectionNameMissing"));
      return snapshot;
    }
    if (metadataProvider == null) {
      snapshot.setConnectionFound(false);
      snapshot.setErrorMessage(
          BaseMessages.getString(PKG, "CatalogDiscoverySupport.Error.NoMetadataProvider"));
      return snapshot;
    }
    try {
      DataCatalogMeta meta = loadMeta(connectionName, metadataProvider);
      if (meta == null) {
        snapshot.setConnectionFound(false);
        snapshot.setErrorMessage(
            BaseMessages.getString(
                PKG, "CatalogDiscoverySupport.Error.ConnectionNotFound", connectionName));
        return snapshot;
      }
      snapshot.setConnectionFound(true);
      snapshot.setEnabled(meta.isEnabled());
      if (!meta.isEnabled()) {
        snapshot.setErrorMessage(
            BaseMessages.getString(
                PKG, "CatalogDiscoverySupport.Error.ConnectionDisabled", connectionName));
        return snapshot;
      }
      fillFromConnectedCatalog(
          snapshot,
          RecordDefinitionRegistry.getInstance()
              .requireConnectedCatalog(connectionName, variables, metadataProvider),
          connectionName,
          namespace,
          name,
          variables,
          metadataProvider,
          listRecords);
    } catch (Exception e) {
      snapshot.setErrorMessage(Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
    }
    return snapshot;
  }

  /**
   * Inspects an in-memory connection (metadata editor Test) without writing it through the registry
   * cache.
   */
  public static CatalogDiscoverySnapshot inspectMeta(
      DataCatalogMeta meta, IVariables variables, IHopMetadataProvider metadataProvider) {
    CatalogDiscoverySnapshot snapshot = new CatalogDiscoverySnapshot();
    if (meta == null || Utils.isEmpty(meta.getName())) {
      snapshot.setConnectionFound(false);
      snapshot.setErrorMessage(
          BaseMessages.getString(PKG, "CatalogDiscoverySupport.Error.ConnectionNameMissing"));
      return snapshot;
    }
    snapshot.setConnectionName(meta.getName());
    snapshot.setConnectionFound(true);
    snapshot.setEnabled(meta.isEnabled());
    if (!meta.isEnabled()) {
      snapshot.setErrorMessage(
          BaseMessages.getString(
              PKG, "CatalogDiscoverySupport.Error.ConnectionDisabled", meta.getName()));
      return snapshot;
    }
    IDataCatalog catalog = null;
    try {
      catalog = DataCatalogPluginFactory.createConnected(meta, variables, metadataProvider);
      fillFromConnectedCatalog(
          snapshot, catalog, meta.getName(), null, null, variables, metadataProvider, true);
    } catch (Exception e) {
      snapshot.setErrorMessage(Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
    } finally {
      if (catalog != null) {
        try {
          catalog.disconnect();
        } catch (Exception ignored) {
          // Best effort.
        }
      }
    }
    return snapshot;
  }

  public static void addCheckRemarks(
      List<ICheckResult> remarks,
      TransformMeta transformMeta,
      String connectionName,
      String namespace,
      String name,
      boolean selectFromInput,
      boolean failIfNoDefinitions,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    CatalogDiscoverySnapshot snapshot =
        inspectConnection(
            connectionName,
            selectFromInput ? null : namespace,
            selectFromInput ? null : name,
            variables,
            metadataProvider,
            !selectFromInput);
    if (!snapshot.isConnectionFound() || !Utils.isEmpty(snapshot.getErrorMessage())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              Const.NVL(
                  snapshot.getErrorMessage(),
                  BaseMessages.getString(
                      PKG,
                      "CatalogDiscoverySupport.Error.ConnectionNotFound",
                      Const.NVL(connectionName, ""))),
              transformMeta));
      return;
    }

    remarks.add(
        new CheckResult(
            ICheckResult.TYPE_RESULT_OK,
            BaseMessages.getString(
                PKG,
                "CatalogDiscoverySupport.Check.ConnectionOk",
                snapshot.getConnectionName(),
                Const.NVL(snapshot.getPluginId(), "")),
            transformMeta));

    if (!Utils.isEmpty(snapshot.getResolvedStorageDirectory())) {
      int existsType =
          snapshot.isStorageDirectoryExists()
              ? ICheckResult.TYPE_RESULT_OK
              : ICheckResult.TYPE_RESULT_WARNING;
      remarks.add(
          new CheckResult(
              existsType,
              BaseMessages.getString(
                  PKG,
                  snapshot.isStorageDirectoryExists()
                      ? "CatalogDiscoverySupport.Check.StorageResolved"
                      : "CatalogDiscoverySupport.Check.StorageMissing",
                  snapshot.getResolvedStorageDirectory()),
              transformMeta));
    }

    if (selectFromInput) {
      return;
    }

    if (snapshot.getSkippedUnreadable() > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_WARNING,
              BaseMessages.getString(
                  PKG,
                  "CatalogDiscoverySupport.Check.SkippedUnreadable",
                  snapshot.getSkippedUnreadable()),
              transformMeta));
    }

    if (snapshot.getWorkingTreeCount() > 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_OK,
              BaseMessages.getString(
                  PKG,
                  "CatalogDiscoverySupport.Check.WorkingTreeCount",
                  snapshot.getWorkingTreeCount()),
              transformMeta));
      return;
    }

    int emptyType =
        failIfNoDefinitions ? ICheckResult.TYPE_RESULT_ERROR : ICheckResult.TYPE_RESULT_WARNING;
    String emptyKey =
        snapshot.isVersionSnapshotsPresent()
            ? "CatalogDiscoverySupport.Check.WorkingTreeEmptyWithVersions"
            : "CatalogDiscoverySupport.Check.WorkingTreeEmpty";
    remarks.add(
        new CheckResult(
            emptyType,
            BaseMessages.getString(
                PKG,
                emptyKey,
                snapshot.getConnectionName(),
                Const.NVL(snapshot.getResolvedStorageDirectory(), locationFallback(snapshot))),
            transformMeta));
  }

  private static void fillFromConnectedCatalog(
      CatalogDiscoverySnapshot snapshot,
      IDataCatalog catalog,
      String connectionName,
      String namespace,
      String name,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      boolean listRecords)
      throws HopException {
    snapshot.setPluginId(catalog != null ? catalog.getPluginId() : null);
    if (catalog instanceof FileDataCatalog fileCatalog) {
      snapshot.setStorageDirectory(fileCatalog.getStorageDirectory());
      if (fileCatalog.getResolvedRoot() != null) {
        snapshot.setResolvedStorageDirectory(fileCatalog.getResolvedRoot().toString());
        snapshot.setStorageDirectoryExists(Files.isDirectory(fileCatalog.getResolvedRoot()));
      }
    }
    if (catalog != null) {
      snapshot.setSkippedUnreadable(catalog.getLastSkippedUnreadable());
      snapshot.setVersionSnapshotsPresent(catalog.hasVersionSnapshots());
      if (Utils.isEmpty(snapshot.getResolvedStorageDirectory())) {
        snapshot.setResolvedStorageDirectory(catalog.describeLocation());
      }
    }
    if (!listRecords) {
      return;
    }
    List<RecordDefinitionRef> refs = new ArrayList<>();
    if (isSingleKeyLookup(namespace, name)) {
      RecordDefinition definition =
          catalog.read(new RecordDefinitionKey(Const.NVL(namespace, ""), name));
      if (definition != null) {
        refs.add(RecordDefinitionRef.of(connectionName, definition));
      }
    } else if (catalog != null && metadataProvider != null && variables != null) {
      // Prefer registry.list so session cache and connection-name stamping stay consistent when
      // the catalog came from requireConnectedCatalog. Throwaway inspectMeta lists directly.
      if (RecordDefinitionRegistry.getInstance().isConnected(connectionName, catalog)) {
        refs.addAll(
            RecordDefinitionRegistry.getInstance()
                .list(connectionName, listQuery(namespace, name), variables, metadataProvider));
      } else {
        for (RecordDefinitionRef ref : catalog.list(listQuery(namespace, name))) {
          if (ref != null) {
            ref.setCatalogConnectionName(connectionName);
            refs.add(ref);
          }
        }
      }
    }
    snapshot.setRefs(refs);
    snapshot.setWorkingTreeCount(refs.size());
    if (catalog != null) {
      snapshot.setSkippedUnreadable(catalog.getLastSkippedUnreadable());
    }
  }

  private static DataCatalogMeta loadMeta(String name, IHopMetadataProvider metadataProvider)
      throws HopException {
    IHopMetadataSerializer<DataCatalogMeta> serializer =
        metadataProvider.getSerializer(DataCatalogMeta.class);
    return serializer.load(name);
  }

  private static String locationFallback(CatalogDiscoverySnapshot snapshot) {
    return Const.NVL(snapshot.getResolvedStorageDirectory(), snapshot.getPluginId());
  }
}
