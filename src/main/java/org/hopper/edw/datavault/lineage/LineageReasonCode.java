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
package org.hopper.edw.datavault.lineage;

/** Machine-filterable explanation for why a table or field mapping exists. */
public enum LineageReasonCode {
  USER_EXPLICIT_NAME,
  USER_EXPLICIT_MAPPING,
  DEFAULT_SAME_AS_SOURCE,
  NAMING_CONVENTION,
  STANDARD_COLUMN,
  HASH_FROM_BUSINESS_KEYS,
  PARENT_HASH_KEY,
  MULTI_SOURCE_HUB,
  LINK_HUB_KEY_MAPPING,
  DEPENDENT_CHILD_KEY,
  DRIVING_KEY,
  BV_SCD2_FIELD_MAP,
  BV_SCD2_CALCULATION,
  BV_PASSTHROUGH,
  DM_ROLE_MAPPING,
  AI_PROPOSAL_APPLIED,
  SUGGEST_MAPPING_APPLIED,
  DDL_DRIVEN_BY
}
