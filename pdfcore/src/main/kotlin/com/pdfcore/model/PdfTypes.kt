package com.pdfcore.model

import kotlinx.serialization.Serializable

/**
 * PDF 对象类型 - 基于 PDF 32000-1:2008 标准 7.3 节
 * 
 * PDF 支持以下基本对象类型:
 * - Boolean: true/false
 * - Number: 整数或实数
 * - String: 文字字符串或十六进制字符串
 * - Name: 原子符号，以 / 开头
 * - Array: 有序对象集合
 * - Dictionary: 键值对集合
 * - Stream: 字典 + 二进制数据
 * - Null: 空对象
 * - Indirect Reference: 对象引用 (n m R)
 */

/**
 * PDF 对象基类
 */
sealed class PdfObject {
    /**
     * 将对象转换为 PDF 语法字符串
     */
    abstract fun toPdfString(): String
    
    /**
     * 深拷贝对象
     */
    abstract fun copy(): PdfObject
}

/**
 * PDF 布尔对象
 * PDF 32000-1:2008 7.3.2
 */
@Serializable
data class PdfBoolean(val value: Boolean) : PdfObject() {
    override fun toPdfString(): String = if (value) "true" else "false"
    override fun copy(): PdfBoolean = PdfBoolean(value)
    
    companion object {
        val TRUE = PdfBoolean(true)
        val FALSE = PdfBoolean(false)
        
        fun of(value: Boolean): PdfBoolean = if (value) TRUE else FALSE
    }
}

/**
 * PDF 数字对象 (整数或实数)
 * PDF 32000-1:2008 7.3.3
 */
@Serializable
data class PdfNumber(val value: Double) : PdfObject() {
    constructor(intValue: Int) : this(intValue.toDouble())
    constructor(longValue: Long) : this(longValue.toDouble())
    constructor(floatValue: Float) : this(floatValue.toDouble())
    
    val isInteger: Boolean
        get() = value == value.toLong().toDouble() && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE
    
    fun toInt(): Int = value.toInt()
    fun toLong(): Long = value.toLong()
    fun toFloat(): Float = value.toFloat()
    
    override fun toPdfString(): String = when {
        isInteger -> value.toLong().toString()
        else -> {
            // 避免科学计数法
            val str = "%.6f".format(value).trimEnd('0').trimEnd('.')
            if (str.isEmpty() || str == "-") "0" else str
        }
    }
    
    override fun copy(): PdfNumber = PdfNumber(value)
    
    companion object {
        val ZERO = PdfNumber(0)
        val ONE = PdfNumber(1)
    }
}

/**
 * PDF 字符串对象
 * PDF 32000-1:2008 7.3.4
 * 
 * 支持两种格式:
 * - 文字字符串: (Hello World)
 * - 十六进制字符串: <48656C6C6F>
 */
@Serializable
data class PdfString(
    val value: String,
    val isHex: Boolean = false,
    val rawBytes: ByteArray? = null
) : PdfObject() {
    
    /**
     * 获取字符串的字节表示
     */
    fun toBytes(): ByteArray {
        return rawBytes ?: if (isHex) {
            hexToBytes(value)
        } else {
            value.toByteArray(Charsets.ISO_8859_1)
        }
    }
    
    override fun toPdfString(): String = when {
        isHex -> "<${value}>"
        else -> "(${escapeLiteralString(value)})"
    }
    
    override fun copy(): PdfString = PdfString(value, isHex, rawBytes?.copyOf())
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PdfString) return false
        return value == other.value && isHex == other.isHex
    }
    
    override fun hashCode(): Int = 31 * value.hashCode() + isHex.hashCode()
    
    companion object {
        /**
         * 从字节数组创建字符串
         */
        fun fromBytes(bytes: ByteArray, asHex: Boolean = false): PdfString {
            return if (asHex) {
                PdfString(bytesToHex(bytes), true, bytes)
            } else {
                PdfString(String(bytes, Charsets.ISO_8859_1), false, bytes)
            }
        }
        
        /**
         * 转义文字字符串中的特殊字符
         */
        private fun escapeLiteralString(s: String): String {
            val sb = StringBuilder()
            for (c in s) {
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '(' -> sb.append("\\(")
                    ')' -> sb.append("\\)")
                    '\r' -> sb.append("\\r")
                    '\n' -> sb.append("\\n")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else -> {
                        if (c.code < 32 || c.code > 126) {
                            sb.append("\\%03o".format(c.code and 0xFF))
                        } else {
                            sb.append(c)
                        }
                    }
                }
            }
            return sb.toString()
        }
        
        private fun hexToBytes(hex: String): ByteArray {
            val cleanHex = hex.filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
            val paddedHex = if (cleanHex.length % 2 != 0) cleanHex + "0" else cleanHex
            return ByteArray(paddedHex.length / 2) { i ->
                paddedHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
        
        private fun bytesToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02X".format(it) }
        }
    }
}

