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
package org.hopper.edw.datavault.metadata.businessvault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class SurvivorshipMergeLogicTest {

  private static final Timestamp T1 = Timestamp.valueOf("2020-01-01 00:00:00");
  private static final Timestamp T2 = Timestamp.valueOf("2024-01-01 00:00:00");
  private static final Timestamp T3 = Timestamp.valueOf("2024-06-01 00:00:00");
  private static final Timestamp T4 = Timestamp.valueOf("2024-09-01 00:00:00");
  private static final Timestamp T5 = Timestamp.valueOf("2025-01-01 00:00:00");

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void kafkaWinsOracleRemainsAndSeedDoesNotOverwrite() {
    List<SurvivorshipMergeLogic.Rule> rules =
        List.of(
            rule("email", "KAFKA", 1, BvNullPolicy.INHERIT, BvLegOperation.UPSERT),
            rule("email", "ORACLE", 2, BvNullPolicy.APPLY, BvLegOperation.UPSERT),
            rule("email", "SAS", 3, BvNullPolicy.APPLY, BvLegOperation.SEED));

    List<SurvivorshipMergeLogic.Emit> emits =
        SurvivorshipMergeLogic.replay(
            List.of(
                event(T1, "SAS", BvLegOperation.SEED, "seed@"),
                event(T2, "ORACLE", BvLegOperation.UPSERT, "ora@"),
                event(T3, "KAFKA", BvLegOperation.UPSERT, "kaf@"),
                event(T4, "KAFKA", BvLegOperation.DELETE, "kaf@"),
                event(T5, "SAS", BvLegOperation.SEED, "newseed@")),
            rules);

    assertEquals("seed@", emits.get(0).values.get("email"));
    assertEquals("ora@", emits.get(1).values.get("email"));
    assertEquals("kaf@", emits.get(2).values.get("email"));
    assertEquals("ora@", emits.get(3).values.get("email"));
    assertEquals(4, emits.size());
    assertEquals("ORACLE", emits.get(3).sourceId);
  }

  @Test
  void kafkaNullInheritsTheOracleValue() {
    List<SurvivorshipMergeLogic.Rule> rules =
        List.of(
            rule("email", "KAFKA", 1, BvNullPolicy.INHERIT, BvLegOperation.UPSERT),
            rule("email", "ORACLE", 2, BvNullPolicy.APPLY, BvLegOperation.UPSERT));
    List<SurvivorshipMergeLogic.Emit> emits =
        SurvivorshipMergeLogic.replay(
            List.of(
                event(T1, "ORACLE", BvLegOperation.UPSERT, "ora@"),
                eventNull(T2, "KAFKA", BvLegOperation.UPSERT)),
            rules);

    assertEquals(1, emits.size());
    assertEquals("ora@", emits.get(0).values.get("email"));
  }

  @Test
  void applyNullClearsAndFieldRanksCanDisagree() {
    List<SurvivorshipMergeLogic.Rule> rules =
        List.of(
            rule("email", "KAFKA", 1, BvNullPolicy.APPLY, BvLegOperation.UPSERT),
            rule("email", "ORACLE", 2, BvNullPolicy.APPLY, BvLegOperation.UPSERT),
            rule("name", "ORACLE", 1, BvNullPolicy.APPLY, BvLegOperation.UPSERT),
            rule("name", "KAFKA", 2, BvNullPolicy.INHERIT, BvLegOperation.UPSERT));
    List<SurvivorshipMergeLogic.Emit> emits =
        SurvivorshipMergeLogic.replay(
            List.of(
                event(T1, "ORACLE", BvLegOperation.UPSERT, Map.of("email", "ora@", "name", "Ora")),
                event(T2, "KAFKA", BvLegOperation.UPSERT, Map.of("email", "kaf@", "name", "Kaf")),
                event(T3, "KAFKA", BvLegOperation.UPSERT, mapWithNull("email", "name"))),
            rules);

    assertEquals("kaf@", emits.get(1).values.get("email"));
    assertEquals("Ora", emits.get(1).values.get("name"));
    assertNull(emits.get(2).values.get("email"));
    assertEquals("Ora", emits.get(2).values.get("name"));
  }

  @Test
  void absentFlagKeepsTheValueAndAPresentNullClearsIt() {
    List<SurvivorshipMergeLogic.Rule> rules =
        List.of(
            new SurvivorshipMergeLogic.Rule(
                "email", "KAFKA", 1, BvNullPolicy.INHERIT, BvLegOperation.UPSERT, "email_present"),
            rule("email", "ORACLE", 2, BvNullPolicy.APPLY, BvLegOperation.UPSERT));
    Map<String, Object> absent = new java.util.LinkedHashMap<>();
    java.util.Set<String> absentCleared = new java.util.LinkedHashSet<>();
    SurvivorshipMergeLogic.readField(absent, absentCleared, "email", null, "email_present", "N");
    Map<String, Object> cleared = new java.util.LinkedHashMap<>();
    java.util.Set<String> clearedFields = new java.util.LinkedHashSet<>();
    SurvivorshipMergeLogic.readField(cleared, clearedFields, "email", null, "email_present", "Y");

    List<SurvivorshipMergeLogic.Emit> emits =
        SurvivorshipMergeLogic.replay(
            List.of(
                event(T1, "ORACLE", BvLegOperation.UPSERT, "ora@"),
                new SurvivorshipMergeLogic.Event(
                    "P1", T2, "KAFKA", BvLegOperation.UPSERT, absent, absentCleared),
                new SurvivorshipMergeLogic.Event(
                    "P1", T3, "KAFKA", BvLegOperation.UPSERT, cleared, clearedFields)),
            rules);

    assertEquals(2, emits.size());
    assertEquals("ora@", emits.get(0).values.get("email"));
    assertNull(emits.get(1).values.get("email"));
    assertTrue(absent.isEmpty());
    assertTrue(clearedFields.contains("email"));
  }

  @Test
  void aMissingDeltaKeyIsNotDeleted() {
    List<SurvivorshipMergeLogic.Rule> rules =
        List.of(
            rule("email", "KAFKA", 1, BvNullPolicy.INHERIT, BvLegOperation.UPSERT),
            rule("email", "ORACLE", 2, BvNullPolicy.APPLY, BvLegOperation.UPSERT));
    List<SurvivorshipMergeLogic.Emit> emits =
        SurvivorshipMergeLogic.replay(
            List.of(
                new SurvivorshipMergeLogic.Event(
                    "P1", T1, "ORACLE", BvLegOperation.UPSERT, Map.of("email", "ora@")),
                new SurvivorshipMergeLogic.Event(
                    "P1", T3, "KAFKA", BvLegOperation.DELETE, Map.of()),
                new SurvivorshipMergeLogic.Event(
                    "P2", T2, "KAFKA", BvLegOperation.UPSERT, Map.of("email", "kaf@"))),
            rules);

    assertEquals("ora@", email(emits, "P1", T1));
    assertEquals("kaf@", email(emits, "P2", T2));
    assertNull(email(emits, "P1", T3));
    assertEquals(2, emits.size());
  }

  @Test
  void deltaCalendarDefaultsToInheritAndFullDefaultsToApply() {
    BvSourceCalendar calendar = new BvSourceCalendar();
    calendar.getEntries().add(entry("KAFKA", BvSourceCalendarMode.DELTA));
    calendar.getEntries().add(entry("ORACLE", BvSourceCalendarMode.FULL));

    assertEquals(
        BvNullPolicy.INHERIT,
        BvSurvivorshipSupport.resolveNullPolicy(null, null, calendar, "KAFKA"));
    assertEquals(
        BvNullPolicy.APPLY,
        BvSurvivorshipSupport.resolveNullPolicy(null, null, calendar, "ORACLE"));
    assertEquals(
        BvNullPolicy.INHERIT,
        BvSurvivorshipSupport.resolveNullPolicy(
            BvNullPolicy.INHERIT, BvNullPolicy.APPLY, calendar, "ORACLE"));
  }

  @Test
  void sharedTargetWithoutRanksStaysAnErrorAndRanksRoundTrip() throws Exception {
    BvScd2Table unranked = new BvScd2Table();
    unranked.setName("customer_360");
    unranked.getFieldMappings().add(new BvScd2FieldMapping("sat_a", "email", "email"));
    unranked.getFieldMappings().add(new BvScd2FieldMapping("sat_b", "email_txt", "email"));
    List<ICheckResult> remarks = new java.util.ArrayList<>();
    BvSurvivorshipSupport.validateRanks(remarks, unranked, new Variables());
    assertTrue(remarks.stream().anyMatch(remark -> remark.getType() == ICheckResult.TYPE_RESULT_ERROR));

    BvScd2Table ranked = new BvScd2Table();
    ranked.setName("customer_360");
    ranked.setTableName("customer_360");
    BvScd2FieldMapping kafka = new BvScd2FieldMapping("sat_a", "email", "email");
    kafka.setRank("1");
    kafka.setNullPolicy(BvNullPolicy.INHERIT);
    BvScd2FieldMapping oracle = new BvScd2FieldMapping("sat_b", "email_txt", "email");
    oracle.setRank("2");
    oracle.setNullPolicy(BvNullPolicy.APPLY);
    ranked.getFieldMappings().add(kafka);
    ranked.getFieldMappings().add(oracle);
    remarks.clear();
    BvSurvivorshipSupport.validateRanks(remarks, ranked, new Variables());
    assertFalse(remarks.stream().anyMatch(remark -> remark.getType() == ICheckResult.TYPE_RESULT_ERROR));

    BvScd2Table restored = roundTrip(ranked);
    assertEquals("1", restored.getFieldMappings().get(0).getRank());
    assertEquals(BvNullPolicy.INHERIT, restored.getFieldMappings().get(0).getNullPolicy());
    assertEquals("2", restored.getFieldMappings().get(1).getRank());
    assertTrue(BvSurvivorshipSupport.usesSurvivorship(restored));
  }

  @Test
  void twoClocksOnOneSourceQueryAreRejected() {
    BvSourceQuery query = new BvSourceQuery();
    query.setName("sas_person");
    query.setFunctionalTimestampField("event_ts");
    query.setLoadDateField("load_ts");
    java.util.List<org.apache.hop.core.ICheckResult> remarks = new java.util.ArrayList<>();
    BvScd2FieldMappingValidationSupport.validateOneClock(
        remarks, null, query, new Variables());
    assertTrue(
        remarks.stream().anyMatch(remark -> remark.getText() != null && remark.getText().contains("one clock")));
  }

  private static SurvivorshipMergeLogic.Rule rule(
      String field, String source, int rank, BvNullPolicy policy, BvLegOperation operation) {
    return new SurvivorshipMergeLogic.Rule(field, source, rank, policy, operation);
  }

  private static SurvivorshipMergeLogic.Event event(
      Timestamp timestamp, String source, BvLegOperation operation, String email) {
    return event(timestamp, source, operation, Map.of("email", email == null ? "" : email));
  }

  private static SurvivorshipMergeLogic.Event event(
      Timestamp timestamp, String source, BvLegOperation operation, Map<String, Object> values) {
    return new SurvivorshipMergeLogic.Event("P1", timestamp, source, operation, values);
  }

  private static SurvivorshipMergeLogic.Event eventNull(
      Timestamp timestamp, String source, BvLegOperation operation) {
    Map<String, Object> values = new java.util.LinkedHashMap<>();
    values.put("email", null);
    return new SurvivorshipMergeLogic.Event("P1", timestamp, source, operation, values);
  }

  private static Map<String, Object> mapWithNull(String nullField, String otherNull) {
    Map<String, Object> values = new java.util.LinkedHashMap<>();
    values.put(nullField, null);
    values.put(otherNull, null);
    return values;
  }

  private static String email(
      List<SurvivorshipMergeLogic.Emit> emits, String key, Timestamp timestamp) {
    for (SurvivorshipMergeLogic.Emit emit : emits) {
      if (key.equals(emit.key) && timestamp.equals(emit.timestamp)) {
        return (String) emit.values.get("email");
      }
    }
    return null;
  }

  private static BvSourceCalendarEntry entry(String sourceId, BvSourceCalendarMode mode) {
    BvSourceCalendarEntry entry = new BvSourceCalendarEntry();
    entry.setSourceId(sourceId);
    entry.setMode(mode);
    return entry;
  }

  private static BvScd2Table roundTrip(BvScd2Table original) throws Exception {
    String xml = XmlHandler.aroundTag("table", XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(xml);
    Node rootNode = XmlHandler.getSubNode(document, "table");
    BvScd2Table restored = new BvScd2Table();
    XmlMetadataUtil.deSerializeFromXml(rootNode, BvScd2Table.class, restored, null);
    return restored;
  }
}
