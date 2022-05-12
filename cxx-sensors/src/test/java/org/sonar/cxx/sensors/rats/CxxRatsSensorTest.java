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
package org.sonar.cxx.sensors.rats;

import static org.assertj.core.api.Assertions.assertThat;
import org.assertj.core.api.SoftAssertions;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.cxx.sensors.utils.CxxReportSensor;
import org.sonar.cxx.sensors.utils.TestUtils;

public class CxxRatsSensorTest {

  private CxxRatsSensor sensor;
  private DefaultFileSystem fs;
  private final MapSettings settings = new MapSettings();

  @Before
  public void setUp() {
    fs = TestUtils.mockFileSystem();
  }

  @Test
  public void shouldReportCorrectViolations() {
    SensorContextTester context = SensorContextTester.create(fs.baseDir());
    settings.setProperty(CxxReportSensor.ERROR_RECOVERY_KEY, true);
    settings.setProperty(CxxRatsSensor.REPORT_PATH_KEY, "rats-reports/rats-result-*.xml");
    context.setSettings(settings);

    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "sources/utils/code_chunks.cpp").setLanguage(
      "cxx").initMetadata("asd\nasdas\nasda\n").build());
    context.fileSystem().add(TestInputFileBuilder.create("ProjectKey", "report.c").setLanguage("cxx").initMetadata(
      "asd\nasdas\nasda\n").build());

    sensor = new CxxRatsSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(5);
  }

  @Test
  public void sensorDescriptor() {
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
    sensor = new CxxRatsSensor();
    sensor.describe(descriptor);

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(descriptor.name()).isEqualTo("CXX RATS report import");
    softly.assertThat(descriptor.languages()).containsOnly("cxx", "cpp", "c++", "c");
    softly.assertThat(descriptor.ruleRepositories()).containsOnly(CxxRatsRuleRepository.KEY);
    softly.assertAll();
  }

}
