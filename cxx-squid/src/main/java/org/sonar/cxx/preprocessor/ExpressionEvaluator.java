/*
 * C++ Community Plugin (cxx plugin)
 * Copyright (C) 2010-2022 SonarOpenCommunity
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
package org.sonar.cxx.preprocessor;

import com.sonar.cxx.sslr.api.AstNode;
import com.sonar.cxx.sslr.api.GenericTokenType;
import com.sonar.cxx.sslr.api.Grammar;
import com.sonar.cxx.sslr.api.Token;
import com.sonar.cxx.sslr.impl.Parser;
import java.math.BigInteger;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.cxx.parser.CxxTokenType;

public final class ExpressionEvaluator {

  private static final BigInteger UINT64_MAX = new BigInteger("FFFFFFFFFFFFFFFF", 16);
  private static final Logger LOG = Loggers.get(ExpressionEvaluator.class);
  private final Parser<Grammar> parser;
  private final CxxPreprocessor preprocessor;
  private final Deque<String> macroEvaluationStack;

  private ExpressionEvaluator(CxxPreprocessor preprocessor) {
    parser = CppParser.createConstantExpressionParser(preprocessor.getCharset());

    this.preprocessor = preprocessor;
    macroEvaluationStack = new LinkedList<>();
  }

  public static boolean eval(CxxPreprocessor preprocessor, String constExpr) {
    return new ExpressionEvaluator(preprocessor).evalToBoolean(constExpr, null);
  }

  public static boolean eval(CxxPreprocessor preprocessor, AstNode constExpr) {
    return new ExpressionEvaluator(preprocessor).evalToBoolean(constExpr);
  }

  public static BigInteger decode(String number) {

    // This function is only responsible for providing a string and a radix to BigInteger.
    // The lexer ensures that the number has a valid format.
    var radix = 10;
    var begin = 0;
    if (number.length() > 2) {
      if (number.charAt(0) == '0') {
        switch (number.charAt(1)) {
          case 'x':
          case 'X':
            radix = 16; // 0x...
            begin = 2;
            break;
          case 'b':
          case 'B':
            radix = 2; // 0b...
            begin = 2;
            break;
          default:
            radix = 8; // 0...
            break;
        }
      }
    }

    var sb = new StringBuilder(number.length());
    var suffix = false;
    for (var index = begin; index < number.length() && !suffix; index++) {
      var c = number.charAt(index);
      switch (c) {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':

        case 'a':
        case 'b':
        case 'c':
        case 'd':
        case 'e':
        case 'f':

        case 'A':
        case 'B':
        case 'C':
        case 'D':
        case 'E':
        case 'F':
          sb.append(c);
          break;

        case '\'': // ignore digit separator
          break;

        default: // suffix
          suffix = true;
          break;
      }
    }

    return new BigInteger(sb.toString(), radix);
  }

  // ///////////////// Primitives //////////////////////
  private static BigInteger evalBool(String boolValue) {
    return "true".equalsIgnoreCase(boolValue) ? BigInteger.ONE : BigInteger.ZERO;
  }

  private static BigInteger evalNumber(String intValue) {
    // the if expressions aren't allowed to contain floats
    BigInteger number;
    try {
      number = decode(intValue);
    } catch (java.lang.NumberFormatException e) {
      LOG
        .warn("preprocessor cannot decode the number '{}' falling back to value '{}' instead", intValue, BigInteger.ONE);
      number = BigInteger.ONE;
    }

    return number;
  }

  private static BigInteger evalCharacter(String charValue) {
    // TODO: replace this simplification by something more sane
    return "'\0'".equals(charValue) ? BigInteger.ZERO : BigInteger.ONE;
  }

  @CheckForNull
  private static AstNode getNextOperand(@Nullable AstNode node) {
    AstNode sibling = node;
    if (sibling != null) {
      sibling = sibling.getNextSibling();
      if (sibling != null) {
        sibling = sibling.getNextSibling();
      }
    }
    return sibling;
  }

  private BigInteger evalToInt(String constExpr, @Nullable AstNode exprAst) {
    AstNode constExprAst;
    try {
      constExprAst = parser.parse(constExpr);
    } catch (com.sonar.cxx.sslr.api.RecognitionException e) {
      if (exprAst != null) {
        LOG.warn("preprocessor error evaluating expression '{}' for token '{}', assuming 0", constExpr, exprAst
                 .getToken());
      } else {
        LOG.warn("preprocessor error evaluating expression '{}', assuming 0", constExpr);
      }
      return BigInteger.ZERO;
    }

    return evalToInt(constExprAst);
  }

  private BigInteger evalToInt(AstNode exprAst) {
    int noChildren = exprAst.getNumberOfChildren();
    if (noChildren == 0) {
      return evalLeaf(exprAst);
    } else if (noChildren == 1) {
      return evalOneChildAst(exprAst);
    }

    return evalComplexAst(exprAst);
  }

  private boolean evalToBoolean(AstNode exprAst) {
    return !BigInteger.ZERO.equals(evalToInt(exprAst));
  }

  private boolean evalToBoolean(String constExpr, @Nullable AstNode exprAst) {
    return !BigInteger.ZERO.equals(evalToInt(constExpr, exprAst));
  }

  private BigInteger evalLeaf(AstNode exprAst) {
    // Evaluation of leafs
    //
    var nodeType = exprAst.getType();

    if (nodeType.equals(CxxTokenType.NUMBER)) {
      return evalNumber(exprAst.getTokenValue());
    } else if (nodeType.equals(CxxTokenType.CHARACTER)) {
      return evalCharacter(exprAst.getTokenValue());
    } else if (nodeType.equals(GenericTokenType.IDENTIFIER)) {

      String id = exprAst.getTokenValue();
      if (macroEvaluationStack.contains(id)) {
        LOG.debug("preprocessor: self-referential macro '{}' detected;"
                    + " assume true; evaluation stack = ['{} <- {}']",
                  id, id, String.join(" <- ", macroEvaluationStack));
        return BigInteger.ONE;
      }
      String value = preprocessor.valueOf(id);
      if (value == null) {
        return BigInteger.ZERO;
      }

      macroEvaluationStack.addFirst(id);
      BigInteger expansion = evalToInt(value, exprAst);
      macroEvaluationStack.removeFirst();
      return expansion;

    } else {
      throw new EvaluationException("Unknown expression type '" + nodeType + "'");
    }
  }

  private BigInteger evalOneChildAst(AstNode exprAst) {
    // Evaluation of booleans and 'pass-through's
    //
    var nodeType = exprAst.getType();
    if (nodeType.equals(CppGrammarImpl.bool)) {
      return evalBool(exprAst.getTokenValue());
    }
    return evalToInt(exprAst.getFirstChild());
  }

  private BigInteger evalComplexAst(AstNode exprAst) {

    // More complex expressions with more than one child
    //
    var nodeType = exprAst.getType();
    if (nodeType.equals(CppGrammarImpl.unaryExpression)) {
      return evalUnaryExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.conditionalExpression)) {
      return evalConditionalExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.logicalOrExpression)) {
      return evalLogicalOrExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.logicalAndExpression)) {
      return evalLogicalAndExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.inclusiveOrExpression)) {
      return evalInclusiveOrExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.exclusiveOrExpression)) {
      return evalExclusiveOrExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.andExpression)) {
      return evalAndExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.equalityExpression)) {
      return evalEqualityExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.relationalExpression)) {
      return evalRelationalExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.shiftExpression)) {
      return evalShiftExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.additiveExpression)) {
      return evalAdditiveExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.multiplicativeExpression)) {
      return evalMultiplicativeExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.primaryExpression)) {
      return evalPrimaryExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.definedExpression)) {
      return evalDefinedExpression(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.functionlikeMacro)) {
      return evalFunctionlikeMacro(exprAst);
    } else if (nodeType.equals(CppGrammarImpl.hasIncludeExpression)) {
      return evalHasIncludeExpression(exprAst);
    } else {
      LOG.error("preprocessor: unknown expression type '" + nodeType + "' for token '"
                  + exprAst.getToken() + "', assuming 0");
      return BigInteger.ZERO;
    }
  }

  // ////////////// logical expressions ///////////////////////////
  private BigInteger evalLogicalOrExpression(AstNode exprAst) {
    var operand = exprAst.getFirstChild();
    boolean result = evalToBoolean(operand);

    while (!result && ((operand = getNextOperand(operand)) != null)) {
      result = evalToBoolean(operand);
    }

    return result ? BigInteger.ONE : BigInteger.ZERO;
  }

  private BigInteger evalLogicalAndExpression(AstNode exprAst) {
    var operand = exprAst.getFirstChild();
    boolean result = evalToBoolean(operand);

    while (result && ((operand = getNextOperand(operand)) != null)) {
      result = evalToBoolean(operand);
    }

    return result ? BigInteger.ONE : BigInteger.ZERO;
  }

  private BigInteger evalEqualityExpression(AstNode exprAst) {
    var lhs = exprAst.getFirstChild();
    var operator = lhs.getNextSibling();
    var rhs = operator.getNextSibling();
    var operatorType = operator.getType();

    boolean result;
    if (operatorType.equals(CppPunctuator.EQ)) {
      result = evalToInt(lhs).compareTo(evalToInt(rhs)) == 0;
    } else if (operatorType.equals(CppPunctuator.NOT_EQ)) {
      result = evalToInt(lhs).compareTo(evalToInt(rhs)) != 0;
    } else {
      throw new EvaluationException("Unknown equality operator '" + operatorType + "'");
    }

    while ((operator = rhs.getNextSibling()) != null) {
      operatorType = operator.getType();
      rhs = operator.getNextSibling();
      if (operatorType.equals(CppPunctuator.EQ)) {
        result = result == evalToBoolean(rhs);
      } else if (operatorType.equals(CppPunctuator.NOT_EQ)) {
        result = result != evalToBoolean(rhs);
      } else {
        throw new EvaluationException("Unknown equality operator '" + operatorType + "'");
      }
    }

    return result ? BigInteger.ONE : BigInteger.ZERO;
  }

  private BigInteger evalRelationalExpression(AstNode exprAst) {
    var lhs = exprAst.getFirstChild();
    var operator = lhs.getNextSibling();
    var rhs = operator.getNextSibling();
    var operatorType = operator.getType();

    boolean result;
    if (operatorType.equals(CppPunctuator.LT)) {
      result = evalToInt(lhs).compareTo(evalToInt(rhs)) < 0;
    } else if (operatorType.equals(CppPunctuator.GT)) {
      result = evalToInt(lhs).compareTo(evalToInt(rhs)) > 0;
    } else if (operatorType.equals(CppPunctuator.LT_EQ)) {
      result = evalToInt(lhs).compareTo(evalToInt(rhs)) <= 0;
    } else if (operatorType.equals(CppPunctuator.GT_EQ)) {
      result = evalToInt(lhs).compareTo(evalToInt(rhs)) >= 0;
    } else {
      throw new EvaluationException("Unknown relational operator '" + operatorType + "'");
    }

    BigInteger resultAsInt;
    while ((operator = rhs.getNextSibling()) != null) {
      operatorType = operator.getType();
      rhs = operator.getNextSibling();

      resultAsInt = result ? BigInteger.ONE : BigInteger.ZERO;
      if (operatorType.equals(CppPunctuator.LT)) {
        result = resultAsInt.compareTo(evalToInt(rhs)) < 0;
      } else if (operatorType.equals(CppPunctuator.GT)) {
        result = resultAsInt.compareTo(evalToInt(rhs)) > 0;
      } else if (operatorType.equals(CppPunctuator.LT_EQ)) {
        result = resultAsInt.compareTo(evalToInt(rhs)) <= 0;
      } else if (operatorType.equals(CppPunctuator.GT_EQ)) {
        result = resultAsInt.compareTo(evalToInt(rhs)) >= 0;
      } else {
        throw new EvaluationException("Unknown relational operator '" + operatorType + "'");
      }
    }

    return result ? BigInteger.ONE : BigInteger.ZERO;
  }

  // ///////////////// bitwise expressions ///////////////////////
  private BigInteger evalAndExpression(AstNode exprAst) {
    var operand = exprAst.getFirstChild();
    BigInteger result = evalToInt(operand);

    while ((operand = getNextOperand(operand)) != null) {
      result = result.and(evalToInt(operand));
    }

    return result;
  }

  private BigInteger evalInclusiveOrExpression(AstNode exprAst) {
    var operand = exprAst.getFirstChild();
    BigInteger result = evalToInt(operand);

    while ((operand = getNextOperand(operand)) != null) {
      result = result.or(evalToInt(operand));
    }

    return result;
  }

  private BigInteger evalExclusiveOrExpression(AstNode exprAst) {
    var operand = exprAst.getFirstChild();
    BigInteger result = evalToInt(operand);

    while ((operand = getNextOperand(operand)) != null) {
      result = result.xor(evalToInt(operand));
    }

    return result;
  }

  // ///////////////// other ... ///////////////////
  private BigInteger evalUnaryExpression(AstNode exprAst) {
    // only 'unary-operator cast-expression' production is allowed in #if-context

    var operator = exprAst.getFirstChild();
    var operand = operator.getNextSibling();
    var operatorType = operator.getFirstChild().getType();

    if (operatorType.equals(CppPunctuator.PLUS)) {
      return evalToInt(operand);
    } else if (operatorType.equals(CppPunctuator.MINUS)) {
      return evalToInt(operand).negate();
    } else if (operatorType.equals(CppPunctuator.NOT)) {
      boolean result = !evalToBoolean(operand);
      return result ? BigInteger.ONE : BigInteger.ZERO;
    } else if (operatorType.equals(CppPunctuator.BW_NOT)) {
      //todo: need more information (signed/unsigned, data type length) to invert bits in all cases correct
      return evalToInt(operand).not().and(UINT64_MAX);
    } else {
      throw new EvaluationException("Unknown unary operator  '" + operatorType + "'");
    }
  }

  private BigInteger evalShiftExpression(AstNode exprAst) {
    var rhs = exprAst.getFirstChild();
    AstNode operator;
    BigInteger result = evalToInt(rhs);

    while ((operator = rhs.getNextSibling()) != null) {
      var operatorType = operator.getType();
      rhs = operator.getNextSibling();

      if (operatorType.equals(CppPunctuator.BW_LSHIFT)) {
        result = result.shiftLeft(evalToInt(rhs).intValue()).and(UINT64_MAX);
      } else if (operatorType.equals(CppPunctuator.BW_RSHIFT)) {
        result = result.shiftRight(evalToInt(rhs).intValue());
      } else {
        throw new EvaluationException("Unknown shift operator '" + operatorType + "'");
      }
    }

    return result;
  }

  private BigInteger evalAdditiveExpression(AstNode exprAst) {
    var rhs = exprAst.getFirstChild();
    AstNode operator;
    BigInteger result = evalToInt(rhs);

    while ((operator = rhs.getNextSibling()) != null) {
      var operatorType = operator.getType();
      rhs = operator.getNextSibling();

      if (operatorType.equals(CppPunctuator.PLUS)) {
        result = result.add(evalToInt(rhs));
      } else if (operatorType.equals(CppPunctuator.MINUS)) {
        result = result.subtract(evalToInt(rhs));
      } else {
        throw new EvaluationException("Unknown additive operator '" + operatorType + "'");
      }
    }

    return result;
  }

  private BigInteger evalMultiplicativeExpression(AstNode exprAst) {
    var rhs = exprAst.getFirstChild();
    AstNode operator;
    BigInteger result = evalToInt(rhs);

    while ((operator = rhs.getNextSibling()) != null) {
      var operatorType = operator.getType();
      rhs = operator.getNextSibling();

      if (operatorType.equals(CppPunctuator.MUL)) {
        result = result.multiply(evalToInt(rhs));
      } else if (operatorType.equals(CppPunctuator.DIV)) {
        result = result.divide(evalToInt(rhs));
      } else if (operatorType.equals(CppPunctuator.MODULO)) {
        result = result.mod(evalToInt(rhs));
      } else {
        throw new EvaluationException("Unknown multiplicative operator '" + operatorType + "'");
      }
    }

    return result;
  }

  private BigInteger evalConditionalExpression(AstNode exprAst) {
    if (exprAst.getNumberOfChildren() == 5) {
      var decisionOperand = exprAst.getFirstChild();
      var operator = decisionOperand.getNextSibling();
      var trueCaseOperand = operator.getNextSibling();
      operator = trueCaseOperand.getNextSibling();
      var falseCaseOperand = operator.getNextSibling();
      return evalToBoolean(decisionOperand) ? evalToInt(trueCaseOperand) : evalToInt(falseCaseOperand);
    } else {
      var decisionOperand = exprAst.getFirstChild();
      var operator = decisionOperand.getNextSibling();
      operator = operator.getNextSibling();
      var falseCaseOperand = operator.getNextSibling();
      BigInteger decision = evalToInt(decisionOperand);
      return decision.compareTo(BigInteger.ZERO) != 0 ? decision : evalToInt(falseCaseOperand);
    }
  }

  private BigInteger evalPrimaryExpression(AstNode exprAst) {
    // case "( expression )"
    var caseNode = exprAst.getFirstChild();
    return evalToInt(caseNode.getNextSibling());
  }

  private BigInteger evalDefinedExpression(AstNode exprAst) {
    var child = exprAst.getFirstChild();

    if (exprAst.getNumberOfChildren() != 2) {
      child = child.getNextSibling();
    }

    String macroName = child.getNextSibling().getTokenValue();
    String value = preprocessor.valueOf(macroName);
    return value == null ? BigInteger.ZERO : BigInteger.ONE;
  }

  private BigInteger evalFunctionlikeMacro(AstNode exprAst) {
    String macroName = exprAst.getFirstChild().getTokenValue();
    List<Token> tokens = exprAst.getTokens();
    List<Token> restTokens = tokens.subList(1, tokens.size());
    String value = preprocessor.expandFunctionLikeMacro(macroName, restTokens);

    if (value == null || "".equals(value)) {
      LOG.error("preprocessor: undefined function-like macro '{}' assuming 0", macroName);
      return BigInteger.ZERO;
    }

    return evalToInt(value, exprAst);
  }

  private BigInteger evalHasIncludeExpression(AstNode exprAst) {
    return preprocessor.expandHasIncludeExpression(exprAst) ? BigInteger.ONE : BigInteger.ZERO;
  }

}
