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
    FileObject[] children = folder.getChildren();
    if (children == null) {
      return;
    }
    for (FileObject child : children) {
      if (child.isHidden()) {
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
      String relative = sourceRoot.getName().getRelativeName(child.getName());
      files.add(
          new ScannedFile(child, relative.replace('\\', '/'), extension.toLowerCase(Locale.ROOT)));
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
      String relToTarget = sourceRoot.getName().getRelativeName(targetRoot.getName());
      String relFolder = sourceRoot.getName().getRelativeName(folder.getName());
      if (relFolder != null
          && relToTarget != null
          && !".".equals(relToTarget)
          && (relFolder.equals(relToTarget) || relFolder.startsWith(relToTarget + "/"))) {
        return true;
      }
    }
    return false;
  }

  private static boolean sameFile(FileObject a, FileObject b) {
    if (a == null || b == null) {
      return false;
    }
    try {
      return a.getName().getURI().equals(b.getName().getURI())
          || HopVfs.getFilename(a).equals(HopVfs.getFilename(b));
    } catch (Exception e) {
      return false;
    }
  }
}
