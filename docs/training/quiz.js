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

/*
 * Offline questionnaire widget for the Data Hopper EDW training modules (issue #206).
 *
 * No dependencies, no network, no storage: the pages must work from the plugin zip over file://
 * as well as from Hop GUI, Hop Web and a static host.
 *
 * Host integration point: when every question in a quiz is answered the widget dispatches
 *   window.dispatchEvent(new CustomEvent("edw-quiz-complete",
 *       {detail: {module: "<module-file-stem>", correct: <n>, total: <n>}}))
 * and appends the same object to window.__edwQuizzes. A future LMS or site can collect results
 * without changing the module pages.
 */
(function () {
  "use strict";

  var QUESTION = ".edw-quiz-q";
  var OPTION = ".edw-quiz-option";
  var EXPLAIN = ".edw-quiz-explain";

  function updateScore(quiz, state) {
    var score = quiz.querySelector(".edw-quiz-score");
    if (!score) {
      return;
    }
    if (state.answered < state.total) {
      score.textContent =
        "Answered " + state.answered + " of " + state.total + " — " + state.correct + " correct so far.";
      return;
    }
    score.textContent =
      "Done: " + state.correct + " of " + state.total + " correct." +
      (state.correct === state.total ? " Perfect." : " Read the explanations, then Try again.");
    var detail = { module: quiz.dataset.module || "", correct: state.correct, total: state.total };
    window.__edwQuizzes = window.__edwQuizzes || [];
    window.__edwQuizzes.push(detail);
    window.dispatchEvent(new CustomEvent("edw-quiz-complete", { detail: detail }));
  }

  function reset(quiz, state) {
    state.answered = 0;
    state.correct = 0;
    quiz.querySelectorAll(QUESTION).forEach(function (question) {
      question.classList.remove("is-answered", "is-right", "is-wrong");
      question.querySelectorAll(OPTION).forEach(function (option) {
        option.disabled = false;
        option.classList.remove("is-picked", "is-correct", "is-wrong");
        option.removeAttribute("aria-pressed");
      });
      var explain = question.querySelector(EXPLAIN);
      if (explain) {
        explain.hidden = true;
      }
    });
    updateScore(quiz, state);
  }

  function answer(quiz, state, question, option) {
    if (question.classList.contains("is-answered")) {
      return;
    }
    question.classList.add("is-answered");
    option.classList.add("is-picked");
    option.setAttribute("aria-pressed", "true");
    question.querySelectorAll(OPTION).forEach(function (other) {
      other.disabled = true;
    });
    if (option.dataset.correct === "true") {
      option.classList.add("is-correct");
      question.classList.add("is-right");
      state.correct++;
    } else {
      option.classList.add("is-wrong");
      question.classList.add("is-wrong");
      var right = question.querySelector(OPTION + '[data-correct="true"]');
      if (right) {
        right.classList.add("is-correct");
      }
    }
    state.answered++;
    var explain = question.querySelector(EXPLAIN);
    if (explain) {
      explain.hidden = false;
    }
    updateScore(quiz, state);
  }

  function copyText(text, done) {
    var fallback = function () {
      var area = document.createElement("textarea");
      area.value = text;
      area.setAttribute("readonly", "");
      area.className = "edw-quiz-copy-area";
      document.body.appendChild(area);
      area.select();
      try {
        document.execCommand("copy");
        done();
      } catch (e) {
        /* the textarea is on screen (off-canvas): the learner can copy by hand */
      }
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, fallback);
    } else {
      fallback();
    }
  }

  function copyResults(quiz, state, button) {
    var lines = ["Data Hopper EDW training — " + (quiz.dataset.module || document.title), ""];
    quiz.querySelectorAll(QUESTION).forEach(function (question) {
      var prompt = question.querySelector(".edw-quiz-prompt");
      var picked = question.querySelector(OPTION + ".is-picked");
      var right = question.querySelector(OPTION + '[data-correct="true"]');
      var ok = picked && picked.dataset.correct === "true";
      lines.push(
        "[" + (ok ? "ok" : "review") + "] " + (question.dataset.id || "") + " " +
        (prompt ? prompt.textContent.trim() : "")
      );
      lines.push("      picked: " + (picked ? picked.textContent.trim() : "(none)"));
      if (!ok && right) {
        lines.push("      answer: " + right.textContent.trim());
      }
    });
    lines.push("", "Result: " + state.correct + "/" + state.total);
    copyText(lines.join("\n"), function () {
      var label = button.textContent;
      button.textContent = "Copied";
      window.setTimeout(function () {
        button.textContent = label;
      }, 2000);
    });
  }

  function buildFooter(quiz, state) {
    if (quiz.querySelector(".edw-quiz-footer")) {
      return;
    }
    var footer = document.createElement("div");
    footer.className = "edw-quiz-footer";

    var score = document.createElement("span");
    score.className = "edw-quiz-score";
    score.setAttribute("aria-live", "polite");

    var retry = document.createElement("button");
    retry.type = "button";
    retry.className = "edw-quiz-retry";
    retry.textContent = "Try again";
    retry.addEventListener("click", function () {
      reset(quiz, state);
    });

    var copy = document.createElement("button");
    copy.type = "button";
    copy.className = "edw-quiz-copy";
    copy.textContent = "Copy my results";
    copy.addEventListener("click", function () {
      copyResults(quiz, state, copy);
    });

    footer.appendChild(score);
    footer.appendChild(retry);
    footer.appendChild(copy);
    quiz.appendChild(footer);
  }

  function init(quiz) {
    var questions = quiz.querySelectorAll(QUESTION);
    if (!questions.length) {
      return;
    }
    var state = { answered: 0, correct: 0, total: questions.length };
    questions.forEach(function (question) {
      question.querySelectorAll(OPTION).forEach(function (option) {
        option.addEventListener("click", function () {
          answer(quiz, state, question, option);
        });
      });
      if (!question.querySelector(EXPLAIN)) {
        console.warn("edw-quiz: question without explanation", question.dataset.id || "");
      }
      if (!question.querySelector(OPTION + '[data-correct="true"]')) {
        console.warn("edw-quiz: question without a correct option", question.dataset.id || "");
      }
    });
    buildFooter(quiz, state);
    updateScore(quiz, state);
  }

  function start() {
    document.querySelectorAll(".edw-quiz").forEach(init);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
