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
package org.apache.hop.datavault.hopgui;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiPluginType;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.util.EnvUtil;
import org.apache.hop.core.util.Utils;

/** Resolves the plugin-shipped HTML documentation {@code docs/index.html}. */
public final class EdwDocsSupport {

  public static final String DOCS_INDEX_RELATIVE = "docs/index.html";

  private EdwDocsSupport() {}

  /**
   * Locate {@code plugins/misc/datavault/docs/index.html} next to the plugin jar, via the plugin
   * registry, or under {@code HOP_PLUGIN_BASE_FOLDERS}.
   *
   * @return absolute path, or {@code null} when no file exists
   */
  public static Path findIndexHtml() {
    return findIndexHtml(EdwDocsSupport.class, PluginRegistry.getInstance());
  }

  static Path findIndexHtml(Class<?> pluginClass, PluginRegistry registry) {
    for (Path candidate : candidates(pluginClass, registry)) {
      if (candidate != null && Files.isRegularFile(candidate)) {
        return candidate.toAbsolutePath().normalize();
      }
    }
    return null;
  }

  static List<Path> candidates(Class<?> pluginClass, PluginRegistry registry) {
    List<Path> candidates = new ArrayList<>();
    addCodeSourceCandidate(candidates, pluginClass);
    addRegistryCandidate(candidates, pluginClass, registry);
    addPluginFolderCandidates(candidates);
    return candidates;
  }

  private static void addCodeSourceCandidate(List<Path> candidates, Class<?> pluginClass) {
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
        candidates.add(path.resolve(DOCS_INDEX_RELATIVE));
      }
    } catch (Exception ignored) {
      // Try the next strategy.
    }
  }

  private static void addRegistryCandidate(
      List<Path> candidates, Class<?> pluginClass, PluginRegistry registry) {
    if (registry == null) {
      return;
    }
    try {
      IPlugin plugin = registry.getPlugin(GuiPluginType.class, pluginClass);
      if (plugin == null || plugin.getPluginDirectory() == null) {
        return;
      }
      URI uri = plugin.getPluginDirectory().toURI();
      candidates.add(Paths.get(uri).resolve(DOCS_INDEX_RELATIVE));
    } catch (Exception ignored) {
      // Try the next strategy.
    }
  }

  private static void addPluginFolderCandidates(List<Path> candidates) {
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
      candidates.add(Paths.get(trimmed, "misc", "datavault").resolve(DOCS_INDEX_RELATIVE));
    }
  }
}
