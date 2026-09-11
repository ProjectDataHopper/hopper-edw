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
package org.hopper.edw.datavault.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.textblock.HTextBlockComponent;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.junit.jupiter.api.Test;

class EdwPresentationDashboardsTest {

  @Test
  void emptyProjectOverviewStillBuilds() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("HOP_PROJECT_NAME", "hop");
    HGeneratedCatalog catalog =
        EdwPresentationDashboards.projectOverview(new MemoryMetadataProvider(), variables);
    assertEquals(
        EdwPresentationDashboards.PROJECT_OVERVIEW_NAME, catalog.getPresentation().getName());
    List<HComponent> components = catalog.getPresentation().getPages().get(0).getComponents();
    assertFalse(components.isEmpty());
    HLabelComponent title = (HLabelComponent) components.get(0).getComponent();
    assertEquals("Project overview", title.getLabel());
    assertTrue(
        components.stream()
            .anyMatch(
                c ->
                    c.getComponent() instanceof HTextBlockComponent note
                        && note.getText() != null
                        && note.getText().contains("execution information")));
  }
}
