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
package org.apache.hop.datavault.transform.sourcemodelsql;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.hopgui.ModelGeneratedArtifactOpenSupport;
import org.apache.hop.datavault.hopgui.dialog.ShowRowsDialog;
import org.apache.hop.datavault.virtualization.sql.SourceModelSqlPlan;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.widget.SQLStyledTextComp;
import org.apache.hop.ui.core.widget.StyledTextComp;
import org.apache.hop.ui.core.widget.TextComposite;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Dialog for {@link SourceModelSqlMeta}. */
public class SourceModelSqlDialog extends BaseTransformDialog {

  private static final Class<?> PKG = SourceModelSqlMeta.class;

  private final SourceModelSqlMeta input;

  private TextVar wSourceModelFilename;
  private TextComposite wSql;
  private Text wRowLimit;

  public SourceModelSqlDialog(
      Shell parent,
      IVariables variables,
      SourceModelSqlMeta transformMeta,
      PipelineMeta pipelineMeta) {
    super(parent, variables, transformMeta, pipelineMeta);
    input = transformMeta;
  }

  @Override
  public String open() {
    createShell(BaseMessages.getString(PKG, "SourceModelSqlDialog.Shell.Title"));

    buildButtonBar().ok(e -> ok()).preview(e -> preview()).cancel(e -> cancel()).build();

    // Source model file
    Label wlModel = new Label(shell, SWT.RIGHT);
    wlModel.setText(BaseMessages.getString(PKG, "SourceModelSqlDialog.SourceModel.Label"));
    PropsUi.setLook(wlModel);
    FormData fdlModel = new FormData();
    fdlModel.left = new FormAttachment(0, 0);
    fdlModel.right = new FormAttachment(middle, -margin);
    fdlModel.top = new FormAttachment(wSpacer, margin);
    wlModel.setLayoutData(fdlModel);

    Button wBrowse = new Button(shell, SWT.PUSH);
    wBrowse.setText(BaseMessages.getString(PKG, "System.Button.Browse"));
    PropsUi.setLook(wBrowse);
    FormData fdBrowse = new FormData();
    fdBrowse.right = new FormAttachment(100, 0);
    fdBrowse.top = new FormAttachment(wSpacer, margin);
    wBrowse.setLayoutData(fdBrowse);
    wBrowse.addListener(SWT.Selection, e -> browseSourceModel());

    wSourceModelFilename = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSourceModelFilename);
    FormData fdModel = new FormData();
    fdModel.left = new FormAttachment(middle, 0);
    fdModel.top = new FormAttachment(wSpacer, margin);
    fdModel.right = new FormAttachment(wBrowse, -margin);
    wSourceModelFilename.setLayoutData(fdModel);

    // Row limit
    Label wlLimit = new Label(shell, SWT.RIGHT);
    wlLimit.setText(BaseMessages.getString(PKG, "SourceModelSqlDialog.RowLimit.Label"));
    PropsUi.setLook(wlLimit);
    FormData fdlLimit = new FormData();
    fdlLimit.left = new FormAttachment(0, 0);
    fdlLimit.right = new FormAttachment(middle, -margin);
    fdlLimit.top = new FormAttachment(wSourceModelFilename, margin);
    wlLimit.setLayoutData(fdlLimit);

