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

import java.util.List;
import java.util.function.Supplier;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.ai.DvAiAvailability;
import org.apache.hop.datavault.ai.HopAiAdvisoryResponse;
import org.apache.hop.datavault.ai.HopAiConfig;
import org.apache.hop.datavault.ai.HopAiConfigSingleton;
import org.apache.hop.datavault.ai.HopAiConversationSession;
import org.apache.hop.datavault.ai.HopAiProposal;
import org.apache.hop.datavault.ai.workflow.WorkflowAiAdvisorService;
import org.apache.hop.datavault.ai.workflow.WorkflowAiContextBuilder;
import org.apache.hop.datavault.ai.workflow.WorkflowAiProposalApplier;
import org.apache.hop.datavault.ai.workflow.WorkflowAiProposalValidator;
import org.apache.hop.datavault.ai.workflow.WorkflowAiRequest;
import org.apache.hop.datavault.ai.workflow.WorkflowAiScenario;
import org.apache.hop.datavault.hopgui.EnumDialogSupport;
import org.apache.hop.datavault.hopgui.help.DialogHelpSupport;
import org.apache.hop.datavault.hopgui.help.HelpTopics;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.ui.core.FormDataBuilder;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.MessageBox;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.gui.WindowProperty;
import org.apache.hop.ui.hopgui.HopGui;
import org.apache.hop.ui.hopgui.file.workflow.HopGuiWorkflowGraph;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.workflow.WorkflowMeta;
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

/** AI advisory dialog for Apache Hop workflows. */
public class WorkflowAiAdvisorDialog {

  private static final Class<?> PKG = WorkflowAiAdvisorDialog.class;

  private final Shell parent;
  private final HopGui hopGui;
  private final HopGuiWorkflowGraph workflowGraph;
  private final WorkflowMeta workflowMeta;
  private final IVariables variables;
  private final IHopMetadataProvider metadataProvider;
  private final Supplier<String> logsSupplier;
  private final String focusActionName;

  private final HopAiConversationSession session = new HopAiConversationSession();

  private Shell shell;
  private Combo wScenario;
  private Text wPrompt;
  private Button wIncludeChecks;
  private Button wIncludeCatalog;
  private Button wIncludeTopologyXml;
  private Button wIncludeExecutionLog;
  private Button wSend;
  private Label wlStatusMessage;
  private HopAiTranscriptPanel transcriptPanel;
  private boolean working;
  private int lastScenarioIndex = WorkflowAiScenario.WORKFLOW_GENERAL.ordinal();

  public WorkflowAiAdvisorDialog(
      Shell parent,
      HopGui hopGui,
      HopGuiWorkflowGraph workflowGraph,
      WorkflowMeta workflowMeta,
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      Supplier<String> logsSupplier,
      String focusActionName) {
    this.parent = parent;
    this.hopGui = hopGui;
    this.workflowGraph = workflowGraph;
    this.workflowMeta = workflowMeta;
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.logsSupplier = logsSupplier;
    this.focusActionName = focusActionName;
  }

