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
package org.hopper.edw.datavault.transform.survivorshipmerge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;
import org.hopper.edw.datavault.metadata.businessvault.SurvivorshipMergeLogic;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeLogic;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeRow;
import org.hopper.edw.datavault.transform.sortedschemamerge.SortedSchemaMergeSortKey;

@SuppressWarnings("java:S1104")
public class SurvivorshipMergeData extends BaseTransformData implements ITransformData {
  public List<SortedSchemaMergeRow> sortedBuffer = new ArrayList<>();
  public SortedSchemaMergeLogic.SchemaMapping schemaMapping;
  public IRowMeta outputRowMeta;
  public Comparator<SortedSchemaMergeRow> comparator;
  public List<SortedSchemaMergeSortKey> sortKeys = new ArrayList<>();
  public int[][] sortKeyIndices;
  public int keyCount;
  public List<SurvivorshipMergeLogic.Rule> rules = new ArrayList<>();
  public Map<String, SurvivorshipMergeLogic.Snapshot> state = new LinkedHashMap<>();
  public Map<String, Object> previous;
  public List<Object[]> pending = new ArrayList<>();
  public boolean finished;
}
