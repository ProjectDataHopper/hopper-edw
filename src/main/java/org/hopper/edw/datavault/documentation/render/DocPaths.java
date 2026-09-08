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
package org.hopper.edw.datavault.documentation.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;

/** Site-relative paths, slugs, and stable SVG ids. */
public final class DocPaths {

  private DocPaths() {}

  public static String posix(String path) {
    if (path == null) {
      return "";
    }
    return path.replace('\\', '/');
  }

  public static String stripDot(String relative) {
    String path = posix(relative);
    while (path.startsWith("./")) {
      path = path.substring(2);
    }
    while (path.startsWith("/")) {
      path = path.substring(1);
    }
    if (".".equals(path)) {
      return "";
    }
    return path;
  }

  public static String withoutExtension(String filename) {
    String path = posix(filename);
    int slash = path.lastIndexOf('/');
    String base = slash >= 0 ? path.substring(slash + 1) : path;
    int dot = base.lastIndexOf('.');
    return dot > 0 ? base.substring(0, dot) : base;
  }

  public static String parentPath(String relative) {
    String path = stripDot(relative);
    int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }

  /** Path segments of a POSIX or mixed-separator path; empty, {@code .}, and {@code ..} skipped. */
  public static List<String> pathSegments(String path) {
    List<String> parts = new ArrayList<>();
    if (path == null || path.isEmpty()) {
      return parts;
    }
    for (String part : posix(path).split("/")) {
      if (!part.isEmpty() && !".".equals(part) && !"..".equals(part)) {
        parts.add(part);
      }
    }
    return parts;
  }

  /** Parent folders of a project-relative file (not including the filename). */
  public static List<String> folderSegments(String relativeFile) {
    return pathSegments(parentPath(relativeFile));
  }

  /**
   * Resolve {@code relative} against a project-relative directory, honoring {@code .} and {@code
   * ..}. {@code relative} must not be an absolute filesystem path.
   */
  public static String resolveRelative(String baseDir, String relative) {
    List<String> parts = new ArrayList<>();
    appendResolved(parts, baseDir);
    appendResolved(parts, relative);
    if (parts.isEmpty()) {
      return "";
    }
    return String.join("/", parts);
  }

  public static String extensionOf(String path) {
    String posix = posix(path);
    int slash = posix.lastIndexOf('/');
    String base = slash >= 0 ? posix.substring(slash + 1) : posix;
    int dot = base.lastIndexOf('.');
    return dot > 0 ? base.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
  }

  private static void appendResolved(List<String> parts, String path) {
    if (path == null || path.isEmpty()) {
      return;
    }
    for (String part : posix(path).split("/")) {
      if (part.isEmpty() || ".".equals(part)) {
        continue;
      }
      if ("..".equals(part)) {
        if (!parts.isEmpty()) {
          parts.remove(parts.size() - 1);
        }
      } else {
        parts.add(part);
      }
    }
  }

  /** Sidebar folder label for a documented table layer ({@code source}, {@code dv}, …). */
  public static String tableLayerNavLabel(String layer) {
    if (layer == null || layer.isEmpty()) {
      return "";
    }
    return switch (layer.toLowerCase(Locale.ROOT)) {
      case "source" -> DocObjectKind.SOURCE_MODEL.navLabel();
      case "dv" -> DocObjectKind.DATA_VAULT_MODEL.navLabel();
      case "bv" -> DocObjectKind.BUSINESS_VAULT_MODEL.navLabel();
      case "dm" -> DocObjectKind.DIMENSIONAL_MODEL.navLabel();
      default -> layer;
    };
  }

