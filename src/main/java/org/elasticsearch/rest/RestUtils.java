/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.rest;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.path.PathTrie;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

public class RestUtils {

    public static final PathTrie.Decoder REST_DECODER = new PathTrie.Decoder() {
        @Override
        public String decode(String value) {
            return RestUtils.decodeComponent(value);
        }
    };

    public static void decodeQueryString(String s, int fromIndex, Map<String, String> params) {
       System.out.println("org.elasticsearch.rest_RestUtil_decodeQueryString(String s, int fromIndex, Map<String, String> params)");
       System.out.println(s);
       System.out.println(params);
       if (fromIndex < 0) {
            return;
        }
        if (fromIndex >= s.length()) {
            return;
        }

        int queryStringLength = s.contains("#") ? s.indexOf("#") : s.length();

        String name = null;
        int pos = fromIndex; // Beginning of the unprocessed region
        int i;       // End of the unprocessed region
        char c = 0;  // Current character
        for (i = fromIndex; i < queryStringLength; i++) {
            c = s.charAt(i);
            if (c == '=' && name == null) {
                if (pos != i) {
                    name = decodeComponent(s.substring(pos, i));
                }
                pos = i + 1;
            } else if (c == '&' || c == ';') {
                if (name == null && pos != i) {
                    // We haven't seen an `=' so far but moved forward.
                    // Must be a param of the form '&a&' so add it with
                    // an empty value.
                    addParam(params, decodeComponent(s.substring(pos, i)), "");
                } else if (name != null) {
                    addParam(params, name, decodeComponent(s.substring(pos, i)));
                    name = null;
                }
                pos = i + 1;
            }
        }

        if (pos != i) {  // Are there characters we haven't dealt with?
            if (name == null) {     // Yes and we haven't seen any `='.
                addParam(params, decodeComponent(s.substring(pos, i)), "");
            } else {                // Yes and this must be the last value.
                addParam(params, name, decodeComponent(s.substring(pos, i)));
            }
        } else if (name != null) {  // Have we seen a name without value?
            addParam(params, name, "");
        }
    }

    private static void addParam(Map<String, String> params, String name, String value) {
        System.out.println("org.elasticsearch.rest_RestUtil_addParam(Map<String, String> params, String name, String value)");
        System.out.println("执行addParam函数之前的params的值:"+params);
        System.out.println("name is"+name);
        System.out.println("value is"+value);
        params.put(name, value);
        System.out.println("执行addParam函数之后的params的值:"+params);
    }

    /**
     * Decodes a bit of an URL encoded by a browser.
     * <p>
     * This is equivalent to calling {@link #decodeComponent(String, Charset)}
     * with the UTF-8 charset (recommended to comply with RFC 3986, Section 2).
     *
     * @param s The string to decode (can be empty).
     * @return The decoded string, or {@code s} if there's nothing to decode.
     *         If the string to decode is {@code null}, returns an empty string.
     * @throws IllegalArgumentException if the string contains a malformed
     *                                  escape sequence.
     */
    public static String decodeComponent(final String s) {
        System.out.println("org.elasticsearch.rest_RestUtil_decodeComponent(final String s)");
        System.out.println(s);
        return decodeComponent(s, StandardCharsets.UTF_8);
    }

    /**
     * Decodes a bit of an URL encoded by a browser.
     * <p>
     * The string is expected to be encoded as per RFC 3986, Section 2.
     * This is the encoding used by JavaScript functions {@code encodeURI}
     * and {@code encodeURIComponent}, but not {@code escape}.  For example
     * in this encoding, &eacute; (in Unicode {@code U+00E9} or in UTF-8
     * {@code 0xC3 0xA9}) is encoded as {@code %C3%A9} or {@code %c3%a9}.
     * <p>
     * This is essentially equivalent to calling
     * <code>{@link java.net.URLDecoder URLDecoder}.{@link
     * java.net.URLDecoder#decode(String, String)}</code>
     * except that it's over 2x faster and generates less garbage for the GC.
     * Actually this function doesn't allocate any memory if there's nothing
     * to decode, the argument itself is returned.
     *
     * @param s       The string to decode (can be empty).
     * @param charset The charset to use to decode the string (should really
     *                be {@link StandardCharsets#UTF_8}.
     * @return The decoded string, or {@code s} if there's nothing to decode.
     *         If the string to decode is {@code null}, returns an empty string.
     * @throws IllegalArgumentException if the string contains a malformed
     *                                  escape sequence.
     */
    public static String decodeComponent(final String s, final Charset charset) {
        System.out.println("org.elasticsearch.rest_RestUtil_decodeComponent(final String s, final Charset charset)");
        System.out.println(s);
        if (s == null) {
            return "";
        }
        final int size = s.length();
        if (!decodingNeeded(s, size)) {
            return s;
        }
        final byte[] buf = new byte[size];
        int pos = decode(s, size, buf);
        return new String(buf, 0, pos, charset);
    }