/**
 * PDF 名称对象
 * PDF 32000-1:2008 7.3.5
 * 
 * 名称以 / 开头，是原子符号，用于字典键和其他标识符
 */
@Serializable
data class PdfName(val name: String) : PdfObject() {
    
    override fun toPdfString(): String = "/${escapeName(name)}"
    
    override fun copy(): PdfName = PdfName(name)
    
    companion object {
        // 常用名称常量
        val TYPE = PdfName("Type")
        val SUBTYPE = PdfName("Subtype")
        val CATALOG = PdfName("Catalog")
        val PAGES = PdfName("Pages")
        val PAGE = PdfName("Page")
        val CONTENTS = PdfName("Contents")
        val RESOURCES = PdfName("Resources")
        val MEDIABOX = PdfName("MediaBox")
        val CROPBOX = PdfName("CropBox")
        val FONT = PdfName("Font")
        val XOBJECT = PdfName("XObject")
        val EXTGSTATE = PdfName("ExtGState")
        val COLORSPACE = PdfName("ColorSpace")
        val PATTERN = PdfName("Pattern")
        val SHADING = PdfName("Shading")
        val LENGTH = PdfName("Length")
        val FILTER = PdfName("Filter")
        val DECODEPARMS = PdfName("DecodeParms")
        val ROOT = PdfName("Root")
        val SIZE = PdfName("Size")
        val INFO = PdfName("Info")
        val ID = PdfName("ID")
        val PREV = PdfName("Prev")
        val KIDS = PdfName("Kids")
        val COUNT = PdfName("Count")
        val PARENT = PdfName("Parent")
        val ROTATE = PdfName("Rotate")
        
        // 过滤器名称
        val FLATEDECODE = PdfName("FlateDecode")
        val LZWDECODE = PdfName("LZWDecode")
        val ASCIIHEXDECODE = PdfName("ASCIIHexDecode")
        val ASCII85DECODE = PdfName("ASCII85Decode")
        val DCTDECODE = PdfName("DCTDecode")
        val CCITTFAXDECODE = PdfName("CCITTFaxDecode")
        val JBIG2DECODE = PdfName("JBIG2Decode")
        val JPXDECODE = PdfName("JPXDecode")
        val RUNLENGTHDECODE = PdfName("RunLengthDecode")
        
        /**
         * 转义名称中的特殊字符
         * PDF 32000-1:2008 7.3.5: 使用 #xx 格式转义
         */
        private fun escapeName(name: String): String {
            val specialChars = setOf(
                ' ', '\t', '\n', '\r', '\u000C', '\u0000',
                '(', ')', '<', '>', '[', ']', '{', '}', '/', '%', '#'
            )
            return name.map { c ->
                when {
                    c.code < 0x21 || c.code > 0x7E -> "#%02X".format(c.code)
                    c in specialChars -> "#%02X".format(c.code)
                    else -> c.toString()
                }
            }.joinToString("")
        }
        
        /**
         * 反转义名称
         */
        fun unescapeName(escaped: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < escaped.length) {
                if (escaped[i] == '#' && i + 2 < escaped.length) {
                    try {
                        val hex = escaped.substring(i + 1, i + 3)
                        sb.append(hex.toInt(16).toChar())
                        i += 3
                    } catch (e: NumberFormatException) {
                        sb.append(escaped[i])
                        i++
                    }
                } else {
                    sb.append(escaped[i])
                    i++
                }
            }
            return sb.toString()
        }
    }
}

/**
 * PDF 数组对象
 * PDF 32000-1:2008 7.3.6
 */
