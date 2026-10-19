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
import org.apache.hop.ai.advisor.AiAdvisorPrompt;
import org.apache.hop.ai.advisor.AiAdvisorRequest;
import org.apache.hop.core.exception.HopException;
import org.hopper.edw.datavault.ai.dimensional.DimensionalAiAdvisor;
import org.hopper.edw.datavault.ai.dimensional.DmAiContextBuilder;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
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
