package com.pdfcore.font

/**
 * ToUnicode CMap 解析器
 * 基于 PDF 32000-1:2008 标准 9.10.3
 * 
 * 支持:
 * - 多字符 Unicode 序列
 * - Surrogate pairs (U+10000 以上的字符，如表情符号)
 * - UTF-16 编码的 CMap 值
 */
object ToUnicodeParser {
    
    /**
     * 解析 ToUnicode CMap
     */
    fun parse(data: ByteArray): ToUnicodeMap {
        val content = String(data, Charsets.ISO_8859_1)
        val map = ToUnicodeMap()
        
        // 解析 bfchar 映射
        parseBfChar(content, map)
        
        // 解析 bfrange 映射
        parseBfRange(content, map)
        
        return map
    }
    
    /**
     * 解析 beginbfchar ... endbfchar
     */
    private fun parseBfChar(content: String, map: ToUnicodeMap) {
        val regex = Regex("""(\d+)\s+beginbfchar\s+(.*?)\s*endbfchar""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in regex.findAll(content)) {
            val entries = match.groupValues[2]
            parseCharEntries(entries, map)
        }
    }
    
    /**
     * 解析 beginbfrange ... endbfrange
     */
    private fun parseBfRange(content: String, map: ToUnicodeMap) {
        val regex = Regex("""(\d+)\s+beginbfrange\s+(.*?)\s*endbfrange""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in regex.findAll(content)) {
            val entries = match.groupValues[2]
            parseRangeEntries(entries, map)
        }
    }
    
    /**
     * 解析 bfchar 条目
     */
    private fun parseCharEntries(content: String, map: ToUnicodeMap) {
        val regex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
        
        for (match in regex.findAll(content)) {
            val srcCode = match.groupValues[1].toIntOrNull(16) ?: continue
            val dstHex = match.groupValues[2]
            val unicode = hexToString(dstHex)
            
            if (unicode.isNotEmpty()) {
                map.put(srcCode, unicode)
            }
        }
    }
    
    /**
     * 解析 bfrange 条目
     */
    private fun parseRangeEntries(content: String, map: ToUnicodeMap) {
        // 格式1: <srcLo> <srcHi> <dstLo>
        val simpleRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
        
        for (match in simpleRegex.findAll(content)) {
            val srcLo = match.groupValues[1].toIntOrNull(16) ?: continue
            val srcHi = match.groupValues[2].toIntOrNull(16) ?: continue
            val dstHex = match.groupValues[3]
            
            // 解析目标起始值，支持 surrogate pairs
            val dstStartCodePoints = hexToCodePoints(dstHex)
            if (dstStartCodePoints.isEmpty()) continue
            
            // 对于范围映射，递增最后一个 code point
            var offset = 0
            for (srcCode in srcLo..srcHi) {
                if (dstStartCodePoints.size == 1) {
                    // 单个 code point，简单递增
                    val codePoint = dstStartCodePoints[0] + offset
                    if (Character.isValidCodePoint(codePoint)) {
                        map.put(srcCode, String(Character.toChars(codePoint)))
                    }
                } else {
                    // 多个 code points，递增最后一个
                    val codePoints = dstStartCodePoints.toMutableList()
                    codePoints[codePoints.lastIndex] = codePoints.last() + offset
                    val sb = StringBuilder()
                    for (cp in codePoints) {
                        if (Character.isValidCodePoint(cp)) {
                            sb.appendCodePoint(cp)
                        }
                    }
                    map.put(srcCode, sb.toString())
                }
                offset++
            }
        }
        
        // 格式2: <srcLo> <srcHi> [<dst1> <dst2> ...]
        val arrayRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in arrayRegex.findAll(content)) {
            val srcLo = match.groupValues[1].toIntOrNull(16) ?: continue
            val srcHi = match.groupValues[2].toIntOrNull(16) ?: continue
            val dstArray = match.groupValues[3]
            
            val dstRegex = Regex("""<([0-9A-Fa-f]+)>""")
            val dstValues = dstRegex.findAll(dstArray).map { hexToString(it.groupValues[1]) }.toList()
            
            var index = 0
            for (srcCode in srcLo..srcHi) {
                if (index < dstValues.size) {
                    map.put(srcCode, dstValues[index])
                }
                index++
            }
        }
    }
    
    /**
     * 将十六进制字符串转换为 Unicode 字符串
     * 
     * 支持:
     * - 2 位十六进制: 单字节值 (00-FF)
     * - 4 位十六进制: BMP 字符 (0000-FFFF) 或 UTF-16 code unit
     * - 8 位十六进制: UTF-16 surrogate pair (如表情符号)
     * - 更长的序列: 多字符映射
     */
    private fun hexToString(hex: String): String {
        if (hex.isEmpty()) return ""
        
        val codePoints = hexToCodePoints(hex)
        val sb = StringBuilder()
        for (cp in codePoints) {
            if (Character.isValidCodePoint(cp)) {
                sb.appendCodePoint(cp)
            }
        }
        return sb.toString()
    }
    
    /**
     * 将十六进制字符串解析为 Unicode code points 列表
     * 正确处理 UTF-16 surrogate pairs
     */
    private fun hexToCodePoints(hex: String): List<Int> {
        if (hex.isEmpty()) return emptyList()
        
        val codePoints = mutableListOf<Int>()
        val utf16Units = mutableListOf<Int>()
        var i = 0
        
        // 首先将十六进制解析为 UTF-16 code units (每 4 位一个)
        while (i + 4 <= hex.length) {
            val unit = hex.substring(i, i + 4).toIntOrNull(16)
            if (unit != null) {
                utf16Units.add(unit)
            }
            i += 4
        }
        
        // 处理剩余的 2 位十六进制（单字节值）
        if (i + 2 <= hex.length && i == 0) {
            // 只有 2 位的情况，直接作为 code point
            val value = hex.substring(i, i + 2).toIntOrNull(16)
            if (value != null) {
                return listOf(value)
            }
        }
        
        // 将 UTF-16 code units 转换为 Unicode code points
        // 处理 surrogate pairs
        var j = 0
        while (j < utf16Units.size) {
            val unit = utf16Units[j]
            
            if (Character.isHighSurrogate(unit.toChar()) && j + 1 < utf16Units.size) {
                val lowUnit = utf16Units[j + 1]
                if (Character.isLowSurrogate(lowUnit.toChar())) {
                    // 这是一个 surrogate pair，组合成完整的 code point
                    val codePoint = Character.toCodePoint(unit.toChar(), lowUnit.toChar())
                    codePoints.add(codePoint)
                    j += 2
                    continue
                }
            }
            
            // 不是 surrogate pair，直接使用
            codePoints.add(unit)
            j++
        }
        
        return codePoints
    }
}

