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
package org.apache.hop.datavault.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DataVaultConfigTest {

  @Test
  void defaultsLiveUpdatePollIntervalToTenSeconds() {
    DataVaultConfig config = new DataVaultConfig();

    assertEquals(10, config.getLiveUpdatePollIntervalSeconds());
    assertEquals(10_000L, config.resolveLiveUpdatePollIntervalMs());
  }

  @Test
  void rejectsNonPositiveLiveUpdatePollInterval() {
    DataVaultConfig config = new DataVaultConfig();

    config.setLiveUpdatePollIntervalSeconds(0);
    assertEquals(
        DataVaultConfig.DEFAULT_LIVE_UPDATE_POLL_INTERVAL_SECONDS,
        config.getLiveUpdatePollIntervalSeconds());

    config.setLiveUpdatePollIntervalSeconds(-5);
    assertEquals(
        DataVaultConfig.DEFAULT_LIVE_UPDATE_POLL_INTERVAL_SECONDS,
        config.getLiveUpdatePollIntervalSeconds());
  }

  @Test
  void copiesLiveUpdatePollInterval() {
    DataVaultConfig source = new DataVaultConfig();
    source.setLiveUpdatePollIntervalSeconds(30);

    DataVaultConfig copy = new DataVaultConfig(source);

    assertEquals(30, copy.getLiveUpdatePollIntervalSeconds());
    assertEquals(30_000L, copy.resolveLiveUpdatePollIntervalMs());
  }

  @Test
  void defaultsSchemaRemediationFolder() {
    DataVaultConfig config = new DataVaultConfig();
    assertEquals(
        DataVaultConfig.DEFAULT_SCHEMA_REMEDIATION_FOLDER,
        config.getSchemaRemediationFolderOrDefault());

    config.setSchemaRemediationFolder("  ");
    assertEquals(
        DataVaultConfig.DEFAULT_SCHEMA_REMEDIATION_FOLDER,
        config.getSchemaRemediationFolderOrDefault());

    config.setSchemaRemediationFolder("${PROJECT_HOME}/remediation");
    assertEquals("${PROJECT_HOME}/remediation", config.getSchemaRemediationFolderOrDefault());
  }
}
