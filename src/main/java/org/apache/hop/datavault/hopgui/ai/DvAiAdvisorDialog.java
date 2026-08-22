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
package org.apache.hop.datavault.hopgui.ai;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.ai.DvAiAdvisorService;
import org.apache.hop.datavault.ai.DvAiAvailability;
import org.apache.hop.datavault.ai.DvAiContextBuilder;
import org.apache.hop.datavault.ai.DvAiConversationSession;
import org.apache.hop.datavault.ai.DvAiProposal;
import org.apache.hop.datavault.ai.DvAiProposalApplier;
import org.apache.hop.datavault.ai.DvAiProposalValidator;
import org.apache.hop.datavault.ai.DvAiRequest;
import org.apache.hop.datavault.ai.DvAiResponse;
import org.apache.hop.datavault.ai.DvAiScenario;
import org.apache.hop.datavault.ai.HopAiConfig;
import org.apache.hop.datavault.ai.HopAiConfigSingleton;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.datavault.metadata.DataVaultModel;
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

/** AI advisory dialog for the Data Vault modeler. */
public class DvAiAdvisorDialog {

  private static final Class<?> PKG = DvAiAdvisorDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final DataVaultModel model;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final Runnable onBeforeApply;
  private final Runnable onModelChanged;

  private final DvAiConversationSession session = new DvAiConversationSession();

  private Shell shell;
  private Combo wScenario;
  private Text wPrompt;
  private Button wIncludeChecks;
  private Button wIncludeCatalog;
  private Button wIncludeModelXml;
  private Button wIncludeLoadRunMetrics;
  private Button wIncludeExecutionInfo;
  private Button wChangeCatalogSources;
  private Button wSend;
  private Label wlStatusMessage;
  private HopAiTranscriptPanel transcriptPanel;
  private boolean working;
  private int lastScenarioIndex = DvAiScenario.GENERAL.ordinal();

