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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.apache.hop.core.variables.Variables;
import org.hopper.edw.datavault.busmatrix.BusMatrix;
import org.hopper.edw.datavault.busmatrix.BusMatrixCell;
import org.hopper.edw.datavault.busmatrix.BusMatrixColumn;
import org.hopper.edw.datavault.busmatrix.BusMatrixRow;
import org.hopper.edw.datavault.busmatrix.BusMatrixSvgPainter;
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
  void painterUsesLightPaletteWhenNotDark() {
    BusMatrixColumn col =
        new BusMatrixColumn("d_date", "d_date", "d_date", "DIMENSION", "star.hdm", "d_date");
    BusMatrixRow row =
        new BusMatrixRow(
            "f_orders",
            "f_orders",
            "grain",
            "FACT",
            "star.hdm",
            "orders",
            "Retail",
            "Sales",
            "",
            "",
            Map.of("d_date", new BusMatrixCell(List.of("date"))));
    BusMatrix matrix = new BusMatrix("g", List.of(row), List.of(col), List.of());

    String svg = BusMatrixSvgPainter.paint(matrix, false);
    assertTrue(svg.contains("#ffffff"));
    assertTrue(svg.contains("#17324d"));
    assertTrue(svg.contains("#c9e8fb"));
    assertTrue(svg.contains("font-family:Segoe UI, sans-serif"));
  }

  @Test
  void painterUsesDarkPaletteWhenDark() {
    BusMatrixColumn col =
        new BusMatrixColumn("d_date", "d_date", "d_date", "DIMENSION", "star.hdm", "d_date");
    BusMatrixRow row =
        new BusMatrixRow(
            "f_orders",
            "f_orders",
            "grain",
            "FACT",
            "star.hdm",
            "orders",
            "Retail",
            "Sales",
            "",
            "",
            Map.of("d_date", new BusMatrixCell(List.of("date"))));
    BusMatrix matrix = new BusMatrix("g", List.of(row), List.of(col), List.of());

    String svg = BusMatrixSvgPainter.paint(matrix, true);
    assertTrue(svg.contains("#242424"));
    assertTrue(svg.contains("#0d1b27"));
    assertTrue(svg.contains("#1b3a4b"));
    assertTrue(svg.contains("#62bdf7"));
    assertTrue(svg.contains("font-family:Segoe UI, sans-serif"));
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