class PdfArray(
    private val elements: MutableList<PdfObject> = mutableListOf()
) : PdfObject(), MutableList<PdfObject> by elements {
    
    constructor(vararg objects: PdfObject) : this(objects.toMutableList())
    
    override fun toPdfString(): String {
        return elements.joinToString(prefix = "[ ", postfix = " ]", separator = " ") { 
            it.toPdfString() 
        }
    }
    
    override fun copy(): PdfArray = PdfArray(elements.map { it.copy() }.toMutableList())
    
    // 便捷的类型安全访问方法
    fun getBoolean(index: Int): PdfBoolean? = elements.getOrNull(index) as? PdfBoolean
    fun getNumber(index: Int): PdfNumber? = elements.getOrNull(index) as? PdfNumber
    fun getString(index: Int): PdfString? = elements.getOrNull(index) as? PdfString
    fun getName(index: Int): PdfName? = elements.getOrNull(index) as? PdfName
    fun getArray(index: Int): PdfArray? = elements.getOrNull(index) as? PdfArray
    fun getDictionary(index: Int): PdfDictionary? = elements.getOrNull(index) as? PdfDictionary
    fun getRef(index: Int): PdfIndirectRef? = elements.getOrNull(index) as? PdfIndirectRef
    
    fun getInt(index: Int): Int? = getNumber(index)?.toInt()
    fun getFloat(index: Int): Float? = getNumber(index)?.toFloat()
    
    /**
     * 将数组转换为 Rectangle (4 个数字)
     */
    fun toRectangle(): PdfRectangle? {
        if (size < 4) return null
        val llx = getFloat(0) ?: return null
        val lly = getFloat(1) ?: return null
        val urx = getFloat(2) ?: return null
        val ury = getFloat(3) ?: return null
        return PdfRectangle(llx, lly, urx, ury)
    }
    
    /**
     * 将数组转换为变换矩阵 (6 个数字)
     */
    fun toMatrix(): PdfMatrix? {
        if (size < 6) return null
        return PdfMatrix(
            getFloat(0) ?: return null,
            getFloat(1) ?: return null,
            getFloat(2) ?: return null,
            getFloat(3) ?: return null,
            getFloat(4) ?: return null,
            getFloat(5) ?: return null
        )
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PdfArray) return false
        return elements == other.elements
    }
    
    override fun hashCode(): Int = elements.hashCode()
}

/**
 * PDF 字典对象
 * PDF 32000-1:2008 7.3.7
 */
class PdfDictionary(
    private val backingMap: MutableMap<String, PdfObject> = mutableMapOf()
) : PdfObject(), MutableMap<String, PdfObject> by backingMap {
    
    override fun toPdfString(): String {
        if (backingMap.isEmpty()) return "<< >>"
        val entriesStr = backingMap.entries.joinToString("\n  ") { (k, v) ->
            "/${PdfName.unescapeName(k).let { PdfName(it).toPdfString().substring(1) }} ${v.toPdfString()}"
        }
        return "<<\n  $entriesStr\n>>"
    }
    
    override fun copy(): PdfDictionary = PdfDictionary(
        backingMap.mapValues { (_, v) -> v.copy() }.toMutableMap()
    )
    
    // 类型安全的访问方法
    fun getBoolean(key: String): PdfBoolean? = backingMap[key] as? PdfBoolean
    fun getNumber(key: String): PdfNumber? = backingMap[key] as? PdfNumber
    fun getString(key: String): PdfString? = backingMap[key] as? PdfString
    fun getName(key: String): PdfName? = backingMap[key] as? PdfName
    fun getArray(key: String): PdfArray? = backingMap[key] as? PdfArray
    fun getDictionary(key: String): PdfDictionary? = backingMap[key] as? PdfDictionary
    fun getStream(key: String): PdfStream? = backingMap[key] as? PdfStream
    fun getRef(key: String): PdfIndirectRef? = backingMap[key] as? PdfIndirectRef
    
    fun getInt(key: String): Int? = getNumber(key)?.toInt()
    fun getFloat(key: String): Float? = getNumber(key)?.toFloat()
    fun getNameValue(key: String): String? = getName(key)?.name
    fun getBool(key: String): Boolean? = getBoolean(key)?.value
    
    /**
     * 获取 Type 字段
     */
    fun getType(): String? = getNameValue("Type")
    
    /**
     * 获取 Subtype 字段
     */
    fun getSubtype(): String? = getNameValue("Subtype")
    
    // 设置方法的便捷重载
    operator fun set(key: String, value: Int) { backingMap[key] = PdfNumber(value) }
    operator fun set(key: String, value: Double) { backingMap[key] = PdfNumber(value) }
    operator fun set(key: String, value: Boolean) { backingMap[key] = PdfBoolean.of(value) }
    operator fun set(key: String, value: String) { backingMap[key] = PdfString(value) }
    fun setName(key: String, value: String) { backingMap[key] = PdfName(value) }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PdfDictionary) return false
        return backingMap == other.backingMap
    }
    
    override fun hashCode(): Int = backingMap.hashCode()
}

