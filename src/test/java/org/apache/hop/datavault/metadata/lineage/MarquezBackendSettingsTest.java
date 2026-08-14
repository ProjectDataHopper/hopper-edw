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
package org.apache.hop.datavault.metadata.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarquezBackendSettingsTest {

  private HttpServer server;
  private int port;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/namespaces",
        exchange -> {
          byte[] body = "{\"namespaces\":[{},{}]}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
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
  void testConnectionCountsNamespaces() throws Exception {
    MarquezBackendSettings settings = new MarquezBackendSettings();
    settings.setBaseUrl("http://127.0.0.1:" + port + "/api/v1/lineage");
    LineageConnectionTestResult result = settings.testConnection(null, null, null);
    assertTrue(result.isOk());
    assertEquals(2, result.getDetailCount());
    assertTrue(result.getMessage().contains("127.0.0.1"));
  }

  @Test
  void parseTimeoutFallsBack() {
    assertEquals(30_000, MarquezBackendSettings.parseTimeout(null));
    assertEquals(5_000, MarquezBackendSettings.parseTimeout("5000"));
    assertEquals(30_000, MarquezBackendSettings.parseTimeout("nope"));
  }
}
