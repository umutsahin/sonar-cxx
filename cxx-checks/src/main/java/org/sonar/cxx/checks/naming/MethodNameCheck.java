/*
 * C++ Community Plugin (cxx plugin)
 * Copyright (C) 2010-2021 SonarOpenCommunity
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
package org.sonar.cxx.checks.naming;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.GenericTokenType;
import static com.sonar.sslr.api.GenericTokenType.IDENTIFIER;
import com.sonar.sslr.api.Grammar;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.cxx.checks.utils.CheckUtils;
import static org.sonar.cxx.checks.utils.CheckUtils.isFunctionDefinition;
import org.sonar.cxx.parser.CxxGrammarImpl;
import org.sonar.cxx.squidbridge.annotations.ActivatedByDefault;
import org.sonar.cxx.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.cxx.squidbridge.checks.SquidCheck;
import org.sonar.cxx.tag.Tag;

/**
 * MethodNameCheck
 *
 */
@Rule(
  key = "MethodName",
  priority = Priority.MAJOR,
  name = "Method names should comply with a naming convention",
  tags = {Tag.CONVENTION})
@SqaleConstantRemediation("10min")
@ActivatedByDefault
public class MethodNameCheck extends SquidCheck<Grammar> {

  private static final String DEFAULT = "^[A-Z][A-Za-z0-9]{2,30}$";
  /**
   * format
   */
  @RuleProperty(
    key = "format",
    defaultValue = "" + DEFAULT)
  public String format = DEFAULT;
  private Pattern pattern = null;

  @CheckForNull
  private static AstNode getMethodName(AstNode node) {
    AstNode result = null;
    if (isFunctionDefinition(node)) {
      AstNode declId = node.getFirstDescendant(CxxGrammarImpl.declaratorId);
      if (declId != null) {
        // method inside of class
        result = getInsideMemberDeclaration(declId);
        if (result == null) {
          // a nested name - method outside of class
          result = getOutsideMemberDeclaration(declId);
        }
      }
    }
    return result;
  }

  @CheckForNull
  private static AstNode getInsideMemberDeclaration(AstNode declId) {
    AstNode result = null;
    if (declId.hasAncestor(CxxGrammarImpl.memberDeclaration)) {
      AstNode idNode = declId.getLastChild(IDENTIFIER);
      if (idNode != null) {
        AstNode classSpecifier = declId.getFirstAncestor(CxxGrammarImpl.classSpecifier);
        if (classSpecifier != null) {
          AstNode classHeadName = classSpecifier.getFirstDescendant(CxxGrammarImpl.classHeadName);
          if (classHeadName != null) {
            AstNode className = classHeadName.getLastChild(CxxGrammarImpl.className);
            // if class name is equal to method name then it is a ctor or dtor
            if ((className != null) && !className.getTokenValue().equals(idNode.getTokenValue())) {
              result = idNode;
            }
          }
        }
      }
    }
    return result;
  }

  private static Optional<AstNode> getMostNestedTypeName(AstNode nestedNameSpecifier) {
    Optional<AstNode> result = Optional.empty();
    for (AstNode child : nestedNameSpecifier.getChildren()) {
      if ( // type name was recognized by parser (most probably the least nested type)
        child.is(CxxGrammarImpl.typeName)
          || // type name was recognized as template
        child.is(CxxGrammarImpl.simpleTemplateId)
          || // type name was recognized, but not properly typed
        GenericTokenType.IDENTIFIER.equals(child.getToken().getType())) {
        result = Optional.of(child);
      }
    }
    return result;
  }

  @CheckForNull
  private static AstNode getOutsideMemberDeclaration(AstNode declId) {
    AstNode qualifiedId = declId.getFirstDescendant(CxxGrammarImpl.qualifiedId);
    AstNode result = null;
    if (qualifiedId != null) {
      AstNode nestedNameSpecifier = qualifiedId.getFirstDescendant(CxxGrammarImpl.nestedNameSpecifier);
      if (nestedNameSpecifier != null) {
        AstNode idNode = qualifiedId.getLastChild(IDENTIFIER);
        if (idNode != null) {
          Optional<AstNode> typeName = getMostNestedTypeName(nestedNameSpecifier);
          // if class name is equal to method name then it is a ctor or dtor
          if (typeName.isPresent() && !typeName.get().getTokenValue().equals(idNode.getTokenValue())) {
            result = idNode;
          }
        }
      }
    }
    return result;
  }

  @Override
  public void init() {
    pattern = CheckUtils.compileUserRegexp(format);
    subscribeTo(CxxGrammarImpl.functionDefinition);
  }

  @Override
  public void visitNode(AstNode astNode) {
    AstNode idNode = getMethodName(astNode);
    if (idNode != null) {
      String identifier = idNode.getTokenValue();
      if (!pattern.matcher(identifier).matches()) {
        getContext().createLineViolation(this,
                                         "Rename method \"{0}\" to match the regular expression {1}.", idNode,
                                         identifier, format);
      }
    }
  }

}
