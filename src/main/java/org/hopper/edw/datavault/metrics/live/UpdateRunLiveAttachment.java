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
package org.hopper.edw.datavault.metrics.live;

import lombok.Value;
import org.apache.hop.core.logging.ILoggingObject;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;

/**
 * Non-persisted live-monitor identity copied onto programmatic child DV/BV/DM updates so their
 * snapshots can join a resource-group wave without renaming the child action.
 */
@Value
public class UpdateRunLiveAttachment {
  public static final String EXTENSION_KEY = "hopper.edw.updateRunLiveAttachment";

  String waveId;
  String canvasActionName;

  public static void apply(IAction action, UpdateRunLiveAttachment attachment) {
    if (attachment == null || !(action instanceof ActionBase base)) {
      return;
    }
    base.getExtensionDataMap().put(EXTENSION_KEY, attachment);
  }

  public static UpdateRunLiveAttachment from(ILoggingObject parent) {
    if (!(parent instanceof ActionBase base)) {
      return null;
    }
    Object value = base.getExtensionDataMap().get(EXTENSION_KEY);
    return value instanceof UpdateRunLiveAttachment attachment ? attachment : null;
  }
}
