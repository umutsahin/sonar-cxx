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
package org.sonar.cxx.sensors.compiler.gcc;

import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import org.assertj.core.api.SoftAssertions;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.cxx.sensors.utils.TestUtils;

public class CxxCompilerGccSensorTest {

  private DefaultFileSystem fs;
  private final MapSettings settings = new MapSettings();

  @Before
  public void setUp() {
    fs = TestUtils.mockFileSystem();
  }

  @Test
  public void sensorDescriptorGcc() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
    CxxCompilerGccSensor sensor = new CxxCompilerGccSensor();
    sensor.describe(descriptor);
    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(descriptor.name()).isEqualTo("CXX GCC compiler report import");
    softly.assertThat(descriptor.languages()).containsOnly("cxx", "cpp", "c++", "c");
    softly.assertThat(descriptor.ruleRepositories())
      .containsOnly(CxxCompilerGccRuleRepository.KEY);
    softly.assertAll();
  }

  @Test
  public void shouldReportCorrectGccViolations() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxCompilerGccSensor.REPORT_PATH_KEY, "compiler-reports/build.gcclog");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "src/zipmanager.cpp")
      .setLanguage("cxx").initMetadata("asd\nasdas\nasda\n").build());

    CxxCompilerGccSensor sensor = new CxxCompilerGccSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(4);
  }

  @Test
  public void shouldReportCorrectGccViolationsWithOrWithoutIds() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxCompilerGccSensor.REPORT_PATH_KEY, "compiler-reports/build-warning-without-id.gcclog");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "main.c")
      .setLanguage("cxx").initMetadata("asd\nasdas\nasda\n").build());

    CxxCompilerGccSensor sensor = new CxxCompilerGccSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(2);
    ArrayList<Issue> issuesList = new ArrayList<Issue>(context.allIssues());
    // warning without activation switch (no id) should be mapped to the "default" rule
    assertThat(issuesList.get(0).ruleKey().rule()).isEqualTo("default");
    // warning with activation switch should be mapped to the matching rule
    assertThat(issuesList.get(1).ruleKey().rule()).isEqualTo("-Wunused-variable");
  }

}