/**
 * PDF 流对象
 * PDF 32000-1:2008 7.3.8
 * 
 * 流由字典和二进制数据组成
 */
class PdfStream(
    val dict: PdfDictionary = PdfDictionary(),
    var rawData: ByteArray = ByteArray(0)
) : PdfObject() {
    
    // 解码后的数据缓存
    private var decodedDataCache: ByteArray? = null
    
    /**
     * 获取流的长度
     */
    val length: Int
        get() = dict.getInt("Length") ?: rawData.size
    
    /**
     * 获取过滤器列表
     */
    fun getFilters(): List<String> {
        return when (val filter = dict["Filter"]) {
            is PdfName -> listOf(filter.name)
            is PdfArray -> filter.mapNotNull { (it as? PdfName)?.name }
            else -> emptyList()
        }
    }
    
    /**
     * 获取解码参数
     */
    fun getDecodeParams(index: Int = 0): PdfDictionary? {
        return when (val params = dict["DecodeParms"]) {
            is PdfDictionary -> if (index == 0) params else null
            is PdfArray -> params.getDictionary(index)
            else -> null
        }
    }
    
    /**
     * 获取解码后的数据
     * 注意：需要外部设置解码器才能正常工作
     */
    var decodedData: ByteArray
        get() = decodedDataCache ?: rawData
        set(value) { decodedDataCache = value }
    
    /**
     * 清除解码缓存
     */
    fun clearDecodedCache() {
        decodedDataCache = null
    }
    
    /**
     * 设置编码后的数据，同时更新 Length
     */
    fun setEncodedData(data: ByteArray, filters: List<String>? = null) {
        rawData = data
        dict["Length"] = PdfNumber(data.size)
        if (filters != null) {
            if (filters.size == 1) {
                dict["Filter"] = PdfName(filters[0])
            } else if (filters.size > 1) {
                dict["Filter"] = PdfArray(filters.map { PdfName(it) }.toMutableList())
            } else {
                dict.remove("Filter")
            }
        }
        decodedDataCache = null
    }
    
    override fun toPdfString(): String {
        dict["Length"] = PdfNumber(rawData.size)
        return "${dict.toPdfString()}\nstream\n${String(rawData, Charsets.ISO_8859_1)}\nendstream"
    }
    
    override fun copy(): PdfStream = PdfStream(dict.copy(), rawData.copyOf()).also {
        it.decodedDataCache = decodedDataCache?.copyOf()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PdfStream) return false
        return dict == other.dict && rawData.contentEquals(other.rawData)
    }
    
    override fun hashCode(): Int = 31 * dict.hashCode() + rawData.contentHashCode()
}

/**
 * PDF Null 对象
 * PDF 32000-1:2008 7.3.9
 */
@Serializable
object PdfNull : PdfObject() {
    override fun toPdfString(): String = "null"
    override fun copy(): PdfNull = PdfNull
}

/**
 * PDF 间接引用
 * PDF 32000-1:2008 7.3.10
 * 
 * 格式: objectNumber generationNumber R
 */
@Serializable
data class PdfIndirectRef(
    val objectNumber: Int,
    val generationNumber: Int = 0
) : PdfObject() {
    
    override fun toPdfString(): String = "$objectNumber $generationNumber R"
    
    override fun copy(): PdfIndirectRef = PdfIndirectRef(objectNumber, generationNumber)
    
    /**
     * 生成用于查找的键
     */
    fun toKey(): String = "$objectNumber $generationNumber"
    
    companion object {
        fun fromKey(key: String): PdfIndirectRef? {
            val parts = key.split(" ")
            if (parts.size != 2) return null
            val objNum = parts[0].toIntOrNull() ?: return null
            val genNum = parts[1].toIntOrNull() ?: return null
            return PdfIndirectRef(objNum, genNum)
        }
    }
}

/**
 * PDF 间接对象
 * 
 * 格式: objectNumber generationNumber obj ... endobj
 */
