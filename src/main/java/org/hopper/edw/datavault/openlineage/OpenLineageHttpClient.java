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
package org.hopper.edw.datavault.openlineage;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;

/**
 * Posts single OpenLineage {@code RunEvent} documents to an HTTP endpoint (Marquez {@code POST
 * /api/v1/lineage}, Collibra OL-compatible endpoints, etc.).
 */
public final class OpenLineageHttpClient {

  private final String url;
  private final String apiKeyHeader;
  private final String apiKey;
  private final int timeoutMs;
  private final HttpClient httpClient;

  public OpenLineageHttpClient(String url, String apiKeyHeader, String apiKey, int timeoutMs) {
    this.url = url;
    this.apiKeyHeader = apiKeyHeader;
    this.apiKey = apiKey;
    this.timeoutMs = timeoutMs > 0 ? timeoutMs : 30_000;
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(this.timeoutMs)).build();
  }

  public void postEvent(ObjectNode event) throws HopException {
    if (Utils.isEmpty(url)) {
      throw new HopException("OpenLineage HTTP URL is required");
    }
    if (event == null) {
      throw new HopException("OpenLineage event is required");
    }
    try {
      String body = OpenLineageSnapshotMapper.toCompactJson(event);
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .uri(URI.create(url.trim()))
              .timeout(Duration.ofMillis(timeoutMs))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
      if (!Utils.isEmpty(apiKey) && !Utils.isEmpty(apiKeyHeader)) {
        builder.header(apiKeyHeader.trim(), apiKey);
      } else if (!Utils.isEmpty(apiKey)) {
        builder.header("Authorization", apiKey);
      }
      HttpResponse<String> response =
          httpClient.send(
              builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        String responseBody = response.body();
        String snippet =
            responseBody == null
                ? ""
                : responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody;
        throw new HopException(
            "OpenLineage HTTP POST failed with status " + status + " for " + url + ": " + snippet);
      }
    } catch (HopException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HopException("OpenLineage HTTP POST interrupted: " + url, e);
    } catch (Exception e) {
      throw new HopException("Unable to POST OpenLineage event to " + url, e);
    }
  }
}
