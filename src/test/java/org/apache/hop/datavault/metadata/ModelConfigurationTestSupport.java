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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.apache.hop.datavault.metadata.dimensional.DimensionalConfiguration;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModelConfiguration;
import org.apache.hop.datavault.metadata.xp.RegisterModelConfigurationMetadataExtensionPoint;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;

/** Test helper to register and load shared model-configuration metadata. */
public final class ModelConfigurationTestSupport {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private ModelConfigurationTestSupport() {}

  public static final Path INTEGRATION_TESTS = Path.of("integration-tests").toAbsolutePath();
  public static final Path RETAIL_EXAMPLE = Path.of("retail-example").toAbsolutePath();

  public static void registerTypes() throws Exception {
    new RegisterModelConfigurationMetadataExtensionPoint()
        .callExtensionPoint(LogChannel.GENERAL, new Variables(), PluginRegistry.getInstance());
  }

  public static IHopMetadataProvider prepare(IHopMetadataProvider existing, Path projectHome)
      throws Exception {
    registerTypes();
    IHopMetadataProvider provider = existing != null ? existing : new MemoryMetadataProvider();
    loadProjectMetadata(provider, projectHome);
    return provider;
  }

  public static void attachIntegrationTests(Object model, IHopMetadataProvider existing)
      throws Exception {
    IHopMetadataProvider provider = prepare(existing, INTEGRATION_TESTS);
    ModelConfigurationResolver.attach(model, provider);
  }

  public static void loadProjectMetadata(IHopMetadataProvider provider, Path projectHome)
      throws Exception {
    if (provider == null || projectHome == null) {
      return;
    }
    loadFolder(
        provider,
        projectHome.resolve("metadata/source-model-configuration"),
        SourceModelConfiguration.class);
    loadFolder(
        provider,
        projectHome.resolve("metadata/data-vault-configuration"),
        DataVaultConfiguration.class);
    loadFolder(
        provider,
        projectHome.resolve("metadata/business-vault-configuration"),
        BusinessVaultConfiguration.class);
    loadFolder(
        provider,
        projectHome.resolve("metadata/dimensional-configuration"),
        DimensionalConfiguration.class);
  }

  private static <T extends IHopMetadata> void loadFolder(
      IHopMetadataProvider provider, Path folder, Class<T> type) throws Exception {
    if (!Files.isDirectory(folder)) {
      return;
    }
    try (var stream = Files.list(folder)) {
      for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
        T loaded = MAPPER.readValue(file.toFile(), type);
        if (loaded.getName() == null || loaded.getName().isBlank()) {
          String filename = file.getFileName().toString();
          loaded.setName(filename.substring(0, filename.length() - ".json".length()));
        }
        provider.getSerializer(type).save(loaded);
      }
    }
  }
}
