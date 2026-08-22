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
package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

/** Dual-read and part-expansion coverage for composite hub business keys (metadata only). */
class BusinessKeyCompositeMetadataTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void resolveSourcePartsPrefersListOverScalar() {
    BusinessKey bk = new BusinessKey("burger_bk");
    bk.setComposite(true);
    bk.setSourceFieldName("ignored");
    bk.setSourceFieldNames(List.of("num_seq_bkcc_bk", "num_seq_bk"));

    assertEquals(List.of("num_seq_bkcc_bk", "num_seq_bk"), bk.resolveSourceParts());
    assertEquals(2, bk.sourcePartCount());
  }

  @Test
  void resolveSourcePartsFallsBackToScalar() {
    BusinessKey bk = new BusinessKey("customer_id");
    bk.setSourceFieldName("cust_no");
    assertEquals(List.of("cust_no"), bk.resolveSourceParts());
  }

  @Test
  void businessKeySourceResolveSourcePartsDualRead() {
    BusinessKeySource multi = new BusinessKeySource();
    multi.setBusinessKeyField("burger_bk");
    multi.setSourceFieldNames(List.of("a", "b"));
    assertEquals(List.of("a", "b"), multi.resolveSourceParts());

    BusinessKeySource single = new BusinessKeySource("burger_bk", "only");
    assertEquals(List.of("only"), single.resolveSourceParts());
  }

  @Test
  void legacyBusinessKeyXmlStillLoadsWithoutComposite() throws Exception {
    String xml =
        """
        <businessKey>
          <name>customer_id</name>
          <dataType>Integer</dataType>
          <length>9</length>
          <sourceFieldName>customer_id</sourceFieldName>
          <recordSourceName>E2E-customer-hub</recordSourceName>
        </businessKey>
        """;
    Node node = XmlHandler.loadXmlString(xml, "businessKey");
    BusinessKey bk = XmlMetadataUtil.deSerializeFromXml(node, BusinessKey.class, null);
    assertEquals("customer_id", bk.getName());
    assertFalse(bk.isComposite());
    assertEquals(List.of("customer_id"), bk.resolveSourceParts());
  }

  @Test
  void compositeBusinessKeyXmlRoundTrip() throws Exception {
    String xml =
        """
        <businessKey>
          <name>burger_bk</name>
          <dataType>String</dataType>
          <length>100</length>
          <composite>Y</composite>
          <sourceFieldNames>
            <sourceFieldName>num_seq_bkcc_bk</sourceFieldName>
            <sourceFieldName>num_seq_bk</sourceFieldName>
          </sourceFieldNames>
          <recordSourceName>ext_ami</recordSourceName>
        </businessKey>
        """;
    Node node = XmlHandler.loadXmlString(xml, "businessKey");
    BusinessKey bk = XmlMetadataUtil.deSerializeFromXml(node, BusinessKey.class, null);
    assertTrue(bk.isComposite());
    assertEquals(List.of("num_seq_bkcc_bk", "num_seq_bk"), bk.resolveSourceParts());

    // serializeObjectToXml emits property children only — wrap for re-parse
    String serialized =
        "<businessKey>" + XmlMetadataUtil.serializeObjectToXml(bk) + "</businessKey>";
    assertTrue(
        serialized.contains("num_seq_bkcc_bk") && serialized.contains("num_seq_bk"),
        "serialized XML should retain parts: " + serialized);
    assertTrue(
        serialized.contains("composite"),
        "serialized XML should retain composite flag: " + serialized);

    Node roundTripNode = XmlHandler.loadXmlString(serialized, "businessKey");
    BusinessKey again = XmlMetadataUtil.deSerializeFromXml(roundTripNode, BusinessKey.class, null);
    assertEquals("burger_bk", again.getName());
    assertTrue(again.isComposite());
    assertEquals(List.of("num_seq_bkcc_bk", "num_seq_bk"), again.resolveSourceParts());
  }

  @Test
  void businessKeySourceXmlRoundTripWithParts() throws Exception {
    String xml =
        """
        <businessKeySource>
          <businessKeyField>burger_bk</businessKeyField>
          <sourceFieldNames>
            <sourceFieldName>part_a</sourceFieldName>
            <sourceFieldName>part_b</sourceFieldName>
          </sourceFieldNames>
        </businessKeySource>
        """;
    Node node = XmlHandler.loadXmlString(xml, "businessKeySource");
    BusinessKeySource source =
        XmlMetadataUtil.deSerializeFromXml(node, BusinessKeySource.class, null);
    assertEquals("burger_bk", source.getBusinessKeyField());
    assertEquals(List.of("part_a", "part_b"), source.resolveSourceParts());

    String serialized =
        "<businessKeySource>"
            + XmlMetadataUtil.serializeObjectToXml(source)
            + "</businessKeySource>";
    Node roundTripNode = XmlHandler.loadXmlString(serialized, "businessKeySource");
    BusinessKeySource again =
        XmlMetadataUtil.deSerializeFromXml(roundTripNode, BusinessKeySource.class, null);
    assertEquals(List.of("part_a", "part_b"), again.resolveSourceParts());
  }

  @Test
  void vaultKeysAndHashInputsExpandCompositeParts() {
    DvHub hub = new DvHub("hub_burger");
    BusinessKey bk = new BusinessKey("burger_bk");
    bk.setComposite(true);
    bk.setSourceFieldNames(List.of("num_seq_bkcc_bk", "num_seq_bk"));
    bk.setRecordSourceName("ext");
    hub.setBusinessKeys(List.of(bk));

    List<DvBusinessKeyPartSupport.VaultBusinessKey> vaultKeys =
        DvBusinessKeyPartSupport.resolveVaultBusinessKeys(hub);
    assertEquals(1, vaultKeys.size());
    assertTrue(vaultKeys.get(0).composite());
    assertEquals(2, vaultKeys.get(0).partCount());
    assertEquals(2, DvBusinessKeyPartSupport.totalHashInputPartCount(hub));

    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setHashUsesComposedBusinessKey(false);
    assertEquals(
        List.of("num_seq_bkcc_bk", "num_seq_bk"),
        DvBusinessKeyPartSupport.resolveHashInputStreamFieldNames(
            hub, "ext", config, new Variables()));

    config.setHashUsesComposedBusinessKey(true);
    assertEquals(
        List.of("burger_bk"),
        DvBusinessKeyPartSupport.resolveHashInputStreamFieldNames(
            hub, "ext", config, new Variables()));
  }

  @Test
  void multipartiteVaultKeysUnchanged() {
    DvHub hub = new DvHub("hub_line");
    BusinessKey a = new BusinessKey("order_id");
    a.setSourceFieldName("order_id");
    BusinessKey b = new BusinessKey("line_no");
    b.setSourceFieldName("line_no");
    hub.setBusinessKeys(List.of(a, b));

    assertEquals(2, DvBusinessKeyPartSupport.totalHashInputPartCount(hub));
    assertFalse(DvBusinessKeyPartSupport.hubHasCompositeBusinessKey(hub));
    assertEquals(
        List.of("order_id", "line_no"),
        DvBusinessKeyPartSupport.resolveHashInputStreamFieldNames(
            hub, null, new DataVaultConfiguration(), new Variables()));
  }

  @Test
  void multiSourceCompositePartCountMismatchDetected() {
    DvHub hub = new DvHub("hub_burger");
    BusinessKey s1 = new BusinessKey("burger_bk");
    s1.setComposite(true);
    s1.setSourceFieldNames(List.of("a", "b"));
    s1.setRecordSourceName("src1");
    BusinessKey s2 = new BusinessKey("burger_bk");
    s2.setComposite(true);
    s2.setSourceFieldNames(List.of("x"));
    s2.setRecordSourceName("src2");
    hub.setBusinessKeys(List.of(s1, s2));

    List<String> mismatches =
        DvBusinessKeyPartSupport.findCompositePartCountMismatches(hub, new Variables());
    assertEquals(1, mismatches.size());
    assertTrue(mismatches.get(0).contains("burger_bk"));
  }

  @Test
  void composeStoredBusinessKeyUsesDelimiterWithoutSuffixOrCasing() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setBusinessKeyDelimiter("#");
    config.setHashContentSuffix("#");
    config.setHashContentCasing(HashContentCasing.UPPER.name());
    config.setTrimBusinessKeys(true);
    config.setNullPlaceholder("^^");

    String composed =
        DvBusinessKeyPartSupport.composeStoredBusinessKey(
            List.of(" ikl ", "12278170"), config, new Variables());
    assertEquals("ikl#12278170", composed);

    assertNull(
        DvBusinessKeyPartSupport.composeStoredBusinessKey(List.of(), config, new Variables()));
  }

  @Test
  void composeStoredBusinessKeyUsesNullPlaceholder() {
    DataVaultConfiguration config = new DataVaultConfiguration();
    config.setBusinessKeyDelimiter("#");
    config.setNullPlaceholder("^^");
    config.setTrimBusinessKeys(true);

    String composed =
        DvBusinessKeyPartSupport.composeStoredBusinessKey(
            Arrays.asList("IKL", null), config, new Variables());
    assertEquals("IKL#^^", composed);
  }

  @Test
  void hashUsesComposedBusinessKeyDefaultsFalse() {
    assertFalse(new DataVaultConfiguration().isHashUsesComposedBusinessKey());
  }
}
