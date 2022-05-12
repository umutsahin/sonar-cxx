/*
 * C++ Community Plugin (cxx plugin)
 * Copyright (C) 2021 SonarOpenCommunity
 * http://github.com/SonarOpenCommunity/sonar-cxx
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
/**
 * fork of SSLR Squid Bridge: https://github.com/SonarSource/sslr-squid-bridge/tree/2.6.1
 * Copyright (C) 2010 SonarSource / mailto: sonarqube@googlegroups.com / license: LGPL v3
 */
package org.sonar.cxx.squidbridge.test.miniC;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.AstNodeType;
import com.sonar.sslr.api.Grammar;
import com.sonar.sslr.impl.Parser;
import com.sonar.sslr.test.minic.MiniCGrammar;
import com.sonar.sslr.test.minic.MiniCParser;
import org.sonar.cxx.squidbridge.AstScanner;
import org.sonar.cxx.squidbridge.CommentAnalyser;
import org.sonar.cxx.squidbridge.SourceCodeBuilderCallback;
import org.sonar.cxx.squidbridge.SourceCodeBuilderVisitor;
import org.sonar.cxx.squidbridge.SquidAstVisitor;
import org.sonar.cxx.squidbridge.SquidAstVisitorContextImpl;
import org.sonar.cxx.squidbridge.api.SourceCode;
import org.sonar.cxx.squidbridge.api.SourceFunction;
import org.sonar.cxx.squidbridge.api.SourceProject;
import org.sonar.cxx.squidbridge.measures.AggregationFormula;
import org.sonar.cxx.squidbridge.measures.CalculatedMetricFormula;
import org.sonar.cxx.squidbridge.measures.MetricDef;
import org.sonar.cxx.squidbridge.measures.SumAggregationFormula;
import org.sonar.cxx.squidbridge.metrics.CommentsVisitor;
import org.sonar.cxx.squidbridge.metrics.ComplexityVisitor;
import org.sonar.cxx.squidbridge.metrics.CounterVisitor;
import org.sonar.cxx.squidbridge.metrics.LinesOfCodeVisitor;
import org.sonar.cxx.squidbridge.metrics.LinesVisitor;

public final class MiniCAstScanner {

  public static enum MiniCMetrics implements MetricDef {
    FILES, STATEMENTS, COMPLEXITY, LINES, LINES_OF_CODE, COMMENT_LINES, BLANK_COMMENT_LINES, FUNCTIONS;

    public double getInitValue() {
      return 0;
    }

    @Override
    public String getName() {
      return name();
    }

    @Override
    public boolean isCalculatedMetric() {
      return false;
    }

    @Override
    public boolean aggregateIfThereIsAlreadyAValue() {
      return true;
    }

    @Override
    public boolean isThereAggregationFormula() {
      return true;
    }

    @Override
    public CalculatedMetricFormula getCalculatedMetricFormula() {
      return null;
    }

    public AggregationFormula getAggregationFormula() {
      return new SumAggregationFormula();
    }

  }

  private MiniCAstScanner() {
  }

  public static AstScanner<Grammar> create(SquidAstVisitor<Grammar>... visitors) {
    return create(false, visitors);
  }

  public static AstScanner<Grammar> createIgnoreHeaderComments(SquidAstVisitor<Grammar>... visitors) {
    return create(true, visitors);
  }

  private static AstScanner<Grammar> create(boolean ignoreHeaderComments, SquidAstVisitor<Grammar>... visitors) {

    final SquidAstVisitorContextImpl<Grammar> context = new SquidAstVisitorContextImpl<Grammar>(
      new SourceProject("MiniC Project"));
    final Parser<Grammar> parser = MiniCParser.create();

    AstScanner.Builder<Grammar> builder = AstScanner.<Grammar>builder(context).setBaseParser(parser);

    /* Metrics */
    builder.withMetrics(MiniCMetrics.values());

    /* Comments */
    builder.setCommentAnalyser(
      new CommentAnalyser() {

      @Override
      public boolean isBlank(String commentLine) {
        for (int i = 0; i < commentLine.length(); i++) {
          if (Character.isLetterOrDigit(commentLine.charAt(i))) {
            return false;
          }
        }

        return true;
      }

      @Override
      public String getContents(String comment) {
        return comment.substring(2, comment.length() - 2);
      }

    }
    );

    /* Files */
    builder.setFilesMetric(MiniCMetrics.FILES);

    /* Functions */
    builder.withSquidAstVisitor(new SourceCodeBuilderVisitor<Grammar>(new SourceCodeBuilderCallback() {

      @Override
      public SourceCode createSourceCode(SourceCode parentSourceCode, AstNode astNode) {
        String functionName = astNode.findFirstChild(MiniCGrammar.BIN_FUNCTION_DEFINITION).getTokenValue();

        SourceFunction function = new SourceFunction(astNode.getFromIndex() + "@" + functionName);
        function.setStartAtLine(astNode.getTokenLine());

        return function;
      }
    }, MiniCGrammar.FUNCTION_DEFINITION));

    builder.withSquidAstVisitor(CounterVisitor.<Grammar>builder().setMetricDef(MiniCMetrics.FUNCTIONS)
      .subscribeTo(MiniCGrammar.FUNCTION_DEFINITION).build());

    /* Metrics */
    builder.withSquidAstVisitor(new LinesVisitor<Grammar>(MiniCMetrics.LINES));
    builder.withSquidAstVisitor(new LinesOfCodeVisitor<Grammar>(MiniCMetrics.LINES_OF_CODE));
    builder.withSquidAstVisitor(CommentsVisitor.<Grammar>builder().withCommentMetric(MiniCMetrics.COMMENT_LINES)
      .withNoSonar(true)
      .withIgnoreHeaderComment(ignoreHeaderComments)
      .build());
    builder.withSquidAstVisitor(CounterVisitor.<Grammar>builder().setMetricDef(MiniCMetrics.STATEMENTS)
      .subscribeTo(MiniCGrammar.STATEMENT).build());

    AstNodeType[] complexityAstNodeType = new AstNodeType[]{
      MiniCGrammar.FUNCTION_DEFINITION,
      MiniCGrammar.RETURN_STATEMENT,
      MiniCGrammar.IF_STATEMENT,
      MiniCGrammar.WHILE_STATEMENT,
      MiniCGrammar.CONTINUE_STATEMENT,
      MiniCGrammar.BREAK_STATEMENT
    };
    builder.withSquidAstVisitor(ComplexityVisitor.<Grammar>builder().setMetricDef(MiniCMetrics.COMPLEXITY)
      .subscribeTo(complexityAstNodeType).addExclusions(MiniCGrammar.NO_COMPLEXITY_STATEMENT).build());

    /* External visitors (typically Check ones) */
    for (SquidAstVisitor<Grammar> visitor : visitors) {
      builder.withSquidAstVisitor(visitor);
    }

    return builder.build();
  }

}
