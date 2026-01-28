package com.pdfcore.font

import com.pdfcore.model.*
import com.pdfcore.parser.StreamFilters

/**
 * PDF 字体处理器
 * 基于 PDF 32000-1:2008 标准 9 章
 * 
 * 负责:
 * - 解析字体字典
 * - 字符编码转换
 * - Unicode 映射
 */
class FontHandler(private val document: PdfDocument) {
    
    // 字体缓存
    private val fontCache = mutableMapOf<String, PdfFont>()
    
    /**
     * 获取或创建字体对象
     */
    fun getFont(fontDict: PdfDictionary): PdfFont {
        val key = System.identityHashCode(fontDict).toString()
        return fontCache.getOrPut(key) { parseFont(fontDict) }
    }
    
    /**
     * 解析字体字典
     */
    private fun parseFont(fontDict: PdfDictionary): PdfFont {
        val subtype = fontDict.getNameValue("Subtype") ?: "Type1"
        val baseFont = fontDict.getNameValue("BaseFont") ?: ""
        val encoding = parseEncoding(fontDict)
        val toUnicode = parseToUnicode(fontDict)
        val widths = parseWidths(fontDict)
        
        return when (subtype) {
            "Type0" -> parseType0Font(fontDict, baseFont, encoding, toUnicode)
            "Type1", "MMType1" -> SimpleFont(subtype, baseFont, encoding, toUnicode, widths)
            "TrueType" -> SimpleFont(subtype, baseFont, encoding, toUnicode, widths)
            "Type3" -> SimpleFont(subtype, baseFont, encoding, toUnicode, widths)
            else -> SimpleFont(subtype, baseFont, encoding, toUnicode, widths)
        }
    }
    
    /**
     * 解析 Type0 复合字体
     */
    private fun parseType0Font(
        fontDict: PdfDictionary,
        baseFont: String,
        encoding: FontEncoding,
        toUnicode: ToUnicodeMap?
    ): Type0Font {
        // 获取 DescendantFonts
        val descendantFonts = fontDict.getArray("DescendantFonts")
        val cidFont = if (descendantFonts != null && descendantFonts.isNotEmpty()) {
            val cidFontRef = descendantFonts[0]
            val cidFontDict = when (cidFontRef) {
                is PdfDictionary -> cidFontRef
                is PdfIndirectRef -> document.getObject(cidFontRef) as? PdfDictionary
                else -> null
            }
            cidFontDict?.let { parseCIDFont(it) }
        } else null
        
        return Type0Font(baseFont, encoding, toUnicode, cidFont)
    }
    
    /**
     * 解析 CIDFont
     */
    private fun parseCIDFont(fontDict: PdfDictionary): CIDFont {
        val subtype = fontDict.getNameValue("Subtype") ?: "CIDFontType0"
        val baseFont = fontDict.getNameValue("BaseFont") ?: ""
        
        // 获取默认宽度
        val dw = fontDict.getInt("DW") ?: 1000
        
        // 获取宽度数组
        val widthsArray = fontDict.getArray("W")
        val widths = parseCIDWidths(widthsArray)
        
        // 获取 CIDToGIDMap
        val cidToGidMap = when (val map = fontDict["CIDToGIDMap"]) {
            is PdfName -> if (map.name == "Identity") CIDToGIDMap.Identity else CIDToGIDMap.Identity
            is PdfIndirectRef -> {
                val stream = document.getObject(map) as? PdfStream
                if (stream != null) parseCIDToGIDMap(stream) else CIDToGIDMap.Identity
            }
            is PdfStream -> parseCIDToGIDMap(map)
            else -> CIDToGIDMap.Identity
        }
        
        return CIDFont(subtype, baseFont, dw, widths, cidToGidMap)
    }
    
    /**
     * 解析 CID 宽度数组
     */
    private fun parseCIDWidths(array: PdfArray?): Map<Int, Int> {
        if (array == null) return emptyMap()
        
        val widths = mutableMapOf<Int, Int>()
        var i = 0
        
        while (i < array.size) {
            val first = array.getInt(i) ?: break
            i++
            
            if (i >= array.size) break
            
            val second = array[i]
            when (second) {
                is PdfArray -> {
                    // 格式: c [w1 w2 ...]
                    var cid = first
                    for (w in second) {
                        if (w is PdfNumber) {
                            widths[cid++] = w.toInt()
                        }
                    }
                    i++
                }
                is PdfNumber -> {
                    // 格式: c_first c_last w
                    val last = second.toInt()
                    i++
                    if (i >= array.size) break
                    val width = array.getInt(i) ?: break
                    i++
                    
                    for (cid in first..last) {
                        widths[cid] = width
                    }
                }
                else -> i++
            }
        }
        
        return widths
    }
    
