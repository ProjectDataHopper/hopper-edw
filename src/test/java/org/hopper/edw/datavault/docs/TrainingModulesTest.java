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
package org.hopper.edw.datavault.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the online training modules under {@code docs/training/} (issue #206): every module is
 * linked from the index, carries the six required sections in order, has a questionnaire of five to
 * ten well-formed questions, and points only at images and documents that exist.
 */
class TrainingModulesTest {

  private static final Path TRAINING = Path.of("docs", "training");
  private static final Path INDEX = TRAINING.resolve("index.adoc");
  private static final Path TEMPLATE = TRAINING.resolve("module-template.adoc");

  /** Module files in curriculum order. Add the next module here before writing it. */
  private static final List<String> MODULES = List.of("module-0-orientation.adoc");

  private static final List<String> SECTION_TITLES =
      List.of(
          "Why it matters",
          "Concepts",
          "Guided walkthrough",
          "Try it yourself",
          "Questionnaire",
          "Where to go next");

  private static final Pattern SECTION = Pattern.compile("^== (.+)$", Pattern.MULTILINE);
  private static final Pattern IMAGE = Pattern.compile("image::([^\\[]+)\\[");
  private static final Pattern LINK = Pattern.compile("link:([^\\[]+)\\[");
  private static final Pattern QUESTION_ID =
      Pattern.compile("class=\"edw-quiz-q\"\\s+data-id=\"([^\"]+)\"");
  private static final Pattern OPTION = Pattern.compile("class=\"edw-quiz-option\"");
  private static final String QUESTION_MARKER = "class=\"edw-quiz-q\"";
  private static final String CORRECT_MARKER = "data-correct=\"true\"";
  private static final String EXPLAIN_MARKER = "class=\"edw-quiz-explain\"";

  @Test
  void indexListsEveryModule() throws IOException {
    String index = read(INDEX);
    for (String module : MODULES) {
      assertTrue(index.contains(module), "docs/training/index.adoc does not link " + module);
    }
  }

  @Test
  void everyModuleHasTheRequiredSectionsInOrder() throws IOException {
    for (String module : MODULES) {
      Path path = TRAINING.resolve(module);
      List<String> titles = sectionTitles(read(path));
      int cursor = -1;
      for (String required : SECTION_TITLES) {
        int found = titles.indexOf(required);
        assertTrue(found >= 0, path + " is missing the '== " + required + "' section");
        assertTrue(
            found > cursor,
            path
                + " has '== "
                + required
                + "' out of order (index "
                + found
                + ", after "
                + cursor
                + ")");
        cursor = found;
      }
    }
  }

  @Test
  void everyQuestionnaireHasFiveToTenQuestions() throws IOException {
    for (String module : MODULES) {
      Path path = TRAINING.resolve(module);
      int count = questionBlocks(read(path)).size();
      assertTrue(count >= 5, path + " has only " + count + " questions; 5-10 required");
      assertTrue(count <= 10, path + " has " + count + " questions; 5-10 required");
    }
  }

  @Test
  void everyQuestionIsWellFormed() throws IOException {
    for (String module : MODULES) {
      Path path = TRAINING.resolve(module);
      String adoc = read(path);
      List<String> blocks = questionBlocks(adoc);
      List<String> ids = declaredIds(adoc);
      assertEquals(
          blocks.size(),
          ids.size(),
          path + ": every 'class=\"edw-quiz-q\"' needs an immediate data-id attribute");
      Set<String> seen = new HashSet<>();
      for (int i = 0; i < blocks.size(); i++) {
        String id = ids.get(i);
        String block = blocks.get(i);
        assertTrue(seen.add(id), path + ": duplicate question id " + id);
        int options = countMatches(OPTION, block);
        assertTrue(options >= 2, path + " " + id + ": " + options + " options (2-5 required)");
        assertTrue(options <= 5, path + " " + id + ": " + options + " options (2-5 required)");
        assertEquals(
            1,
            countMatches(Pattern.compile(Pattern.quote(CORRECT_MARKER)), block),
            path + " " + id + ": exactly one option must carry data-correct=\"true\"");
        assertTrue(
            block.contains(EXPLAIN_MARKER),
            path + " " + id + ": missing edw-quiz-explain paragraph");
      }
    }
  }

  @Test
  void everyModuleLoadsTheQuizAssets() throws IOException {
    assertTrue(Files.isRegularFile(TRAINING.resolve("quiz.css")), "missing docs/training/quiz.css");
    assertTrue(Files.isRegularFile(TRAINING.resolve("quiz.js")), "missing docs/training/quiz.js");
    for (String module : MODULES) {
      String adoc = read(TRAINING.resolve(module));
      assertTrue(adoc.contains("href=\"quiz.css\""), module + " does not link quiz.css");
      assertTrue(adoc.contains("src=\"quiz.js\""), module + " does not load quiz.js");
    }
  }

  @Test
  void everyImageAndDocumentLinkResolves() throws IOException {
    for (String module : MODULES) {
      Path path = TRAINING.resolve(module);
      String adoc = read(path);
      for (Path image : targets(IMAGE, adoc, path)) {
        assertTrue(Files.isRegularFile(image), path + ": missing image " + image);
      }
      for (String link : literalTargets(LINK, adoc)) {
        String cleaned = link.split("#", 2)[0].trim();
        if (cleaned.isEmpty()
            || cleaned.startsWith("http://")
            || cleaned.startsWith("https://")
            || cleaned.startsWith("mailto:")) {
          continue;
        }
        Path target = path.getParent().resolve(cleaned).normalize();
        assertTrue(Files.isRegularFile(target), path + ": dangling link " + link);
      }
    }
  }

  @Test
  void templateShowsTheSixSections() throws IOException {
    List<String> titles = sectionTitles(read(TEMPLATE));
    for (String required : SECTION_TITLES) {
      assertTrue(
          titles.contains(required), TEMPLATE + " is missing the '== " + required + "' section");
    }
  }

  private static String read(Path path) throws IOException {
    assertTrue(Files.isRegularFile(path), "missing file: " + path);
    return Files.readString(path);
  }

  private static List<String> sectionTitles(String adoc) {
    List<String> titles = new ArrayList<>();
    Matcher matcher = SECTION.matcher(adoc);
    while (matcher.find()) {
      titles.add(matcher.group(1).trim());
    }
    return titles;
  }

  /**
   * One entry per questionnaire question: the text between a question marker and the next one. The
   * final block is cut at the closing {@code ++++} of the passthrough block so later sections
   * cannot leak into it.
   */
  private static List<String> questionBlocks(String adoc) {
    String[] parts = adoc.split(Pattern.quote(QUESTION_MARKER), -1);
    List<String> blocks = new ArrayList<>();
    for (int i = 1; i < parts.length; i++) {
      String block = parts[i];
      int end = block.indexOf("++++");
      if (end >= 0) {
        block = block.substring(0, end);
      }
      blocks.add(block);
    }
    return blocks;
  }

  private static List<String> declaredIds(String adoc) {
    List<String> ids = new ArrayList<>();
    Matcher matcher = QUESTION_ID.matcher(adoc);
    while (matcher.find()) {
      ids.add(matcher.group(1));
    }
    return ids;
  }

  private static List<Path> targets(Pattern pattern, String adoc, Path source) {
    List<Path> targets = new ArrayList<>();
    Matcher matcher = pattern.matcher(adoc);
    while (matcher.find()) {
      targets.add(source.getParent().resolve(matcher.group(1).trim()).normalize());
    }
    return targets;
  }

  private static List<String> literalTargets(Pattern pattern, String adoc) {
    List<String> targets = new ArrayList<>();
    Matcher matcher = pattern.matcher(adoc);
    while (matcher.find()) {
      targets.add(matcher.group(1));
    }
    return targets;
  }

  private static int countMatches(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
