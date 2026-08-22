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
package org.hopper.edw.datavault.hopgui.file.executionmap;

import java.util.List;
import java.util.function.Consumer;
import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.executionmap.ExecutionMapFocusContext;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapDocument;
import org.hopper.edw.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;

/** Breadcrumb navigation for execution map drill-down. */
public class ExecutionMapBreadcrumbBar extends Composite {

  private static final Class<?> PKG = HopGuiExecutionMapGraph.class;

  private final Consumer<String> navigateListener;
  private final Runnable navigateRootListener;

  public ExecutionMapBreadcrumbBar(
      Composite parent, Consumer<String> navigateListener, Runnable navigateRootListener) {
    super(parent, SWT.NONE);
    this.navigateListener = navigateListener;
    this.navigateRootListener = navigateRootListener;
    PropsUi.setLook(this);
    setLayout(new RowLayout(SWT.HORIZONTAL));
  }

  public void update(ExecutionMapDocument document, ExecutionMapFocusContext focus) {
    for (var child : getChildren()) {
      child.dispose();
    }
    if (document == null) {
      layout(true, true);
      return;
    }

    ExecutionMapFocusContext resolvedFocus = focus != null ? focus : new ExecutionMapFocusContext();
    List<ExecutionMapNode> breadcrumb = resolvedFocus.getBreadcrumb(document);
    if (breadcrumb.isEmpty()) {
      layout(true, true);
      return;
    }

    Link rootLink = new Link(this, SWT.NONE);
    PropsUi.setLook(rootLink);
    rootLink.setText(
        "<a>" + BaseMessages.getString(PKG, "HopGuiExecutionMapGraph.Breadcrumb.Root") + "</a>");
    rootLink.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            if (navigateRootListener != null) {
              navigateRootListener.run();
            }
          }
        });

    for (int i = 0; i < breadcrumb.size(); i++) {
      ExecutionMapNode node = breadcrumb.get(i);
      if (node == null) {
        continue;
      }
      Label separator = new Label(this, SWT.NONE);
      PropsUi.setLook(separator);
      separator.setText(" > ");

      boolean last = i == breadcrumb.size() - 1;
      String label = node.getName() != null ? node.getName() : node.getId();
      if (last) {
        Label current = new Label(this, SWT.NONE);
        PropsUi.setLook(current);
        current.setText(label);
      } else if (!Utils.isEmpty(node.getId())) {
        Link link = new Link(this, SWT.NONE);
        PropsUi.setLook(link);
        link.setText("<a>" + escapeLinkText(label) + "</a>");
        String nodeId = node.getId();
        link.addSelectionListener(
            new SelectionAdapter() {
              @Override
              public void widgetSelected(SelectionEvent e) {
                if (navigateListener != null) {
                  navigateListener.accept(nodeId);
                }
              }
            });
      }
    }
    layout(true, true);
  }

  private static String escapeLinkText(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
