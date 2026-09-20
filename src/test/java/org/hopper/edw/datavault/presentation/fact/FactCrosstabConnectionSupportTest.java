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
package org.hopper.edw.datavault.presentation.fact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.variables.Variables;
import org.hopper.core.HDatabaseConnection;
import org.hopper.edw.datavault.metadata.ModelConfigurationTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FactCrosstabConnectionSupportTest {

  @BeforeAll
  static void initHop() throws Exception {
    HopEnvironment.init();
    ModelConfigurationTestSupport.registerTypes();
  }

  @Test
  void resolvesHopEnvironmentVariablesIncludingPassword() throws Exception {
    DatabaseMeta source =
        new DatabaseMeta(
            "Vault",
            "POSTGRESQL",
            "Native",
            "${DB_HOST}",
            "${DB_TARGET_NAME}",
            "${DB_PORT}",
            "${DB_USER}",
            "${DB_PASSWORD}");
    Variables variables = new Variables();
    variables.setVariable("DB_HOST", "localhost");
    variables.setVariable("DB_PORT", "54320");
    variables.setVariable("DB_USER", "test");
    variables.setVariable("DB_PASSWORD", "test");
    variables.setVariable("DB_TARGET_NAME", "test_edw");

    HDatabaseConnection connection =
        FactCrosstabConnectionSupport.fromDatabaseMeta(source, variables);

    assertEquals("localhost", connection.getHostname());
    assertEquals("54320", connection.getPort());
    assertEquals("test_edw", connection.getDatabaseName());
    assertEquals("test", connection.getUsername());
    assertEquals("test", connection.getPassword());
  }

  @Test
  void decryptsHopEncryptedPasswordAfterVariableResolve() throws Exception {
    String encrypted = Encr.encryptPasswordIfNotUsingVariables("warehouse-secret");
    DatabaseMeta source =
        new DatabaseMeta(
            "Vault", "POSTGRESQL", "Native", "localhost", "test_edw", "5432", "test", encrypted);

    HDatabaseConnection connection =
        FactCrosstabConnectionSupport.fromDatabaseMeta(source, new Variables());

    assertEquals("warehouse-secret", connection.getPassword());
  }
}
