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
package org.sonar.cxx.sensors.coverage.bullseye;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamException;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.sonar.api.utils.PathUtils;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.cxx.sensors.coverage.CoverageMeasures;
import org.sonar.cxx.sensors.coverage.CoverageParser;
import org.sonar.cxx.sensors.utils.EmptyReportException;
import org.sonar.cxx.sensors.utils.InvalidReportException;
import org.sonar.cxx.sensors.utils.StaxParser;

/**
 * {@inheritDoc}
 */
public class BullseyeParser implements CoverageParser {

  private static final Logger LOG = Loggers.get(BullseyeParser.class);

  private String prevLine;
  private int totalconditions;
  private int totalcoveredconditions;

  private static String ensureRefPathIsCorrect(@Nullable String refPath) {
    if (refPath == null || refPath.isEmpty()) {
      return refPath;
    }
    if (refPath.endsWith("\\") || refPath.endsWith("/")) {
      return refPath.replace('\\', '/');
    }
    return refPath.replace('\\', '/') + "/";
  }

  /**
   * @param path
   * @param correctPath
   * @return
   */
  private static String buildPath(List<String> path, String correctPath) {
    String fileName = String.join(File.separator, path);
    if (!(new File(fileName)).isAbsolute()) {
      fileName = correctPath + fileName;
    }
    return PathUtils.sanitize(fileName);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map<String, CoverageMeasures> parse(File report)  {
    HashMap<String, CoverageMeasures> coverageData = new HashMap<String, CoverageMeasures>();
    try {
      StaxParser topLevelparser = new StaxParser((SMHierarchicCursor rootCursor) -> {
        try {
          rootCursor.advance();
        } catch (com.ctc.wstx.exc.WstxEOFException e) {
          throw new EmptyReportException("Coverage report " + report + " result is empty (parsed by " + this + ")", e);
        }
        collectCoverageLeafNodes(rootCursor.getAttrValue("dir"), rootCursor.childElementCursor("src"), coverageData);
      });

      StaxParser parser = new StaxParser((SMHierarchicCursor rootCursor) -> {
        rootCursor.advance();
        collectCoverage2(rootCursor.getAttrValue("dir"), rootCursor.childElementCursor("folder"), coverageData);
      });

      topLevelparser.parse(report);
      parser.parse(report);
    } catch(XMLStreamException e) {
      throw new InvalidReportException("Bullseye coverage report '" + report + "' cannot be parsed.", e);
    }
    return coverageData;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  private void collectCoverageLeafNodes(String refPath, SMInputCursor folder,
                                        final Map<String, CoverageMeasures> coverageData)
    throws XMLStreamException {

    String correctPath = ensureRefPathIsCorrect(refPath);

    while (folder.getNext() != null) {
      File fileName = new File(correctPath, folder.getAttrValue("name"));
      recTreeTopWalk(fileName, folder, coverageData);
    }
  }

  private void recTreeTopWalk(File fileName, SMInputCursor folder,
                              final Map<String, CoverageMeasures> coverageData)
    throws XMLStreamException {
    SMInputCursor child = folder.childElementCursor();
    while (child.getNext() != null) {
      CoverageMeasures fileMeasuresBuilderIn = CoverageMeasures.create();

      funcWalk(child, fileMeasuresBuilderIn);
      coverageData.put(fileName.getPath(), fileMeasuresBuilderIn);
    }
  }

  private void collectCoverage2(String refPath, SMInputCursor folder,
                                final Map<String, CoverageMeasures> coverageData)
    throws XMLStreamException {

    String correctPath = ensureRefPathIsCorrect(refPath);

    LinkedList<String> path = new LinkedList<String>();
    while (folder.getNext() != null) {
      String folderName = folder.getAttrValue("name");
      path.add(folderName);
      recTreeWalk(correctPath, folder, path, coverageData);
      path.removeLast();
    }
  }

  private void probWalk(SMInputCursor prob, CoverageMeasures fileMeasuresBuilderIn) throws XMLStreamException {
    String line = prob.getAttrValue("line");
    String kind = prob.getAttrValue("kind");
    String event = prob.getAttrValue("event");
    if (!line.equals(prevLine)) {
      saveConditions(fileMeasuresBuilderIn);
    }
    updateMeasures(kind, event, line, fileMeasuresBuilderIn);
    prevLine = line;
  }

  private void funcWalk(SMInputCursor func, CoverageMeasures fileMeasuresBuilderIn) throws XMLStreamException {
    SMInputCursor prob = func.childElementCursor("probe");
    while (prob.getNext() != null) {
      probWalk(prob, fileMeasuresBuilderIn);
    }
    saveConditions(fileMeasuresBuilderIn);
  }

  private void fileWalk(SMInputCursor file, CoverageMeasures fileMeasuresBuilderIn) throws XMLStreamException {
    SMInputCursor func = file.childElementCursor("fn");
    while (func.getNext() != null) {
      funcWalk(func, fileMeasuresBuilderIn);
    }
  }

  private void recTreeWalk(String refPath, SMInputCursor folder, List<String> path,
                           final Map<String, CoverageMeasures> coverageData)
    throws XMLStreamException {

    String correctPath = ensureRefPathIsCorrect(refPath);

    SMInputCursor child = folder.childElementCursor();
    while (child.getNext() != null) {
      String folderChildName = child.getLocalName();
      String name = child.getAttrValue("name");
      path.add(name);
      if ("src".equalsIgnoreCase(folderChildName)) {
        String filePath = buildPath(path, correctPath);
        CoverageMeasures fileMeasuresBuilderIn = CoverageMeasures.create();
        fileWalk(child, fileMeasuresBuilderIn);
        coverageData.put(filePath, fileMeasuresBuilderIn);
      } else {
        recTreeWalk(correctPath, child, path, coverageData);
      }
      path.remove(path.size() - 1);
    }
  }

  private void saveConditions(CoverageMeasures fileMeasuresBuilderIn) {
    if (totalconditions > 0) {
      if (totalcoveredconditions == 0) {
        fileMeasuresBuilderIn.setHits(Integer.parseInt(prevLine), 0);
      } else {
        fileMeasuresBuilderIn.setHits(Integer.parseInt(prevLine), 1);
      }
      fileMeasuresBuilderIn.setConditions(Integer.parseInt(prevLine), totalconditions, totalcoveredconditions);
    }
    totalconditions = 0;
    totalcoveredconditions = 0;
  }

  private void updateMeasures(String kind, String event, String line, CoverageMeasures fileMeasuresBuilderIn) {

    switch (kind.toLowerCase(Locale.ENGLISH)) {
      case "decision":
      case "condition":
        totalconditions += 2;
        setTotalCoveredConditions(event);
        break;
      case "catch":
      case "for-range-body":
      case "switch-label":
      case "try":
        totalconditions++;
        if ("full".equalsIgnoreCase(event)) {
          totalcoveredconditions++;
        }
        break;
      case "function":
        int lineHits = 0;
        if ("full".equalsIgnoreCase(event)) {
          lineHits = 1;
        }
        fileMeasuresBuilderIn.setHits(Integer.parseInt(line), lineHits);
        break;
      case "constant":
        break;
      default:
        LOG.error("BullseyeParser unknown probe kind '{}'", kind);
    }
  }

  /**
   * @param event
   */
  private void setTotalCoveredConditions(String event) {
    switch (event.toLowerCase(Locale.ENGLISH)) {
      case "full":
        totalcoveredconditions += 2;
        break;
      case "true":
      case "false":
        totalcoveredconditions++;
        break;
      case "none":
        // do nothing
        break;
      default:
        LOG.error("BullseyeParser unknown probe event '{}'", event);
    }
  }

}
