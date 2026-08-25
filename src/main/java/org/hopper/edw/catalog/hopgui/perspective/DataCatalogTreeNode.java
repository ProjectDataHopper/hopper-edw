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
package org.hopper.edw.catalog.hopgui.perspective;

import org.hopper.edw.catalog.model.RecordDefinitionKey;
import org.hopper.edw.catalog.model.RecordDefinitionRef;

/** Data attached to items in the data catalog perspective tree. */
public final class DataCatalogTreeNode {

  public enum Type {
    CATALOG,
    /** Live working-tree records (what Get Record Definition Names lists). */
    WORKING_ROOT,
    /** Placeholder when the working tree is empty. */
    WORKING_EMPTY_HINT,
    NAMESPACE,
    RECORD,
    /** Virtual folder listing catalog version tags. */
    VERSIONS_ROOT,
    /** A single catalog version tag (immutable snapshot). */
    VERSION_TAG,
    /** A record definition inside a catalog version snapshot (read-only). */
    VERSION_RECORD
  }

  private final Type type;
  private final String catalogConnectionName;
  private final String namespace;
  private final RecordDefinitionKey recordKey;
  private final String versionTag;

  private DataCatalogTreeNode(
      Type type,
      String catalogConnectionName,
      String namespace,
      RecordDefinitionKey recordKey,
      String versionTag) {
    this.type = type;
    this.catalogConnectionName = catalogConnectionName;
    this.namespace = namespace;
    this.recordKey = recordKey;
    this.versionTag = versionTag;
  }

  public static DataCatalogTreeNode catalog(String connectionName) {
    return new DataCatalogTreeNode(Type.CATALOG, connectionName, null, null, null);
  }

  public static DataCatalogTreeNode workingRoot(String connectionName) {
    return new DataCatalogTreeNode(Type.WORKING_ROOT, connectionName, null, null, null);
  }

  public static DataCatalogTreeNode workingEmptyHint(String connectionName) {
    return new DataCatalogTreeNode(Type.WORKING_EMPTY_HINT, connectionName, null, null, null);
  }

  public static DataCatalogTreeNode namespace(String connectionName, String namespace) {
    return new DataCatalogTreeNode(Type.NAMESPACE, connectionName, namespace, null, null);
  }

  public static DataCatalogTreeNode record(String connectionName, RecordDefinitionRef ref) {
    return new DataCatalogTreeNode(Type.RECORD, connectionName, null, ref.getKey(), null);
  }

  public static DataCatalogTreeNode versionsRoot(String connectionName) {
    return new DataCatalogTreeNode(Type.VERSIONS_ROOT, connectionName, null, null, null);
  }

  public static DataCatalogTreeNode versionTag(String connectionName, String tag) {
    return new DataCatalogTreeNode(Type.VERSION_TAG, connectionName, null, null, tag);
  }

  public static DataCatalogTreeNode versionRecord(
      String connectionName, String tag, RecordDefinitionKey key) {
    return new DataCatalogTreeNode(Type.VERSION_RECORD, connectionName, null, key, tag);
  }

  public Type getType() {
    return type;
  }

  public String getCatalogConnectionName() {
    return catalogConnectionName;
  }

  public String getNamespace() {
    return namespace;
  }

  public RecordDefinitionKey getRecordKey() {
    return recordKey;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public boolean isReadOnlyVersionView() {
    return type == Type.VERSION_RECORD || type == Type.VERSION_TAG || type == Type.VERSIONS_ROOT;
  }
}
