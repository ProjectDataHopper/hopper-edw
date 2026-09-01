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
package org.hopper.edw.datavault.transform.sqlexpression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.Const;
import org.apache.hop.core.Props;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.core.widget.SQLStyledTextComp;
import org.apache.hop.ui.core.widget.StyledTextComp;
import org.apache.hop.ui.core.widget.TextComposite;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.hopper.edw.datavault.expression.SqlExpressionCompiler;
import org.hopper.edw.datavault.expression.SqlExpressionDraft;
import org.hopper.edw.datavault.expression.SqlExpressionPattern;
import org.hopper.edw.datavault.expression.SqlExpressionPatterns;
import org.hopper.edw.datavault.hopgui.help.DialogHelpSupport;
import org.hopper.edw.datavault.hopgui.help.HelpTopics;

/**
 * Formula-style editor for a single SQL scalar: metadata widgets, field/pattern insertion, and a
 * large SQL widget.
 */
public class SqlExpressionEditorDialog {

  private static final Class<?> PKG = SqlExpressionMeta.class;
  private static final String DATA_FIELD = "field";
  private static final String DATA_PATTERN = "pattern";

  private final Shell parent;
  private final IVariables variables;
  private final String[] fieldNames;
  private final IRowMeta compileRowMeta;
  private final boolean showDescription;
  private final SqlExpressionDraft draft;

  private Shell shell;
  private Text wFieldName;
  private Combo wHopType;
  private Text wLength;
  private Text wPrecision;
  private Text wDescription;
  private TextComposite wSql;
  private Label wlStatus;
  private boolean ok;

  public SqlExpressionEditorDialog(
      Shell parent,
      IVariables variables,
      SqlExpressionDraft draft,
      String[] fieldNames,
      IRowMeta compileRowMeta,
      boolean showDescription) {
    this.parent = parent;
    this.variables = variables;
    this.draft = draft != null ? new SqlExpressionDraft(draft) : new SqlExpressionDraft();
    this.fieldNames = fieldNames != null ? fieldNames : new String[0];
    this.compileRowMeta = compileRowMeta;
    this.showDescription = showDescription;
  }

  /**
   * @return the edited draft, or {@code null} if the user cancelled
   */
  public SqlExpressionDraft open() {
    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.APPLICATION_MODAL);
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Title"));
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = PropsUi.getFormMargin();
    formLayout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(formLayout);

    int margin = PropsUi.getMargin();
    int middle = PropsUi.getInstance().getMiddlePct();

