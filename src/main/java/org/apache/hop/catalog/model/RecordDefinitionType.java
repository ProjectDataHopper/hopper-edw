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
package org.apache.hop.catalog.model;

/** Semantic type of a record definition stored in a data catalog. */
public enum RecordDefinitionType {
  DV_HUB,
  DV_LINK,
  DV_SATELLITE,
  /** Physical Data Vault reference / code table (natural keys, no hash key). */
  DV_REFERENCE,
  DV_SOURCE,
  BV_TABLE,
  DIM_TABLE,
  FACT_TABLE,
  PHYSICAL_TABLE,
  VIEW,
  /** Index entry for a Data Vault model file ({@code .hdv}). */
  DV_MODEL,
  /** Index entry for a Business Vault model file ({@code .hbv}). */
  BV_MODEL,
  /** Index entry for a dimensional model file ({@code .hdm}). */
  DM_MODEL,
  UNKNOWN
}
