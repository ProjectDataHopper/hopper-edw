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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPluginType;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.EnvUtil;
import org.apache.hop.core.util.Utils;

/** Resolves plugin-shipped HTML documentation under {@code docs/}. */
public final class EdwDocsSupport {

  public static final String DOCS_INDEX_RELATIVE = "docs/index.html";

  private static final Pattern PAGE_NAME =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*\\.(html|md)");
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
      if (location == null || !"file".equalsIgnoreCase(location.getProtocol())) {
        return;
      }
      Path path = Paths.get(location.toURI());
      if (Files.isRegularFile(path)) {
        path = path.getParent();
      }
      if (path != null) {
        candidates.add(path.resolve(relativePath));
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
      IPlugin plugin = registry.getPlugin(GuiPluginType.class, pluginClass);
      if (plugin == null || plugin.getPluginDirectory() == null) {
        return;
      }
      URI uri = plugin.getPluginDirectory().toURI();
      candidates.add(Paths.get(uri).resolve(relativePath));
    } catch (Exception ignored) {
      // Try the next strategy.
    }
  }

  private static void addPluginFolderCandidates(List<Path> candidates, String relativePath) {
    String folders =
        Const.NVL(
            EnvUtil.getSystemProperty(Const.HOP_PLUGIN_BASE_FOLDERS),
            Const.DEFAULT_PLUGIN_BASE_FOLDERS);
    if (Utils.isEmpty(folders)) {
      return;
    }
    for (String folder : folders.split(",")) {
      String trimmed = Const.trim(folder);
      if (Utils.isEmpty(trimmed)) {
        continue;
      }
      candidates.add(Paths.get(trimmed, "misc", "hopper-edw").resolve(relativePath));
    }
  }
}
