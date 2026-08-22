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
package org.hopper.edw.datavault.dbt;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.vfs.HopVfs;

/** HopVfs helpers for walking a dbt project tree. */
public final class DbtVfsSupport {

  private static final Set<String> SKIP_DIRS =
      Set.of("dbt_packages", "target", ".git", ".github", "logs", "__pycache__");

  private DbtVfsSupport() {}

  public static boolean exists(String path) {
    if (Utils.isEmpty(path)) {
      return false;
    }
    try {
      return HopVfs.getFileObject(path).exists();
    } catch (Exception e) {
      return false;
    }
  }

  public static String readUtf8(String path) throws HopException {
    try (InputStream in = HopVfs.getInputStream(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to read " + path, e);
    }
  }

  public static String filename(FileObject file) {
    return HopVfs.getFilename(file);
  }

  public static String baseName(String path) {
    if (Utils.isEmpty(path)) {
      return "";
    }
    String norm = path.replace('\\', '/');
    int slash = norm.lastIndexOf('/');
    return slash >= 0 ? norm.substring(slash + 1) : norm;
  }

  public static String parentPath(String path) {
    if (Utils.isEmpty(path)) {
      return "";
    }
    String norm = path.replace('\\', '/');
    if (norm.endsWith("/")) {
      norm = norm.substring(0, norm.length() - 1);
    }
    int slash = norm.lastIndexOf('/');
    return slash > 0 ? norm.substring(0, slash) : norm;
  }

  public static String join(String parent, String child) {
    if (Utils.isEmpty(parent)) {
      return child;
    }
    if (Utils.isEmpty(child)) {
      return parent;
    }
    String left = parent.replace('\\', '/');
    if (left.endsWith("/")) {
      left = left.substring(0, left.length() - 1);
    }
    String right = child.replace('\\', '/');
    while (right.startsWith("./")) {
      right = right.substring(2);
    }
    if (right.startsWith("/")) {
      return right;
    }
    return left + "/" + right;
  }

  public static String relativeTo(String path, String root) {
    if (Utils.isEmpty(path)) {
      return path;
    }
    String normPath = path.replace('\\', '/');
    String normRoot = root != null ? root.replace('\\', '/') : "";
    if (!Utils.isEmpty(normRoot)) {
      if (normRoot.endsWith("/")) {
        normRoot = normRoot.substring(0, normRoot.length() - 1);
      }
      if (normPath.startsWith(normRoot + "/")) {
        return normPath.substring(normRoot.length() + 1);
      }
      if (normPath.equals(normRoot)) {
        return "";
      }
    }
    return normPath;
  }

  public static List<String> listFiles(String root, String suffix) throws HopException {
    List<String> out = new ArrayList<>();
    if (Utils.isEmpty(root)) {
      return out;
    }
    try {
      FileObject folder = HopVfs.getFileObject(root);
      walk(folder, suffix.toLowerCase(Locale.ROOT), out, 0);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to list files under " + root, e);
    }
    return out;
  }

  private static void walk(FileObject node, String suffix, List<String> out, int depth)
      throws Exception {
    if (node == null || !node.exists() || depth > 24) {
      return;
    }
    if (node.getType() == FileType.FILE) {
      String name = node.getName() != null ? node.getName().getBaseName() : "";
      if (name != null && name.toLowerCase(Locale.ROOT).endsWith(suffix)) {
        out.add(filename(node));
      }
      return;
    }
    String dirName = node.getName() != null ? node.getName().getBaseName() : "";
    if (depth > 0 && SKIP_DIRS.contains(dirName)) {
      return;
    }
    FileObject[] children = node.getChildren();
    if (children == null) {
      return;
    }
    for (FileObject child : children) {
      walk(child, suffix, out, depth + 1);
    }
  }
}
