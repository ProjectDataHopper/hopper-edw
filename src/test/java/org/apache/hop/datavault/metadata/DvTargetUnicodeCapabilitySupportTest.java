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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DvTargetUnicodeCapabilitySupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void sqlServerUtf8CollationIsCapable() {
    var assessment =
        DvTargetUnicodeCapabilitySupport.evaluate(
            sqlServerMeta(), DvDdlSupport.SQL_SERVER_UTF8_COLLATION);
    assertEquals(DvTargetUnicodeCapabilitySupport.Status.CAPABLE, assessment.status());
    assertTrue(assessment.isCapable());
    assertFalse(assessment.isHardFailure());
  }

  @Test
  void sqlServerLegacyCollationIsNotCapable() {
    var assessment =
        DvTargetUnicodeCapabilitySupport.evaluate(sqlServerMeta(), "SQL_Latin1_General_CP1_CI_AS");
    assertEquals(DvTargetUnicodeCapabilitySupport.Status.NOT_CAPABLE, assessment.status());
    assertTrue(assessment.isHardFailure());
    assertTrue(assessment.remediation().contains("UTF-8"));
  }

  @Test
  void isSqlServerUtf8CollationDetectsSuffix() {
    assertTrue(
        DvTargetUnicodeCapabilitySupport.isSqlServerUtf8Collation(
            "Latin1_General_100_CI_AS_SC_UTF8"));
    assertTrue(
        DvTargetUnicodeCapabilitySupport.isSqlServerUtf8Collation(
            "latin1_general_100_ci_as_sc_utf8"));
    assertFalse(
        DvTargetUnicodeCapabilitySupport.isSqlServerUtf8Collation("SQL_Latin1_General_CP1_CI_AS"));
    assertFalse(DvTargetUnicodeCapabilitySupport.isSqlServerUtf8Collation(null));
  }

  @Test
  void postgresUtf8EncodingIsCapable() {
    var assessment = DvTargetUnicodeCapabilitySupport.evaluate(postgresMeta(), "UTF8");
    assertEquals(DvTargetUnicodeCapabilitySupport.Status.CAPABLE, assessment.status());
  }

  @Test
  void postgresLatin1EncodingIsNotCapable() {
    var assessment = DvTargetUnicodeCapabilitySupport.evaluate(postgresMeta(), "LATIN1");
    assertEquals(DvTargetUnicodeCapabilitySupport.Status.NOT_CAPABLE, assessment.status());
  }

  @Test
  void mysqlUtf8mb4IsCapable() {
    var assessment = DvTargetUnicodeCapabilitySupport.evaluate(mysqlMeta(), "utf8mb4");
    assertEquals(DvTargetUnicodeCapabilitySupport.Status.CAPABLE, assessment.status());
  }

  @Test
  void mysqlLatin1AndUtf8AreNotCapable() {
    assertEquals(
        DvTargetUnicodeCapabilitySupport.Status.NOT_CAPABLE,
        DvTargetUnicodeCapabilitySupport.evaluate(mysqlMeta(), "latin1").status());
    assertEquals(
        DvTargetUnicodeCapabilitySupport.Status.NOT_CAPABLE,
        DvTargetUnicodeCapabilitySupport.evaluate(mysqlMeta(), "utf8").status());
  }

  @Test
  void notCapableIsErrorWhenEnforcedAndWarningWhenSoft() {
    var assessment = DvTargetUnicodeCapabilitySupport.evaluate(mysqlMeta(), "utf8");
    assertTrue(assessment.isHardFailure());

    List<ICheckResult> enforced = new ArrayList<>();
    DvTargetUnicodeCapabilitySupport.addRemark(
        enforced, assessment, "Vault", mysqlMeta().getPluginId(), true);
    assertEquals(1, enforced.size());
    assertEquals(ICheckResult.TYPE_RESULT_ERROR, enforced.get(0).getType());

    List<ICheckResult> soft = new ArrayList<>();
    DvTargetUnicodeCapabilitySupport.addRemark(
        soft, assessment, "Vault", mysqlMeta().getPluginId(), false);
    assertEquals(1, soft.size());
    assertEquals(ICheckResult.TYPE_RESULT_WARNING, soft.get(0).getType());
  }

  @Test
  void capabilityQueriesMatchEngines() {
    assertTrue(
        DvTargetUnicodeCapabilitySupport.buildCapabilityQuery(sqlServerMeta())
            .contains("DATABASEPROPERTYEX"));
    assertTrue(
        DvTargetUnicodeCapabilitySupport.buildCapabilityQuery(postgresMeta())
            .contains("pg_encoding_to_char"));
    assertTrue(
        DvTargetUnicodeCapabilitySupport.buildCapabilityQuery(mysqlMeta())
            .contains("DEFAULT_CHARACTER_SET_NAME"));
  }

  private static DatabaseMeta sqlServerMeta() {
    return databaseMetaWithPluginId(DvBulkLoadPluginSupport.MSSQLNATIVE_DB_PLUGIN_ID);
  }

  private static DatabaseMeta postgresMeta() {
    return databaseMetaWithPluginId(DvBulkLoadPluginSupport.POSTGRESQL_DB_PLUGIN_ID);
  }

  private static DatabaseMeta mysqlMeta() {
    return databaseMetaWithPluginId(DvBulkLoadPluginSupport.MYSQL_DB_PLUGIN_ID);
  }

  private static DatabaseMeta databaseMetaWithPluginId(String pluginId) {
    return new DatabaseMeta() {
      @Override
      public String getPluginId() {
        return pluginId;
      }
    };
  }
}