/**
 * ToUnicode 映射表
 * 
 * 支持:
 * - 单字符到 Unicode 的映射
 * - 多字符 Unicode 序列 (如连字、表情符号序列)
 * - Supplementary characters (U+10000 以上)
 * - 双向映射 (code -> unicode 和 unicode -> code)
 */
class ToUnicodeMap {
    private val codeToUnicode = mutableMapOf<Int, String>()
    private val unicodeToCode = mutableMapOf<String, Int>()
    
    /**
     * 添加映射
     */
    fun put(code: Int, unicode: String) {
        codeToUnicode[code] = unicode
        // 支持完整字符串的反向映射
        unicodeToCode[unicode] = code
    }
    
    /**
     * 获取单个字符 (兼容旧 API)
     * 对于 supplementary characters，返回高代理项
     * 建议使用 getString() 获取完整字符串
     */
    fun get(code: Int): Char? {
        val str = codeToUnicode[code]
        return if (str != null && str.isNotEmpty()) str[0] else null
    }
    
    /**
     * 获取完整的 Unicode 字符串
     * 支持多字符序列和 supplementary characters
     */
    fun getString(code: Int): String? {
        return codeToUnicode[code]
    }
    
    /**
     * 获取单个字符的字符码 (兼容旧 API)
     */
    fun getCode(char: Char): Int? {
        return unicodeToCode[char.toString()]
    }
    
    /**
     * 获取完整字符串的字符码
     * 支持多字符序列和 supplementary characters
     */
    fun getCodeForString(unicode: String): Int? {
        return unicodeToCode[unicode]
    }
    
    /**
     * 获取 code point 的字符码
     * 用于处理 supplementary characters
     */
    fun getCodeForCodePoint(codePoint: Int): Int? {
        val str = String(Character.toChars(codePoint))
        return unicodeToCode[str]
    }
    
    fun isEmpty(): Boolean = codeToUnicode.isEmpty()
    
    fun size(): Int = codeToUnicode.size
    
    /**
     * 检查是否包含指定的字符码
     */
    fun containsCode(code: Int): Boolean = codeToUnicode.containsKey(code)
    
    /**
     * 获取所有映射的字符码
     */
    fun getCodes(): Set<Int> = codeToUnicode.keys
}
