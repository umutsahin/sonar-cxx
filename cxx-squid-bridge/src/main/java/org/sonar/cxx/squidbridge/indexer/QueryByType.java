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
package org.sonar.cxx.squidbridge.indexer;

import javax.annotation.Nullable;
import org.sonar.cxx.squidbridge.api.Query;
import org.sonar.cxx.squidbridge.api.SourceCode;

public class QueryByType implements Query {

  private final Class<? extends SourceCode> resourceType;

  public QueryByType(@Nullable Class<? extends SourceCode> resourceType) {
    if (resourceType == null) {
      throw new IllegalStateException("The type of resource can't be null !");
    }
    this.resourceType = resourceType;
  }

  @Override
  public boolean match(SourceCode unit) {
    return unit.isType(resourceType);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (obj.getClass() != getClass()) {
      return false;
    }

    QueryByType other = (QueryByType) obj;

    if (resourceType != null ? !resourceType.equals(other.resourceType) : other.resourceType != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return resourceType != null ? resourceType.hashCode() : 0;
  }
}
