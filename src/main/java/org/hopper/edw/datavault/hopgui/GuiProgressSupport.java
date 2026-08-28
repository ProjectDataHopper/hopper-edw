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
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.IProgressMonitor;
import org.apache.hop.core.ProgressNullMonitorListener;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.dialog.ProgressMonitorDialog;
import org.apache.hop.ui.util.EnvironmentUtils;
import org.eclipse.swt.widgets.Shell;

/**
 * Runs long work under Hop's {@link ProgressMonitorDialog} (desktop) or a wait cursor (Hop Web /
 * missing shell).
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
   * Runs {@code work} with a cancelable progress dialog on desktop Hop. On Hop Web, or when {@code
   * shell} is missing, falls back to {@link GuiBusySupport} and a null monitor.
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
