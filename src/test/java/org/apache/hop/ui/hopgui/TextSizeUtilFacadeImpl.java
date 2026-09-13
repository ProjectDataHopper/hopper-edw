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
package org.apache.hop.ui.hopgui;

import org.eclipse.swt.graphics.Point;

/**
 * Headless stub for unit tests. Hop's real implementation lives in rcp/rap; hop-ui loads {@code
 * TextSizeUtilFacadeImpl} from the same package via {@link ImplementationLoader}.
 */
public class TextSizeUtilFacadeImpl extends TextSizeUtilFacade {

  @Override
  Point textExtentInternal(String text) {
    int length = text == null ? 0 : text.length();
    return new Point(Math.max(1, length * 7), 14);
  }
}
