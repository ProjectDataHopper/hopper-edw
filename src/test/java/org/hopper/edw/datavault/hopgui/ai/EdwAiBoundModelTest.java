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
package org.hopper.edw.datavault.hopgui.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.hop.ai.advisor.AiAdvisorInclusionChoice;
import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.ai.advisor.AiProposal;
import org.apache.hop.ai.advisor.AiProposalValidation;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.edw.datavault.ai.DataVaultAiAdvisor;
import org.hopper.edw.datavault.ai.EdwAiInclusions;
import org.hopper.edw.datavault.ai.businessvault.BusinessVaultAiAdvisor;
import org.hopper.edw.datavault.ai.dimensional.DimensionalAiAdvisor;
import org.hopper.edw.datavault.ai.sourcemodel.SourceModelAiAdvisor;
import org.hopper.edw.datavault.ai.dimensional.DmAiContextBuilder;
import org.hopper.edw.datavault.metadata.coaching.CoachingSourceRef;
import org.hopper.edw.datavault.metadata.coaching.CoachingSourceType;
import org.hopper.edw.datavault.metadata.DataVaultConfiguration;
import org.hopper.edw.datavault.metadata.DataVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultModel;
import org.hopper.edw.datavault.metadata.businessvault.BvScd2Table;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModel;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceTable;
import org.hopper.edw.datavault.metadata.dimensional.DmDimensionAlias;
import org.junit.jupiter.api.Test;

class EdwAiBoundModelTest {

  @Test
  void keepsTheModelAlreadyOnTheRequest() {
    DimensionalModel model = model("orders.hdm");
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(model);

    assertSame(model, EdwAiBoundModel.bind(request, DimensionalModel.class));
    assertSame(model, request.getArtifact());
  }

  @Test
  void usesTheOnlyOpenModelWhenTheSessionLostIt() {
    DimensionalModel open = model("orders.hdm");

    assertSame(
        open, EdwAiBoundModel.select(null, "pipeline", List.of(open), DimensionalModel.class));
  }

  @Test
  void prefersTheActiveEditorWhenSeveralModelsAreOpen() {
    DimensionalModel orders = model("orders.hdm");
    DimensionalModel customers = model("customers.hdm");

    assertSame(
        customers,
        EdwAiBoundModel.select(
            null, customers, List.of(orders, customers), DimensionalModel.class));
  }

  @Test
  void doesNotGuessWhenSeveralModelsAreOpenAndAnotherFileIsActive() {
    DimensionalModel orders = model("orders.hdm");
    DimensionalModel customers = model("customers.hdm");

    assertNull(
        EdwAiBoundModel.select(
            null, "pipeline", List.of(orders, customers), DimensionalModel.class));
  }

  @Test
  void rebindsAClosedCopyToTheReopenedFile() {
    DimensionalModel closed = model("orders.hdm");
    DimensionalModel reopened = model("orders.hdm");

    assertSame(
        reopened,
        EdwAiBoundModel.select(closed, reopened, List.of(reopened), DimensionalModel.class));
  }

  @Test
  void keepsAClosedModelWhenThatFileIsNotOpen() {
    DimensionalModel closed = model("orders.hdm");
    DimensionalModel other = model("customers.hdm");

    assertSame(
        closed, EdwAiBoundModel.select(closed, other, List.of(other), DimensionalModel.class));
  }

  @Test
  void matchesAnOpenModelByFilenameWhenTheArtifactIsAnotherType() {
    DimensionalModel open = model("/project/orders.hdm");
    FilenameArtifact linked = new FilenameArtifact("/project/orders.hdm");

    assertSame(
        open,
        EdwAiBoundModel.select(linked, "pipeline", List.of(open), DimensionalModel.class));
  }

