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
package org.hopper.edw.catalog.transform.recordinput;

import java.util.List;
import org.hopper.edw.catalog.model.RecordDefinition;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

public class RecordDefinitionInputData extends BaseTransformData implements ITransformData {

  public IRowMeta outputRowMeta;
  public int namespaceFieldIndex = -1;
  public int nameFieldIndex = -1;

  public List<RecordDefinition> definitionsToOutput;
  public int currentDefIndex = 0;

  public RecordDefinitionInputData() {
    super();
  }
}