data class PdfIndirectObject(
    val objectNumber: Int,
    val generationNumber: Int = 0,
    val obj: PdfObject
) {
    fun toKey(): String = "$objectNumber $generationNumber"
    
    fun toRef(): PdfIndirectRef = PdfIndirectRef(objectNumber, generationNumber)
    
    fun toPdfString(): String = "$objectNumber $generationNumber obj\n${obj.toPdfString()}\nendobj"
    
    fun copy(): PdfIndirectObject = PdfIndirectObject(objectNumber, generationNumber, obj.copy())
}

/**
 * 矩形数据结构
 * PDF 32000-1:2008 7.9.5
 * 
 * 格式: [llx lly urx ury] (lower-left x, lower-left y, upper-right x, upper-right y)
 */
data class PdfRectangle(
    val llx: Float,
    val lly: Float,
    val urx: Float,
    val ury: Float
) {
    val width: Float get() = kotlin.math.abs(urx - llx)
    val height: Float get() = kotlin.math.abs(ury - lly)
    
    val minX: Float get() = kotlin.math.min(llx, urx)
    val minY: Float get() = kotlin.math.min(lly, ury)
    val maxX: Float get() = kotlin.math.max(llx, urx)
    val maxY: Float get() = kotlin.math.max(lly, ury)
    
    fun toArray(): PdfArray = PdfArray(
        PdfNumber(llx.toDouble()),
        PdfNumber(lly.toDouble()),
        PdfNumber(urx.toDouble()),
        PdfNumber(ury.toDouble())
    )
    
    fun contains(x: Float, y: Float): Boolean {
        return x >= minX && x <= maxX && y >= minY && y <= maxY
    }
    
    fun intersects(other: PdfRectangle): Boolean {
        return !(other.minX > maxX || other.maxX < minX ||
                 other.minY > maxY || other.maxY < minY)
    }
}

/**
 * 变换矩阵
 * PDF 32000-1:2008 8.3.3
 * 
 * 格式: [a b c d e f]
 * 表示变换:
 *   x' = a*x + c*y + e
 *   y' = b*x + d*y + f
 */
data class PdfMatrix(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val e: Float,
    val f: Float
) {
    companion object {
        val IDENTITY = PdfMatrix(1f, 0f, 0f, 1f, 0f, 0f)
        
        fun translation(tx: Float, ty: Float) = PdfMatrix(1f, 0f, 0f, 1f, tx, ty)
        
        fun scale(sx: Float, sy: Float) = PdfMatrix(sx, 0f, 0f, sy, 0f, 0f)
        
        fun rotation(angleRadians: Float): PdfMatrix {
            val cos = kotlin.math.cos(angleRadians)
            val sin = kotlin.math.sin(angleRadians)
            return PdfMatrix(cos, sin, -sin, cos, 0f, 0f)
        }
    }
    
    /**
     * 矩阵乘法: this * other
     */
    fun multiply(other: PdfMatrix): PdfMatrix {
        return PdfMatrix(
            a = this.a * other.a + this.b * other.c,
            b = this.a * other.b + this.b * other.d,
            c = this.c * other.a + this.d * other.c,
            d = this.c * other.b + this.d * other.d,
            e = this.e * other.a + this.f * other.c + other.e,
            f = this.e * other.b + this.f * other.d + other.f
        )
    }
    
    /**
     * 变换点
     */
    fun transform(x: Float, y: Float): Pair<Float, Float> {
        val newX = a * x + c * y + e
        val newY = b * x + d * y + f
        return newX to newY
    }
    
    /**
     * 计算逆矩阵
     */
    fun inverse(): PdfMatrix? {
        val det = a * d - b * c
        if (kotlin.math.abs(det) < 1e-10f) return null
        
        val invDet = 1f / det
        return PdfMatrix(
            a = d * invDet,
            b = -b * invDet,
            c = -c * invDet,
            d = a * invDet,
            e = (c * f - d * e) * invDet,
            f = (b * e - a * f) * invDet
        )
    }
    
    fun toArray(): PdfArray = PdfArray(
        PdfNumber(a.toDouble()),
        PdfNumber(b.toDouble()),
        PdfNumber(c.toDouble()),
        PdfNumber(d.toDouble()),
        PdfNumber(e.toDouble()),
        PdfNumber(f.toDouble())
    )
}
