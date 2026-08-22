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
package org.apache.hop.datavault.i18n;

import java.nio.file.Path;

/** A single i18n key reference discovered in Java source. */
public record I18nReference(
    Path sourceFile,
    int line,
    String key,
    String lookupPackage,
    String lookupClassFqn,
    String declaringClassFqn,
    Kind kind) {

  public enum Kind {
    /** Annotation value {@code i18n::Key} — package is the declaring class. */
    ANNOTATION,
    /** Annotation value {@code i18n:package:Key}. */
    ANNOTATION_EXPLICIT,
    /** {@code BaseMessages.getString(PKG, "key")}. */
    CODE_PKG,
    /** {@code BaseMessages.getString(Foo.class, "key")}. */
    CODE_CLASS,
    /** {@code BaseMessages.getString("package", "key")}. */
    CODE_PACKAGE,
    /** Enum constant description key string literal. */
    ENUM_DESCRIPTION,
    /** {@code BaseMessages.getString("System.Some.Key")} — Hop system bundle. */
    SYSTEM
  }

  public String lookupKey() {
    return lookupPackage + ":" + key;
  }
}
