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
package org.hopper.edw.datavault.metadata.businessvault;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.hop.core.gui.Point;
import org.hopper.edw.datavault.metadata.DvTableType;
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * A placed reference on a Business Vault canvas to a Hub, Link, or Satellite table in the linked
 * Data Vault model (by name).
 */
@Getter
@Setter
@NoArgsConstructor
public class BvDvTableReference {

  @HopMetadataProperty private String dvTableName;

  @HopMetadataProperty(storeWithCode = true)
  private DvTableType dvTableType;

  /**
   * Optional path to the Data Vault model that owns this table when it is not the BV-linked {@code
   * .hdv} (portable {@code ${PROJECT_HOME}/…} form preferred).
   */
  @HopMetadataProperty private String referencedModelFilename;

  @HopMetadataProperty(inline = true)
  private Point location = new Point(0, 0);

  private boolean selected;
  private int drawnBoxWidth = 140;
  private int drawnBoxHeight = 70;

  public BvDvTableReference(String dvTableName, DvTableType dvTableType) {
    this.dvTableName = dvTableName;
    this.dvTableType = dvTableType;
  }
}
