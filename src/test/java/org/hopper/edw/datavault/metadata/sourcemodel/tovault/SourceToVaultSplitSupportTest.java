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
package org.hopper.edw.datavault.metadata.sourcemodel.tovault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.naming.metadata.NamingCaseStyle;
import org.apache.hop.naming.metadata.NamingScheme;
import org.apache.hop.naming.metadata.NamingWordSeparator;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.DvHub;
import org.hopper.edw.datavault.metadata.DvLink;
import org.hopper.edw.datavault.metadata.DvLinkedTable;
import org.hopper.edw.datavault.metadata.DvModelLoadSupport;
import org.hopper.edw.datavault.metadata.DvReferenceTable;
import org.hopper.edw.datavault.metadata.DvSatellite;
import org.hopper.edw.datavault.metadata.IDvTable;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceColumn;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceRelationship;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.metadata.sourcemodel.tovault.SourceToVaultSplitOptions.ExistingFilePolicy;
import org.hopper.edw.datavault.naming.EdwNamingSchemeTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceToVaultSplitSupportTest {

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @AfterEach
  void clearModelCache() {
    DvModelLoadSupport.clearCache();
  }

  @Test
  void retailSplitSeparatesHubsLinksAndLinkSatellites(@TempDir Path temp) throws Exception {
    Variables variables = projectVariables(temp);
    SourceToVaultSplitResult split = split(retailLikeModel(), options(temp), variables, null);

    SourceToVaultSplitModel customer = find(split, "hub_customer");
    assertEquals(SourceToVaultSplitModel.Kind.HUB, customer.getKind());
    assertTrue(customer.getFilename().endsWith("/hub_customer.hdv"));
    assertInstanceOf(DvHub.class, customer.getModel().findTable("hub_customer"));
    assertInstanceOf(DvSatellite.class, customer.getModel().findTable("sat_customer_demo"));
    assertFalse(containsType(customer.getModel(), DvLink.class));
    assertFalse(containsType(customer.getModel(), DvLinkedTable.class));

    SourceToVaultSplitModel orderLink = find(split, "lnk_order");
    assertEquals(SourceToVaultSplitModel.Kind.LINK, orderLink.getKind());
    assertInstanceOf(DvLink.class, orderLink.getModel().findTable("lnk_order"));
    assertFalse(containsType(orderLink.getModel(), DvHub.class));
    DvLinkedTable customerAlias =
        assertInstanceOf(DvLinkedTable.class, orderLink.getModel().findTable("hub_customer"));
    assertTrue(
        customerAlias.getReferencedModelFilename().contains("${PROJECT_HOME}"),
        customerAlias.getReferencedModelFilename());
    assertTrue(customerAlias.getReferencedModelFilename().contains("hub_customer.hdv"));
    assertNotNull(orderLink.getModel().findTable("hub_order"));

    SourceToVaultSplitModel lineLink = find(split, "lnk_order_line");
    assertInstanceOf(DvSatellite.class, lineLink.getModel().findTable("sat_lnk_order_line"));
    assertInstanceOf(DvLinkedTable.class, lineLink.getModel().findTable("hub_order"));
    assertInstanceOf(DvLinkedTable.class, lineLink.getModel().findTable("hub_product"));
    assertFalse(containsType(lineLink.getModel(), DvHub.class));
    assertEquals(5, split.getModels().size());
  }

  @Test
  void hierarchyAliasPointsAtHubFile(@TempDir Path temp) throws Exception {
    Variables variables = projectVariables(temp);
    SourceModel model = new SourceModel();
    model.getTables().add(table("employee", pk("employee_id"), col("manager_id"), col("full_name")));
    model.getRelationships().add(rel("employee", "employee", "manager_id"));

    SourceToVaultSplitResult split = split(model, options(temp), variables, null);
    SourceToVaultSplitModel hub = find(split, "hub_employee");
    assertInstanceOf(DvHub.class, hub.getModel().findTable("hub_employee"));
    assertInstanceOf(DvSatellite.class, hub.getModel().findTable("sat_employee"));
    assertFalse(containsType(hub.getModel(), DvLink.class));
    assertFalse(containsType(hub.getModel(), DvLinkedTable.class));

    SourceToVaultSplitModel link = find(split, "lnk_employee_hierarchy");
    assertFalse(containsType(link.getModel(), DvHub.class));
    DvLinkedTable natural =
        assertInstanceOf(DvLinkedTable.class, link.getModel().findTable("hub_employee"));
    DvLinkedTable parent =
        assertInstanceOf(DvLinkedTable.class, link.getModel().findTable("hub_employee_parent"));
    assertEquals("employee_parent_hk", parent.getHashKeyFieldName());
    assertTrue(natural.getReferencedModelFilename().contains("hub_employee.hdv"));
    assertEquals(natural.getReferencedModelFilename(), parent.getReferencedModelFilename());
    assertTrue(UtilsIsEmpty(natural.getHashKeyFieldName()));
  }

  @Test
  void referenceTableGetsItsOwnModel(@TempDir Path temp) throws Exception {
    SourceModel model = new SourceModel();
    model.getTables().add(table("country", pk("country_code"), col("country_name")));
    model.getTables().add(table("customer_hub", pk("customer_id"), col("country_code")));
    model.getRelationships().add(rel("customer_hub", "country", "country_code"));

    SourceToVaultSplitResult split = split(model, options(temp), projectVariables(temp), null);
    SourceToVaultSplitModel reference = find(split, "ref_country");
    assertEquals(SourceToVaultSplitModel.Kind.REFERENCE, reference.getKind());
    assertEquals(1, reference.getModel().getTables().size());
    assertInstanceOf(DvReferenceTable.class, reference.getModel().findTable("ref_country"));
    assertFalse(containsType(find(split, "hub_customer").getModel(), DvReferenceTable.class));
  }

  @Test
  void prefixAndNamingSchemeChangeTheFileName(@TempDir Path temp) throws Exception {
    SourceToVaultSplitOptions prefixed = options(temp);
    prefixed.setNamePrefix("aw");
    SourceToVaultSplitResult split =
        split(retailLikeModel(), prefixed, projectVariables(temp), null);
    assertTrue(find(split, "hub_customer").getFilename().endsWith("/aw_hub_customer.hdv"));

    prefixed.setNamePrefix("aw-");
    split = split(retailLikeModel(), prefixed, projectVariables(temp), null);
    assertTrue(find(split, "hub_customer").getFilename().endsWith("/aw-hub_customer.hdv"));

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    NamingScheme scheme = new NamingScheme("edw-dv-model-files");
    scheme.setType(EdwNamingSchemeTypes.EDW_DV_MODEL);
    scheme.setCaseStyle(NamingCaseStyle.LOWER.getCode());
    scheme.setWordSeparator(NamingWordSeparator.UNDERSCORE.getCode());
    scheme.setPrefix("dv_");
    scheme.setSuffix("");
    scheme.setRemoveSpecialCharacters(true);
    scheme.setCollapseRepeatedSeparators(true);
    scheme.setTrimEdgeSeparators(true);
    provider.getSerializer(NamingScheme.class).save(scheme);

    prefixed.setNamePrefix("aw");
    split = split(retailLikeModel(), prefixed, projectVariables(temp), provider);
    assertTrue(find(split, "hub_customer").getFilename().endsWith("/dv_aw_hub_customer.hdv"));

    assertThrows(
        HopException.class, () -> SourceToVaultSplitSupport.normalizePrefix("../aw"));
  }

  @Test
  void reloadResolvesHubBusinessKey(@TempDir Path temp) throws Exception {
    Variables variables = projectVariables(temp);
    IHopMetadataProvider provider = new MemoryMetadataProvider();
    SourceToVaultSplitResult split = split(retailLikeModel(), options(temp), variables, provider);
    SourceToVaultSplitSupport.write(split, ExistingFilePolicy.SKIP, variables);
    DvModelLoadSupport.clearCache();

    SourceToVaultSplitModel orderLink = find(split, "lnk_order");
    DataVaultModel loaded =
        DvModelLoadSupport.loadDataVaultModel(
            orderLink.getFilename(), null, variables, provider);
    assertInstanceOf(DvLinkedTable.class, loaded.findTable("hub_customer"));
    DvHub customer = loaded.findHub("hub_customer", variables, provider);
    assertNotNull(customer);
    assertEquals("customer_id", customer.getBusinessKeys().get(0).getName());
  }

  @Test
  void skipLeavesExistingFileAndReplaceOverwritesIt(@TempDir Path temp) throws Exception {
    Variables variables = projectVariables(temp);
    SourceToVaultSplitResult split = split(retailLikeModel(), options(temp), variables, null);
    SourceToVaultSplitWriteResult first =
        SourceToVaultSplitSupport.write(split, ExistingFilePolicy.SKIP, variables);
    assertFalse(first.getWrittenFilenames().isEmpty());

    String hubFile = find(split, "hub_customer").getFilename();
    byte[] sentinel = "sentinel-not-a-model".getBytes(StandardCharsets.UTF_8);
    try (OutputStream out = HopVfs.getOutputStream(hubFile, false)) {
      out.write(sentinel);
    }

    SourceToVaultSplitWriteResult skipped =
        SourceToVaultSplitSupport.write(split, ExistingFilePolicy.SKIP, variables);
    assertTrue(skipped.getSkippedFilenames().contains(hubFile));
    assertTrue(Arrays.equals(sentinel, readBytes(hubFile)));

    SourceToVaultSplitSupport.write(split, ExistingFilePolicy.REPLACE, variables);
    byte[] replaced = readBytes(hubFile);
    assertFalse(Arrays.equals(sentinel, replaced));
    assertTrue(new String(replaced, StandardCharsets.UTF_8).contains("hub_customer"));
  }

  @Test
  void excludedHubOmitsHubAndDependentLink(@TempDir Path temp) throws Exception {
    SourceModel source = retailLikeModel();
    SourceToVaultClassification classification = SourceToVaultClassifier.classify(source);
    for (SourceToVaultProposal proposal : classification.getProposals()) {
      for (ProposedVaultObject object : proposal.getObjects()) {
        if (object.getKind() == ProposedObjectKind.HUB
            && "hub_customer".equalsIgnoreCase(object.getName())) {
          object.setIncluded(false);
        }
      }
    }
    DataVaultModel vault = new DataVaultModel();
    SourceToVaultApplyResult applied =
        SourceToVaultApplySupport.apply(
            source,
            vault,
            classification,
            false,
            new Variables(),
            new MemoryMetadataProvider());
    assertTrue(
        applied.getWarnings().stream()
            .anyMatch(warning -> warning.contains("lnk_order") && warning.contains("hub_customer")));

    SourceToVaultSplitResult split =
        SourceToVaultSplitSupport.partition(vault, options(temp), projectVariables(temp), null);
    assertTrue(split.getModels().stream().noneMatch(model -> "hub_customer".equals(model.getAnchorName())));
    assertTrue(split.getModels().stream().noneMatch(model -> "lnk_order".equals(model.getAnchorName())));
    assertNotNull(find(split, "hub_order"));
    assertNotNull(find(split, "lnk_order_line"));
  }

  private static SourceToVaultSplitResult split(
      SourceModel source,
      SourceToVaultSplitOptions options,
      Variables variables,
      IHopMetadataProvider metadataProvider)
      throws Exception {
    DataVaultModel vault = new DataVaultModel();
    SourceToVaultApplySupport.apply(
        source,
        vault,
        SourceToVaultClassifier.classify(source),
        false,
        variables,
        metadataProvider != null ? metadataProvider : new MemoryMetadataProvider());
    return SourceToVaultSplitSupport.partition(vault, options, variables, metadataProvider);
  }

  private static SourceToVaultSplitOptions options(Path temp) {
    SourceToVaultSplitOptions options = new SourceToVaultSplitOptions();
    options.setOutputFolder(temp.resolve("models").toString());
    return options;
  }

  private static Variables projectVariables(Path temp) {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", temp.toAbsolutePath().toString());
    return variables;
  }

  private static SourceToVaultSplitModel find(SourceToVaultSplitResult split, String anchor) {
    return split.getModels().stream()
        .filter(model -> anchor.equalsIgnoreCase(model.getAnchorName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing split model " + anchor));
  }

  private static boolean containsType(DataVaultModel model, Class<?> type) {
    for (IDvTable table : model.getTables()) {
      if (type.isInstance(table)) {
        return true;
      }
    }
    return false;
  }

  private static boolean UtilsIsEmpty(String value) {
    return value == null || value.isBlank();
  }

  private static byte[] readBytes(String filename) throws Exception {
    try (InputStream in = HopVfs.getInputStream(filename)) {
      return in.readAllBytes();
    }
  }

  private static SourceModel retailLikeModel() {
    SourceModel model = new SourceModel();
    model.getTables().add(table("customer_hub", pk("customer_id"), col("load_date")));
    model.getTables().add(table("customer_demo", pk("customer_id"), col("segment"), col("load_date")));
    model.getTables().add(table("product", pk("product_id"), col("product_name"), col("load_date")));
    model
        .getTables()
        .add(table("order_header", pk("order_id"), col("customer_id"), col("order_date"), col("load_date")));
    model
        .getTables()
        .add(
            table(
                "order_line",
                pk("order_id", 1),
                pk("product_id", 2),
                pk("line_number", 3),
                col("quantity"),
                col("load_date")));
    model.getRelationships().add(rel("customer_hub", "customer_demo", "customer_id"));
    model.getRelationships().add(rel("order_header", "customer_hub", "customer_id"));
    model.getRelationships().add(rel("order_line", "order_header", "order_id"));
    model.getRelationships().add(rel("order_line", "product", "product_id"));
    return model;
  }

  private static SourceTable table(String name, SourceColumn... columns) {
    SourceTable table = new SourceTable(name);
    table.setCatalogSourceName(name);
    for (SourceColumn column : columns) {
      table.getColumns().add(column);
    }
    return table;
  }

  private static SourceColumn pk(String name) {
    return pk(name, 1);
  }

  private static SourceColumn pk(String name, int position) {
    SourceColumn column = new SourceColumn(name);
    column.setPrimaryKeyPosition(position);
    column.setHopType(2);
    return column;
  }

  private static SourceColumn col(String name) {
    SourceColumn column = new SourceColumn(name);
    column.setHopType(2);
    return column;
  }

  private static SourceRelationship rel(String child, String parent, String column) {
    SourceRelationship relationship = new SourceRelationship("fk_" + child + "_" + parent);
    relationship.setChildEndpointKind(SourceEndpointKind.TABLE);
    relationship.setParentEndpointKind(SourceEndpointKind.TABLE);
    relationship.setChildTableName(child);
    relationship.setParentTableName(parent);
    relationship.getChildColumns().add(column);
    relationship.getParentColumns().add(column);
    return relationship;
  }
}
