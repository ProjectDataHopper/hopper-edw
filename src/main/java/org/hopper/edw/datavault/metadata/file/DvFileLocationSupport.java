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
package org.hopper.edw.datavault.metadata.file;

import org.apache.hop.core.util.Utils;
import org.hopper.edw.catalog.model.PhysicalFileRef;

/** Shared helpers for file-based Data Vault source locations. */
public final class DvFileLocationSupport {

  private DvFileLocationSupport() {}

  public static String toIncludeFileMask(String baseName) {
    return baseName.replace(".", "\\.");
  }

  public static void applyPhysicalFile(
      IDvFileBasedSource fileSource, PhysicalFileRef physicalFile) {
    if (physicalFile == null || fileSource == null) {
      return;
    }
    fileSource.setFolder(physicalFile.getFolder());
    fileSource.setIncludeFileMask(physicalFile.getIncludeFileMask());
    fileSource.setExcludeFileMask(physicalFile.getExcludeFileMask());
    fileSource.setIncludeSubfolders(physicalFile.isIncludeSubfolders());
  }

  public static PhysicalFileRef toPhysicalFileRef(IDvFileBasedSource fileSource) {
    if (fileSource == null) {
      return null;
    }
    PhysicalFileRef ref = new PhysicalFileRef();
    ref.setFolder(fileSource.getFolder());
    ref.setIncludeFileMask(fileSource.getIncludeFileMask());
    ref.setExcludeFileMask(fileSource.getExcludeFileMask());
    ref.setIncludeSubfolders(fileSource.isIncludeSubfolders());
    ref.setRequired(true);
    return ref;
  }

  public static String buildSuggestedSourceName(String filePath) {
    if (Utils.isEmpty(filePath)) {
      return "";
    }
    String normalized = filePath.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
    int dot = fileName.lastIndexOf('.');
    String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
    return baseName.replaceAll("[^A-Za-z0-9_-]+", "-");
  }
}
