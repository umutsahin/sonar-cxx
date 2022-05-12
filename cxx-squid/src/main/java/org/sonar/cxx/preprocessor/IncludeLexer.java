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
package org.sonar.cxx.preprocessor;

import com.sonar.sslr.api.Preprocessor;
import com.sonar.sslr.impl.Lexer;
import com.sonar.sslr.impl.channel.BlackHoleChannel;
import static com.sonar.sslr.impl.channel.RegexpChannelBuilder.ANY_CHAR;
import static com.sonar.sslr.impl.channel.RegexpChannelBuilder.commentRegexp;
import org.sonar.cxx.channels.PreprocessorChannel;
import org.sonar.cxx.config.CxxSquidConfiguration;

public final class IncludeLexer {

  private IncludeLexer() {
  }

  public static Lexer create(Preprocessor... preprocessors) {
    return create(new CxxSquidConfiguration(), preprocessors);
  }

  public static Lexer create(CxxSquidConfiguration squidConfig, Preprocessor... preprocessors) {
    Lexer.Builder builder = Lexer.builder()
                                 .withCharset(squidConfig.getCharset())
                                 .withFailIfNoChannelToConsumeOneCharacter(true)
                                 .withChannel(new BlackHoleChannel("\\s"))
                                 .withChannel(new PreprocessorChannel())
                                 .withChannel(commentRegexp("/\\*", ANY_CHAR + "*?", "\\*/"))
                                 .withChannel(new BlackHoleChannel(".*"));

    for (Preprocessor preprocessor : preprocessors) {
      builder.withPreprocessor(preprocessor);
    }

    return builder.build();
  }

}
