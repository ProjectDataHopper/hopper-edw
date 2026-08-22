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
package org.apache.hop.datavault.hopgui.perspective.journey;

/** Canonical EDW journey stages in operate / layer load order. */
public enum EdwJourneyStage {
  SOURCES("sources", 1),
  CONTROLS("controls", 2),
  DATA_VAULT("data-vault", 3),
  BUSINESS_VAULT("business-vault", 4),
  DIMENSIONAL("dimensional", 5),
  TARGET_QUALITY("target-quality", 6),
  ORCHESTRATION("orchestration", 7),
  OUTPUTS("outputs", 8);

  private final String id;
  private final int order;

  EdwJourneyStage(String id, int order) {
    this.id = id;
    this.order = order;
  }

  public String id() {
    return id;
  }

  public int order() {
    return order;
  }
}
