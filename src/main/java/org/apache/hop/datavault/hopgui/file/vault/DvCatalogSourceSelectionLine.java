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
package org.apache.hop.datavault.hopgui.file.vault;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
import org.apache.hop.datavault.hopgui.GuiBusySupport;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DataVaultSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.PropsUi;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * Combo-based picker for Data Vault sources stored in the data catalog.
 *
 * <p>Catalog source names are loaded lazily on first focus (with wait cursor) so table dialogs open
 * without listing the catalog up front. {@link #setText(String)} can show the current value without
 * loading the list.
 */
public class DvCatalogSourceSelectionLine extends Composite {

  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final DataVaultModel model;
  private final Combo wCombo;
  private boolean itemsLoaded;

  public DvCatalogSourceSelectionLine(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      Composite parent,
      int style,
      String labelText,
      String toolTip) {
    super(parent, SWT.NONE);
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.model = model;

    PropsUi props = PropsUi.getInstance();
    int middle = props.getMiddlePct();
    int margin = PropsUi.getMargin();

    setLayout(new FormLayout());

    Label label = new Label(this, SWT.RIGHT);
    PropsUi.setLook(label);
    label.setText(labelText);
    if (toolTip != null) {
      label.setToolTipText(toolTip);
    }
    FormData fdl = new FormData();
    fdl.left = new FormAttachment(0, 0);
    fdl.right = new FormAttachment(middle, -margin);
    fdl.top = new FormAttachment(0, margin);
    label.setLayoutData(fdl);

    wCombo = new Combo(this, style);
    PropsUi.setLook(wCombo);
    if (toolTip != null) {
      wCombo.setToolTipText(toolTip);
    }
    FormData fd = new FormData();
    fd.left = new FormAttachment(middle, 0);
    fd.right = new FormAttachment(100, 0);
    fd.top = new FormAttachment(label, 0, SWT.CENTER);
    wCombo.setLayoutData(fd);

    wCombo.addListener(SWT.FocusIn, e -> ensureItemsLoaded());
    wCombo.addListener(SWT.MouseDown, e -> ensureItemsLoaded());
  }

  /**
   * Eagerly loads catalog source names (wait cursor). Prefer relying on focus-time loading; call
   * this only when the full list is required immediately.
   */
  public void fillItems() throws HopException {
    String previous = wCombo.getText();
    final HopException[] error = new HopException[1];
    GuiBusySupport.showWhile(
        this,
        () -> {
          try {
            doFillItems();
          } catch (HopException e) {
            error[0] = e;
          }
        });
    if (error[0] != null) {
      throw error[0];
    }
    if (!Utils.isEmpty(previous) && !wCombo.isDisposed()) {
      wCombo.setText(previous);
    }
    itemsLoaded = true;
  }

  public String getText() {
    return wCombo.getText();
  }

  public void setText(String text) {
    wCombo.setText(text != null ? text : "");
  }

  public void addModifyListener(ModifyListener listener) {
    wCombo.addModifyListener(listener);
  }

  public DataVaultSource resolveSelectedSource() throws HopException {
    return DvSourceCatalogService.resolveSource(getText(), model, variables, metadataProvider);
  }

  private void ensureItemsLoaded() {
    if (itemsLoaded || wCombo.isDisposed()) {
      return;
    }
    String previous = wCombo.getText();
    GuiBusySupport.showWhile(
        this,
        () -> {
          try {
            doFillItems();
          } catch (HopException e) {
            // Leave combo items empty; typed value still works.
          }
        });
    if (!wCombo.isDisposed() && !Utils.isEmpty(previous)) {
      wCombo.setText(previous);
    }
    itemsLoaded = true;
  }

  private void doFillItems() throws HopException {
    wCombo.removeAll();
    for (String name : DvSourceCatalogService.listSourceNames(model, variables, metadataProvider)) {
      wCombo.add(name);
    }
  }
}
