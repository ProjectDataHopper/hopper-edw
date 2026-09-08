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
 * Header chrome: color theme combo and remembered nav-tree expansion.
 */
(function () {
  var THEME_KEY = "hop-doc-theme";
  var NAV_KEY = "hop-doc-nav-open";
  var NAV_NAME_PREFIX = "hop-doc-nav:";

  function isAbsoluteHref(href) {
    if (!href) {
      return true;
    }
    var lower = href.toLowerCase();
    return (
      lower.indexOf("http://") === 0 ||
      lower.indexOf("https://") === 0 ||
      lower.indexOf("mailto:") === 0 ||
      lower.indexOf("javascript:") === 0 ||
      lower.indexOf("data:") === 0 ||
      href.charAt(0) === "/" ||
      href.charAt(0) === "#" ||
      href.indexOf("servicehandler=") >= 0
    );
  }

  function resolveAgainst(currentFile, relative) {
    var base = String(currentFile || "").replace(/\\/g, "/");
    var slash = base.lastIndexOf("/");
    var dir = slash >= 0 ? base.substring(0, slash + 1) : "";
    var parts = (dir + relative).split("/");
    var out = [];
    for (var i = 0; i < parts.length; i++) {
      var part = parts[i];
      if (!part || part === ".") {
        continue;
      }
      if (part === "..") {
        if (out.length) {
          out.pop();
        }
        continue;
      }
      out.push(part);
    }
    return out.join("/");
  }

  function hopDocHref(href) {
    if (!href || isAbsoluteHref(href)) {
      return href;
    }
    try {
      var params = new URLSearchParams(window.location.search);
      if (!params.get("servicehandler") || !params.get("file")) {
        return href;
      }
      var hash = "";
      var path = href;
      var hashAt = href.indexOf("#");
      if (hashAt >= 0) {
        hash = href.substring(hashAt);
        path = href.substring(0, hashAt);
      }
      if (!path) {
        return href;
      }
      params.set("file", resolveAgainst(params.get("file"), path));
      return window.location.pathname + "?" + params.toString() + hash;
    } catch (e) {
      return href;
    }
  }

  window.hopDocHref = hopDocHref;

  document.addEventListener(
    "click",
    function (event) {
      var target = event.target;
      while (target && target.tagName !== "A") {
        target = target.parentElement;
      }
      if (!target) {
        return;
      }
      var href = target.getAttribute("href");
      var rewritten = hopDocHref(href);
      if (rewritten && rewritten !== href) {
        event.preventDefault();
        window.location.href = rewritten;
      }
    },
    true
  );

  function systemDark() {
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  }

  function currentTheme() {
    try {
      return localStorage.getItem(THEME_KEY) || "system";
    } catch (e) {
      return "system";
    }
  }

  function apply(theme) {
    if (theme !== "light" && theme !== "dark") {
      theme = "system";
    }
    document.documentElement.setAttribute("data-theme", theme);
    var resolved = theme === "system" ? (systemDark() ? "dark" : "light") : theme;
    document.documentElement.setAttribute("data-theme-resolved", resolved);
    var select = document.getElementById("hop-doc-theme");
    if (select && select.value !== theme) {
      select.value = theme;
    }
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch (e) {
      /* file:// origins may restrict storage */
    }
  }

  function parseOpenList(raw) {
    if (!raw) {
      return [];
    }
    var parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  }

  function readOpenNav() {
    try {
      if (window.name && window.name.indexOf(NAV_NAME_PREFIX) === 0) {
        return parseOpenList(window.name.substring(NAV_NAME_PREFIX.length));
      }
    } catch (e) {
      /* ignore */
    }
    try {
      return parseOpenList(localStorage.getItem(NAV_KEY));
    } catch (e) {
      return [];
    }
  }

  function writeOpenNav(ids) {
    var payload = JSON.stringify(ids || []);
    try {
      window.name = NAV_NAME_PREFIX + payload;
    } catch (e) {
      /* ignore */
    }
    try {
      localStorage.setItem(NAV_KEY, payload);
    } catch (e) {
      /* file:// origins may restrict storage */
    }
  }

  function navNodes() {
    return document.querySelectorAll(".hop-doc-nav details[data-nav-id]");
  }

  function collectOpenNav() {
    var open = [];
    var nodes = navNodes();
    for (var i = 0; i < nodes.length; i++) {
      if (nodes[i].open) {
        open.push(nodes[i].getAttribute("data-nav-id"));
      }
    }
    return open;
  }

  function bindNav() {
    var saved = readOpenNav();
    var nodes = navNodes();
    for (var i = 0; i < nodes.length; i++) {
      var id = nodes[i].getAttribute("data-nav-id");
      if (saved.indexOf(id) >= 0) {
        nodes[i].open = true;
      }
    }
    writeOpenNav(collectOpenNav());
    var nav = document.querySelector(".hop-doc-nav");
    if (!nav) {
      return;
    }
    nav.addEventListener(
      "toggle",
      function () {
        writeOpenNav(collectOpenNav());
      },
      true
    );
  }

  function bind() {
    apply(currentTheme());
    var select = document.getElementById("hop-doc-theme");
    if (select) {
      select.addEventListener("change", function () {
        apply(select.value);
      });
    }
    bindNav();
    if (window.matchMedia) {
      var media = window.matchMedia("(prefers-color-scheme: dark)");
      var listener = function () {
        if (currentTheme() === "system") {
          apply("system");
        }
      };
      if (media.addEventListener) {
        media.addEventListener("change", listener);
      } else if (media.addListener) {
        media.addListener(listener);
      }
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bind);
  } else {
    bind();
  }
})();
