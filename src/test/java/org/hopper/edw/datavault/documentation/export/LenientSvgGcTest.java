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
package org.hopper.edw.datavault.documentation.export;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.hop.core.svg.SvgFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LenientSvgGcTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void missingIconDoesNotThrow() throws Exception {
    HopSvgGraphics2D graphics2D = HopSvgGraphics2D.newDocument();
    LenientSvgGc gc = new LenientSvgGc(graphics2D, new Point(200, 200), 32, 0, 0, false);
    SvgFile missing = new SvgFile("sortedmerge.svg", LenientSvgGc.class.getClassLoader());
    assertDoesNotThrow(() -> gc.drawImage(missing, 10, 10, 32, 32, 1.0f, 0));
    String xml = graphics2D.toXml();
    assertTrue(xml.contains("<svg"), xml);
  }
}
