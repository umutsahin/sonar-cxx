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
package org.sonar.plugins.cxx;

import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.cxx.checks.CheckList;
import org.sonar.cxx.squidbridge.annotations.AnnotationBasedRulesDefinition;

public class CxxRuleRepository implements RulesDefinition {

  private static final String REPOSITORY_NAME = "SonarQube";

  @Override
  public void define(Context context) {
    NewRepository repository = context.createRepository("cxx", CxxLanguage.KEY).
      setName(REPOSITORY_NAME);
    new AnnotationBasedRulesDefinition(repository, CxxLanguage.KEY).addRuleClasses(false, CheckList.getChecks());
    repository.done();
  }

}
