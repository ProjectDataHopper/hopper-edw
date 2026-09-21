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
package org.hopper.edw.datavault.transform.syntheticdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.textfileoutput.TextFileOutputMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SyntheticDataEngineTest {

  private final SyntheticDataEngine engine = new SyntheticDataEngine();

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void retailPipelineLoadsSyntheticTransforms() throws Exception {
    PipelineMeta pipeline = loadRetailPipeline();
    TransformMeta hub = pipeline.findTransform("Customers hub");
    assertEquals(SyntheticDataMeta.class, hub.getTransform().getClass());
    SyntheticDataMeta meta = (SyntheticDataMeta) hub.getTransform();
    assertEquals("COMBINE", meta.getCardinalityMode());
    assertTrue(
        meta.getFields().stream().anyMatch(field -> field.isPublish() && "customer_id".equals(field.getName())));
    SyntheticDataMeta headers = synthetic(pipeline, "Order headers");
    assertTrue(
        headers.getFields().stream()
            .anyMatch(field -> field.isPublish() && "order_id".equals(field.getName())));
    assertEquals("Order headers", ((SyntheticDataMeta) pipeline.findTransform("Order lines").getTransform()).getParentsTransform());
    TextFileOutputMeta shipmentFile =
        (TextFileOutputMeta) pipeline.findTransform("Write shipment events").getTransform();
    assertEquals(
        "\"",
        shipmentFile.getEnclosure(),
        "JSON payload contains commas and must be enclosed so CSV input keeps column alignment");
    assertFalse(
        shipmentFile.isEnclosureFixDisabled(),
        "Hop leaves the enclosure fix disabled unless the flag is set, so commas in the payload are not quoted");
  }

  @Test
  void retailInitialConfigurationGeneratesTheFirstOrderDemo() throws Exception {
    Variables variables = retailVariables("initial", "2024-01-01", "1");
    variables.setVariable("CUSTOMERS", "20");
    variables.setVariable("PRODUCTS", "10");
    variables.setVariable("ORDERS", "8");
    variables.setVariable("WAREHOUSES", "4");
    PipelineMeta pipeline = loadRetailPipeline();
    List<Map<String, Object>> orders =
        engine.generate(synthetic(pipeline, "Order headers"), variables, List.of(), List.of(), List.of(), List.of());
    assertEquals(8, orders.size());
    assertEquals("O000001", orders.get(0).get("order_id"));

    List<Map<String, Object>> lines =
        engine.generate(synthetic(pipeline, "Order lines"), variables, orders, List.of(), List.of(), List.of());
    List<Map<String, Object>> firstOrder =
        lines.stream().filter(row -> "O000001".equals(row.get("order_id"))).toList();
    assertEquals(3, firstOrder.size());
    assertEquals(firstOrder.get(0).get("product_id"), firstOrder.get(1).get("product_id"));
    assertTrue(String.valueOf(firstOrder.get(0).get("product_id")).startsWith("P"));
    assertEquals(4.5d, (Double) firstOrder.get(0).get("unit_price"), 0.001);
    assertEquals(5.0d, (Double) firstOrder.get(1).get("unit_price"), 0.001);

    SyntheticDataMeta asn = synthetic(pipeline, "ASN lines");
    List<Map<String, Object>> asnRows =
        engine.generate(asn, variables, orders, lines, List.of(), List.of());
    assertFalse(asnRows.isEmpty());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    NestedXmlWriter.write(out, asn, variables, asnRows);
    String xml = out.toString(StandardCharsets.UTF_8);
    assertTrue(xml.contains("<AdvancedShipmentNotices wave=\"initial\""));
    assertTrue(xml.contains("<Line "));
  }

  @Test
  void expressionComputesSampleSizeAndNewIdStart() throws HopException {
    Variables variables = new Variables();
    variables.setVariable("CUSTOMERS", "10000");
    variables.setVariable("PERIOD_MONTHS", "1");

    assertEquals(
        100L,
        NumericExpression.evaluateLong("max(50, div(${CUSTOMERS},100))", variables, Map.of()));
    assertEquals(
        10001L,
        NumericExpression.evaluateLong(
            "${CUSTOMERS}+(max(1,${PERIOD_MONTHS})-1)*max(50,div(${CUSTOMERS},100))+1",
            variables,
            Map.of()));
    assertEquals(
        10000L,
        NumericExpression.evaluateLong(
            "ifEq('${MODE}','initial',${CUSTOMERS},max(50,div(${CUSTOMERS},100)))",
            variablesWith(variables, "MODE", "initial"),
            Map.of()));
  }

  @Test
  void dateDiffFeedsADayOffset() throws HopException {
    Map<String, Object> fields =
        Map.of(
            "ship", "2024-01-04",
            "order", "2024-01-01",
            "step_name", "PICKED_UP");
    assertEquals(
        3L,
        NumericExpression.evaluateWithDates(
                "ifEq(step_name,'PICKED_UP',dateDiff(ship,order),0)", null, fields)
            .longValue());
  }

  @Test
  void countModeIsSeedStableAndDeclaresRowMeta() throws HopException {
    SyntheticDataMeta meta = countMeta();
    List<Map<String, Object>> first = engine.generate(meta, new Variables(), List.of(), List.of(), List.of(), List.of());
    List<Map<String, Object>> second = engine.generate(meta, new Variables(), List.of(), List.of(), List.of(), List.of());

    assertEquals(List.of(1L, 2L, 3L), first.stream().map(row -> row.get("id")).toList());
    assertEquals("A", first.get(0).get("bucket"));
    assertEquals("B", first.get(1).get("bucket"));
    assertEquals(first, second);

    RowMeta rowMeta = new RowMeta();
    meta.getFields(rowMeta, "synthetic", null, null, new Variables(), null);
    assertEquals(2, rowMeta.size());
    assertEquals(IValueMeta.TYPE_INTEGER, rowMeta.searchValueMeta("id").getType());
    assertEquals(IValueMeta.TYPE_STRING, rowMeta.searchValueMeta("bucket").getType());
  }

  @Test
  void sampleIsUniqueAndZeroPopulationsAreSkipped() throws HopException {
    SyntheticDataMeta meta = new SyntheticDataMeta();
    meta.setSeed("11");
    meta.setCardinalityMode("COMBINE");
    SyntheticPopulation skipped = new SyntheticPopulation();
    skipped.setKind("SAMPLE");
    skipped.setFrom("1");
    skipped.setTo("100");
    skipped.setCount("0");
    SyntheticPopulation sample = new SyntheticPopulation();
    sample.setKind("SAMPLE");
    sample.setFrom("1");
    sample.setTo("100");
    sample.setCount("20");
    meta.getPopulations().add(skipped);
    meta.getPopulations().add(sample);
    meta.getFields().add(field("id", "Integer", "SEQUENCE", "source=population"));

    List<Map<String, Object>> rows = engine.generate(meta, new Variables(), List.of(), List.of(), List.of(), List.of());
    List<Long> ids = rows.stream().map(row -> (Long) row.get("id")).toList();
    assertEquals(20, ids.size());
    assertEquals(20, new HashSet<>(ids).size());
    assertTrue(ids.stream().allMatch(id -> id >= 1 && id <= 100));
    assertEquals(ids.stream().sorted().toList(), ids);
  }

  @Test
  void updateHubIdsStayAboveTheBaseWhileSatellitesMix() throws HopException {
    Variables variables = new Variables();
    variables.setVariable("MODE", "update");
    variables.setVariable("CUSTOMERS", "100");
    variables.setVariable("PERIOD_MONTHS", "1");

    List<Long> hub = ids(hubMeta(), variables);
    List<Long> satellites = ids(satelliteMeta(), variables);

    assertFalse(hub.isEmpty());
    assertTrue(hub.stream().allMatch(id -> id > 100));
    assertTrue(satellites.stream().anyMatch(id -> id >= 1 && id <= 100));
    assertTrue(satellites.stream().anyMatch(id -> id > 100));
    Set<Long> existingSatellites = new HashSet<>();
    for (Long id : satellites) {
      if (id <= 100) {
        existingSatellites.add(id);
      }
    }
    assertTrue(existingSatellites.stream().noneMatch(hub::contains));
  }

  @Test
  void uniquePairsAreCappedAtTheCartesianProduct() throws HopException {
    SyntheticDataMeta meta = new SyntheticDataMeta();
    meta.setSeed("42");
    meta.setCardinalityMode("UNIQUE_PAIRS");
    meta.setPairCount("20");
    meta.setPairLeftStart("1");
    meta.setPairLeftCount("5");
    meta.setPairRightStart("1");
    meta.setPairRightCount("10");
    meta.getFields().add(field("left", "Integer", "COPY", "field=pair_left"));
    meta.getFields().add(field("right", "Integer", "COPY", "field=pair_right"));

    List<Map<String, Object>> rows =
        engine.generate(meta, new Variables(), List.of(), List.of(), List.of(), List.of());
    Set<String> keys = new HashSet<>();
    for (Map<String, Object> row : rows) {
      keys.add(row.get("left") + "/" + row.get("right"));
    }
    assertEquals(20, rows.size());
    assertEquals(20, keys.size());

    meta.setPairCount("100");
    meta.setPairLeftCount("2");
    meta.setPairRightCount("2");
    List<Map<String, Object>> capped =
        engine.generate(meta, new Variables(), List.of(), List.of(), List.of(), List.of());
    assertEquals(4, capped.size());
  }

  @Test
  void firstParentOverrideRepeatsTheKeyAndFixesPrices() throws HopException {
    SyntheticDataMeta meta = new SyntheticDataMeta();
    meta.setSeed("5");
    meta.setCardinalityMode("PER_PARENT");
    meta.setFraction("1");
    meta.setMinChildren("1");
    meta.setMaxChildren("1");
    meta.getFields().add(field("product_id", "Integer", "INT_RANGE", "min=1;max=10;repeatPreviousProbability=0.25"));
    meta.getFields().add(field("unit_price", "Number", "NUMBER_RANGE", "min=5;max=9;scale=2"));
    SyntheticOverride force = new SyntheticOverride();
    force.setParentIndex("0");
    force.setForceChildCount("3");
    SyntheticOverride repeat = new SyntheticOverride();
    repeat.setParentIndex("0");
    repeat.setChildIndex("1");
    repeat.setField("product_id");
    repeat.setGenerator("COPY_SIBLING");
    repeat.setArguments("child=0");
    SyntheticOverride price = new SyntheticOverride();
    price.setParentIndex("0");
    price.setChildIndex("1");
    price.setChildOp("<=");
    price.setField("unit_price");
    price.setGenerator("NUMBER_EXPR");
    price.setArguments("expression=4+(${child_index}+1)*0.5;scale=2");
    meta.getOverrides().add(force);
    meta.getOverrides().add(repeat);
    meta.getOverrides().add(price);

    List<Map<String, Object>> parents = List.of(Map.of("order_id", "O000001"));
    List<Map<String, Object>> rows =
        engine.generate(meta, new Variables(), parents, List.of(), List.of(), List.of());

    assertEquals(3, rows.size());
    assertEquals(rows.get(0).get("product_id"), rows.get(1).get("product_id"));
    assertEquals(4.5d, (Double) rows.get(0).get("unit_price"), 0.001);
    assertEquals(5.0d, (Double) rows.get(1).get("unit_price"), 0.001);
  }

  @Test
  void pathsCanEmitNothingAndDeliveredEmitsEveryStep() throws HopException {
    SyntheticDataMeta cancelled = pathMeta("0");
    SyntheticPath none = new SyntheticPath();
    none.setWhen("CANCELLED");
    none.setProbability("0");
    none.setSteps("EXCEPTION");
    cancelled.getPaths().add(none);
    List<Map<String, Object>> noRows =
        engine.generate(
            cancelled,
            new Variables(),
            List.of(Map.of("order_status", "CANCELLED")),
            List.of(),
            List.of(),
            List.of());
    assertTrue(noRows.isEmpty());

    SyntheticDataMeta delivered = pathMeta("3");
    SyntheticPath full = new SyntheticPath();
    full.setWhen("DELIVERED");
    full.setSteps("LABEL_CREATED|PICKED_UP|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED");
    delivered.getPaths().add(full);
    List<Map<String, Object>> rows =
        engine.generate(
            delivered,
            new Variables(),
            List.of(Map.of("order_status", "DELIVERED", "order_id", "O000001")),
            List.of(),
            List.of(),
            List.of());
    assertEquals(
        List.of("LABEL_CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"),
        rows.stream().map(row -> row.get("step")).toList());
    assertEquals(0, ((Number) rows.get(0).get("partition")).intValue());
  }

  @Test
  void jsonNestsObjectsAndXmlKeepsTheAsnShape() throws HopException {
    SyntheticDataMeta meta = new SyntheticDataMeta();
    meta.setSeed("1");
    meta.setCardinalityMode("COUNT");
    meta.setRowCount("1");
    meta.getFields().add(field("city", "String", "CONSTANT", "value=Austin"));
    meta.getFields().add(field("qty", "Integer", "CONSTANT", "value=2"));
    meta.getFields()
        .add(field("payload", "String", "JSON", "order_id=O1;location.city=${city};quantity=${qty}"));

    Map<String, Object> row =
        engine.generate(meta, new Variables(), List.of(), List.of(), List.of(), List.of()).get(0);
    assertEquals("{\"order_id\":\"O1\",\"location\":{\"city\":\"Austin\"},\"quantity\":2}", row.get("payload"));

    SyntheticDataMeta document = new SyntheticDataMeta();
    document.setDocumentRootElement("AdvancedShipmentNotices");
    document.setDocumentRootAttributes("wave=initial");
    document.getXmlNodes().add(node("asn", "", "GROUP", "ASN", "asn_id", "asn_id:asn_id"));
    document.getXmlNodes().add(node("order", "asn", "ELEMENT", "Order", "", "order_id:order_id"));
    document.getXmlNodes().add(node("packages", "asn", "WRAPPER", "Packages", "", ""));
    document.getXmlNodes().add(node("pkg", "packages", "GROUP", "Package", "package_id", "package_id:package_id"));
    document.getXmlNodes().add(node("lines", "pkg", "WRAPPER", "Lines", "", ""));
    document
        .getXmlNodes()
        .add(node("line", "lines", "ROW", "Line", "", "product_id:product_id,quantity:quantity"));

    List<Map<String, Object>> lines = new ArrayList<>();
    lines.add(Map.of("asn_id", "ASN000001", "order_id", "O000001", "package_id", "PKG-1", "product_id", "P000001", "quantity", 1));
    lines.add(Map.of("asn_id", "ASN000001", "order_id", "O000001", "package_id", "PKG-1", "product_id", "P000002", "quantity", 3));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    NestedXmlWriter.write(out, document, new Variables(), lines);
    String xml = out.toString(StandardCharsets.UTF_8);
    assertTrue(xml.contains("<AdvancedShipmentNotices wave=\"initial\">"));
    assertTrue(xml.contains("<ASN asn_id=\"ASN000001\">"));
    assertTrue(xml.contains("<Order order_id=\"O000001\"/>"));
    assertTrue(xml.contains("<Package package_id=\"PKG-1\">"));
    assertTrue(xml.contains("<Line product_id=\"P000001\" quantity=\"1\"/>"));
    assertTrue(xml.contains("<Line product_id=\"P000002\" quantity=\"3\"/>"));
  }

  @Test
  void hierarchySplitsAShippableOrderAndSkipsCancelled() throws HopException {
    SyntheticDataMeta meta = new SyntheticDataMeta();
    meta.setSeed("9");
    meta.setCardinalityMode("HIERARCHY");
    meta.setFraction("1");
    meta.setIncludeFirst(true);
    meta.setMinRows("1");
    meta.setSelectorField("order_status");
    meta.setIncludeStatuses("SHIPPED|DELIVERED");
    meta.setFallbackStatuses("NEW");
    meta.setParentKey("order_id");
    meta.setChildKey("order_id");
    meta.setSplitMinChildren("3");
    meta.setSplitProbability("1");
    meta.getFields().add(field("asn_id", "String", "SEQUENCE", "source=group;format=ASN%06d"));
    meta.getFields().add(field("package_id", "String", "TEMPLATE", "pattern=PKG-${package_index};scope=package"));
    meta.getFields().add(field("product_id", "String", "COPY", "field=child.product_id"));

    List<Map<String, Object>> parents =
        List.of(
            Map.of("order_id", "O1", "order_status", "SHIPPED"),
            Map.of("order_id", "O2", "order_status", "CANCELLED"));
    List<Map<String, Object>> children =
        List.of(
            Map.of("order_id", "O1", "product_id", "P1"),
            Map.of("order_id", "O1", "product_id", "P2"),
            Map.of("order_id", "O1", "product_id", "P3"));
    List<Map<String, Object>> rows =
        engine.generate(meta, new Variables(), parents, children, List.of(), List.of());

    assertEquals(3, rows.size());
    assertTrue(rows.stream().allMatch(row -> "ASN000001".equals(row.get("asn_id"))));
    assertEquals("PKG-1", rows.get(0).get("package_id"));
    assertEquals("PKG-2", rows.get(2).get("package_id"));
    assertEquals(List.of("P1", "P2", "P3"), rows.stream().map(row -> row.get("product_id")).toList());
  }

  private PipelineMeta loadRetailPipeline() throws Exception {
    return new PipelineMeta(
        "retail-example/pipelines/generate-retail-data.hpl",
        new MemoryMetadataProvider(),
        new Variables());
  }

  private static SyntheticDataMeta synthetic(PipelineMeta pipeline, String name) {
    return (SyntheticDataMeta) pipeline.findTransform(name).getTransform();
  }

  private static Variables retailVariables(String mode, String progressDate, String periodMonths) {
    Variables variables = new Variables();
    variables.setVariable("MODE", mode);
    variables.setVariable("SEED", "42");
    variables.setVariable("PERIOD_MONTHS", periodMonths);
    variables.setVariable("WAVE", "initial".equals(mode) ? "initial" : progressDate.substring(0, 7));
    variables.setVariable("LOAD_DATE", "initial".equals(mode) ? "2024-01-01" : progressDate);
    variables.setVariable("ANCHOR", variables.getVariable("LOAD_DATE", ""));
    variables.setVariable("PROGRESS_DATE", progressDate);
    variables.setVariable("SALES_REPS", "6");
    return variables;
  }

  private static SyntheticDataMeta countMeta() {
    SyntheticDataMeta meta = new SyntheticDataMeta();
    meta.setSeed("7");
    meta.setCardinalityMode("COUNT");
    meta.setRowCount("3");
    meta.getFields().add(field("id", "Integer", "SEQUENCE", "start=1;step=1"));
    meta.getFields().add(field("bucket", "String", "CHOICE", "values=A|B|C;strategy=round_robin"));
    SyntheticField hidden = field("secret", "String", "CONSTANT", "value=hide");
    hidden.setPublish(false);
    meta.getFields().add(hidden);
    return meta;
  }

  private static SyntheticDataMeta hubMeta() {
    SyntheticDataMeta meta = new SyntheticDataMeta();
    meta.setSeed("42");
    meta.setCardinalityMode("COMBINE");
    SyntheticPopulation range = new SyntheticPopulation();
    range.setKind("RANGE");
    range.setStart(
        "ifEq('${MODE}','initial',1,${CUSTOMERS}+(max(1,${PERIOD_MONTHS})-1)*max(50,div(${CUSTOMERS},100))+1)");
    range.setCount("ifEq('${MODE}','initial',${CUSTOMERS},max(50,div(${CUSTOMERS},100)))");
    meta.getPopulations().add(range);
    meta.getFields().add(field("id", "Integer", "SEQUENCE", "source=population"));
    return meta;
  }

  private static SyntheticDataMeta satelliteMeta() {
    SyntheticDataMeta meta = hubMeta();
    SyntheticPopulation sample = new SyntheticPopulation();
    sample.setKind("SAMPLE");
    sample.setFrom("1");
    sample.setTo("${CUSTOMERS}");
    sample.setCount("ifEq('${MODE}','initial',0,max(50,div(${CUSTOMERS},100)))");
    meta.getPopulations().add(0, sample);
    return meta;
  }

  private static SyntheticDataMeta pathMeta(String seed) {
    SyntheticDataMeta meta = new SyntheticDataMeta();
    meta.setSeed(seed);
    meta.setCardinalityMode("PATHS");
    meta.setFraction("1");
    meta.setSelectorField("order_status");
    meta.getFields().add(field("step", "String", "COPY", "field=step_name"));
    meta.getFields().add(field("partition", "Integer", "MOD_HASH", "field=parent.order_id;divisor=8"));
    return meta;
  }

  private List<Long> ids(SyntheticDataMeta meta, IVariables variables) throws HopException {
    return engine.generate(meta, variables, List.of(), List.of(), List.of(), List.of()).stream()
        .map(row -> (Long) row.get("id"))
        .toList();
  }

  private static SyntheticField field(String name, String type, String generator, String arguments) {
    SyntheticField field = new SyntheticField();
    field.setName(name);
    field.setHopType(type);
    field.setGenerator(generator);
    field.setArguments(arguments);
    return field;
  }

  private static SyntheticXmlNode node(
      String id, String parentId, String type, String element, String groupField, String attributes) {
    SyntheticXmlNode node = new SyntheticXmlNode();
    node.setId(id);
    node.setParentId(parentId);
    node.setNodeType(type);
    node.setElement(element);
    node.setGroupField(groupField);
    node.setAttributes(attributes);
    return node;
  }

  private static IVariables variablesWith(Variables variables, String name, String value) {
    variables.setVariable(name, value);
    return variables;
  }
}
