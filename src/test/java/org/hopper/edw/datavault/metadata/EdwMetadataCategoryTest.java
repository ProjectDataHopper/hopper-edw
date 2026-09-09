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
package org.hopper.edw.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataCategory;
import org.hopper.edw.catalog.metadata.DataCatalogMeta;
import org.hopper.edw.catalog.metadata.ResourceDefinitionGroupMeta;
import org.hopper.edw.datavault.metadata.businessvault.BusinessVaultConfiguration;
import org.hopper.edw.datavault.metadata.datatypemapping.DataTypeMappingMeta;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalConfiguration;
import org.hopper.edw.datavault.metadata.jinja.JinjaMacroLibraryMeta;
import org.hopper.edw.datavault.metadata.lineage.LineageBackendMeta;
import org.hopper.edw.datavault.metadata.sourcemodel.SourceModelConfiguration;
import org.hopper.edw.datavault.metadata.sourcemodel.service.SourceModelService;
import org.hopper.edw.datavault.metadata.targettypemapping.TargetTypeMappingMeta;
import org.hopper.edw.datavault.metrics.metadata.ExecutionMetricsProfileMeta;
import org.hopper.edw.quality.metadata.DataQualityRuleSetMeta;
import org.junit.jupiter.api.Test;

class EdwMetadataCategoryTest {

  private static final List<Class<?>> EDW_METADATA_TYPES =
      List.of(
          BusinessVaultConfiguration.class,
          DataCatalogMeta.class,
          DataQualityRuleSetMeta.class,
          DataTypeMappingMeta.class,
          DataVaultConfiguration.class,
          DimensionalConfiguration.class,
          ExecutionMetricsProfileMeta.class,
          JinjaMacroLibraryMeta.class,
          LineageBackendMeta.class,
          ResourceDefinitionGroupMeta.class,
          SourceModelConfiguration.class,
          TargetTypeMappingMeta.class);

  private static final Pattern HOP_METADATA_ANNOTATION =
      Pattern.compile("@HopMetadata\\s*\\(([^)]*)\\)", Pattern.DOTALL);

  @Test
  void pluginMetadataTypesUseTheEdwCategory() {
    for (Class<?> type : EDW_METADATA_TYPES) {
      HopMetadata annotation = type.getAnnotation(HopMetadata.class);
      assertNotNull(annotation, type.getName() + " must be a @HopMetadata type");
      assertEquals(
          EdwMetadataCategory.EDW,
          annotation.category(),
          type.getSimpleName() + " must appear under EDW, not Other");
    }
  }

  @Test
  void sourceModelServiceStaysInServers() {
    HopMetadata annotation = SourceModelService.class.getAnnotation(HopMetadata.class);
    assertNotNull(annotation);
    assertEquals(HopMetadataCategory.SERVERS, annotation.category());
  }

  @Test
  void everyHopMetadataAnnotationDeclaresAKnownCategory() throws IOException {
    Path javaRoot = Paths.get(System.getProperty("user.dir"), "src", "main", "java");
    assertTrue(Files.isDirectory(javaRoot), javaRoot.toString());

    List<String> issues = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(javaRoot)) {
      walk.filter(path -> path.toString().endsWith(".java"))
          .forEach(path -> collectCategoryIssues(path, javaRoot, issues));
    }

    assertTrue(
        issues.isEmpty(),
        () ->
            issues.size()
                + " @HopMetadata type(s) without EdwMetadataCategory.EDW or HopMetadataCategory.SERVERS:"
                + System.lineSeparator()
                + String.join(System.lineSeparator(), issues));
  }

  private static void collectCategoryIssues(Path file, Path javaRoot, List<String> issues) {
    String source;
    try {
      source = Files.readString(file);
    } catch (IOException e) {
      issues.add(javaRoot.relativize(file) + ": " + e.getMessage());
      return;
    }
    if (!source.contains("@HopMetadata(")) {
      return;
    }
    Matcher matcher = HOP_METADATA_ANNOTATION.matcher(source);
    assertTrue(
        matcher.find(),
        () ->
            javaRoot.relativize(file)
                + " mentions @HopMetadata( but the annotation was not parsed");
    do {
      String body = matcher.group(1);
      boolean edw = body.contains("category = EdwMetadataCategory.EDW");
      boolean servers = body.contains("category = HopMetadataCategory.SERVERS");
      assertFalse(
          edw && servers, javaRoot.relativize(file) + " declares both EDW and SERVERS categories");
      if (!edw && !servers) {
        issues.add(javaRoot.relativize(file).toString());
      }
    } while (matcher.find());
  }
}
