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
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Tunables for classifying a source model into raw-vault objects. */
@Getter
@Setter
public class SourceToVaultOptions {

  /** Create a binary link for each leftover non-identifying FK from a hub table. */
  private boolean createFkLinks = true;

  /** Create a hub satellite when the hub kernel table has descriptive columns. */
  private boolean createHubSatellites = true;

  /** Drop load-date / record-source style columns from satellite attribute lists. */
  private boolean excludeTechnicalColumns = true;

  /**
   * Drop columns that became link hub keys from satellite attribute lists. When false, those FKs
   * stay on the satellite for raw-audit fidelity.
   */
  private boolean excludeFkColumnsFromSatellites = true;

  /**
   * Propose hubs for FK/link parents even when those parent tables were not in the selected set.
   */
  private boolean includeUnselectedParents = true;

  /** Classify small lookup / code tables as {@code REFERENCE} instead of hub+satellite. */
  private boolean createReferenceTables = true;

  /** Create same-as / hierarchy links (and a hub alias) for self-referencing foreign keys. */
  private boolean createHierarchyLinks = true;

  /**
   * When a feed has its own identity and leftover FKs to two or more distinct hubs, emit one n-ary
   * transactional link instead of a binary link per FK.
   */
  private boolean createNaryLinksForMultiFkFeeds = true;

  /** Include source queries, JSON extractions, and pipeline cards in classification. */
  private boolean includeNonTableSources = true;

  /**
   * Extra technical column names (matched case-insensitively) in addition to the model load-date
   * and record-source fields.
   */
  private List<String> extraTechnicalColumnNames = defaultTechnicalNames();

  public static SourceToVaultOptions defaults() {
    return new SourceToVaultOptions();
  }

  private static List<String> defaultTechnicalNames() {
    List<String> names = new ArrayList<>();
    names.add("load_date");
    names.add("record_source");
    names.add("LOAD_DATE");
    names.add("RECORD_SOURCE");
    return names;
  }
}
