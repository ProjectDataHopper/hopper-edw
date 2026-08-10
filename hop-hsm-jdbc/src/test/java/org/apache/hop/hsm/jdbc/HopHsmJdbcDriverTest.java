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
package org.apache.hop.hsm.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class HopHsmJdbcDriverTest {

  @Test
  void acceptsOnlyRemoteUrls() {
    HopHsmJdbcDriver driver = new HopHsmJdbcDriver();
    assertTrue(driver.acceptsURL("jdbc:hop-hsm://u:p@localhost:8182"));
    assertTrue(driver.acceptsURL("jdbc:hop-hsm://u:p@localhost:8182/crm"));
    assertTrue(driver.acceptsURL("jdbc:hop-hsm:https://host/hop/sourceModelData?schema=x"));
    assertFalse(driver.acceptsURL("jdbc:hop-hsm:file=/tmp/a.hsm"));
    assertFalse(driver.acceptsURL("jdbc:postgresql://x/y"));
  }

  @Test
  void parseUrlMinimalNoSchema() throws Exception {
    HopHsmJdbcDriver.ParsedUrl p =
        HopHsmJdbcDriver.parse("jdbc:hop-hsm://alice:s3cret@hop.example:8182", new Properties());
    assertEquals("http://hop.example:8182/hop/sourceModelData", p.endpointUrl());
    assertNull(p.defaultSchema());
    assertEquals("alice", p.user());
    assertEquals("s3cret", p.password());
  }

  @Test
  void parseUrlPathAsSchema() throws Exception {
    HopHsmJdbcDriver.ParsedUrl p =
        HopHsmJdbcDriver.parse("jdbc:hop-hsm://host:8182/crm", new Properties());
    assertEquals("http://host:8182/hop/sourceModelData", p.endpointUrl());
    assertEquals("crm", p.defaultSchema());
  }

  @Test
  void parseUrlQuerySchema() throws Exception {
    HopHsmJdbcDriver.ParsedUrl p =
        HopHsmJdbcDriver.parse(
            "jdbc:hop-hsm://host:8182/hop/sourceModelData?schema=retail&rowLimit=50",
            new Properties());
    assertEquals("http://host:8182/hop/sourceModelData", p.endpointUrl());
    assertEquals("retail", p.defaultSchema());
    assertEquals(50, p.rowLimit());
  }

  @Test
  void parseLegacyModelName() throws Exception {
    HopHsmJdbcDriver.ParsedUrl p =
        HopHsmJdbcDriver.parse(
            "jdbc:hop-hsm://host:8182/hop/sourceModelData?modelName=crm", new Properties());
    assertEquals("crm", p.defaultSchema());
  }

  @Test
  void parseHttpsAndProperties() throws Exception {
    Properties info = new Properties();
    info.setProperty("user", "u");
    info.setProperty("password", "p");
    info.setProperty("schema", "fromProp");
    HopHsmJdbcDriver.ParsedUrl p =
        HopHsmJdbcDriver.parse("jdbc:hop-hsm:https://host:9443", info);
    assertEquals("https://host:9443/hop/sourceModelData", p.endpointUrl());
    assertEquals("fromProp", p.defaultSchema());
    assertEquals("u", p.user());
  }

  @Test
  void jsonRoundTripQueryShape() throws Exception {
    String json =
        "{\"ok\":true,\"v\":1,\"truncated\":false,\"columns\":[{\"n\":\"id\",\"t\":\"BIGINT\",\"j\":-5},{\"n\":\"name\",\"t\":\"VARCHAR\",\"j\":12}],\"rows\":[[1,\"alice\"],[2,\"bob\"]]}";
    Map<String, Object> map = HsmJson.asObject(HsmJson.parse(json));
    assertTrue(HsmJson.bool(map, "ok", false));
    HopHsmJdbcResultSet rs = HopHsmJdbcResultSet.fromQueryResponse(null, map);
    assertTrue(rs.next());
    assertEquals(1L, rs.getLong(1));
    assertEquals("alice", rs.getString("name"));
    assertTrue(rs.next());
    assertEquals("bob", rs.getString(2));
    assertFalse(rs.next());
  }

  @Test
  void jsonParsesSchemasAsServices() {
    String json =
        "{\"ok\":true,\"v\":1,\"schemas\":[{\"n\":\"crm\",\"remarks\":\"Retail CRM\"},{\"n\":\"erp\",\"remarks\":\"\"}]}";
    Map<String, Object> map = HsmJson.asObject(HsmJson.parse(json));
    List<Object> schemas = HsmJson.asArray(map.get("schemas"));
    assertEquals(2, schemas.size());
    assertEquals("crm", HsmJson.str(HsmJson.asObject(schemas.get(0)), "n"));
  }

  @Test
  void normalizeTableTypeMapsCustomKindsForDBeaver() {
    assertEquals("VIEW", HopHsmJdbcDatabaseMetaData.normalizeTableType("JSON"));
    assertEquals("VIEW", HopHsmJdbcDatabaseMetaData.normalizeTableType("PIPELINE"));
    assertEquals("TABLE", HopHsmJdbcDatabaseMetaData.normalizeTableType("TABLE"));
  }
}
