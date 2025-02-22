/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */


package org.opensearch.sql.opensearch.request;

import static org.opensearch.search.sort.FieldSortBuilder.DOC_FIELD_NAME;
import static org.opensearch.search.sort.SortOrder.ASC;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.sort.SortBuilder;
import org.opensearch.sql.common.setting.Settings;
import org.opensearch.sql.common.utils.StringUtils;
import org.opensearch.sql.data.type.ExprType;
import org.opensearch.sql.expression.ReferenceExpression;
import org.opensearch.sql.opensearch.data.value.OpenSearchExprValueFactory;
import org.opensearch.sql.opensearch.response.agg.OpenSearchAggregationResponseParser;

/**
 * OpenSearch search request builder.
 */
@EqualsAndHashCode
@Getter
@ToString
public class OpenSearchRequestBuilder {

  /**
   * Default query timeout in minutes.
   */
  public static final TimeValue DEFAULT_QUERY_TIMEOUT = TimeValue.timeValueMinutes(1L);

  /**
   * {@link OpenSearchRequest.IndexName}.
   */
  private final OpenSearchRequest.IndexName indexName;

  /**
   * Index max result window.
   */
  private final Integer maxResultWindow;

  /**
   * Search request source builder.
   */
  private final SearchSourceBuilder sourceBuilder;

  /**
   * OpenSearchExprValueFactory.
   */
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private final OpenSearchExprValueFactory exprValueFactory;

  /**
   * Query size of the request.
   */
  private Integer querySize;

  public OpenSearchRequestBuilder(String indexName,
                                  Integer maxResultWindow,
                                  Settings settings,
                                  OpenSearchExprValueFactory exprValueFactory) {
    this(new OpenSearchRequest.IndexName(indexName), maxResultWindow, settings, exprValueFactory);
  }

  /**
   * Constructor.
   */
  public OpenSearchRequestBuilder(OpenSearchRequest.IndexName indexName,
                                  Integer maxResultWindow,
                                  Settings settings,
                                  OpenSearchExprValueFactory exprValueFactory) {
    this.indexName = indexName;
    this.maxResultWindow = maxResultWindow;
    this.sourceBuilder = new SearchSourceBuilder();
    this.exprValueFactory = exprValueFactory;
    this.querySize = settings.getSettingValue(Settings.Key.QUERY_SIZE_LIMIT);
    sourceBuilder.from(0);
    sourceBuilder.size(querySize);
    sourceBuilder.timeout(DEFAULT_QUERY_TIMEOUT);
  }

  /**
   * Build DSL request.
   *
   * @return query request or scroll request
   */
  public OpenSearchRequest build() {
    Integer from = sourceBuilder.from();
    Integer size = sourceBuilder.size();

    if (from + size <= maxResultWindow) {
      return new OpenSearchQueryRequest(indexName, sourceBuilder, exprValueFactory);
    } else {
      sourceBuilder.size(maxResultWindow - from);
      return new OpenSearchScrollRequest(indexName, sourceBuilder, exprValueFactory);
    }
  }

  /**
   * Push down query to DSL request.
   *
   * @param query  query request
   */
  public void pushDown(QueryBuilder query) {
    QueryBuilder current = sourceBuilder.query();

    if (current == null) {
      sourceBuilder.query(query);
    } else {
      if (isBoolFilterQuery(current)) {
        ((BoolQueryBuilder) current).filter(query);
      } else {
        sourceBuilder.query(QueryBuilders.boolQuery()
            .filter(current)
            .filter(query));
      }
    }

    if (sourceBuilder.sorts() == null) {
      sourceBuilder.sort(DOC_FIELD_NAME, ASC); // Make sure consistent order
    }
  }

  /**
   * Push down aggregation to DSL request.
   *
   * @param aggregationBuilder pair of aggregation query and aggregation parser.
   */
  public void pushDownAggregation(
      Pair<List<AggregationBuilder>, OpenSearchAggregationResponseParser> aggregationBuilder) {
    aggregationBuilder.getLeft().forEach(builder -> sourceBuilder.aggregation(builder));
    sourceBuilder.size(0);
    exprValueFactory.setParser(aggregationBuilder.getRight());
  }

  /**
   * Push down sort to DSL request.
   *
   * @param sortBuilders sortBuilders.
   */
  public void pushDownSort(List<SortBuilder<?>> sortBuilders) {
    for (SortBuilder<?> sortBuilder : sortBuilders) {
      sourceBuilder.sort(sortBuilder);
    }
  }

  /**
   * Push down size (limit) and from (offset) to DSL request.
   */
  public void pushDownLimit(Integer limit, Integer offset) {
    querySize = limit;
    sourceBuilder.from(offset).size(limit);
  }

  /**
   * Add highlight to DSL requests.
   * @param field name of the field to highlight
   */
  public void pushDownHighlight(String field) {
    if (sourceBuilder.highlighter() != null) {
      sourceBuilder.highlighter().field(StringUtils.unquoteText(field));
    } else {
      HighlightBuilder highlightBuilder =
          new HighlightBuilder().field(StringUtils.unquoteText(field));
      sourceBuilder.highlighter(highlightBuilder);
    }
  }

  /**
   * Push down project list to DSL requets.
   */
  public void pushDownProjects(Set<ReferenceExpression> projects) {
    final Set<String> projectsSet =
        projects.stream().map(ReferenceExpression::getAttr).collect(Collectors.toSet());
    sourceBuilder.fetchSource(projectsSet.toArray(new String[0]), new String[0]);
  }

  public void pushTypeMapping(Map<String, ExprType> typeMapping) {
    exprValueFactory.setTypeMapping(typeMapping);
  }

  private boolean isBoolFilterQuery(QueryBuilder current) {
    return (current instanceof BoolQueryBuilder);
  }
}
