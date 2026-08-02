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

package org.apache.hop.datavault.hopgui.search;

import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableCallback;
import org.apache.hop.datavault.hopgui.file.businessvault.HopBusinessVaultFileType;
import org.apache.hop.datavault.hopgui.file.businessvault.HopGuiBusinessVaultGraph;
import org.apache.hop.datavault.metadata.businessvault.BusinessVaultModel;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;

/** Project / open-tab searchable for a Business Vault model ({@code .hbv}). */
public class HopGuiBusinessVaultModelSearchable implements ISearchable<BusinessVaultModel> {

  private final String location;
  private final BusinessVaultModel model;

  public HopGuiBusinessVaultModelSearchable(String location, BusinessVaultModel model) {
    this.location = location;
    this.model = model;
  }

  @Override
  public String getLocation() {
    return location;
  }

  @Override
  public String getName() {
    return model != null ? model.getName() : null;
  }

  @Override
  public String getType() {
    return HopBusinessVaultFileType.BUSINESS_VAULT_FILE_TYPE_DESCRIPTION;
  }

  @Override
  public String getFilename() {
    return model != null ? model.getFilename() : null;
  }

  @Override
  public BusinessVaultModel getSearchableObject() {
    return model;
  }

  @Override
  public ISearchableCallback getSearchCallback() {
    return (searchable, searchResult) -> {
      IHopFileTypeHandler handler =
          ModelSearchOpenSupport.openModelFile(getFilename(), new HopBusinessVaultFileType());
      if (handler instanceof HopGuiBusinessVaultGraph graph
          && searchResult != null
          && searchResult.getComponent() != null) {
        graph.openSearchComponent(searchResult.getComponent());
      }
    };
  }
}
