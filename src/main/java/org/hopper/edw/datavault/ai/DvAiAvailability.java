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
package org.hopper.edw.datavault.ai;

/** Detects whether Hop's Language Model Chat transform is on the runtime classpath. */
public final class DvAiAvailability {

  private static final Boolean AVAILABLE = detect();

  private DvAiAvailability() {}

  public static boolean isLanguageModelChatAvailable() {
    return Boolean.TRUE.equals(AVAILABLE);
  }

  private static Boolean detect() {
    try {
      Class.forName(
          "org.apache.hop.pipeline.transforms.languagemodelchat.internals.LanguageModelFacade");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
