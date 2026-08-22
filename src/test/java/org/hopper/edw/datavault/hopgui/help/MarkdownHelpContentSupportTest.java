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
package org.hopper.edw.datavault.hopgui.help;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MarkdownHelpContentSupportTest {

  private static final String LICENSE_HEADER =
      """
      <!--
          Licensed to the Apache Software Foundation (ASF) under one or more
          contributor license agreements.  See the NOTICE file distributed with
          this work for additional information regarding copyright ownership.
          The ASF licenses this file to You under the Apache License, Version 2.0
          (the "License"); you may not use this file except in compliance with
          the License.  You may obtain a copy of the License at

               http://www.apache.org/licenses/LICENSE-2.0

          Unless required by applicable law or agreed to in writing, software
          distributed under the License is distributed on an "AS IS" BASIS,
          WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
          See the License for the specific language governing permissions and
          limitations under the License.
      -->
      """;

  private static final String IBRIDGE_LICENSE_HEADER =
      """
      <!--
      Copyright 2026 i-Bridge bv

      Licensed under the Apache License, Version 2.0 (the "License");
      you may not use this file except in compliance with the License.
      You may obtain a copy of the License at

           http://www.apache.org/licenses/LICENSE-2.0

      Unless required by applicable law or agreed to in writing, software
      distributed under the License is distributed on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      See the License for the specific language governing permissions and
      limitations under the License.
      -->
      """;

  @Test
  void stripLicenseHeaderRemovesLeadingAsfComment() {
    String markdown = LICENSE_HEADER + "# Hub editor\n\nBody text.";

    String stripped = MarkdownHelpContentSupport.stripLicenseHeader(markdown);

    assertEquals("# Hub editor\n\nBody text.", stripped);
  }

  @Test
  void stripLicenseHeaderRemovesLeadingIBridgeComment() {
    String markdown = IBRIDGE_LICENSE_HEADER + "# Hub editor\n\nBody text.";

    String stripped = MarkdownHelpContentSupport.stripLicenseHeader(markdown);

    assertEquals("# Hub editor\n\nBody text.", stripped);
  }

  @Test
  void stripLicenseHeaderLeavesContentWithoutHeaderUnchanged() {
    String markdown = "# Test topic\n\nfixture";

    assertEquals(markdown, MarkdownHelpContentSupport.stripLicenseHeader(markdown));
  }

  @Test
  void stripLicenseHeaderLeavesNullAndBlankUnchanged() {
    assertNull(MarkdownHelpContentSupport.stripLicenseHeader(null));
    assertEquals("", MarkdownHelpContentSupport.stripLicenseHeader(""));
    assertEquals("   ", MarkdownHelpContentSupport.stripLicenseHeader("   "));
  }

  @Test
  void stripLicenseHeaderDoesNotRemoveBodyHtmlComments() {
    String markdown = "# Title\n\n<!-- not a license header -->\n\nMore text.";

    assertEquals(markdown, MarkdownHelpContentSupport.stripLicenseHeader(markdown));
    assertFalse(markdown.startsWith("<!--"));
  }
}
