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
package org.hopper.edw.datavault.metadata;

/**
 * Category id for Data Hopper EDW metadata types in the Hop GUI metadata perspective.
 *
 * <p>{@code @HopMetadata} defaults to an empty category, which Hop groups under <em>Other</em>. A
 * non-empty id that is not one of Hop's built-in categories becomes its own heading. Hop shows that
 * id as the label, so {@link #EDW} is both the stable key and the text users see.
 */
public final class EdwMetadataCategory {

  /** Heading in the metadata perspective, the New-type menu, and the overview. */
  public static final String EDW = "EDW";

  private EdwMetadataCategory() {
    // Constants holder, do not instantiate.
  }
}