  public DvAiAdvisorDialog(
      Shell parent,
      HopGui hopGui,
      DataVaultModel model,
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
    shell.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Title"));

    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();

    Button wNewConversation = new Button(shell, SWT.PUSH);
    wNewConversation.setText(
        BaseMessages.getString(PKG, "DvAiAdvisorDialog.NewConversation.Label"));
    wNewConversation.addListener(SWT.Selection, e -> startNewConversation());
    wSend = new Button(shell, SWT.PUSH);
    wSend.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Send.Label"));
    wSend.addListener(SWT.Selection, e -> sendMessage());
    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> dispose());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.DV_AI_ADVISOR);

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
            BaseMessages.getString(PKG, "DvAiAdvisorDialog.Conversation.Label"));

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
    wlStatusLabel.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Status.Label"));
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
    wIncludeChecks.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.IncludeChecks.Label"));
    wIncludeChecks.setSelection(true);
    PropsUi.setLook(wIncludeChecks);
    FormData fdChecks = new FormData();
    fdChecks.left = new FormAttachment(0, 0);
    fdChecks.top = new FormAttachment(lastControl, margin);
    wIncludeChecks.setLayoutData(fdChecks);

    wIncludeCatalog = new Button(shell, SWT.CHECK);
    wIncludeCatalog.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.IncludeCatalog.Label"));
    wIncludeCatalog.setSelection(true);
    PropsUi.setLook(wIncludeCatalog);
    wIncludeCatalog.addListener(SWT.Selection, e -> updateCatalogSourceButton());
    FormData fdCatalog = new FormData();
    fdCatalog.left = new FormAttachment(wIncludeChecks, margin);
    fdCatalog.top = new FormAttachment(wIncludeChecks, 0, SWT.CENTER);
    wIncludeCatalog.setLayoutData(fdCatalog);

    wIncludeModelXml = new Button(shell, SWT.CHECK);
    wIncludeModelXml.setText(
        BaseMessages.getString(PKG, "DvAiAdvisorDialog.IncludeModelXml.Label"));
    wIncludeModelXml.setSelection(false);
    PropsUi.setLook(wIncludeModelXml);
    FormData fdModelXml = new FormData();
    fdModelXml.left = new FormAttachment(wIncludeCatalog, margin);
    fdModelXml.top = new FormAttachment(wIncludeChecks, 0, SWT.CENTER);
    wIncludeModelXml.setLayoutData(fdModelXml);

    wChangeCatalogSources = new Button(shell, SWT.PUSH);
    wChangeCatalogSources.setText(
        BaseMessages.getString(PKG, "DvAiAdvisorDialog.ChangeCatalogSources.Label"));
    wChangeCatalogSources.setEnabled(false);
    PropsUi.setLook(wChangeCatalogSources);
    wChangeCatalogSources.addListener(SWT.Selection, e -> changeCatalogSources());
    FormData fdChangeCatalog = new FormData();
    fdChangeCatalog.left = new FormAttachment(wIncludeModelXml, margin);
    fdChangeCatalog.top = new FormAttachment(wIncludeChecks, 0, SWT.CENTER);
    wChangeCatalogSources.setLayoutData(fdChangeCatalog);

    wIncludeLoadRunMetrics = new Button(shell, SWT.CHECK);
    wIncludeLoadRunMetrics.setText(
        BaseMessages.getString(PKG, "DvAiAdvisorDialog.IncludeLoadRunMetrics.Label"));
    wIncludeLoadRunMetrics.setSelection(false);
    PropsUi.setLook(wIncludeLoadRunMetrics);
    FormData fdLoadRunMetrics = new FormData();
    fdLoadRunMetrics.left = new FormAttachment(0, 0);
    fdLoadRunMetrics.top = new FormAttachment(wIncludeChecks, margin);
    wIncludeLoadRunMetrics.setLayoutData(fdLoadRunMetrics);

    wIncludeExecutionInfo = new Button(shell, SWT.CHECK);
    wIncludeExecutionInfo.setText(
        BaseMessages.getString(PKG, "DvAiAdvisorDialog.IncludeExecutionInfo.Label"));
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
    wlScenario.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Scenario.Label"));
    PropsUi.setLook(wlScenario);
    FormData fdlScenario = new FormData();
    fdlScenario.left = new FormAttachment(0, 0);
    fdlScenario.right = new FormAttachment(100, 0);
    fdlScenario.top = new FormAttachment(0, margin);
    wlScenario.setLayoutData(fdlScenario);

    wScenario = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wScenario);
    EnumDialogSupport.populateCombo(wScenario, DvAiScenario.class);
    EnumDialogSupport.selectCombo(wScenario, DvAiScenario.GENERAL);
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
      box.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.ScenarioChange.Title"));
      box.setMessage(BaseMessages.getString(PKG, "DvAiAdvisorDialog.ScenarioChange.Message"));
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
    DvAiScenario scenario =
        EnumDialogSupport.readCombo(wScenario, DvAiScenario.class, DvAiScenario.GENERAL);
    if (scenario == DvAiScenario.PERFORMANCE_TUNING) {
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
    wlPrompt.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Prompt.Label"));
    PropsUi.setLook(wlPrompt);
    FormData fdlPrompt = new FormData();
    fdlPrompt.left = new FormAttachment(0, 0);
    fdlPrompt.top = new FormAttachment(wSend, height);
    wlPrompt.setLayoutData(fdlPrompt);

    wPrompt = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
    PropsUi.setLook(wPrompt);
    wPrompt.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Prompt.Default"));
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
      return BaseMessages.getString(PKG, "DvAiAdvisorDialog.Status.Disabled");
    }
    if (!DvAiAvailability.isLanguageModelChatAvailable()) {
      return BaseMessages.getString(PKG, "DvAiAdvisorDialog.Status.PluginMissing");
    }
    return BaseMessages.getString(PKG, "DvAiAdvisorDialog.Status.Ready");
  }

  private void sendMessage() {
    if (working) {
      return;
    }
    String question = wPrompt.getText();
    if (Utils.isEmpty(question)) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.EmptyPrompt.Title"));
      box.setMessage(BaseMessages.getString(PKG, "DvAiAdvisorDialog.EmptyPrompt.Message"));
      box.open();
      return;
    }

    HopAiConfig config = HopAiConfigSingleton.getConfig();
    List<String> catalogSources = List.of();
    if (wIncludeCatalog.getSelection()) {
      catalogSources =
          DvAiCatalogSourceSelector.selectSources(
              shell,
              model,
              variables,
              metadataProvider,
              onModelChanged,
              session.getCatalogSourceNames(),
              false);
      if (catalogSources == null) {
        return;
      }
      if (catalogSources.isEmpty()) {
        MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
        box.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.NoCatalogSources.Title"));
        box.setMessage(BaseMessages.getString(PKG, "DvAiAdvisorDialog.NoCatalogSources.Message"));
        box.open();
        return;
      }
      session.setCatalogSourceNames(catalogSources);
      updateCatalogSourceButton();
    }

    boolean followUp = !session.isEmpty();
    var requestBuilder =
        DvAiRequest.builder()
            .userPrompt(question.trim())
            .scenario(
                EnumDialogSupport.readCombo(wScenario, DvAiScenario.class, DvAiScenario.GENERAL))
            .includeCheckResults(wIncludeChecks.getSelection())
            .includeCatalogSources(wIncludeCatalog.getSelection())
            .includeModelXml(wIncludeModelXml.getSelection())
            .includeLoadRunMetrics(wIncludeLoadRunMetrics.getSelection())
            .includeExecutionInfo(wIncludeExecutionInfo.getSelection())
            .followUp(followUp);
    for (String catalogSource : catalogSources) {
      requestBuilder.catalogSourceName(catalogSource);
    }
    for (String summary : session.consumePendingAppliedSummaries()) {
      requestBuilder.appliedChangeSummary(summary);
    }
    DvAiRequest request = requestBuilder.build();

    transcriptPanel.appendHeading(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Transcript.You"));
    transcriptPanel.appendText(question.trim(), true);
    wPrompt.setText("");

    working = true;
    wSend.setEnabled(false);
    wlStatusMessage.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Status.Working"));

    Thread worker =
        new Thread(
            () -> {
              try {
                var context = DvAiContextBuilder.build(model, metadataProvider, variables, request);
                String llmUserMessage =
                    followUp
                        ? DvAiAdvisorService.buildFollowUpUserPrompt(context)
                        : DvAiAdvisorService.buildInitialUserPrompt(context);
                DvAiResponse response =
                    DvAiAdvisorService.advise(
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
                              BaseMessages.getString(PKG, "DvAiAdvisorDialog.Status.Error"));
                          new ErrorDialog(
                              shell,
                              BaseMessages.getString(PKG, "DvAiAdvisorDialog.Error.Title"),
                              BaseMessages.getString(PKG, "DvAiAdvisorDialog.Error.Message"),
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

    transcriptPanel.appendHeading(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Transcript.Ai"));
    String advice = response.getMarkdownAdvice() != null ? response.getMarkdownAdvice() : "";
    transcriptPanel.appendText(advice, false);

    boolean hasProposals = response.getProposals() != null && !response.getProposals().isEmpty();
    if (hasProposals) {
      int count = response.getProposals().size();
      Button reviewButton =
          transcriptPanel.appendButton(
              BaseMessages.getString(PKG, "DvAiAdvisorDialog.ReviewTurn.Label", count));
      reviewButton.addListener(SWT.Selection, e -> reviewProposalsForTurn(turnIndex));
    } else if (session.getTurns().get(turnIndex).isProposalsDropped()) {
      transcriptPanel.appendSystemLine(
          BaseMessages.getString(PKG, "DvAiAdvisorDialog.ProposalsDropped.Message"));
    }

    working = false;
    wSend.setEnabled(true);
    wlStatusMessage.setText(
        hasProposals
            ? BaseMessages.getString(PKG, "DvAiAdvisorDialog.Status.WithProposals")
            : BaseMessages.getString(PKG, "DvAiAdvisorDialog.Status.Done"));
  }

  private void reviewProposalsForTurn(int turnIndex) {
    if (turnIndex < 0 || turnIndex >= session.getTurns().size()) {
      return;
    }
    List<DvAiProposal> proposals = session.getTurns().get(turnIndex).getProposals();
    if (proposals == null || proposals.isEmpty()) {
      return;
    }
    List<DvAiProposalValidator.ValidationResult> validation =
        DvAiProposalValidator.validate(model, proposals, metadataProvider, variables);
    DvAiProposalReviewDialog reviewDialog =
        new DvAiProposalReviewDialog(shell, proposals, validation);
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
      DvAiProposalApplier.apply(model, selected, metadataProvider, variables);
      if (onModelChanged != null) {
        onModelChanged.run();
      }
      session.recordApplied(turnIndex, selected);
      transcriptPanel.appendSystemLine(
          BaseMessages.getString(PKG, "DvAiAdvisorDialog.Transcript.Applied", selected.size()));
      wlStatusMessage.setText(BaseMessages.getString(PKG, "DvAiAdvisorDialog.Status.Applied"));
      transcriptPanel.refreshScroll();
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "DvAiAdvisorDialog.ApplyError.Title"),
          BaseMessages.getString(PKG, "DvAiAdvisorDialog.ApplyError.Message"),
          e instanceof HopException ? e : new HopException(e));
    }
  }

  private void changeCatalogSources() {
    List<String> selected =
        DvAiCatalogSourceSelector.selectSources(
            shell, model, variables, metadataProvider, onModelChanged, null, true);
    if (selected == null) {
      return;
    }
    if (selected.isEmpty()) {
      session.setCatalogSourceNames(List.of());
    } else {
      session.setCatalogSourceNames(selected);
    }
    updateCatalogSourceButton();
  }

  private void updateCatalogSourceButton() {
    boolean show = wIncludeCatalog.getSelection() && !session.getCatalogSourceNames().isEmpty();
    wChangeCatalogSources.setEnabled(show);
  }

  private void startNewConversation() {
    session.clear();
    lastScenarioIndex = wScenario.getSelectionIndex();
    session.setScenario(
        EnumDialogSupport.readCombo(wScenario, DvAiScenario.class, DvAiScenario.GENERAL));
    transcriptPanel.clear();
    updateCatalogSourceButton();
    wlStatusMessage.setText(statusMessage());
  }
}