    @SuppressWarnings("fallthrough")
    private static boolean decodingNeeded(String s, int size) {
        System.out.println("org.elasticsearch.rest_RestUtil_decodingNeeded(String s, int size)");
        System.out.println(s); 
        boolean decodingNeeded = false;
        for (int i = 0; i < size; i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '%':
                    i++;  // We can skip at least one char, e.g. `%%'.
                    // Fall through.
                case '+':
                    decodingNeeded = true;
                    break;
            }
        }
        return decodingNeeded;
    }

    @SuppressWarnings("fallthrough")
    private static int decode(String s, int size, byte[] buf) {
        System.out.println("org.elasticsearch.rest_RestUtil_decode(String s, int size, byte[] buf)");
        
        int pos = 0;  // position in `buf'.
        for (int i = 0; i < size; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '+':
                    buf[pos++] = ' ';  // "+" -> " "
                    break;
                case '%':
                    if (i == size - 1) {
                        throw new IllegalArgumentException("unterminated escape sequence at end of string: " + s);
                    }
                    c = s.charAt(++i);
                    if (c == '%') {
                        buf[pos++] = '%';  // "%%" -> "%"
                        break;
                    } else if (i == size - 1) {
                        throw new IllegalArgumentException("partial escape sequence at end of string: " + s);
                    }
                    c = decodeHexNibble(c);
                    final char c2 = decodeHexNibble(s.charAt(++i));
                    if (c == Character.MAX_VALUE || c2 == Character.MAX_VALUE) {
                        throw new IllegalArgumentException(
                            "invalid escape sequence `%" + s.charAt(i - 1)
                                + s.charAt(i) + "' at index " + (i - 2)
                                + " of: " + s);
                    }
                    c = (char) (c * 16 + c2);
                    // Fall through.
                default:
                    buf[pos++] = (byte) c;
                    break;
            }
        }
        return pos;
    }

    /**
     * Helper to decode half of a hexadecimal number from a string.
     *
     * @param c The ASCII character of the hexadecimal number to decode.
     *          Must be in the range {@code [0-9a-fA-F]}.
     * @return The hexadecimal value represented in the ASCII character
     *         given, or {@link Character#MAX_VALUE} if the character is invalid.
     */
    private static char decodeHexNibble(final char c) {
        System.out.println("org.elasticsearch.rest_RestUtil_decodeHexNibble(final char c)");
        
        if ('0' <= c && c <= '9') {
            return (char) (c - '0');
        } else if ('a' <= c && c <= 'f') {
            return (char) (c - 'a' + 10);
        } else if ('A' <= c && c <= 'F') {
            return (char) (c - 'A' + 10);
        } else {
            return Character.MAX_VALUE;
        }
    }

    /**
     * Determine if CORS setting is a regex
     *
     * @return a corresponding {@link Pattern} if so and o.w. null.
     */
    public static Pattern checkCorsSettingForRegex(String corsSetting) {
        System.out.println("org.elasticsearch.rest_RestUtil_checkCorsSettingForRegex(String corsSetting)");
        
        if (corsSetting == null) {
            return null;
        }
        int len = corsSetting.length();
        boolean isRegex = len > 2 &&  corsSetting.startsWith("/") && corsSetting.endsWith("/");

        if (isRegex) {
            return Pattern.compile(corsSetting.substring(1, corsSetting.length()-1));
        }

        return null;
    }

    /**
     * Return the CORS setting as an array of origins.
     *
     * @param corsSetting the CORS allow origin setting as configured by the user;
     *                    should never pass null, but we check for it anyway.
     * @return an array of origins if set, otherwise {@code null}.
     */
    public static String[] corsSettingAsArray(String corsSetting) {
        System.out.println("org.elasticsearch.rest_RestUtil_corsSettingAsArray(String corsSetting)");
        
        if (Strings.isNullOrEmpty(corsSetting)) {
            return new String[0];
        }
        return Arrays.asList(corsSetting.split(","))
                     .stream()
                     .map(String::trim)
                     .toArray(size -> new String[size]);
    }
}
