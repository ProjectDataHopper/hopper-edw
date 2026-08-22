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
package org.hopper.edw.datavault.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rewrite generated Asciidoctor HTML so published docs navigate as HTML.
 *
 * <p>AsciiDoc {@code link:page.adoc[page.adoc]} is a hard URL, so HTML keeps the {@code .adoc}
 * suffix on both the href and the visible label. This pass:
 *
 * <ul>
 *   <li>href {@code page.adoc} / {@code page.adoc#id} → {@code page.html} / {@code page.html#id}
 *   <li>link text {@code page.adoc} → {@code page} ({@code architecture.adoc D1} → {@code
 *       architecture D1})
 * </ul>
 *
 * <p>Invoked from Maven {@code prepare-package} with the generated-docs directory. Implemented in
 * Java so CI agents need only a JDK (Jenkins {@code hop-build} has no {@code python3}).
 */
public final class RewriteAdocHrefs {

  static final Pattern HREF_ADOC =
      Pattern.compile(
          "(?<prefix>href\\s*=\\s*(?<q>['\"]))(?<path>[^'\"]+?)\\.adoc(?<frag>#[^'\"]*)?\\k<q>",
          Pattern.CASE_INSENSITIVE);
  static final Pattern LINK_TEXT_ADOC =
      Pattern.compile(
          "(?<open><a\\b[^>]*>)(?<text>[^<]*?)\\.adoc(?<rest>[^<]*)(?<close></a>)",
          Pattern.CASE_INSENSITIVE);

  private RewriteAdocHrefs() {}

  public static String rewrite(String html, int[] counts) {
    Matcher href = HREF_ADOC.matcher(html);
    StringBuffer hrefOut = new StringBuffer();
    int nHref = 0;
    while (href.find()) {
      String frag = href.group("frag") == null ? "" : href.group("frag");
      href.appendReplacement(
          hrefOut,
          Matcher.quoteReplacement(
              href.group("prefix") + href.group("path") + ".html" + frag + href.group("q")));
      nHref++;
    }
    href.appendTail(hrefOut);

    Matcher text = LINK_TEXT_ADOC.matcher(hrefOut.toString());
    StringBuffer textOut = new StringBuffer();
    int nText = 0;
    while (text.find()) {
      text.appendReplacement(
          textOut,
          Matcher.quoteReplacement(
              text.group("open") + text.group("text") + text.group("rest") + text.group("close")));
      nText++;
    }
    text.appendTail(textOut);
    if (counts != null && counts.length >= 1) {
      counts[0] = nHref + nText;
    }
    return textOut.toString();
  }

  public static int rewriteDirectory(Path root) throws IOException {
    if (!Files.isDirectory(root)) {
      throw new IOException("Not a directory: " + root);
    }
    int files = 0;
    int total = 0;
    try (Stream<Path> walk = Files.walk(root)) {
      for (Path path : walk.filter(p -> p.toString().endsWith(".html")).sorted().toList()) {
        String original = Files.readString(path, StandardCharsets.UTF_8);
        int[] counts = new int[1];
        String updated = rewrite(original, counts);
        if (counts[0] > 0) {
          Files.writeString(path, updated, StandardCharsets.UTF_8);
          files++;
          total += counts[0];
        }
      }
    }
    System.out.println(
        "Rewrote " + total + " .adoc href/label(s) in " + files + " HTML file(s) under " + root);
    return total;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("Usage: RewriteAdocHrefs <generated-docs-dir>");
      System.exit(1);
    }
    Path root = Path.of(args[0]);
    if (!Files.isDirectory(root)) {
      System.out.println("Skipping href rewrite (no directory): " + root);
      return;
    }
    rewriteDirectory(root);
  }
}
