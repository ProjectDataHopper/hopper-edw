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
package org.hopper.edw.datavault.documentation.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.vfs.HopVfs;

/** Recursively finds documentable Hop / EDW files under a project folder. */
public final class ProjectFileScanner {

  private static final Set<String> EXTENSIONS =
      Set.of("hpl", "hwf", "hsm", "hdv", "hbv", "hdm", "hem");
  private static final Set<String> SKIP_FOLDERS =
      Set.of(".git", "target", "node_modules", ".svn", ".idea");

  private ProjectFileScanner() {}

  public static List<ScannedFile> scan(FileObject sourceRoot, FileObject targetRoot)
      throws HopException {
    List<ScannedFile> files = new ArrayList<>();
    try {
      walk(sourceRoot, sourceRoot, targetRoot, files);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to scan " + sourceRoot, e);
    }
    return files;
  }

  private static void walk(
      FileObject sourceRoot, FileObject folder, FileObject targetRoot, List<ScannedFile> files)
      throws Exception {
    if (folder == null || !folder.exists() || !folder.isFolder()) {
      return;
    }
    if (isSkippedFolder(sourceRoot, folder, targetRoot)) {
      return;
    }
    FileObject[] children;
    try {
      children = folder.getChildren();
    } catch (Exception e) {
      return;
    }
    if (children == null) {
      return;
    }
    for (FileObject child : children) {
      if (hidden(child)) {
        continue;
      }
      if (child.isFolder()) {
        walk(sourceRoot, child, targetRoot, files);
        continue;
      }
      String extension = child.getName().getExtension();
      if (extension == null || !EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
        continue;
      }
      String relative = posixRelative(sourceRoot, child);
      if (relative == null || relative.isEmpty()) {
        continue;
      }
      files.add(new ScannedFile(child, relative, extension.toLowerCase(Locale.ROOT)));
    }
  }

  private static boolean isSkippedFolder(
      FileObject sourceRoot, FileObject folder, FileObject targetRoot) throws Exception {
    String base = folder.getName().getBaseName();
    if (SKIP_FOLDERS.contains(base)) {
      return true;
    }
    if (targetRoot != null && sameFile(folder, targetRoot)) {
      return true;
    }
    if (targetRoot != null) {
      String relToTarget = posixRelative(sourceRoot, targetRoot);
      String relFolder = posixRelative(sourceRoot, folder);
      if (isUnderRelativeFolder(relFolder, relToTarget)) {
        return true;
      }
    }
    return false;
  }

  /**
   * True when {@code relFolder} is {@code relToTarget} or a descendant. Separators are normalized
   * and comparison is case-insensitive so Windows drive-letter and backslash paths still skip the
   * output folder inside the source tree.
   */
  static boolean isUnderRelativeFolder(String relFolder, String relToTarget) {
    String folder = stripDot(posix(relFolder));
    String target = stripDot(posix(relToTarget));
    if (folder.isEmpty() || target.isEmpty()) {
      return false;
    }
    if (folder.equalsIgnoreCase(target)) {
      return true;
    }
    int n = target.length();
    return folder.length() > n
        && (folder.charAt(n) == '/')
        && folder.regionMatches(true, 0, target, 0, n);
  }

  static String posix(String path) {
    if (path == null) {
      return "";
    }
    return path.replace('\\', '/');
  }

  private static String stripDot(String path) {
    String posix = posix(path);
    if (posix.startsWith("./")) {
      posix = posix.substring(2);
    }
    if (".".equals(posix)) {
      return "";
    }
    return posix;
  }

  private static String posixRelative(FileObject root, FileObject file) {
    if (root == null || file == null) {
      return null;
    }
    try {
      return stripLeadingSlash(stripDot(posix(root.getName().getRelativeName(file.getName()))));
    } catch (Exception e) {
      String rootPath = stripDot(posix(HopVfs.getFilename(root)));
      String filePath = stripDot(posix(HopVfs.getFilename(file)));
      if (rootPath.isEmpty() || filePath.isEmpty()) {
        return null;
      }
      if (filePath.equalsIgnoreCase(rootPath)) {
        return "";
      }
      String prefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
      if (filePath.length() > prefix.length()
          && filePath.regionMatches(true, 0, prefix, 0, prefix.length())) {
        return stripLeadingSlash(filePath.substring(prefix.length()));
      }
      return null;
    }
  }

  private static String stripLeadingSlash(String path) {
    String posix = posix(path);
    while (posix.startsWith("/")) {
      posix = posix.substring(1);
    }
    return posix;
  }

  private static boolean hidden(FileObject child) {
    try {
      return child.isHidden();
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean sameFile(FileObject a, FileObject b) {
    if (a == null || b == null) {
      return false;
    }
    try {
      if (a.getName().getURI().equals(b.getName().getURI())) {
        return true;
      }
      String left = HopVfs.getFilename(a);
      String right = HopVfs.getFilename(b);
      return left.equals(right) || (Const.isWindows() && left.equalsIgnoreCase(right));
    } catch (Exception e) {
      return false;
    }
  }
}
