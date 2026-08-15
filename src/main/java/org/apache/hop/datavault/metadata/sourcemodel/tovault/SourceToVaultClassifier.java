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
package org.apache.hop.datavault.metadata.sourcemodel.tovault;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.hop.core.util.Utils;
import org.apache.hop.datavault.metadata.BusinessKey;
import org.apache.hop.datavault.metadata.DataVaultConfiguration;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.datavault.metadata.DvHub;
import org.apache.hop.datavault.metadata.DvTableType;
import org.apache.hop.datavault.metadata.IDvTable;
import org.apache.hop.datavault.metadata.sourcemodel.SourceColumn;
import org.apache.hop.datavault.metadata.sourcemodel.SourceEndpointKind;
import org.apache.hop.datavault.metadata.sourcemodel.SourceModel;
import org.apache.hop.datavault.metadata.sourcemodel.SourceRelationship;
import org.apache.hop.datavault.metadata.sourcemodel.SourceTable;

/**
 * Classifies source-model tables into raw Data Vault hubs, links, and satellites.
 *
 * <p>Relationship direction is inferred from PK/FK column membership so inverted child/parent
 * labels still classify correctly. Tables that share the same identifying PK become one hub plus
 * one satellite per source table.
 */
public final class SourceToVaultClassifier {

  private SourceToVaultClassifier() {}

  public static SourceToVaultClassification classify(SourceModel sourceModel) {
    return classify(sourceModel, null, null, SourceToVaultOptions.defaults());
  }

  public static SourceToVaultClassification classify(
      SourceModel sourceModel, Collection<String> selectedTableNames) {
    return classify(sourceModel, selectedTableNames, null, SourceToVaultOptions.defaults());
  }

  public static SourceToVaultClassification classify(
      SourceModel sourceModel,
      Collection<String> selectedTableNames,
      DataVaultModel existingVault,
      SourceToVaultOptions options) {
    SourceToVaultClassification result = new SourceToVaultClassification();
    if (sourceModel == null) {
      result.getWarnings().add("Source model is required");
      return result;
    }
    SourceToVaultOptions effective = options != null ? options : SourceToVaultOptions.defaults();
    Set<String> technical = technicalNames(existingVault, effective);

    List<SourceTable> selected = resolveSelectedTables(sourceModel, selectedTableNames);
    List<ClassifiableSource> selectedFeeds =
        effective.isIncludeNonTableSources()
            ? resolveSelectedFeeds(sourceModel, selectedTableNames)
            : List.of();
    if (selected.isEmpty() && selectedFeeds.isEmpty()) {
      result.getWarnings().add("No source tables selected");
      return result;
    }

    List<NormalizedFk> fks = normalizeRelationships(sourceModel, result.getWarnings());
    Map<String, HubCluster> clusters = buildClusters(sourceModel, fks);
    assignHubNames(clusters, existingVault);
    Set<String> referenceNames =
        effective.isCreateReferenceTables()
            ? detectReferenceTables(sourceModel, selected, fks, clusters)
            : Set.of();

    Set<String> selectedNames = new LinkedHashSet<>();
    for (SourceTable table : selected) {
      selectedNames.add(table.getName());
    }

    Set<String> requiredHubClusters = new LinkedHashSet<>();
    Map<String, SourceToVaultProposal> bySource = new LinkedHashMap<>();

    for (SourceTable table : selected) {
      SourceToVaultProposal proposal =
          classifyTable(
              table,
              sourceModel,
              clusters,
              fks,
              technical,
              effective,
              requiredHubClusters,
              referenceNames);
      bySource.put(table.getName(), proposal);
      result.getProposals().add(proposal);
    }

    for (ClassifiableSource feed : selectedFeeds) {
      if (bySource.containsKey(feed.getName())) {
        continue;
      }
      SourceToVaultProposal proposal =
          classifyFeed(
              feed, clusters, fks, technical, effective, requiredHubClusters, referenceNames);
      bySource.put(feed.getName(), proposal);
      result.getProposals().add(proposal);
    }

    if (effective.isIncludeUnselectedParents()) {
      for (String clusterId : requiredHubClusters) {
        HubCluster cluster = clusters.get(clusterId);
        if (cluster == null || cluster.kernel == null) {
          continue;
        }
        if (selectedNames.contains(cluster.kernel.getName())) {
          continue;
        }
        boolean selectedMemberEmitsHub = false;
        for (SourceTable member : cluster.members) {
          SourceToVaultProposal existing = bySource.get(member.getName());
          if (existing != null && existing.firstOfKind(ProposedObjectKind.HUB) != null) {
            selectedMemberEmitsHub = true;
            break;
          }
        }
        if (selectedMemberEmitsHub) {
          continue;
        }
        SourceToVaultProposal implied = impliedHubProposal(cluster);
        result.getProposals().add(implied);
      }
    }

    return result;
  }

  private static SourceToVaultProposal classifyTable(
      SourceTable table,
      SourceModel sourceModel,
      Map<String, HubCluster> clusters,
      List<NormalizedFk> fks,
      Set<String> technical,
      SourceToVaultOptions options,
      Set<String> requiredHubClusters,
      Set<String> referenceNames) {
    SourceToVaultProposal proposal = new SourceToVaultProposal();
    proposal.setSourceTableName(table.getName());

    List<String> pk = pkNames(table);
    if (pk.isEmpty()) {
      proposal.setRole(SourceTableRole.SKIP);
      proposal.setIncluded(false);
      proposal.setConfidence(ClassificationConfidence.HIGH);
      proposal.setSkipReason("Table has no primary key");
      proposal.setEvidence("No primary-key columns");
      return proposal;
    }

    List<NormalizedFk> outgoing = outgoingFks(table.getName(), fks);
    List<NormalizedFk> tableFks = fksInvolving(table.getName(), fks);
    boolean involvedInTableFk = !tableFks.isEmpty();
    HubCluster ownCluster = clusterOf(table.getName(), clusters);

    if (!involvedInTableFk && (ownCluster == null || ownCluster.members.size() <= 1)) {
      proposal.setRole(SourceTableRole.SKIP);
      proposal.setIncluded(false);
      proposal.setConfidence(ClassificationConfidence.MEDIUM);
      proposal.setSkipReason("No table-to-table relationships");
      proposal.setEvidence("Isolated table; classify manually or draw relationships first");
      return proposal;
    }

    Map<String, List<String>> fkGroups = fkGroupsByParentCluster(outgoing, clusters);
    if (isLinkCandidate(pk, fkGroups)) {
      return classifyLink(
          table, pk, fkGroups, outgoing, clusters, technical, options, requiredHubClusters);
    }

    if (referenceNames.contains(table.getName())) {
      return classifyReference(table, pk, technical, options);
    }

    if (ownCluster != null
        && ownCluster.kernel != null
        && !ownCluster.kernel.getName().equals(table.getName())
        && ownCluster.members.size() > 1) {
      return classifySameGrainSatellite(table, ownCluster, pk, technical, options);
    }

    return classifyHub(
        table,
        pk,
        outgoing,
        clusters,
        technical,
        options,
        requiredHubClusters,
        ownCluster,
        referenceNames);
  }

