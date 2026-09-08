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
package org.hopper.edw.datavault.hopgui.file.projectdoc;

import java.nio.charset.StandardCharsets;
import org.apache.hop.core.Const;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.Utils;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerFile;
import org.apache.hop.ui.hopgui.perspective.explorer.ExplorerPerspective;
import org.apache.hop.ui.hopgui.perspective.explorer.file.types.base.BaseExplorerFileTypeHandler;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Composite;
import org.hopper.edw.datavault.hopgui.EdwDocsWebSupport;

/**
 * Loads generated project documentation in the explorer {@link Browser} via a RAP handler URL so
 * CSS, header/sidebar chrome, and in-page links resolve. Hop's default HTML handler uses {@code
 * Browser.setText()} and has no document base.
 */
public class HopProjectDocExplorerFileTypeHandler extends BaseExplorerFileTypeHandler {

  private Browser wBrowser;

  public HopProjectDocExplorerFileTypeHandler(
      HopGui hopGui, ExplorerPerspective perspective, ExplorerFile explorerFile) {
    super(hopGui, perspective, explorerFile);
  }

  @Override
  public void renderFile(Composite composite) {
    wBrowser = new Browser(composite, SWT.NONE);
    PropsUi.setLook(wBrowser);
    FormData fdBrowser = new FormData();
    fdBrowser.left = new FormAttachment(0, 0);
    fdBrowser.right = new FormAttachment(100, 0);
    fdBrowser.top = new FormAttachment(0, 0);
    fdBrowser.bottom = new FormAttachment(100, 0);
    wBrowser.setLayoutData(fdBrowser);
    wBrowser.addProgressListener(
        new ProgressListener() {
          @Override
          public void changed(ProgressEvent event) {
            // Progress while the page loads.
          }

          @Override
          public void completed(ProgressEvent event) {
            updateTitleFromPageTitle();
          }
        });
    reload();
  }

  @Override
  public void reload() {
    if (wBrowser == null || wBrowser.isDisposed()) {
      return;
    }
    try {
      String filename = explorerFile.getFilename();
      if (HopProjectDocFileType.isHttp(filename)) {
        wBrowser.setUrl(filename);
        clearChanged();
        return;
      }
      String url = EdwDocsWebSupport.absoluteBrowserUrl(filename);
      if (!Utils.isEmpty(url)) {
        wBrowser.setUrl(url);
        clearChanged();
        return;
      }
      wBrowser.setText(Const.NVL(readTextFileContent(StandardCharsets.UTF_8), ""));
      clearChanged();
    } catch (Exception e) {
      LogChannel.UI.logError(
          "Error opening project documentation '" + explorerFile.getFilename() + "'", e);
      wBrowser.setText(
          "<html><body><h1>Error loading documentation</h1><p>"
              + Const.NVL(e.getMessage(), "Unknown error")
              + "</p></body></html>");
    }
  }

  private void updateTitleFromPageTitle() {
    if (wBrowser == null || wBrowser.isDisposed()) {
      return;
    }
    hopGui
        .getDisplay()
        .timerExec(
            500,
            () -> {
              if (wBrowser == null || wBrowser.isDisposed()) {
                return;
              }
              try {
                Object result = wBrowser.evaluate("return document.title;");
                if (result == null) {
                  return;
                }
                String pageTitle = result.toString();
                if (Utils.isEmpty(pageTitle) || "null".equals(pageTitle)) {
                  return;
                }
                String shortTitle =
                    pageTitle.length() > 30 ? pageTitle.substring(0, 27) + "..." : pageTitle;
                if (!shortTitle.equals(explorerFile.getName())) {
                  explorerFile.setName(shortTitle);
                  perspective.updateTabItem(this);
                }
              } catch (Exception e) {
                LogChannel.UI.logDebug(
                    "Could not extract project documentation page title: " + e.getMessage());
              }
            });
  }
}
