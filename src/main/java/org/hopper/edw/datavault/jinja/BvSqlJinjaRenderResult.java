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
package org.hopper.edw.datavault.jinja;

import java.util.List;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlRef;
import org.hopper.edw.datavault.metadata.businessvault.BvSqlSource;

/**
 * Result of rendering authoring SQL through Jinja.
 *
 * @param renderedSql SQL after macros expanded and {@code ref}/{@code source} quoted
 * @param refs distinct {@code ref()} calls collected during render
 * @param sourceUsages distinct {@code source()} calls collected during render
 */
public record BvSqlJinjaRenderResult(
    String renderedSql, List<BvSqlRef> refs, List<BvSqlSource> sourceUsages) {}
