package com.pdfcore.font

/**
 * ToUnicode CMap 解析器
 * 基于 PDF 32000-1:2008 标准 9.10.3
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
            val dstLo = match.groupValues[3].toIntOrNull(16) ?: continue
            
            var dstCode = dstLo
            for (srcCode in srcLo..srcHi) {
                map.put(srcCode, String(Character.toChars(dstCode)))
                dstCode++
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
     */
    private fun hexToString(hex: String): String {
        if (hex.isEmpty()) return ""
        
        val sb = StringBuilder()
        var i = 0
        
        // 处理 2 字节或 4 字节序列
        while (i + 3 <= hex.length) {
            val codePoint = hex.substring(i, i + 4).toIntOrNull(16)
            if (codePoint != null) {
                if (Character.isValidCodePoint(codePoint)) {
                    sb.appendCodePoint(codePoint)
                }
            }
            i += 4
        }
        
        // 处理剩余的 2 字节
        if (i + 2 <= hex.length) {
            val codePoint = hex.substring(i, i + 2).toIntOrNull(16)
            if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                sb.appendCodePoint(codePoint)
            }
        }
        
        return sb.toString()
    }
}

/**
 * ToUnicode 映射表
 */
class ToUnicodeMap {
    private val codeToUnicode = mutableMapOf<Int, String>()
    private val unicodeToCode = mutableMapOf<Char, Int>()
    
    fun put(code: Int, unicode: String) {
        codeToUnicode[code] = unicode
        if (unicode.length == 1) {
            unicodeToCode[unicode[0]] = code
        }
    }
    
    fun get(code: Int): Char? {
        val str = codeToUnicode[code]
        return if (str != null && str.isNotEmpty()) str[0] else null
    }
    
    fun getString(code: Int): String? {
        return codeToUnicode[code]
    }
    
    fun getCode(char: Char): Int? {
        return unicodeToCode[char]
    }
    
    fun isEmpty(): Boolean = codeToUnicode.isEmpty()
    
    fun size(): Int = codeToUnicode.size
}
