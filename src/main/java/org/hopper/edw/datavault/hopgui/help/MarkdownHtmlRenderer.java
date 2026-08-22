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
package org.hopper.edw.datavault.hopgui.help;

import org.apache.hop.core.util.Utils;
import org.hopper.edw.datavault.hopgui.markdown.CommonMarkSupport;
import org.apache.hop.ui.core.PropsUi;

/** Converts markdown to HTML using CommonMark (mirrors hop-transform-textfile preview). */
public final class MarkdownHtmlRenderer {

  private MarkdownHtmlRenderer() {}

  public static String toHtmlDocument(String title, String markdown) {
    String safeTitle = Utils.isEmpty(title) ? "Help" : escapeHtml(title);
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <title>%s</title>
        <style>
        %s
        </style>
        </head>
        <body>
        %s
        </body>
        </html>
        """
        .formatted(safeTitle, documentStyles(), toHtmlBody(markdown));
  }

  static String toHtmlBody(String markdown) {
    return CommonMarkSupport.toHtmlBody(markdown);
  }

  private static String documentStyles() {
    StringBuilder css = new StringBuilder();
    css.append(
        """
        body {
          font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
          line-height: 1.625;
          margin: 40px auto;
          max-width: 800px;
          padding: 0 20px;
        }
        h1, h2, h3, h4, h5, h6 {
          font-weight: 600;
        }
        h1 {
          font-size: 2.25rem;
          padding-bottom: 0.3em;
          border-bottom: 1px solid;
        }
        h2 {
          font-size: 1.5rem;
          padding-bottom: 0.3em;
          border-bottom: 1px solid;
        }
        a {
          text-decoration: none;
          font-weight: 500;
        }
        a:hover {
          text-decoration: underline;
        }
        pre, code {
          font-family: SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace;
          font-size: 0.9em;
          border-radius: 6px;
        }
        code {
          padding: 0.2em 0.4em;
        }
        pre {
          padding: 16px;
          overflow-x: auto;
        }
        pre code {
          padding: 0;
          background-color: transparent;
          border-radius: 0;
          border: none;
        }
        blockquote {
          margin: 1.5em 0;
          padding: 0.5em 1em;
          border-left-width: 4px;
          border-left-style: solid;
        }
        table {
          width: 100%;
          margin: 1.5em 0;
          border-collapse: collapse;
          border-radius: 6px;
          overflow: hidden;
          font-size: 0.95em;
        }
        th, td {
          padding: 0.6em 0.85em;
          border: 1px solid;
          text-align: left;
        }
        th {
          font-weight: 600;
        }
        ul, ol {
          margin: 0.75em 0;
          padding-left: 1.5em;
        }
        """);
    if (isDarkMode()) {
      css.append(
          """
          body {
            background-color: #0b0f19;
            color: #94a3b8;
          }
          h1, h2, h3, h4, h5, h6 {
            color: #f8fafc;
          }
          h1, h2 {
            border-bottom-color: #1e293b;
          }
          a {
            color: #38bdf8;
          }
          pre, code {
            background-color: #1e293b;
            border: 1px solid #334155;
            color: #e2e8f0;
          }
          blockquote {
            border-left-color: #475569;
            color: #94a3b8;
            background-color: #0f172a;
          }
          table {
            background-color: #0f172a;
          }
          th, td {
            border-color: #334155;
          }
          th {
            background-color: #1e293b;
            color: #f8fafc;
          }
          tbody tr:nth-child(even) {
            background-color: #111827;
          }
          """);
    } else {
      css.append(
          """
          body {
            background-color: #f8fafc;
            color: #334155;
          }
          h1, h2, h3, h4, h5, h6 {
            color: #0f172a;
          }
          h1, h2 {
            border-bottom-color: #e2e8f0;
          }
          a {
            color: #2563eb;
          }
          pre, code {
            background-color: #f1f5f9;
            border: 1px solid #e2e8f0;
            color: #334155;
          }
          blockquote {
            border-left-color: #cbd5e1;
            color: #64748b;
            background-color: #f8fafc;
          }
          table {
            background-color: #ffffff;
          }
          th, td {
            border-color: #e2e8f0;
          }
          th {
            background-color: #f1f5f9;
            color: #0f172a;
          }
          tbody tr:nth-child(even) {
            background-color: #f8fafc;
          }
          """);
    }
    return css.toString();
  }

  private static boolean isDarkMode() {
    try {
      return PropsUi.getInstance().isDarkMode();
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static String escapeHtml(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    StringBuilder escaped = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      switch (ch) {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '"' -> escaped.append("&quot;");
        default -> escaped.append(ch);
      }
    }
    return escaped.toString();
  }
}
