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
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;

/** Discovers DV/BV/DM model files under {@code ${PROJECT_HOME}}. */
public final class ResourceDefinitionGroupModelDiscoverySupport {

  private static final String VARIABLE_PROJECT_HOME = "PROJECT_HOME";

  private ResourceDefinitionGroupModelDiscoverySupport() {}

  public static List<String> findProjectModelFiles(IVariables variables, String extension) {
    List<String> files = new ArrayList<>();
    if (variables == null || Utils.isEmpty(extension)) {
      return files;
    }
    String normalizedExtension = extension.startsWith(".") ? extension : "." + extension;
    String projectHome = resolveProjectHome(variables);
    if (Utils.isEmpty(projectHome)) {
      return files;
    }
    try {
      Path homePath = Path.of(projectHome).toAbsolutePath().normalize();
      if (!Files.isDirectory(homePath)) {
        return files;
      }
      Files.walkFileTree(
          homePath,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (attrs.isRegularFile()
                  && file.getFileName().toString().toLowerCase().endsWith(normalizedExtension)) {
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
}
