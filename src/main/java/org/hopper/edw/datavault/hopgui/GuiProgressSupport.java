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
package org.hopper.edw.datavault.hopgui;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.hop.core.Const;
import org.apache.hop.core.IProgressMonitor;
import org.apache.hop.core.ProgressNullMonitorListener;
import org.apache.hop.core.util.Utils;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.ProgressMonitorDialog;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.hopgui.ServerPushSessionFacade;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

/**
 * Runs long work under Hop's {@link ProgressMonitorDialog} (desktop). {@link #run} falls back to a
 * wait cursor on Hop Web. {@link #runAsync} shows a RAP-safe progress dialog and completes later
 * via server push.
 *
 * <p>{@link ProgressMonitorDialog}'s monitor {@code done()} disposes the dialog and unblocks the UI
 * thread. Callers must not invoke {@code done()} themselves. This helper defers {@code done()}
 * until the worker return value is stored, matching {@code ModelDialogValidationSupport}.
 */
public final class GuiProgressSupport {

  private static final Class<?> PKG = GuiProgressSupport.class;

  private GuiProgressSupport() {}

  /** Work that reports progress and may throw checked exceptions. */
  @FunctionalInterface
  public interface ProgressWork<T> {
    T run(IProgressMonitor monitor) throws Exception;
  }

  /**
   * Result of {@link #run(Shell, boolean, ProgressWork)}, including whether the user cancelled.
   *
   * <p>{@code value} is {@code null} when work threw (an error dialog is already shown) or when
   * there was no work.
   */
  public record ProgressResult<T>(T value, boolean cancelled) {}

  /**
   * Runs {@code work} and invokes {@code onDone} with the result. Desktop still blocks until the
   * progress dialog closes. Hop Web cannot pump a blocking UI loop, so a dialog is shown, the
   * request returns, and {@code onDone} runs later on the UI thread via server push.
   */
  public static <T> void runAsync(
      Shell shell,
      String title,
      boolean cancelable,
      ProgressWork<T> work,
      Consumer<ProgressResult<T>> onDone) {
    Consumer<ProgressResult<T>> done = onDone == null ? ignored -> {} : onDone;
    if (!EnvironmentUtils.getInstance().isWeb() || shell == null || shell.isDisposed()) {
      done.accept(run(shell, cancelable, work));
      return;
    }
    runWithWebDialog(shell, title, cancelable, work, done);
  }

