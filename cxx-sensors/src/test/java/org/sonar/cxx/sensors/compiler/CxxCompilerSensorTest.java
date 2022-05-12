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
package org.sonar.cxx.sensors.compiler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.log.LogTester;
import org.sonar.cxx.sensors.utils.CxxReportSensor;
import org.sonar.cxx.sensors.utils.TestUtils;

public class CxxCompilerSensorTest {

  @Rule
  public LogTester logTester = new LogTester();

  private DefaultFileSystem fs;
  private final MapSettings settings = new MapSettings();
  private SensorContextTester context;
  private CxxCompilerSensorMock sensor;

  @Before
  public void setUp() {
    settings.setProperty(CxxReportSensor.ERROR_RECOVERY_KEY, true);
    fs = TestUtils.mockFileSystem();
    context = SensorContextTester.create(fs.baseDir());
    context.setSettings(settings);
    sensor = new CxxCompilerSensorMock(context);
  }

  @Test
  public void testFileNotFound() {
    File report = new File("");
    sensor.setRegex("(?<test>.*)");
    sensor.testExecuteReport(report);
    String log = logTester.logs().toString();
    assertThat(log).contains("FileNotFoundException");
  }

  @Test
  public void testRegexEmpty() {
    File report = new File("");
    sensor.testExecuteReport(report);
    String log = logTester.logs().toString();
    assertThat(log).contains("empty custom regular expression");
  }

  @Test
  public void testRegexInvalid() {
    File report = new File(fs.baseDir(), "compiler-reports/VC-report.vclog");
    sensor.setRegex("(?<test>*)");
    sensor.testExecuteReport(report);
    String log = logTester.logs().toString();
    assertThat(log).contains("PatternSyntaxException");
  }

  @Test
  public void testRegexNamedGroupMissing() {
    File report = new File(fs.baseDir(), "compiler-reports/VC-report.vclog");
    sensor.setRegex(".*");
    sensor.testExecuteReport(report);
    String log = logTester.logs().toString();
    assertThat(log).contains("contains no named-capturing group");
  }

  private class CxxCompilerSensorMock extends CxxCompilerSensor {

    private String regex = "";

    public CxxCompilerSensorMock(SensorContext context) {
      this.context = context;
    }

    @Override
    public void describe(SensorDescriptor descriptor) {
    }

    public void testExecuteReport(File report) {
      executeReport(report);
    }

    public void setRegex(String regex) {
      this.regex = regex;
    }

    @Override
    protected String getCompilerKey() {
      return "XXX";
    }

    @Override
    protected String getEncoding() {
      return StandardCharsets.UTF_8.name();
    }

    @Override
    protected String getRegex() {
      return regex;
    }

    @Override
    protected String getReportPathsKey() {
      return "cxx.reportPaths";
    }

    @Override
    protected String getRuleRepositoryKey() {
      return "cxx.XXX";
    }

  }

}
