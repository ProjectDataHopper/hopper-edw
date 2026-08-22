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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.exception.HopException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class I18nKeysValidationTest {

  private static I18nValidator validator;

  @BeforeAll
  static void initHop() throws HopException, IOException {
    HopEnvironment.init();
    Path projectRoot = Paths.get(System.getProperty("user.dir"));
    validator = I18nValidator.forProject(projectRoot);
  }

  @Test
  void allReferencedKeysResolveStaticallyAndAtRuntime() {
    List<I18nIssue> issues = validator.validateStatic();

    assertTrue(
        issues.isEmpty(),
        () ->
            issues.size()
                + " i18n issue(s):"
                + System.lineSeparator()
                + String.join(
                    System.lineSeparator() + System.lineSeparator(), formatIssues(issues)));
  }

  @Test
  void noOrphanMessageKeys() {
    List<I18nIssue> issues = validator.findOrphans();
    assertTrue(
        issues.isEmpty(),
        () ->
            issues.size()
                + " orphan i18n key(s):"
                + System.lineSeparator()
                + String.join(
                    System.lineSeparator() + System.lineSeparator(), formatIssues(issues)));
  }

  private static List<String> formatIssues(List<I18nIssue> issues) {
    return issues.stream().map(I18nIssue::message).toList();
  }
}
