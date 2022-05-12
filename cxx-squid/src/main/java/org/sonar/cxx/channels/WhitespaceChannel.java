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
package org.sonar.cxx.channels;

import com.sonar.sslr.api.Token;
import com.sonar.sslr.impl.Lexer;
import org.sonar.cxx.parser.CxxTokenType;
import org.sonar.sslr.channel.Channel;
import org.sonar.sslr.channel.CodeReader;

public class WhitespaceChannel extends Channel<Lexer> {

  @Override
  public boolean consume(CodeReader code, Lexer output) {
    boolean isWhitespace = false;
    while (Character.isWhitespace(code.peek())) {
      if (!isWhitespace) {
        int line = code.getLinePosition();
        int column = code.getColumnPosition();
        // merge multiple whitespaces into one
        output.addToken(Token.builder()
          .setLine(line)
          .setColumn(column)
          .setURI(output.getURI())
          .setValueAndOriginalValue(" ")
          .setType(CxxTokenType.WS)
          .build());
        isWhitespace = true;
      }
      code.pop();
    }

    return isWhitespace;
  }

}