    wRowLimit = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wRowLimit);
    FormData fdLimit = new FormData();
    fdLimit.left = new FormAttachment(middle, 0);
    fdLimit.top = new FormAttachment(wSourceModelFilename, margin);
    fdLimit.right = new FormAttachment(middle, 120);
    wRowLimit.setLayoutData(fdLimit);

    // Free SQL actions
    Button wExplain = new Button(shell, SWT.PUSH);
    wExplain.setText(BaseMessages.getString(PKG, "SourceModelSqlDialog.Explain.Button"));
    PropsUi.setLook(wExplain);
    FormData fdExplain = new FormData();
    fdExplain.left = new FormAttachment(0, 0);
    fdExplain.top = new FormAttachment(wRowLimit, margin);
    wExplain.setLayoutData(fdExplain);
    wExplain.addListener(SWT.Selection, e -> explain());

    Button wViewPipeline = new Button(shell, SWT.PUSH);
    wViewPipeline.setText(BaseMessages.getString(PKG, "SourceModelSqlDialog.ViewPipeline.Button"));
    PropsUi.setLook(wViewPipeline);
    FormData fdView = new FormData();
    fdView.left = new FormAttachment(wExplain, margin);
    fdView.top = new FormAttachment(wRowLimit, margin);
    wViewPipeline.setLayoutData(fdView);
    wViewPipeline.addListener(SWT.Selection, e -> viewPipeline());

    Label wlSql = new Label(shell, SWT.LEFT);
    wlSql.setText(BaseMessages.getString(PKG, "SourceModelSqlDialog.Sql.Label"));
    PropsUi.setLook(wlSql);
    FormData fdlSql = new FormData();
    fdlSql.left = new FormAttachment(0, 0);
    fdlSql.top = new FormAttachment(wExplain, margin);
    wlSql.setLayoutData(fdlSql);

    int sqlStyle = SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;
    if (EnvironmentUtils.getInstance().isWeb()) {
      wSql = new StyledTextComp(variables, shell, sqlStyle);
    } else {
      wSql = new SQLStyledTextComp(variables, shell, sqlStyle);
    }
    wSql.addLineStyleListener(List.of());
    PropsUi.setLook(wSql, Props.WIDGET_STYLE_FIXED);
    FormData fdSql = new FormData();
    fdSql.left = new FormAttachment(0, 0);
    fdSql.top = new FormAttachment(wlSql, margin);
    fdSql.right = new FormAttachment(100, 0);
    fdSql.bottom = new FormAttachment(wOk, -margin);
    wSql.setLayoutData(fdSql);

    getData();
    focusTransformName();
    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  private void browseSourceModel() {
    BaseDialog.presentFileDialog(
        shell,
        wSourceModelFilename,
        variables,
        new String[] {"*.hsm;*.HSM", "*"},
        new String[] {
          BaseMessages.getString(PKG, "SourceModelSqlDialog.FileType.Hsm"),
          BaseMessages.getString(PKG, "System.FileType.AllFiles")
        },
        true);
  }

  private void getData() {
    wTransformName.setText(Const.NVL(transformName, ""));
    wSourceModelFilename.setText(Const.NVL(input.getSourceModelFilename(), ""));
    wSql.setText(Const.NVL(input.getSql(), ""));
    wRowLimit.setText(Const.NVL(input.getRowLimit(), "0"));
  }

  private void ok() {
    if (Utils.isEmpty(wTransformName.getText())) {
      return;
    }
    transformName = wTransformName.getText();
    input.setSourceModelFilename(wSourceModelFilename.getText());
    input.setSql(wSql.getText());
    input.setRowLimit(wRowLimit.getText());
    input.setChanged();
    dispose();
  }

  private void cancel() {
    transformName = null;
    dispose();
  }

  private void preview() {
    try {
      int limit = SourceModelSqlSupport.parseRowLimit(wRowLimit.getText(), variables);
      if (limit <= 0) {
        limit = 100;
      }
      List<RowMetaAndData> rows =
          SourceModelSqlSupport.execute(
              wSourceModelFilename.getText(), wSql.getText(), variables, metadataProvider, limit);
      if (rows.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText(BaseMessages.getString(PKG, "SourceModelSqlDialog.Preview.Empty.Title"));
        box.setMessage(BaseMessages.getString(PKG, "SourceModelSqlDialog.Preview.Empty.Message"));
        box.open();
        return;
      }
      List<Object[]> data = new ArrayList<>();
      for (RowMetaAndData row : rows) {
        data.add(row.getData());
      }
      new ShowRowsDialog(
              shell,
              variables,
              BaseMessages.getString(PKG, "SourceModelSqlDialog.Preview.Title"),
              BaseMessages.getString(PKG, "SourceModelSqlDialog.Preview.Message"),
              rows.get(0).getRowMeta(),
              data)
          .open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SourceModelSqlDialog.Preview.Error.Title"),
          BaseMessages.getString(PKG, "SourceModelSqlDialog.Preview.Error.Message"),
          e);
    }
  }

  private void explain() {
    try {
      SourceModelSqlPlan plan = planFromDialog(0);
      MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
      box.setText(BaseMessages.getString(PKG, "SourceModelSqlDialog.Explain.Title"));
      box.setMessage(plan.explainText());
      box.open();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SourceModelSqlDialog.Explain.Error.Title"),
          BaseMessages.getString(PKG, "SourceModelSqlDialog.Explain.Error.Message"),
          e);
    }
  }

  private void viewPipeline() {
    try {
      SourceModelSqlPlan plan = planFromDialog(0);
      HopGui hopGui = HopGui.getInstance();
      if (hopGui == null) {
        MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
        box.setText(BaseMessages.getString(PKG, "SourceModelSqlDialog.ViewPipeline.NoGui.Title"));
        box.setMessage(
            BaseMessages.getString(PKG, "SourceModelSqlDialog.ViewPipeline.NoGui.Message"));
        box.open();
        return;
      }
      if (plan.pipelineMeta() != null) {
        plan.pipelineMeta()
            .setName("source-model-sql-" + Const.NVL(wTransformName.getText(), "preview"));
        // Configuration-perspective ELK settings (same as other generated DV pipelines).
        SourceModelSqlSupport.applyConfiguredElkLayout(plan.pipelineMeta());
      }
      ModelGeneratedArtifactOpenSupport.openGeneratedPipeline(
          hopGui, plan.pipelineMeta(), variables);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SourceModelSqlDialog.ViewPipeline.Error.Title"),
          BaseMessages.getString(PKG, "SourceModelSqlDialog.ViewPipeline.Error.Message"),
          e);
    }
  }

  private SourceModelSqlPlan planFromDialog(int rowLimit) throws Exception {
    return SourceModelSqlSupport.plan(
        wSourceModelFilename.getText(), wSql.getText(), variables, metadataProvider, rowLimit);
  }
}