    /**
     * 解析 CIDToGIDMap 流
     */
    private fun parseCIDToGIDMap(stream: PdfStream): CIDToGIDMap {
        val data = decodeStream(stream)
        val map = mutableMapOf<Int, Int>()
        
        // 每个 CID 对应 2 字节的 GID
        for (i in 0 until data.size / 2) {
            val gid = ((data[i * 2].toInt() and 0xFF) shl 8) or (data[i * 2 + 1].toInt() and 0xFF)
            if (gid != 0) {
                map[i] = gid
            }
        }
        
        return CIDToGIDMap.Mapped(map)
    }
    
    /**
     * 解析编码
     */
    private fun parseEncoding(fontDict: PdfDictionary): FontEncoding {
        return when (val enc = fontDict["Encoding"]) {
            is PdfName -> FontEncoding.Named(enc.name)
            is PdfDictionary -> {
                val baseEncoding = enc.getNameValue("BaseEncoding") ?: "WinAnsiEncoding"
                val differences = parseDifferences(enc.getArray("Differences"))
                FontEncoding.Custom(baseEncoding, differences)
            }
            is PdfIndirectRef -> {
                val encObj = document.getObject(enc)
                when (encObj) {
                    is PdfName -> FontEncoding.Named(encObj.name)
                    is PdfDictionary -> {
                        val baseEncoding = encObj.getNameValue("BaseEncoding") ?: "WinAnsiEncoding"
                        val differences = parseDifferences(encObj.getArray("Differences"))
                        FontEncoding.Custom(baseEncoding, differences)
                    }
                    else -> FontEncoding.Named("WinAnsiEncoding")
                }
            }
            else -> FontEncoding.Named("WinAnsiEncoding")
        }
    }
    
    /**
     * 解析 Differences 数组
     */
    private fun parseDifferences(array: PdfArray?): Map<Int, String> {
        if (array == null) return emptyMap()
        
        val differences = mutableMapOf<Int, String>()
        var code = 0
        
        for (item in array) {
            when (item) {
                is PdfNumber -> code = item.toInt()
                is PdfName -> {
                    differences[code] = item.name
                    code++
                }
                else -> { /* ignore other types */ }
            }
        }
        
        return differences
    }
    
    /**
     * 解析 ToUnicode CMap
     */
    private fun parseToUnicode(fontDict: PdfDictionary): ToUnicodeMap? {
        val toUnicode = fontDict["ToUnicode"] ?: return null
        
        val stream = when (toUnicode) {
            is PdfStream -> toUnicode
            is PdfIndirectRef -> document.getObject(toUnicode) as? PdfStream
            else -> null
        } ?: return null
        
        return ToUnicodeParser.parse(decodeStream(stream))
    }
    
    /**
     * 解析字体宽度
     */
    private fun parseWidths(fontDict: PdfDictionary): FontWidths {
        val firstChar = fontDict.getInt("FirstChar") ?: 0
        val lastChar = fontDict.getInt("LastChar") ?: 255
        val widthsArray = fontDict.getArray("Widths")
        
        val widths = mutableMapOf<Int, Int>()
        
        if (widthsArray != null) {
            for (i in widthsArray.indices) {
                val code = firstChar + i
                val width = widthsArray.getInt(i) ?: 0
                widths[code] = width
            }
        }
        
        // 获取默认宽度
        val missingWidth = fontDict.getDictionary("FontDescriptor")
            ?.getInt("MissingWidth") ?: 0
        
        return FontWidths(widths, missingWidth)
    }
    
    /**
     * 解码流
     */
    private fun decodeStream(stream: PdfStream): ByteArray {
        val filters = stream.getFilters()
        if (filters.isEmpty()) return stream.rawData
        
        var data = stream.rawData
        for ((index, filter) in filters.withIndex()) {
            val params = stream.getDecodeParams(index)
            data = StreamFilters.decode(data, filter, params)
        }
        return data
    }
}

/**
 * PDF 字体基类
 */
sealed class PdfFont {
    abstract val subtype: String
    abstract val baseFont: String
    abstract val encoding: FontEncoding
    abstract val toUnicode: ToUnicodeMap?
    
    /**
     * 将字节序列转换为 Unicode 文本
     */
    abstract fun decode(bytes: ByteArray): String
    
    /**
     * 将 Unicode 文本转换为字节序列
     */
    abstract fun encode(text: String): ByteArray?
    
    /**
     * 获取字符宽度
     */
    abstract fun getWidth(code: Int): Int
}

/**
 * 简单字体 (Type1, TrueType, Type3)
 */
