package com.pdfcore.font

import com.pdfcore.model.PdfDictionary
import com.pdfcore.model.PdfStream

/**
 * CMap 解析器
 * 基于 PDF 32000-1:2008 标准 9.7.5 和 Adobe CMap 规范
 * 
 * 支持:
 * - codespacerange: 定义有效的字符码范围
 * - bfchar/bfrange: 字符码到 Unicode 的映射 (用于 ToUnicode)
 * - cidchar/cidrange: 字符码到 CID 的映射
 * - notdefchar/notdefrange: 未定义字符的处理
 * - WMode: 水平/垂直书写模式
 */
object CMapParser {
    
    /**
     * 从流数据解析 CMap
     */
    fun parse(data: ByteArray): CMap {
        val content = String(data, Charsets.ISO_8859_1)
        return parseContent(content)
    }
    
    /**
     * 从 PdfStream 解析 CMap
     */
    fun parse(stream: PdfStream, decodeStream: (PdfStream) -> ByteArray): CMap {
        val data = decodeStream(stream)
        val cmap = parse(data)
        
        // 从流字典获取额外信息
        val useCMap = stream.dict.getName("UseCMap")?.name
        if (useCMap != null) {
            // 引用预定义 CMap
            val baseCMap = PredefinedCMaps.get(useCMap)
            if (baseCMap != null) {
                return cmap.withBase(baseCMap)
            }
        }
        
        return cmap
    }
    
    /**
     * 解析 CMap 内容
     */
    private fun parseContent(content: String): CMap {
        val cmap = CMap()
        
        // 解析 CMap 名称
        parseCMapName(content, cmap)
        
        // 解析 CIDSystemInfo
        parseCIDSystemInfo(content, cmap)
        
        // 解析 WMode
        parseWMode(content, cmap)
        
        // 解析 codespacerange
        parseCodespaceRange(content, cmap)
        
        // 解析 cidchar
        parseCidChar(content, cmap)
        
        // 解析 cidrange
        parseCidRange(content, cmap)
        
        // 解析 bfchar (字符码到 Unicode)
        parseBfChar(content, cmap)
        
        // 解析 bfrange (字符码范围到 Unicode)
        parseBfRange(content, cmap)
        
        // 解析 notdefchar
        parseNotdefChar(content, cmap)
        
        // 解析 notdefrange
        parseNotdefRange(content, cmap)
        
        return cmap
    }
    
    /**
     * 解析 CMap 名称
     */
    private fun parseCMapName(content: String, cmap: CMap) {
        val regex = Regex("""/CMapName\s*/(\S+)\s+def""")
        val match = regex.find(content)
        if (match != null) {
            cmap.name = match.groupValues[1]
        }
    }
    