  public static String slug(String value) {
    if (Utils.isEmpty(value)) {
      return "unnamed";
    }
    StringBuilder out = new StringBuilder(value.length());
    boolean dash = false;
    for (int i = 0; i < value.length(); i++) {
      char c = Character.toLowerCase(value.charAt(i));
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        out.append(c);
        dash = false;
      } else if (!dash) {
        out.append('-');
        dash = true;
      }
    }
    String slug = out.toString();
    if (slug.startsWith("-")) {
      slug = slug.substring(1);
    }
    if (slug.endsWith("-")) {
      slug = slug.substring(0, slug.length() - 1);
    }
    return slug.isEmpty() ? "unnamed" : slug;
  }

  public static String stableId(String relativeSource) {
    String path = stripDot(relativeSource);
    if (path.isEmpty()) {
      return "root";
    }
    return path.replace('/', '_');
  }

  public static String htmlForSource(String relativeSource, String extension) {
    String path = stripDot(relativeSource);
    String folder = folderForExtension(extension);
    String withoutExt = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : path;
    if (withoutExt.isEmpty()) {
      withoutExt = "unnamed";
    }
    return folder + "/" + withoutExt + ".html";
  }

  public static String folderForExtension(String extension) {
    if (extension == null) {
      return "other";
    }
    return switch (extension.toLowerCase(Locale.ROOT)) {
      case "hpl" -> "pipelines";
      case "hwf" -> "workflows";
      case "hsm" -> "models/source";
      case "hdv" -> "models/data-vault";
      case "hbv" -> "models/business-vault";
      case "hdm" -> "models/dimensional";
      case "hem" -> "models/execution-maps";
      default -> "other";
    };
  }

  public static DocObjectKind kindForExtension(String extension) {
    if (extension == null) {
      return DocObjectKind.OVERVIEW;
    }
    return switch (extension.toLowerCase(Locale.ROOT)) {
      case "hpl" -> DocObjectKind.PIPELINE;
      case "hwf" -> DocObjectKind.WORKFLOW;
      case "hsm" -> DocObjectKind.SOURCE_MODEL;
      case "hdv" -> DocObjectKind.DATA_VAULT_MODEL;
      case "hbv" -> DocObjectKind.BUSINESS_VAULT_MODEL;
      case "hdm" -> DocObjectKind.DIMENSIONAL_MODEL;
      case "hem" -> DocObjectKind.EXECUTION_MAP;
      default -> DocObjectKind.OVERVIEW;
    };
  }

  public static String tableHref(String layer, String name) {
    return "tables/" + slug(layer) + "/" + slug(name) + ".html";
  }

  public static String metadataHref(String typeKey, String name) {
    return "metadata/" + slug(typeKey) + "/" + slug(name) + ".html";
  }

  public static String catalogHref(String namespace, String name) {
    return "catalog/" + slug(namespace) + "/" + slug(name) + ".html";
  }

  public static String asset(String htmlPath, String siteRelativeAsset) {
    return relativize(htmlPath, siteRelativeAsset);
  }

  public static String relativize(String fromHtmlPath, String toSitePath) {
    String from = stripDot(fromHtmlPath);
    String to = stripDot(toSitePath);
    if (from.isEmpty() || !from.contains("/")) {
      return to;
    }
    List<String> fromParts = split(parentPath(from));
    List<String> toParts = split(to);
    int i = 0;
    while (i < fromParts.size() && i < toParts.size() && fromParts.get(i).equals(toParts.get(i))) {
      i++;
    }
    StringBuilder rel = new StringBuilder();
    for (int up = i; up < fromParts.size(); up++) {
      rel.append("../");
    }
    for (int j = i; j < toParts.size(); j++) {
      if (j > i) {
        rel.append('/');
      }
      rel.append(toParts.get(j));
    }
    return rel.length() == 0 ? to : rel.toString();
  }

  public static String rootPrefix(String htmlPath) {
    int depth = folderSegments(htmlPath).size();
    return depth == 0 ? "" : "../".repeat(depth);
  }

  public static String fragmentId(String prefix, String name) {
    return prefix + "-" + slug(name);
  }

  private static List<String> split(String path) {
    return pathSegments(path);
  }
}