  @Test
  void unboundMessageNamesAForeignArtifact() {
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact("not-a-model");

    assertEquals(
        "No dimensional model is bound to this session.",
        EdwAiBoundModel.unbound("dimensional model", new AiAdvisorRequest()));
    assertTrue(
        EdwAiBoundModel.unbound("dimensional model", request).contains("java.lang.String"));
  }

  @Test
  void promptAcceptsAModelFromAnotherClassLoader() throws Exception {
    ClassLoader loader = new HopperFirstLoader(EdwAiBoundModelTest.class.getClassLoader());
    Class<?> foreignType = loader.loadClass(DimensionalModel.class.getName());
    Object foreign = foreignType.getDeclaredConstructor().newInstance();
    foreignType.getMethod("setName", String.class).invoke(foreign, "retail-f-orders");
    assertFalse(foreign instanceof DimensionalModel);
    assertTrue(EdwAiBoundModel.isForeignModel(foreign, DimensionalModel.class));

    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(foreign);
    request.setUserPrompt("What facts are in this model?");

    AiAdvisorPrompt prompt = new DimensionalAiAdvisor().buildPrompt(request);

    assertNotNull(prompt);
    assertNotNull(prompt.getUserPrompt());
    assertTrue(prompt.getUserPrompt().contains("What facts are in this model?"));
    assertTrue(prompt.getUserPrompt().contains("retail-f-orders"));
    assertSame(foreign, request.getArtifact());
  }

  @Test
  void dataVaultPromptAcceptsAModelFromAnotherClassLoader() throws Exception {
    ClassLoader loader = new HopperFirstLoader(EdwAiBoundModelTest.class.getClassLoader());
    Class<?> foreignType = loader.loadClass(DataVaultModel.class.getName());
    Object foreign = foreignType.getDeclaredConstructor().newInstance();
    foreignType.getMethod("setNameSynchronizedWithFilename", boolean.class).invoke(foreign, false);
    foreignType.getMethod("setName", String.class).invoke(foreign, "retail-360");
    assertFalse(foreign instanceof DataVaultModel);
    assertTrue(EdwAiBoundModel.isForeignModel(foreign, DataVaultModel.class));

    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(foreign);
    request.setUserPrompt("What hubs are in this model?");

    AiAdvisorPrompt prompt = new DataVaultAiAdvisor().buildPrompt(request);

    assertNotNull(prompt);
    assertTrue(prompt.getUserPrompt().contains("What hubs are in this model?"));
    assertTrue(prompt.getUserPrompt().contains("retail-360"));
    assertSame(foreign, request.getArtifact());
  }

  @Test
  void coachCatalogSourcesIgnoreNonRecordEntries() {
    DataVaultModel model = new DataVaultModel();
    model
        .getCoachingOrDefault()
        .addCoachingSource(
            CoachingSourceRef.forRecordDefinition(
                "local-catalog", "hop/retail-example/sources", "E2E-customer-address"));
    CoachingSourceRef sql = new CoachingSourceRef();
    sql.setSourceType(CoachingSourceType.SQL);
    sql.setDerivedFromTable("orders");
    model.getCoachingOrDefault().addCoachingSource(sql);

    List<EdwAiBoundModel.CatalogCoachSource> sources = EdwAiBoundModel.coachCatalogSources(model);

    assertEquals(1, sources.size());
    assertEquals("E2E-customer-address", sources.get(0).recordName());
    assertEquals("local-catalog", sources.get(0).catalogConnection());
    assertEquals("hop/retail-example/sources", sources.get(0).recordNamespace());
    assertEquals("local-catalog", EdwAiBoundModel.catalogConnectionName(model, "fallback"));

    model.getConfigurationOrDefault().setDataCatalogConnection("vault-catalog");
    assertEquals("vault-catalog", EdwAiBoundModel.catalogConnectionName(model, "local-catalog"));
  }

