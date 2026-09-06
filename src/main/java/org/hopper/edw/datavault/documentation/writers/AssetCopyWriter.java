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
package org.hopper.edw.datavault.documentation.writers;

import java.nio.charset.StandardCharsets;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.documentation.DocumentationSite;
import org.hopper.edw.datavault.documentation.ProjectDocumentationOptions;
import org.hopper.edw.datavault.documentation.render.DocumentationIo;

/** Copies bundled CSS/JS into the output folder. */
public final class AssetCopyWriter {

  private static final String BASE = "/org/hopper/edw/datavault/documentation/assets/";

  private AssetCopyWriter() {}

  public static void write(DocumentationSite site) throws HopException {
    copy(site, "css/themes/default.css");
    copy(site, "css/themes/compact.css");
    copy(site, "css/themes/high-contrast.css");
    copy(site, "css/print.css");
    copy(site, "js/hop-doc.js");
    copy(site, "js/search.js");
    copy(site, "js/svg-viewer.js");

    String theme = site.getOptions().resolvedTheme();
    byte[] selected = DocumentationIo.readClasspath(BASE + "css/themes/" + theme + ".css");
    String css = new String(selected, StandardCharsets.UTF_8);
    if (!ProjectDocumentationOptions.THEME_DEFAULT.equals(theme)) {
      css = css.replace("@import url(\"default.css\");", "@import url(\"themes/default.css\");");
    }
    DocumentationIo.writeUtf8(
        DocumentationIo.child(site.getTargetRoot(), "assets/css/hop-doc.css"), css);
  }

  private static void copy(DocumentationSite site, String relative) throws HopException {
    byte[] bytes = DocumentationIo.readClasspath(BASE + relative);
    DocumentationIo.writeBytes(
        DocumentationIo.child(site.getTargetRoot(), "assets/" + relative), bytes);
  }
}
