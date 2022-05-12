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

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Test;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;

public class CxxSonarWayProfileTest {

  @Test
  public void should_create_sonar_way_profile() {
    CxxSonarWayProfile profileDef = new CxxSonarWayProfile();
    BuiltInQualityProfilesDefinition.Context context = new BuiltInQualityProfilesDefinition.Context();
    profileDef.define(context);
    BuiltInQualityProfilesDefinition.BuiltInQualityProfile profile = context.profile("cxx", "Sonar way");
    assertThat(profile.language()).isEqualTo(CxxLanguage.KEY);
    assertThat(profile.name()).isEqualTo("Sonar way");
    List<BuiltInQualityProfilesDefinition.BuiltInActiveRule> activeRules = profile.rules();
    assertThat(activeRules.size()).as("Expected number of rules in profile").isNotNegative();
  }

}
