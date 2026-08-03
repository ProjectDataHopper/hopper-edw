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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans Java sources for i18n key references in annotations and {@code BaseMessages.getString}
 * calls.
 */
public final class I18nReferenceScanner {

  private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
  private static final Pattern IMPORT_PATTERN =
      Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;");
  private static final Pattern CLASS_DECL_PATTERN =
      Pattern.compile(
          "^(?:public\\s+|protected\\s+|private\\s+)?(?:abstract\\s+|final\\s+)?(?:class|enum|interface|record)\\s+(\\w+)");
  private static final Pattern PKG_CONSTANT_PATTERN =
      Pattern.compile(
          "(?:public|protected|private)?\\s*static\\s+final\\s+Class<[^>]+>\\s+PKG\\s*=\\s*(\\w+)\\.class\\s*;");

  private static final Pattern I18N_EXPLICIT_PATTERN =
      Pattern.compile("\"i18n:([^:\"]+):([^\"]+)\"");
  private static final Pattern I18N_DEFAULT_PATTERN = Pattern.compile("\"i18n::([^\"]+)\"");

  private static final Pattern BASE_MESSAGES_PKG_PATTERN =
      Pattern.compile("BaseMessages\\.getString\\(\\s*PKG\\s*,\\s*\"([^\"]+)\"");
  private static final Pattern BASE_MESSAGES_CLASS_PATTERN =
      Pattern.compile("BaseMessages\\.getString\\(\\s*(\\w+)\\.class\\s*,\\s*\"([^\"]+)\"");
  private static final Pattern BASE_MESSAGES_PACKAGE_PATTERN =
      Pattern.compile(
          "BaseMessages\\.getString\\(\\s*\"((?:[\\w.]+\\.)+[\\w.]+)\"\\s*,\\s*\"([^\"]+)\"");
  private static final Pattern BASE_MESSAGES_SYSTEM_PATTERN =
      Pattern.compile("BaseMessages\\.getString\\(\\s*\"([^\"]+)\"\\s*\\)");
  private static final Pattern MAP_ENTRY_TITLE_KEY_PATTERN =
      Pattern.compile("Map\\.entry\\([^,]+,\\s*\"([A-Za-z][\\w.]+\\.[\\w.]+)\"\\)");
  private static final Pattern QUOTED_I18N_KEY_PATTERN =
      Pattern.compile("\"([A-Za-z][\\w.]+\\.[\\w.]+)\"");

  private static final Pattern ENUM_DESCRIPTION_KEY_PATTERN =
      Pattern.compile(
          "^\\s*[A-Z][A-Z0-9_]*\\([^\"]*\"[^\"]*\"\\s*,\\s*\"([A-Za-z][\\w.]+\\.[\\w.]+)\"");
  private static final Pattern ENUM_SINGLE_KEY_PATTERN =
      Pattern.compile("^\\s*[A-Z][A-Z0-9_]*\\(\\s*\"([A-Za-z][\\w.]+\\.[\\w.]+)\"\\s*\\)");

  private final Path javaRoot;

  public I18nReferenceScanner(Path javaRoot) {
    this.javaRoot = javaRoot;
  }

  public List<I18nReference> scan() throws IOException {
    List<I18nReference> references = new ArrayList<>();
    if (!Files.isDirectory(javaRoot)) {
      return references;
    }
    try (Stream<Path> paths = Files.walk(javaRoot)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(path -> scanFile(path, references));
    }
    return deduplicate(references);
  }

