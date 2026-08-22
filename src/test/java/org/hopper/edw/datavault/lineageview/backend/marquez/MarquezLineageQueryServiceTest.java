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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.lineageview.backend.ILineageQueryService;
import org.hopper.edw.datavault.lineageview.backend.LineageBackendKind;
import org.hopper.edw.datavault.lineageview.backend.LineageGraph;
import org.hopper.edw.datavault.lineageview.backend.LineageQuery;
import org.hopper.edw.datavault.lineageview.backend.OpenLineageRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarquezLineageQueryServiceTest {

  private HttpServer server;
  private int port;
  private final AtomicReference<String> lastPath = new AtomicReference<>();
  private byte[] lineageBody;
  private byte[] datasetBody;

  @BeforeEach
  void start() throws IOException {
    lineageBody = readResource("lineage-f_orders.json");
    datasetBody =
        """
        {"namespace":"retail-dataset","name":"f_orders","fields":[{"name":"order_amount"}],
         "facets":{"hop_location":{"kind":"DATABASE","tableName":"f_orders","catalogConnection":"edw-catalog"}}}
        """
            .getBytes(StandardCharsets.UTF_8);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          lastPath.set(exchange.getRequestURI().toString());
          String path = exchange.getRequestURI().getPath();
          byte[] body;
          int status = 200;
          if (path.endsWith("/api/v1/lineage")) {
            body = lineageBody;
          } else if (path.contains("/datasets/")) {
            body = datasetBody;
          } else if (path.contains("/api/v1/search")) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("filter=job")) {
              body =
                  "{\"results\":[{\"namespace\":\"retail-job\",\"name\":\"dm/retail-pos/f_orders\",\"type\":\"JOB\"}]}"
                      .getBytes(StandardCharsets.UTF_8);
            } else {
              body =
                  "{\"results\":[{\"namespace\":\"retail-dataset\",\"name\":\"f_orders\",\"type\":\"DATASET\"}]}"
                      .getBytes(StandardCharsets.UTF_8);
            }
          } else {
            status = 404;
            body = "{}".getBytes(StandardCharsets.UTF_8);
          }
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    port = server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void fetchGraphUsesDatasetSeedAndDoesNotCopyLatestRunDuration() throws Exception {
    try (MarquezLineageQueryService service = service()) {
      assertEquals(LineageBackendKind.MARQUEZ, service.kind());
      assertFalse(service.facetsInlineOnGraph());
      LineageGraph graph =
          service.fetchGraph(
              LineageQuery.builder()
                  .dataset(
                      OpenLineageRef.builder().namespace("retail-dataset").name("f_orders").build())
                  .depth(6)
                  .build());
      assertEquals("dataset:retail-dataset:f_orders", graph.getSeedNodeId());
      assertEquals(5, graph.getNodesOrEmpty().size());
      assertNotNull(graph.findNode("job:retail-job:dm/retail-pos/f_orders"));
      assertTrue(
          graph.getNodesOrEmpty().stream().noneMatch(n -> n.getHopOps() != null),
          "export duration must not appear as hopOps");
      assertNotNull(graph.findNode(graph.getSeedNodeId()).getHopLocation());
    }
  }

  @Test
  void fetchGraphSearchesWhenExactNamespaceMisses() throws Exception {
    try (MarquezLineageQueryService service = service()) {
      LineageGraph graph =
          service.fetchGraph(
              LineageQuery.builder()
                  .job(
                      OpenLineageRef.builder()
                          .namespace("hop-data-vault/retail-example")
                          .name("dm/retail-pos/f_orders")
                          .build())
                  .depth(6)
                  .build());
      assertEquals("job:retail-job:dm/retail-pos/f_orders", graph.getSeedNodeId());
    }
  }

  @Test
  void missingSeedThrowsSeedNotFound() {
    try (MarquezLineageQueryService service = service()) {
      HopException error =
          assertThrows(
              HopException.class,
              () ->
                  service.fetchGraph(
                      LineageQuery.builder()
                          .dataset(
                              OpenLineageRef.builder().namespace("missing").name("nope").build())
                          .build()));
      assertTrue(error.getMessage().contains(ILineageQueryService.SEED_NOT_FOUND));
    }
  }

  @Test
  void searchWrapsHintAsLikePattern() throws Exception {
    try (MarquezLineageQueryService service = service()) {
      List<OpenLineageRef> found = service.searchDatasets("orders");
      assertEquals(1, found.size());
      assertEquals("f_orders", found.get(0).getName());
      assertTrue(lastPath.get().contains("q=%25orders%25"), lastPath.get());
    }
  }

  private MarquezLineageQueryService service() {
    return new MarquezLineageQueryService("http://127.0.0.1:" + port, null, null, 5_000);
  }

  private static byte[] readResource(String name) throws IOException {
    try (InputStream in = MarquezLineageQueryServiceTest.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IOException("missing " + name);
      }
      return in.readAllBytes();
    }
  }
}