  private static SourceToVaultProposal classifyLink(
      SourceTable table,
      List<String> pk,
      Map<String, List<String>> fkGroups,
      List<NormalizedFk> outgoing,
      Map<String, HubCluster> clusters,
      Set<String> technical,
      SourceToVaultOptions options,
      Set<String> requiredHubClusters) {
    SourceToVaultProposal proposal = new SourceToVaultProposal();
    proposal.setSourceTableName(table.getName());
    proposal.setRole(SourceTableRole.LINK);
    proposal.setIncluded(true);
    proposal.setConfidence(ClassificationConfidence.HIGH);

    ProposedVaultObject link =
        new ProposedVaultObject(
            ProposedObjectKind.LINK, SourceToVaultNaming.linkNameFromTable(table.getName()));
    stampTable(link, table);

    Set<String> fkColumns = new LinkedHashSet<>();
    List<String> hubNames = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : fkGroups.entrySet()) {
      HubCluster parent = clusters.get(entry.getKey());
      if (parent == null) {
        continue;
      }
      requiredHubClusters.add(entry.getKey());
      String hubName = parent.hubName;
      hubNames.add(hubName);
      link.getHubSourceKeyColumns().put(hubName, new ArrayList<>(entry.getValue()));
      fkColumns.addAll(entry.getValue());
    }
    link.getParticipatingHubNames().addAll(hubNames);

    List<String> dcks = new ArrayList<>();
    for (String key : pk) {
      if (!containsIgnoreCase(fkColumns, key)) {
        dcks.add(key);
      }
    }
    link.getDependentChildKeyColumns().addAll(dcks);
    proposal.getObjects().add(link);

    List<String> satCols = satelliteColumns(table, pk, fkColumns, technical, options);
    if (!satCols.isEmpty()) {
      ProposedVaultObject sat =
          new ProposedVaultObject(
              ProposedObjectKind.SATELLITE, SourceToVaultNaming.linkSatelliteName(table.getName()));
      stampTable(sat, table);
      sat.setParentLinkName(link.getName());
      sat.getSatelliteAttributeColumns().addAll(satCols);
      proposal.getObjects().add(sat);
    }

