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
package org.hopper.edw.datavault.hopgui.perspective.journey.link;

import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.program.Program;
import org.hopper.edw.datavault.hopgui.perspective.journey.EdwJourneySnapshot;
import org.hopper.presentation.host.HPresentationHost;

/** Routes presentation clicks to {@code hop-edw} links. Other http URLs open in the browser. */
public final class EdwJourneyPresentationHost implements HPresentationHost {

  private final Supplier<EdwJourneySnapshot> snapshot;
  private final Supplier<EdwJourneyLinkExecutor.EdwJourneyNavigator> navigator;

  public EdwJourneyPresentationHost(
      Supplier<EdwJourneySnapshot> snapshot,
      Supplier<EdwJourneyLinkExecutor.EdwJourneyNavigator> navigator) {
    this.snapshot = snapshot;
    this.navigator = navigator;
  }

  @Override
  public void openLink(String url, boolean newTab) {
    if (StringUtils.isBlank(url) || url.indexOf('{') >= 0) {
      return;
    }
    EdwJourneyLink link = EdwJourneyLink.parse(url);
    if (link == null) {
      if (url.startsWith("http://") || url.startsWith("https://")) {
        Program.launch(url);
      }
      return;
    }
    EdwJourneyLinkExecutor.EdwJourneyNavigator target = navigator == null ? null : navigator.get();
    EdwJourneySnapshot picture = snapshot == null ? null : snapshot.get();
    EdwJourneyLinkExecutor.execute(link, picture, target);
  }
}
