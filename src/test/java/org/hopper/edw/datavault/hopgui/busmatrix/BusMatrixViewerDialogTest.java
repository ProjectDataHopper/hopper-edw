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
package org.hopper.edw.datavault.hopgui.busmatrix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class BusMatrixViewerDialogTest {

  @Test
  void withLowercaseExtensionNormalizesCsvAndSvg() {
    assertEquals("out.csv", BusMatrixViewerDialog.withLowercaseExtension("out.CSV", "csv"));
    assertEquals("out.csv", BusMatrixViewerDialog.withLowercaseExtension("out.csv", "csv"));
    assertEquals("out.csv", BusMatrixViewerDialog.withLowercaseExtension("out", "csv"));
    assertEquals(
        "${PROJECT_HOME}/work/m.svg",
        BusMatrixViewerDialog.withLowercaseExtension("${PROJECT_HOME}/work/m.SVG", "svg"));
  }

  @Test
  void contrastIfDarkLeavesSvgWhenNotDark() {
    String svg = "<svg fill=\"#ffffff\"/>";
    assertEquals(svg, BusMatrixCanvas.contrastIfDark(svg));
  }

  @Test
  void resolveExportFilenameStripsHopInstallPrefixAndExpandsProjectHome() {
    Variables vars = new Variables();
    vars.setVariable("PROJECT_HOME", "/home/matt/git/ProjectDataHopper/hopper-edw");
    assertEquals(
        "/home/matt/git/ProjectDataHopper/hopper-edw/work/bus-matrix.svg",
        BusMatrixViewerDialog.resolveExportFilename(
            "file:///home/matt/git/mattcasters/hop/assemblies/client/target/hop/${PROJECT_HOME}/work/bus-matrix.svg",
            vars));
    assertEquals(
        "/home/matt/git/ProjectDataHopper/hopper-edw/work/bus-matrix.csv",
        BusMatrixViewerDialog.resolveExportFilename("${PROJECT_HOME}/work/bus-matrix.csv", vars));
  }
}
