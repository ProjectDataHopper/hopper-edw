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
package org.hopper.edw.datavault.presentation.fact;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.hopper.core.AggregationMethod;
import org.hopper.core.Constants;
import org.hopper.core.HColumn;
import org.hopper.core.HDatabaseConnection;
import org.hopper.core.HDimension;
import org.hopper.core.HFact;
import org.hopper.core.HFont;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HSortMethod;
import org.hopper.core.HVerticalAlignment;
import org.hopper.core.exception.HException;
import org.hopper.edw.datavault.metadata.dimensional.DimensionalModel;
import org.hopper.edw.datavault.metadata.dimensional.DmTargetDatabaseSupport;
import org.hopper.edw.datavault.metadata.dimensional.IDmFactLikeTable;
import org.hopper.edw.datavault.presentation.fact.FactCrosstabQuery.SelectedColumn;
import org.hopper.edw.semantic.model.SemanticModel;
import org.hopper.edw.semantic.query.SemanticSelectionAdapter;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.composite.HCompositeComponent;
import org.hopper.presentation.component.types.crosstab.HCrosstabComponent;
import org.hopper.presentation.component.types.group.HGroupComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.sql.HSqlConnector;
import org.hopper.presentation.layout.HLayoutBuilder;
import org.hopper.presentation.layout.HLayoutMode;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.simple.HGeneratedCatalog;
import org.hopper.presentation.simple.HGeneratedThemes;
import org.hopper.presentation.theme.HTheme;

/** Builds an isolated Hopper presentation catalog for a fact-table crosstab. */
public final class FactCrosstabPresentationBuilder {

  public static final String CONNECTOR_NAME = "fact-query";
  public static final String COMPONENT_TITLE = "Title";
  public static final String COMPONENT_CROSSTAB = "Crosstab";
  public static final String COMPONENT_COMPOSITE = "Composite";
  public static final String COMPONENT_GROUP = "Group";
  public static final String COMPONENT_GROUP_LABEL = "GroupLabel";

  private FactCrosstabPresentationBuilder() {}

  public static HGeneratedCatalog build(
      DimensionalModel model,
      IDmFactLikeTable fact,
      FactCrosstabSpec spec,
      FactCrosstabSourceModel sourceModel,
      IVariables variables,
      IHopMetadataProvider hopMetadata)
      throws HopException, HException {
    return build(model, fact, spec, sourceModel, variables, hopMetadata, null);
  }

  public static HGeneratedCatalog build(
      DimensionalModel model,
      IDmFactLikeTable fact,
      FactCrosstabSpec spec,
      FactCrosstabSourceModel sourceModel,
      IVariables variables,
      IHopMetadataProvider hopMetadata,
      SemanticModel semanticModel)
      throws HopException, HException {
    if (spec == null || spec.isEmpty()) {
      throw new HopException("Select at least one column for the crosstab");
    }
    DatabaseMeta target =
        DmTargetDatabaseSupport.loadTargetDatabase(
            hopMetadata, model != null ? model.getConfigurationOrDefault() : null);
    if (target == null) {
      throw new HopException(
          "Set a target database on the dimensional configuration before creating a presentation");
    }
    FactCrosstabQuery query = FactCrosstabSqlBuilder.build(spec, sourceModel, target, variables);
    HDatabaseConnection connection =
        FactCrosstabConnectionSupport.fromDatabaseMeta(target, variables);

    MemoryMetadataProvider provider = new MemoryMetadataProvider();
    ensureThemes(provider);
    provider.getSerializer(HDatabaseConnection.class).save(connection);
    HConnector connector =
        new HConnector(
            CONNECTOR_NAME,
            new HSqlConnector(FactCrosstabConnectionSupport.CONNECTION_NAME, query.getSql()));
    provider.getSerializer(HConnector.class).save(connector);

    String title = presentationTitle(fact);
    HPresentation presentation = new HPresentation();
    presentation.setName(title);
    presentation.setDescription("Generated fact crosstab for " + Const.NVL(fact.getName(), "fact"));
    presentation.setDefaultThemeName(Constants.GENERATED_THEME_NAME);
    presentation.setDarkThemeName(Constants.GENERATED_DARK_THEME_NAME);
    presentation.setLayoutMode(HLayoutMode.PAGINATED.wireValue());

    HPage page = HPage.getA4(false);
    presentation.getPages().add(page);

    HCrosstabComponent crosstab = createCrosstab(spec, query, semanticModel);
    if (spec.getGroups().isEmpty()) {
      HLabelComponent label = new HLabelComponent(title);
      label.setDefaultFont(new HFont("Arial", "18", true, false));
      HComponent titleComponent = new HComponent(COMPONENT_TITLE, label);
      titleComponent.setLayout(new HLayoutBuilder().left().top().right().build());
      page.getComponents().add(titleComponent);

      HComponent crosstabComponent = new HComponent(COMPONENT_CROSSTAB, crosstab);
      crosstabComponent.setLayout(
          new HLayoutBuilder().left().right().below(COMPONENT_TITLE, 8).build());
      page.getComponents().add(crosstabComponent);
    } else {
      HCompositeComponent composite = new HCompositeComponent();
      HLabelComponent groupLabel = new HLabelComponent(groupLabelText(query, spec));
      groupLabel.setDefaultFont(new HFont("Arial", "16", true, false));
      HComponent labelComponent = new HComponent(COMPONENT_GROUP_LABEL, groupLabel);
      labelComponent.setLayout(new HLayoutBuilder().left().top().right().build());
      composite.getChildren().add(labelComponent);

      HComponent crosstabComponent = new HComponent(COMPONENT_CROSSTAB, crosstab);
      crosstabComponent.setLayout(
          new HLayoutBuilder().left().right().below(COMPONENT_GROUP_LABEL, 8).build());
      composite.getChildren().add(crosstabComponent);

      HComponent compositeComponent = new HComponent(COMPONENT_COMPOSITE, composite);
      compositeComponent.setLayout(new HLayoutBuilder().left().top().build());

      List<HColumn> groupColumns = new ArrayList<>();
      List<HSortMethod> sorts = new ArrayList<>();
      for (SelectedColumn column : query.matching(spec.getGroups())) {
        groupColumns.add(
            new HColumn(
                column.getResultAlias(),
                headerOf(column),
                HHorizontalAlignment.LEFT,
                HVerticalAlignment.TOP));
        sorts.add(new HSortMethod(HSortMethod.Type.NATIVE_VALUE, true));
      }
      HGroupComponent group =
          new HGroupComponent(CONNECTOR_NAME, groupColumns, sorts, true, compositeComponent, 12);
      HComponent groupComponent = new HComponent(COMPONENT_GROUP, group);
      groupComponent.setLayout(new HLayoutBuilder().left().top().right().build());
      page.getComponents().add(groupComponent);
    }

    provider.getSerializer(HPresentation.class).save(presentation);
    return new HGeneratedCatalog(provider, presentation);
  }

