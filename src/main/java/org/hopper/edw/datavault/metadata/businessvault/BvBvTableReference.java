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
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Canvas reference to a table defined in another Business Vault model ({@code .hbv}), used for
 * multi-step BV layers (historize → harmonize → rename).
 */
@Getter
@Setter
@NoArgsConstructor
public class BvBvTableReference {

  /** Logical name of the referenced BV table. */
  @HopMetadataProperty private String bvTableName;

  /** Physical table/view name when known. */
  @HopMetadataProperty private String physicalTableName;

  @HopMetadataProperty private BvTableType bvTableType;

  /**
   * Path to the owning {@code .hbv} (portable {@code ${PROJECT_HOME}/…} form preferred). Required
   * for external models.
   */
  @HopMetadataProperty private String referencedModelFilename;

  @HopMetadataProperty private String description;

  @HopMetadataProperty(inline = true)
  private Point location = new Point(0, 0);

  private boolean selected;
  private int drawnBoxWidth = 140;
  private int drawnBoxHeight = 70;

  public BvBvTableReference(String bvTableName, BvTableType bvTableType) {
    this.bvTableName = bvTableName;
    this.bvTableType = bvTableType;
  }

  public BvBvTableReference(
      String bvTableName, BvTableType bvTableType, String referencedModelFilename) {
    this.bvTableName = bvTableName;
    this.bvTableType = bvTableType;
    this.referencedModelFilename = referencedModelFilename;
  }
}
