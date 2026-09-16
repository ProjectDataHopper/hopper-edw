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
package org.hopper.edw.datavault.metadata.busmatrix;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.HopMetadataPropertyType;
import org.apache.hop.metadata.api.IHopMetadata;
import org.hopper.edw.datavault.metadata.EdwMetadataCategory;

/**
 * Named library of business domains and three process levels used to tag facts on the Kimball bus
 * matrix.
 */
@HopMetadata(
    key = "business-process-catalog",
    name = "i18n::BusinessProcessCatalogMeta.name",
    description = "i18n::BusinessProcessCatalogMeta.description",
    image = "business-process-catalog.svg",
    category = EdwMetadataCategory.EDW,
    documentationUrl = "/bus-matrix.html",
    hopMetadataPropertyType = HopMetadataPropertyType.NONE)
@Getter
@Setter
public class BusinessProcessCatalogMeta extends HopMetadataBase implements IHopMetadata {

  @HopMetadataProperty private String description;

  @HopMetadataProperty(key = "domain", groupKey = "domains")
  private List<BusinessProcessTerm> domains = new ArrayList<>();

  @HopMetadataProperty(key = "level1", groupKey = "level1")
  private List<BusinessProcessTerm> level1 = new ArrayList<>();

  @HopMetadataProperty(key = "level2", groupKey = "level2")
  private List<BusinessProcessTerm> level2 = new ArrayList<>();

  @HopMetadataProperty(key = "level3", groupKey = "level3")
  private List<BusinessProcessTerm> level3 = new ArrayList<>();

  public BusinessProcessCatalogMeta() {
    super();
  }

  public BusinessProcessCatalogMeta(String name) {
    super(name);
  }

  public List<BusinessProcessTerm> getDomains() {
    if (domains == null) {
      domains = new ArrayList<>();
    }
    return domains;
  }

  public List<BusinessProcessTerm> getLevel1() {
    if (level1 == null) {
      level1 = new ArrayList<>();
    }
    return level1;
  }

  public List<BusinessProcessTerm> getLevel2() {
    if (level2 == null) {
      level2 = new ArrayList<>();
    }
    return level2;
  }

  public List<BusinessProcessTerm> getLevel3() {
    if (level3 == null) {
      level3 = new ArrayList<>();
    }
    return level3;
  }
}
