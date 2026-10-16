# Online training modules (issue #206) — internal design note

Guided training pages that ship with the offline HTML documentation: a walkthrough plus a
questionnaire per topic. Product entry point: [../training/index.adoc](../training/index.adoc).

## Locked decisions

* **Platform: AsciiDoc pages in this repository**, built by the existing `mvn` docs pipeline
  (`asciidoctor` `generate-html-docs` at `prepare-package`) and shipped in the plugin zip under
  `plugins/misc/hopper-edw/docs/training/`. Do not stand up a second site or an LMS for v1; an
  external site can embed or mirror these pages later.
* **Questionnaire: `docs/training/quiz.js` + `quiz.css` inside the page.** No backend, no storage,
  no third-party code, so the pages work from `file://` in the plugin zip, in Hop GUI, in Hop Web and
  on any static host. Do not add a JSON fetch: it is blocked on `file://`.
* **Walkthroughs use committed screenshots** (`docs/images/…`), reusing the product documentation
  captures. Short videos stay the separate `docs/video-tutorials/` pipeline (manifest + TTS) and need
  their own issue.
* **Curriculum**: modules 0-4 are the core "getting started" path (about half a day), 5-8 the
  advanced path; module 0 plus module 7 form the manager/architect path.
* **English only in v1.** Localisation would mirror the video pipeline's `locale:` notion, one file
  per locale; decide when there is a request.

## Format contract (enforced)

`src/test/java/org/hopper/edw/datavault/docs/TrainingModulesTest.java` is the executable
specification. Per module it checks that the file is listed in `MODULES` and in
`docs/training/index.adoc`; that the six level-2 sections exist in order (*Why it matters*,
*Concepts*, *Guided walkthrough*, *Try it yourself*, *Questionnaire*, *Where to go next*); that the
questionnaire has 5-10 questions, each with 2-5 options, exactly one `data-correct="true"` and one
`edw-quiz-explain`; that `quiz.css` and `quiz.js` are referenced; and that every `image::` and
`link:` target exists on disk. Module 1 must name the supported versions (2.20 / Java 21) — the
version drift guard.

Rules that are easy to get wrong:

* `.adoc` files start with the title; **no HTML license comment** above it. Asciidoctor then treats
  the title as a nested section: *level 0 sections can only be used when doctype is book*, and the
  page loses its `<h1>`. (`docs/**` is RAT-excluded, so no header is required there.)
* Inside the questionnaire passthrough block (`++++ … ++++`) only raw HTML runs: no backticks, no
  `**bold**`, no `link:` macros, no `{attribute}` substitution. Use `<code>`, `<strong>`, `<em>`,
  `<a href="page.html#anchor">`.
* Images inside a numbered list attach on a `+` continuation line, otherwise the list restarts and
  the image is no longer part of the step.
* Never link a module file that is not on disk yet — the link test fails. Module 0 pointed at
  `index.adoc` until module 1 landed, then switched to module 1.

## Host integration point (future tracking)

The widget dispatches `edw-quiz-complete` (`{module, correct, total}`) on `window` when the last
question is answered, and appends the same object to `window.__edwQuizzes`. A wrapper page on
data-hopper.com, or an LMS embedding these pages, can collect results without touching module
content. The in-page **Copy my results** button gives the learner a text summary for a trainer or a
feedback form. No central collection is wired up in v1.

## Review on each minor release

1. `mvn -o test -Dtest=TrainingModulesTest` — green. Catches removed files, dangling links, renamed
   screenshots and malformed questionnaires.
2. Check module 1 against the version pins: `pom.xml` (`hop.version`) and the version table in
   `CLAUDE.md` (Hop / JDK).
3. Re-run the training-environment commands below and update the *what you should see* text in
   `docs/training/index.adoc` if the output changed.
4. Walk module 0 in Hop GUI for ten minutes against the current plugin; fix renamed toolbar or menu
   labels (GUI labels are the usual drift).
5. Read the release's `CHANGELOG.md` entries. For each user-visible capability decide *add to an
   existing module* / *new module* / *reference docs only*, and open an issue for what needs writing.

Training environment (see the training index for the learner-facing version):

```sh
./scripts/run-postgres.sh up                 # hop-data-vault-postgres-local, port 54320, test/test
./scripts/run-hop.sh retail-example workflows/run-retail-initial.hwf
docker exec hop-data-vault-postgres-local psql -U test -p 54320 -d test_edw -c '\dt' | head -20
```

## Deferred (do not re-litigate in review)

* **One-command bootstrap** (`scripts/run-training.sh`) — duplicates `run-postgres.sh up` plus the
  retail initial load, both already documented in the training index.
* **Certificates, badges or completion tracking** — needs accounts or an LMS; the site wrapper is the
  prerequisite (see the host integration point).
* **Videos per module** — the `docs/video-tutorials/` pipeline is the home for those; own issue.
* **Localisation** — one file per locale when there is a request.
