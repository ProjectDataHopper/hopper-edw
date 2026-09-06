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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.hopper.edw.datavault.documentation.model.DocObjectKind;
import org.hopper.edw.datavault.documentation.model.NavItem;

/**
 * Builds the documentation sidebar: type → folders / parent objects → leaves. Groups start
 * collapsed; ancestors of the current page are opened. Children sort alphabetically,
 * case-insensitive.
 */
public final class NavTree {

  static final DocObjectKind[] NAV_ORDER = {
    DocObjectKind.PIPELINE,
    DocObjectKind.WORKFLOW,
    DocObjectKind.SOURCE_MODEL,
    DocObjectKind.DATA_VAULT_MODEL,
    DocObjectKind.BUSINESS_VAULT_MODEL,
    DocObjectKind.DIMENSIONAL_MODEL,
    DocObjectKind.EXECUTION_MAP,
    DocObjectKind.TABLE,
    DocObjectKind.METADATA,
    DocObjectKind.CATALOG
  };

  private static final Comparator<Node> NODE_ORDER =
      Comparator.comparing((Node n) -> n.label, String.CASE_INSENSITIVE_ORDER)
          .thenComparing(n -> n.href == null ? "" : n.href, String.CASE_INSENSITIVE_ORDER);

  private NavTree() {}

  public static List<Node> build(Map<DocObjectKind, List<NavItem>> nav) {
    List<Node> roots = new ArrayList<>();
    if (nav == null || nav.isEmpty()) {
      return roots;
    }
    for (DocObjectKind kind : NAV_ORDER) {
      List<NavItem> items = nav.get(kind);
      if (items == null || items.isEmpty()) {
        continue;
      }
      Node root = new Node(kind.navLabel(), null, true);
      for (NavItem item : items) {
        insert(root, item);
      }
      sort(root);
      roots.add(root);
    }
    return roots;
  }

  public static String render(Map<DocObjectKind, List<NavItem>> nav, String fromHtml) {
    return render(build(nav), fromHtml);
  }

  public static String render(List<Node> roots, String fromHtml) {
    if (roots == null || roots.isEmpty()) {
      return "";
    }
    StringBuilder html = new StringBuilder();
    for (Node root : roots) {
      appendFolder(html, fromHtml, root, true, "");
    }
    return html.toString();
  }

  /**
   * Type label, folder segments, and page title for {@code htmlPath}, or an empty list when the
   * path is not a documented object (overview / theming).
   */
  public static List<String> breadcrumb(Map<DocObjectKind, List<NavItem>> nav, String htmlPath) {
    if (nav == null || htmlPath == null || htmlPath.isEmpty()) {
      return List.of();
    }
    for (DocObjectKind kind : NAV_ORDER) {
      List<NavItem> items = nav.get(kind);
      if (items == null) {
        continue;
      }
      for (NavItem item : items) {
        if (item != null && htmlPath.equals(item.href())) {
          List<String> parts = new ArrayList<>();
          parts.add(kind.navLabel());
          if (item.treePath() != null) {
            parts.addAll(item.treePath());
          }
          if (item.title() != null && !item.title().isEmpty()) {
            parts.add(item.title());
          }
          return parts;
        }
      }
    }
    return List.of();
  }

  private static void insert(Node root, NavItem item) {
    if (item == null) {
      return;
    }
    Node current = root;
    List<String> path = item.treePath();
    if (path != null) {
      for (String segment : path) {
        current = childFolder(current, segment);
      }
    }
    String title = item.title() == null ? "" : item.title();
    current.children.add(new Node(title, item.href(), false));
  }

  private static Node childFolder(Node parent, String label) {
    for (Node child : parent.children) {
      if (child.folder && child.label.equalsIgnoreCase(label)) {
        return child;
      }
    }
    Node folder = new Node(label, null, true);
    parent.children.add(folder);
    return folder;
  }

  private static void sort(Node node) {
    node.children.sort(NODE_ORDER);
    for (Node child : node.children) {
      if (child.folder) {
        sort(child);
      }
    }
  }

  private static void appendFolder(
      StringBuilder html, String fromHtml, Node node, boolean topLevel, String parentId) {
    boolean current = node.containsHref(fromHtml);
    String id = parentId.isEmpty() ? node.label : parentId + "/" + node.label;
    html.append("<li class=\"")
        .append(topLevel ? "hop-doc-nav-group" : "hop-doc-nav-folder")
        .append("\"><details data-nav-id=\"")
        .append(HtmlEscaper.escape(id))
        .append("\"");
    if (current) {
      html.append(" class=\"is-current\" open");
    }
    html.append("><summary>").append(HtmlEscaper.escape(node.label)).append("</summary>\n<ul>\n");
    for (Node child : node.children) {
      if (child.folder) {
        appendFolder(html, fromHtml, child, false, id);
      } else {
        appendLeaf(html, fromHtml, child);
      }
    }
    html.append("</ul></details></li>\n");
  }

  private static void appendLeaf(StringBuilder html, String fromHtml, Node node) {
    boolean current = fromHtml != null && fromHtml.equals(node.href);
    String href = DocPaths.relativize(fromHtml, node.href);
    html.append("<li");
    if (current) {
      html.append(" class=\"is-current\"");
    }
    html.append("><a href=\"").append(HtmlEscaper.escape(href)).append("\"");
    if (current) {
      html.append(" aria-current=\"page\"");
    }
    html.append(">").append(HtmlEscaper.escape(node.label)).append("</a></li>\n");
  }

  @Getter
  public static final class Node {
    private final String label;
    private final String href;
    private final boolean folder;
    private final List<Node> children = new ArrayList<>();

    Node(String label, String href, boolean folder) {
      this.label = label == null ? "" : label;
      this.href = href;
      this.folder = folder;
    }

    public boolean containsHref(String htmlPath) {
      if (htmlPath == null) {
        return false;
      }
      if (htmlPath.equals(href)) {
        return true;
      }
      for (Node child : children) {
        if (child.containsHref(htmlPath)) {
          return true;
        }
      }
      return false;
    }
  }
}
