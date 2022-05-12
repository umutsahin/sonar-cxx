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
import com.sonar.sslr.api.Grammar;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.cxx.checks.utils.CheckUtils;
import org.sonar.cxx.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.cxx.squidbridge.checks.SquidCheck;
import org.sonar.cxx.tag.Tag;

/**
 * FileNameCheck
 *
 */
@Rule(
  key = "FileName",
  priority = Priority.MINOR,
  name = "File names should comply with a naming convention",
  tags = {Tag.CONVENTION})
@SqaleConstantRemediation("10min")
public class FileNameCheck extends SquidCheck<Grammar> {

  private static final String DEFAULT = "(([a-z_][a-z0-9_]*)|([A-Z][a-zA-Z0-9]+))$";
  private static final String MESSAGE = "Rename this file to match this regular expression: \"%s\".";

  /**
   * pattern
   */
  @RuleProperty(
    key = "format",
    defaultValue = "" + DEFAULT)
  public String format = DEFAULT;
  private Pattern pattern = null;

  @Override
  public void init() {
    pattern = CheckUtils.compileUserRegexp(format);
  }

  @Override
  public void visitFile(@Nullable AstNode astNode) {
    String fileName = getContext().getInputFile().filename();
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex > 0) {
      String moduleName = fileName.substring(0, dotIndex);
      if (!pattern.matcher(moduleName).matches()) {
        getContext().createFileViolation(this, String.format(MESSAGE, format));
      }
    }
  }

}
