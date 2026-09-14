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
package org.hopper.edw.datavault.naming;

import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.ICheckResultSource;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.naming.engine.NamingSchemeValidator.Finding;
import org.apache.hop.naming.engine.NamingSchemeValidator.Severity;

/**
 * Adds naming-scheme errors to model Check. Same policy as pipeline Verify: errors only, skip when
 * the project has no schemes.
 */
public final class EdwNamingCheckSupport {

  private EdwNamingCheckSupport() {}

  public static void addRemarks(
      Object root, String location, List<ICheckResult> remarks, IHopMetadataProvider provider) {
    if (root == null || remarks == null || provider == null) {
      return;
    }
    String loc = Const.NVL(location, root.getClass().getSimpleName());
    for (Finding finding : EdwNamingSupport.walk(root, loc, provider, null)) {
      if (finding == null || finding.getSeverity() != Severity.ERROR) {
        continue;
      }
      ICheckResultSource source =
          root instanceof ICheckResultSource checkSource ? checkSource : null;
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, Const.NVL(finding.getMessage(), ""), source));
    }
  }
}
