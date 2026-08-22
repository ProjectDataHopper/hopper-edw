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
package org.apache.hop.datavault.hopgui.perspective.journey;

/** Non-modeling controls on the EDW operate chain. */
public enum EdwJourneyControl {
  HARVEST("harvest"),
  SCHEMA_GATE("schema-gate"),
  SOURCE_QUALITY("source-quality"),
  CATALOG_VERSION("catalog-version");

  private final String id;

  EdwJourneyControl(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
