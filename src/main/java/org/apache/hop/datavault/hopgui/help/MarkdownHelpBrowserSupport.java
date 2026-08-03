/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.hopgui.help;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.HopFileTypeRegistry;
import org.apache.hop.ui.hopgui.file.IHopFileType;
import org.apache.hop.ui.hopgui.perspective.explorer.config.ExplorerPerspectiveConfigSingleton;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.widgets.Shell;

/** Writes markdown help to a temporary HTML file and opens it in Hop or an external browser. */
public final class MarkdownHelpBrowserSupport {

  private static final Class<?> PKG = MarkdownHelpBrowserSupport.class;

  private MarkdownHelpBrowserSupport() {}

  public static void openInBrowser(
      Shell parentShell, String title, String markdown, String topicId) {
    if (parentShell == null || parentShell.isDisposed()) {
      return;
    }
    try {
      String html = MarkdownHtmlRenderer.toHtmlDocument(title, markdown);
      Path tempFile = writeTempHtmlFile(html, topicId);
      openHelpUrl(tempFile.toUri().toString());
    } catch (Exception ex) {
      new ErrorDialog(
          parentShell,
          BaseMessages.getString(PKG, "DialogHelp.Error.Title"),
          BaseMessages.getString(PKG, "MarkdownHelpDialog.OpenInBrowser.Error", ex.getMessage()),
          ex);
    }
  }

  static Path writeTempHtmlFile(String html, String topicId) throws HopException {
    try {
      String prefix = "hop-dv-help-";
      if (!Utils.isEmpty(topicId)) {
        String safeTopic = topicId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!safeTopic.isEmpty()) {
          prefix = prefix + safeTopic + "-";
        }
      }
      Path tempFile = Files.createTempFile(prefix, ".html");
      tempFile.toFile().deleteOnExit();
      Files.writeString(tempFile, html, StandardCharsets.UTF_8);
      return tempFile;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "MarkdownHelpDialog.OpenInBrowser.WriteFailed", e.getMessage()),
          e);
    }
  }

  private static void openHelpUrl(String url) throws HopException {
    try {
      if (ExplorerPerspectiveConfigSingleton.getConfig().isOpeningHelpFiles()) {
        openHelpInTab(url);
      } else {
        EnvironmentUtils.getInstance().openUrl(url);
      }
    } catch (Exception ex) {
      throw new HopException(
          BaseMessages.getString(
              PKG, "MarkdownHelpDialog.OpenInBrowser.OpenFailed", ex.getMessage()),
          ex);
    }
  }

  private static void openHelpInTab(String url) throws HopException {
    HopGui hopGui = HopGui.getInstance();
    if (hopGui != null) {
      IHopFileType htmlFileType = HopFileTypeRegistry.getInstance().findHopFileType("help.html");
      if (htmlFileType != null) {
        htmlFileType.openFile(hopGui, url, hopGui.getVariables());
        return;
      }
    }
    EnvironmentUtils.getInstance().openUrl(url);
  }
}
