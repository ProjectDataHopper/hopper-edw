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
package org.hopper.edw.datavault.hopgui.file.businessvault;

import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.hopper.edw.datavault.hopgui.GuiProgressSupport;
import org.hopper.edw.datavault.hopgui.ModelGeneratedArtifactOpenSupport;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2CalculationUnitTestSupport;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2CalculationUnitTestSupport.GeneratedArtifacts;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;

/** GUI entry for generating SCD2 calculation unit-test pipelines and data sets. */
final class BvScd2CalculationUnitTestGuiSupport {

  private static final Class<?> PKG = HopGuiBusinessVaultGraph.class;

  private BvScd2CalculationUnitTestGuiSupport() {}

  static void generate(
      HopGui hopGui,
      Shell shell,
      IVariables variables,
      BusinessVaultModel bvModel,
      DataVaultModel dvModel,
      BvScd2Table scd2Table) {
    if (hopGui == null || scd2Table == null) {
      return;
    }
    if (!scd2Table.hasCalculations()) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.CalcUnitTest.NoCalc.Title"));
      box.setMessage(
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.CalcUnitTest.NoCalc.Message"));
      box.open();
      return;
    }
    try {
      GeneratedArtifacts generated =
          BvScd2CalculationUnitTestSupport.generate(
              hopGui.getMetadataProvider(), variables, bvModel, dvModel, scd2Table, true);

      boolean runCapture = false;
      if (generated.capturePipeline() != null) {
        MessageBox ask = new MessageBox(shell, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
        ask.setText(
            BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.CalcUnitTest.Capture.Title"));
        ask.setMessage(
            BaseMessages.getString(
                PKG,
                "HopGuiBusinessVaultGraph.CalcUnitTest.Capture.Message",
                String.valueOf(BvScd2CalculationUnitTestSupport.DEFAULT_SAMPLE_SIZE)));
        runCapture = ask.open() == SWT.YES;
      }

      if (runCapture) {
        GeneratedArtifacts toCapture = generated;
        GuiProgressSupport.ProgressResult<GeneratedArtifacts> result =
            GuiProgressSupport.run(
                shell,
                true,
                monitor ->
                    BvScd2CalculationUnitTestSupport.runCapture(
                        toCapture, hopGui.getMetadataProvider(), variables));
        if (result != null && result.value() != null) {
          generated = result.value();
        }
      }

      ModelGeneratedArtifactOpenSupport.openGeneratedPipeline(
          hopGui, generated.unitTestPipeline(), variables);
      if (generated.capturePipeline() != null) {
        ModelGeneratedArtifactOpenSupport.openGeneratedPipeline(
            hopGui, generated.capturePipeline(), variables);
      }

      MessageBox done = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      done.setText(BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.CalcUnitTest.Done.Title"));
      done.setMessage(formatDoneMessage(generated));
      done.open();
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.CalcUnitTest.Error.Title"),
          e.getMessage(),
          e);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.CalcUnitTest.Error.Title"),
          BaseMessages.getString(PKG, "HopGuiBusinessVaultGraph.CalcUnitTest.Error.Message"),
          e);
    }
  }

  private static String formatDoneMessage(GeneratedArtifacts generated) {
    StringBuilder message = new StringBuilder();
    message.append(
        BaseMessages.getString(
            PKG,
            "HopGuiBusinessVaultGraph.CalcUnitTest.Done.Message",
            generated.names().unitTestName(),
            Const.NVL(generated.unitTestPipelineFilename(), ""),
            generated.names().collapseDataSetName(),
            generated.names().calculatedDataSetName()));
    if (!Utils.isEmpty(generated.capturePipelineFilename())) {
      message.append(Const.CR);
      message.append(
          BaseMessages.getString(
              PKG,
              "HopGuiBusinessVaultGraph.CalcUnitTest.Done.CaptureFile",
              generated.capturePipelineFilename()));
    }
    if (generated.collapseRowsWritten() > 0) {
      message.append(Const.CR);
      message.append(
          BaseMessages.getString(
              PKG,
              "HopGuiBusinessVaultGraph.CalcUnitTest.Done.Sampled",
              String.valueOf(generated.collapseRowsWritten())));
    }
    return message.toString();
  }
}