  @Test
  void catalogPickerListsCoachSourcesWhenTheCatalogIsUnavailable() throws Exception {
    Object foreign = foreignDataVaultWithCoach("E2E-customer-address");
    assertFalse(foreign instanceof DataVaultModel);

    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(foreign);

    List<AiAdvisorInclusionChoice> choices =
        new DataVaultAiAdvisor().listInclusionChoices(EdwAiInclusions.CATALOG, request);

    assertFalse(choices.isEmpty());
    assertEquals("E2E-customer-address", choices.get(0).getId());
    assertTrue(choices.get(0).getLabel().contains("Coach"));
    assertTrue(choices.get(0).getLabel().contains("E2E-customer-address"));
  }

  @Test
  void proposalsValidateAgainstAModelFromAnotherClassLoader() throws Exception {
    Object foreign = foreignDataVaultWithHub("HUB_CUSTOMER");
    assertFalse(foreign instanceof DataVaultModel);

    AiProposal link = new AiProposal();
    link.setId("link-1");
    link.setType("ADD_LINK");
    link.setDescription("Order link");
    link.setParameters(Map.of("name", "LNK_ORDER_CUSTOMER", "hubNames", "HUB_CUSTOMER,HUB_ORDER"));
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(foreign);

    List<AiProposalValidation> results =
        new DataVaultAiAdvisor().validateProposals(request, List.of(link));

    assertEquals(1, results.size());
    assertTrue(results.get(0).isBlocked());
    assertTrue(results.get(0).getReason().contains("HUB_ORDER"), results.get(0).getReason());
  }

  @Test
  void emptyModelWithConfigurationValidatesProposals() throws Exception {
    Object foreign = foreignDataVaultWithHub(null);
    foreign.getClass().getMethod("setConfigurationName", String.class).invoke(foreign, "data-vault");
    assertEquals(0, ((List<?>) foreign.getClass().getMethod("getTables").invoke(foreign)).size());

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    DataVaultConfiguration configuration = new DataVaultConfiguration();
    configuration.setName("data-vault");
    configuration.setDataCatalogConnection("local-catalog");
    provider.getSerializer(DataVaultConfiguration.class).save(configuration);

    AiProposal hub = new AiProposal();
    hub.setId("hub-1");
    hub.setType("ADD_HUB");
    hub.setDescription("Customer hub");
    hub.setParameters(Map.of("name", "HUB_CUSTOMER", "recordSource", "E2E-customer-address"));
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(foreign);
    request.setMetadataProvider(provider);

    List<AiProposalValidation> results =
        new DataVaultAiAdvisor().validateProposals(request, List.of(hub));

    assertEquals(1, results.size());
    assertTrue(results.get(0).isBlocked());
    assertTrue(results.get(0).getReason().contains("local-catalog"), results.get(0).getReason());
  }

  @Test
  void validateProposalsDoesNotFailWhenNothingIsOpen() {
    AiProposal link = new AiProposal();
    link.setType("ADD_LINK");
    link.setParameters(Map.of("name", "LNK_ORDER_CUSTOMER", "hubNames", "HUB_CUSTOMER,HUB_ORDER"));
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact("not-a-model");

    List<AiProposalValidation> results =
        new DataVaultAiAdvisor().validateProposals(request, List.of(link));

    assertEquals(1, results.size());
    assertTrue(results.get(0).isBlocked());
    assertEquals("No model is open", results.get(0).getReason());
  }

  @Test
  void applyWritesANoteBackOntoAModelFromAnotherClassLoader() throws Exception {
    Object foreign = foreignDataVaultWithHub("HUB_CUSTOMER");
    AiProposal note = new AiProposal();
    note.setType("ADD_MODEL_NOTE");
    note.setDescription("Add a note");
    note.setParameters(Map.of("text", "Model the address feed as a satellite"));
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(foreign);

    new DataVaultAiAdvisor().applyProposals(request, List.of(note));

    Object notes = foreign.getClass().getMethod("getNotes").invoke(foreign);
    assertTrue(notes instanceof List<?> list && !list.isEmpty());
    Object text = ((List<?>) notes).get(0).getClass().getMethod("getText").invoke(((List<?>) notes).get(0));
    assertEquals("Model the address feed as a satellite", text);
  }

