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
 * Pan / zoom / fit for .hop-doc-svg-viewport plus clickable AreaOwner hits.
 * Hits come from window.HOP_DOC_HITS assigned by assets/js/hits/{id}.js.
 */
(function () {
  function hitsFor(id) {
    var all = window.HOP_DOC_HITS;
    if (!all) {
      return [];
    }
    if (Array.isArray(all)) {
      return all;
    }
    return all[id] || [];
  }

  function bindViewport(viewport) {
    var stage = viewport.querySelector(".hop-doc-svg-stage");
    if (!stage) {
      return;
    }
    var id = viewport.getAttribute("data-hop-doc-svg") || "";
    var scale = 1;
    var tx = 0;
    var ty = 0;
    var panning = false;
    var lastX = 0;
    var lastY = 0;

    function apply() {
      stage.style.transform = "translate(" + tx + "px," + ty + "px) scale(" + scale + ")";
    }

    function fit() {
      var img = stage.querySelector("img.hop-doc-svg:not([style*='display: none'])") ||
        stage.querySelector("img.hop-doc-svg");
      if (!img || !img.naturalWidth) {
        scale = 1;
        tx = 8;
        ty = 8;
        apply();
        return;
      }
      var pad = 16;
      var sx = (viewport.clientWidth - pad) / img.naturalWidth;
      var sy = (viewport.clientHeight - pad) / img.naturalHeight;
      scale = Math.min(1, Math.max(0.05, Math.min(sx, sy)));
      tx = (viewport.clientWidth - img.naturalWidth * scale) / 2;
      ty = (viewport.clientHeight - img.naturalHeight * scale) / 2;
      apply();
    }

    function zoomAt(factor, cx, cy) {
      var next = Math.min(8, Math.max(0.05, scale * factor));
      var rect = viewport.getBoundingClientRect();
      var x = (cx !== undefined ? cx : rect.width / 2) - tx;
      var y = (cy !== undefined ? cy : rect.height / 2) - ty;
      tx = tx - x * (next / scale - 1);
      ty = ty - y * (next / scale - 1);
      scale = next;
      apply();
    }

    viewport.addEventListener("pointerdown", function (event) {
      if (event.button !== 0) {
        return;
      }
      panning = true;
      lastX = event.clientX;
      lastY = event.clientY;
      viewport.classList.add("is-panning");
      viewport.setPointerCapture(event.pointerId);
    });
    viewport.addEventListener("pointermove", function (event) {
      if (!panning) {
        return;
      }
      tx += event.clientX - lastX;
      ty += event.clientY - lastY;
      lastX = event.clientX;
      lastY = event.clientY;
      apply();
    });
    viewport.addEventListener("pointerup", function () {
      panning = false;
      viewport.classList.remove("is-panning");
    });
    viewport.addEventListener(
      "wheel",
      function (event) {
        event.preventDefault();
        var rect = viewport.getBoundingClientRect();
        var factor = event.deltaY < 0 ? 1.12 : 1 / 1.12;
        zoomAt(factor, event.clientX - rect.left, event.clientY - rect.top);
      },
      { passive: false }
    );

    var toolbar = viewport.parentElement && viewport.parentElement.querySelector(".hop-doc-svg-toolbar");
    if (toolbar) {
      toolbar.addEventListener("click", function (event) {
        var button = event.target.closest("[data-svg-zoom]");
        if (!button) {
          return;
        }
        var mode = button.getAttribute("data-svg-zoom");
        if (mode === "in") {
          zoomAt(1.2);
        } else if (mode === "out") {
          zoomAt(1 / 1.2);
        } else if (mode === "fit") {
          fit();
        } else if (mode === "reset") {
          scale = 1;
          tx = 8;
          ty = 8;
          apply();
        }
      });
    }

    var img = stage.querySelector("img.hop-doc-svg-light") || stage.querySelector("img.hop-doc-svg");
    function placeHits() {
      var existing = stage.querySelectorAll(".hop-doc-svg-hit");
      for (var i = 0; i < existing.length; i++) {
        existing[i].remove();
      }
      var list = hitsFor(id);
      for (var h = 0; h < list.length; h++) {
        var hit = list[h];
        var a = document.createElement("a");
        a.className = "hop-doc-svg-hit";
        a.href = hit.href || "#";
        a.title = hit.name || "";
        a.style.left = hit.x + "px";
        a.style.top = hit.y + "px";
        a.style.width = Math.max(8, hit.w) + "px";
        a.style.height = Math.max(8, hit.h) + "px";
        stage.appendChild(a);
      }
    }
    if (img) {
      if (img.complete) {
        placeHits();
        fit();
      } else {
        img.addEventListener("load", function () {
          placeHits();
          fit();
        });
      }
    } else {
      fit();
    }
  }

  function bind() {
    var viewports = document.querySelectorAll(".hop-doc-svg-viewport");
    for (var i = 0; i < viewports.length; i++) {
      bindViewport(viewports[i]);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bind);
  } else {
    bind();
  }
})();
