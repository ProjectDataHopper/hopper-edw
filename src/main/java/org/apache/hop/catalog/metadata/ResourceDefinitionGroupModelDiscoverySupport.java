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
package org.apache.hop.catalog.metadata;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;

/**
 * Discovers DV/BV/DM model files under {@code ${PROJECT_HOME}}.
 *
 * <p>Results are cached briefly (per project home + extension) so the Add models dialog stays
 * responsive on large projects. Cache entries expire after {@link #CACHE_TTL_MS} or when {@link
 * #invalidateCache()} is called.
 */
public final class ResourceDefinitionGroupModelDiscoverySupport {

  private static final String VARIABLE_PROJECT_HOME = "PROJECT_HOME";

  /** How long a discovery result is reused without re-walking the project tree. */
  static final long CACHE_TTL_MS = 60_000L;

  /**
   * Directory names skipped while walking (case-sensitive on case-sensitive filesystems). Keeps
   * discovery off build output, VCS metadata, and common dependency trees.
   */
  static final Set<String> SKIP_DIRECTORY_NAMES =
      Set.of(
          ".git",
          ".svn",
          ".hg",
          ".idea",
          ".vscode",
          ".settings",
          "target",
          "build",
          "out",
          "node_modules",
          ".gradle",
          "dist",
          "tmp",
          "temp");

  private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

  private ResourceDefinitionGroupModelDiscoverySupport() {}

  /** Drops all cached discovery results (e.g. after project switch). */
  public static void invalidateCache() {
    CACHE.clear();
  }

  public static List<String> findProjectModelFiles(IVariables variables, String extension) {
    return findProjectModelFiles(variables, extension, false);
  }

  /**
   * @param forceRefresh when true, bypasses the in-memory cache and re-walks the project
   */
  public static List<String> findProjectModelFiles(
      IVariables variables, String extension, boolean forceRefresh) {
    List<String> empty = List.of();
    if (variables == null || Utils.isEmpty(extension)) {
      return new ArrayList<>();
    }
    String normalizedExtension = normalizeExtension(extension);
    String projectHome = resolveProjectHome(variables);
    if (Utils.isEmpty(projectHome)) {
      return new ArrayList<>();
    }

    String cacheKey = projectHome + "\0" + normalizedExtension;
    long now = System.currentTimeMillis();
    if (!forceRefresh) {
      CacheEntry cached = CACHE.get(cacheKey);
      if (cached != null && now - cached.cachedAtMs <= CACHE_TTL_MS) {
        return new ArrayList<>(cached.paths);
      }
    }

    List<String> files = walkProjectForModels(projectHome, normalizedExtension, variables);
    CACHE.put(cacheKey, new CacheEntry(List.copyOf(files), now));
    return files;
  }

  static List<String> walkProjectForModels(
      String projectHome, String normalizedExtension, IVariables variables) {
    List<String> files = new ArrayList<>();
    try {
      Path homePath = Path.of(projectHome).toAbsolutePath().normalize();
      if (!Files.isDirectory(homePath)) {
        return files;
      }
      Files.walkFileTree(
          homePath,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (dir.equals(homePath)) {
                return FileVisitResult.CONTINUE;
              }
              String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
              if (shouldSkipDirectory(name)) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (attrs.isRegularFile()
                  && file.getFileName()
                      .toString()
                      .toLowerCase(Locale.ROOT)
                      .endsWith(normalizedExtension)) {
                String relative = toProjectRelativePath(file.normalize().toString(), variables);
                if (!Utils.isEmpty(relative)) {
                  files.add(relative);
                }
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (Exception ignored) {
      return files;
    }
    files.sort(Comparator.naturalOrder());
    return files;
  }

  static boolean shouldSkipDirectory(String directoryName) {
    if (Utils.isEmpty(directoryName)) {
      return false;
    }
    return SKIP_DIRECTORY_NAMES.contains(directoryName);
  }

  static String normalizeExtension(String extension) {
    if (Utils.isEmpty(extension)) {
      return "";
    }
    String normalized = extension.startsWith(".") ? extension : "." + extension;
    return normalized.toLowerCase(Locale.ROOT);
  }

  static String resolveProjectHome(IVariables variables) {
    if (variables == null) {
      return null;
    }
    String projectHome = variables.resolve("${" + VARIABLE_PROJECT_HOME + "}");
    if (Utils.isEmpty(projectHome) || projectHome.contains("${")) {
      return null;
    }
    try {
      return HopVfs.normalize(projectHome);
    } catch (Exception ignored) {
      return projectHome;
    }
  }

  public static String toProjectRelativePath(String normalizedPath, IVariables variables) {
    if (variables == null || Utils.isEmpty(normalizedPath)) {
      return null;
    }
    String projectHome = resolveProjectHome(variables);
    if (Utils.isEmpty(projectHome)) {
      return null;
    }
    try {
      Path homePath = Path.of(projectHome).toAbsolutePath().normalize();
      Path selectedPath = Path.of(normalizedPath).toAbsolutePath().normalize();
      if (!selectedPath.startsWith(homePath)) {
        return null;
      }
      String relative = homePath.relativize(selectedPath).toString().replace('\\', '/');
      return "${" + VARIABLE_PROJECT_HOME + "}/" + relative;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static final class CacheEntry {
    private final List<String> paths;
    private final long cachedAtMs;

    private CacheEntry(List<String> paths, long cachedAtMs) {
      this.paths = paths;
      this.cachedAtMs = cachedAtMs;
    }
  }
}
