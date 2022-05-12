/*
 * C++ Community Plugin (cxx plugin)
 * Copyright (C) 2021 SonarOpenCommunity
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
/**
 * fork of SSLR Squid Bridge: https://github.com/SonarSource/sslr-squid-bridge/tree/2.6.1
 * Copyright (C) 2010 SonarSource / mailto: sonarqube@googlegroups.com / license: LGPL v3
 */
package org.sonar.cxx.squidbridge.api;

import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SourceFileTest {

  @Test
  public void testGetStartAtLine() {
    SourceFile file = new SourceFile("com/sonarsource/Toto.java");
    assertEquals(1, file.getStartAtLine());
    file = new SourceFile("com/sonarsource/Toto.java", "Toto.java");
    assertEquals(1, file.getStartAtLine());
  }

  @Test
  public void testHasNoSon() {
    SourceFile file = new SourceFile("com/sonarsource/Toto.java");
    Set<Integer> noSonarTagLines = new HashSet<>();
    noSonarTagLines.add(23);
    noSonarTagLines.add(10);
    file.addNoSonarTagLines(noSonarTagLines);
    assertTrue(file.hasNoSonarTagAtLine(23));
    assertTrue(file.hasNoSonarTagAtLine(10));
    assertFalse(file.hasNoSonarTagAtLine(11));
  }
}
