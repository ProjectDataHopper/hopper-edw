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
package org.apache.hop.datavault.hopgui.ai;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.ai.DvAiAvailability;
import org.apache.hop.datavault.ai.DvAiProposal;
import org.apache.hop.datavault.ai.DvAiResponse;
import org.apache.hop.datavault.ai.HopAiConfig;
import org.apache.hop.datavault.ai.HopAiConfigSingleton;
import org.apache.hop.datavault.ai.dimensional.DmAiAdvisorService;
import org.apache.hop.datavault.ai.dimensional.DmAiContextBuilder;
import org.apache.hop.datavault.ai.dimensional.DmAiConversationSession;
import org.apache.hop.datavault.ai.dimensional.DmAiProposalApplier;
import org.apache.hop.datavault.ai.dimensional.DmAiProposalValidator;
import org.apache.hop.datavault.ai.dimensional.DmAiRequest;
import org.apache.hop.datavault.ai.dimensional.DmAiScenario;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.jspecify.annotations.NonNull;

/** AI advisory dialog for the dimensional modeler. */
public class DmAiAdvisorDialog {

  private static final Class<?> PKG = DmAiAdvisorDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final DimensionalModel model;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final Runnable onBeforeApply;
  private final Runnable onModelChanged;

  private final DmAiConversationSession session = new DmAiConversationSession();

  private Shell shell;
  private Combo wScenario;
  private Text wPrompt;
  private Button wIncludeChecks;
  private Button wIncludeModelXml;
  private Button wIncludeLoadRunMetrics;
  private Button wIncludeExecutionInfo;
  private Button wSend;
  private Label wlStatusMessage;
  private HopAiTranscriptPanel transcriptPanel;
  private boolean working;
  private int lastScenarioIndex = DmAiScenario.GENERAL.ordinal();

