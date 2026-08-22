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
package org.hopper.edw.datavault.hopgui.ai;

import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.eclipse.swt.widgets.Shell;

/** Shared GUI helpers for Hop AI plugin entry points. */
public final class HopAiGuiPluginSupport {

  private static final Class<?> PKG = HopAiGuiPluginSupport.class;

  private HopAiGuiPluginSupport() {}

  /**
   * Runs an AI advisor dialog action and shows a clear message when the plugin JAR was replaced
   * while Hop GUI is still running (common during local development).
   */
  public static void runAdvisorAction(Shell parent, Runnable openDialog) {
    try {
      openDialog.run();
    } catch (LinkageError e) {
      new ErrorDialog(
          parent,
          BaseMessages.getString(PKG, "HopAiGuiPluginSupport.PluginReloadError.Title"),
          BaseMessages.getString(PKG, "HopAiGuiPluginSupport.PluginReloadError.Message"),
          e);
    } catch (Exception e) {
      new ErrorDialog(
          parent,
          BaseMessages.getString(PKG, "HopAiGuiPluginSupport.OpenError.Title"),
          BaseMessages.getString(PKG, "HopAiGuiPluginSupport.OpenError.Message"),
          e);
    }
  }
}
