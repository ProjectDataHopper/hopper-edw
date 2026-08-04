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
package org.apache.hop.catalog.transform.recordoutput;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;

public class RecordDefinitionOutputData extends BaseTransformData implements ITransformData {

  public IRowMeta outputRowMeta;
  public int namespaceFieldIndex = -1;
  public int nameFieldIndex = -1;
  public int descriptionFieldIndex = -1;
  public int databaseConnectionFieldIndex = -1;
  public int schemaFieldIndex = -1;
  public int tableFieldIndex = -1;
  public int filePathFieldIndex = -1;
  public int folderFieldIndex = -1;
  public int includeFileMaskFieldIndex = -1;
  public int statusFieldStartIndex = -1;
  public boolean fixedConfigProcessed;

  // Stream field-definition mode
  public int fieldGroupingFieldIndex = -1;
  public int fieldNameFieldIndex = -1;
  public int fieldTypeFieldIndex = -1;
  public int fieldLengthFieldIndex = -1;
  public int fieldPrecisionFieldIndex = -1;
  public int fieldPrimaryKeyPositionFieldIndex = -1;
  public int fieldFormatFieldIndex = -1;
  public int fieldDecimalFieldIndex = -1;
  public int fieldGroupingSymbolFieldIndex = -1;
  public int deliveryTypeFieldIndex = -1;

  public final List<SourceField> currentFields = new ArrayList<>();
  public Object[] currentGroupBaseRow;
  public String currentGroupValue;
  public boolean hasOpenGroup;

  public RecordDefinitionOutputData() {
    super();
  }
}
