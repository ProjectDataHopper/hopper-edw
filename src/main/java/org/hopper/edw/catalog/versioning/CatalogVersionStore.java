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
package org.hopper.edw.catalog.versioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.hopper.edw.catalog.impl.file.FileDataCatalog;
import org.hopper.edw.catalog.impl.file.RecordDefinitionDocument;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.hopper.edw.catalog.model.RecordDefinitionKey;

/**
 * File IO for catalog version manifests and immutable snapshot trees under a file catalog storage
 * root.
 *
 * <p>Layout (non-hidden directory name by design):
 *
 * <pre>
 *   {catalogRoot}/catalog-versions/versions.json
 *   {catalogRoot}/catalog-versions/snapshots/{snapshotId}/manifest.json
 *   {catalogRoot}/catalog-versions/snapshots/{snapshotId}/records/{namespace}/{name}.json
 * </pre>
 */
public final class CatalogVersionStore {

  public static final String VERSIONS_DIRECTORY_NAME = "catalog-versions";
  public static final String VERSIONS_MANIFEST_FILE = "versions.json";
  public static final String SNAPSHOTS_DIRECTORY_NAME = "snapshots";
  public static final String SNAPSHOT_MANIFEST_FILE = "manifest.json";
  public static final String RECORDS_DIRECTORY_NAME = "records";

  private static final Class<?> PKG = CatalogVersionStore.class;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private final Path catalogRoot;
  private final Path versionsRoot;

  public CatalogVersionStore(Path catalogRoot) throws HopException {
    if (catalogRoot == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.MissingCatalogRoot"));
    }
    this.catalogRoot = catalogRoot.toAbsolutePath().normalize();
    this.versionsRoot = this.catalogRoot.resolve(VERSIONS_DIRECTORY_NAME);
  }

  public Path getCatalogRoot() {
    return catalogRoot;
  }

  public Path getVersionsRoot() {
    return versionsRoot;
  }

