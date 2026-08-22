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
package org.hopper.edw.datavault.hopgui.search;

import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableCallback;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopDimensionalFileType;
import org.hopper.edw.datavault.hopgui.file.dimensional.HopGuiDimensionalModelGraph;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;

/** Project / open-tab searchable for a dimensional model ({@code .hdm}). */
public class HopGuiDimensionalModelSearchable implements ISearchable<DimensionalModel> {

  private final String location;
  private final DimensionalModel model;

  public HopGuiDimensionalModelSearchable(String location, DimensionalModel model) {
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
    return HopDimensionalFileType.DIMENSIONAL_FILE_TYPE_DESCRIPTION;
  }

  @Override
  public String getFilename() {
    return model != null ? model.getFilename() : null;
  }

  @Override
  public DimensionalModel getSearchableObject() {
    return model;
  }

  @Override
  public ISearchableCallback getSearchCallback() {
    return (searchable, searchResult) -> {
      IHopFileTypeHandler handler =
          ModelSearchOpenSupport.openModelFile(getFilename(), new HopDimensionalFileType());
      if (handler instanceof HopGuiDimensionalModelGraph graph
          && searchResult != null
          && searchResult.getComponent() != null) {
        graph.openSearchComponent(searchResult.getComponent());
      }
    };
  }
}
