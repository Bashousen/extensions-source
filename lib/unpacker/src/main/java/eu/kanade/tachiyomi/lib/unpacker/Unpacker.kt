package eu.kanade.tachiyomi.lib.unpacker

/*
 * Copyright (C) The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * Helper class to unpack JavaScript code compressed by [packer](http://dean.edwards.name/packer/).
 *
 * Source code of packer can be found [here](https://github.com/evanw/packer/blob/master/packer.js).
 */
object Unpacker {

    /**
     * Unpacks JavaScript code compressed by packer.
     *
     * Specify [left] and [right] to unpack only the data between them.
     *
     * Note: single quotes `\'` in the data will be replaced with double quotes `"`.
     */
    fun unpack(script: String, left: String? = null, right: String? = null): String =
        unpack(SubstringExtractor(script), left, right)

    /**
     * Unpacks JavaScript code compressed by packer.
     *
     * Specify [left] and [right] to unpack only the data between them.
     *
     * Note: single quotes `\'` in the data will be replaced with double quotes `"`.
     */
    fun unpack(script: SubstringExtractor, left: String? = null, right: String? = null): String {
        val packed = script
            .substringBetween("}('", ".split('|'),0,{}))")
            .replace("\\'", "\"")

        val radix = Regex("""',(\d+),(\d+),'""").findAll(packed).lastOrNull()
            ?.groupValues?.get(1)?.toIntOrNull() ?: 62

        val parser = SubstringExtractor(packed)
        val data: String
        if (left != null && right != null) {
            data = parser.substringBetween(left, right)
            parser.skipOver("',")
        } else {
            data = parser.substringBefore("',")
        }
        if (data.isEmpty()) return ""

        val dictionary = parser.substringBetween("'", "'").split("|")
        val size = dictionary.size

        return wordRegex.replace(data) {
            val key = it.value
            val index = parseRadix(key, radix)
            if (index >= size) return@replace key
            dictionary[index].ifEmpty { key }
        }
    }

    private val wordRegex by lazy { Regex("""[0-9A-Za-z]+""") }

    private fun parseRadix(str: String, radix: Int): Int {
        var result = 0
        for (ch in str.toCharArray()) {
            result = result * radix + when {
                ch.code <= '9'.code -> { // 0-9
                    ch.code - '0'.code
                }

                ch.code >= 'a'.code -> { // a-z
                    // ch - 'a' + 10
                    ch.code - ('a'.code - 10)
                }

                else -> { // A-Z
                    // ch - 'A' + 36
                    ch.code - ('A'.code - 36)
                }
            }
        }
        return result
    }
}
