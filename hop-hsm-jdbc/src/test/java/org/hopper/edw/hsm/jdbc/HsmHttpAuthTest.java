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
package org.hopper.edw.hsm.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HsmHttpAuthTest {

  private HttpServer server;

  @AfterEach
  void stop() {
    HsmOAuth2Tokens.clearCache();
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void basicSendsAuthorizationHeader() throws Exception {
    List<String> auths = new ArrayList<>();
    startServlet(
        (ex) -> {
          auths.add(ex.getRequestHeaders().getFirst("Authorization"));
          byte[] body = "{\"ok\":true,\"v\":1}".getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    Properties info = new Properties();
    info.setProperty("user", "cluster");
    info.setProperty("password", "secret");
    HopHsmJdbcDriver.ParsedUrl parsed =
        HopHsmJdbcDriver.parse("jdbc:hop-hsm://127.0.0.1:" + port() + "/crm", info);
    new HsmHttpClient(parsed).ping();
    assertTrue(auths.get(0).startsWith("Basic "));
  }

  @Test
  void bearerSendsToken() throws Exception {
    List<String> auths = new ArrayList<>();
    startServlet(
        (ex) -> {
          auths.add(ex.getRequestHeaders().getFirst("Authorization"));
          byte[] body = "{\"ok\":true,\"v\":1}".getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    Properties info = new Properties();
    info.setProperty("authType", "bearer");
    info.setProperty("accessToken", "abc.def.ghi");
    HopHsmJdbcDriver.ParsedUrl parsed =
        HopHsmJdbcDriver.parse("jdbc:hop-hsm://127.0.0.1:" + port() + "/crm", info);
    new HsmHttpClient(parsed).ping();
    assertEquals("Bearer abc.def.ghi", auths.get(0));
  }

  @Test
  void oauth2FetchesTokenThenSendsBearer() throws Exception {
    AtomicInteger tokenHits = new AtomicInteger();
    List<String> servletAuths = new ArrayList<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/token",
        ex -> {
          tokenHits.incrementAndGet();
          byte[] body =
              "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}"
                  .getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "application/json");
          ex.sendResponseHeaders(200, body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    server.createContext(
        "/hop/sourceModelData",
        ex -> {
          servletAuths.add(ex.getRequestHeaders().getFirst("Authorization"));
          byte[] body = "{\"ok\":true,\"v\":1}".getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    server.start();
    Properties info = new Properties();
    info.setProperty("authType", "oauth2");
    info.setProperty("oauthTokenUrl", "http://127.0.0.1:" + port() + "/token");
    info.setProperty("oauthClientId", "id");
    info.setProperty("oauthClientSecret", "secret");
    HopHsmJdbcDriver.ParsedUrl parsed =
        HopHsmJdbcDriver.parse("jdbc:hop-hsm://127.0.0.1:" + port() + "/crm", info);
    HsmHttpClient client = new HsmHttpClient(parsed);
    client.ping();
    client.ping();
    assertEquals(1, tokenHits.get());
    assertEquals("Bearer tok-1", servletAuths.get(0));
    assertEquals("Bearer tok-1", servletAuths.get(1));
  }

  private void startServlet(com.sun.net.httpserver.HttpHandler handler) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/hop/sourceModelData", handler);
    server.start();
  }

  private int port() {
    return server.getAddress().getPort();
  }
}