    StringBuilder evidence = new StringBuilder();
    evidence.append("Composite PK uses ").append(fkGroups.size()).append(" foreign keys");
    if (!dcks.isEmpty()) {
      evidence.append(" plus dependent child key(s) ").append(String.join(", ", dcks));
    }
    if (outgoing.stream().anyMatch(fk -> fk.directionFlipped)) {
      evidence.append("; FK direction inferred from column membership");
      proposal.setConfidence(ClassificationConfidence.MEDIUM);
    }
    proposal.setEvidence(evidence.toString());
    return proposal;
  }

  private static SourceToVaultProposal classifySameGrainSatellite(
      SourceTable table,
      HubCluster cluster,
      List<String> pk,
      Set<String> technical,
      SourceToVaultOptions options) {
    SourceToVaultProposal proposal = new SourceToVaultProposal();
    proposal.setSourceTableName(table.getName());
    proposal.setRole(SourceTableRole.SATELLITE);
    proposal.setIncluded(true);
    proposal.setConfidence(ClassificationConfidence.HIGH);

    ProposedVaultObject sat =
        new ProposedVaultObject(
            ProposedObjectKind.SATELLITE,
            SourceToVaultNaming.extensionSatelliteName(table.getName()));
    stampTable(sat, table);
    sat.setParentHubName(cluster.hubName);
    sat.getSatelliteAttributeColumns()
        .addAll(satelliteColumns(table, pk, Set.of(), technical, options));
    proposal.getObjects().add(sat);
    proposal.setEvidence(
        "Same primary key as "
            + cluster.kernel.getName()
            + " ("
            + String.join(", ", pk)
            + "); satellite of "
            + cluster.hubName);
    return proposal;
  }

  private static SourceToVaultProposal classifyHub(
      SourceTable table,
      List<String> pk,
      List<NormalizedFk> outgoing,
      Map<String, HubCluster> clusters,
      Set<String> technical,
      SourceToVaultOptions options,
      Set<String> requiredHubClusters,
      HubCluster ownCluster,
      Set<String> referenceNames) {
    SourceToVaultProposal proposal = new SourceToVaultProposal();
    proposal.setSourceTableName(table.getName());
    proposal.setRole(SourceTableRole.HUB);
    proposal.setIncluded(true);
    proposal.setConfidence(
        outgoing.isEmpty() ? ClassificationConfidence.MEDIUM : ClassificationConfidence.HIGH);

    String hubName =
        ownCluster != null && !Utils.isEmpty(ownCluster.hubName)
            ? ownCluster.hubName
            : SourceToVaultNaming.hubName(table.getName());

    ProposedVaultObject hub = new ProposedVaultObject(ProposedObjectKind.HUB, hubName);
    stampTable(hub, table);
    hub.setReuseExisting(ownCluster != null && ownCluster.reuseExisting);
    if (ownCluster != null && ownCluster.reuseExisting) {
      hub.setName(ownCluster.hubName);
      hub.setTableName(ownCluster.hubName);
    }
    hub.getBusinessKeyColumns().addAll(pk);
    proposal.getObjects().add(hub);

    Set<String> linkFkColumns = new LinkedHashSet<>();
    List<NormalizedFk> leftoverFks = new ArrayList<>();
    List<NormalizedFk> selfFks = new ArrayList<>();
    for (NormalizedFk fk : outgoing) {
      if (fk.identifyingSameGrain) {
        continue;
      }
      if (fk.selfRelationship) {
        selfFks.add(fk);
        continue;
      }
      if (referenceNames.contains(fk.parentTable)) {
        continue;
      }
      leftoverFks.add(fk);
      linkFkColumns.addAll(fk.childColumns);
      HubCluster parent = clusterOf(fk.parentTable, clusters);
      if (parent != null) {
        requiredHubClusters.add(clusterId(parent));
      }
    }
    if (options.isCreateHierarchyLinks()) {
      for (NormalizedFk fk : selfFks) {
        linkFkColumns.addAll(fk.childColumns);
      }
    }

    if (options.isCreateHubSatellites()) {
      List<String> satCols = satelliteColumns(table, pk, linkFkColumns, technical, options);
      if (!satCols.isEmpty()) {
        ProposedVaultObject sat =
            new ProposedVaultObject(
                ProposedObjectKind.SATELLITE,
                SourceToVaultNaming.hubSatelliteName(table.getName()));
        stampTable(sat, table);
        sat.setParentHubName(hub.getName());
        sat.getSatelliteAttributeColumns().addAll(satCols);
        proposal.getObjects().add(sat);
      }
    }

    addLeftoverFkLinks(
        proposal,
        hub,
        table.getName(),
        SourceEndpointKind.TABLE,
        catalogName(table),
        pk,
        leftoverFks,
        clusters,
        options);
    if (options.isCreateHierarchyLinks()) {
      addHierarchyObjects(proposal, hub, table.getName(), catalogName(table), pk, selfFks);
    }

    StringBuilder evidence = new StringBuilder();
    evidence.append("Independent primary key ").append(String.join(", ", pk));
    if (!leftoverFks.isEmpty() && options.isCreateFkLinks()) {
      evidence.append("; leftover FK(s) become link(s)");
    }
    if (!selfFks.isEmpty() && options.isCreateHierarchyLinks()) {
      evidence.append("; self-FK becomes hierarchy link");
    }
    if (outgoing.stream().anyMatch(fk -> fk.directionFlipped)) {
      evidence.append("; FK direction inferred from column membership");
      if (proposal.getConfidence() == ClassificationConfidence.HIGH) {
        proposal.setConfidence(ClassificationConfidence.MEDIUM);
      }
    }
    proposal.setEvidence(evidence.toString());
    return proposal;
  }

  private static SourceToVaultProposal impliedHubProposal(HubCluster cluster) {
    SourceTable kernel = cluster.kernel;
    SourceToVaultProposal proposal = new SourceToVaultProposal();
    proposal.setSourceTableName(kernel.getName());
    proposal.setRole(SourceTableRole.HUB);
    proposal.setImplied(true);
    proposal.setIncluded(true);
    proposal.setConfidence(ClassificationConfidence.HIGH);
    proposal.setEvidence("Required parent hub for a selected link or foreign key");

    ProposedVaultObject hub = new ProposedVaultObject(ProposedObjectKind.HUB, cluster.hubName);
    stampTable(hub, kernel);
    hub.setReuseExisting(cluster.reuseExisting);
    hub.getBusinessKeyColumns().addAll(pkNames(kernel));
    proposal.getObjects().add(hub);
    return proposal;
  }

  private static boolean isLinkCandidate(List<String> pk, Map<String, List<String>> fkGroups) {
    if (fkGroups.size() < 2 || pk.isEmpty()) {
      return false;
    }
    Set<String> covered = new HashSet<>();
    for (List<String> cols : fkGroups.values()) {
      covered.addAll(lowerCopy(cols));
    }
    for (String key : pk) {
      if (!covered.contains(key.toLowerCase(Locale.ROOT))) {
        // leftover PK column is allowed (dependent child key)
        continue;
      }
    }
    int coveredPk = 0;
    for (String key : pk) {
      if (covered.contains(key.toLowerCase(Locale.ROOT))) {
        coveredPk++;
      }
    }
    return coveredPk >= 2;
  }

  private static Map<String, List<String>> fkGroupsByParentCluster(
      List<NormalizedFk> outgoing, Map<String, HubCluster> clusters) {
    Map<String, List<String>> groups = new LinkedHashMap<>();
    for (NormalizedFk fk : outgoing) {
      if (fk.identifyingSameGrain) {
        continue;
      }
      HubCluster parent = clusterOf(fk.parentTable, clusters);
      if (parent == null) {
        continue;
      }
      String id = clusterId(parent);
      groups.computeIfAbsent(id, k -> new ArrayList<>()).addAll(fk.childColumns);
    }
    return groups;
  }

  private static List<NormalizedFk> outgoingFks(String tableName, List<NormalizedFk> fks) {
    List<NormalizedFk> out = new ArrayList<>();
    for (NormalizedFk fk : fks) {
      if (tableName.equals(fk.childTable)) {
        out.add(fk);
      }
    }
    return out;
  }

  private static List<NormalizedFk> fksInvolving(String tableName, List<NormalizedFk> fks) {
    List<NormalizedFk> out = new ArrayList<>();
    for (NormalizedFk fk : fks) {
      if (tableName.equals(fk.childTable) || tableName.equals(fk.parentTable)) {
        out.add(fk);
      }
    }
    return out;
  }

  private static List<SourceTable> resolveSelectedTables(
      SourceModel model, Collection<String> selectedTableNames) {
    List<SourceTable> selected = new ArrayList<>();
    if (selectedTableNames == null || selectedTableNames.isEmpty()) {
      selected.addAll(model.getTables());
    } else {
      Set<String> wanted = new LinkedHashSet<>(selectedTableNames);
      for (String name : wanted) {
        SourceTable table = model.findTable(name);
        if (table != null) {
          selected.add(table);
        }
      }
    }
    selected.sort(Comparator.comparing(t -> t.getName() == null ? "" : t.getName()));
    return selected;
  }

  static List<NormalizedFk> normalizeRelationships(SourceModel model, List<String> warnings) {
    List<NormalizedFk> fks = new ArrayList<>();
    for (SourceRelationship rel : model.getRelationships()) {
      if (rel == null) {
        continue;
      }
      SourceEndpointKind childKind = rel.resolveChildEndpointKind();
      SourceEndpointKind parentKind = rel.resolveParentEndpointKind();
      ClassifiableSource storedChild =
          ClassifiableSource.of(model, childKind, rel.getChildTableName());
      ClassifiableSource storedParent =
          ClassifiableSource.of(model, parentKind, rel.getParentTableName());
      if (storedChild == null || storedParent == null) {
        continue;
      }
      List<String> childCols = copyNames(rel.getChildColumns());
      List<String> parentCols = copyNames(rel.getParentColumns());
      List<String> childPk = storedChild.getPrimaryKeyNames();
      List<String> parentPk = storedParent.getPrimaryKeyNames();

      boolean sameGrain =
          !childPk.isEmpty()
              && namesEqual(childPk, parentPk)
              && (namesEqual(childCols, childPk) || namesEqual(parentCols, childPk));

      boolean childMatchesParentPk =
          namesEqual(childCols, parentPk) || isSubset(childCols, parentPk);
      boolean parentMatchesChildPk =
          namesEqual(parentCols, childPk) || isSubset(parentCols, childPk);

      boolean flip = false;
      if (!sameGrain) {
        if (parentMatchesChildPk && !childMatchesParentPk) {
          flip = true;
        } else if (!childMatchesParentPk && parentMatchesChildPk) {
          flip = true;
        } else if (parentMatchesChildPk && childMatchesParentPk) {
          boolean childColsSubsetOfChildPk = isProperSubset(childCols, childPk);
          boolean parentColsSubsetOfParentPk = isProperSubset(parentCols, parentPk);
          if (!childColsSubsetOfChildPk && parentColsSubsetOfParentPk) {
            flip = true;
          }
        }
      }

      NormalizedFk fk = new NormalizedFk();
      if (flip) {
        fk.childTable = storedParent.getName();
        fk.parentTable = storedChild.getName();
        fk.childKind = storedParent.getKind();
        fk.parentKind = storedChild.getKind();
        fk.childColumns = parentCols;
        fk.parentColumns = childCols;
        fk.directionFlipped = true;
      } else {
        fk.childTable = storedChild.getName();
        fk.parentTable = storedParent.getName();
        fk.childKind = storedChild.getKind();
        fk.parentKind = storedParent.getKind();
        fk.childColumns = childCols;
        fk.parentColumns = parentCols;
      }
      fk.identifyingSameGrain = sameGrain;
      fk.selfRelationship = fk.childTable.equals(fk.parentTable) && fk.childKind == fk.parentKind;
      fks.add(fk);
    }
    return fks;
  }

  private static Map<String, HubCluster> buildClusters(SourceModel model, List<NormalizedFk> fks) {
    Map<String, String> parent = new HashMap<>();
    for (SourceTable table : model.getTables()) {
      if (table != null && !Utils.isEmpty(table.getName())) {
        parent.put(table.getName(), table.getName());
      }
    }
    for (NormalizedFk fk : fks) {
      if (!fk.identifyingSameGrain) {
        continue;
      }
      union(parent, fk.childTable, fk.parentTable);
    }
    // Also union tables that share an identifying PK and have any relationship on those columns.
    Map<String, List<SourceTable>> byPk = new LinkedHashMap<>();
    for (SourceTable table : model.getTables()) {
      List<String> pk = pkNames(table);
      if (pk.isEmpty()) {
        continue;
      }
      byPk.computeIfAbsent(pkSignature(pk), k -> new ArrayList<>()).add(table);
    }
    for (List<SourceTable> group : byPk.values()) {
      if (group.size() < 2) {
        continue;
      }
      for (int i = 0; i < group.size(); i++) {
        for (int j = i + 1; j < group.size(); j++) {
          if (related(group.get(i).getName(), group.get(j).getName(), fks)) {
            union(parent, group.get(i).getName(), group.get(j).getName());
          }
        }
      }
    }

    Map<String, HubCluster> byRoot = new LinkedHashMap<>();
    for (SourceTable table : model.getTables()) {
      if (table == null || Utils.isEmpty(table.getName()) || pkNames(table).isEmpty()) {
        continue;
      }
      String root = find(parent, table.getName());
      HubCluster cluster = byRoot.computeIfAbsent(root, k -> new HubCluster());
      cluster.members.add(table);
    }
    for (HubCluster cluster : byRoot.values()) {
      cluster.kernel = pickKernel(cluster, fks);
    }

    Map<String, HubCluster> byTable = new HashMap<>();
    for (HubCluster cluster : byRoot.values()) {
      for (SourceTable member : cluster.members) {
        byTable.put(member.getName(), cluster);
      }
    }
    return byTable;
  }

  private static void assignHubNames(Map<String, HubCluster> clusters, DataVaultModel existing) {
    Set<HubCluster> seen = new HashSet<>();
    for (HubCluster cluster : clusters.values()) {
      if (!seen.add(cluster) || cluster.kernel == null) {
        continue;
      }
      DvHub match = findExistingHub(cluster, existing);
      if (match != null) {
        cluster.hubName = match.getName();
        cluster.reuseExisting = true;
      } else {
        cluster.hubName = SourceToVaultNaming.hubName(cluster.kernel.getName());
      }
    }
  }

  private static DvHub findExistingHub(HubCluster cluster, DataVaultModel existing) {
    if (existing == null || cluster.kernel == null) {
      return null;
    }
    String catalog = catalogName(cluster.kernel);
    List<String> pk = pkNames(cluster.kernel);
    Set<String> pkSet = new HashSet<>(lowerCopy(pk));
    for (IDvTable table : existing.getTables()) {
      if (!(table instanceof DvHub hub)) {
        continue;
      }
      if (!Utils.isEmpty(catalog) && hub.getRecordSources() != null) {
        for (String source : hub.getRecordSources()) {
          if (catalog.equalsIgnoreCase(source)) {
            return hub;
          }
        }
      }
      Set<String> bkFields = new HashSet<>();
      if (hub.getBusinessKeys() != null) {
        for (BusinessKey key : hub.getBusinessKeys()) {
          if (key == null) {
            continue;
          }
          List<String> parts = key.resolveSourceParts();
          if (parts.isEmpty() && !Utils.isEmpty(key.getName())) {
            bkFields.add(key.getName().toLowerCase(Locale.ROOT));
          } else {
            for (String part : parts) {
              bkFields.add(part.toLowerCase(Locale.ROOT));
            }
          }
        }
      }
      if (!pkSet.isEmpty() && pkSet.equals(bkFields)) {
        return hub;
      }
    }
    return null;
  }

  private static SourceTable pickKernel(HubCluster cluster, List<NormalizedFk> fks) {
    return cluster.members.stream()
        .min(
            Comparator.comparing(
                    (SourceTable t) -> !SourceToVaultNaming.looksLikeHubKernelName(t.getName()))
                .thenComparingInt(t -> descriptiveCount(t))
                .thenComparingInt((SourceTable t) -> -incomingCount(t.getName(), fks))
                .thenComparing(t -> t.getName() == null ? "" : t.getName()))
        .orElse(cluster.members.get(0));
  }

  private static int incomingCount(String tableName, List<NormalizedFk> fks) {
    int count = 0;
    for (NormalizedFk fk : fks) {
      if (tableName.equals(fk.parentTable)) {
        count++;
      }
    }
    return count;
  }

  private static int descriptiveCount(SourceTable table) {
    int count = 0;
    for (SourceColumn column : table.getColumns()) {
      if (column == null || column.isPrimaryKey() || Utils.isEmpty(column.getName())) {
        continue;
      }
      count++;
    }
    return count;
  }

  private static boolean related(String a, String b, List<NormalizedFk> fks) {
    for (NormalizedFk fk : fks) {
      boolean ab = a.equals(fk.childTable) && b.equals(fk.parentTable);
      boolean ba = b.equals(fk.childTable) && a.equals(fk.parentTable);
      if (ab || ba) {
        return true;
      }
    }
    return false;
  }

  private static HubCluster clusterOf(String tableName, Map<String, HubCluster> clusters) {
    return clusters.get(tableName);
  }

  private static String clusterId(HubCluster cluster) {
    return cluster.kernel != null ? cluster.kernel.getName() : cluster.members.get(0).getName();
  }

  private static List<String> satelliteColumns(
      SourceTable table,
      List<String> pk,
      Set<String> fkColumns,
      Set<String> technical,
      SourceToVaultOptions options) {
    List<String> cols = new ArrayList<>();
    for (SourceColumn column : table.getColumns()) {
      if (column == null || Utils.isEmpty(column.getName())) {
        continue;
      }
      String name = column.getName();
      if (containsIgnoreCase(pk, name)) {
        continue;
      }
      if (options.isExcludeTechnicalColumns() && isTechnical(name, technical)) {
        continue;
      }
      if (options.isExcludeFkColumnsFromSatellites() && containsIgnoreCase(fkColumns, name)) {
        continue;
      }
      cols.add(name);
    }
    return cols;
  }

  private static boolean isTechnical(String name, Set<String> technical) {
    return technical.contains(name.toLowerCase(Locale.ROOT));
  }

  private static Set<String> technicalNames(DataVaultModel vault, SourceToVaultOptions options) {
    Set<String> names = new HashSet<>();
    if (options.getExtraTechnicalColumnNames() != null) {
      for (String name : options.getExtraTechnicalColumnNames()) {
        if (!Utils.isEmpty(name)) {
          names.add(name.toLowerCase(Locale.ROOT));
        }
      }
    }
    if (vault != null) {
      DataVaultConfiguration config = vault.getConfigurationOrDefault();
      if (config != null) {
        if (!Utils.isEmpty(config.getLoadDateField())) {
          names.add(config.getLoadDateField().toLowerCase(Locale.ROOT));
        }
        if (!Utils.isEmpty(config.getRecordSourceField())) {
          names.add(config.getRecordSourceField().toLowerCase(Locale.ROOT));
        }
      }
    }
    return names;
  }

  static List<String> pkNames(SourceTable table) {
    List<String> names = new ArrayList<>();
    if (table == null) {
      return names;
    }
    for (SourceColumn column : table.primaryKeyColumns()) {
      if (column != null && !Utils.isEmpty(column.getName())) {
        names.add(column.getName());
      }
    }
    return names;
  }

  private static String catalogName(SourceTable table) {
    if (table == null) {
      return null;
    }
    if (!Utils.isEmpty(table.getCatalogSourceName())) {
      return table.getCatalogSourceName();
    }
    return table.getName();
  }

  private static List<String> copyNames(List<String> names) {
    List<String> copy = new ArrayList<>();
    if (names == null) {
      return copy;
    }
    for (String name : names) {
      if (!Utils.isEmpty(name)) {
        copy.add(name);
      }
    }
    return copy;
  }

  private static List<String> lowerCopy(Collection<String> names) {
    List<String> copy = new ArrayList<>();
    for (String name : names) {
      if (!Utils.isEmpty(name)) {
        copy.add(name.toLowerCase(Locale.ROOT));
      }
    }
    return copy;
  }

  private static boolean namesEqual(List<String> left, List<String> right) {
    if (left == null || right == null || left.size() != right.size() || left.isEmpty()) {
      return false;
    }
    for (int i = 0; i < left.size(); i++) {
      if (!left.get(i).equalsIgnoreCase(right.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isSubset(List<String> part, List<String> whole) {
    if (part == null || whole == null || part.isEmpty() || whole.isEmpty()) {
      return false;
    }
    Set<String> pool = new HashSet<>(lowerCopy(whole));
    for (String name : part) {
      if (!pool.contains(name.toLowerCase(Locale.ROOT))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isProperSubset(List<String> part, List<String> whole) {
    return isSubset(part, whole) && part.size() < whole.size();
  }

  private static boolean containsIgnoreCase(Collection<String> names, String candidate) {
    if (names == null || Utils.isEmpty(candidate)) {
      return false;
    }
    for (String name : names) {
      if (candidate.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private static String pkSignature(List<String> pk) {
    StringBuilder sb = new StringBuilder();
    for (String name : pk) {
      if (sb.length() > 0) {
        sb.append('|');
      }
      sb.append(name.toLowerCase(Locale.ROOT));
    }
    return sb.toString();
  }

  private static void union(Map<String, String> parent, String a, String b) {
    String ra = find(parent, a);
    String rb = find(parent, b);
    if (!Objects.equals(ra, rb)) {
      parent.put(ra, rb);
    }
  }

  private static String find(Map<String, String> parent, String name) {
    String current = name;
    while (current != null && !current.equals(parent.getOrDefault(current, current))) {
      current = parent.get(current);
    }
    return current;
  }

  private static List<ClassifiableSource> resolveSelectedFeeds(
      SourceModel model, Collection<String> selectedNames) {
    List<ClassifiableSource> selected = new ArrayList<>();
    List<ClassifiableSource> all = ClassifiableSource.allIn(model);
    if (selectedNames == null || selectedNames.isEmpty()) {
      for (ClassifiableSource source : all) {
        if (source != null && !source.isTable()) {
          selected.add(source);
        }
      }
    } else {
      Set<String> wanted = new LinkedHashSet<>(selectedNames);
      for (ClassifiableSource source : all) {
        if (source != null && !source.isTable() && wanted.contains(source.getName())) {
          selected.add(source);
        }
      }
    }
    selected.sort(Comparator.comparing(ClassifiableSource::getName));
    return selected;
  }

  private static SourceToVaultProposal classifyReference(
      SourceTable table, List<String> pk, Set<String> technical, SourceToVaultOptions options) {
    SourceToVaultProposal proposal = new SourceToVaultProposal();
    proposal.setSourceTableName(table.getName());
    proposal.setRole(SourceTableRole.REFERENCE);
    proposal.setIncluded(true);
    proposal.setConfidence(ClassificationConfidence.MEDIUM);
    proposal.setEvidence("Lookup / code table: single key, incoming FKs, no leftover hub FKs");

    ProposedVaultObject ref =
        new ProposedVaultObject(
            ProposedObjectKind.REFERENCE, SourceToVaultNaming.referenceName(table.getName()));
    stampTable(ref, table);
    ref.getBusinessKeyColumns().addAll(pk);
    ref.getSatelliteAttributeColumns()
        .addAll(satelliteColumns(table, pk, Set.of(), technical, options));
    proposal.getObjects().add(ref);
    return proposal;
  }

  private static SourceToVaultProposal classifyFeed(
      ClassifiableSource feed,
      Map<String, HubCluster> clusters,
      List<NormalizedFk> fks,
      Set<String> technical,
      SourceToVaultOptions options,
      Set<String> requiredHubClusters,
      Set<String> referenceNames) {
    SourceToVaultProposal proposal = new SourceToVaultProposal();
    proposal.setSourceTableName(feed.getName());

    List<NormalizedFk> outgoing = outgoingFks(feed.getName(), fks);
    List<String> pk = new ArrayList<>(feed.getPrimaryKeyNames());
    if (pk.isEmpty()) {
      pk.addAll(inferIdentityColumns(feed, outgoing));
    }

    if (pk.isEmpty() && outgoing.isEmpty()) {
      proposal.setRole(SourceTableRole.SKIP);
      proposal.setIncluded(false);
      proposal.setConfidence(ClassificationConfidence.MEDIUM);
      proposal.setSkipReason("No primary key or relationships on this feed");
      proposal.setEvidence(feed.getKind() + " feed has no grain and no table relationships");
      return proposal;
    }

    Map<String, List<String>> fkGroups = fkGroupsByParentCluster(outgoing, clusters);
    HubCluster sameGrain = clusterByPk(pk, clusters);

    if (sameGrain != null
        && !pk.isEmpty()
        && namesEqual(pk, pkNames(sameGrain.kernel))
        && isViableHubCluster(sameGrain, fks)) {
      ProposedVaultObject sat =
          new ProposedVaultObject(
              ProposedObjectKind.SATELLITE,
              SourceToVaultNaming.extensionSatelliteName(feed.getName()));
      stampFeed(sat, feed);
      sat.setParentHubName(sameGrain.hubName);
      sat.getSatelliteAttributeColumns()
          .addAll(satelliteColumns(feed.getColumnNames(), pk, Set.of(), technical, options));
      proposal.setRole(SourceTableRole.SATELLITE);
      proposal.setIncluded(true);
      proposal.setConfidence(ClassificationConfidence.HIGH);
      proposal.setEvidence(
          feed.getKind()
              + " feed shares grain with "
              + sameGrain.kernel.getName()
              + "; satellite of "
              + sameGrain.hubName);
      proposal.getObjects().add(sat);
      return proposal;
    }

    if (isLinkCandidate(pk, fkGroups) || (pk.isEmpty() && fkGroups.size() >= 2)) {
      if (pk.isEmpty()) {
        for (List<String> cols : fkGroups.values()) {
          pk.addAll(cols);
        }
      }
      return classifyFeedLink(
          feed, pk, fkGroups, outgoing, clusters, technical, options, requiredHubClusters);
    }

    ProposedVaultObject hub =
        new ProposedVaultObject(
            ProposedObjectKind.HUB, SourceToVaultNaming.hubName(feed.getName()));
    stampFeed(hub, feed);
    if (pk.isEmpty()) {
      pk.addAll(
          feed.getColumnNames().isEmpty() ? List.of() : List.of(feed.getColumnNames().get(0)));
    }
    hub.getBusinessKeyColumns().addAll(pk);
    proposal.setRole(SourceTableRole.HUB);
    proposal.setIncluded(true);
    proposal.setConfidence(ClassificationConfidence.MEDIUM);
    proposal.getObjects().add(hub);

    Set<String> linkFkColumns = new LinkedHashSet<>();
    List<NormalizedFk> leftover = new ArrayList<>();
    for (NormalizedFk fk : outgoing) {
      if (fk.selfRelationship || referenceNames.contains(fk.parentTable)) {
        continue;
      }
      leftover.add(fk);
      linkFkColumns.addAll(fk.childColumns);
      HubCluster parent = clusterOf(fk.parentTable, clusters);
      if (parent != null) {
        requiredHubClusters.add(clusterId(parent));
      }
    }

    List<String> satCols =
        satelliteColumns(feed.getColumnNames(), pk, linkFkColumns, technical, options);
    if (!satCols.isEmpty() && options.isCreateHubSatellites()) {
      ProposedVaultObject sat =
          new ProposedVaultObject(
              ProposedObjectKind.SATELLITE, SourceToVaultNaming.hubSatelliteName(feed.getName()));
      stampFeed(sat, feed);
      sat.setParentHubName(hub.getName());
      sat.getSatelliteAttributeColumns().addAll(satCols);
      proposal.getObjects().add(sat);
    }

    addLeftoverFkLinks(
        proposal,
        hub,
        feed.getName(),
        feed.getKind(),
        feed.getCatalogSourceName(),
        pk,
        leftover,
        clusters,
        options);
    proposal.setEvidence(
        feed.getKind()
            + " feed with identity "
            + String.join(", ", pk)
            + (leftover.isEmpty() ? "" : "; leftover FKs become link(s)"));
    return proposal;
  }

  private static SourceToVaultProposal classifyFeedLink(
      ClassifiableSource feed,
      List<String> pk,
      Map<String, List<String>> fkGroups,
      List<NormalizedFk> outgoing,
      Map<String, HubCluster> clusters,
      Set<String> technical,
      SourceToVaultOptions options,
      Set<String> requiredHubClusters) {
    SourceToVaultProposal proposal = new SourceToVaultProposal();
    proposal.setSourceTableName(feed.getName());
    proposal.setRole(SourceTableRole.LINK);
    proposal.setIncluded(true);
    proposal.setConfidence(ClassificationConfidence.MEDIUM);

    ProposedVaultObject link =
        new ProposedVaultObject(
            ProposedObjectKind.LINK, SourceToVaultNaming.linkNameFromTable(feed.getName()));
    stampFeed(link, feed);
    Set<String> fkColumns = new LinkedHashSet<>();
    for (Map.Entry<String, List<String>> entry : fkGroups.entrySet()) {
      requiredHubClusters.add(entry.getKey());
      HubCluster parent = clusterById(entry.getKey(), clusters);
      String hubName =
          parent != null ? parent.hubName : SourceToVaultNaming.hubName(entry.getKey());
      link.getParticipatingHubNames().add(hubName);
      link.getHubSourceKeyColumns().put(hubName, new ArrayList<>(entry.getValue()));
      fkColumns.addAll(entry.getValue());
    }
    List<String> dcks = new ArrayList<>();
    for (String key : pk) {
      if (!containsIgnoreCase(fkColumns, key)) {
        dcks.add(key);
      }
    }
    link.getDependentChildKeyColumns().addAll(dcks);
    proposal.getObjects().add(link);

    List<String> satCols =
        satelliteColumns(feed.getColumnNames(), pk, fkColumns, technical, options);
    if (!satCols.isEmpty()) {
      ProposedVaultObject sat =
          new ProposedVaultObject(
              ProposedObjectKind.SATELLITE, SourceToVaultNaming.linkSatelliteName(feed.getName()));
      stampFeed(sat, feed);
      sat.setParentLinkName(link.getName());
      sat.getSatelliteAttributeColumns().addAll(satCols);
      proposal.getObjects().add(sat);
    }
    proposal.setEvidence(
        feed.getKind()
            + " feed is a relationship grain ("
            + fkGroups.size()
            + " hubs"
            + (dcks.isEmpty() ? "" : ", DCK " + String.join(", ", dcks))
            + ")");
    return proposal;
  }

  private static void addLeftoverFkLinks(
      SourceToVaultProposal proposal,
      ProposedVaultObject hub,
      String sourceName,
      SourceEndpointKind sourceKind,
      String catalog,
      List<String> pk,
      List<NormalizedFk> leftoverFks,
      Map<String, HubCluster> clusters,
      SourceToVaultOptions options) {
    if (!options.isCreateFkLinks() || leftoverFks.isEmpty()) {
      return;
    }
    leftoverFks.sort(Comparator.comparing(fk -> fk.parentTable));
    Set<String> distinctParents = new LinkedHashSet<>();
    for (NormalizedFk fk : leftoverFks) {
      HubCluster parent = clusterOf(fk.parentTable, clusters);
      if (parent != null) {
        distinctParents.add(parent.hubName);
      }
    }
    if (options.isCreateNaryLinksForMultiFkFeeds()
        && sourceKind != SourceEndpointKind.TABLE
        && distinctParents.size() >= 2) {
      ProposedVaultObject link =
          new ProposedVaultObject(
              ProposedObjectKind.LINK, SourceToVaultNaming.naryLinkName(sourceName));
      stamp(link, sourceName, sourceKind, catalog);
      link.getParticipatingHubNames().add(hub.getName());
      link.getHubSourceKeyColumns().put(hub.getName(), new ArrayList<>(pk));
      for (NormalizedFk fk : leftoverFks) {
        HubCluster parent = clusterOf(fk.parentTable, clusters);
        if (parent == null) {
          continue;
        }
        if (!link.getParticipatingHubNames().contains(parent.hubName)) {
          link.getParticipatingHubNames().add(parent.hubName);
        }
        link.getHubSourceKeyColumns().put(parent.hubName, new ArrayList<>(fk.childColumns));
      }
      proposal.getObjects().add(link);
      return;
    }
    for (NormalizedFk fk : leftoverFks) {
      HubCluster parent = clusterOf(fk.parentTable, clusters);
      if (parent == null) {
        continue;
      }
      ProposedVaultObject link =
          new ProposedVaultObject(
              ProposedObjectKind.LINK, SourceToVaultNaming.fkLinkName(sourceName));
      if (leftoverFks.size() > 1) {
        link.setName(
            SourceToVaultNaming.fkLinkName(sourceName)
                + "_"
                + SourceToVaultNaming.entityName(fk.parentTable));
        link.setTableName(link.getName());
      }
      stamp(link, sourceName, sourceKind, catalog);
      link.getParticipatingHubNames().add(hub.getName());
      link.getParticipatingHubNames().add(parent.hubName);
      link.getHubSourceKeyColumns().put(hub.getName(), new ArrayList<>(pk));
      link.getHubSourceKeyColumns().put(parent.hubName, new ArrayList<>(fk.childColumns));
      proposal.getObjects().add(link);
    }
  }

  private static void addHierarchyObjects(
      SourceToVaultProposal proposal,
      ProposedVaultObject hub,
      String sourceName,
      String catalog,
      List<String> pk,
      List<NormalizedFk> selfFks) {
    if (selfFks.isEmpty()) {
      return;
    }
    ProposedVaultObject alias =
        new ProposedVaultObject(
            ProposedObjectKind.LINKED_TABLE, SourceToVaultNaming.hierarchyAliasName(sourceName));
    stamp(alias, sourceName, SourceEndpointKind.TABLE, catalog);
    alias.setReferencedTableName(hub.getName());
    alias.setReferencedTableType(DvTableType.HUB);
    alias.setRoleHashKeyFieldName(SourceToVaultNaming.entityName(sourceName) + "_parent_hk");
    proposal.getObjects().add(alias);

    ProposedVaultObject link =
        new ProposedVaultObject(
            ProposedObjectKind.LINK, SourceToVaultNaming.hierarchyLinkName(sourceName));
    stamp(link, sourceName, SourceEndpointKind.TABLE, catalog);
    link.getParticipatingHubNames().add(hub.getName());
    link.getParticipatingHubNames().add(alias.getName());
    link.getHubSourceKeyColumns().put(hub.getName(), new ArrayList<>(pk));
    link.getHubSourceKeyColumns()
        .put(alias.getName(), new ArrayList<>(selfFks.get(0).childColumns));
    proposal.getObjects().add(link);
  }

  private static Set<String> detectReferenceTables(
      SourceModel model,
      List<SourceTable> selected,
      List<NormalizedFk> fks,
      Map<String, HubCluster> clusters) {
    Set<String> refs = new LinkedHashSet<>();
    Set<String> linkParents = new HashSet<>();
    for (SourceTable table : model.getTables()) {
      List<NormalizedFk> outgoing = outgoingFks(table.getName(), fks);
      if (isLinkCandidate(pkNames(table), fkGroupsByParentCluster(outgoing, clusters))) {
        for (NormalizedFk fk : outgoing) {
          if (!fk.selfRelationship) {
            linkParents.add(fk.parentTable);
          }
        }
      }
    }
    for (SourceTable table : selected) {
      if (table == null || Utils.isEmpty(table.getName())) {
        continue;
      }
      if (SourceToVaultNaming.looksLikeHubKernelName(table.getName())) {
        continue;
      }
      HubCluster cluster = clusterOf(table.getName(), clusters);
      if (cluster != null && cluster.members.size() > 1) {
        continue;
      }
      if (linkParents.contains(table.getName())) {
        continue;
      }
      List<String> pk = pkNames(table);
      if (pk.isEmpty() || pk.size() > 2) {
        continue;
      }
      int incomingFromTables = 0;
      int leftoverOut = 0;
      for (NormalizedFk fk : fks) {
        if (fk.selfRelationship) {
          continue;
        }
        if (table.getName().equals(fk.parentTable) && fk.childKind == SourceEndpointKind.TABLE) {
          incomingFromTables++;
        }
        if (table.getName().equals(fk.childTable) && !fk.identifyingSameGrain) {
          leftoverOut++;
        }
      }
      if (leftoverOut > 0) {
        continue;
      }
      boolean lookupName = looksLikeLookupName(table.getName());
      int attrs = descriptiveCount(table);
      if (lookupName || (incomingFromTables >= 1 && attrs <= 2 && looksLikeCodeKey(pk))) {
        refs.add(table.getName());
      }
    }
    return refs;
  }

  private static boolean isViableHubCluster(HubCluster cluster, List<NormalizedFk> fks) {
    if (cluster == null || cluster.kernel == null) {
      return false;
    }
    if (cluster.members.size() > 1) {
      return true;
    }
    return !fksInvolving(cluster.kernel.getName(), fks).isEmpty();
  }

  private static boolean looksLikeCodeKey(List<String> pk) {
    if (pk == null || pk.isEmpty()) {
      return false;
    }
    for (String name : pk) {
      if (!looksLikeCodeKeyPart(name)) {
        return false;
      }
    }
    return true;
  }

  private static boolean looksLikeCodeKeyPart(String name) {
    if (Utils.isEmpty(name)) {
      return false;
    }
    String n = name.toLowerCase(Locale.ROOT);
    return n.equals("code")
        || n.equals("iso")
        || n.equals("iso2")
        || n.equals("iso3")
        || n.endsWith("_code")
        || n.endsWith("_cd")
        || n.endsWith("_iso");
  }

  private static boolean looksLikeLookupName(String name) {
    if (Utils.isEmpty(name)) {
      return false;
    }
    String n = name.toLowerCase(Locale.ROOT);
    return n.endsWith("_type")
        || n.endsWith("_status")
        || n.endsWith("_code")
        || n.endsWith("_category")
        || n.endsWith("_lookup")
        || n.endsWith("_reason")
        || n.equals("country")
        || n.equals("currency")
        || n.equals("status")
        || n.equals("type");
  }

  private static List<String> inferIdentityColumns(
      ClassifiableSource feed, List<NormalizedFk> outgoing) {
    Set<String> fkCols = new HashSet<>();
    for (NormalizedFk fk : outgoing) {
      if (!fk.selfRelationship) {
        fkCols.addAll(lowerCopy(fk.childColumns));
      }
    }
    List<String> inferred = new ArrayList<>();
    for (String column : feed.getColumnNames()) {
      if (fkCols.contains(column.toLowerCase(Locale.ROOT))) {
        continue;
      }
      if (looksLikeIdentityColumn(column)) {
        inferred.add(column);
      }
    }
    return inferred;
  }

  private static boolean looksLikeIdentityColumn(String name) {
    if (Utils.isEmpty(name)) {
      return false;
    }
    String n = name.toLowerCase(Locale.ROOT);
    return n.equals("line_number")
        || n.equals("lineno")
        || n.endsWith("_id")
        || n.endsWith("_key")
        || n.endsWith("_nr")
        || n.endsWith("_no")
        || n.equals("id");
  }

  private static HubCluster clusterByPk(List<String> pk, Map<String, HubCluster> clusters) {
    if (pk == null || pk.isEmpty()) {
      return null;
    }
    String signature = pkSignature(pk);
    Set<HubCluster> seen = new HashSet<>();
    for (HubCluster cluster : clusters.values()) {
      if (cluster == null || cluster.kernel == null || !seen.add(cluster)) {
        continue;
      }
      if (signature.equals(pkSignature(pkNames(cluster.kernel)))) {
        return cluster;
      }
    }
    return null;
  }

  private static HubCluster clusterById(String clusterId, Map<String, HubCluster> clusters) {
    if (Utils.isEmpty(clusterId)) {
      return null;
    }
    HubCluster direct = clusters.get(clusterId);
    if (direct != null) {
      return direct;
    }
    for (HubCluster cluster : clusters.values()) {
      if (cluster != null && clusterId.equals(clusterId(cluster))) {
        return cluster;
      }
    }
    return null;
  }

  private static List<String> satelliteColumns(
      List<String> allColumns,
      List<String> pk,
      Set<String> fkColumns,
      Set<String> technical,
      SourceToVaultOptions options) {
    List<String> cols = new ArrayList<>();
    for (String name : allColumns) {
      if (Utils.isEmpty(name) || containsIgnoreCase(pk, name)) {
        continue;
      }
      if (options.isExcludeTechnicalColumns() && isTechnical(name, technical)) {
        continue;
      }
      if (options.isExcludeFkColumnsFromSatellites() && containsIgnoreCase(fkColumns, name)) {
        continue;
      }
      cols.add(name);
    }
    return cols;
  }

  private static void stampTable(ProposedVaultObject object, SourceTable table) {
    stamp(object, table.getName(), SourceEndpointKind.TABLE, catalogName(table));
  }

  private static void stampFeed(ProposedVaultObject object, ClassifiableSource feed) {
    stamp(object, feed.getName(), feed.getKind(), feed.getCatalogSourceName());
  }

  private static void stamp(
      ProposedVaultObject object, String sourceName, SourceEndpointKind kind, String catalog) {
    object.setSourceTableName(sourceName);
    object.setSourceKind(kind);
    object.setCatalogSourceName(catalog);
  }

  static final class NormalizedFk {
    String childTable;
    String parentTable;
    SourceEndpointKind childKind = SourceEndpointKind.TABLE;
    SourceEndpointKind parentKind = SourceEndpointKind.TABLE;
    List<String> childColumns = new ArrayList<>();
    List<String> parentColumns = new ArrayList<>();
    boolean identifyingSameGrain;
    boolean directionFlipped;
    boolean selfRelationship;
  }

  private static final class HubCluster {
    final List<SourceTable> members = new ArrayList<>();
    SourceTable kernel;
    String hubName;
    boolean reuseExisting;
  }
}
