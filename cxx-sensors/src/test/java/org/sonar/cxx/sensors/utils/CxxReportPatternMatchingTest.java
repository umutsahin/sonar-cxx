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
package org.sonar.cxx.sensors.utils;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import org.apache.commons.io.FileUtils;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;

public class CxxReportPatternMatchingTest {

  private static final String REPORT_PATH_KEY = "sonar.cxx.cppcheck.reportPaths";
  @Rule
  public TemporaryFolder base = new TemporaryFolder();
  private final MapSettings settings = new MapSettings();
  private final List<String[]> examples = new LinkedList<>();

  @Before
  public void setUp() {
    //                        "pattern",      "matches",   "matches not"
    // relative
    examples.add(new String[]{"A.ext", "A.ext", "dir/B.ext"});
    examples.add(new String[]{"dir/A.ext", "dir/A.ext", "A.ext,dir/B.ext"});
    examples.add(new String[]{"dir/../A.ext", "A.ext", "B.ext,dir/A.ext"});
    examples.add(new String[]{"./A.ext", "A.ext", "B.ext"});
    examples.add(new String[]{"./A.ext", "A.ext", "B.ext"});
    // empty
    examples.add(new String[]{"", "", ""});
    // question mark glob
    examples.add(new String[]{"A?.ext", "AA.ext,AB.ext", "B.ext"});
    // multi-char glob
    examples.add(new String[]{"A*.ext", "A.ext,AAA.ext", "B.ext"});
    // multi-dir glob
    examples.add(new String[]{"**/A.ext", "A.ext,dir/A.ext,dir/subdir/A.ext", "B.ext,dir/B.ext"});
    examples.add(new String[]{"dir/**/A.ext", "dir/A.ext,dir/subdir/A.ext", "A.ext,dir/B.ext,dir/subdir/B.ext"});
  }

  @Test
  public void getReports_patternMatching() throws java.io.IOException, java.lang.InterruptedException {
    String pattern, expected, allpaths;
    List<File> reports;
    for (String[] example : examples) {
      pattern = example[0];
      expected = example[1];
      allpaths = String.join(",", Arrays.copyOfRange(example, 1, 3));
      setupExample(allpaths);

      settings.setProperty(REPORT_PATH_KEY, pattern);
      SensorContextTester context = SensorContextTester.create(base.getRoot());
      context.setSettings(settings);
      reports = CxxUtils.getFiles(context, REPORT_PATH_KEY);
      String[] parsedPaths = expected.split(",");
      LinkedList<File> expectedFiles = new LinkedList<File>();
      for (String path : parsedPaths) {
        path = path.trim();
        if (!path.isEmpty()) {
          expectedFiles.add(new File(base.getRoot(), path));
        }
      }

      TreeSet<File> realSet = new TreeSet<File>(reports);
      TreeSet<File> expectedSet = new TreeSet<File>(expectedFiles);
      assertThat(realSet).describedAs("Failed for pattern: {}", pattern).isEqualTo(expectedSet);
      deleteExample(base.getRoot());
    }

  }

  private void setupExample(String pathes) throws java.io.IOException {
    String[] parsedPaths = pathes.split(",");
    for (String path : parsedPaths) {
      path = path.trim();
      if (!path.isEmpty()) {
        FileUtils.touch(new File(base.getRoot(), path));
      }
    }
  }

  private void deleteExample(File dir) throws java.io.IOException {
    FileUtils.cleanDirectory(dir);
  }

}
