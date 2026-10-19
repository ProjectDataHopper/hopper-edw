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
package org.hopper.edw.datavault.hopgui.presentation;

import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.hopper.core.HColorMode;
import org.hopper.edw.datavault.hopgui.PresentationGuiPlugin;
import org.hopper.edw.datavault.hopgui.perspective.journey.panel.EdwJourneyPanelTheme;
import org.hopper.presentation.host.HPresentationHost;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.swt.HPresentationChrome;
import org.hopper.presentation.swt.HPresentationViewer;
import org.hopper.presentation.swt.HPresentationZoom;

/**
 * Report viewer used by the EDW Journey: the full presentation toolbar, light page, and fit width.
 * An embedded pane adds a Float icon on that toolbar. A window that is already floating does not.
 */
public final class EdwPresentationViewerPane extends Composite {

  private final HPresentationHost host;
  private final boolean floating;
  private final LoggingObject logging = new LoggingObject(EdwPresentationViewerPane.class);
  private HPresentationViewer viewer;
  private HGeneratedCatalog catalog;
  private String title = "";

  public EdwPresentationViewerPane(Composite parent, HPresentationHost host, boolean floating) {
    super(parent, SWT.NONE);
    this.host = host;
    this.floating = floating;
    setLayout(new FormLayout());
    Color page = new Color(getDisplay(), 0xFA, 0xFB, 0xFC);
    setBackground(page);
    addDisposeListener(e -> page.dispose());
  }

  /** A window that is already floating does not offer another Float icon. */
  public static boolean showFloatButton(boolean floating) {
    return !floating;
  }

  public void show(HGeneratedCatalog catalog, String title) throws Exception {
    this.catalog = catalog;
    this.title = title == null ? "" : title;
    if (viewer != null && !viewer.isDisposed()) {
      viewer.dispose();
      viewer = null;
    }
    if (catalog == null || catalog.getPresentation() == null) {
      return;
    }
    PresentationGuiPlugin.ensureEnvironment();
    viewer =
        new HPresentationViewer(
            this,
            logging,
            catalog.getProvider(),
            catalog.getPresentation(),
            HPresentationChrome.FULL,
            host,
            showFloatButton(floating) ? this::openFloating : null);
    viewer.forceColorMode(HColorMode.LIGHT);
    viewer.setChromeBackgroundHex(EdwJourneyPanelTheme.PAGE);
    viewer.setFitMode(HPresentationZoom.WIDTH);
    FormData data = new FormData();
    data.left = new FormAttachment(0, 0);
    data.right = new FormAttachment(100, 0);
    data.bottom = new FormAttachment(100, 0);
    data.top = new FormAttachment(0, 0);
    viewer.setLayoutData(data);
    layout(true, true);
  }

  private void openFloating() {
    if (catalog == null) {
      return;
    }
    Shell parent = getShell();
    HopGui hopGui = HopGui.getInstance();
    if (hopGui != null && hopGui.getShell() != null && !hopGui.getShell().isDisposed()) {
      parent = hopGui.getShell();
    }
    Shell shell = new Shell(parent, SWT.SHELL_TRIM);
    shell.setText(title.isBlank() ? "Presentation" : title);
    shell.setLayout(new FormLayout());
    shell.setSize(1100, 800);
    EdwPresentationViewerPane pane = new EdwPresentationViewerPane(shell, host, true);
    FormData data = new FormData();
    data.left = new FormAttachment(0, 0);
    data.top = new FormAttachment(0, 0);
    data.right = new FormAttachment(100, 0);
    data.bottom = new FormAttachment(100, 0);
    pane.setLayoutData(data);
    try {
      pane.show(catalog, title);
    } catch (Exception e) {
      shell.dispose();
      return;
    }
    shell.open();
  }
}
