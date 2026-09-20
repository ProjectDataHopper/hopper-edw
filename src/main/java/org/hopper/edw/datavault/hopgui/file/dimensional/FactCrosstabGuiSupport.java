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
package org.hopper.edw.datavault.hopgui.file.dimensional;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.logging.LoggingObject;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.dialog.EnterSelectionDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.metadata.dimensional.IDmTable;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabSourceModel;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabSpec;
import org.hopper.edw.semantic.SemanticLayerSupport;
import org.hopper.edw.semantic.bind.HdmSemanticSeed;
import org.hopper.edw.semantic.model.SemanticEntity;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.model.SemanticSelection;
import org.hopper.edw.semantic.query.SemanticSelectionAdapter;
import org.hopper.presentation.simple.HGeneratedCatalog;

/** Opens the fact crosstab editor and generated presentation viewer. */
public final class FactCrosstabGuiSupport {

  private static final Class<?> PKG = HopGuiDimensionalModelGraph.class;

  public static final String ACTION_ID_CREATE_PRESENTATION = "dm-graph-create-fact-presentation";

  private FactCrosstabGuiSupport() {}

  public static boolean isFactLikeTable(IDmTable table) {
    return table instanceof IDmFactLikeTable;
  }

  public static void open(HopGui hopGui, DimensionalModel model, IDmTable table) {
    if (hopGui == null || model == null || !(table instanceof IDmFactLikeTable fact)) {
      return;
    }
    Shell shell = hopGui.getShell();
    IVariables variables = hopGui.getVariables();
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
    try {
      SemanticModel semantic = HdmSemanticSeed.seed(model, variables, metadataProvider);
      SemanticEntity factEntity = semantic.findEntity(fact.getName());
      FactCrosstabSourceModel sources =
          factEntity != null
              ? FactCrosstabSourceModel.fromSemantic(
                  semantic, factEntity, model, variables, metadataProvider)
              : FactCrosstabSourceModel.build(model, fact, variables, metadataProvider);
      FactCrosstabSpec spec = new FactCrosstabSpec();
      spec.setFactTableName(fact.getName());
      openEditor(hopGui, model, fact, spec, sources, variables, metadataProvider, semantic);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Title"),
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Open"),
          e);
    }
  }

  public static void openFromSemanticLayer(
      HopGui hopGui, SemanticModel semantic, String preferredFactName) {
    if (hopGui == null || semantic == null) {
      return;
    }
    Shell shell = hopGui.getShell();
    if (Utils.isEmpty(semantic.getDimensionalModelFilename())) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "FactCrosstabGuiSupport.NoDimensionalModel.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "FactCrosstabGuiSupport.NoDimensionalModel.Message"));
      box.open();
      return;
    }
    IVariables variables = hopGui.getVariables();
    IHopMetadataProvider metadataProvider = hopGui.getMetadataProvider();
    try {
      DimensionalModel model =
          SemanticLayerSupport.loadBoundDimensionalModel(semantic, variables, metadataProvider);
      SemanticEntity factEntity = resolveFactEntity(hopGui, semantic, preferredFactName);
      if (factEntity == null) {
        return;
      }
      FactCrosstabSourceModel sources =
          FactCrosstabSourceModel.fromSemantic(
              semantic, factEntity, model, variables, metadataProvider);
      IDmFactLikeTable fact = sources.getFact();
      FactCrosstabSpec spec = new FactCrosstabSpec();
      spec.setFactTableName(factEntity.getName());
      SemanticSelection last = lastSelectionFor(semantic, factEntity.getName());
      if (last != null) {
        spec = SemanticSelectionAdapter.toSpec(semantic, last);
      }
      openEditor(hopGui, model, fact, spec, sources, variables, metadataProvider, semantic);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Title"),
          BaseMessages.getString(PKG, "FactCrosstabEditorDialog.Error.Open"),
          e);
    }
  }

  private static SemanticEntity resolveFactEntity(
      HopGui hopGui, SemanticModel semantic, String preferredFactName) {
    List<SemanticEntity> facts = semantic.listFactEntities();
    if (facts.isEmpty()) {
      MessageBox box = new MessageBox(hopGui.getShell(), SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "FactCrosstabGuiSupport.NoFact.Title"));
      box.setMessage(BaseMessages.getString(PKG, "FactCrosstabGuiSupport.NoFact.Message"));
      box.open();
      return null;
    }
    if (!Utils.isEmpty(preferredFactName)) {
      SemanticEntity preferred = semantic.findEntity(preferredFactName);
      if (preferred != null && preferred.resolveRole().isFact()) {
        return preferred;
      }
    }
    if (facts.size() == 1) {
      return facts.get(0);
    }
    List<String> names = new ArrayList<>();
    for (SemanticEntity entity : facts) {
      names.add(entity.getName());
    }
    EnterSelectionDialog dialog =
        new EnterSelectionDialog(
            hopGui.getShell(),
            names.toArray(String[]::new),
            BaseMessages.getString(PKG, "FactCrosstabGuiSupport.SelectFact.Title"),
            BaseMessages.getString(PKG, "FactCrosstabGuiSupport.SelectFact.Message"));
    String name = dialog.open();
    if (Utils.isEmpty(name)) {
      return null;
    }
    return semantic.findEntity(name);
  }

  static void openEditor(
      HopGui hopGui,
      DimensionalModel model,
      IDmFactLikeTable fact,
      FactCrosstabSpec spec,
      FactCrosstabSourceModel sources,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    openEditor(hopGui, model, fact, spec, sources, variables, metadataProvider, null);
  }

  static void openEditor(
      HopGui hopGui,
      DimensionalModel model,
      IDmFactLikeTable fact,
      FactCrosstabSpec spec,
      FactCrosstabSourceModel sources,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      SemanticModel semanticModel) {
    FactCrosstabEditorDialog dialog =
        new FactCrosstabEditorDialog(
            hopGui.getShell(),
            model,
            fact,
            spec,
            sources,
            variables,
            metadataProvider,
            semanticModel);
    if (!dialog.open()) {
      return;
    }
    HGeneratedCatalog catalog = dialog.getCatalog();
    FactCrosstabSpec shown = dialog.getSpec();
    if (catalog == null) {
      return;
    }
    new FactCrosstabViewerShell(
            hopGui,
            catalog,
            () ->
                openEditor(
                    hopGui,
                    model,
                    fact,
                    shown.copy(),
                    sources,
                    variables,
                    metadataProvider,
                    semanticModel))
        .open();
  }

  private static SemanticSelection lastSelectionFor(SemanticModel semantic, String factName) {
    if (semantic == null
        || semantic.getSelections() == null
        || semantic.getSelections().isEmpty()) {
      return null;
    }
    for (int i = semantic.getSelections().size() - 1; i >= 0; i--) {
      SemanticSelection selection = semantic.getSelections().get(i);
      if (selection != null && factName.equals(selection.getEntityName())) {
        return selection;
      }
    }
    return semantic.getSelections().get(semantic.getSelections().size() - 1);
  }

  static LoggingObject loggingObject() {
    return new LoggingObject("fact-crosstab-viewer");
  }
}
