/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2010-2020 SonarOpenCommunity
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
package org.sonar.cxx.sensors.other;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.sonar.api.PropertyType;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.cxx.sensors.utils.CxxIssuesReportSensor;
import org.sonar.cxx.sensors.utils.InvalidReportException;
import org.sonar.cxx.sensors.utils.StaxParser;
import org.sonar.cxx.utils.CxxReportIssue;

/**
 * Custom Rule Import, all static analysis are supported.
 */
public class CxxOtherSensor extends CxxIssuesReportSensor {

  public static final String REPORT_PATH_KEY = "sonar.cxx.other.reportPaths";
  public static final String RULES_KEY = "sonar.cxx.other.rules";

  private static final Logger LOG = Loggers.get(CxxOtherSensor.class);

  public static List<PropertyDefinition> properties() {
    return Collections.unmodifiableList(Arrays.asList(
      PropertyDefinition.builder(REPORT_PATH_KEY)
        .name("External analyser XML report(s)")
        .description(
          "Path to a code analysis XML report, which is generated by some unsupported code analyser, relative to"
            + " projects root." + USE_ANT_STYLE_WILDCARDS
            + " See <a href='https://github.com/SonarOpenCommunity/sonar-cxx"
            + "/wiki/Extending-the-code-analysis'>here</a> for details.")
        .category("CXX External Analyzers")
        .subCategory("Other Analyser")
        .onQualifiers(Qualifiers.PROJECT)
        .multiValues(true)
        .build(),
      PropertyDefinition.builder(RULES_KEY)
        .name("External rules")
        .description(
          "Rule sets for 'external' code analysers. Use one value per rule set. See <a href='"
            + "https://github.com/SonarOpenCommunity/sonar-cxx/wiki/Extending-the-code-analysis"
            + "'>this page</a> for details.")
        .type(PropertyType.TEXT)
        .multiValues(true)
        .category("CXX External Analyzers")
        .subCategory("Other Analyser")
        .build()
    ));
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .name("CXX external analyser report import")
      .onlyOnLanguages("cxx", "cpp", "c")
      .createIssuesForRuleRepository(getRuleRepositoryKey())
      .onlyWhenConfiguration(conf -> conf.hasKey(getReportPathsKey()));
  }

  @Override
  public void processReport(File report) {
    LOG.debug("Processing 'other' report '{}'", report.getName());

    try {
      var parser = new StaxParser((SMHierarchicCursor rootCursor) -> {
        rootCursor.advance();

        SMInputCursor errorCursor = rootCursor.childElementCursor("error");
        while (errorCursor.getNext() != null) {
          String file = errorCursor.getAttrValue("file");
          String line = errorCursor.getAttrValue("line");
          String column = errorCursor.getAttrValue("column");
          String id = errorCursor.getAttrValue("id");
          String msg = errorCursor.getAttrValue("msg");

          var issue = new CxxReportIssue(id, file, line, column, msg);
          saveUniqueViolation(issue);
        }
      });

      parser.parse(report);
    } catch (XMLStreamException e) {
      throw new InvalidReportException("The 'other' report is invalid", e);
    }
  }

  @Override
  protected String getReportPathsKey() {
    return REPORT_PATH_KEY;
  }

  @Override
  protected String getRuleRepositoryKey() {
    return CxxOtherRepository.KEY;
  }

}
