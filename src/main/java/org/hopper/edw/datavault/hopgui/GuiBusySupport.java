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

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/** Shows the platform wait cursor while running short, UI-thread work. */
public final class GuiBusySupport {

  private GuiBusySupport() {}

  /**
   * Runs {@code callable} under the wait cursor and returns its value. Checked exceptions from
   * {@code callable} are rethrown to the caller after the cursor is restored.
   */
  public static <T> T callWhile(Control control, Callable<T> callable) throws Exception {
    if (callable == null) {
      return null;
    }
    AtomicReference<T> value = new AtomicReference<>();
    AtomicReference<Exception> error = new AtomicReference<>();
    showWhile(
        control,
        () -> {
          try {
            value.set(callable.call());
          } catch (Exception e) {
            error.set(e);
          }
        });
    if (error.get() != null) {
      throw error.get();
    }
    return value.get();
  }

  public static void showWhile(Control control, Runnable runnable) {
    if (runnable == null) {
      return;
    }
    if (control == null || control.isDisposed()) {
      runnable.run();
      return;
    }
    Display display = control.getDisplay();
    Shell shell = control.getShell();
    if (display == null || display.isDisposed() || shell == null || shell.isDisposed()) {
      runnable.run();
      return;
    }

    Cursor wait = display.getSystemCursor(SWT.CURSOR_WAIT);
    Cursor previous = shell.getCursor();
    shell.setCursor(wait);
    try {
      pumpEvents(display);
      runnable.run();
    } finally {
      if (!shell.isDisposed()) {
        shell.setCursor(previous);
      }
      pumpEvents(display);
    }
  }

  private static void pumpEvents(Display display) {
    while (display.readAndDispatch()) {
      // Allow the wait cursor to paint before blocking work continues.
    }
  }
}