  public DmAiAdvisorDialog(
      Shell parent,
      HopGui hopGui,
      DimensionalModel model,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Runnable onBeforeApply,
      Runnable onModelChanged) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.model = model;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.onBeforeApply = onBeforeApply;
    this.onModelChanged = onModelChanged;
  }

  public void open() {
    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX);
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Title"));

    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();

    Button wNewConversation = new Button(shell, SWT.PUSH);
    wNewConversation.setText(
        BaseMessages.getString(PKG, "DmAiAdvisorDialog.NewConversation.Label"));
    wNewConversation.addListener(SWT.Selection, e -> startNewConversation());
    wSend = new Button(shell, SWT.PUSH);
    wSend.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Send.Label"));
    wSend.addListener(SWT.Selection, e -> sendMessage());
    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> dispose());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.DM_AI_ADVISOR);

    BaseTransformDialog.positionBottomButtons(
        shell, new Button[] {wSend, wNewConversation, wClose}, margin, null);

    Control previousControl = createScenarioWidgets(margin);
    previousControl = addHorizontalSeparator(previousControl, margin);
    previousControl = createInclusionWidgets(previousControl, margin);
    previousControl = addHorizontalSeparator(previousControl, margin);
    previousControl = createStatusWidgets(previousControl, margin);
    previousControl = addHorizontalSeparator(previousControl, margin);

    createPromptArea(margin);
    transcriptPanel =
        new HopAiTranscriptPanel(
            shell,
            previousControl,
            wPrompt,
            margin,
            BaseMessages.getString(PKG, "DmAiAdvisorDialog.Conversation.Label"));

    shell.addListener(SWT.Resize, e -> transcriptPanel.refreshScroll());

    BaseTransformDialog.setSize(shell, 1000, 750);
    shell.open();
    while (!shell.isDisposed()) {
      if (!parent.getDisplay().readAndDispatch()) {
        parent.getDisplay().sleep();
      }
    }
  }

  private @NonNull Label addHorizontalSeparator(Control lastControl, int margin) {
    Label wlSeparator1 = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
    FormData fdlSeparator1 =
        new FormDataBuilder().left().right().top(lastControl, 2 * margin).build();
    wlSeparator1.setLayoutData(fdlSeparator1);
    return wlSeparator1;
  }

  private Control createStatusWidgets(Control lastControl, int margin) {
    Label wlStatusLabel = new Label(shell, SWT.LEFT);
    wlStatusLabel.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Status.Label"));
    PropsUi.setLook(wlStatusLabel);
    FormData fdlStatus = new FormData();
    fdlStatus.left = new FormAttachment(0, 0);
    fdlStatus.top = new FormAttachment(lastControl, margin);
    wlStatusLabel.setLayoutData(fdlStatus);

    wlStatusMessage = new Label(shell, SWT.LEFT);
    wlStatusMessage.setText(statusMessage());
    PropsUi.setLook(wlStatusMessage);
    wlStatusMessage.setFont(GuiResource.getInstance().getFontBold());
    FormData fdlStatusMessage = new FormData();
    fdlStatusMessage.left = new FormAttachment(0, 0);
    fdlStatusMessage.right = new FormAttachment(100, 0);
    fdlStatusMessage.top = new FormAttachment(wlStatusLabel, margin);
    wlStatusMessage.setLayoutData(fdlStatusMessage);

    return wlStatusMessage;
  }

  private Control createInclusionWidgets(Control lastControl, int margin) {
    wIncludeChecks = new Button(shell, SWT.CHECK);
    wIncludeChecks.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.IncludeChecks.Label"));
    wIncludeChecks.setSelection(true);
    PropsUi.setLook(wIncludeChecks);
    FormData fdChecks = new FormData();
    fdChecks.left = new FormAttachment(0, 0);
    fdChecks.top = new FormAttachment(lastControl, margin);
    wIncludeChecks.setLayoutData(fdChecks);

    wIncludeModelXml = new Button(shell, SWT.CHECK);
    wIncludeModelXml.setText(
        BaseMessages.getString(PKG, "DmAiAdvisorDialog.IncludeModelXml.Label"));
    wIncludeModelXml.setSelection(false);
    PropsUi.setLook(wIncludeModelXml);
    FormData fdModelXml = new FormData();
    fdModelXml.left = new FormAttachment(wIncludeChecks, margin);
    fdModelXml.top = new FormAttachment(wIncludeChecks, 0, SWT.CENTER);
    wIncludeModelXml.setLayoutData(fdModelXml);

    wIncludeLoadRunMetrics = new Button(shell, SWT.CHECK);
    wIncludeLoadRunMetrics.setText(
        BaseMessages.getString(PKG, "DmAiAdvisorDialog.IncludeLoadRunMetrics.Label"));
    wIncludeLoadRunMetrics.setSelection(false);
    PropsUi.setLook(wIncludeLoadRunMetrics);
    FormData fdLoadRunMetrics = new FormData();
    fdLoadRunMetrics.left = new FormAttachment(0, 0);
    fdLoadRunMetrics.top = new FormAttachment(wIncludeChecks, margin);
    wIncludeLoadRunMetrics.setLayoutData(fdLoadRunMetrics);

    wIncludeExecutionInfo = new Button(shell, SWT.CHECK);
    wIncludeExecutionInfo.setText(
        BaseMessages.getString(PKG, "DmAiAdvisorDialog.IncludeExecutionInfo.Label"));
    wIncludeExecutionInfo.setSelection(false);
    PropsUi.setLook(wIncludeExecutionInfo);
    FormData fdExecutionInfo = new FormData();
    fdExecutionInfo.left = new FormAttachment(wIncludeLoadRunMetrics, margin);
    fdExecutionInfo.top = new FormAttachment(wIncludeChecks, margin);
    wIncludeExecutionInfo.setLayoutData(fdExecutionInfo);

    return wIncludeExecutionInfo;
  }

  private Control createScenarioWidgets(int margin) {
    Label wlScenario = new Label(shell, SWT.LEFT);
    wlScenario.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Scenario.Label"));
    PropsUi.setLook(wlScenario);
    FormData fdlScenario = new FormData();
    fdlScenario.left = new FormAttachment(0, 0);
    fdlScenario.right = new FormAttachment(100, 0);
    fdlScenario.top = new FormAttachment(0, margin);
    wlScenario.setLayoutData(fdlScenario);

    wScenario = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wScenario);
    EnumDialogSupport.populateCombo(wScenario, DmAiScenario.class);
    EnumDialogSupport.selectCombo(wScenario, DmAiScenario.GENERAL);
    lastScenarioIndex = wScenario.getSelectionIndex();
    wScenario.addListener(SWT.Selection, this::changeScenarioMessageBox);
    FormData fdScenario = new FormData();
    fdScenario.left = new FormAttachment(0, 0);
    fdScenario.top = new FormAttachment(wlScenario, margin);
    fdScenario.right = new FormAttachment(100, 0);
    wScenario.setLayoutData(fdScenario);

    return wScenario;
  }

  private void changeScenarioMessageBox(Event e) {
    if (wScenario.getSelectionIndex() != lastScenarioIndex && !session.isEmpty()) {
      MessageBox box = new MessageBox(shell, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
      box.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.ScenarioChange.Title"));
      box.setMessage(BaseMessages.getString(PKG, "DmAiAdvisorDialog.ScenarioChange.Message"));
      if (box.open() == SWT.YES) {
        startNewConversation();
        lastScenarioIndex = wScenario.getSelectionIndex();
      } else {
        wScenario.select(lastScenarioIndex);
      }
    } else {
      lastScenarioIndex = wScenario.getSelectionIndex();
    }
    syncPerformanceOptionsForScenario();
  }

  private void syncPerformanceOptionsForScenario() {
    DmAiScenario scenario =
        EnumDialogSupport.readCombo(wScenario, DmAiScenario.class, DmAiScenario.GENERAL);
    if (scenario == DmAiScenario.PERFORMANCE_TUNING) {
      wIncludeLoadRunMetrics.setSelection(true);
      wIncludeExecutionInfo.setSelection(true);
    }
  }

  private void dispose() {
    WindowProperty winProp = new WindowProperty(shell);
    PropsUi.getInstance().setSessionScreen(winProp);
    shell.dispose();
  }

  private Control createPromptArea(int margin) {
    int height = (int) (-100 * PropsUi.getNativeZoomFactor());

    Label wlPrompt = new Label(shell, SWT.RIGHT | SWT.TOP);
    wlPrompt.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Prompt.Label"));
    PropsUi.setLook(wlPrompt);
    FormData fdlPrompt = new FormData();
    fdlPrompt.left = new FormAttachment(0, 0);
    fdlPrompt.top = new FormAttachment(wSend, height);
    wlPrompt.setLayoutData(fdlPrompt);

    wPrompt = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
    PropsUi.setLook(wPrompt);
    wPrompt.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Prompt.Default"));
    FormData fdPrompt = new FormData();
    fdPrompt.left = new FormAttachment(wlPrompt, 2 * margin);
    fdPrompt.top = new FormAttachment(wSend, height);
    fdPrompt.bottom = new FormAttachment(wSend, -margin);
    fdPrompt.right = new FormAttachment(100, 0);
    wPrompt.setLayoutData(fdPrompt);
    return wPrompt;
  }

  private String statusMessage() {
    HopAiConfig config = HopAiConfigSingleton.getConfig();
    if (!config.isAiEnabled()) {
      return BaseMessages.getString(PKG, "DmAiAdvisorDialog.Status.Disabled");
    }
    if (!DvAiAvailability.isLanguageModelChatAvailable()) {
      return BaseMessages.getString(PKG, "DmAiAdvisorDialog.Status.PluginMissing");
    }
    return BaseMessages.getString(PKG, "DmAiAdvisorDialog.Status.Ready");
  }

  private void sendMessage() {
    if (working) {
      return;
    }
    String question = wPrompt.getText();
    if (Utils.isEmpty(question)) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.EmptyPrompt.Title"));
      box.setMessage(BaseMessages.getString(PKG, "DmAiAdvisorDialog.EmptyPrompt.Message"));
      box.open();
      return;
    }

    HopAiConfig config = HopAiConfigSingleton.getConfig();
    boolean followUp = !session.isEmpty();
    var requestBuilder =
        DmAiRequest.builder()
            .userPrompt(question.trim())
            .scenario(
                EnumDialogSupport.readCombo(wScenario, DmAiScenario.class, DmAiScenario.GENERAL))
            .includeCheckResults(wIncludeChecks.getSelection())
            .includeModelXml(wIncludeModelXml.getSelection())
            .includeLoadRunMetrics(wIncludeLoadRunMetrics.getSelection())
            .includeExecutionInfo(wIncludeExecutionInfo.getSelection())
            .followUp(followUp);
    for (String summary : session.consumePendingAppliedSummaries()) {
      requestBuilder.appliedChangeSummary(summary);
    }
    DmAiRequest request = requestBuilder.build();

    transcriptPanel.appendHeading(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Transcript.You"));
    transcriptPanel.appendText(question.trim(), true);
    wPrompt.setText("");

    working = true;
    wSend.setEnabled(false);
    wlStatusMessage.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Status.Working"));

    Thread worker =
        new Thread(
            () -> {
              try {
                var context = DmAiContextBuilder.build(model, metadataProvider, variables, request);
                String llmUserMessage =
                    followUp
                        ? DmAiAdvisorService.buildFollowUpUserPrompt(context)
                        : DmAiAdvisorService.buildInitialUserPrompt(context);
                DvAiResponse response =
                    DmAiAdvisorService.advise(
                        config, variables, context, session.buildLlmHistory());
                Display.getDefault()
                    .asyncExec(
                        () -> {
                          if (shell.isDisposed()) {
                            return;
                          }
                          handleAssistantResponse(question.trim(), llmUserMessage, response);
                        });
              } catch (Exception e) {
                Display.getDefault()
                    .asyncExec(
                        () -> {
                          if (shell.isDisposed()) {
                            return;
                          }
                          working = false;
                          wSend.setEnabled(true);
                          wlStatusMessage.setText(
                              BaseMessages.getString(PKG, "DmAiAdvisorDialog.Status.Error"));
                          new ErrorDialog(
                              shell,
                              BaseMessages.getString(PKG, "DmAiAdvisorDialog.Error.Title"),
                              BaseMessages.getString(PKG, "DmAiAdvisorDialog.Error.Message"),
                              e instanceof HopException ? e : new HopException(e));
                        });
              }
            });
    worker.setDaemon(true);
    worker.start();
  }

  private void handleAssistantResponse(
      String userPrompt, String llmUserMessage, DvAiResponse response) {
    session.recordTurn(userPrompt, llmUserMessage, response);
    int turnIndex = session.getTurns().size() - 1;

    transcriptPanel.appendHeading(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Transcript.Ai"));
    String advice = response.getMarkdownAdvice() != null ? response.getMarkdownAdvice() : "";
    transcriptPanel.appendText(advice, false);

    boolean hasProposals = response.getProposals() != null && !response.getProposals().isEmpty();
    if (hasProposals) {
      int count = response.getProposals().size();
      Button reviewButton =
          transcriptPanel.appendButton(
              BaseMessages.getString(PKG, "DmAiAdvisorDialog.ReviewTurn.Label", count));
      reviewButton.addListener(SWT.Selection, e -> reviewProposalsForTurn(turnIndex));
    } else if (session.getTurns().get(turnIndex).isProposalsDropped()) {
      transcriptPanel.appendSystemLine(
          BaseMessages.getString(PKG, "DmAiAdvisorDialog.ProposalsDropped.Message"));
    }

    working = false;
    wSend.setEnabled(true);
    wlStatusMessage.setText(
        hasProposals
            ? BaseMessages.getString(PKG, "DmAiAdvisorDialog.Status.WithProposals")
            : BaseMessages.getString(PKG, "DmAiAdvisorDialog.Status.Done"));
  }

  private void reviewProposalsForTurn(int turnIndex) {
    if (turnIndex < 0 || turnIndex >= session.getTurns().size()) {
      return;
    }
    List<DvAiProposal> proposals = session.getTurns().get(turnIndex).getProposals();
    if (proposals == null || proposals.isEmpty()) {
      return;
    }
    List<DmAiProposalValidator.ValidationResult> validation =
        DmAiProposalValidator.validate(model, proposals, metadataProvider, variables);
    ModelAiProposalReviewDialog reviewDialog =
        new ModelAiProposalReviewDialog(
            shell, proposals, mapValidationResults(validation), DmAiProposalApplier::preview);
    if (!reviewDialog.open()) {
      return;
    }
    List<DvAiProposal> selected = reviewDialog.getSelectedProposals();
    if (selected.isEmpty()) {
      return;
    }
    try {
      if (onBeforeApply != null) {
        onBeforeApply.run();
      }
      DmAiProposalApplier.apply(model, selected, metadataProvider, variables);
      if (onModelChanged != null) {
        onModelChanged.run();
      }
      session.recordApplied(turnIndex, selected);
      transcriptPanel.appendSystemLine(
          BaseMessages.getString(PKG, "DmAiAdvisorDialog.Transcript.Applied", selected.size()));
      wlStatusMessage.setText(BaseMessages.getString(PKG, "DmAiAdvisorDialog.Status.Applied"));
      transcriptPanel.refreshScroll();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DmAiAdvisorDialog.ApplyError.Title"),
          BaseMessages.getString(PKG, "DmAiAdvisorDialog.ApplyError.Message"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private static List<ModelAiProposalReviewDialog.ValidationResult> mapValidationResults(
      List<DmAiProposalValidator.ValidationResult> validation) {
    if (validation == null || validation.isEmpty()) {
      return List.of();
    }
    List<ModelAiProposalReviewDialog.ValidationResult> mapped = new ArrayList<>();
    for (DmAiProposalValidator.ValidationResult result : validation) {
      if (result == null) {
        continue;
      }
      ModelAiProposalReviewDialog.Status status =
          ModelAiProposalReviewDialog.Status.valueOf(result.getStatus().name());
      mapped.add(
          new ModelAiProposalReviewDialog.ValidationResult(
              result.getProposal(), status, result.getMessage()));
    }
    return mapped;
  }

  private void startNewConversation() {
    session.clear();
    lastScenarioIndex = wScenario.getSelectionIndex();
    session.setScenario(
        EnumDialogSupport.readCombo(wScenario, DmAiScenario.class, DmAiScenario.GENERAL));
    transcriptPanel.clear();
    wlStatusMessage.setText(statusMessage());
  }
}
