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
package org.apache.hop.datavault.hopgui.perspective.journey;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.hop.catalog.metadata.ResourceDefinitionGroupModelDiscoverySupport;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.ActionRef;
import org.apache.hop.datavault.hopgui.perspective.journey.EdwJourneySnapshot.WorkflowRef;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Lightweight {@code .hwf} scan for actions that name a resource definition group. Does not load
 * {@code WorkflowMeta} or run an execution-map crawl.
 */
public final class EdwJourneyWorkflowScanner {

  static final Set<String> SKIP_DIRECTORY_NAMES =
      Set.of(
          ".git",
          ".svn",
          ".hg",
          ".idea",
          ".vscode",
          ".settings",
          "target",
          "build",
          "out",
          "node_modules",
          ".gradle",
          "dist",
          "tmp",
          "temp",
          "work");

  private EdwJourneyWorkflowScanner() {}

  public static List<WorkflowRef> scan(String groupName, IVariables variables, Path projectHome) {
    List<WorkflowRef> hits = new ArrayList<>();
    if (Utils.isEmpty(groupName) || projectHome == null || !Files.isDirectory(projectHome)) {
      return hits;
    }
    List<Path> workflowFiles = new ArrayList<>();
    try {
      Files.walkFileTree(
          projectHome,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (dir.equals(projectHome)) {
                return FileVisitResult.CONTINUE;
              }
              String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
              if (SKIP_DIRECTORY_NAMES.contains(name)) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (attrs.isRegularFile()
                  && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".hwf")) {
                workflowFiles.add(file);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (Exception ignored) {
      return hits;
    }

    for (Path file : workflowFiles) {
      WorkflowRef ref = scanFile(file, groupName, variables);
      if (ref != null) {
        hits.add(ref);
      }
    }
    hits.sort(Comparator.comparing(WorkflowRef::storedPath, String.CASE_INSENSITIVE_ORDER));
    return hits;
  }

  static WorkflowRef scanFile(Path file, String groupName, IVariables variables) {
    if (file == null || Utils.isEmpty(groupName) || !Files.isRegularFile(file)) {
      return null;
    }
    try {
      Document document = XmlHandler.loadXmlFile(file.toAbsolutePath().toString());
      if (document == null) {
        return null;
      }
      Node workflowNode = XmlHandler.getSubNode(document, "workflow");
      if (workflowNode == null) {
        workflowNode = document.getDocumentElement();
      }
      String workflowName = XmlHandler.getTagValue(workflowNode, "name");
      Node actionsNode = XmlHandler.getSubNode(workflowNode, "actions");
      if (actionsNode == null) {
        return null;
      }
      List<ActionRef> matching = new ArrayList<>();
      NodeList children = actionsNode.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node actionNode = children.item(i);
        if (actionNode == null || !"action".equals(actionNode.getNodeName())) {
          continue;
        }
        String groupValue = XmlHandler.getTagValue(actionNode, "resourceDefinitionGroup");
        if (Utils.isEmpty(groupValue)) {
          continue;
        }
        String resolved = variables != null ? variables.resolve(groupValue) : groupValue;
        if (groupName.equals(resolved) || groupName.equals(groupValue)) {
          matching.add(
              new ActionRef(
                  XmlHandler.getTagValue(actionNode, "name"),
                  XmlHandler.getTagValue(actionNode, "type")));
        }
      }
      if (matching.isEmpty()) {
        return null;
      }
      String stored =
          ResourceDefinitionGroupModelDiscoverySupport.toProjectRelativePath(
              file.toAbsolutePath().normalize().toString(), variables);
      if (Utils.isEmpty(stored)) {
        stored = file.toAbsolutePath().normalize().toString().replace('\\', '/');
      }
      if (Utils.isEmpty(workflowName)) {
        workflowName = EdwJourneyDisplayNames.basenameWithoutExtension(stored);
      }
      return new WorkflowRef(stored, workflowName, matching);
    } catch (Exception ignored) {
      return null;
    }
  }
}
