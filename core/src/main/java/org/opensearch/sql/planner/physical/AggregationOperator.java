/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */


package org.opensearch.sql.planner.physical;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.opensearch.sql.data.model.ExprValue;
import org.opensearch.sql.expression.Expression;
import org.opensearch.sql.expression.NamedExpression;
import org.opensearch.sql.expression.aggregation.Aggregator;
import org.opensearch.sql.expression.aggregation.NamedAggregator;
import org.opensearch.sql.expression.span.SpanExpression;
import org.opensearch.sql.planner.physical.collector.Collector;
import org.opensearch.sql.storage.bindingtuple.BindingTuple;

/**
 * Group the all the input {@link BindingTuple} by {@link AggregationOperator#groupByExprList},
 * calculate the aggregation result by using {@link AggregationOperator#aggregatorList}.
 */
@EqualsAndHashCode
@ToString
public class AggregationOperator extends PhysicalPlan {
  @Getter
  private final PhysicalPlan input;
  @Getter
  private final List<NamedAggregator> aggregatorList;
  @Getter
  private final List<NamedExpression> groupByExprList;
  @Getter
  private final NamedExpression span;
  /**
   * {@link BindingTuple} Collector.
   */
  @EqualsAndHashCode.Exclude
  private final Collector collector;
  @EqualsAndHashCode.Exclude
  private Iterator<ExprValue> iterator;

  /**
   * AggregationOperator Constructor.
   *
   * @param input           Input {@link PhysicalPlan}
   * @param aggregatorList  List of {@link Aggregator}
   * @param groupByExprList List of group by {@link Expression}
   */
  public AggregationOperator(PhysicalPlan input, List<NamedAggregator> aggregatorList,
                             List<NamedExpression> groupByExprList) {
    this.input = input;
    this.aggregatorList = aggregatorList;
    this.groupByExprList = groupByExprList;
    if (hasSpan(groupByExprList)) {
      // span expression is always the first expression in group list if exist.
      this.span = groupByExprList.get(0);
      this.collector =
          Collector.Builder.build(
              this.span, groupByExprList.subList(1, groupByExprList.size()), this.aggregatorList);

    } else {
      this.span = null;
      this.collector =
          Collector.Builder.build(this.span, this.groupByExprList, this.aggregatorList);
    }
  }

  @Override
  public <R, C> R accept(PhysicalPlanNodeVisitor<R, C> visitor, C context) {
    return visitor.visitAggregation(this, context);
  }

  @Override
  public List<PhysicalPlan> getChild() {
    return Collections.singletonList(input);
  }


  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }

  @Override
  public ExprValue next() {
    return iterator.next();
  }

  @Override
  public void open() {
    super.open();
    while (input.hasNext()) {
      collector.collect(input.next().bindingTuples());
    }
    iterator = collector.results().iterator();
  }

  private boolean hasSpan(List<NamedExpression> namedExpressionList) {
    return !namedExpressionList.isEmpty()
        && namedExpressionList.get(0).getDelegated() instanceof SpanExpression;
  }
}
