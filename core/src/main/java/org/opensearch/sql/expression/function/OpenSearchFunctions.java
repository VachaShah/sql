/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.expression.function;

import static org.opensearch.sql.data.type.ExprCoreType.STRING;
import static org.opensearch.sql.data.type.ExprCoreType.STRUCT;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.opensearch.sql.data.model.ExprValue;
import org.opensearch.sql.data.type.ExprCoreType;
import org.opensearch.sql.data.type.ExprType;
import org.opensearch.sql.expression.Expression;
import org.opensearch.sql.expression.FunctionExpression;
import org.opensearch.sql.expression.HighlightExpression;
import org.opensearch.sql.expression.NamedArgumentExpression;
import org.opensearch.sql.expression.env.Environment;

@UtilityClass
public class OpenSearchFunctions {
  /**
   * Add functions specific to OpenSearch to repository.
   */
  public void register(BuiltinFunctionRepository repository) {
    repository.register(match_bool_prefix());
    repository.register(match());
    repository.register(multi_match());
    repository.register(simple_query_string());
    repository.register(query_string());
    // Register MATCHPHRASE as MATCH_PHRASE as well for backwards
    // compatibility.
    repository.register(match_phrase(BuiltinFunctionName.MATCH_PHRASE));
    repository.register(match_phrase(BuiltinFunctionName.MATCHPHRASE));
    repository.register(match_phrase_prefix());
    repository.register(highlight());
  }

  private static FunctionResolver highlight() {
    FunctionName functionName = BuiltinFunctionName.HIGHLIGHT.getName();
    FunctionSignature functionSignature = new FunctionSignature(functionName, List.of(STRING));
    FunctionBuilder functionBuilder = arguments -> new HighlightExpression(arguments.get(0));
    return new DefaultFunctionResolver(functionName,
        ImmutableMap.of(functionSignature, functionBuilder));
  }

  private static FunctionResolver match_bool_prefix() {
    FunctionName name = BuiltinFunctionName.MATCH_BOOL_PREFIX.getName();
    return new RelevanceFunctionResolver(name, STRING);
  }

  private static FunctionResolver match() {
    FunctionName funcName = BuiltinFunctionName.MATCH.getName();
    return new RelevanceFunctionResolver(funcName, STRING);
  }

  private static FunctionResolver match_phrase_prefix() {
    FunctionName funcName = BuiltinFunctionName.MATCH_PHRASE_PREFIX.getName();
    return new RelevanceFunctionResolver(funcName, STRING);
  }

  private static FunctionResolver match_phrase(BuiltinFunctionName matchPhrase) {
    FunctionName funcName = matchPhrase.getName();
    return new RelevanceFunctionResolver(funcName, STRING);
  }

  private static FunctionResolver multi_match() {
    FunctionName funcName = BuiltinFunctionName.MULTI_MATCH.getName();
    return new RelevanceFunctionResolver(funcName, STRUCT);
  }

  private static FunctionResolver simple_query_string() {
    FunctionName funcName = BuiltinFunctionName.SIMPLE_QUERY_STRING.getName();
    return new RelevanceFunctionResolver(funcName, STRUCT);
  }

  private static FunctionResolver query_string() {
    FunctionName funcName = BuiltinFunctionName.QUERY_STRING.getName();
    return new RelevanceFunctionResolver(funcName, STRUCT);
  }

  public static class OpenSearchFunction extends FunctionExpression {
    private final FunctionName functionName;
    private final List<Expression> arguments;

    /**
     * Required argument constructor.
     * @param functionName name of the function
     * @param arguments a list of expressions
     */
    public OpenSearchFunction(FunctionName functionName, List<Expression> arguments) {
      super(functionName, arguments);
      this.functionName = functionName;
      this.arguments = arguments;
    }

    @Override
    public ExprValue valueOf(Environment<Expression, ExprValue> valueEnv) {
      throw new UnsupportedOperationException(String.format(
          "OpenSearch defined function [%s] is only supported in WHERE and HAVING clause.",
          functionName));
    }

    @Override
    public ExprType type() {
      return ExprCoreType.BOOLEAN;
    }

    @Override
    public String toString() {
      List<String> args = arguments.stream()
          .map(arg -> String.format("%s=%s", ((NamedArgumentExpression) arg)
              .getArgName(), ((NamedArgumentExpression) arg).getValue().toString()))
          .collect(Collectors.toList());
      return String.format("%s(%s)", functionName, String.join(", ", args));
    }
  }
}
