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
package org.hopper.edw.datavault.hopgui;

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPluginType;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.IPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.EnvUtil;
import org.apache.hop.core.util.Utils;

/** Resolves plugin-shipped HTML documentation under {@code docs/}. */
public final class EdwDocsSupport {

  public static final String DOCS_INDEX_RELATIVE = "docs/index.html";

  private static final Pattern PAGE_NAME =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*\\.(html|md)");
  private static final Pattern HOPPER_EDW_JAR =
      Pattern.compile("hopper-edw-\\d[^/\\\\]*\\.jar", Pattern.CASE_INSENSITIVE);
  private static final String HELP_DIR = "help/";

  private EdwDocsSupport() {}

  /**
   * Locate {@code plugins/misc/hopper-edw/docs/index.html} next to the plugin jar, via the plugin
   * registry, or under {@code HOP_PLUGIN_BASE_FOLDERS}.
   *
   * @return absolute path, or {@code null} when no file exists
   */
  public static Path findIndexHtml() {
    return findHtmlPage("index.html");
  }

  /**
   * Locate a page under {@code plugins/misc/hopper-edw/docs/} (for example {@code
   * source-modeler-overview.html}).
   *
   * @param pageName file name, optionally with a {@code docs/} prefix
   * @return absolute path, or {@code null} when the name is invalid or no file exists
   */
  public static Path findHtmlPage(String pageName) {
    return findHtmlPage(EdwDocsSupport.class, PluginRegistry.getInstance(), pageName);
  }

  static Path findIndexHtml(Class<?> pluginClass, PluginRegistry registry) {
    return findHtmlPage(pluginClass, registry, "index.html");
  }

  static Path findHtmlPage(Class<?> pluginClass, PluginRegistry registry, String pageName) {
    String relative = docsRelative(pageName);
    if (relative == null) {
      return null;
    }
    for (Path candidate : candidatesFor(pluginClass, registry, relative)) {
      if (candidate != null && Files.isRegularFile(candidate)) {
        return candidate.toAbsolutePath().normalize();
      }
    }
    return null;
  }

  static List<Path> candidates(Class<?> pluginClass, PluginRegistry registry) {
    return candidatesFor(pluginClass, registry, DOCS_INDEX_RELATIVE);
  }

  static String docsRelative(String pageName) {
    if (Utils.isEmpty(pageName)) {
      return DOCS_INDEX_RELATIVE;
    }
    String name = pageName.trim().replace('\\', '/');
    while (name.startsWith("./")) {
      name = name.substring(2);
    }
    if (name.startsWith("docs/")) {
      name = name.substring("docs/".length());
    }
    if (name.contains("..")) {
      return null;
    }
    String fileName = name;
    if (name.startsWith(HELP_DIR)) {
      fileName = name.substring(HELP_DIR.length());
      if (fileName.contains("/")) {
        return null;
      }
    } else if (name.contains("/")) {
      return null;
    }
    if (!fileName.contains(".")) {
      fileName = fileName + ".html";
      name = name.contains("/") ? HELP_DIR + fileName : fileName;
    }
    if (!PAGE_NAME.matcher(fileName).matches()) {
      return null;
    }
    return "docs/" + name;
  }

  static List<Path> candidatesFor(
      Class<?> pluginClass, PluginRegistry registry, String relativePath) {
    List<Path> candidates = new ArrayList<>();
    addCodeSourceCandidate(candidates, pluginClass, relativePath);
    addRegistryCandidate(candidates, pluginClass, registry, relativePath);
    addPluginFolderCandidates(candidates, relativePath);
    return candidates;
  }

  private static void addCodeSourceCandidate(
      List<Path> candidates, Class<?> pluginClass, String relativePath) {
    try {
      URL location = pluginClass.getProtectionDomain().getCodeSource().getLocation();
      Path path = pathFromLocation(location);
      if (path == null) {
        return;
      }
      if (Files.isRegularFile(path)) {
        Path fromJar = pluginFolderFromJar(path);
        if (fromJar != null) {
          addCandidate(candidates, fromJar.resolve(relativePath));
        }
        path = path.getParent();
      }
      if (path != null) {
        addCandidate(candidates, path.resolve(relativePath));
      }
    } catch (Exception ignored) {
      // Try the next strategy.
    }
  }

  private static void addRegistryCandidate(
      List<Path> candidates, Class<?> pluginClass, PluginRegistry registry, String relativePath) {
    if (registry == null) {
      return;
    }
    try {
      addPluginCandidate(
          candidates, registry.getPlugin(GuiPluginType.class, pluginClass), relativePath);
      for (Class<? extends IPluginType> type : registry.getPluginTypes()) {
        for (IPlugin plugin : registry.getPlugins(type)) {
          if (isHopperEdwPlugin(plugin)) {
            addPluginCandidate(candidates, plugin, relativePath);
          }
        }
      }
    } catch (Exception ignored) {
      // Try the next strategy.
    }
  }

  private static void addPluginCandidate(
      List<Path> candidates, IPlugin plugin, String relativePath) {
    if (plugin == null) {
      return;
    }
    addLibraryCandidates(candidates, plugin.getLibraries(), relativePath);
    if (plugin.getPluginDirectory() == null) {
      return;
    }
    try {
      URI uri = plugin.getPluginDirectory().toURI();
      addCandidate(candidates, Paths.get(uri).resolve(relativePath));
    } catch (Exception ignored) {
      // Try the next strategy.
    }
  }

