/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devrel.gmscore.tools.apk.arsc;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Test;

public class BinaryResourceConfigurationTest {
  @Test
  public void testConfigurationString() {
    {
      byte[] buff = new byte[] {
          0, 0, 0, 28, 0, 0, 0, 0, 'f', 'r', 'C', 'A', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
      };
      assertEquals("fr-rCA", BinaryResourceConfiguration.create(ByteBuffer.wrap(buff)).toString());
    }
    {
      byte[] buff = new byte[] {
          0, 0, 0, 48, 0, 0, 0, 0, 's', 'r', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 'L', 'a', 't', 'n', 0, 0, 0, 0, 0, 0, 0, 0
      };
      assertEquals("b+sr+Latn", BinaryResourceConfiguration.create(ByteBuffer.wrap(buff)).toString());
    }
  }
}
