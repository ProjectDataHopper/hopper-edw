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
 *
 */

package org.apache.hop.datavault.executionmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.executionmap.HopExecutionMapFileType;
import org.apache.hop.datavault.metadata.ModelXmlWriteSupport;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

class ExecutionMapSerializationTest {

  private static final Path RETAIL_HOME =
      Path.of("retail-example").toAbsolutePath().normalize();
  private static final Path ROOT_WORKFLOW =
      RETAIL_HOME.resolve("workflows/update-retail-dv-bv-dm.hwf");

  @TempDir Path tempDir;

  @BeforeAll
  static void initHop() throws HopException {
    HopEnvironment.init();
  }

  @Test
  void xmlRoundTripPreservesNodeAndEdgeCounts() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", RETAIL_HOME.toString());
    CrawlOptions options =
        CrawlOptions.builder().includeGeneratedPipelines(false).captureSnapshots(true).build();

    ExecutionMapCrawler.CrawlResult crawlResult =
        ExecutionMapCrawler.crawl(ROOT_WORKFLOW.toString(), variables, null, options);
    ExecutionMapDocument original = crawlResult.getDocument();

    ExecutionMapDocument loaded = roundTripXml(original, variables);

    assertNotNull(loaded);
    assertEquals(original.getNodesOrEmpty().size(), loaded.getNodesOrEmpty().size());
    assertEquals(original.getEdgesOrEmpty().size(), loaded.getEdgesOrEmpty().size());
    assertEquals(original.getSnapshotsOrEmpty().size(), loaded.getSnapshotsOrEmpty().size());
    assertEquals(original.getRootArtifactPath(), loaded.getRootArtifactPath());
    assertEquals(original.getRootArtifactType(), loaded.getRootArtifactType());
  }

  @Test
  void loadFromWrittenHemFilePreservesCounts() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("PROJECT_HOME", RETAIL_HOME.toString());
    CrawlOptions options =
        CrawlOptions.builder().includeGeneratedPipelines(false).captureSnapshots(true).build();

    ExecutionMapDocument original =
        ExecutionMapCrawler.crawl(ROOT_WORKFLOW.toString(), variables, null, options).getDocument();

    Path output = tempDir.resolve("update-retail-dv-bv-dm.hem");
    // NIO write avoids HopVfs/commons-io classloader issues in surefire; portableize first.
    ExecutionMapPathSupport.portableizeDocument(original, variables);
    String xml =
        ModelXmlWriteSupport.formatModelXml(HopExecutionMapFileType.XML_TAG, original, variables);
    Files.writeString(output, xml, StandardCharsets.UTF_8);

    assertTrue(Files.exists(output));
    ExecutionMapDocument loaded =
        ExecutionMapPersistence.load(output.toString(), null, variables);

    assertEquals(original.getNodesOrEmpty().size(), loaded.getNodesOrEmpty().size());
    assertEquals(original.getEdgesOrEmpty().size(), loaded.getEdgesOrEmpty().size());
  }

  @Test
  void savedHemUsesPortableProjectHomePathsAndNoFilename() throws Exception {
    Variables variables = new Variables();
    String projectHome = RETAIL_HOME.toString().replace('\\', '/');
    variables.setVariable("PROJECT_HOME", projectHome);
    CrawlOptions options =
        CrawlOptions.builder().includeGeneratedPipelines(false).captureSnapshots(true).build();

    ExecutionMapDocument document =
        ExecutionMapCrawler.crawl(ROOT_WORKFLOW.toString(), variables, null, options).getDocument();

    ExecutionMapPathSupport.portableizeDocument(document, variables);
    String xml =
        ModelXmlWriteSupport.formatModelXml(HopExecutionMapFileType.XML_TAG, document, variables);
    Path output = tempDir.resolve("portable-map.hem");
    Files.writeString(output, xml, StandardCharsets.UTF_8);

    assertFalse(xml.contains("<filename>"), "document filename must not be serialized");
    assertTrue(
        document.getRootArtifactPath().startsWith("${PROJECT_HOME}/"),
        () -> "rootArtifactPath not portable: " + document.getRootArtifactPath());
    assertTrue(
        document.getRootArtifactPath().endsWith("workflows/update-retail-dv-bv-dm.hwf"),
        () -> "unexpected root: " + document.getRootArtifactPath());

    // Resolved root must match the absolute crawl input under PROJECT_HOME.
    String resolvedRoot =
        ExecutionMapPathSupport.toResolvedPath(document.getRootArtifactPath(), variables)
            .replace('\\', '/');
    assertEquals(
        ROOT_WORKFLOW.toAbsolutePath().normalize().toString().replace('\\', '/'), resolvedRoot);

    for (var node : document.getNodesOrEmpty()) {
      if (node == null || Utils.isEmpty(node.getPath())) {
        continue;
      }
      String path = node.getPath();
      if (ExecutionMapPathSupport.isLogicalScheme(path)) {
        continue;
      }
      assertTrue(
          path.startsWith("${PROJECT_HOME}/") || path.contains("${"),
          () -> "node path not portable: " + path);
      assertFalse(
          path.startsWith("/home/") || path.matches("^[A-Za-z]:\\\\.*"),
          () -> "absolute host path in node: " + path);
    }

    for (var snapshot : document.getSnapshotsOrEmpty()) {
      if (snapshot == null || Utils.isEmpty(snapshot.getSourcePath())) {
        continue;
      }
      String source = snapshot.getSourcePath();
      if (ExecutionMapPathSupport.isLogicalScheme(source)) {
        continue;
      }
      assertTrue(
          source.startsWith("${PROJECT_HOME}/") || source.contains("${"),
          () -> "snapshot sourcePath not portable: " + source);
    }

    assertFalse(xml.contains(projectHome + "/workflows"), "absolute project path leaked into XML");
  }

  private static ExecutionMapDocument roundTripXml(
      ExecutionMapDocument original, Variables variables) throws Exception {
    String wrapped =
        XmlHandler.aroundTag(
            HopExecutionMapFileType.XML_TAG, XmlMetadataUtil.serializeObjectToXml(original));
    Document document = XmlHandler.loadXmlString(wrapped);
    Node rootNode = XmlHandler.getSubNode(document, HopExecutionMapFileType.XML_TAG);
    ExecutionMapDocument loaded = new ExecutionMapDocument();
    XmlMetadataUtil.deSerializeFromXml(
        rootNode, ExecutionMapDocument.class, loaded, null);
    loaded.setFilename(original.getFilename());
    return loaded;
  }
}