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
 *
 * Client-side search over window.HOP_DOC_INDEX (file:// safe; no fetch).
 */
(function () {
  var active = -1;
  var shown = [];

  function rootPrefix() {
    var root = document.documentElement.getAttribute("data-hop-doc-root") || "";
    return root;
  }

  function index() {
    return (window.HOP_DOC_INDEX && window.HOP_DOC_INDEX.entries) || [];
  }

  function haystack(entry) {
    var parts = [entry.name, entry.description, entry.keywords, entry.parent];
    if (entry.aliases && entry.aliases.length) {
      parts = parts.concat(entry.aliases);
    }
    return parts.join(" ").toLowerCase();
  }

  function search(query) {
    var q = (query || "").trim().toLowerCase();
    if (!q) {
      return [];
    }
    var tokens = q.split(/\s+/);
    var hits = [];
    var entries = index();
    for (var i = 0; i < entries.length; i++) {
      var text = haystack(entries[i]);
      var ok = true;
      for (var t = 0; t < tokens.length; t++) {
        if (text.indexOf(tokens[t]) < 0) {
          ok = false;
          break;
        }
      }
      if (ok) {
        hits.push(entries[i]);
        if (hits.length >= 40) {
          break;
        }
      }
    }
    return hits;
  }

  function render(results) {
    var box = document.getElementById("hop-doc-search-results");
    if (!box) {
      return;
    }
    shown = results;
    active = results.length ? 0 : -1;
    if (!results.length) {
      var q = document.getElementById("hop-doc-search-input");
      if (q && q.value.trim()) {
        box.hidden = false;
        box.innerHTML = '<div class="hop-doc-search-empty">No matches</div>';
      } else {
        box.hidden = true;
        box.innerHTML = "";
      }
      return;
    }
    var html = "";
    var prefix = rootPrefix();
    for (var i = 0; i < results.length; i++) {
      var entry = results[i];
      var href = prefix + (entry.path || "");
      var kind = (entry.kind || "").replace(/_/g, " ");
      html +=
        '<a href="' +
        href +
        '"' +
        (i === 0 ? ' class="is-active"' : "") +
        '><span class="hop-doc-search-kind">' +
        kind +
        "</span>" +
        escapeHtml(entry.name || "") +
        "</a>";
    }
    box.innerHTML = html;
    box.hidden = false;
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function move(delta) {
    if (!shown.length) {
      return;
    }
    active = (active + delta + shown.length) % shown.length;
    var links = document.querySelectorAll("#hop-doc-search-results a");
    for (var i = 0; i < links.length; i++) {
      if (i === active) {
        links[i].classList.add("is-active");
        links[i].scrollIntoView({ block: "nearest" });
      } else {
        links[i].classList.remove("is-active");
      }
    }
  }

  function go() {
    if (active < 0 || !shown[active]) {
      return;
    }
    var prefix = rootPrefix();
    window.location.href = prefix + shown[active].path;
  }

  function bind() {
    var input = document.getElementById("hop-doc-search-input");
    var box = document.getElementById("hop-doc-search-results");
    if (!input || !box) {
      return;
    }
    input.addEventListener("input", function () {
      render(search(input.value));
    });
    input.addEventListener("keydown", function (event) {
      if (event.key === "ArrowDown") {
        event.preventDefault();
        move(1);
      } else if (event.key === "ArrowUp") {
        event.preventDefault();
        move(-1);
      } else if (event.key === "Enter") {
        event.preventDefault();
        go();
      } else if (event.key === "Escape") {
        box.hidden = true;
        input.blur();
      }
    });
    document.addEventListener("click", function (event) {
      if (!event.target.closest(".hop-doc-search")) {
        box.hidden = true;
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bind);
  } else {
    bind();
  }
})();
