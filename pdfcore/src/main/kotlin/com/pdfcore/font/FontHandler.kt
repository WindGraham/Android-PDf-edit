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
 * - 嵌入字体数据提取
 */
class FontHandler(private val document: PdfDocument) {
    
    // 字体缓存
    private val fontCache = mutableMapOf<String, PdfFont>()
    
    // 嵌入字体数据缓存
    private val embeddedFontCache = mutableMapOf<String, EmbeddedFontData>()
    
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
        val subtype = fontDict.getName("Subtype")?.name ?: "Type1"
        val baseFont = fontDict.getName("BaseFont")?.name ?: ""
        val encoding = parseEncoding(fontDict, baseFont)
        val toUnicode = parseToUnicode(fontDict)
        val widths = parseWidths(fontDict)
        
        // 尝试提取嵌入字体数据
        val embeddedFont = parseEmbeddedFont(fontDict, baseFont)
        
        return when (subtype) {
            "Type0" -> parseType0Font(fontDict, baseFont, encoding, toUnicode)
            "Type1", "MMType1" -> SimpleFont(subtype, baseFont, encoding, toUnicode, widths, embeddedFont)
            "TrueType" -> SimpleFont(subtype, baseFont, encoding, toUnicode, widths, embeddedFont)
            "Type3" -> SimpleFont(subtype, baseFont, encoding, toUnicode, widths, embeddedFont)
            else -> SimpleFont(subtype, baseFont, encoding, toUnicode, widths, embeddedFont)
        }
    }
    
    /**
     * 解析嵌入字体数据
     * PDF 32000-1:2008 9.9
     */
    private fun parseEmbeddedFont(fontDict: PdfDictionary, baseFont: String): EmbeddedFontData? {
        // 检查缓存
        if (embeddedFontCache.containsKey(baseFont)) {
            return embeddedFontCache[baseFont]
        }
        
        // 获取 FontDescriptor
        val fontDescriptor = when (val fd = fontDict["FontDescriptor"]) {
            is PdfDictionary -> fd
            is PdfIndirectRef -> document.getObject(fd) as? PdfDictionary
            else -> null
        } ?: return null
        
        // 尝试获取嵌入的字体程序
        val fontData = extractFontProgram(fontDescriptor)
        
        if (fontData != null) {
            embeddedFontCache[baseFont] = fontData
        }
        
        return fontData
    }
    
    /**
     * 从 FontDescriptor 提取字体程序
     */
    private fun extractFontProgram(fontDescriptor: PdfDictionary): EmbeddedFontData? {
        // FontFile - Type 1 字体程序 (PFB 格式)
        val fontFile = fontDescriptor["FontFile"]
        if (fontFile != null) {
            val stream = resolveStream(fontFile)
            if (stream != null) {
                val length1 = stream.dict.getNumber("Length1")?.toInt() ?: 0
                val length2 = stream.dict.getNumber("Length2")?.toInt() ?: 0
                val length3 = stream.dict.getNumber("Length3")?.toInt() ?: 0
                val data = decodeStream(stream)
                return EmbeddedFontData(
                    type = EmbeddedFontType.TYPE1,
                    data = data,
                    length1 = length1,
                    length2 = length2,
                    length3 = length3
                )
            }
        }
        
        // FontFile2 - TrueType 字体程序 (TTF 格式)
        val fontFile2 = fontDescriptor["FontFile2"]
        if (fontFile2 != null) {
            val stream = resolveStream(fontFile2)
            if (stream != null) {
                val length1 = stream.dict.getNumber("Length1")?.toInt() ?: 0
                val data = decodeStream(stream)
                return EmbeddedFontData(
                    type = EmbeddedFontType.TRUETYPE,
                    data = data,
                    length1 = length1
                )
            }
        }
        
        // FontFile3 - 可以是 Type1C, CIDFontType0C, 或 OpenType
        val fontFile3 = fontDescriptor["FontFile3"]
        if (fontFile3 != null) {
            val stream = resolveStream(fontFile3)
            if (stream != null) {
                val subtype = stream.dict.getName("Subtype")?.name
                val data = decodeStream(stream)
                val fontType = when (subtype) {
                    "Type1C" -> EmbeddedFontType.TYPE1C
                    "CIDFontType0C" -> EmbeddedFontType.CID_TYPE0C
                    "OpenType" -> EmbeddedFontType.OPENTYPE
                    else -> EmbeddedFontType.UNKNOWN
                }
                return EmbeddedFontData(
                    type = fontType,
                    data = data,
                    subtype = subtype
                )
            }
        }
        
        return null
    }
    
    /**
     * 解析流对象引用
     */
    private fun resolveStream(obj: PdfObject): PdfStream? {
        return when (obj) {
            is PdfStream -> obj
            is PdfIndirectRef -> document.getObject(obj) as? PdfStream
            else -> null
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
        val subtype = fontDict.getName("Subtype")?.name ?: "CIDFontType0"
        val baseFont = fontDict.getName("BaseFont")?.name ?: ""
        
        // 获取默认宽度
        val dw = fontDict.getNumber("DW")?.toInt() ?: 1000
        
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
            val first = array.getNumber(i)?.toInt() ?: break
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
                    val width = array.getNumber(i)?.toInt() ?: break
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
     * 
     * 对于 Symbol 和 ZapfDingbats 字体，根据 PDF 32000-1:2008 标准，
     * 它们有内置的专用编码，应该使用内置编码而不是通用编码。
     */
    private fun parseEncoding(fontDict: PdfDictionary, baseFont: String = ""): FontEncoding {
        // 检测 Symbol 字体 - 使用内置 SymbolEncoding
        // Symbol, SymbolMT 等都应该使用 SymbolEncoding
        if (baseFont.contains("Symbol", ignoreCase = true)) {
            // Symbol 字体使用专用编码（如果没有明确指定其他编码）
            if (fontDict["Encoding"] == null) {
                return FontEncoding.Named("SymbolEncoding")
            }
        }
        
        // 检测 ZapfDingbats 字体 - 使用内置 ZapfDingbatsEncoding
        if (baseFont.contains("ZapfDingbats", ignoreCase = true) ||
            baseFont.contains("Dingbats", ignoreCase = true)) {
            // ZapfDingbats 字体使用专用编码
            if (fontDict["Encoding"] == null) {
                return FontEncoding.Named("ZapfDingbatsEncoding")
            }
        }
        
        return when (val enc = fontDict["Encoding"]) {
            is PdfName -> FontEncoding.Named(enc.name)
            is PdfDictionary -> {
                val baseEncoding = enc.getName("BaseEncoding")?.name ?: "WinAnsiEncoding"
                val differences = parseDifferences(enc.getArray("Differences"))
                FontEncoding.Custom(baseEncoding, differences)
            }
            is PdfIndirectRef -> {
                val encObj = document.getObject(enc)
                when (encObj) {
                    is PdfName -> FontEncoding.Named(encObj.name)
                    is PdfDictionary -> {
                        val baseEncoding = encObj.getName("BaseEncoding")?.name ?: "WinAnsiEncoding"
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
        val firstChar = fontDict.getNumber("FirstChar")?.toInt() ?: 0
        val lastChar = fontDict.getNumber("LastChar")?.toInt() ?: 255
        val widthsArray = fontDict.getArray("Widths")
        
        val widths = mutableMapOf<Int, Int>()
        
        if (widthsArray != null) {
            for (i in widthsArray.indices) {
                val code = firstChar + i
                val width = widthsArray.getNumber(i)?.toInt() ?: 0
                widths[code] = width
            }
        }
        
        // 获取默认宽度
        val missingWidth = fontDict.getDictionary("FontDescriptor")
            ?.getNumber("MissingWidth")?.toInt() ?: 0
        
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
    private val widths: FontWidths,
    val embeddedFont: EmbeddedFontData? = null
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
    
    /**
     * 检查是否有嵌入字体数据
     */
    fun hasEmbeddedFont(): Boolean = embeddedFont != null
    
    /**
     * 获取嵌入字体的原始数据（用于 Android Typeface 创建）
     */
    fun getEmbeddedFontBytes(): ByteArray? = embeddedFont?.data
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

/**
 * 嵌入字体类型
 */
enum class EmbeddedFontType {
    TYPE1,          // Type 1 字体 (PFB 格式)
    TRUETYPE,       // TrueType 字体 (TTF 格式)
    TYPE1C,         // Type 1 紧凑字体 (CFF 格式)
    CID_TYPE0C,     // CID Type 0 紧凑字体
    OPENTYPE,       // OpenType 字体 (OTF 格式)
    UNKNOWN         // 未知格式
}

/**
 * 嵌入字体数据
 */
data class EmbeddedFontData(
    val type: EmbeddedFontType,
    val data: ByteArray,
    val length1: Int = 0,      // Type1: 明文部分长度; TrueType: 整个字体长度
    val length2: Int = 0,      // Type1: 加密部分长度
    val length3: Int = 0,      // Type1: 固定内容部分长度
    val subtype: String? = null // FontFile3 的子类型
) {
    /**
     * 检查是否可以被 Android Typeface 直接使用
     */
    fun isAndroidCompatible(): Boolean {
        return when (type) {
            EmbeddedFontType.TRUETYPE -> true
            EmbeddedFontType.OPENTYPE -> true
            else -> false
        }
    }
    
    /**
     * 获取用于识别的文件扩展名
     */
    fun getFileExtension(): String {
        return when (type) {
            EmbeddedFontType.TYPE1 -> "pfb"
            EmbeddedFontType.TRUETYPE -> "ttf"
            EmbeddedFontType.TYPE1C -> "cff"
            EmbeddedFontType.CID_TYPE0C -> "cff"
            EmbeddedFontType.OPENTYPE -> "otf"
            EmbeddedFontType.UNKNOWN -> "bin"
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddedFontData) return false
        return type == other.type && data.contentEquals(other.data)
    }
    
    override fun hashCode(): Int {
        return 31 * type.hashCode() + data.contentHashCode()
    }
}
