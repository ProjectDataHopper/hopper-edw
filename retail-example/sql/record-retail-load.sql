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

-- Run only after the vault update succeeds. LOAD_DATE is yyyy/MM/dd HH:mm:ss.SSS.
INSERT INTO retail_load_log (load_date, wave)
VALUES (
  to_date(left('${LOAD_DATE}', 10), 'YYYY/MM/DD'),
  '${RETAIL_CSV_WAVE}'
);