    Button wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString(PKG, "System.Button.OK"));
    wOk.addListener(SWT.Selection, e -> ok());
    Button wValidate = new Button(shell, SWT.PUSH);
    wValidate.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Validate.Label"));
    wValidate.addListener(SWT.Selection, e -> validateExpression());
    Button wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString(PKG, "System.Button.Cancel"));
    wCancel.addListener(SWT.Selection, e -> cancel());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.SQL_EXPRESSION);
    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wOk, wValidate, wCancel}, margin, null);

    Label wlName = new Label(shell, SWT.RIGHT);
    wlName.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.FieldName.Label"));
    PropsUi.setLook(wlName);
    wlName.setLayoutData(
        new FormDataBuilder().left().top(0, margin).right(middle, -margin).result());

    wFieldName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wFieldName);
    wFieldName.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(0, margin).right(70, 0).result());

    Label wlType = new Label(shell, SWT.RIGHT);
    wlType.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Type.Label"));
    PropsUi.setLook(wlType);
    wlType.setLayoutData(new FormDataBuilder().left(wFieldName, margin).top(0, margin).result());

    wHopType = new Combo(shell, SWT.BORDER | SWT.READ_ONLY);
    PropsUi.setLook(wHopType);
    wHopType.setItems(hopTypeComboValues());
    wHopType.setLayoutData(
        new FormDataBuilder().left(wlType, margin).top(0, margin).right(85, 0).result());

    Label wlLength = new Label(shell, SWT.RIGHT);
    wlLength.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Length.Label"));
    PropsUi.setLook(wlLength);
    wlLength.setLayoutData(
        new FormDataBuilder().left().top(wFieldName, margin).right(middle, -margin).result());

    wLength = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wLength);
    wLength.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wFieldName, margin).right(60, 0).result());

    Label wlPrecision = new Label(shell, SWT.RIGHT);
    wlPrecision.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Precision.Label"));
    PropsUi.setLook(wlPrecision);
    wlPrecision.setLayoutData(
        new FormDataBuilder().left(wLength, margin).top(wFieldName, margin).result());

    wPrecision = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wPrecision);
    wPrecision.setLayoutData(
        new FormDataBuilder()
            .left(wlPrecision, margin)
            .top(wFieldName, margin)
            .right(85, 0)
            .result());

    Label wlDescription = new Label(shell, SWT.RIGHT);
    wlDescription.setText(
        BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Description.Label"));
    PropsUi.setLook(wlDescription);
    wlDescription.setLayoutData(
        new FormDataBuilder().left().top(wLength, margin).right(middle, -margin).result());

    wDescription = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wDescription);
    wDescription.setLayoutData(
        new FormDataBuilder().left(middle, 0).top(wLength, margin).right().result());
    wDescription.setEnabled(showDescription);
    if (!showDescription) {
      wDescription.setToolTipText(
          BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Description.TransformTooltip"));
    }

    wlStatus = new Label(shell, SWT.LEFT | SWT.WRAP);
    PropsUi.setLook(wlStatus);
    wlStatus.setLayoutData(new FormDataBuilder().left().right().bottom(wOk, -margin).result());
    wlStatus.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Status.InsertHint"));

    SashForm sash = new SashForm(shell, SWT.HORIZONTAL);
    PropsUi.setLook(sash);
    sash.setLayoutData(
        new FormDataBuilder()
            .left()
            .top(wDescription, margin)
            .right()
            .bottom(wlStatus, -margin)
            .result());

    Tree tree = new Tree(sash, SWT.SINGLE | SWT.BORDER);
    PropsUi.setLook(tree);
    populateTree(tree);
    tree.addListener(SWT.Selection, e -> showHint(tree));
    tree.addListener(SWT.MouseDoubleClick, e -> insertFromTree(tree));

    Composite sqlComp = new Composite(sash, SWT.NONE);
    PropsUi.setLook(sqlComp);
    sqlComp.setLayout(new FormLayout());

    Label wlSql = new Label(sqlComp, SWT.LEFT);
    wlSql.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Sql.Label"));
    PropsUi.setLook(wlSql);
    wlSql.setLayoutData(new FormDataBuilder().left().top(0, 0).right().result());

    int sqlStyle = SWT.MULTI | SWT.LEFT | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;
    if (EnvironmentUtils.getInstance().isWeb()) {
      wSql = new StyledTextComp(variables, sqlComp, sqlStyle);
    } else {
      wSql = new SQLStyledTextComp(variables, sqlComp, sqlStyle);
    }
    wSql.addLineStyleListener(List.of(fieldNames));
    PropsUi.setLook(wSql, Props.WIDGET_STYLE_FIXED);
    wSql.setLayoutData(new FormDataBuilder().left().top(wlSql, margin).right().bottom().result());

    sash.setWeights(new int[] {20, 80});

    getData();
    BaseTransformDialog.setSize(shell, 960, 640);
    BaseDialog.defaultShellHandling(shell, e -> ok(), e -> cancel());
    return ok ? draft : null;
  }

  private void populateTree(Tree tree) {
    TreeItem fieldsRoot = new TreeItem(tree, SWT.NONE);
    fieldsRoot.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Tree.Fields"));
    for (String fieldName : fieldNames) {
      if (Utils.isEmpty(fieldName)) {
        continue;
      }
      TreeItem item = new TreeItem(fieldsRoot, SWT.NONE);
      item.setText(fieldName);
      item.setData(DATA_FIELD, fieldName);
    }
    fieldsRoot.setExpanded(true);

    TreeItem patternsRoot = new TreeItem(tree, SWT.NONE);
    patternsRoot.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Tree.Patterns"));
    Map<String, TreeItem> categories = new LinkedHashMap<>();
    for (SqlExpressionPattern pattern : SqlExpressionPatterns.all()) {
      String category = BaseMessages.getString(PKG, pattern.categoryKey());
      TreeItem catItem = categories.get(category);
      if (catItem == null) {
        catItem = new TreeItem(patternsRoot, SWT.NONE);
        catItem.setText(category);
        categories.put(category, catItem);
      }
      TreeItem item = new TreeItem(catItem, SWT.NONE);
      item.setText(BaseMessages.getString(PKG, pattern.labelKey()));
      item.setData(DATA_PATTERN, pattern);
    }
    patternsRoot.setExpanded(true);
    for (TreeItem catItem : categories.values()) {
      catItem.setExpanded(true);
    }
  }

  private void showHint(Tree tree) {
    if (tree.getSelectionCount() != 1) {
      return;
    }
    Object data = tree.getSelection()[0].getData(DATA_PATTERN);
    if (data instanceof SqlExpressionPattern pattern) {
      wlStatus.setText(BaseMessages.getString(PKG, pattern.hintKey()));
    } else if (tree.getSelection()[0].getData(DATA_FIELD) instanceof String fieldName) {
      wlStatus.setText(
          BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Status.FieldHint", fieldName));
    }
  }

  private void insertFromTree(Tree tree) {
    if (tree.getSelectionCount() != 1 || wSql == null) {
      return;
    }
    TreeItem item = tree.getSelection()[0];
    Object field = item.getData(DATA_FIELD);
    if (field instanceof String fieldName) {
      wSql.insert(SqlExpressionPatterns.quoteIdentifier(fieldName));
      return;
    }
    Object patternData = item.getData(DATA_PATTERN);
    if (patternData instanceof SqlExpressionPattern pattern) {
      wSql.insert(pattern.snippet());
    }
  }

  private void getData() {
    wFieldName.setText(Const.NVL(draft.getFieldName(), ""));
    wHopType.setText(Const.NVL(draft.getHopTypeName(), ""));
    wLength.setText(draft.getLength() >= 0 ? String.valueOf(draft.getLength()) : "");
    wPrecision.setText(draft.getPrecision() >= 0 ? String.valueOf(draft.getPrecision()) : "");
    wDescription.setText(Const.NVL(draft.getDescription(), ""));
    wSql.setText(Const.NVL(draft.getExpression(), ""));
  }

  private void saveToDraft() {
    draft.setFieldName(wFieldName.getText());
    draft.setHopTypeName(wHopType.getText());
    draft.setLength(Const.toInt(wLength.getText(), -1));
    draft.setPrecision(Const.toInt(wPrecision.getText(), -1));
    draft.setDescription(wDescription.getText());
    draft.setExpression(wSql.getText());
  }

  private void validateExpression() {
    try {
      saveToDraft();
      SqlExpressionCompiler.compile(draft.toSpec(), compileMeta(), variables);
      wlStatus.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Status.Valid"));
    } catch (Exception e) {
      wlStatus.setText(Const.NVL(e.getMessage(), e.getClass().getSimpleName()));
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Validate.Error.Title"),
          e.getMessage(),
          e);
    }
  }

  private IRowMeta compileMeta() {
    if (compileRowMeta != null && !compileRowMeta.isEmpty()) {
      return compileRowMeta;
    }
    RowMeta rowMeta = new RowMeta();
    for (String fieldName : fieldNames) {
      if (!Utils.isEmpty(fieldName)) {
        rowMeta.addValueMeta(new ValueMetaString(fieldName));
      }
    }
    if (rowMeta.isEmpty()) {
      rowMeta.addValueMeta(new ValueMetaString("dummy"));
    }
    return rowMeta;
  }

  private void ok() {
    saveToDraft();
    if (Utils.isEmpty(draft.getFieldName()) || Utils.isEmpty(draft.getExpression())) {
      wlStatus.setText(BaseMessages.getString(PKG, "SqlExpressionEditorDialog.Status.Missing"));
      return;
    }
    ok = true;
    dispose();
  }

  private void cancel() {
    ok = false;
    dispose();
  }

  private void dispose() {
    if (shell != null && !shell.isDisposed()) {
      PropsUi.getInstance().setSessionScreen(new WindowProperty(shell));
      shell.dispose();
    }
  }

  static String[] hopTypeComboValues() {
    String[] names = ValueMetaFactory.getValueMetaNames();
    String[] values = new String[names.length + 1];
    values[0] = "";
    System.arraycopy(names, 0, values, 1, names.length);
    return values;
  }
}