  static HCrosstabComponent createCrosstab(FactCrosstabSpec spec, FactCrosstabQuery query) {
    return createCrosstab(spec, query, null);
  }

  static HCrosstabComponent createCrosstab(
      FactCrosstabSpec spec, FactCrosstabQuery query, SemanticModel semanticModel) {
    HCrosstabComponent crosstab = new HCrosstabComponent(CONNECTOR_NAME);
    crosstab.setHorizontalDimensions(toDimensions(query.matching(spec.getHorizontalDimensions())));
    crosstab.setVerticalDimensions(toDimensions(query.matching(spec.getVerticalDimensions())));
    List<HFact> facts = new ArrayList<>();
    for (SelectedColumn column : query.matching(spec.getFacts())) {
      AggregationMethod method =
          column.getField().getAggregation() != null
              ? column.getField().getAggregation()
              : AggregationMethod.SUM;
      String mask = SemanticSelectionAdapter.formatMask(semanticModel, column.getField());
      if (Utils.isEmpty(mask)) {
        mask = method == AggregationMethod.COUNT ? "0" : null;
      }
      HFact fact =
          new HFact(
              column.getResultAlias(),
              headerOf(column),
              HHorizontalAlignment.RIGHT,
              HVerticalAlignment.MIDDLE,
              method,
              mask);
      if (spec.isShowingHorizontalTotals()) {
        fact.setHorizontalAggregation(true);
        fact.setHorizontalAggregationHeader("Total");
      }
      if (spec.isShowingVerticalTotals()) {
        fact.setVerticalAggregation(true);
        fact.setVerticalAggregationHeader("Total");
      }
      fact.setHeaderHorizontalAlignment(HHorizontalAlignment.CENTER);
      fact.setHeaderVerticalAlignment(HVerticalAlignment.MIDDLE);
      facts.add(fact);
    }
    crosstab.setFacts(facts);
    crosstab.setShowingHorizontalTotals(spec.isShowingHorizontalTotals());
    crosstab.setShowingVerticalTotals(spec.isShowingVerticalTotals());
    crosstab.setBackground(true);
    crosstab.setBorder(false);
    crosstab.setHorizontalMargin(3);
    crosstab.setVerticalMargin(2);
    crosstab.setEvenHeights(true);
    crosstab.setHeaderOnEveryPage(true);
    return crosstab;
  }

  private static List<HDimension> toDimensions(List<SelectedColumn> columns) {
    List<HDimension> dimensions = new ArrayList<>();
    for (SelectedColumn column : columns) {
      dimensions.add(
          new HDimension(
              column.getResultAlias(),
              headerOf(column),
              HHorizontalAlignment.CENTER,
              HVerticalAlignment.MIDDLE));
    }
    return dimensions;
  }

  private static String headerOf(SelectedColumn column) {
    String header = column.getField().getHeader();
    if (StringUtils.isNotBlank(header)) {
      return header;
    }
    return FactCrosstabLabels.humanize(column.getField().getColumnName());
  }

  private static String groupLabelText(FactCrosstabQuery query, FactCrosstabSpec spec) {
    List<String> parts = new ArrayList<>();
    for (SelectedColumn column : query.matching(spec.getGroups())) {
      parts.add(headerOf(column) + ": ${" + column.getResultAlias() + "}");
    }
    return String.join("   ", parts);
  }

  private static String presentationTitle(IDmFactLikeTable fact) {
    if (fact == null) {
      return "Fact crosstab";
    }
    String name = Const.NVL(fact.getName(), "Fact");
    if (!Utils.isEmpty(fact.getGrain())) {
      return name + " — " + fact.getGrain();
    }
    return name;
  }

  private static void ensureThemes(IHopMetadataProvider provider) throws HopException {
    var themes = provider.getSerializer(HTheme.class);
    if (themes.load(Constants.DEFAULT_THEME_NAME) == null) {
      themes.save(HTheme.getDefault());
    }
    if (themes.load(Constants.DEFAULT_DARK_THEME_NAME) == null) {
      themes.save(HTheme.getDefaultDark());
    }
    if (themes.load(Constants.GENERATED_THEME_NAME) == null) {
      themes.save(HGeneratedThemes.light());
    }
    if (themes.load(Constants.GENERATED_DARK_THEME_NAME) == null) {
      themes.save(HGeneratedThemes.dark());
    }
  }
}
