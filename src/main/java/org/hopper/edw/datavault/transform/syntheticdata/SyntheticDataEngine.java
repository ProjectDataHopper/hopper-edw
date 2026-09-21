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
package org.hopper.edw.datavault.transform.syntheticdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;

/**
 * Seeded row generator used by the synthetic data transform. Each call has its own {@link Random};
 * referenced rows come from the caller so pipelines do not share an RNG across transforms.
 *
 * <p>Structural choices (which parents, how many children, which path) are drawn before field
 * values. Field values are produced one row at a time.
 */
public final class SyntheticDataEngine {

  public List<Map<String, Object>> generate(
      SyntheticDataMeta meta,
      IVariables variables,
      List<Map<String, Object>> parents,
      List<Map<String, Object>> children,
      List<Object> pairLeft,
      List<Object> pairRight)
      throws HopException {
    List<Map<String, Object>> rows = new ArrayList<>();
    Iterator<Map<String, Object>> cursor =
        iterate(meta, variables, parents, children, pairLeft, pairRight);
    while (cursor.hasNext()) {
      rows.add(cursor.next());
    }
    return rows;
  }

  public Iterator<Map<String, Object>> iterate(
      SyntheticDataMeta meta,
      IVariables variables,
      List<Map<String, Object>> parents,
      List<Map<String, Object>> children,
      List<Object> pairLeft,
      List<Object> pairRight)
      throws HopException {
    long seed =
        NumericExpression.evaluateLong(blank(meta.getSeed(), "1"), variables, Map.of());
    Random random = new Random(seed);
    List<Plan> plans =
        buildPlans(
            meta,
            variables,
            random,
            nullToEmpty(parents),
            nullToEmpty(children),
            pairLeft == null ? List.of() : pairLeft,
            pairRight == null ? List.of() : pairRight);
    return new Cursor(meta, variables, random, plans, nullToEmpty(parents));
  }

  private List<Plan> buildPlans(
      SyntheticDataMeta meta,
      IVariables variables,
      Random random,
      List<Map<String, Object>> parents,
      List<Map<String, Object>> children,
      List<Object> pairLeft,
      List<Object> pairRight)
      throws HopException {
    String mode = blank(meta.getCardinalityMode(), "COUNT").trim().toUpperCase(Locale.ROOT);
    return switch (mode) {
      case "COUNT" -> countPlans(meta, variables);
      case "COMBINE" -> combinePlans(meta, variables, random);
      case "PER_PARENT" -> parentPlans(meta, variables, random, parents, false);
      case "PATHS" -> pathPlans(meta, variables, random, parents);
      case "HIERARCHY" -> hierarchyPlans(meta, variables, random, parents, children);
      case "UNIQUE_PAIRS" -> pairPlans(meta, variables, random, pairLeft, pairRight);
      default -> throw new HopException("Unknown synthetic cardinality '" + mode + "'");
    };
  }

