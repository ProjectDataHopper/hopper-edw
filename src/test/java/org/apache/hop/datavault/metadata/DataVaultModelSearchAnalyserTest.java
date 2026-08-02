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

package org.apache.hop.datavault.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.hop.core.search.ISearchResult;
import org.apache.hop.core.search.ISearchable;
import org.apache.hop.core.search.ISearchableCallback;
import org.apache.hop.core.search.SearchQuery;
import org.junit.jupiter.api.Test;

class DataVaultModelSearchAnalyserTest {

  @Test
  void findsHubAndNoteText() {
    DataVaultModel model = new DataVaultModel();
    model.setName("retail-360");
    model.setDescription("Retail vault");

    DvHub hub = new DvHub();
    hub.setName("hub_customer");
    hub.setTableName("hub_customer");
    model.getTables().add(hub);

    DvNote note = new DvNote();
    note.setText("Customer hub notes about GDPR");
    model.getNotes().add(note);

    DataVaultModelSearchAnalyser analyser = new DataVaultModelSearchAnalyser();
    ISearchable<DataVaultModel> searchable = searchable(model);

    List<ISearchResult> hubHits =
        analyser.search(searchable, new SearchQuery("hub_customer", false, false));
    assertFalse(hubHits.isEmpty());
    assertTrue(hubHits.stream().anyMatch(r -> "hub_customer".equals(r.getComponent())));

    List<ISearchResult> noteHits =
        analyser.search(searchable, new SearchQuery("GDPR", false, false));
    assertFalse(noteHits.isEmpty());
  }

  private static ISearchable<DataVaultModel> searchable(DataVaultModel model) {
    return new ISearchable<>() {
      @Override
      public String getLocation() {
        return "test";
      }

      @Override
      public String getName() {
        return model.getName();
      }

      @Override
      public String getType() {
        return "Data Vault Model";
      }

      @Override
      public String getFilename() {
        return model.getFilename();
      }

      @Override
      public DataVaultModel getSearchableObject() {
        return model;
      }

      @Override
      public ISearchableCallback getSearchCallback() {
        return null;
      }
    };
  }
}
