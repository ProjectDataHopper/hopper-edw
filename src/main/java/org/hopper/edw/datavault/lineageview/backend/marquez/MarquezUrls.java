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
package org.hopper.edw.datavault.lineageview.backend.marquez;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.apache.hop.core.util.Utils;

/**
 * Marquez 0.50 URL helpers. Call {@code /api/v1/...} only. Strip a pasted {@code ${MARQUEZ_API}}
 * that already includes {@code /api/v1/lineage}.
 */
public final class MarquezUrls {

  private MarquezUrls() {}

  /**
   * Host root without a trailing slash. Accepts {@code http://localhost:5001} or {@code
   * http://localhost:5001/api/v1/lineage}.
   */
  public static String normalizeBaseUrl(String raw) {
    if (Utils.isEmpty(raw)) {
      return raw;
    }
    String url = raw.trim();
    while (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    if (url.endsWith("/api/v1-beta/lineage")) {
      url = url.substring(0, url.length() - "/api/v1-beta/lineage".length());
    } else if (url.endsWith("/api/v1/lineage")) {
      url = url.substring(0, url.length() - "/api/v1/lineage".length());
    } else if (url.endsWith("/api/v1-beta")) {
      url = url.substring(0, url.length() - "/api/v1-beta".length());
    } else if (url.endsWith("/api/v1")) {
      url = url.substring(0, url.length() - "/api/v1".length());
    }
    while (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    return url;
  }

  public static String lineageUrl(String baseUrl, String nodeId, int depth) {
    String base = normalizeBaseUrl(baseUrl);
    int n = depth > 0 ? depth : 6;
    return base + "/api/v1/lineage?nodeId=" + encodeQuery(nodeId) + "&depth=" + n;
  }

  public static String namespacesUrl(String baseUrl) {
    return normalizeBaseUrl(baseUrl) + "/api/v1/namespaces";
  }

  public static String searchUrl(String baseUrl, String nameHint, String filter) {
    return normalizeBaseUrl(baseUrl)
        + "/api/v1/search?q="
        + encodeQuery(searchQuery(nameHint))
        + "&filter="
        + encodeQuery(filter)
        + "&limit=100";
  }

  public static String jobUrl(String baseUrl, String namespace, String jobName) {
    return normalizeBaseUrl(baseUrl)
        + "/api/v1/namespaces/"
        + encodePathSegment(namespace)
        + "/jobs/"
        + encodePathSegment(jobName);
  }

  public static String datasetUrl(String baseUrl, String namespace, String datasetName) {
    return normalizeBaseUrl(baseUrl)
        + "/api/v1/namespaces/"
        + encodePathSegment(namespace)
        + "/datasets/"
        + encodePathSegment(datasetName);
  }

  public static String runFacetsUrl(String baseUrl, String runId) {
    return normalizeBaseUrl(baseUrl)
        + "/api/v1/jobs/runs/"
        + encodePathSegment(runId)
        + "/facets?type=run";
  }

  /**
   * Marquez search is SQL {@code LIKE}. A raw {@code q=orders} is an exact match. Wrap with {@code
   * %} unless the hint already has wildcards. Blank → {@code %}.
   */
  public static String searchQuery(String nameHint) {
    if (Utils.isEmpty(nameHint)) {
      return "%";
    }
    String hint = nameHint.trim();
    if (hint.indexOf('%') >= 0 || hint.indexOf('_') >= 0) {
      return hint;
    }
    return "%" + hint + "%";
  }

  static String encodeQuery(String value) {
    if (value == null) {
      return "";
    }
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  static String encodePathSegment(String value) {
    if (value == null) {
      return "";
    }
    // Encode the whole segment so slashes in job names become %2F.
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
