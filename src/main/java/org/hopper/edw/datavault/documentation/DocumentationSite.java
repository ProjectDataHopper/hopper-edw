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
package org.hopper.edw.datavault.documentation;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;
import org.hopper.edw.datavault.documentation.model.SearchEntry;
import org.hopper.edw.datavault.documentation.model.TableDoc;

/** Mutable state for one documentation generation run. */
@Getter
@Setter
public class DocumentationSite {

  private ProjectDocumentationOptions options;
  private ProjectDocumentationResult result;
  private IVariables variables;
  private IHopMetadataProvider metadataProvider;
  private ILogChannel log;
  private FileObject sourceRoot;
  private FileObject targetRoot;
  private String projectName;
  private Date generatedAt = new Date();
  private String generatorVersion = "";

  private final Map<DocObjectKind, List<NavItem>> nav = new EnumMap<>(DocObjectKind.class);
  private final List<SearchEntry> searchEntries = new ArrayList<>();
  private final List<TableDoc> tables = new ArrayList<>();
  private final Map<String, String> pageBySource = new LinkedHashMap<>();
  private final Map<String, String> metadataHrefByKey = new LinkedHashMap<>();
  private final Map<String, List<NavItem>> usedBy = new LinkedHashMap<>();
  private final List<PendingPage> pendingPages = new ArrayList<>();

  public void addNav(NavItem item) {
    if (item == null || item.kind() == null) {
      return;
    }
    nav.computeIfAbsent(item.kind(), k -> new ArrayList<>()).add(item);
  }

  public void addSearch(SearchEntry entry) {
    if (entry != null) {
      searchEntries.add(entry);
    }
  }

  public void addTable(TableDoc table) {
    if (table != null) {
      tables.add(table);
    }
  }

  public void rememberPage(String relativeSource, String htmlPath) {
    if (relativeSource != null && htmlPath != null) {
      pageBySource.put(relativeSource.replace('\\', '/'), htmlPath);
    }
  }

  public void rememberMetadata(String typeKey, String name, String href) {
    if (typeKey != null && name != null && href != null) {
      metadataHrefByKey.put(typeKey + ":" + name, href);
    }
  }

  public void enqueuePage(
      String htmlPath, String title, String badge, String bodyHtml, List<String> extraHead) {
    if (htmlPath == null || htmlPath.isEmpty()) {
      return;
    }
    pendingPages.add(
        new PendingPage(
            htmlPath,
            title,
            badge,
            bodyHtml == null ? "" : bodyHtml,
            extraHead == null || extraHead.isEmpty() ? List.of() : List.copyOf(extraHead)));
  }

  public void addUsedBy(String metadataKey, NavItem item) {
    if (metadataKey == null || item == null) {
      return;
    }
    usedBy.computeIfAbsent(metadataKey, k -> new ArrayList<>()).add(item);
  }

  public void warn(String message) {
    if (result != null) {
      result.addWarning(message);
    }
    if (log != null) {
      log.logBasic(message);
    }
  }

  public void error(String message, Throwable t) {
    if (result != null) {
      result.setErrors(result.getErrors() + 1);
      result.addWarning(message);
    }
    if (log != null) {
      if (t != null) {
        log.logError(message, t);
      } else {
        log.logError(message);
      }
    }
  }

  /** Body and metadata for one HTML page, rendered after the nav tree is complete. */
  public record PendingPage(
      String htmlPath, String title, String badge, String bodyHtml, List<String> extraHead) {}
}
