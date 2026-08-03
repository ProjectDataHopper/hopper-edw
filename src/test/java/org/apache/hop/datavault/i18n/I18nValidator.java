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
package org.apache.hop.datavault.i18n;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hop.i18n.BaseMessages;

/**
 * Validates i18n references against message bundle files. Uses static bundle inspection only for
 * project keys so plugin classloader boundaries do not produce false positives.
 */
public final class I18nValidator {

  private final I18nBundleIndex bundleIndex;
  private final List<I18nReference> references;
  private final Set<String> quotedKeyLiterals;
  private final Set<String> implicitMetadataKeys;

  public I18nValidator(
      I18nBundleIndex bundleIndex,
      List<I18nReference> references,
      Set<String> quotedKeyLiterals,
      Set<String> implicitMetadataKeys) {
    this.bundleIndex = bundleIndex;
    this.references = references;
    this.quotedKeyLiterals = quotedKeyLiterals;
    this.implicitMetadataKeys = implicitMetadataKeys;
  }

  public static I18nValidator forProject(Path projectRoot) throws IOException {
    Path javaRoot = projectRoot.resolve("src/main/java");
    I18nBundleIndex projectIndex =
        I18nBundleIndex.loadProject(projectRoot.resolve("src/main/resources"));
    I18nBundleIndex classpathIndex =
        I18nBundleIndex.loadClasspath(I18nValidator.class.getClassLoader());
    I18nBundleIndex bundleIndex = I18nBundleIndex.merge(projectIndex, classpathIndex);

    I18nReferenceScanner scanner = new I18nReferenceScanner(javaRoot);
    List<I18nReference> references = scanner.scan();
    Set<String> quotedKeyLiterals = scanner.scanQuotedKeyLiterals();
    Set<String> implicitMetadataKeys = scanner.scanImplicitMetadataKeys();
    return new I18nValidator(bundleIndex, references, quotedKeyLiterals, implicitMetadataKeys);
  }

  public List<I18nIssue> validateStatic() {
    List<I18nIssue> issues = new ArrayList<>();
    for (I18nReference reference : references) {
      issues.addAll(validateReference(reference));
    }
    return issues;
  }

  public List<I18nIssue> validateRuntime() {
    return List.of();
  }

  private List<I18nIssue> validateReference(I18nReference reference) {
    List<I18nIssue> issues = new ArrayList<>();

    if (isSystemKey(reference)) {
      String resolved = BaseMessages.getString(reference.key());
      if (isMissingKey(resolved, reference.key())) {
        issues.add(I18nIssue.runtimeMissing(reference, resolved));
      }
      return issues;
    }

    if (bundleIndex.contains(reference.lookupPackage(), reference.key())) {
      return issues;
    }

    if (bundleIndex.isProjectPackage(reference.lookupPackage())
        && isDynamicPrefixReference(reference)) {
      return issues;
    }

    List<String> projectPackages = bundleIndex.findProjectPackagesContaining(reference.key());
    if (!projectPackages.isEmpty()) {
      String found = firstPreferredPackage(projectPackages, reference.lookupPackage());
      issues.add(I18nIssue.wrongPackage(reference, found));
      return issues;
    }

    List<String> dependencyPackages =
        bundleIndex.findPackagesContaining(reference.key()).stream()
            .filter(pkg -> !bundleIndex.isProjectPackage(pkg))
            .toList();
    if (!dependencyPackages.isEmpty()) {
      issues.add(I18nIssue.wrongPackage(reference, dependencyPackages.getFirst(), true));
      return issues;
    }

    if (bundleIndex.isProjectPackage(reference.lookupPackage())) {
      issues.add(I18nIssue.missing(reference));
    }
    return issues;
  }

  private boolean isDynamicPrefixReference(I18nReference reference) {
    if (!reference.key().endsWith(".")) {
      return false;
    }
    return bundleIndex.hasKeyPrefixInProjectPackage(reference.lookupPackage(), reference.key());
  }

  private static String firstPreferredPackage(List<String> packages, String lookupPackage) {
    return packages.stream()
        .filter(pkg -> !pkg.equals(lookupPackage))
        .findFirst()
        .orElse(packages.getFirst());
  }

  public List<I18nIssue> findOrphans() {
    Set<String> referencedKeysByPackage = new HashSet<>();
    Set<String> referencedKeysAnywhere = new HashSet<>();
    Set<String> referencedPrefixesByPackage = new HashSet<>();
    for (I18nReference reference : references) {
      referencedKeysAnywhere.add(reference.key());
      if (!bundleIndex.isProjectPackage(reference.lookupPackage())) {
        continue;
      }
      referencedKeysByPackage.add(reference.lookupPackage() + ":" + reference.key());
      referencedPrefixesByPackage.add(reference.lookupPackage() + ":" + keyPrefix(reference.key()));
    }

    List<I18nIssue> issues = new ArrayList<>();
    for (String packageName : bundleIndex.projectPackages()) {
      for (String key : bundleIndex.keysForProjectPackage(packageName)) {
        String lookup = packageName + ":" + key;
        if (referencedKeysByPackage.contains(lookup)) {
          continue;
        }
        if (referencedKeysAnywhere.contains(key)) {
          continue;
        }
        if (referencedPrefixesByPackage.contains(packageName + ":" + keyPrefix(key))) {
          continue;
        }
        if (quotedKeyLiterals.contains(key)) {
          continue;
        }
        if (implicitMetadataKeys.contains(key)
            || implicitMetadataKeys.contains(packageName + ":" + key)) {
          continue;
        }
        if (isProvidedByDependencyBundle(key)) {
          continue;
        }
        issues.add(I18nIssue.orphan(packageName, key));
      }
    }
    return issues;
  }

  private boolean isProvidedByDependencyBundle(String key) {
    return bundleIndex.findPackagesContaining(key).stream()
        .anyMatch(pkg -> !bundleIndex.isProjectPackage(pkg));
  }

  private static boolean isSystemKey(I18nReference reference) {
    return reference.kind() == I18nReference.Kind.SYSTEM || reference.key().startsWith("System.");
  }

  private static String keyPrefix(String key) {
    int dot = key.indexOf('.');
    return dot > 0 ? key.substring(0, dot) : key;
  }

  static boolean isMissingKey(String resolved, String key) {
    return resolved != null && resolved.equals("!" + key + "!");
  }

  public List<I18nReference> references() {
    return List.copyOf(references);
  }
}
