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

package org.apache.hop.datavault.lineage;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Field-level lineage for one physical/logical column on a target table. */
@Getter
@Setter
public class FieldLineage {

  private String targetFieldName;
  private String dataType;
  private String length;
  private String precision;
  private boolean technical;
  private final List<FieldContribution> contributions = new ArrayList<>();

  public FieldLineage() {}

  public FieldLineage(String targetFieldName) {
    this.targetFieldName = targetFieldName;
  }

  public FieldLineage addContribution(FieldContribution contribution) {
    if (contribution != null) {
      contributions.add(contribution);
    }
    return this;
  }

  public boolean hasContributions() {
    return !contributions.isEmpty();
  }
}