  @Test
  void catalogInclusionAddsCoachSourceToThePrompt() throws Exception {
    Object foreign = foreignDataVaultWithCoach("E2E-customer-address");
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(foreign);
    request.setUserPrompt("Which hubs should I add for this address feed?");
    request.getInclusions().put(EdwAiInclusions.CATALOG, true);

    AiAdvisorPrompt prompt = new DataVaultAiAdvisor().buildPrompt(request);

    assertTrue(prompt.getUserPrompt().contains("Which hubs should I add for this address feed?"));
    assertTrue(prompt.getUserPrompt().contains("Catalog record definitions JSON"));
    assertTrue(prompt.getUserPrompt().contains("E2E-customer-address"));
  }

  @Test
  void sourcePromptAcceptsAModelFromAnotherClassLoader() throws Exception {
    Object foreign =
        foreignModelWithTable(
            SourceModel.class, "source-tables-crm", SourceTable.class, "customer_address");
    assertFalse(foreign instanceof SourceModel);

    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(foreign);
    request.setUserPrompt("What tables are on this source model?");

    AiAdvisorPrompt prompt = new SourceModelAiAdvisor().buildPrompt(request);

    assertNotNull(prompt);
    assertTrue(prompt.getUserPrompt().contains("What tables are on this source model?"));
    assertTrue(prompt.getUserPrompt().contains("source-tables-crm"));
    assertTrue(prompt.getUserPrompt().contains("customer_address"));
    assertSame(foreign, request.getArtifact());
  }

  @Test
  void businessVaultPromptAcceptsAModelFromAnotherClassLoader() throws Exception {
    Object foreign =
        foreignModelWithTable(
            BusinessVaultModel.class, "retail-360", BvScd2Table.class, "customer_360_bv");
    assertFalse(foreign instanceof BusinessVaultModel);

    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setArtifact(foreign);
    request.setUserPrompt("What SCD2 tables are in this model?");

    AiAdvisorPrompt prompt = new BusinessVaultAiAdvisor().buildPrompt(request);

    assertNotNull(prompt);
    assertTrue(prompt.getUserPrompt().contains("What SCD2 tables are in this model?"));
    assertTrue(prompt.getUserPrompt().contains("retail-360"));
    assertTrue(prompt.getUserPrompt().contains("customer_360_bv"));
    assertSame(foreign, request.getArtifact());
  }

  @Test
  void structureJsonDoesNotOpenAnAliasedModelFile() {
    DimensionalModel model = new DimensionalModel();
    model.setName("orders");
    DmDimensionAlias alias = new DmDimensionAlias();
    alias.setName("d_customer");
    alias.setReferencedDimensionName("d_customer");
    alias.setReferencedModelFilename("${PROJECT_HOME}/models/does-not-exist.hdm");
    model.getTables().add(alias);

    String json = DmAiContextBuilder.serializeModelStructure(model);

    assertTrue(json.contains("d_customer"));
    assertTrue(json.contains("does-not-exist.hdm"));
  }

  @Test
  void buildPromptWithoutAnOpenModelStillSaysTheSessionIsUnbound() {
    AiAdvisorRequest request = new AiAdvisorRequest();
    request.setUserPrompt("How should this fact be modeled?");
    HopException error =
        assertThrows(HopException.class, () -> new DimensionalAiAdvisor().buildPrompt(request));
    assertTrue(error.getMessage().contains("No dimensional model is bound to this session."));
  }