  public void open() {
    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX);
    PropsUi.setLook(shell);
    shell.setText(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Title"));

    FormLayout layout = new FormLayout();
    layout.marginWidth = PropsUi.getFormMargin();
    layout.marginHeight = PropsUi.getFormMargin();
    shell.setLayout(layout);
    int margin = PropsUi.getMargin();

    wSend = new Button(shell, SWT.PUSH);
    wSend.setText(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Send.Label"));
    wSend.addListener(SWT.Selection, e -> sendMessage());
    Button wNewConversation = new Button(shell, SWT.PUSH);
    wNewConversation.setText(
        BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.NewConversation.Label"));
    wNewConversation.addListener(SWT.Selection, e -> startNewConversation());
    Button wClose = new Button(shell, SWT.PUSH);
    wClose.setText(BaseMessages.getString(PKG, "System.Button.Close"));
    wClose.addListener(SWT.Selection, e -> dispose());
    DialogHelpSupport.createHelpButton(shell, HelpTopics.WORKFLOW_AI_ADVISOR);

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
            BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Conversation.Label"));

    shell.addListener(SWT.Resize, e -> transcriptPanel.refreshScroll());

    BaseTransformDialog.setSize(shell, 1000, 750);
    shell.open();
    while (!shell.isDisposed()) {
      if (!parent.getDisplay().readAndDispatch()) {
        parent.getDisplay().sleep();
      }
    }
  }

  private Control createScenarioWidgets(int margin) {
    Label wlScenario = new Label(shell, SWT.LEFT);
    wlScenario.setText(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Scenario.Label"));
    PropsUi.setLook(wlScenario);
    FormData fdlScenario = new FormData();
    fdlScenario.left = new FormAttachment(0, 0);
    fdlScenario.right = new FormAttachment(100, 0);
    fdlScenario.top = new FormAttachment(0, margin);
    wlScenario.setLayoutData(fdlScenario);

    wScenario = new Combo(shell, SWT.READ_ONLY | SWT.BORDER);
    PropsUi.setLook(wScenario);
    EnumDialogSupport.populateCombo(wScenario, WorkflowAiScenario.class);
    EnumDialogSupport.selectCombo(wScenario, WorkflowAiScenario.WORKFLOW_GENERAL);
    lastScenarioIndex = wScenario.getSelectionIndex();
    wScenario.addListener(SWT.Selection, this::changeScenarioMessageBox);
    FormData fdScenario = new FormData();
    fdScenario.left = new FormAttachment(0, 0);
    fdScenario.top = new FormAttachment(wlScenario, margin);
    fdScenario.right = new FormAttachment(100, 0);
    wScenario.setLayoutData(fdScenario);
    return wScenario;
  }

  private Control createInclusionWidgets(Control lastControl, int margin) {
    wIncludeChecks = new Button(shell, SWT.CHECK);
    wIncludeChecks.setText(
        BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.IncludeChecks.Label"));
    wIncludeChecks.setSelection(true);
    PropsUi.setLook(wIncludeChecks);
    FormData fdChecks = new FormData();
    fdChecks.left = new FormAttachment(0, 0);
    fdChecks.top = new FormAttachment(lastControl, margin);
    wIncludeChecks.setLayoutData(fdChecks);

    wIncludeCatalog = new Button(shell, SWT.CHECK);
    wIncludeCatalog.setText(
        BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.IncludeCatalog.Label"));
    wIncludeCatalog.setSelection(true);
    PropsUi.setLook(wIncludeCatalog);
    FormData fdCatalog = new FormData();
    fdCatalog.left = new FormAttachment(wIncludeChecks, margin);
    fdCatalog.top = new FormAttachment(wIncludeChecks, 0, SWT.CENTER);
    wIncludeCatalog.setLayoutData(fdCatalog);

    wIncludeTopologyXml = new Button(shell, SWT.CHECK);
    wIncludeTopologyXml.setText(
        BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.IncludeTopologyXml.Label"));
    wIncludeTopologyXml.setSelection(false);
    PropsUi.setLook(wIncludeTopologyXml);
    FormData fdTopology = new FormData();
    fdTopology.left = new FormAttachment(wIncludeCatalog, margin);
    fdTopology.top = new FormAttachment(wIncludeChecks, 0, SWT.CENTER);
    wIncludeTopologyXml.setLayoutData(fdTopology);

    wIncludeExecutionLog = new Button(shell, SWT.CHECK);
    wIncludeExecutionLog.setText(
        BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.IncludeExecutionLog.Label"));
    wIncludeExecutionLog.setSelection(true);
    PropsUi.setLook(wIncludeExecutionLog);
    FormData fdLog = new FormData();
    fdLog.left = new FormAttachment(0, 0);
    fdLog.top = new FormAttachment(wIncludeChecks, margin);
    wIncludeExecutionLog.setLayoutData(fdLog);

    return wIncludeExecutionLog;
  }

  private Control createStatusWidgets(Control lastControl, int margin) {
    Label wlStatusLabel = new Label(shell, SWT.LEFT);
    wlStatusLabel.setText(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Status.Label"));
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

  private void createPromptArea(int margin) {
    int height = (int) (-100 * PropsUi.getNativeZoomFactor());

    Label wlPrompt = new Label(shell, SWT.RIGHT | SWT.TOP);
    wlPrompt.setText(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Prompt.Label"));
    PropsUi.setLook(wlPrompt);
    FormData fdlPrompt = new FormData();
    fdlPrompt.left = new FormAttachment(0, 0);
    fdlPrompt.top = new FormAttachment(wSend, height);
    wlPrompt.setLayoutData(fdlPrompt);

    wPrompt = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
    PropsUi.setLook(wPrompt);
    wPrompt.setText(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Prompt.Default"));
    FormData fdPrompt = new FormData();
    fdPrompt.left = new FormAttachment(wlPrompt, 2 * margin);
    fdPrompt.top = new FormAttachment(wSend, height);
    fdPrompt.bottom = new FormAttachment(wSend, -margin);
    fdPrompt.right = new FormAttachment(100, 0);
    wPrompt.setLayoutData(fdPrompt);
  }

  private Label addHorizontalSeparator(Control lastControl, int margin) {
    Label separator = new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL);
    FormData fd = new FormDataBuilder().left().right().top(lastControl, 2 * margin).build();
    separator.setLayoutData(fd);
    return separator;
  }

  private void changeScenarioMessageBox(Event e) {
    if (wScenario.getSelectionIndex() != lastScenarioIndex && !session.isEmpty()) {
      MessageBox box = new MessageBox(shell, SWT.YES | SWT.NO | SWT.ICON_QUESTION);
      box.setText(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.ScenarioChange.Title"));
      box.setMessage(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.ScenarioChange.Message"));
      if (box.open() == SWT.YES) {
        startNewConversation();
        lastScenarioIndex = wScenario.getSelectionIndex();
      } else {
        wScenario.select(lastScenarioIndex);
      }
    } else {
      lastScenarioIndex = wScenario.getSelectionIndex();
    }
  }

  private void sendMessage() {
    if (working) {
      return;
    }
    String question = wPrompt.getText();
    if (Utils.isEmpty(question)) {
      MessageBox box = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
      box.setText(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.EmptyPrompt.Title"));
      box.setMessage(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.EmptyPrompt.Message"));
      box.open();
      return;
    }

    boolean followUp = !session.isEmpty();
    String logsExcerpt = "";
    if (wIncludeExecutionLog.getSelection() && logsSupplier != null) {
      String logs = logsSupplier.get();
      logsExcerpt = logs != null ? logs : "";
    }

    var requestBuilder =
        WorkflowAiRequest.builder()
            .userPrompt(question.trim())
            .scenario(
                EnumDialogSupport.readCombo(
                    wScenario, WorkflowAiScenario.class, WorkflowAiScenario.WORKFLOW_GENERAL))
            .includeCheckResults(wIncludeChecks.getSelection())
            .includeActionCatalog(wIncludeCatalog.getSelection())
            .includeTopologyXml(wIncludeTopologyXml.getSelection())
            .includeExecutionLog(wIncludeExecutionLog.getSelection())
            .focusActionName(focusActionName)
            .logsExcerpt(logsExcerpt)
            .followUp(followUp);
    for (String summary : session.consumePendingAppliedSummaries()) {
      requestBuilder.appliedChangeSummary(summary);
    }
    WorkflowAiRequest request = requestBuilder.build();

    transcriptPanel.appendHeading(
        BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Transcript.You"));
    transcriptPanel.appendText(question.trim(), true);
    wPrompt.setText("");

    working = true;
    wSend.setEnabled(false);
    wlStatusMessage.setText(BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Status.Working"));

    HopAiConfig config = HopAiConfigSingleton.getConfig();
    Thread worker =
        new Thread(
            () -> {
              try {
                var context =
                    WorkflowAiContextBuilder.build(
                        workflowMeta, metadataProvider, variables, request);
                String llmUserMessage =
                    followUp
                        ? WorkflowAiAdvisorService.buildFollowUpUserPrompt(context)
                        : WorkflowAiAdvisorService.buildInitialUserPrompt(context);
                HopAiAdvisoryResponse response =
                    WorkflowAiAdvisorService.advise(
                        config, variables, context, session.buildLlmHistory());
                Display.getDefault()
                    .asyncExec(
                        () -> {
                          if (shell.isDisposed()) {
                            return;
                          }
                          handleAssistantResponse(question.trim(), llmUserMessage, response);
                        });
              } catch (Exception ex) {
                Display.getDefault()
                    .asyncExec(
                        () -> {
                          if (shell.isDisposed()) {
                            return;
                          }
                          working = false;
                          wSend.setEnabled(true);
                          wlStatusMessage.setText(
                              BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Status.Error"));
                          new ErrorDialog(
                              shell,
                              BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Error.Title"),
                              BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Error.Message"),
                              ex instanceof HopException ? ex : new HopException(ex));
                        });
              }
            });
    worker.setDaemon(true);
    worker.start();
  }

  private void handleAssistantResponse(
      String userPrompt, String llmUserMessage, HopAiAdvisoryResponse response) {
    session.recordTurn(userPrompt, llmUserMessage, response);
    int turnIndex = session.getTurns().size() - 1;

    transcriptPanel.appendHeading(
        BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Transcript.Ai"));
    String advice = response.getMarkdownAdvice() != null ? response.getMarkdownAdvice() : "";
    transcriptPanel.appendText(advice, false);

    boolean hasProposals = response.getProposals() != null && !response.getProposals().isEmpty();
    if (hasProposals) {
      int count = response.getProposals().size();
      Button reviewButton =
          transcriptPanel.appendButton(
              BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.ReviewTurn.Label", count));
      final int reviewTurnIndex = turnIndex;
      reviewButton.addListener(SWT.Selection, e -> reviewProposalsForTurn(reviewTurnIndex));
    } else if (session.getTurns().get(turnIndex).isProposalsDropped()) {
      transcriptPanel.appendSystemLine(
          BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.ProposalsDropped.Message"));
    }

    working = false;
    wSend.setEnabled(true);
    wlStatusMessage.setText(
        hasProposals
            ? BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Status.WithProposals")
            : BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Status.Done"));
  }

  private void reviewProposalsForTurn(int turnIndex) {
    if (turnIndex < 0 || turnIndex >= session.getTurns().size()) {
      return;
    }
    List<HopAiProposal> proposals = session.getTurns().get(turnIndex).getProposals();
    if (proposals == null || proposals.isEmpty()) {
      return;
    }
    var validation = WorkflowAiProposalValidator.validate(workflowMeta, proposals);
    HopAiProposalReviewDialog reviewDialog =
        new HopAiProposalReviewDialog(
            shell, proposals, validation, WorkflowAiProposalApplier::preview);
    if (!reviewDialog.open()) {
      return;
    }
    List<HopAiProposal> selected = reviewDialog.getSelectedProposals();
    if (selected.isEmpty()) {
      return;
    }
    try {
      WorkflowAiProposalApplier.apply(workflowMeta, selected, hopGui);
      if (workflowGraph != null) {
        workflowGraph.setChanged();
        workflowGraph.updateGui();
      }
      session.recordApplied(turnIndex, selected);
      transcriptPanel.appendSystemLine(
          BaseMessages.getString(
              PKG, "WorkflowAiAdvisorDialog.Transcript.Applied", selected.size()));
      wlStatusMessage.setText(
          BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Status.Applied"));
      transcriptPanel.refreshScroll();
    } catch (Exception ex) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.ApplyError.Title"),
          BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.ApplyError.Message"),
          ex instanceof HopException ? ex : new HopException(ex));
    }
  }

  private void startNewConversation() {
    session.clear();
    lastScenarioIndex = wScenario.getSelectionIndex();
    transcriptPanel.clear();
    wlStatusMessage.setText(statusMessage());
  }

  private String statusMessage() {
    HopAiConfig config = HopAiConfigSingleton.getConfig();
    if (!config.isAiEnabled()) {
      return BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Status.Disabled");
    }
    if (!DvAiAvailability.isLanguageModelChatAvailable()) {
      return BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Status.PluginMissing");
    }
    return BaseMessages.getString(PKG, "WorkflowAiAdvisorDialog.Status.Ready");
  }

  private void dispose() {
    WindowProperty winProp = new WindowProperty(shell);
    PropsUi.getInstance().setSessionScreen(winProp);
    shell.dispose();
  }

  public static void open(
      HopGui hopGui,
      HopGuiWorkflowGraph workflowGraph,
      WorkflowMeta workflowMeta,
      IVariables variables,
      Supplier<String> logsSupplier,
      String focusActionName) {
    if (workflowMeta == null) {
      return;
    }
    new WorkflowAiAdvisorDialog(
            hopGui.getShell(),
            hopGui,
            workflowGraph,
            workflowMeta,
            variables,
            hopGui.getMetadataProvider(),
            logsSupplier,
            focusActionName)
        .open();
  }
}