  public Set<String> scanImplicitMetadataKeys() throws IOException {
    Set<String> keys = new HashSet<>();
    if (!Files.isDirectory(javaRoot)) {
      return keys;
    }
    try (Stream<Path> paths = Files.walk(javaRoot)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        List<String> lines = Files.readAllLines(path);
        String packageName = extractPackage(lines);
        String typeName = extractTopLevelTypeName(lines);
        keys.add(typeName + ".name");
        keys.add(typeName + ".description");
        keys.add(packageName + ":" + typeName + ".name");
        keys.add(packageName + ":" + typeName + ".description");
      }
    }
    return keys;
  }

  public Set<String> scanQuotedKeyLiterals() throws IOException {
    Set<String> keys = new HashSet<>();
    if (!Files.isDirectory(javaRoot)) {
      return keys;
    }
    try (Stream<Path> paths = Files.walk(javaRoot)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String content = Files.readString(path);
        Matcher matcher = QUOTED_I18N_KEY_PATTERN.matcher(content);
        while (matcher.find()) {
          String key = matcher.group(1);
          if (isVerifiableKey(key, content, matcher.end())) {
            keys.add(key);
          }
        }
      }
    }
    return keys;
  }

  private void scanFile(Path file, List<I18nReference> references) {
    try {
      List<String> lines = Files.readAllLines(file);
      FileContext context = new FileContext(file, lines);
      String flattened = flatten(lines);
      for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
        String line = lines.get(lineIndex);
        int lineNumber = lineIndex + 1;
        scanLine(context, line, lineNumber, references);
      }
      scanFlattenedBaseMessages(context, flattened, lines, references);
      scanMapEntryTitleKeys(context, flattened, lines, references);
      if (context.isEnum()) {
        scanEnumDescriptionKeys(context, references);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read " + file, e);
    }
  }

  private void scanFlattenedBaseMessages(
      FileContext context, String flattened, List<String> lines, List<I18nReference> references) {
    Matcher pkgMatcher = BASE_MESSAGES_PKG_PATTERN.matcher(flattened);
    while (pkgMatcher.find()) {
      String key = pkgMatcher.group(1);
      String lookupClassFqn = context.pkgLookupClassFqn();
      if (lookupClassFqn != null) {
        maybeAddReference(
            references,
            codeReference(
                context,
                lineNumberForKey(lines, key),
                key,
                lookupClassFqn,
                I18nReference.Kind.CODE_PKG),
            flattened,
            pkgMatcher.end());
      }
    }

    Matcher classMatcher = BASE_MESSAGES_CLASS_PATTERN.matcher(flattened);
    while (classMatcher.find()) {
      String key = classMatcher.group(2);
      String lookupClassFqn = context.resolveClassFqn(classMatcher.group(1));
      if (lookupClassFqn != null) {
        maybeAddReference(
            references,
            codeReference(
                context,
                lineNumberForKey(lines, key),
                key,
                lookupClassFqn,
                I18nReference.Kind.CODE_CLASS),
            flattened,
            classMatcher.end());
      }
    }

    Matcher packageMatcher = BASE_MESSAGES_PACKAGE_PATTERN.matcher(flattened);
    while (packageMatcher.find()) {
      String key = packageMatcher.group(2);
      maybeAddReference(
          references,
          new I18nReference(
              context.file(),
              lineNumberForKey(lines, key),
              key,
              packageMatcher.group(1),
              context.declaringClassFqn(),
              context.declaringClassFqn(),
              I18nReference.Kind.CODE_PACKAGE),
          flattened,
          packageMatcher.end());
    }

    if (!flattened.contains("BaseMessages.getString(PKG")
        && !flattened.contains(".class")
        && flattened.contains("BaseMessages.getString(")) {
      Matcher systemMatcher = BASE_MESSAGES_SYSTEM_PATTERN.matcher(flattened);
      while (systemMatcher.find()) {
        String key = systemMatcher.group(1);
        maybeAddReference(
            references,
            new I18nReference(
                context.file(),
                lineNumberForKey(lines, key),
                key,
                "org.apache.hop.i18n",
                "org.apache.hop.i18n.GlobalMessages",
                context.declaringClassFqn(),
                I18nReference.Kind.SYSTEM),
            flattened,
            systemMatcher.end());
      }
    }
  }

  private void scanLine(
      FileContext context, String line, int lineNumber, List<I18nReference> references) {
    Matcher explicitMatcher = I18N_EXPLICIT_PATTERN.matcher(line);
    while (explicitMatcher.find()) {
      String key = explicitMatcher.group(2);
      maybeAddReference(
          references,
          annotationReference(
              context,
              lineNumber,
              key,
              explicitMatcher.group(1),
              I18nReference.Kind.ANNOTATION_EXPLICIT),
          line,
          explicitMatcher.end());
    }

    Matcher defaultMatcher = I18N_DEFAULT_PATTERN.matcher(line);
    while (defaultMatcher.find()) {
      String key = defaultMatcher.group(1);
      maybeAddReference(
          references,
          annotationReference(
              context, lineNumber, key, context.packageName(), I18nReference.Kind.ANNOTATION),
          line,
          defaultMatcher.end());
    }

    Matcher pkgMatcher = BASE_MESSAGES_PKG_PATTERN.matcher(line);
    while (pkgMatcher.find()) {
      String key = pkgMatcher.group(1);
      String lookupClassFqn = context.pkgLookupClassFqn();
      if (lookupClassFqn != null) {
        maybeAddReference(
            references,
            codeReference(context, lineNumber, key, lookupClassFqn, I18nReference.Kind.CODE_PKG),
            line,
            pkgMatcher.end());
      }
    }

    Matcher classMatcher = BASE_MESSAGES_CLASS_PATTERN.matcher(line);
    while (classMatcher.find()) {
      String key = classMatcher.group(2);
      String lookupClassFqn = context.resolveClassFqn(classMatcher.group(1));
      if (lookupClassFqn != null) {
        maybeAddReference(
            references,
            codeReference(context, lineNumber, key, lookupClassFqn, I18nReference.Kind.CODE_CLASS),
            line,
            classMatcher.end());
      }
    }

    Matcher packageMatcher = BASE_MESSAGES_PACKAGE_PATTERN.matcher(line);
    while (packageMatcher.find()) {
      String key = packageMatcher.group(2);
      maybeAddReference(
          references,
          new I18nReference(
              context.file(),
              lineNumber,
              key,
              packageMatcher.group(1),
              context.declaringClassFqn(),
              context.declaringClassFqn(),
              I18nReference.Kind.CODE_PACKAGE),
          line,
          packageMatcher.end());
    }

    if (!line.contains("BaseMessages.getString(PKG")
        && !line.contains(".class")
        && line.contains("BaseMessages.getString(")) {
      Matcher systemMatcher = BASE_MESSAGES_SYSTEM_PATTERN.matcher(line);
      while (systemMatcher.find()) {
        String key = systemMatcher.group(1);
        maybeAddReference(
            references,
            new I18nReference(
                context.file(),
                lineNumber,
                key,
                "org.apache.hop.i18n",
                "org.apache.hop.i18n.GlobalMessages",
                context.declaringClassFqn(),
                I18nReference.Kind.SYSTEM),
            line,
            systemMatcher.end());
      }
    }
  }

  private void scanMapEntryTitleKeys(
      FileContext context, String flattened, List<String> lines, List<I18nReference> references) {
    Matcher matcher = MAP_ENTRY_TITLE_KEY_PATTERN.matcher(flattened);
    while (matcher.find()) {
      String key = matcher.group(1);
      maybeAddReference(
          references,
          codeReference(
              context,
              lineNumberForKey(lines, key),
              key,
              context.declaringClassFqn(),
              I18nReference.Kind.ENUM_DESCRIPTION),
          flattened,
          matcher.end());
    }
  }

  private void scanEnumDescriptionKeys(FileContext context, List<I18nReference> references) {
    for (int lineIndex = 0; lineIndex < context.lines().size(); lineIndex++) {
      String line = context.lines().get(lineIndex);
      if (line.contains("BaseMessages.getString(")) {
        continue;
      }

      Matcher multiArgMatcher = ENUM_DESCRIPTION_KEY_PATTERN.matcher(line);
      if (multiArgMatcher.find()) {
        addEnumReference(context, references, lineIndex + 1, multiArgMatcher.group(1));
        continue;
      }

      Matcher singleArgMatcher = ENUM_SINGLE_KEY_PATTERN.matcher(line);
      if (singleArgMatcher.find()) {
        addEnumReference(context, references, lineIndex + 1, singleArgMatcher.group(1));
      }
    }
  }

  private I18nReference annotationReference(
      FileContext context,
      int lineNumber,
      String key,
      String lookupPackage,
      I18nReference.Kind kind) {
    return new I18nReference(
        context.file(),
        lineNumber,
        key,
        lookupPackage,
        context.declaringClassFqn(),
        context.declaringClassFqn(),
        kind);
  }

  private I18nReference codeReference(
      FileContext context,
      int lineNumber,
      String key,
      String lookupClassFqn,
      I18nReference.Kind kind) {
    return new I18nReference(
        context.file(),
        lineNumber,
        key,
        packageNameFromFqn(lookupClassFqn),
        lookupClassFqn,
        context.declaringClassFqn(),
        kind);
  }

  private void addEnumReference(
      FileContext context, List<I18nReference> references, int lineNumber, String key) {
    maybeAddReference(
        references,
        codeReference(
            context,
            lineNumber,
            key,
            context.declaringClassFqn(),
            I18nReference.Kind.ENUM_DESCRIPTION),
        String.join(" ", context.lines()),
        -1);
  }

  private static void maybeAddReference(
      List<I18nReference> references, I18nReference reference, String source, int endPos) {
    if (isVerifiableKey(reference.key(), source, endPos)) {
      references.add(reference);
    }
  }

  private static boolean isVerifiableKey(String key, String source, int endPos) {
    if (key == null || key.isBlank() || key.endsWith(".")) {
      return false;
    }
    if (!key.contains(".")) {
      return false;
    }
    if (endPos >= 0 && endPos < source.length()) {
      String after = source.substring(endPos).trim();
      if (after.startsWith("+")) {
        return false;
      }
    }
    return true;
  }

  private static String packageNameFromFqn(String classFqn) {
    int lastDot = classFqn.lastIndexOf('.');
    return lastDot >= 0 ? classFqn.substring(0, lastDot) : classFqn;
  }

  private static String flatten(List<String> lines) {
    return String.join(" ", lines).replaceAll("\\s+", " ");
  }

  private static int lineNumberForKey(List<String> lines, String key) {
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      if (lines.get(lineIndex).contains("\"" + key + "\"")) {
        return lineIndex + 1;
      }
    }
    return 1;
  }

  private static List<I18nReference> deduplicate(List<I18nReference> references) {
    Set<String> seen = new HashSet<>();
    List<I18nReference> unique = new ArrayList<>();
    for (I18nReference reference : references) {
      String identity =
          reference.sourceFile()
              + ":"
              + reference.line()
              + ":"
              + reference.kind()
              + ":"
              + reference.lookupPackage()
              + ":"
              + reference.key();
      if (seen.add(identity)) {
        unique.add(reference);
      }
    }
    return unique;
  }

  private final class FileContext {
    private final Path file;
    private final List<String> lines;
    private final String packageName;
    private final String topLevelTypeName;
    private final Map<String, String> simpleNameToFqn;
    private final String pkgClassSimpleName;

    private FileContext(Path file, List<String> lines) {
      this.file = file;
      this.lines = lines;
      this.packageName = extractPackage(lines);
      this.topLevelTypeName = extractTopLevelTypeName(lines);
      this.simpleNameToFqn = extractImports(lines, packageName);
      if (topLevelTypeName != null) {
        simpleNameToFqn.putIfAbsent(topLevelTypeName, packageName + "." + topLevelTypeName);
      }
      this.pkgClassSimpleName = extractPkgClassSimpleName(lines);
    }

    Path file() {
      return file;
    }

    List<String> lines() {
      return lines;
    }

    String packageName() {
      return packageName;
    }

    String declaringClassFqn() {
      return packageName + "." + topLevelTypeName;
    }

    boolean isEnum() {
      return lines.stream().anyMatch(line -> line.contains("enum " + topLevelTypeName));
    }

    String pkgLookupClassFqn() {
      if (pkgClassSimpleName == null) {
        return null;
      }
      return resolveClassFqn(pkgClassSimpleName);
    }

    String lookupPackageForClass(String simpleName) {
      String fqn = resolveClassFqn(simpleName);
      if (fqn == null) {
        return null;
      }
      int lastDot = fqn.lastIndexOf('.');
      return lastDot >= 0 ? fqn.substring(0, lastDot) : packageName;
    }

    String resolveClassFqn(String simpleName) {
      return simpleNameToFqn.get(simpleName);
    }
  }

  private static String extractPackage(List<String> lines) {
    for (String line : lines) {
      Matcher matcher = PACKAGE_PATTERN.matcher(line);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }
    throw new IllegalStateException("No package declaration found");
  }

  private static String extractTopLevelTypeName(List<String> lines) {
    for (String line : lines) {
      Matcher matcher = CLASS_DECL_PATTERN.matcher(line);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }
    throw new IllegalStateException("No top-level type declaration found");
  }

  private static Map<String, String> extractImports(List<String> lines, String packageName) {
    Map<String, String> imports = new HashMap<>();
    for (String line : lines) {
      Matcher matcher = IMPORT_PATTERN.matcher(line);
      if (matcher.find()) {
        String fqn = matcher.group(1);
        int lastDot = fqn.lastIndexOf('.');
        if (lastDot >= 0) {
          imports.put(fqn.substring(lastDot + 1), fqn);
        }
      }
    }
    return imports;
  }

  private static String extractPkgClassSimpleName(List<String> lines) {
    for (String line : lines) {
      Matcher matcher = PKG_CONSTANT_PATTERN.matcher(line);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }
    return null;
  }
}