class SimpleFont(
    override val subtype: String,
    override val baseFont: String,
    override val encoding: FontEncoding,
    override val toUnicode: ToUnicodeMap?,
    private val widths: FontWidths
) : PdfFont() {
    
    override fun decode(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            val char = toUnicode?.get(code)
                ?: encoding.toUnicode(code)
                ?: code.toChar()
            sb.append(char)
        }
        return sb.toString()
    }
    
    override fun encode(text: String): ByteArray? {
        val bytes = mutableListOf<Byte>()
        for (char in text) {
            val code = toUnicode?.getCode(char)
                ?: encoding.fromUnicode(char)
                ?: return null
            bytes.add(code.toByte())
        }
        return bytes.toByteArray()
    }
    
    override fun getWidth(code: Int): Int {
        return widths.getWidth(code)
    }
}

/**
 * Type0 复合字体
 */
class Type0Font(
    override val baseFont: String,
    override val encoding: FontEncoding,
    override val toUnicode: ToUnicodeMap?,
    val cidFont: CIDFont?
) : PdfFont() {
    
    override val subtype: String = "Type0"
    
    override fun decode(bytes: ByteArray): String {
        // 对于复合字体，通常使用 2 字节编码
        if (toUnicode != null) {
            val sb = StringBuilder()
            var i = 0
            while (i < bytes.size) {
                // 尝试 2 字节
                if (i + 1 < bytes.size) {
                    val code2 = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
                    val char2 = toUnicode.get(code2)
                    if (char2 != null) {
                        sb.append(char2)
                        i += 2
                        continue
                    }
                }
                // 尝试 1 字节
                val code1 = bytes[i].toInt() and 0xFF
                val char1 = toUnicode.get(code1) ?: code1.toChar()
                sb.append(char1)
                i++
            }
            return sb.toString()
        }
        
        // 没有 ToUnicode，尝试使用 Identity 编码
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < bytes.size) {
            val code = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            sb.append(code.toChar())
            i += 2
        }
        return sb.toString()
    }
    
    override fun encode(text: String): ByteArray? {
        if (toUnicode == null) return null
        
        val bytes = mutableListOf<Byte>()
        for (char in text) {
            val code = toUnicode.getCode(char) ?: return null
            if (code > 255) {
                bytes.add((code shr 8).toByte())
                bytes.add((code and 0xFF).toByte())
            } else {
                bytes.add(code.toByte())
            }
        }
        return bytes.toByteArray()
    }
    
    override fun getWidth(code: Int): Int {
        return cidFont?.getWidth(code) ?: 1000
    }
}

/**
 * CID 字体
 */
class CIDFont(
    val subtype: String,
    val baseFont: String,
    val defaultWidth: Int,
    val widths: Map<Int, Int>,
    val cidToGid: CIDToGIDMap
) {
    fun getWidth(cid: Int): Int {
        return widths[cid] ?: defaultWidth
    }
}

/**
 * CID 到 GID 映射
 */
sealed class CIDToGIDMap {
    object Identity : CIDToGIDMap()
    data class Mapped(val map: Map<Int, Int>) : CIDToGIDMap()
    
    fun getGID(cid: Int): Int {
        return when (this) {
            is Identity -> cid
            is Mapped -> map[cid] ?: cid
        }
    }
}

/**
 * 字体编码
 */
sealed class FontEncoding {
    data class Named(val name: String) : FontEncoding()
    data class Custom(val baseEncoding: String, val differences: Map<Int, String>) : FontEncoding()
    
    /**
     * 字符码转 Unicode
     */
    fun toUnicode(code: Int): Char? {
        return when (this) {
            is Named -> StandardEncodings.toUnicode(name, code)
            is Custom -> {
                val glyphName = differences[code]
                if (glyphName != null) {
                    GlyphList.toUnicode(glyphName)
                } else {
                    StandardEncodings.toUnicode(baseEncoding, code)
                }
            }
        }
    }
    
    /**
     * Unicode 转字符码
     */
    fun fromUnicode(char: Char): Int? {
        return when (this) {
            is Named -> StandardEncodings.fromUnicode(name, char)
            is Custom -> {
                // 先检查 differences
                for ((code, glyphName) in differences) {
                    if (GlyphList.toUnicode(glyphName) == char) {
                        return code
                    }
                }
                // 再检查基础编码
                StandardEncodings.fromUnicode(baseEncoding, char)
            }
        }
    }
}

/**
 * 字体宽度
 */
class FontWidths(
    private val widths: Map<Int, Int>,
    private val missingWidth: Int
) {
    fun getWidth(code: Int): Int {
        return widths[code] ?: missingWidth
    }
}
