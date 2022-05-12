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
package org.sonar.cxx.sensors.pclint;

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
import org.sonar.cxx.sensors.utils.CxxReportSensor;
import org.sonar.cxx.sensors.utils.TestUtils;

public class CxxPCLintSensorTest {

  private DefaultFileSystem fs;
  private final MapSettings settings = new MapSettings();

  @Before
  public void setUp() {
    fs = TestUtils.mockFileSystem();
    settings.setProperty(CxxReportSensor.ERROR_RECOVERY_KEY, true);
  }

  @Test
  public void shouldReportCorrectViolations() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY, "pclint-reports/pclint-result-SAMPLE.xml");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "FileZip.cpp").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());
    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "FileZip.h").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());
    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "ZipManager.cpp").setLanguage("cxx")
      .initMetadata("asd\nasdas\nasda\n").build());

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(16);
  }

  @Test
  public void shouldReportCorrectMisra2004Violations() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY, "pclint-reports/pclint-result-MISRA2004-SAMPLE1.xml");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "test.c").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(29);
  }

  @Test
  public void shouldReportCorrectMisra2004PcLint9Violations() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY, "pclint-reports/pclint-result-MISRA2004-SAMPLE2.xml");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "test.c").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);
  }

  @Test
  public void shouldReportCorrectMisraCppViolations() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY, "pclint-reports/pclint-result-MISRACPP.xml");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "test.c").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(2);
    ArrayList<Issue> issuesList = new ArrayList<Issue>(context.allIssues());
    assertThat(issuesList.get(0).ruleKey().rule()).isEqualTo("M5-0-19");
    assertThat(issuesList.get(1).ruleKey().rule()).isEqualTo("M18-4-1");
  }

  @Test
  public void shouldNotSaveIssuesWhenMisra2004DescIsWrong() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY, "pclint-reports/incorrect-pclint-MISRA2004-desc.xml");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "test.c").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).isEmpty();
  }

  @Test
  public void shouldNotSaveAnythingWhenMisra2004RuleDoNotExist() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY,
                         "pclint-reports/incorrect-pclint-MISRA2004-rule-do-not-exist.xml");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "test.c").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).isEmpty();
  }

  @Test
  public void shouldNotRemapMisra1998Rules() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY, "pclint-reports/pclint-result-MISRA1998-SAMPLE.xml");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "test.c").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);
  }

  @Test
  public void shouldReportProjectLevelViolations() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY, "pclint-reports/pclint-result-projectlevelviolation.xml");
    context.setSettings(settings);

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);
  }

  @Test
  public void shouldThrowExceptionInvalidChar() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY, "pclint-reports/pclint-result-invalid-char.xml");
    context.setSettings(settings);

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues().size()).isZero();
  }

  @Test
  public void sensorDescriptor() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.describe(descriptor);

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(descriptor.name()).isEqualTo("CXX PC-lint report import");
    softly.assertThat(descriptor.languages()).containsOnly("cxx", "cpp", "c++", "c");
    softly.assertThat(descriptor.ruleRepositories()).containsOnly(CxxPCLintRuleRepository.KEY);
    softly.assertAll();
  }

  @Test
  public void loadSupplementalMsg() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxPCLintSensor.REPORT_PATH_KEY, "pclint-reports/pclint-result-with-supplemental.xml");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "FileZip.cpp").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());
    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "FileZip.h").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());

    CxxPCLintSensor sensor = new CxxPCLintSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(2);

    ArrayList<Issue> allIssues = new ArrayList<Issue>(context.allIssues());

    Issue firstIssue = allIssues.get(0);
    assertThat(firstIssue.flows()).hasSize(1);
    assertThat(firstIssue.flows().get(0).locations()).hasSize(3);

    Issue secondIssue = allIssues.get(1);
    assertThat(secondIssue.flows()).hasSize(1);
    assertThat(secondIssue.flows().get(0).locations()).hasSize(1);
  }
}
