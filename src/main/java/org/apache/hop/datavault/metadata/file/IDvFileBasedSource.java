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
package org.apache.hop.datavault.metadata.file;

import org.apache.hop.datavault.metadata.IDvSource;

/** Common file location properties for CSV, Parquet, and other file-based sources. */
public interface IDvFileBasedSource extends IDvSource {

  String getFolder();

  void setFolder(String folder);

  String getIncludeFileMask();

  void setIncludeFileMask(String includeFileMask);

  String getExcludeFileMask();

  void setExcludeFileMask(String excludeFileMask);

  boolean isIncludeSubfolders();

  void setIncludeSubfolders(boolean includeSubfolders);

  String getSingleFilename();

  void setSingleFilename(String singleFilename);
}
