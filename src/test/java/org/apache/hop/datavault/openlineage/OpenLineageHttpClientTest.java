/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hop.datavault.openlineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenLineageHttpClientTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpServer server;
  private final AtomicInteger postCount = new AtomicInteger();
  private final AtomicReference<String> lastBody = new AtomicReference<>();
  private final AtomicReference<String> lastAuth = new AtomicReference<>();
  private int port;

  @BeforeEach
  void startServer() throws IOException {
    postCount.set(0);
    lastBody.set(null);
    lastAuth.set(null);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/lineage",
        exchange -> {
          if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
          }
          postCount.incrementAndGet();
          lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          lastBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] ok = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(201, ok.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(ok);
          }
        });
    server.start();
    port = server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void postsSingleRunEventWithOptionalAuth() throws Exception {
    ObjectNode event = MAPPER.createObjectNode();
    event.put("eventType", "COMPLETE");
    ObjectNode job = MAPPER.createObjectNode();
    job.put("namespace", "hop-data-vault");
    job.put("name", "dv/test/hub_customer");
    event.set("job", job);

    OpenLineageHttpClient client =
        new OpenLineageHttpClient(
            "http://127.0.0.1:" + port + "/api/v1/lineage",
            "Authorization",
            "Bearer test-token",
            5000);
    client.postEvent(event);

    assertEquals(1, postCount.get());
    assertEquals("Bearer test-token", lastAuth.get());
    JsonNode body = MAPPER.readTree(lastBody.get());
    assertTrue(body.isObject(), "Marquez expects a single RunEvent object, not an array");
    assertEquals("COMPLETE", body.path("eventType").asText());
    assertEquals("dv/test/hub_customer", body.path("job").path("name").asText());
  }

  @Test
  void non2xxThrowsHopException() {
    server.createContext(
        "/fail",
        exchange -> {
          byte[] msg = "nope".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(500, msg.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(msg);
          }
        });
    OpenLineageHttpClient client =
        new OpenLineageHttpClient("http://127.0.0.1:" + port + "/fail", null, null, 5000);
    ObjectNode event = MAPPER.createObjectNode();
    event.put("eventType", "COMPLETE");
    HopException ex = assertThrows(HopException.class, () -> client.postEvent(event));
    assertTrue(ex.getMessage().contains("500"));
  }
}
