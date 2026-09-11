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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches OAuth2 access tokens ({@code client_credentials} / {@code refresh_token})
 * using JDK HTTP only.
 */
final class HsmOAuth2Tokens {

  private static final long SAFETY_MARGIN_MS = 30_000L;
  private static final long DEFAULT_LIFETIME_MS = 300_000L;

  private static final ConcurrentHashMap<String, Cached> CACHE = new ConcurrentHashMap<>();

  private HsmOAuth2Tokens() {}

  static String accessToken(HopHsmJdbcDriver.ParsedUrl parsed) throws SQLException {
    return accessToken(parsed, System.currentTimeMillis());
  }

  static String accessToken(HopHsmJdbcDriver.ParsedUrl parsed, long nowMs) throws SQLException {
    String key = cacheKey(parsed);
    Cached cached = CACHE.get(key);
    if (cached != null && cached.usableUntilMs > nowMs) {
      return cached.token;
    }
    synchronized (lockFor(key)) {
      cached = CACHE.get(key);
      if (cached != null && cached.usableUntilMs > nowMs) {
        return cached.token;
      }
      Cached fetched = fetch(parsed, nowMs);
      CACHE.put(key, fetched);
      return fetched.token;
    }
  }

  static void invalidate(HopHsmJdbcDriver.ParsedUrl parsed) {
    CACHE.remove(cacheKey(parsed));
  }

  static void clearCache() {
    CACHE.clear();
  }

  private static Cached fetch(HopHsmJdbcDriver.ParsedUrl parsed, long nowMs) throws SQLException {
    if (parsed.oauthTokenUrl() == null || parsed.oauthTokenUrl().isBlank()) {
      throw new SQLException("oauth2 requires oauthTokenUrl");
    }
    String grant =
        parsed.oauthGrant() == null || parsed.oauthGrant().isBlank()
            ? "client_credentials"
            : parsed.oauthGrant().trim();
    String clientId = firstNonEmpty(parsed.oauthClientId(), parsed.user());
    String clientSecret = firstNonEmpty(parsed.oauthClientSecret(), parsed.password());
    try {
      Map<String, String> form = new LinkedHashMap<>();
      form.put("grant_type", grant);
      if ("refresh_token".equalsIgnoreCase(grant)) {
        if (parsed.oauthRefreshToken() == null || parsed.oauthRefreshToken().isBlank()) {
          throw new SQLException("oauth2 refresh_token grant requires oauthRefreshToken");
        }
        form.put("refresh_token", parsed.oauthRefreshToken());
      }
      if (parsed.oauthScope() != null && !parsed.oauthScope().isBlank()) {
        form.put("scope", parsed.oauthScope());
      }
      boolean credentialsInBody = parsed.oauthCredentialsInBody();
      if (credentialsInBody) {
        if (clientId != null) {
          form.put("client_id", clientId);
        }
        if (clientSecret != null) {
          form.put("client_secret", clientSecret);
        }
      }
      byte[] body = HsmJson.encodeForm(form).getBytes(StandardCharsets.UTF_8);
      HttpURLConnection conn = (HttpURLConnection) new URL(parsed.oauthTokenUrl()).openConnection();
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      conn.setConnectTimeout(parsed.connectTimeoutMs());
      conn.setReadTimeout(parsed.readTimeoutMs());
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
      conn.setRequestProperty("Accept", "application/json");
      if (!credentialsInBody && clientId != null) {
        String basic =
            Base64.getEncoder()
                .encodeToString(
                    (clientId + ":" + (clientSecret != null ? clientSecret : ""))
                        .getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + basic);
      }
      conn.setFixedLengthStreamingMode(body.length);
      try (OutputStream out = conn.getOutputStream()) {
        out.write(body);
      }
      int status = conn.getResponseCode();
      InputStream in =
          status >= 400
              ? (conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream())
              : conn.getInputStream();
      String json = readAll(in);
      Map<String, Object> map = HsmJson.asObject(HsmJson.parse(json));
      if (map == null) {
        throw new SQLException("OAuth2 token endpoint returned invalid JSON (HTTP " + status + ")");
      }
      String token = HsmJson.str(map, "access_token");
      if (token == null || token.isBlank()) {
        String err = HsmJson.str(map, "error");
        String desc = HsmJson.str(map, "error_description");
        throw new SQLException(
            "OAuth2 token request failed (HTTP "
                + status
                + ")"
                + (err != null ? ": " + err : "")
                + (desc != null ? " — " + desc : ""));
      }
      int expiresIn = HsmJson.integer(map, "expires_in", (int) (DEFAULT_LIFETIME_MS / 1000));
      long usableUntil = nowMs + Math.max(1_000L, expiresIn * 1000L - SAFETY_MARGIN_MS);
      return new Cached(token, usableUntil);
    } catch (SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new SQLException("OAuth2 token request failed: " + e.getMessage(), e);
    }
  }

  private static String cacheKey(HopHsmJdbcDriver.ParsedUrl parsed) {
    return String.join(
        "\n",
        nvl(parsed.oauthTokenUrl()),
        nvl(parsed.oauthGrant()),
        nvl(firstNonEmpty(parsed.oauthClientId(), parsed.user())),
        nvl(parsed.oauthScope()),
        nvl(parsed.oauthRefreshToken()));
  }

  private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

  private static Object lockFor(String key) {
    return LOCKS.computeIfAbsent(key, k -> new Object());
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }

  private static String firstNonEmpty(String a, String b) {
    return a != null && !a.isEmpty() ? a : b;
  }

  private static String readAll(InputStream in) throws Exception {
    if (in == null) {
      return "";
    }
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int n;
    while ((n = in.read(chunk)) >= 0) {
      buf.write(chunk, 0, n);
    }
    return buf.toString(StandardCharsets.UTF_8);
  }

  private record Cached(String token, long usableUntilMs) {}
}