  public CatalogVersionManifest readManifest() throws HopException {
    Path manifestPath = versionsRoot.resolve(VERSIONS_MANIFEST_FILE);
    if (!Files.exists(manifestPath)) {
      return new CatalogVersionManifest();
    }
    try {
      CatalogVersionManifest manifest =
          MAPPER.readValue(manifestPath.toFile(), CatalogVersionManifest.class);
      return manifest != null ? manifest : new CatalogVersionManifest();
    } catch (IOException e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "CatalogVersionStore.Error.ReadManifest", manifestPath.toString()),
          e);
    }
  }

  public void writeManifest(CatalogVersionManifest manifest) throws HopException {
    if (manifest == null) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.MissingManifest"));
    }
    Path manifestPath = versionsRoot.resolve(VERSIONS_MANIFEST_FILE);
    try {
      Files.createDirectories(versionsRoot);
      MAPPER.writeValue(manifestPath.toFile(), manifest);
    } catch (IOException e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "CatalogVersionStore.Error.WriteManifest", manifestPath.toString()),
          e);
    }
  }

  public Path snapshotRoot(String snapshotId) throws HopException {
    if (Utils.isEmpty(snapshotId)) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.MissingSnapshotId"));
    }
    return versionsRoot.resolve(SNAPSHOTS_DIRECTORY_NAME).resolve(sanitizePathSegment(snapshotId));
  }

  public void writeSnapshot(
      CatalogVersionSnapshotManifest snapshotManifest, List<RecordDefinition> definitions)
      throws HopException {
    if (snapshotManifest == null || Utils.isEmpty(snapshotManifest.getSnapshotId())) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.MissingSnapshotId"));
    }
    if (definitions == null || definitions.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.NoDefinitions"));
    }

    Path root = snapshotRoot(snapshotManifest.getSnapshotId());
    if (Files.exists(root)) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "CatalogVersionStore.Error.SnapshotExists", snapshotManifest.getSnapshotId()));
    }

    Path recordsRoot = root.resolve(RECORDS_DIRECTORY_NAME);
    List<String> relativePaths = new ArrayList<>();
    try {
      Files.createDirectories(recordsRoot);
      List<RecordDefinition> ordered = new ArrayList<>(definitions);
      ordered.sort(
          Comparator.comparing((RecordDefinition d) -> d.getKey().getNamespace())
              .thenComparing(d -> d.getKey().getName()));

      for (RecordDefinition definition : ordered) {
        if (definition == null || definition.getKey() == null) {
          continue;
        }
        definition.validate();
        Path target = toRecordPath(recordsRoot, definition.getKey());
        Files.createDirectories(target.getParent());
        RecordDefinitionDocument doc = RecordDefinitionDocument.from(definition);
        MAPPER.writeValue(target.toFile(), doc);
        relativePaths.add(recordsRoot.relativize(target).toString().replace('\\', '/'));
      }

      snapshotManifest.setRecordRelativePaths(relativePaths);
      snapshotManifest.setRecordCount(relativePaths.size());
      snapshotManifest.setContentHash(computeContentHash(recordsRoot, relativePaths));

      MAPPER.writeValue(root.resolve(SNAPSHOT_MANIFEST_FILE).toFile(), snapshotManifest);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "CatalogVersionStore.Error.WriteSnapshot", snapshotManifest.getSnapshotId()),
          e);
    }
  }

  public Optional<RecordDefinition> readDefinition(String snapshotId, RecordDefinitionKey key)
      throws HopException {
    if (key == null) {
      return Optional.empty();
    }
    key.validate();
    Path target = toRecordPath(snapshotRoot(snapshotId).resolve(RECORDS_DIRECTORY_NAME), key);
    if (!Files.exists(target)) {
      return Optional.empty();
    }
    return Optional.of(readRecord(target));
  }

  public List<RecordDefinition> readAllDefinitions(String snapshotId) throws HopException {
    Path recordsRoot = snapshotRoot(snapshotId).resolve(RECORDS_DIRECTORY_NAME);
    List<RecordDefinition> definitions = new ArrayList<>();
    if (!Files.isDirectory(recordsRoot)) {
      return definitions;
    }
    try (var paths = Files.walk(recordsRoot)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".json"))
          .sorted()
          .forEach(
              path -> {
                try {
                  definitions.add(readRecord(path));
                } catch (HopException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof HopException hopException) {
        throw hopException;
      }
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.ReadSnapshot", snapshotId), e);
    } catch (IOException e) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.ReadSnapshot", snapshotId), e);
    }
    return definitions;
  }

  public CatalogVersionSnapshotManifest readSnapshotManifest(String snapshotId)
      throws HopException {
    Path manifestPath = snapshotRoot(snapshotId).resolve(SNAPSHOT_MANIFEST_FILE);
    if (!Files.exists(manifestPath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.SnapshotNotFound", snapshotId));
    }
    try {
      return MAPPER.readValue(manifestPath.toFile(), CatalogVersionSnapshotManifest.class);
    } catch (IOException e) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.ReadSnapshot", snapshotId), e);
    }
  }

  public static String sanitizePathSegment(String segment) {
    return FileDataCatalog.sanitizePathSegment(segment);
  }

  public static String buildSnapshotId(String tag, String createdAtIso) {
    String safeTag = sanitizeTagForPath(tag);
    String safeTime =
        createdAtIso == null
            ? "unknown"
            : createdAtIso.replace(":", "").replace(".", "-").replace("+", "_");
    return safeTime + "-" + safeTag;
  }

  public static String sanitizeTagForPath(String tag) {
    if (Utils.isEmpty(tag)) {
      return "untagged";
    }
    String cleaned =
        tag.trim()
            .replaceAll("[^A-Za-z0-9._-]+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    if (cleaned.isEmpty()) {
      return "untagged";
    }
    return cleaned.toLowerCase(Locale.ROOT);
  }

  private static Path toRecordPath(Path recordsRoot, RecordDefinitionKey key) {
    Path path = recordsRoot;
    for (String segment : key.getNamespace().split("/")) {
      if (!segment.isBlank()) {
        path = path.resolve(sanitizePathSegment(segment));
      }
    }
    return path.resolve(sanitizePathSegment(key.getName()) + ".json");
  }

  private static RecordDefinition readRecord(Path path) throws HopException {
    try {
      RecordDefinitionDocument doc =
          MAPPER.readValue(path.toFile(), RecordDefinitionDocument.class);
      return doc.toRecordDefinition();
    } catch (IOException e) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.ReadRecord", path.toString()), e);
    }
  }

  private static String computeContentHash(Path recordsRoot, List<String> relativePaths)
      throws HopException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<String> ordered = new ArrayList<>(relativePaths);
      ordered.sort(String::compareTo);
      for (String relative : ordered) {
        digest.update(relative.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path file = recordsRoot.resolve(relative);
        try (InputStream in = Files.newInputStream(file)) {
          in.transferTo(
              new OutputStream() {
                @Override
                public void write(int b) {
                  digest.update((byte) b);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                  digest.update(b, off, len);
                }
              });
        }
      }
      return "sha256:" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException | IOException e) {
      throw new HopException(
          BaseMessages.getString(PKG, "CatalogVersionStore.Error.ContentHash"), e);
    }
  }
}
