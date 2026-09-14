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
package org.hopper.edw.datavault.naming.type;

import org.apache.hop.core.naming.INamingSchemeType;
import org.apache.hop.core.naming.NamingSchemeTypePlugin;

/** Code and label come from {@link NamingSchemeTypePlugin}. */
public abstract class EdwBuiltinNamingSchemeType implements INamingSchemeType {

  @Override
  public String getCode() {
    NamingSchemeTypePlugin plugin = getClass().getAnnotation(NamingSchemeTypePlugin.class);
    return plugin != null ? plugin.id() : "";
  }

  @Override
  public String getName() {
    NamingSchemeTypePlugin plugin = getClass().getAnnotation(NamingSchemeTypePlugin.class);
    return plugin != null ? plugin.name() : getCode();
  }
}
