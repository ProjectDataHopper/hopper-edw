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
package org.apache.hop.datavault.openlineage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hop.core.util.Utils;

/** Builds OpenLineage dataset facets for physical location metadata. */
public final class OpenLineageDatasetFacetSupport {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static final String HOP_LOCATION_SCHEMA_URL =
      "https://github.com/ProjectDataHopper/hopper-edw#hop-location-facet";

  private OpenLineageDatasetFacetSupport() {}

  /**
   * Attaches {@code dataSource} and {@code hop_location} facets to a dataset node when location is
   * known. Merges into existing facets object if present.
   *
   * <p>Marquez maps {@code dataSource} into its {@code sources} table and requires a non-null
   * {@code connection_url}. URIs are therefore always present when {@code dataSource} is emitted,
   * percent-encoded, and never use a shared generic name like {@code STAGING} (that collapses many
   * synthetic inputs onto one source row and 500s when the URL cannot be parsed).
   */
  public static void attachLocationFacets(ObjectNode dataset, DatasetLocation location) {
    if (dataset == null || location == null || !location.hasStructuredFields()) {
      return;
    }
    ObjectNode facets =
        dataset.has("facets") && dataset.get("facets").isObject()
            ? (ObjectNode) dataset.get("facets")
            : MAPPER.createObjectNode();

    String datasetName = dataset.path("name").asText("");
    String safeUri = resolveSafeDataSourceUri(location, datasetName);
    String dsName = resolveDataSourceName(location, datasetName);

    // Only emit standard dataSource when we have a non-blank URI — Marquez NOT NULL on
    // sources.connection_url.
    if (!Utils.isEmpty(safeUri) && !Utils.isEmpty(dsName)) {
      ObjectNode dataSource = MAPPER.createObjectNode();
      dataSource.put("_producer", OpenLineageConstants.PRODUCER);
      dataSource.put("_schemaURL", OpenLineageConstants.DATA_SOURCE_FACET_URL);
      dataSource.put("name", dsName);
      dataSource.put("uri", safeUri);
      facets.set("dataSource", dataSource);
    }

    ObjectNode hop = MAPPER.createObjectNode();
    hop.put("_producer", OpenLineageConstants.PRODUCER);
    hop.put("_schemaURL", HOP_LOCATION_SCHEMA_URL);
    if (location.getKind() != null) {
      hop.put("kind", location.getKind().name());
    }
    putIfPresent(hop, "connectionName", location.getConnectionName());
    putIfPresent(hop, "schemaName", location.getSchemaName());
    putIfPresent(hop, "tableName", location.getTableName());
    putIfPresent(hop, "folder", location.getFolder());
    putIfPresent(hop, "includeFileMask", location.getIncludeFileMask());
    putIfPresent(hop, "excludeFileMask", location.getExcludeFileMask());
    if (location.getIncludeSubfolders() != null) {
      hop.put("includeSubfolders", location.getIncludeSubfolders());
    }
    putIfPresent(hop, "catalogUri", location.getCatalogUri());
    putIfPresent(hop, "warehouse", location.getWarehouse());
    putIfPresent(hop, "icebergNamespace", location.getIcebergNamespace());
    putIfPresent(hop, "icebergTableName", location.getIcebergTableName());
    putIfPresent(hop, "branch", location.getBranch());
    putIfPresent(hop, "snapshotId", location.getSnapshotId());
    putIfPresent(hop, "uri", safeUri != null ? safeUri : location.getUri());
    putIfPresent(hop, "catalogKey", location.getCatalogKey());
    putIfPresent(hop, "catalogConnection", location.getCatalogConnection());
    facets.set("hop_location", hop);
    // Publish facets before symlink merge so attachSymlink sees dataSource/hop_location.
    dataset.set("facets", facets);

    // Optional symlink for qualified DB names (portable alternate identifier).
    if (location.getKind() == DatasetLocationKind.DATABASE
        && !Utils.isEmpty(location.getTableName())) {
      String qualified =
          !Utils.isEmpty(location.getSchemaName())
              ? location.getSchemaName() + "." + location.getTableName()
              : location.getTableName();
      // Symlink schema.table when the dataset identity is the bare table (or a role alias).
      if (!datasetName.equals(qualified)) {
        attachSymlink(
            dataset,
            !Utils.isEmpty(location.getConnectionName()) ? location.getConnectionName() : dsName,
            qualified,
            "TABLE");
      }
    }
  }

