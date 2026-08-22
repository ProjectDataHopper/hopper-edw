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
package org.hopper.edw.datavault.transform.mergerowsplus;

import java.util.List;
import org.apache.hop.core.IRowSet;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

@SuppressWarnings("java:S1104")
public class MergeRowsPlusData extends BaseTransformData implements ITransformData {
  public IRowMeta outputRowMeta;

  public Object[] one;
  public Object[] two;
  public Object[] oneOriginal;
  public Object[] twoOriginal;
  public int[] keyNrs;
  public int[] valueNrs;

  public IRowSet oneRowSet;
  public IRowSet twoRowSet;
  public IRowMeta referenceRowMeta;
  public IRowMeta compareRowMeta;
  public IRowMeta oneRowMeta;
  public IRowMeta twoRowMeta;
  public MergeRowsPlusAlignment.SchemaMapping schemaMapping;

  public List<Integer> passThroughIndexes;
  public Object[] oneCopy;
  public Object[] twoCopy;

  public MergeRowsPlusData() {
    super();
  }
}
