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
package org.hopper.edw.datavault.diagram;

import java.util.Locale;

/** Filename matching for diagram subject loaders (plain paths and VFS URIs). */
public final class EdwDiagramFiles {

  private EdwDiagramFiles() {}

  public static boolean hasExtension(String filename, String extension) {
    if (filename == null || extension == null) {
      return false;
    }
    String path = filename.trim();
    int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    path = path.toLowerCase(Locale.ROOT).replace('\\', '/');
    String ext = extension.toLowerCase(Locale.ROOT);
    if (!ext.startsWith(".")) {
      ext = "." + ext;
    }
    return path.endsWith(ext);
  }
}