  /**
   * Runs {@code work} with a cancelable progress dialog on desktop Hop. On Hop Web, or when {@code
   * shell} is missing, falls back to {@link GuiBusySupport} and a null monitor.
   *
   * <p>Prefer {@link #runAsync} for long Hop Web work that should show a progress dialog.
   */
  public static <T> ProgressResult<T> run(Shell shell, boolean cancelable, ProgressWork<T> work) {
    if (work == null) {
      return new ProgressResult<>(null, false);
    }
    if (shell == null || shell.isDisposed() || EnvironmentUtils.getInstance().isWeb()) {
      return runWithWaitCursor(shell, work);
    }

    AtomicReference<T> value = new AtomicReference<>();
    ProgressMonitorDialog monitorDialog = new ProgressMonitorDialog(shell);
    try {
      monitorDialog.run(
          cancelable,
          monitor -> {
            DeferredDoneMonitor deferred = new DeferredDoneMonitor(monitor);
            try {
              value.set(work.run(deferred));
            } catch (Throwable e) {
              throw new InvocationTargetException(
                  e,
                  BaseMessages.getString(
                      PKG, "GuiProgressSupport.Error.Exception", e.getMessage()));
            } finally {
              deferred.finish();
            }
          });
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "GuiProgressSupport.Error.Title"),
          BaseMessages.getString(PKG, "GuiProgressSupport.Error.Message"),
          e);
      return new ProgressResult<>(null, false);
    } catch (Exception e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "GuiProgressSupport.Error.Title"),
          BaseMessages.getString(PKG, "GuiProgressSupport.Error.Message"),
          e);
      return new ProgressResult<>(null, false);
    }

    boolean cancelled =
        monitorDialog.getProgressMonitor() != null
            && monitorDialog.getProgressMonitor().isCanceled();
    return new ProgressResult<>(value.get(), cancelled);
  }

  private static <T> ProgressResult<T> runWithWaitCursor(Shell shell, ProgressWork<T> work) {
    AtomicReference<T> value = new AtomicReference<>();
    AtomicReference<Exception> error = new AtomicReference<>();
    Runnable body =
        () -> {
          try {
            value.set(work.run(new ProgressNullMonitorListener()));
          } catch (Exception e) {
            error.set(e);
          }
        };
    if (shell != null && !shell.isDisposed()) {
      GuiBusySupport.showWhile(shell, body);
    } else {
      body.run();
    }
    if (error.get() != null) {
      if (shell != null && !shell.isDisposed()) {
        new ErrorDialog(
            shell,
            BaseMessages.getString(PKG, "GuiProgressSupport.Error.Title"),
            BaseMessages.getString(PKG, "GuiProgressSupport.Error.Message"),
            error.get());
      }
      return new ProgressResult<>(null, false);
    }
    return new ProgressResult<>(value.get(), false);
  }

  private static <T> void runWithWebDialog(
      Shell parent,
      String title,
      boolean cancelable,
      ProgressWork<T> work,
      Consumer<ProgressResult<T>> onDone) {
    Display display = parent.getDisplay();
    Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
    dialog.setText(
        Utils.isEmpty(title)
            ? BaseMessages.getString(PKG, "GuiProgressSupport.Dialog.Title")
            : title);
    dialog.setImage(GuiResource.getInstance().getImageHopUi());
    PropsUi.setLook(dialog);
    FormLayout layout = new FormLayout();
    layout.marginTop = PropsUi.getFormMargin();
    layout.marginLeft = PropsUi.getFormMargin();
    layout.marginRight = PropsUi.getFormMargin();
    layout.marginBottom = PropsUi.getFormMargin();
    dialog.setLayout(layout);

    int margin = PropsUi.getMargin();
    Label wlTask = new Label(dialog, SWT.LEFT);
    wlTask.setText(BaseMessages.getString(PKG, "GuiProgressSupport.Dialog.Task"));
    PropsUi.setLook(wlTask);
    FormData fdlTask = new FormData();
    fdlTask.left = new FormAttachment(0, 0);
    fdlTask.top = new FormAttachment(0, 0);
    fdlTask.right = new FormAttachment(100, 0);
    wlTask.setLayoutData(fdlTask);

    Label wlSubTask = new Label(dialog, SWT.LEFT);
    wlSubTask.setText("");
    PropsUi.setLook(wlSubTask);
    FormData fdlSubTask = new FormData();
    fdlSubTask.left = new FormAttachment(0, 0);
    fdlSubTask.top = new FormAttachment(wlTask, margin);
    fdlSubTask.right = new FormAttachment(100, 0);
    wlSubTask.setLayoutData(fdlSubTask);

    ProgressBar wProgressBar = new ProgressBar(dialog, SWT.HORIZONTAL);
    wProgressBar.setMinimum(0);
    wProgressBar.setMaximum(100);
    wProgressBar.setSelection(0);
    FormData fdBar = new FormData();
    fdBar.left = new FormAttachment(0, 0);
    fdBar.right = new FormAttachment(100, 0);
    fdBar.top = new FormAttachment(wlSubTask, margin);
    wProgressBar.setLayoutData(fdBar);

    AtomicBoolean cancelled = new AtomicBoolean(false);
    if (cancelable) {
      Button wCancel = new Button(dialog, SWT.PUSH);
      wCancel.setText(BaseMessages.getString("System.Button.Cancel"));
      wCancel.addListener(SWT.Selection, e -> cancelled.set(true));
      BaseTransformDialog.positionBottomButtons(
          dialog, new Button[] {wCancel}, margin, wProgressBar);
    }

    dialog.addListener(
        SWT.Close,
        e -> {
          e.doit = false;
          cancelled.set(true);
        });
    dialog.setSize(480, cancelable ? 200 : 160);
    dialog.open();

    WebProgressMonitor monitor =
        new WebProgressMonitor(display, dialog, wlTask, wlSubTask, wProgressBar, cancelled);
    AtomicReference<T> value = new AtomicReference<>();
    AtomicReference<Exception> error = new AtomicReference<>();
    ServerPushSessionFacade.start();
    Runnable worker =
        bindUiSession(
            () -> {
              try {
                value.set(work.run(monitor));
              } catch (Exception e) {
                error.set(e);
              }
              if (display.isDisposed()) {
                ServerPushSessionFacade.stop();
                return;
              }
              display.asyncExec(
                  () -> {
                    try {
                      if (dialog != null && !dialog.isDisposed()) {
                        dialog.dispose();
                      }
                      if (error.get() != null) {
                        if (parent != null && !parent.isDisposed()) {
                          new ErrorDialog(
                              parent,
                              BaseMessages.getString(PKG, "GuiProgressSupport.Error.Title"),
                              BaseMessages.getString(PKG, "GuiProgressSupport.Error.Message"),
                              error.get());
                        }
                        onDone.accept(new ProgressResult<>(null, false));
                        return;
                      }
                      onDone.accept(
                          new ProgressResult<>(
                              value.get(), cancelled.get() || monitor.isCanceled()));
                    } finally {
                      ServerPushSessionFacade.stop();
                    }
                  });
            });
    Thread thread = new Thread(worker, "hopper-edw-web-progress");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Re-binds the RAP UI session onto a background thread (Hop 2.19 has no BackgroundThreadFacade).
   * Desktop has no session and returns {@code work} unchanged.
   */
  static Runnable bindUiSession(Runnable work) {
    if (work == null) {
      return () -> {};
    }
    try {
      Class<?> rwtClass = Class.forName("org.eclipse.rap.rwt.RWT");
      Object session = rwtClass.getMethod("getUISession").invoke(null);
      if (session == null) {
        return work;
      }
      return () -> {
        try {
          Object bound = session.getClass().getMethod("isBound").invoke(session);
          if (Boolean.FALSE.equals(bound)) {
            work.run();
            return;
          }
          session.getClass().getMethod("exec", Runnable.class).invoke(session, work);
        } catch (Exception ignored) {
          work.run();
        }
      };
    } catch (Exception ignored) {
      return work;
    }
  }

  private static final class WebProgressMonitor implements IProgressMonitor {
    private final Display display;
    private final Shell dialog;
    private final Label wlTask;
    private final Label wlSubTask;
    private final ProgressBar wProgressBar;
    private final AtomicBoolean cancelled;
    private final AtomicInteger workedAccumulated = new AtomicInteger();

    private WebProgressMonitor(
        Display display,
        Shell dialog,
        Label wlTask,
        Label wlSubTask,
        ProgressBar wProgressBar,
        AtomicBoolean cancelled) {
      this.display = display;
      this.dialog = dialog;
      this.wlTask = wlTask;
      this.wlSubTask = wlSubTask;
      this.wProgressBar = wProgressBar;
      this.cancelled = cancelled;
    }

    @Override
    public void beginTask(String message, int nrWorks) {
      workedAccumulated.set(0);
      async(
          () -> {
            if (disposed()) {
              return;
            }
            wlTask.setText(Const.NVL(message, ""));
            wProgressBar.setMaximum(Math.max(1, nrWorks));
            wProgressBar.setSelection(0);
          });
    }

    @Override
    public void subTask(String message) {
      async(
          () -> {
            if (disposed()) {
              return;
            }
            wlSubTask.setText(Const.NVL(message, ""));
          });
    }

    @Override
    public boolean isCanceled() {
      return cancelled.get();
    }

    @Override
    public void worked(int nrWorks) {
      if (nrWorks <= 0) {
        return;
      }
      int total = workedAccumulated.addAndGet(nrWorks);
      async(
          () -> {
            if (disposed()) {
              return;
            }
            int max = wProgressBar.getMaximum();
            wProgressBar.setSelection(Math.min(total, max > 0 ? max : total));
          });
    }

    @Override
    public void done() {
      // Dialog is disposed by the caller after the worker returns.
    }

    @Override
    public void setTaskName(String taskName) {
      async(
          () -> {
            if (disposed()) {
              return;
            }
            wlTask.setText(Const.NVL(taskName, ""));
          });
    }

    private boolean disposed() {
      return dialog.isDisposed() || wlTask.isDisposed() || wProgressBar.isDisposed();
    }

    private void async(Runnable runnable) {
      if (display.isDisposed()) {
        return;
      }
      display.asyncExec(runnable);
    }
  }

  /**
   * Forwards progress updates but defers {@link #done()} so the progress shell is not disposed
   * until the caller has stored the worker result.
   */
  private static final class DeferredDoneMonitor implements IProgressMonitor {
    private final IProgressMonitor delegate;
    private boolean finished;

    private DeferredDoneMonitor(IProgressMonitor delegate) {
      this.delegate = delegate != null ? delegate : new ProgressNullMonitorListener();
    }

    @Override
    public void beginTask(String message, int nrWorks) {
      delegate.beginTask(message, nrWorks);
    }

    @Override
    public void subTask(String message) {
      delegate.subTask(message);
    }

    @Override
    public boolean isCanceled() {
      return delegate.isCanceled();
    }

    @Override
    public void worked(int nrWorks) {
      delegate.worked(nrWorks);
    }

    @Override
    public void done() {
      // Deferred — see finish().
    }

    @Override
    public void setTaskName(String taskName) {
      delegate.setTaskName(taskName);
    }

    private void finish() {
      if (finished) {
        return;
      }
      finished = true;
      delegate.done();
    }
  }
}