    /**
     * 解析 CIDSystemInfo
     */
    private fun parseCIDSystemInfo(content: String, cmap: CMap) {
        // 查找 CIDSystemInfo 字典
        val regex = Regex("""/CIDSystemInfo\s*<<\s*(.*?)\s*>>\s*def""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(content) ?: return
        
        val dictContent = match.groupValues[1]
        
        // 解析 Registry
        val registryRegex = Regex("""/Registry\s*\(([^)]*)\)""")
        val registryMatch = registryRegex.find(dictContent)
        if (registryMatch != null) {
            cmap.registry = registryMatch.groupValues[1]
        }
        
        // 解析 Ordering
        val orderingRegex = Regex("""/Ordering\s*\(([^)]*)\)""")
        val orderingMatch = orderingRegex.find(dictContent)
        if (orderingMatch != null) {
            cmap.ordering = orderingMatch.groupValues[1]
        }
        
        // 解析 Supplement
        val supplementRegex = Regex("""/Supplement\s+(\d+)""")
        val supplementMatch = supplementRegex.find(dictContent)
        if (supplementMatch != null) {
            cmap.supplement = supplementMatch.groupValues[1].toIntOrNull() ?: 0
        }
    }
    
    /**
     * 解析 WMode (书写模式)
     */
    private fun parseWMode(content: String, cmap: CMap) {
        val regex = Regex("""/WMode\s+(\d+)\s+def""")
        val match = regex.find(content)
        if (match != null) {
            cmap.wMode = match.groupValues[1].toIntOrNull() ?: 0
        }
    }
    
    /**
     * 解析 codespacerange
     */
    private fun parseCodespaceRange(content: String, cmap: CMap) {
        val regex = Regex("""(\d+)\s+begincodespacerange\s+(.*?)\s*endcodespacerange""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in regex.findAll(content)) {
            val entries = match.groupValues[2]
            val rangeRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
            
            for (rangeMatch in rangeRegex.findAll(entries)) {
                val start = rangeMatch.groupValues[1]
                val end = rangeMatch.groupValues[2]
                cmap.addCodespaceRange(
                    CodespaceRange(
                        startBytes = hexToBytes(start),
                        endBytes = hexToBytes(end)
                    )
                )
            }
        }
    }
    
    /**
     * 解析 cidchar
     */
    private fun parseCidChar(content: String, cmap: CMap) {
        val regex = Regex("""(\d+)\s+begincidchar\s+(.*?)\s*endcidchar""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in regex.findAll(content)) {
            val entries = match.groupValues[2]
            val charRegex = Regex("""<([0-9A-Fa-f]+)>\s+(\d+)""")
            
            for (charMatch in charRegex.findAll(entries)) {
                val code = charMatch.groupValues[1].toIntOrNull(16) ?: continue
                val cid = charMatch.groupValues[2].toIntOrNull() ?: continue
                cmap.addCidMapping(code, cid)
            }
        }
    }
    
    /**
     * 解析 cidrange
     */
    private fun parseCidRange(content: String, cmap: CMap) {
        val regex = Regex("""(\d+)\s+begincidrange\s+(.*?)\s*endcidrange""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in regex.findAll(content)) {
            val entries = match.groupValues[2]
            val rangeRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s+(\d+)""")
            
            for (rangeMatch in rangeRegex.findAll(entries)) {
                val srcLo = rangeMatch.groupValues[1].toIntOrNull(16) ?: continue
                val srcHi = rangeMatch.groupValues[2].toIntOrNull(16) ?: continue
                val cidStart = rangeMatch.groupValues[3].toIntOrNull() ?: continue
                
                var cid = cidStart
                for (code in srcLo..srcHi) {
                    cmap.addCidMapping(code, cid++)
                }
            }
        }
    }
    
    /**
     * 解析 bfchar
     */
    private fun parseBfChar(content: String, cmap: CMap) {
        val regex = Regex("""(\d+)\s+beginbfchar\s+(.*?)\s*endbfchar""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in regex.findAll(content)) {
            val entries = match.groupValues[2]
            val charRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
            
            for (charMatch in charRegex.findAll(entries)) {
                val code = charMatch.groupValues[1].toIntOrNull(16) ?: continue
                val unicode = hexToUnicode(charMatch.groupValues[2])
                if (unicode.isNotEmpty()) {
                    cmap.addUnicodeMapping(code, unicode)
                }
            }
        }
    }
    
    /**
     * 解析 bfrange
     */
    private fun parseBfRange(content: String, cmap: CMap) {
        val regex = Regex("""(\d+)\s+beginbfrange\s+(.*?)\s*endbfrange""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in regex.findAll(content)) {
            val entries = match.groupValues[2]
            
            // 格式1: <srcLo> <srcHi> <dstLo>
            val simpleRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
            for (rangeMatch in simpleRegex.findAll(entries)) {
                val srcLo = rangeMatch.groupValues[1].toIntOrNull(16) ?: continue
                val srcHi = rangeMatch.groupValues[2].toIntOrNull(16) ?: continue
                val dstStart = rangeMatch.groupValues[3].toIntOrNull(16) ?: continue
                
                var dstCode = dstStart
                for (srcCode in srcLo..srcHi) {
                    if (Character.isValidCodePoint(dstCode)) {
                        cmap.addUnicodeMapping(srcCode, String(Character.toChars(dstCode)))
                    }
                    dstCode++
                }
            }
            
            // 格式2: <srcLo> <srcHi> [<dst1> <dst2> ...]
            val arrayRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            for (rangeMatch in arrayRegex.findAll(entries)) {
                val srcLo = rangeMatch.groupValues[1].toIntOrNull(16) ?: continue
                val srcHi = rangeMatch.groupValues[2].toIntOrNull(16) ?: continue
                val dstArray = rangeMatch.groupValues[3]
                
                val dstRegex = Regex("""<([0-9A-Fa-f]+)>""")
                val dstValues = dstRegex.findAll(dstArray).map { hexToUnicode(it.groupValues[1]) }.toList()
                
                var index = 0
                for (srcCode in srcLo..srcHi) {
                    if (index < dstValues.size && dstValues[index].isNotEmpty()) {
                        cmap.addUnicodeMapping(srcCode, dstValues[index])
                    }
                    index++
                }
            }
        }
    }
    
    /**
     * 解析 notdefchar
     */
    private fun parseNotdefChar(content: String, cmap: CMap) {
        val regex = Regex("""(\d+)\s+beginnotdefchar\s+(.*?)\s*endnotdefchar""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in regex.findAll(content)) {
            val entries = match.groupValues[2]
            val charRegex = Regex("""<([0-9A-Fa-f]+)>\s+(\d+)""")
            
            for (charMatch in charRegex.findAll(entries)) {
                val code = charMatch.groupValues[1].toIntOrNull(16) ?: continue
                val cid = charMatch.groupValues[2].toIntOrNull() ?: continue
                cmap.addNotdefMapping(code, cid)
            }
        }
    }
    
    /**
     * 解析 notdefrange
     */
    private fun parseNotdefRange(content: String, cmap: CMap) {
        val regex = Regex("""(\d+)\s+beginnotdefrange\s+(.*?)\s*endnotdefrange""", RegexOption.DOT_MATCHES_ALL)
        
        for (match in regex.findAll(content)) {
            val entries = match.groupValues[2]
            val rangeRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s+(\d+)""")
            
            for (rangeMatch in rangeRegex.findAll(entries)) {
                val srcLo = rangeMatch.groupValues[1].toIntOrNull(16) ?: continue
                val srcHi = rangeMatch.groupValues[2].toIntOrNull(16) ?: continue
                val cid = rangeMatch.groupValues[3].toIntOrNull() ?: continue
                
                for (code in srcLo..srcHi) {
                    cmap.addNotdefMapping(code, cid)
                }
            }
        }
    }
    
    /**
     * 十六进制字符串转字节数组
     */
    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
    
    /**
     * 十六进制字符串转 Unicode 字符串
     */
    private fun hexToUnicode(hex: String): String {
        if (hex.isEmpty()) return ""
        
        val sb = StringBuilder()
        val utf16Units = mutableListOf<Int>()
        var i = 0
        
        // 解析为 UTF-16 code units
        while (i + 4 <= hex.length) {
            val unit = hex.substring(i, i + 4).toIntOrNull(16)
            if (unit != null) {
                utf16Units.add(unit)
            }
            i += 4
        }
        
        // 处理剩余的 2 位
        if (i + 2 <= hex.length && utf16Units.isEmpty()) {
            val value = hex.substring(i, i + 2).toIntOrNull(16)
            if (value != null && Character.isValidCodePoint(value)) {
                return String(Character.toChars(value))
            }
        }
        
        // 处理 surrogate pairs
        var j = 0
        while (j < utf16Units.size) {
            val unit = utf16Units[j]
            
            if (Character.isHighSurrogate(unit.toChar()) && j + 1 < utf16Units.size) {
                val lowUnit = utf16Units[j + 1]
                if (Character.isLowSurrogate(lowUnit.toChar())) {
                    val codePoint = Character.toCodePoint(unit.toChar(), lowUnit.toChar())
                    sb.appendCodePoint(codePoint)
                    j += 2
                    continue
                }
            }
            
            if (Character.isValidCodePoint(unit)) {
                sb.appendCodePoint(unit)
            }
            j++
        }
        
        return sb.toString()
    }
}

/**
 * CMap 数据结构
 */
class CMap {
    var name: String = ""
    var registry: String = "Adobe"
    var ordering: String = "Identity"
    var supplement: Int = 0
    var wMode: Int = 0  // 0 = 水平, 1 = 垂直
    
    private val codespaceRanges = mutableListOf<CodespaceRange>()
    private val cidMappings = mutableMapOf<Int, Int>()  // code -> CID
    private val unicodeMappings = mutableMapOf<Int, String>()  // code -> Unicode
    private val notdefMappings = mutableMapOf<Int, Int>()  // code -> CID
    
    private var baseCMap: CMap? = null
    
    /**
     * 添加 codespace 范围
     */
    fun addCodespaceRange(range: CodespaceRange) {
        codespaceRanges.add(range)
    }
    
    /**
     * 添加 CID 映射
     */
    fun addCidMapping(code: Int, cid: Int) {
        cidMappings[code] = cid
    }
    
    /**
     * 添加 Unicode 映射
     */
    fun addUnicodeMapping(code: Int, unicode: String) {
        unicodeMappings[code] = unicode
    }
    
    /**
     * 添加 notdef 映射
     */
    fun addNotdefMapping(code: Int, cid: Int) {
        notdefMappings[code] = cid
    }
    
    /**
     * 获取 CID
     */
    fun getCid(code: Int): Int? {
        return cidMappings[code] ?: baseCMap?.getCid(code)
    }
    
    /**
     * 获取 Unicode 字符串
     */
    fun getUnicode(code: Int): String? {
        return unicodeMappings[code] ?: baseCMap?.getUnicode(code)
    }
    
    /**
     * 获取 notdef CID
     */
    fun getNotdefCid(code: Int): Int? {
        return notdefMappings[code] ?: baseCMap?.getNotdefCid(code)
    }
    
    /**
     * 检查字符码是否在有效的 codespace 范围内
     */
    fun isValidCode(codeBytes: ByteArray): Boolean {
        if (codespaceRanges.isEmpty()) {
            // 没有定义 codespace，假设所有值都有效
            return true
        }
        
        for (range in codespaceRanges) {
            if (range.contains(codeBytes)) {
                return true
            }
        }
        
        return baseCMap?.isValidCode(codeBytes) ?: false
    }
    
    /**
     * 获取指定字节长度的字符码范围
     */
    fun getCodeLengths(): Set<Int> {
        val lengths = mutableSetOf<Int>()
        for (range in codespaceRanges) {
            lengths.add(range.startBytes.size)
        }
        if (baseCMap != null) {
            lengths.addAll(baseCMap!!.getCodeLengths())
        }
        return lengths
    }
    
    /**
     * 解码字节序列为字符串
     */
    fun decode(bytes: ByteArray): String {
        val sb = StringBuilder()
        val codeLengths = getCodeLengths().sortedDescending()
        
        var i = 0
        while (i < bytes.size) {
            var matched = false
            
            // 尝试不同长度的字符码
            for (len in codeLengths) {
                if (i + len > bytes.size) continue
                
                val codeBytes = bytes.copyOfRange(i, i + len)
                if (!isValidCode(codeBytes)) continue
                
                val code = bytesToCode(codeBytes)
                val unicode = getUnicode(code)
                
                if (unicode != null) {
                    sb.append(unicode)
                    i += len
                    matched = true
                    break
                }
            }
            
            if (!matched) {
                // 回退：尝试直接将字节作为 code point
                val code = bytes[i].toInt() and 0xFF
                val unicode = getUnicode(code)
                if (unicode != null) {
                    sb.append(unicode)
                } else {
                    sb.append(code.toChar())
                }
                i++
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 字节数组转字符码
     */
    private fun bytesToCode(bytes: ByteArray): Int {
        var code = 0
        for (b in bytes) {
            code = (code shl 8) or (b.toInt() and 0xFF)
        }
        return code
    }
    
    /**
     * 创建带有基础 CMap 的新实例
     */
    fun withBase(base: CMap): CMap {
        this.baseCMap = base
        return this
    }
    
    /**
     * 检查是否为 Identity CMap
     */
    fun isIdentity(): Boolean {
        return name.startsWith("Identity") || ordering == "Identity"
    }
    
    /**
     * 获取 CIDSystemInfo 字符串
     */
    fun getCIDSystemInfo(): String {
        return "$registry-$ordering-$supplement"
    }
}

/**
 * Codespace 范围
 */
data class CodespaceRange(
    val startBytes: ByteArray,
    val endBytes: ByteArray
) {
    /**
     * 检查字节序列是否在范围内
     */
    fun contains(codeBytes: ByteArray): Boolean {
        if (codeBytes.size != startBytes.size) return false
        
        for (i in codeBytes.indices) {
            val b = codeBytes[i].toInt() and 0xFF
            val start = startBytes[i].toInt() and 0xFF
            val end = endBytes[i].toInt() and 0xFF
            
            if (b < start || b > end) return false
        }
        
        return true
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CodespaceRange) return false
        return startBytes.contentEquals(other.startBytes) && endBytes.contentEquals(other.endBytes)
    }
    
    override fun hashCode(): Int {
        return 31 * startBytes.contentHashCode() + endBytes.contentHashCode()
    }
}
