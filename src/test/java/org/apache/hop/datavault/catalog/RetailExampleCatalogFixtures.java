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
package org.apache.hop.datavault.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Locates committed retail catalog fixtures for unit tests.
 *
 * <p>Runtime retail catalog data lives under gitignored {@code work/edw-catalog/}. CI and clean
 * checkouts use committed fixtures under {@code retail-example/fixtures/}.
 */
public final class RetailExampleCatalogFixtures {

  public static final String SOURCES_RELATIVE = "hop/retail-example/sources";

  private RetailExampleCatalogFixtures() {}

  public static Path projectHome() {
    return Path.of("retail-example").toAbsolutePath().normalize();
  }

  /**
   * FILE catalog storage root for general retail unit tests. Prefers a bootstrapped {@code
   * work/edw-catalog}; otherwise the committed schema-gate baseline snapshot (E2E sources only).
   */
  public static Path catalogStorageRoot() {
    Path work = projectHome().resolve("work/edw-catalog");
    if (Files.isDirectory(work.resolve(SOURCES_RELATIVE))) {
      return work;
    }
    return seedCatalogStorageRoot();
  }

  /**
   * Committed catalog root used by preview/navigation tests. Includes a minimal set of model
   * targets (hub/BV/DM) plus one E2E source so clean CI workspaces do not depend on {@code work/}.
   */
  public static Path unitCatalogStorageRoot() {
    Path unit = projectHome().resolve("fixtures/unit-catalog");
    if (!Files.isDirectory(unit.resolve(SOURCES_RELATIVE))) {
      throw new IllegalStateException("Missing unit catalog fixtures at " + unit);
    }
    return unit;
  }

  public static String unitCatalogStorageRootPath() {
    return unitCatalogStorageRoot().toString().replace('\\', '/');
  }

  /** Storage root of the committed schema-gate baseline snapshot records. */
  public static Path seedCatalogStorageRoot() {
    Path snapshots = projectHome().resolve("fixtures/schema-gate-baseline/snapshots");
    if (!Files.isDirectory(snapshots)) {
      throw new IllegalStateException("Missing retail schema-gate fixtures at " + snapshots);
    }
    try (Stream<Path> stream = Files.list(snapshots)) {
      return stream
          .filter(Files::isDirectory)
          .sorted(Comparator.comparing(Path::getFileName))
          .map(snapshot -> snapshot.resolve("records"))
          .filter(Files::isDirectory)
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "No schema-gate baseline snapshot records under " + snapshots));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Path seedSourcesDirectory() {
    Path sources = seedCatalogStorageRoot().resolve(SOURCES_RELATIVE);
    if (!Files.isDirectory(sources)) {
      throw new IllegalStateException("Missing seed retail sources at " + sources);
    }
    return sources;
  }

  /** Copy committed E2E source JSON into {@code catalogDir/hop/retail-example/sources}. */
  public static void copySeedSourcesInto(Path catalogDir) throws IOException {
    Path targetRoot = catalogDir.resolve(SOURCES_RELATIVE);
    Files.createDirectories(targetRoot);
    try (Stream<Path> paths = Files.list(seedSourcesDirectory())) {
      paths
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .forEach(
              path -> {
                try {
                  Files.copy(
                      path,
                      targetRoot.resolve(path.getFileName()),
                      StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }
  }

  public static String catalogStorageRootPath() {
    return catalogStorageRoot().toString().replace('\\', '/');
  }
}
