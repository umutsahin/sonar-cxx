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
package org.sonar.cxx.sensors.tests.xunit;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;

import org.codehaus.staxmate.in.ElementFilter;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.sonar.api.utils.ParsingUtils;
import org.sonar.cxx.sensors.utils.CxxUtils;
import org.sonar.cxx.sensors.utils.EmptyReportException;
import org.sonar.cxx.sensors.utils.StaxParser.XmlStreamHandler;

/**
 * {@inheritDoc}
 */
public class XunitReportParser implements XmlStreamHandler {

  private final String baseDir;
  private final Map<Path, TestFile> testFiles = new HashMap<>();

  public XunitReportParser(String baseDir) {
    this.baseDir = baseDir;
    testFiles.put(null, new TestFile("")); // TestFile 'global' (without filename)
  }

  public Collection<TestFile> getTestFiles() {
    return testFiles.values();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void stream(SMHierarchicCursor rootCursor) throws XMLStreamException {
    SMInputCursor testSuiteCursor = rootCursor.constructDescendantCursor(new ElementFilter("testsuite"));
    try {
      while (testSuiteCursor.getNext() != null) {
        parseTestSuiteTag(testSuiteCursor);
      }
    } catch (com.ctc.wstx.exc.WstxEOFException e) {
      throw new EmptyReportException("The 'xUnit' report is empty", e);
    }
  }

  private void parseTestSuiteTag(SMInputCursor testSuiteCursor)
    throws XMLStreamException {
    String testSuiteName = testSuiteCursor.getAttrValue("name");
    String testSuiteFName = testSuiteCursor.getAttrValue("filename");

    SMInputCursor childCursor = testSuiteCursor.childElementCursor();
    while (childCursor.getNext() != null) {
      String elementName = childCursor.getLocalName();
      if ("testsuite".equals(elementName)) {
        parseTestSuiteTag(childCursor);
      } else if ("testcase".equals(elementName)) {
        parseTestCaseTag(childCursor, testSuiteName, testSuiteFName);
      }
    }
  }

  private void parseTestCaseTag(SMInputCursor testCaseCursor, String tsName, String tsFilename) throws
    XMLStreamException {
    String classname = testCaseCursor.getAttrValue("classname");
    String tcFilename = testCaseCursor.getAttrValue("filename");
    String name = parseTestCaseName(testCaseCursor);
    Double time = parseTime(testCaseCursor);
    String status = "ok";
    String stack = "";
    String msg = "";

    // Googletest-reports mark the skipped tests with status="notrun"
    String statusattr = testCaseCursor.getAttrValue("status");
    if ("notrun".equals(statusattr)) {
      status = "skipped";
    } else {
      SMInputCursor childCursor = testCaseCursor.childElementCursor();
      if (childCursor.getNext() != null) {
        String elementName = childCursor.getLocalName();
        if (null != elementName) {
          switch (elementName) {
            case "skipped":
              status = "skipped";
              break;
            case "failure":
              status = "failure";
              msg = childCursor.getAttrValue("message");
              stack = childCursor.collectDescendantText();
              break;
            case "error":
              status = "error";
              msg = childCursor.getAttrValue("message");
              stack = childCursor.collectDescendantText();
              break;
            default:
              break;
          }
        }
      }
    }

    String filename = tcFilename != null ? tcFilename : tsFilename;
    TestFile file = getTestFile(filename);
    file.add(new TestCase(name, time.intValue(), status, stack, msg, classname, filename, tsName));
  }

  private TestFile getTestFile(String filename) {
    Optional<Path> absolute = Optional.ofNullable(CxxUtils.resolveAntPath(baseDir, filename))
                                      .map(p -> Paths.get(p));

    TestFile file = testFiles.get(absolute.orElse(null));
    if (file == null) {
      file = new TestFile(absolute.map(Object::toString).orElse(null));
      testFiles.put(absolute.orElse(null), file);
    }
    
    return file;
  }

  private String parseTestCaseName(SMInputCursor testCaseCursor) throws XMLStreamException {
    String name = testCaseCursor.getAttrValue("name");
    String classname = testCaseCursor.getAttrValue("classname");
    if (classname != null) {
      name = classname + "/" + name;
    }
    return name;
  }

  private double parseTime(SMInputCursor testCaseCursor)
    throws XMLStreamException {
    double time = 0.0;
    try {
      String sTime = testCaseCursor.getAttrValue("time");
      if (sTime != null && !sTime.isEmpty()) {
        Double tmp = ParsingUtils.parseNumber(sTime, Locale.ENGLISH);
        if (!Double.isNaN(tmp)) {
          time = ParsingUtils.scaleValue(tmp * 1000, 3);
        }
      }
    } catch (ParseException e) {
      throw new XMLStreamException(e);
    }

    return time;
  }

}
