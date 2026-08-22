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
package org.hopper.edw.datavault.i18n;

/** A validation problem for an i18n key reference or bundle entry. */
public record I18nIssue(
    Type type, String message, I18nReference reference, String key, String lookupPackage) {

  public enum Type {
    MISSING,
    WRONG_PACKAGE,
    RUNTIME_MISSING,
    ORPHAN
  }

  public static I18nIssue missing(I18nReference reference) {
    return new I18nIssue(
        Type.MISSING,
        formatReferenceIssue(
            "MISSING",
            reference,
            "lookup=" + reference.lookupPackage(),
            "key not found in any in-repo messages bundle"),
        reference,
        reference.key(),
        reference.lookupPackage());
  }

  public static I18nIssue wrongPackage(I18nReference reference, String foundInPackage) {
    return wrongPackage(reference, foundInPackage, false);
  }

  public static I18nIssue wrongPackage(
      I18nReference reference, String foundInPackage, boolean dependencyBundle) {
    String foundLabel =
        dependencyBundle ? "foundInDependency=" + foundInPackage : "found=" + foundInPackage;
    String fix =
        reference.kind() == I18nReference.Kind.ANNOTATION
            ? "i18n:" + foundInPackage + ":" + reference.key()
            : dependencyBundle
                ? "use "
                    + keyOwnerClass(reference.key())
                    + ".class as PKG, or duplicate key into "
                    + bundlePath(reference.lookupPackage())
                : "duplicate key into "
                    + bundlePath(reference.lookupPackage())
                    + " or use explicit package in annotation";
    return new I18nIssue(
        Type.WRONG_PACKAGE,
        formatReferenceIssue(
            "WRONG_PACKAGE",
            reference,
            "lookup=" + reference.lookupPackage(),
            foundLabel,
            "fix=" + fix),
        reference,
        reference.key(),
        reference.lookupPackage());
  }

  private static String keyOwnerClass(String key) {
    int dot = key.indexOf('.');
    return dot > 0 ? key.substring(0, dot) : key;
  }

  public static I18nIssue runtimeMissing(I18nReference reference, String resolved) {
    return new I18nIssue(
        Type.RUNTIME_MISSING,
        formatReferenceIssue(
            "RUNTIME_MISSING",
            reference,
            "lookup=" + reference.lookupPackage(),
            "resolved=" + resolved),
        reference,
        reference.key(),
        reference.lookupPackage());
  }

  public static I18nIssue orphan(String packageName, String key) {
    String bundle = bundlePath(packageName);
    return new I18nIssue(
        Type.ORPHAN,
        "ORPHAN " + bundle + System.lineSeparator() + "  key=" + key,
        null,
        key,
        packageName);
  }

  private static String formatReferenceIssue(
      String label, I18nReference reference, String... details) {
    String fileName = reference.sourceFile().getFileName().toString();
    StringBuilder builder =
        new StringBuilder(label).append(' ').append(fileName).append(':').append(reference.line());
    for (String detail : details) {
      builder.append(System.lineSeparator()).append("  ").append(detail);
    }
    builder.append(System.lineSeparator()).append("  key=").append(reference.key());
    return builder.toString();
  }

  private static String bundlePath(String packageName) {
    return "src/main/resources/"
        + packageName.replace('.', '/')
        + "/messages/messages_en_US.properties";
  }
}
