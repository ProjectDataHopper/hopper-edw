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
package org.hopper.edw.datavault.transform.identitylookup;

import org.apache.hop.core.variables.IVariables;
import org.eclipse.swt.widgets.Shell;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.gui.GuiCompositeWidgets;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;

/** Dialog for {@link IdentityLookupMeta}. Fields sit in one group above the button bar. */
public class IdentityLookupDialog extends BaseTransformDialog {

  private static final Class<?> PKG = IdentityLookupMeta.class;

  private final IdentityLookupMeta input;
  private GuiCompositeWidgets widgets;

  public IdentityLookupDialog(
      Shell parent, IVariables variables, IdentityLookupMeta transformMeta, PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "IdentityLookup.Name"));
    changed = input.hasChanged();
    buildButtonBar().ok(e -> ok()).cancel(e -> cancel()).build();
    widgets =
        GuiCompositeWidgets.addScrolledComposite(
            shell,
            variables,
            wTransformName,
            wOk,
            IdentityLookupMeta.GUI_PLUGIN_ELEMENT_PARENT_ID,
            input);
    input.setChanged(changed);
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return transformName;
  }

  private void ok() {
    widgets.getWidgetsContents(input, IdentityLookupMeta.GUI_PLUGIN_ELEMENT_PARENT_ID);
    transformName = wTransformName.getText();
    input.setChanged();
    dispose();
  }

  private void cancel() {
    input.setChanged(changed);
    transformName = null;
    dispose();
  }
}
