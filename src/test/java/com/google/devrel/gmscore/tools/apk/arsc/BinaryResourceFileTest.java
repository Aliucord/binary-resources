/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

import com.google.common.io.ByteStreams;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.*;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Tests {@link BinaryResourceFile}.
 */
@RunWith(JUnit4.class)
public final class BinaryResourceFileTest {
    private final File apk = new File(getClass().getClassLoader().getResource("test.apk").getPath());

    /**
     * Returns all files in an apk that match a given regular expression.
     * @param apkFile The file containing the apk zip archive.
     * @param regex A regular expression to match the requested filenames.
     * @return A mapping of the matched filenames to their byte contents.
     * @throws IOException Thrown if a matching file cannot be read from the apk.
     */
    private static Map<String, byte[]> getFiles(File apkFile, Pattern regex) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();  // Retain insertion order
        // Extract apk
        try (ZipFile apkZip = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> zipEntries = apkZip.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                // Visit all files with the given extension
                if (regex.matcher(zipEntry.getName()).matches()) {
                    // Map class name to definition
                    try (InputStream is = new BufferedInputStream(apkZip.getInputStream(zipEntry))) {
                        files.put(zipEntry.getName(), ByteStreams.toByteArray(is));
                    }
                }
            }
        }
        return files;
    }

    /**
     * Tests that resource files, when reassembled, are identical.
     */
    @Test
    public void testToByteArray() throws Exception {
        // Get all .arsc and encoded .xml files
        String regex = "(.*?\\.arsc)|(AndroidManifest\\.xml)|(res/.*?\\.xml)";
        Map<String, byte[]> resourceFiles = getFiles(apk, Pattern.compile(regex));
        for (Entry<String, byte[]> entry : resourceFiles.entrySet()) {
            String name = entry.getKey();
            byte[] fileBytes = entry.getValue();
            if (!name.startsWith("res/raw/")) {  // xml files in res/raw/ are not compact XML
                BinaryResourceFile file = new BinaryResourceFile(fileBytes);
                Assert.assertArrayEquals(name, fileBytes, file.toByteArray());
            }
        }
    }

    /**
     * Test that newly added strings to a StringPoolChunk are saved correctly.
     */
    @Test
    public void testAddNewString() throws Exception {
        String newString = "abcdef";

        byte[] arscBytes = getFiles(apk, Pattern.compile("resources.arsc")).get("resources.arsc");
        BinaryResourceFile arsc = new BinaryResourceFile(arscBytes);

        int newStringIdx = ((ResourceTableChunk) arsc.getChunks().get(0))
                .getStringPool()
                .addString(newString);

        byte[] arsc2Bytes = arsc.toByteArray();
        BinaryResourceFile arsc2 = new BinaryResourceFile(arsc2Bytes);

        String arsc2String = ((ResourceTableChunk) arsc2.getChunks().get(0))
                .getStringPool()
                .getString(newStringIdx);

        Assert.assertEquals(newString, arsc2String);
    }

//    @Test
//    public void dumpArsc() throws Exception {
//        byte[] arscBytes = getFiles(apk, Pattern.compile("resources.arsc")).get("resources.arsc");
//        BinaryResourceFile arsc = new BinaryResourceFile(arscBytes);
//
//        try (java.io.FileOutputStream out = new java.io.FileOutputStream("G:/in.arsc")) {
//            out.write(arscBytes);
//        }
//
//        byte[] arscBytes2 = arsc.toByteArray();
//
//        try (java.io.FileOutputStream out = new java.io.FileOutputStream("G:/out.arsc")) {
//            out.write(arscBytes2);
//        }
//
//        new BinaryResourceFile(arscBytes2);
//    }
}
