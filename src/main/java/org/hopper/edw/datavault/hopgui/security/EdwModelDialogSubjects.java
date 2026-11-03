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
package org.hopper.edw.datavault.hopgui.security;

import org.apache.hop.core.security.IDialogEditable;
import org.apache.hop.core.security.Permission;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.security.EdwWebAuthorization;

/**
 * Dialog subject for modeler property dialogs. Hop disables OK when the session lacks {@link
 * Permission#FILE_EDIT}.
 */
public final class EdwModelDialogSubjects {

  /** Subject whose edit permission is {@link Permission#FILE_EDIT}. */
  public static final IDialogEditable FILE_EDIT = () -> Permission.FILE_EDIT;

  private EdwModelDialogSubjects() {}

  /**
   * Attach {@link #FILE_EDIT} to a dialog shell before {@code BaseDialog.defaultShellHandling}.
   *
   * @param shell dialog shell
   */
  public static void bindFileEdit(Shell shell) {
    BaseDialog.setDialogSubject(shell, FILE_EDIT);
  }

  /**
   * Leave a non-mutating inspect button (validate, show lineage) enabled when the dialog is
   * read-only.
   *
   * @param button button to keep enabled
   */
  public static void keepInspect(Button button) {
    BaseDialog.keepEnabledInReadOnly(button);
  }

  /**
   * Leave a preview button enabled only when the session may execute. A read-only role stays
   * disabled.
   *
   * @param button preview, show-differences, or preview-SQL button
   */
  public static void keepPreview(Button button) {
    if (EdwWebAuthorization.canExecute()) {
      BaseDialog.keepEnabledInReadOnly(button);
    }
  }
}
