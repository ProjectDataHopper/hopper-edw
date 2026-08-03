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
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.catalog.DvSourceCatalogService;
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

/** Combo-based picker for Data Vault sources stored in the data catalog. */
public class DvCatalogSourceSelectionLine extends Composite {

  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final DataVaultModel model;
  private final Combo wCombo;

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
  }

  public void fillItems() throws HopException {
    wCombo.removeAll();
    for (String name : DvSourceCatalogService.listSourceNames(model, variables, metadataProvider)) {
      wCombo.add(name);
    }
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
}
