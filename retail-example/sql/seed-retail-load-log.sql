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

-- Finished retail loads. The next update is the latest load_date plus one month.
CREATE TABLE IF NOT EXISTS retail_load_log (
  load_date DATE PRIMARY KEY,
  wave VARCHAR(20) NOT NULL,
  loaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

TRUNCATE retail_load_log;

INSERT INTO retail_load_log (load_date, wave)
VALUES (DATE '2024-01-01', 'initial');