  static void addLibraryCandidates(
      List<Path> candidates, List<String> libraries, String relativePath) {
    Path folder = pluginFolderFromLibraries(libraries);
    if (folder != null) {
      addCandidate(candidates, folder.resolve(relativePath));
    }
  }

  static Path pluginFolderFromLibraries(List<String> libraries) {
    if (libraries == null || libraries.isEmpty()) {
      return null;
    }
    Path fallback = null;
    Path fromMainJar = null;
    for (String library : libraries) {
      Path jar = libraryPath(library);
      if (jar == null
          || jar.getFileName() == null
          || !isHopperEdwPluginJar(jar.getFileName().toString())) {
        continue;
      }
      Path folder = pluginFolderFromJar(jar);
      if (folder == null) {
        continue;
      }
      Path parent = jar.getParent();
      if (parent != null
          && parent.getFileName() != null
          && "lib".equalsIgnoreCase(parent.getFileName().toString())) {
        fallback = folder;
      } else {
        fromMainJar = folder;
      }
    }
    return fromMainJar != null ? fromMainJar : fallback;
  }

  static Path pluginFolderFromJar(Path jar) {
    if (jar == null) {
      return null;
    }
    Path parent = jar.getParent();
    if (parent == null) {
      return null;
    }
    if (parent.getFileName() != null && "lib".equalsIgnoreCase(parent.getFileName().toString())) {
      return parent.getParent();
    }
    return parent;
  }

  static boolean isHopperEdwPluginJar(String fileName) {
    return !Utils.isEmpty(fileName) && HOPPER_EDW_JAR.matcher(fileName).matches();
  }

  static Path libraryPath(String library) {
    if (Utils.isEmpty(library)) {
      return null;
    }
    String raw = library.trim();
    try {
      if (raw.startsWith("file:")) {
        return Paths.get(URI.create(raw)).normalize();
      }
      String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8);
      return Paths.get(decoded).normalize();
    } catch (Exception ignored) {
      try {
        return Paths.get(raw).normalize();
      } catch (Exception ignoredAgain) {
        return null;
      }
    }
  }

  static Path pathFromLocation(URL location) {
    if (location == null) {
      return null;
    }
    try {
      String protocol = location.getProtocol();
      if ("jar".equalsIgnoreCase(protocol)) {
        String file = location.getFile();
        int bang = file.indexOf('!');
        if (bang >= 0) {
          file = file.substring(0, bang);
        }
        if (file.startsWith("file:")) {
          return Paths.get(URI.create(file)).normalize();
        }
        return Paths.get(URLDecoder.decode(file, StandardCharsets.UTF_8)).normalize();
      }
      if ("file".equalsIgnoreCase(protocol)) {
        return Paths.get(location.toURI()).normalize();
      }
    } catch (Exception ignored) {
      // Fall through.
    }
    return null;
  }

  private static boolean isHopperEdwPlugin(IPlugin plugin) {
    if (plugin == null) {
      return false;
    }
    if (pluginFolderFromLibraries(plugin.getLibraries()) != null) {
      return true;
    }
    if (plugin.getClassMap() == null) {
      return false;
    }
    for (String className : plugin.getClassMap().values()) {
      if (className != null && className.startsWith("org.hopper.edw.")) {
        return true;
      }
    }
    return false;
  }

  private static void addPluginFolderCandidates(List<Path> candidates, String relativePath) {
    String folders =
        Const.NVL(
            EnvUtil.getSystemProperty(Const.HOP_PLUGIN_BASE_FOLDERS),
            Const.DEFAULT_PLUGIN_BASE_FOLDERS);
    addHopPluginBaseFolders(candidates, folders, relativePath);
    addCatalinaPluginFolders(candidates, System.getenv("CATALINA_HOME"), relativePath);
    addCatalinaPluginFolders(candidates, System.getenv("CATALINA_BASE"), relativePath);
  }

  private static void addHopPluginBaseFolders(
      List<Path> candidates, String folders, String relativePath) {
    if (Utils.isEmpty(folders)) {
      return;
    }
    for (String folder : folders.split(",")) {
      String trimmed = Const.trim(folder);
      if (Utils.isEmpty(trimmed)) {
        continue;
      }
      Path root = Paths.get(trimmed);
      if (root.getFileName() != null && "hopper-edw".equals(root.getFileName().toString())) {
        addCandidate(candidates, root.resolve(relativePath));
      }
      addCandidate(candidates, root.resolve("misc").resolve("hopper-edw").resolve(relativePath));
      addCandidate(
          candidates,
          root.resolve("plugins").resolve("misc").resolve("hopper-edw").resolve(relativePath));
    }
  }

  private static void addCatalinaPluginFolders(
      List<Path> candidates, String catalinaHome, String relativePath) {
    if (Utils.isEmpty(catalinaHome)) {
      return;
    }
    addCandidate(
        candidates, Paths.get(catalinaHome, "plugins", "misc", "hopper-edw").resolve(relativePath));
  }

  private static void addCandidate(List<Path> candidates, Path candidate) {
    if (candidate != null && !candidates.contains(candidate)) {
      candidates.add(candidate);
    }
  }
}
