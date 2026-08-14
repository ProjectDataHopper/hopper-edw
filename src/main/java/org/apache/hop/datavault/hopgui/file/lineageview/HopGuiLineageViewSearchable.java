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
package org.apache.hop.datavault.hopgui.file.lineageview;

import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableCallback;
import org.apache.hop.datavault.hopgui.search.ModelSearchOpenSupport;
import org.apache.hop.datavault.lineageview.HopLineageViewDocument;
import org.apache.hop.ui.hopgui.file.IHopFileTypeHandler;

/** Project search for a {@code .hlv} view definition. */
public class HopGuiLineageViewSearchable implements ISearchable<HopLineageViewDocument> {

  private final String location;
  private final HopLineageViewDocument document;

  public HopGuiLineageViewSearchable(String location, HopLineageViewDocument document) {
    this.location = location;
    this.document = document;
  }

  @Override
  public String getLocation() {
    return location;
  }

  @Override
  public String getName() {
    return document != null ? document.getName() : null;
  }

  @Override
  public String getType() {
    return HopLineageViewFileType.FILE_TYPE_DESCRIPTION;
  }

  @Override
  public String getFilename() {
    return document != null ? document.getFilename() : null;
  }

  @Override
  public HopLineageViewDocument getSearchableObject() {
    return document;
  }

  @Override
  public ISearchableCallback getSearchCallback() {
    return (searchable, searchResult) -> {
      IHopFileTypeHandler handler =
          ModelSearchOpenSupport.openModelFile(getFilename(), new HopLineageViewFileType());
      if (handler instanceof HopGuiLineageViewGraph graph) {
        graph.updateGui();
      }
    };
  }
}