  @SuppressWarnings("unchecked")
  private static Object foreignDataVaultWithHub(String hubName) throws Exception {
    ClassLoader loader = new HopperFirstLoader(EdwAiBoundModelTest.class.getClassLoader());
    Class<?> modelType = loader.loadClass(DataVaultModel.class.getName());
    Object model = modelType.getDeclaredConstructor().newInstance();
    modelType.getMethod("setNameSynchronizedWithFilename", boolean.class).invoke(model, false);
    modelType.getMethod("setName", String.class).invoke(model, "retail-360");
    if (hubName == null) {
      return model;
    }
    Class<?> hubType = loader.loadClass("org.hopper.edw.datavault.metadata.DvHub");
    Object hub = hubType.getDeclaredConstructor(String.class).newInstance(hubName);
    List<Object> tables = (List<Object>) modelType.getMethod("getTables").invoke(model);
    tables.add(hub);
    return model;
  }

  private static Object foreignDataVaultWithCoach(String recordName) throws Exception {
    ClassLoader loader = new HopperFirstLoader(EdwAiBoundModelTest.class.getClassLoader());
    Class<?> modelType = loader.loadClass(DataVaultModel.class.getName());
    Object model = modelType.getDeclaredConstructor().newInstance();
    modelType.getMethod("setNameSynchronizedWithFilename", boolean.class).invoke(model, false);
    modelType.getMethod("setName", String.class).invoke(model, "retail-360");
    Class<?> refType =
        loader.loadClass("org.hopper.edw.datavault.metadata.coaching.CoachingSourceRef");
    Object ref =
        refType
            .getMethod("forRecordDefinition", String.class, String.class, String.class)
            .invoke(null, "local-catalog", "hop/retail-example/sources", recordName);
    Object coaching = modelType.getMethod("getCoachingOrDefault").invoke(model);
    coaching.getClass().getMethod("addCoachingSource", refType).invoke(coaching, ref);
    return model;
  }

  @SuppressWarnings("unchecked")
  private static Object foreignModelWithTable(
      Class<?> modelClass, String modelName, Class<?> tableClass, String tableName)
      throws Exception {
    ClassLoader loader = new HopperFirstLoader(EdwAiBoundModelTest.class.getClassLoader());
    Class<?> foreignModelType = loader.loadClass(modelClass.getName());
    Object model = foreignModelType.getDeclaredConstructor().newInstance();
    foreignModelType
        .getMethod("setNameSynchronizedWithFilename", boolean.class)
        .invoke(model, false);
    foreignModelType.getMethod("setName", String.class).invoke(model, modelName);
    Class<?> foreignTableType = loader.loadClass(tableClass.getName());
    Object table = foreignTableType.getDeclaredConstructor().newInstance();
    foreignTableType.getMethod("setName", String.class).invoke(table, tableName);
    List<Object> tables = (List<Object>) foreignModelType.getMethod("getTables").invoke(model);
    tables.add(table);
    return model;
  }

  private static DimensionalModel model(String filename) {
    DimensionalModel model = new DimensionalModel();
    model.setName(filename);
    model.setFilename(filename);
    return model;
  }

  /**
   * Defines {@code org.hopper.*} itself and delegates everything else. That is the split Hop hits
   * when two plugin class loaders each own a copy of this jar.
   */
  private static final class HopperFirstLoader extends ClassLoader {
    private HopperFirstLoader(ClassLoader parent) {
      super(parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.startsWith("org.hopper.")) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
          String path = name.replace('.', '/') + ".class";
          try (InputStream in = getParent().getResourceAsStream(path)) {
            if (in == null) {
              throw new ClassNotFoundException(name);
            }
            byte[] bytes = in.readAllBytes();
            loaded = defineClass(name, bytes, 0, bytes.length);
          } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
          }
        }
        if (resolve) {
          resolveClass(loaded);
        }
        return loaded;
      }
    }
  }

  private static final class FilenameArtifact implements org.apache.hop.core.file.IHasFilename {
    private final String filename;

    private FilenameArtifact(String filename) {
      this.filename = filename;
    }

    @Override
    public String getFilename() {
      return filename;
    }
  }
}
