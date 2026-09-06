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

import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.documentation.DocumentationSite;

/** Writes theming.html describing the generated CSS contract. */
public final class ThemingWriter {

  private ThemingWriter() {}

  public static void write(DocumentationSite site) throws HopException {
    String body =
        """
        <p>This documentation set is restyled by replacing <code>assets/css/hop-doc.css</code>.
        Sample themes ship in <code>assets/css/themes/</code>. <code>assets/css/print.css</code>
        is always linked with <code>media="print"</code>.</p>
        <h2>Selecting a sample</h2>
        <p>Regenerate with <code>--theme default</code>, <code>compact</code>, or
        <code>high-contrast</code>, or copy one of:</p>
        <ul>
          <li><code>assets/css/themes/default.css</code></li>
          <li><code>assets/css/themes/compact.css</code></li>
          <li><code>assets/css/themes/high-contrast.css</code></li>
        </ul>
        <h2>Custom properties</h2>
        <p>Override these on <code>:root</code> and <code>html[data-theme="dark"]</code>
        (system theme also follows <code>prefers-color-scheme</code>):</p>
        <pre class="hop-doc-theming-sample">--hop-doc-bg
--hop-doc-fg
--hop-doc-muted
--hop-doc-header-bg
--hop-doc-header-fg
--hop-doc-sidebar-bg
--hop-doc-link
--hop-doc-link-hover
--hop-doc-border
--hop-doc-code-bg
--hop-doc-note-bg
--hop-doc-badge-bg
--hop-doc-badge-fg
--hop-doc-focus
--hop-doc-svg-viewport-bg
--hop-doc-font-sans
--hop-doc-font-mono
--hop-doc-sidebar-width
--hop-doc-radius
--hop-doc-header-height</pre>
        <h2>Stable classes</h2>
        <table class="hop-doc-meta-table">
        <thead><tr><th>Class</th><th>Role</th></tr></thead>
        <tbody>
        <tr><td><code>.hop-doc-page</code></td><td>Body wrapper</td></tr>
        <tr><td><code>.hop-doc-header</code></td><td>Sticky header</td></tr>
        <tr><td><code>.hop-doc-breadcrumb</code></td><td>Project / type / folder / page trail</td></tr>
        <tr><td><code>.hop-doc-brand</code></td><td>Project name (home link in the breadcrumb)</td></tr>
        <tr><td><code>.hop-doc-header-tools</code></td><td>Right-side search and theme combo</td></tr>
        <tr><td><code>.hop-doc-search</code></td><td>Search form</td></tr>
        <tr><td><code>.hop-doc-search-results</code></td><td>Live results panel</td></tr>
        <tr><td><code>.hop-doc-theme-toggle</code></td><td>System / light / dark <code>select</code></td></tr>
        <tr><td><code>.hop-doc-sidebar</code> / <code>.hop-doc-nav</code></td><td>Navigation tree (type groups collapsed by default; expand state is remembered)</td></tr>
        <tr><td><code>.hop-doc-nav-group</code> / <code>.hop-doc-nav-folder</code></td><td>Type and folder nodes (<code>details</code>/<code>summary</code>)</td></tr>
        <tr><td><code>.hop-doc-main</code></td><td>Page content</td></tr>
        <tr><td><code>.hop-doc-badge</code></td><td>Object-kind chip</td></tr>
        <tr><td><code>.hop-doc-meta-table</code></td><td>Details and mappings</td></tr>
        <tr><td><code>.hop-doc-note</code></td><td>Rendered Markdown note</td></tr>
        <tr><td><code>.hop-doc-svg-viewport</code> / <code>.hop-doc-svg-stage</code></td><td>Zoomable diagram</td></tr>
        <tr><td><code>.hop-doc-svg-light</code> / <code>.hop-doc-svg-dark</code></td><td>Theme-swapped SVGs</td></tr>
        <tr><td><code>.hop-doc-footer</code></td><td>Generator footer</td></tr>
        </tbody></table>
        <h2>Search index</h2>
        <p><code>assets/js/search-index.js</code> assigns <code>window.HOP_DOC_INDEX</code>
        (not JSON) so search works from <code>file://</code>. Hit maps live in
        <code>assets/js/hits/</code> as <code>window.HOP_DOC_HITS</code>.</p>
        """;
    PageSupport.writePage(site, "theming.html", "Theming", "CSS", body);
  }
}
