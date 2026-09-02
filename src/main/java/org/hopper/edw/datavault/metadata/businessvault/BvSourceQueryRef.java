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
import org.apache.hop.metadata.api.HopMetadataProperty;

/**
 * Hop from an SCD2 or PIT table to a {@link BvSourceQuery} on the same Business Vault canvas.
 *
 * <p>Stored separately from {@link BvDerivativeRef} so existing DV satellite/hub XML is unchanged.
 */
@Getter
@Setter
@NoArgsConstructor
public class BvSourceQueryRef {

  @HopMetadataProperty private String sourceQueryName;

  public BvSourceQueryRef(String sourceQueryName) {
    this.sourceQueryName = sourceQueryName;
  }
}