  private List<Plan> countPlans(SyntheticDataMeta meta, IVariables variables) throws HopException {
    long count = NumericExpression.evaluateLong(blank(meta.getRowCount(), "0"), variables, Map.of());
    List<Plan> plans = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      plans.add(Plan.count(i));
    }
    return plans;
  }

  private List<Plan> combinePlans(SyntheticDataMeta meta, IVariables variables, Random random)
      throws HopException {
    List<Long> ids = new ArrayList<>();
    for (SyntheticPopulation population : safe(meta.getPopulations())) {
      long count = NumericExpression.evaluateLong(blank(population.getCount(), "0"), variables, Map.of());
      if (count <= 0) {
        continue;
      }
      String kind = blank(population.getKind(), "RANGE").trim().toUpperCase(Locale.ROOT);
      if ("SAMPLE".equals(kind)) {
        long from = NumericExpression.evaluateLong(blank(population.getFrom(), "1"), variables, Map.of());
        long to = NumericExpression.evaluateLong(blank(population.getTo(), "1"), variables, Map.of());
        ids.addAll(sampleIds(from, to, count, random));
      } else if ("RANGE".equals(kind)) {
        long start =
            NumericExpression.evaluateLong(blank(population.getStart(), "1"), variables, Map.of());
        for (long i = 0; i < count; i++) {
          ids.add(start + i);
        }
      } else {
        throw new HopException("Unknown population kind '" + kind + "'");
      }
    }
    List<Plan> plans = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      plans.add(Plan.population(i, ids.get(i)));
    }
    return plans;
  }

  private List<Plan> parentPlans(
      SyntheticDataMeta meta,
      IVariables variables,
      Random random,
      List<Map<String, Object>> parents,
      boolean filtered)
      throws HopException {
    List<Integer> candidates = List.of();
    if (!filtered) {
      candidates = new ArrayList<>();
      for (int i = 0; i < parents.size(); i++) {
        candidates.add(i);
      }
    }
    return expandParents(meta, variables, random, parents, candidates);
  }

  private List<Plan> expandParents(
      SyntheticDataMeta meta,
      IVariables variables,
      Random random,
      List<Map<String, Object>> parents,
      List<Integer> candidates)
      throws HopException {
    List<Integer> selected = selectIndexes(meta, variables, random, candidates.size());
    int minChildren = (int) longExpr(meta.getMinChildren(), 1, variables);
    int maxChildren = (int) longExpr(meta.getMaxChildren(), minChildren, variables);
    if (maxChildren < minChildren) {
      int swap = minChildren;
      minChildren = maxChildren;
      maxChildren = swap;
    }
    List<Plan> plans = new ArrayList<>();
    int row = 0;
    for (int local : selected) {
      int parentIndex = candidates.get(local);
      int childCount =
          minChildren == maxChildren
              ? minChildren
              : minChildren + random.nextInt(maxChildren - minChildren + 1);
      childCount = forceChildCount(meta, variables, parentIndex, childCount);
      for (int child = 0; child < childCount; child++) {
        plans.add(Plan.parent(row++, parentIndex, child));
      }
    }
    return plans;
  }

  private List<Plan> pathPlans(
      SyntheticDataMeta meta,
      IVariables variables,
      Random random,
      List<Map<String, Object>> parents)
      throws HopException {
    List<Integer> candidates = new ArrayList<>();
    for (int i = 0; i < parents.size(); i++) {
      candidates.add(i);
    }
    List<Integer> selected = selectIndexes(meta, variables, random, candidates.size());
    List<Plan> plans = new ArrayList<>();
    int row = 0;
    for (int local : selected) {
      int parentIndex = candidates.get(local);
      String selector =
          TemplateRenderer.stringify(parents.get(parentIndex).get(meta.getSelectorField()));
      SyntheticPath path = matchPath(safe(meta.getPaths()), selector);
      if (path == null) {
        continue;
      }
      List<String> steps = resolveSteps(path, random);
      for (int step = 0; step < steps.size(); step++) {
        plans.add(Plan.path(row++, parentIndex, steps.get(step), step));
      }
    }
    return plans;
  }

  private List<Plan> hierarchyPlans(
      SyntheticDataMeta meta,
      IVariables variables,
      Random random,
      List<Map<String, Object>> parents,
      List<Map<String, Object>> children)
      throws HopException {
    List<Integer> candidates = filterStatuses(parents, meta.getSelectorField(), meta.getIncludeStatuses());
    if (candidates.isEmpty() && !Utils.isEmpty(meta.getFallbackStatuses())) {
      candidates = filterStatuses(parents, meta.getSelectorField(), meta.getFallbackStatuses());
    }
    List<Integer> selectedLocal = selectIndexes(meta, variables, random, candidates.size());
    Map<String, List<Map<String, Object>>> byKey = new LinkedHashMap<>();
    for (Map<String, Object> child : children) {
      String key = TemplateRenderer.stringify(child.get(meta.getChildKey()));
      byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(child);
    }
    int splitMin = (int) longExpr(meta.getSplitMinChildren(), 0, variables);
    double splitP = doubleExpr(meta.getSplitProbability(), 0, variables);
    List<Plan> plans = new ArrayList<>();
    int row = 0;
    int group = 0;
    for (int local : selectedLocal) {
      int parentIndex = candidates.get(local);
      String key =
          TemplateRenderer.stringify(parents.get(parentIndex).get(meta.getParentKey()));
      List<Map<String, Object>> lines = byKey.getOrDefault(key, List.of());
      if (lines.isEmpty()) {
        continue;
      }
      List<List<Map<String, Object>>> packages = splitPackages(lines, splitMin, splitP, random);
      group++;
      int packageIndex = 0;
      for (List<Map<String, Object>> pkg : packages) {
        packageIndex++;
        int childIndex = 0;
        for (Map<String, Object> child : pkg) {
          plans.add(Plan.hierarchy(row++, parentIndex, childIndex++, packageIndex, group, child, pkg));
        }
      }
    }
    return plans;
  }

  private List<Plan> pairPlans(
      SyntheticDataMeta meta,
      IVariables variables,
      Random random,
      List<Object> pairLeft,
      List<Object> pairRight)
      throws HopException {
    List<Object> left = new ArrayList<>(pairLeft);
    List<Object> right = new ArrayList<>(pairRight);
    if (left.isEmpty() && !Utils.isEmpty(meta.getPairLeftCount())) {
      left.addAll(rangeValues(meta.getPairLeftStart(), meta.getPairLeftCount(), variables));
    }
    if (right.isEmpty() && !Utils.isEmpty(meta.getPairRightCount())) {
      right.addAll(rangeValues(meta.getPairRightStart(), meta.getPairRightCount(), variables));
    }
    long requested = longExpr(meta.getPairCount(), 0, variables);
    if (requested <= 0 || left.isEmpty() || right.isEmpty()) {
      return List.of();
    }
    long cartesian = (long) left.size() * right.size();
    long target = Math.min(requested, cartesian);
    Set<Long> seen = new TreeSet<>();
    int guard = 0;
    int guardMax = (int) Math.min(Integer.MAX_VALUE, target * 30 + cartesian);
    while (seen.size() < target && guard < guardMax) {
      int i = random.nextInt(left.size());
      int j = random.nextInt(right.size());
      seen.add((long) i * right.size() + j);
      guard++;
    }
    List<Plan> plans = new ArrayList<>();
    int row = 0;
    for (long key : seen) {
      int i = (int) (key / right.size());
      int j = (int) (key % right.size());
      plans.add(Plan.pair(row++, left.get(i), right.get(j)));
    }
    return plans;
  }

  private static List<Object> rangeValues(String startExpr, String countExpr, IVariables variables)
      throws HopException {
    long start = NumericExpression.evaluateLong(blank(startExpr, "1"), variables, Map.of());
    long count = NumericExpression.evaluateLong(blank(countExpr, "0"), variables, Map.of());
    List<Object> values = new ArrayList<>();
    for (long i = 0; i < count; i++) {
      values.add(start + i);
    }
    return values;
  }

  private List<Long> sampleIds(long from, long to, long count, Random random) throws HopException {
    long span = to - from + 1;
    if (span <= 0 || count <= 0) {
      return List.of();
    }
    if (count >= span) {
      List<Long> all = new ArrayList<>();
      for (long value = from; value <= to; value++) {
        all.add(value);
      }
      return all;
    }
    if (span > Integer.MAX_VALUE) {
      throw new HopException("Sample range " + from + ".." + to + " is too large");
    }
    Set<Long> picked = new TreeSet<>();
    int spanInt = (int) span;
    while (picked.size() < count) {
      picked.add(from + random.nextInt(spanInt));
    }
    return new ArrayList<>(picked);
  }

  private List<Integer> selectIndexes(
      SyntheticDataMeta meta, IVariables variables, Random random, int size) throws HopException {
    if (size <= 0) {
      return List.of();
    }
    double fraction = doubleExpr(meta.getFraction(), 1, variables);
    int minRows = (int) longExpr(meta.getMinRows(), 0, variables);
    int k = (int) (size * fraction);
    k = Math.max(minRows, k);
    if (k <= 0) {
      return List.of();
    }
    if (k >= size) {
      List<Integer> all = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        all.add(i);
      }
      return all;
    }
    Set<Integer> selected = new TreeSet<>();
    if (meta.isIncludeFirst()) {
      selected.add(0);
    }
    int guard = 0;
    while (selected.size() < k && guard < k * 100 + size) {
      selected.add(random.nextInt(size));
      guard++;
    }
    return new ArrayList<>(selected);
  }

  private int forceChildCount(
      SyntheticDataMeta meta, IVariables variables, int parentIndex, int childCount)
      throws HopException {
    for (SyntheticOverride override : safe(meta.getOverrides())) {
      if (Utils.isEmpty(override.getForceChildCount()) || !Utils.isEmpty(override.getChildIndex())) {
        continue;
      }
      if (!indexMatches(override.getParentIndex(), override.getParentOp(), parentIndex)) {
        continue;
      }
      childCount =
          (int)
              NumericExpression.evaluateLong(
                  override.getForceChildCount(), variables, Map.of("parent_index", (long) parentIndex));
    }
    return Math.max(0, childCount);
  }

  private static boolean indexMatches(String expected, String op, int actual) {
    if (Utils.isEmpty(expected)) {
      return true;
    }
    int value = Integer.parseInt(expected.trim());
    if ("<=".equals(op == null ? "" : op.trim())) {
      return actual <= value;
    }
    return actual == value;
  }

  private static SyntheticPath matchPath(List<SyntheticPath> paths, String selector) {
    SyntheticPath wildcard = null;
    for (SyntheticPath path : paths) {
      String when = blank(path.getWhen(), "*").trim();
      if ("*".equals(when)) {
        if (wildcard == null) {
          wildcard = path;
        }
        continue;
      }
      if (when.equals(selector)) {
        return path;
      }
    }
    return wildcard;
  }

  private static List<String> resolveSteps(SyntheticPath path, Random random) {
    boolean primary = true;
    if (!Utils.isEmpty(path.getProbability())) {
      primary = random.nextDouble() < Double.parseDouble(path.getProbability().trim());
    }
    List<String> steps = splitSteps(primary ? path.getSteps() : path.getElseSteps());
    if (!Utils.isEmpty(path.getExtraStep()) && !Utils.isEmpty(path.getExtraProbability())) {
      if (random.nextDouble() < Double.parseDouble(path.getExtraProbability().trim())) {
        steps.add(path.getExtraStep().trim());
      }
    }
    return steps;
  }

  private static List<String> splitSteps(String raw) {
    if (Utils.isEmpty(raw)) {
      return new ArrayList<>();
    }
    List<String> steps = new ArrayList<>();
    for (String step : raw.split("\\|")) {
      if (!step.isBlank()) {
        steps.add(step.trim());
      }
    }
    return steps;
  }

  private static List<Integer> filterStatuses(
      List<Map<String, Object>> parents, String field, String statuses) {
    if (Utils.isEmpty(statuses)) {
      List<Integer> all = new ArrayList<>();
      for (int i = 0; i < parents.size(); i++) {
        all.add(i);
      }
      return all;
    }
    Set<String> allowed = new java.util.HashSet<>();
    for (String status : statuses.split("\\|")) {
      if (!status.isBlank()) {
        allowed.add(status.trim());
      }
    }
    List<Integer> indexes = new ArrayList<>();
    for (int i = 0; i < parents.size(); i++) {
      if (allowed.contains(TemplateRenderer.stringify(parents.get(i).get(field)))) {
        indexes.add(i);
      }
    }
    return indexes;
  }

  private static List<List<Map<String, Object>>> splitPackages(
      List<Map<String, Object>> lines, int splitMin, double splitProbability, Random random) {
    if (splitMin > 0 && lines.size() >= splitMin && splitProbability > 0 && random.nextDouble() < splitProbability) {
      int at = Math.max(1, lines.size() / 2);
      return List.of(
          new ArrayList<>(lines.subList(0, at)), new ArrayList<>(lines.subList(at, lines.size())));
    }
    return List.of(new ArrayList<>(lines));
  }

  private static long longExpr(String expression, long defaultValue, IVariables variables)
      throws HopException {
    if (Utils.isEmpty(expression)) {
      return defaultValue;
    }
    return NumericExpression.evaluateLong(expression, variables, Map.of());
  }

  private static double doubleExpr(String expression, double defaultValue, IVariables variables)
      throws HopException {
    if (Utils.isEmpty(expression)) {
      return defaultValue;
    }
    return NumericExpression.evaluateDouble(expression, variables, Map.of());
  }

  private static String blank(String value, String fallback) {
    return Utils.isEmpty(value) ? fallback : value;
  }

  private static <T> List<T> safe(List<T> values) {
    return values == null ? List.of() : values;
  }

  private static List<Map<String, Object>> nullToEmpty(List<Map<String, Object>> rows) {
    return rows == null ? List.of() : rows;
  }

  private static final class Cursor implements Iterator<Map<String, Object>> {
    private final SyntheticDataMeta meta;
    private final IVariables variables;
    private final Random random;
    private final List<Plan> plans;
    private final List<Map<String, Object>> parents;
    private final Map<String, Object> parentScope = new HashMap<>();
    private final Map<String, Object> packageScope = new HashMap<>();
    private final Map<String, Long> groupCounters = new HashMap<>();
    private final List<Map<String, Object>> priorChildren = new ArrayList<>();
    private int parentScopeKey = Integer.MIN_VALUE;
    private int packageScopeKey = Integer.MIN_VALUE;
    private int index;

    private Cursor(
        SyntheticDataMeta meta,
        IVariables variables,
        Random random,
        List<Plan> plans,
        List<Map<String, Object>> parents) {
      this.meta = meta;
      this.variables = variables;
      this.random = random;
      this.plans = plans;
      this.parents = parents;
    }

    @Override
    public boolean hasNext() {
      return index < plans.size();
    }

    @Override
    public Map<String, Object> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      try {
        return generateRow(plans.get(index++));
      } catch (HopException e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    }

    private Map<String, Object> generateRow(Plan plan) throws HopException {
      if (plan.parentIndex != parentScopeKey) {
        parentScope.clear();
        parentScopeKey = plan.parentIndex;
        priorChildren.clear();
      }
      int packageKey = plan.groupIndex * 1_000_003 + plan.packageIndex;
      if (packageKey != packageScopeKey) {
        packageScope.clear();
        packageScopeKey = packageKey;
        if (plan.groupIndex > 0) {
          priorChildren.clear();
        }
      }
      Map<String, Object> parent = plan.parentIndex >= 0 ? parents.get(plan.parentIndex) : null;
      Map<String, Object> values = new LinkedHashMap<>();
      for (SyntheticField field : safe(meta.getFields())) {
        if (Utils.isEmpty(field.getName())) {
          continue;
        }
        SyntheticField effective = applyOverride(field, plan);
        Map<String, String> args = GeneratorArguments.parse(effective.getArguments());
        String scope = GeneratorArguments.get(args, "scope", "row");
        Object value;
        if ("parent".equals(scope) && parentScope.containsKey(field.getName())) {
          value = parentScope.get(field.getName());
        } else if ("package".equals(scope) && packageScope.containsKey(field.getName())) {
          value = packageScope.get(field.getName());
        } else {
          Map<String, Object> context = context(plan, values, parent, plan.child);
          value = produce(effective, args, plan, context);
          value = coerce(effective.getHopType(), value);
          if ("parent".equals(scope)) {
            parentScope.put(field.getName(), value);
          } else if ("package".equals(scope)) {
            packageScope.put(field.getName(), value);
          }
        }
        values.put(field.getName(), value);
      }
      if (plan.parentIndex >= 0) {
        priorChildren.add(new LinkedHashMap<>(values));
      }
      return values;
    }

    private SyntheticField applyOverride(SyntheticField field, Plan plan) {
      SyntheticField chosen = field;
      for (SyntheticOverride override : safe(meta.getOverrides())) {
        if (Utils.isEmpty(override.getField()) || !override.getField().equals(field.getName())) {
          continue;
        }
        if (!indexMatches(override.getParentIndex(), override.getParentOp(), plan.parentIndex)) {
          continue;
        }
        if (!indexMatches(override.getChildIndex(), override.getChildOp(), plan.childIndex)) {
          continue;
        }
        SyntheticField replaced = new SyntheticField(field);
        replaced.setGenerator(override.getGenerator());
        replaced.setArguments(override.getArguments());
        chosen = replaced;
      }
      return chosen;
    }

    private Object produce(
        SyntheticField field, Map<String, String> args, Plan plan, Map<String, Object> context)
        throws HopException {
      String repeat = args.get("repeatPreviousProbability");
      if (!Utils.isEmpty(repeat) && plan.childIndex > 0 && !priorChildren.isEmpty()) {
        if (random.nextDouble() < Double.parseDouble(repeat)) {
          Object previous =
              priorChildren.get(priorChildren.size() - 1).get(field.getName());
          if (previous != null) {
            return previous;
          }
        }
      }
      String generator = blank(field.getGenerator(), "CONSTANT").trim().toUpperCase(Locale.ROOT);
      Object value =
          switch (generator) {
            case "CONSTANT" -> variables.resolve(GeneratorArguments.get(args, "value", ""));
            case "SEQUENCE" -> sequence(args, plan, false, false);
            case "SEQUENCE_IN_PARENT" -> sequence(args, plan, true, false);
            case "SEQUENCE_IN_GROUP" -> sequenceInGroup(args, context);
            case "CHOICE" -> choice(args, plan);
            case "INT_RANGE" -> intRange(args, field, plan);
            case "NUMBER_RANGE" -> numberRange(args);
            case "NUMBER_EXPR", "LONG_EXPR" -> numberExpr(args, context);
            case "TEMPLATE" ->
                TemplateRenderer.render(GeneratorArguments.get(args, "pattern", ""), variables, context);
            case "DATE_OFFSET" -> dateOffset(args, context);
            case "COPY" -> copy(args, context);
            case "COPY_SIBLING" -> copySibling(args, field);
            case "LOOKUP" -> lookup(args, context);
            case "REFERENCE" -> reference(args, context);
            case "UUID" -> uuid();
            case "MOD_HASH" -> modHash(args, context);
            case "JSON" -> json(args, context);
            case "CHILD_AGGREGATE" -> childAggregate(args, plan);
            default -> throw new HopException("Unknown generator '" + field.getGenerator() + "'");
          };
      String excludeSibling = args.get("excludeSibling");
      if (!Utils.isEmpty(excludeSibling)) {
        int sibling = Integer.parseInt(excludeSibling.trim());
        int guard = 0;
        while (guard < 16
            && sibling >= 0
            && sibling < priorChildren.size()
            && same(value, priorChildren.get(sibling).get(field.getName()))) {
          value = reroll(generator, args, field, plan, context);
          guard++;
        }
      }
      return value;
    }

    private Object reroll(
        String generator,
        Map<String, String> args,
        SyntheticField field,
        Plan plan,
        Map<String, Object> context)
        throws HopException {
      return switch (generator) {
        case "INT_RANGE" -> intRange(args, field, plan);
        case "NUMBER_RANGE" -> numberRange(args);
        case "CHOICE" -> choice(args, plan);
        case "REFERENCE" -> reference(args, context);
        default -> produce(field, withoutExclude(args), plan, context);
      };
    }

    private static Map<String, String> withoutExclude(Map<String, String> args) {
      Map<String, String> copy = new LinkedHashMap<>(args);
      copy.remove("excludeSibling");
      copy.remove("repeatPreviousProbability");
      return copy;
    }

    private Object sequence(Map<String, String> args, Plan plan, boolean inParent, boolean ignored)
        throws HopException {
      String source = GeneratorArguments.get(args, "source", "");
      long number;
      if (inParent || "child".equals(source)) {
        long start = longExpr(args.get("start"), 1, variables);
        long step = longExpr(args.get("step"), 1, variables);
        number = start + (long) plan.childIndex * step;
      } else if ("population".equals(source) || (source.isEmpty() && plan.populationId != null)) {
        number = plan.populationId;
      } else if ("group".equals(source)) {
        number = plan.groupIndex;
      } else if ("package".equals(source)) {
        number = plan.packageIndex;
      } else {
        long start = longExpr(args.get("start"), 1, variables);
        long step = longExpr(args.get("step"), 1, variables);
        number = start + (long) plan.rowIndex * step;
      }
      String format = args.get("format");
      if (!Utils.isEmpty(format)) {
        return String.format(Locale.ROOT, format, number);
      }
      return number;
    }

    private long sequenceInGroup(Map<String, String> args, Map<String, Object> context)
        throws HopException {
      String group = TemplateRenderer.stringify(context.get(GeneratorArguments.get(args, "groupField", "")));
      long start = longExpr(args.get("start"), 0, variables);
      Long current = groupCounters.get(group);
      if (current == null) {
        groupCounters.put(group, start);
        return start;
      }
      long next = current + longExpr(args.get("step"), 1, variables);
      groupCounters.put(group, next);
      return next;
    }

    private Object choice(Map<String, String> args, Plan plan) throws HopException {
      String raw = GeneratorArguments.get(args, "values", "");
      if (raw.isEmpty()) {
        throw new HopException("CHOICE generator requires values");
      }
      String[] values = raw.split("\\|");
      String strategy = GeneratorArguments.get(args, "strategy", "random");
      if ("round_robin".equalsIgnoreCase(strategy)) {
        return values[Math.floorMod(plan.rowIndex, values.length)];
      }
      return values[random.nextInt(values.length)];
    }

    private long intRange(Map<String, String> args, SyntheticField field, Plan plan)
        throws HopException {
      Map<String, Object> ctx = Map.of("row_index", (long) plan.rowIndex);
      long min = NumericExpression.evaluateWithDates(GeneratorArguments.get(args, "min", "0"), variables, ctx)
          .setScale(0, RoundingMode.DOWN)
          .longValue();
      long max = NumericExpression.evaluateWithDates(GeneratorArguments.get(args, "max", "0"), variables, ctx)
          .setScale(0, RoundingMode.DOWN)
          .longValue();
      if (max < min) {
        throw new HopException(
            "INT_RANGE max is below min for field '" + field.getName() + "' (" + min + ".." + max + ")");
      }
      long span = max - min + 1;
      if (span <= 1) {
        return min;
      }
      if (span > Integer.MAX_VALUE) {
        throw new HopException("INT_RANGE span is too large for field '" + field.getName() + "'");
      }
      return min + random.nextInt((int) span);
    }

    private double numberRange(Map<String, String> args) throws HopException {
      BigDecimal min =
          NumericExpression.evaluate(GeneratorArguments.get(args, "min", "0"), variables, Map.of());
      BigDecimal max =
          NumericExpression.evaluate(GeneratorArguments.get(args, "max", "0"), variables, Map.of());
      int scale = (int) longExpr(args.get("scale"), 2, variables);
      double value =
          min.doubleValue() + (max.doubleValue() - min.doubleValue()) * random.nextDouble();
      return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private Object numberExpr(Map<String, String> args, Map<String, Object> context)
        throws HopException {
      BigDecimal value =
          NumericExpression.evaluateWithDates(
              GeneratorArguments.get(args, "expression", "0"), variables, context);
      if (args.containsKey("scale")) {
        int scale = Integer.parseInt(args.get("scale").trim());
        return value.setScale(scale, RoundingMode.HALF_UP).doubleValue();
      }
      return value.setScale(0, RoundingMode.DOWN).longValue();
    }

    private String dateOffset(Map<String, String> args, Map<String, Object> context)
        throws HopException {
      String baseToken = GeneratorArguments.get(args, "base", "");
      if (variables != null) {
        baseToken = variables.resolve(baseToken);
      }
      Object baseValue = resolveToken(baseToken, context);
      LocalDate base = NumericExpression.toLocalDate(baseValue);
      int days;
      if (!Utils.isEmpty(args.get("daysExpr"))) {
        days =
            NumericExpression.evaluateWithDates(args.get("daysExpr"), variables, context)
                .setScale(0, RoundingMode.DOWN)
                .intValue();
      } else {
        int min = (int) longExpr(args.get("minDays"), 0, variables);
        int max = (int) longExpr(args.get("maxDays"), min, variables);
        if (max < min) {
          throw new HopException("DATE_OFFSET maxDays is below minDays");
        }
        days = min == max ? min : min + random.nextInt(max - min + 1);
      }
      String format = GeneratorArguments.get(args, "format", "yyyy-MM-dd");
      return DateTimeFormatter.ofPattern(format).format(base.plusDays(days).atStartOfDay());
    }

    private static Object copy(Map<String, String> args, Map<String, Object> context)
        throws HopException {
      String field = GeneratorArguments.get(args, "field", "");
      if (!context.containsKey(field)) {
        throw new HopException("COPY field '" + field + "' is not available");
      }
      return context.get(field);
    }

    private Object copySibling(Map<String, String> args, SyntheticField field) throws HopException {
      int sibling = (int) longExpr(args.get("child"), 0, variables);
      if (sibling < 0 || sibling >= priorChildren.size()) {
        throw new HopException(
            "COPY_SIBLING child " + sibling + " is not available for field '" + field.getName() + "'");
      }
      String source = GeneratorArguments.get(args, "field", field.getName());
      return priorChildren.get(sibling).get(source);
    }

    private Object lookup(Map<String, String> args, Map<String, Object> context) throws HopException {
      String key =
          TemplateRenderer.stringify(context.get(GeneratorArguments.get(args, "keyField", "")));
      String entries = GeneratorArguments.get(args, "entries", "");
      for (String entry : entries.split("\\|")) {
        int colon = entry.indexOf(':');
        if (colon > 0 && entry.substring(0, colon).trim().equals(key)) {
          return entry.substring(colon + 1).trim();
        }
      }
      if ("CHOICE".equalsIgnoreCase(args.get("fallbackGenerator"))) {
        String values = GeneratorArguments.get(args, "fallbackValues", "");
        String[] options = values.split("\\|");
        if (options.length == 0 || options[0].isEmpty()) {
          return "";
        }
        return options[random.nextInt(options.length)];
      }
      return TemplateRenderer.render(GeneratorArguments.get(args, "fallback", ""), variables, context);
    }

    private Object reference(Map<String, String> args, Map<String, Object> context)
        throws HopException {
      String exclude = args.get("excludeField");
      Object value = null;
      for (int guard = 0; guard < 20; guard++) {
        value = rollReference(args);
        if (Utils.isEmpty(exclude) || !same(value, context.get(exclude))) {
          return value;
        }
      }
      return value;
    }

    private long rollReference(Map<String, String> args) throws HopException {
      long min = NumericExpression.evaluateLong(GeneratorArguments.get(args, "min", "1"), variables, Map.of());
      long max = NumericExpression.evaluateLong(GeneratorArguments.get(args, "max", "1"), variables, Map.of());
      if (max < min) {
        throw new HopException("REFERENCE max is below min");
      }
      long span = max - min + 1;
      if (span > Integer.MAX_VALUE) {
        throw new HopException("REFERENCE span is too large");
      }
      return min + (span <= 1 ? 0 : random.nextInt((int) span));
    }

    private String uuid() {
      byte[] bytes = new byte[16];
      random.nextBytes(bytes);
      bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
      bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
      long high = 0;
      long low = 0;
      for (int i = 0; i < 8; i++) {
        high = (high << 8) | (bytes[i] & 0xffL);
      }
      for (int i = 8; i < 16; i++) {
        low = (low << 8) | (bytes[i] & 0xffL);
      }
      return new UUID(high, low).toString();
    }

    private int modHash(Map<String, String> args, Map<String, Object> context) throws HopException {
      String text =
          TemplateRenderer.stringify(context.get(GeneratorArguments.get(args, "field", "")));
      int sum = 0;
      for (int i = 0; i < text.length(); i++) {
        sum += text.charAt(i);
      }
      int divisor = Integer.parseInt(GeneratorArguments.get(args, "divisor", "1"));
      if (divisor <= 0) {
        throw new HopException("MOD_HASH divisor must be positive");
      }
      return Math.floorMod(sum, divisor);
    }

    private String json(Map<String, String> args, Map<String, Object> context) throws HopException {
      Map<String, Object> root = new LinkedHashMap<>();
      for (Map.Entry<String, String> entry : args.entrySet()) {
        if ("scope".equals(entry.getKey())) {
          continue;
        }
        putPath(root, entry.getKey(), jsonValue(entry.getValue(), context));
      }
      return writeJson(root);
    }

    private Object jsonValue(String raw, Map<String, Object> context) throws HopException {
      String trimmed = raw == null ? "" : raw.trim();
      if (trimmed.startsWith("${") && trimmed.endsWith("}") && trimmed.indexOf('}') == trimmed.length() - 1
          && trimmed.indexOf("${", 2) < 0) {
        String name = trimmed.substring(2, trimmed.length() - 1);
        if (context.containsKey(name) && context.get(name) instanceof Number number) {
          return number;
        }
      }
      return TemplateRenderer.render(trimmed, variables, context);
    }

    @SuppressWarnings("unchecked")
    private static void putPath(Map<String, Object> root, String path, Object value) {
      String[] parts = path.split("\\.");
      Map<String, Object> current = root;
      for (int i = 0; i < parts.length - 1; i++) {
        Object next = current.get(parts[i]);
        if (!(next instanceof Map)) {
          next = new LinkedHashMap<String, Object>();
          current.put(parts[i], next);
        }
        current = (Map<String, Object>) next;
      }
      current.put(parts[parts.length - 1], value);
    }

    private static String writeJson(Object value) {
      if (value instanceof Map<?, ?> map) {
        StringBuilder out = new StringBuilder();
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (!first) {
            out.append(',');
          }
          first = false;
          out.append('"').append(escapeJson(String.valueOf(entry.getKey()))).append('"');
          out.append(':');
          out.append(writeJson(entry.getValue()));
        }
        out.append('}');
        return out.toString();
      }
      if (value instanceof Number) {
        return TemplateRenderer.stringify(value);
      }
      return "\"" + escapeJson(value == null ? "" : String.valueOf(value)) + "\"";
    }

    private static String escapeJson(String value) {
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        switch (c) {
          case '"' -> out.append("\\\"");
          case '\\' -> out.append("\\\\");
          case '\n' -> out.append("\\n");
          case '\r' -> out.append("\\r");
          case '\t' -> out.append("\\t");
          default -> out.append(c);
        }
      }
      return out.toString();
    }

    private double childAggregate(Map<String, String> args, Plan plan) throws HopException {
      String childField = GeneratorArguments.get(args, "childField", "");
      BigDecimal sum = BigDecimal.ZERO;
      List<Map<String, Object>> rows =
          plan.packageChildren == null ? List.of() : plan.packageChildren;
      for (Map<String, Object> child : rows) {
        Object quantity = child.get(childField);
        if (quantity != null) {
          sum = sum.add(new BigDecimal(String.valueOf(quantity)));
        }
      }
      double min = Double.parseDouble(GeneratorArguments.get(args, "factorMin", "1"));
      double max = Double.parseDouble(GeneratorArguments.get(args, "factorMax", "1"));
      double factor = min + (max - min) * random.nextDouble();
      int scale = Integer.parseInt(GeneratorArguments.get(args, "scale", "2"));
      return sum.multiply(BigDecimal.valueOf(factor)).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static Object resolveToken(String token, Map<String, Object> context) throws HopException {
      if (Utils.isEmpty(token)) {
        throw new HopException("DATE_OFFSET base is empty");
      }
      if (context.containsKey(token)) {
        return context.get(token);
      }
      if (token.startsWith("${") && token.endsWith("}")) {
        String name = token.substring(2, token.length() - 1);
        if (context.containsKey(name)) {
          return context.get(name);
        }
      }
      return token;
    }

    private Map<String, Object> context(
        Plan plan,
        Map<String, Object> values,
        Map<String, Object> parent,
        Map<String, Object> child) {
      Map<String, Object> ctx = new HashMap<>();
      ctx.put("row_index", (long) plan.rowIndex);
      ctx.put("parent_index", (long) Math.max(plan.parentIndex, 0));
      ctx.put("child_index", (long) plan.childIndex);
      ctx.put("step_index", (long) plan.stepIndex);
      ctx.put("step_name", plan.stepName == null ? "" : plan.stepName);
      ctx.put("package_index", (long) plan.packageIndex);
      ctx.put("group_index", (long) plan.groupIndex);
      if (plan.populationId != null) {
        ctx.put("population_id", plan.populationId);
      }
      if (plan.pairLeft != null) {
        ctx.put("pair_left", plan.pairLeft);
      }
      if (plan.pairRight != null) {
        ctx.put("pair_right", plan.pairRight);
      }
      if (parent != null) {
        for (Map.Entry<String, Object> entry : parent.entrySet()) {
          ctx.put("parent." + entry.getKey(), entry.getValue());
        }
      }
      if (child != null) {
        for (Map.Entry<String, Object> entry : child.entrySet()) {
          ctx.put("child." + entry.getKey(), entry.getValue());
        }
      }
      ctx.putAll(values);
      return ctx;
    }

    private static Object coerce(String hopType, Object value) {
      if (value == null) {
        return null;
      }
      String type = blank(hopType, "String");
      return switch (type) {
        case "Integer" -> toLong(value);
        case "Number" -> toDouble(value);
        case "Boolean" ->
            value instanceof Boolean
                ? value
                : "Y".equalsIgnoreCase(String.valueOf(value))
                    || "true".equalsIgnoreCase(String.valueOf(value));
        default -> value instanceof String ? value : TemplateRenderer.stringify(value);
      };
    }

    private static long toLong(Object value) {
      if (value instanceof Number number) {
        return number.longValue();
      }
      String text = String.valueOf(value).trim();
      if (text.isEmpty()) {
        return 0L;
      }
      return new BigDecimal(text).setScale(0, RoundingMode.DOWN).longValue();
    }

    private static double toDouble(Object value) {
      if (value instanceof Number number) {
        return number.doubleValue();
      }
      return Double.parseDouble(String.valueOf(value).trim());
    }

    private static boolean same(Object left, Object right) {
      return TemplateRenderer.stringify(left).equals(TemplateRenderer.stringify(right));
    }
  }

  private static final class Plan {
    private final int rowIndex;
    private final Long populationId;
    private final int parentIndex;
    private final int childIndex;
    private final String stepName;
    private final int stepIndex;
    private final int packageIndex;
    private final int groupIndex;
    private final Map<String, Object> child;
    private final List<Map<String, Object>> packageChildren;
    private final Object pairLeft;
    private final Object pairRight;

    private Plan(
        int rowIndex,
        Long populationId,
        int parentIndex,
        int childIndex,
        String stepName,
        int stepIndex,
        int packageIndex,
        int groupIndex,
        Map<String, Object> child,
        List<Map<String, Object>> packageChildren,
        Object pairLeft,
        Object pairRight) {
      this.rowIndex = rowIndex;
      this.populationId = populationId;
      this.parentIndex = parentIndex;
      this.childIndex = childIndex;
      this.stepName = stepName;
      this.stepIndex = stepIndex;
      this.packageIndex = packageIndex;
      this.groupIndex = groupIndex;
      this.child = child;
      this.packageChildren = packageChildren;
      this.pairLeft = pairLeft;
      this.pairRight = pairRight;
    }

    private static Plan count(int rowIndex) {
      return new Plan(rowIndex, null, -1, 0, null, 0, 0, 0, null, null, null, null);
    }

    private static Plan population(int rowIndex, long id) {
      return new Plan(rowIndex, id, -1, 0, null, 0, 0, 0, null, null, null, null);
    }

    private static Plan parent(int rowIndex, int parentIndex, int childIndex) {
      return new Plan(rowIndex, null, parentIndex, childIndex, null, 0, 0, 0, null, null, null, null);
    }

    private static Plan path(int rowIndex, int parentIndex, String stepName, int stepIndex) {
      return new Plan(
          rowIndex, null, parentIndex, 0, stepName, stepIndex, 0, 0, null, null, null, null);
    }

    private static Plan hierarchy(
        int rowIndex,
        int parentIndex,
        int childIndex,
        int packageIndex,
        int groupIndex,
        Map<String, Object> child,
        List<Map<String, Object>> packageChildren) {
      return new Plan(
          rowIndex,
          null,
          parentIndex,
          childIndex,
          null,
          0,
          packageIndex,
          groupIndex,
          child,
          packageChildren,
          null,
          null);
    }

    private static Plan pair(int rowIndex, Object left, Object right) {
      return new Plan(rowIndex, null, -1, 0, null, 0, 0, 0, null, null, left, right);
    }
  }
}
