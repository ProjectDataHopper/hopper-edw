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
package org.apache.hop.datavault.i18n;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Indexes per-package {@code messages_en_US.properties} bundles from the project and/or classpath.
 */
public final class I18nBundleIndex {

  private static final String MESSAGES_RELATIVE_PATH = "messages/messages_en_US.properties";

  private final Map<String, Set<String>> keysByPackage = new HashMap<>();
  private final Set<String> projectPackages = new HashSet<>();

  public static I18nBundleIndex loadProject(Path resourcesRoot) throws IOException {
    I18nBundleIndex index = new I18nBundleIndex();
    if (!Files.isDirectory(resourcesRoot)) {
      return index;
    }
    try (Stream<Path> paths = Files.walk(resourcesRoot)) {
      paths
          .filter(path -> path.endsWith(MESSAGES_RELATIVE_PATH))
          .forEach(
              path -> {
                String packageName = packageFromResourcePath(path.toString().replace('\\', '/'));
                index.loadBundle(packageName, readKeys(path), true);
              });
    }
    return index;
  }

  public static I18nBundleIndex loadClasspath(ClassLoader classLoader) throws IOException {
    I18nBundleIndex index = new I18nBundleIndex();
    String classPath = System.getProperty("java.class.path");
    if (classPath != null) {
      for (String entry : classPath.split(File.pathSeparator)) {
        Path path = Path.of(entry);
        if (Files.isDirectory(path)) {
          loadBundlesFromDirectory(index, path);
        } else if (entry.endsWith(".jar") && Files.isRegularFile(path)) {
          loadBundlesFromJar(index, path);
        }
      }
    }
    Enumeration<URL> resources = classLoader.getResources(MESSAGES_RELATIVE_PATH);
    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      String packageName = packageFromResourcePath(url.getPath());
      if (packageName == null || packageName.isBlank()) {
        continue;
      }
      index.loadBundle(packageName, readKeys(url), false);
    }
    return index;
  }

  private static void loadBundlesFromDirectory(I18nBundleIndex index, Path root)
      throws IOException {
    if (!Files.isDirectory(root)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      paths
          .filter(path -> path.toString().endsWith(MESSAGES_RELATIVE_PATH))
          .forEach(
              path -> {
                String packageName = packageFromResourcePath(path.toString().replace('\\', '/'));
                if (packageName != null && !packageName.isBlank()) {
                  index.loadBundle(packageName, readKeys(path), false);
                }
              });
    }
  }

  private static void loadBundlesFromJar(I18nBundleIndex index, Path jarPath) throws IOException {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (!entry.getName().endsWith(MESSAGES_RELATIVE_PATH)) {
          continue;
        }
        String packageName = packageFromResourcePath(entry.getName());
        if (packageName == null || packageName.isBlank()) {
          continue;
        }
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
          Properties properties = new Properties();
          properties.load(inputStream);
          index.loadBundle(packageName, new HashSet<>(properties.stringPropertyNames()), false);
        }
      }
    }
  }

  public static I18nBundleIndex merge(I18nBundleIndex project, I18nBundleIndex classpath) {
    I18nBundleIndex merged = new I18nBundleIndex();
    project.keysByPackage.forEach((pkg, keys) -> merged.loadBundle(pkg, new HashSet<>(keys), true));
    classpath.keysByPackage.forEach((pkg, keys) -> merged.loadBundle(pkg, keys, false));
    return merged;
  }

  private void loadBundle(String packageName, Set<String> keys, boolean projectPackage) {
    if (projectPackage) {
      projectPackages.add(packageName);
    }
    keysByPackage.computeIfAbsent(packageName, ignored -> new HashSet<>()).addAll(keys);
  }

  static String packageFromResourcePath(String path) {
    String normalized = path.replace('\\', '/');
    int messagesIndex = normalized.indexOf("/messages/messages_en_US.properties");
    if (messagesIndex < 0) {
      return null;
    }
    String packagePath = normalized.substring(0, messagesIndex);
    int projectResourcesIndex = packagePath.indexOf("src/main/resources/");
    if (projectResourcesIndex >= 0) {
      packagePath = packagePath.substring(projectResourcesIndex + "src/main/resources/".length());
    }
    int jarSeparator = packagePath.indexOf("!/");
    if (jarSeparator >= 0) {
      packagePath = packagePath.substring(jarSeparator + 2);
    }
    if (packagePath.startsWith("/")) {
      packagePath = packagePath.substring(1);
    }
    return packagePath.replace('/', '.');
  }

  private static Set<String> readKeys(Path messagesFile) {
    try {
      Set<String> keys = new HashSet<>();
      for (String line : Files.readAllLines(messagesFile)) {
        addKeyFromLine(line, keys);
      }
      return keys;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read " + messagesFile, e);
    }
  }

  private static Set<String> readKeys(URL url) {
    Properties properties = new Properties();
    try (InputStream inputStream = url.openStream()) {
      properties.load(inputStream);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + url, e);
    }
    return new HashSet<>(properties.stringPropertyNames());
  }

  private static void addKeyFromLine(String line, Set<String> keys) {
    String trimmed = line.trim();
    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
      return;
    }
    int separator = trimmed.indexOf('=');
    if (separator > 0) {
      keys.add(trimmed.substring(0, separator).trim());
    }
  }

  public boolean isProjectPackage(String packageName) {
    return projectPackages.contains(packageName);
  }

  public boolean contains(String packageName, String key) {
    Set<String> keys = keysByPackage.get(packageName);
    return keys != null && keys.contains(key);
  }

  public List<String> findPackagesContaining(String key) {
    return keysByPackage.entrySet().stream()
        .filter(entry -> entry.getValue().contains(key))
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  public List<String> findProjectPackagesContaining(String key) {
    return findPackagesContaining(key).stream().filter(this::isProjectPackage).toList();
  }

  public Set<String> projectPackages() {
    return Collections.unmodifiableSet(projectPackages);
  }

  public Set<String> keysForProjectPackage(String packageName) {
    if (!isProjectPackage(packageName)) {
      return Set.of();
    }
    return Collections.unmodifiableSet(keysByPackage.getOrDefault(packageName, Set.of()));
  }

  public boolean hasKeyPrefixInProjectPackage(String packageName, String keyPrefix) {
    Set<String> keys = keysByPackage.get(packageName);
    if (keys == null) {
      return false;
    }
    String prefix = keyPrefix.endsWith(".") ? keyPrefix : keyPrefix + ".";
    return keys.stream().anyMatch(key -> key.startsWith(prefix));
  }
}
