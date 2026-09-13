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
package org.hopper.edw.datavault.diagram;

import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.hopper.core.exception.HException;
import org.hopper.render.pdf.HSvgPdfExporter;

/** SVG → PDF via hopper-presentation-core {@link HSvgPdfExporter}. */
public final class EdwPdfSupport {

  private EdwPdfSupport() {}

  public static byte[] svgToPdf(String svgXml) throws HopException {
    if (svgXml == null || svgXml.isBlank()) {
      throw new HopException("No SVG to transcode to PDF");
    }
    try {
      return HSvgPdfExporter.mergeSvgsToPdf(List.of(svgXml));
    } catch (HException e) {
      throw new HopException("Unable to transcode SVG to PDF", e);
    }
  }
}