  /**
   * Adds (or merges) a symlink identifier on a dataset. Used for schema.table aliases and for
   * dimension role aliases ({@code d_shipping_date} → {@code d_date}).
   */
  public static void attachSymlink(
      ObjectNode dataset, String identifierNamespace, String identifierName, String type) {
    if (dataset == null || Utils.isEmpty(identifierName)) {
      return;
    }
    ObjectNode facets =
        dataset.has("facets") && dataset.get("facets").isObject()
            ? (ObjectNode) dataset.get("facets")
            : MAPPER.createObjectNode();
    ObjectNode symlinks =
        facets.has("symlinks") && facets.get("symlinks").isObject()
            ? (ObjectNode) facets.get("symlinks")
            : MAPPER.createObjectNode();
    symlinks.put("_producer", OpenLineageConstants.PRODUCER);
    symlinks.put(
        "_schemaURL", "https://openlineage.io/spec/facets/1-0-0/SymlinksDatasetFacet.json");
    ArrayNode identifiers =
        symlinks.has("identifiers") && symlinks.get("identifiers").isArray()
            ? (ArrayNode) symlinks.get("identifiers")
            : MAPPER.createArrayNode();
    String ns = !Utils.isEmpty(identifierNamespace) ? identifierNamespace : "default";
    // Dedupe
    for (int i = 0; i < identifiers.size(); i++) {
      if (ns.equals(identifiers.get(i).path("namespace").asText())
          && identifierName.equals(identifiers.get(i).path("name").asText())) {
        facets.set("symlinks", symlinks);
        dataset.set("facets", facets);
        return;
      }
    }
    ObjectNode id = MAPPER.createObjectNode();
    id.put("namespace", ns);
    id.put("name", identifierName);
    id.put("type", !Utils.isEmpty(type) ? type : "TABLE");
    identifiers.add(id);
    symlinks.set("identifiers", identifiers);
    facets.set("symlinks", symlinks);
    dataset.set("facets", facets);
  }

  /**
   * Marquez source name must be unique and stable; avoid the generic label {@code STAGING} which
   * collides across many synthetic inputs.
   */
  static String resolveDataSourceName(DatasetLocation location, String datasetName) {
    if (location.getKind() == DatasetLocationKind.STAGING
        || "STAGING".equalsIgnoreCase(location.getDataSourceName())) {
      String label =
          !Utils.isEmpty(location.getTableName())
              ? location.getTableName()
              : !Utils.isEmpty(datasetName) ? datasetName : "staging";
      return "staging:" + label;
    }
    if (!Utils.isEmpty(location.getDataSourceName())) {
      return location.getDataSourceName();
    }
    if (!Utils.isEmpty(location.getConnectionName())) {
      return location.getConnectionName();
    }
    if (location.getKind() != null) {
      return location.getKind().name();
    }
    return !Utils.isEmpty(datasetName) ? datasetName : "unknown";
  }

  /** Always returns a non-blank, space-safe URI, or null if none can be built. */
  static String resolveSafeDataSourceUri(DatasetLocation location, String datasetName) {
    String raw = location.getUri();
    if (Utils.isEmpty(raw) && !Utils.isEmpty(location.getConnectionName())) {
      raw = "hop://connection/" + location.getConnectionName();
    }
    if (Utils.isEmpty(raw) && !Utils.isEmpty(location.getFolder())) {
      raw = toFileUri(location.getFolder(), location.getIncludeFileMask());
    }
    if (Utils.isEmpty(raw) && location.getKind() == DatasetLocationKind.STAGING) {
      String label =
          !Utils.isEmpty(location.getTableName())
              ? location.getTableName()
              : !Utils.isEmpty(datasetName) ? datasetName : "unknown";
      raw = "hop://staging/" + label;
    }
    if (Utils.isEmpty(raw)) {
      return null;
    }
    return encodeUriForMarquez(raw);
  }

  /**
   * Percent-encode characters that make Marquez drop {@code dataSource.uri} into a null {@code
   * sources.connection_url} (which then 500s on NOT NULL). Observed failures: spaces and unresolved
   * Hop variables ({@code ${PROJECT_HOME}/...}).
   */
  static String encodeUriForMarquez(String uri) {
    if (Utils.isEmpty(uri)) {
      return uri;
    }
    String cleaned = uri.replace("\r", "").replace("\n", "");
    cleaned = cleaned.replace(" ", "%20");
    cleaned = cleaned.replace("$", "%24");
    cleaned = cleaned.replace("{", "%7B");
    cleaned = cleaned.replace("}", "%7D");
    return cleaned;
  }

  private static void putIfPresent(ObjectNode node, String field, String value) {
    if (!Utils.isEmpty(value)) {
      node.put(field, value);
    }
  }

  static String toFileUri(String folder, String includeMask) {
    String base = folder == null ? "" : folder.replace('\\', '/');
    if (!base.isEmpty()
        && !base.startsWith("file:")
        && !base.startsWith("s3:")
        && !base.startsWith("s3a:")) {
      if (base.startsWith("/")) {
        base = "file://" + base;
      } else {
        base = "file:///" + base;
      }
    }
    if (!Utils.isEmpty(includeMask)) {
      if (!base.endsWith("/")) {
        base = base + "/";
      }
      return base + includeMask;
    }
    return base;
  }

  /** Strip userinfo and common credential query params from JDBC URLs. */
  public static String stripCredentials(String url) {
    if (Utils.isEmpty(url)) {
      return url;
    }
    String cleaned = url;
    // user:pass@host
    cleaned = cleaned.replaceAll("(?i)(jdbc:[a-z0-9:+.-]+://)([^/@\\s]+@)", "$1");
    // user= / password= query params
    cleaned = cleaned.replaceAll("(?i)([?&])(user|username|password|pwd)=[^&]*", "$1");
    cleaned = cleaned.replace("?&", "?").replaceAll("[?&]$", "");
    return cleaned;
  }
}
