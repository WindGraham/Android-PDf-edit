package com.pdfcore.parser

import com.pdfcore.model.*

/**
 * PDF 词法分析器
 * 基于 PDF 32000-1:2008 标准 7.2 节
 * 
 * 负责将 PDF 字节流解析为 PDF 对象
 */
class PdfLexer(
    private val data: ByteArray,
    private var position: Int = 0
) {
    /**
     * 从字符串创建词法分析器
     */
    constructor(input: String) : this(input.toByteArray(Charsets.ISO_8859_1))
    
    /**
     * 当前位置
     */
    val currentPosition: Int get() = position
    
    /**
     * 是否到达末尾
     */
    val isEof: Boolean get() = position >= data.size
    
    /**
     * 剩余字节数
     */
    val remaining: Int get() = data.size - position
    
    /**
     * 查看当前字节（不移动位置）
     */
    fun peek(): Int = if (isEof) -1 else data[position].toInt() and 0xFF
    
    /**
     * 查看指定偏移处的字节
     */
    fun peek(offset: Int): Int {
        val pos = position + offset
        return if (pos < 0 || pos >= data.size) -1 else data[pos].toInt() and 0xFF
    }
    
    /**
     * 读取一个字节
     */
    fun read(): Int = if (isEof) -1 else (data[position++].toInt() and 0xFF)
    
    /**
     * 跳过指定字节数
     */
    fun skip(count: Int) {
        position = minOf(position + count, data.size)
    }
    
    /**
     * 设置位置
     */
    fun seek(pos: Int) {
        position = pos.coerceIn(0, data.size)
    }
    
    /**
     * 读取下一个 PDF 对象
     */
    fun nextObject(): PdfObject? {
        skipWhitespaceAndComments()
        if (isEof) return null
        
        return when (val ch = peek()) {
            '('.code -> parseLiteralString()
            '<'.code -> {
                if (peek(1) == '<'.code) {
                    parseDictionary()
                } else {
                    parseHexString()
                }
            }
            '['.code -> parseArray()
            '/'.code -> parseName()
            't'.code, 'f'.code -> parseBoolean()
            'n'.code -> parseNull()
            '+'.code, '-'.code, '.'.code,
            in '0'.code..'9'.code -> parseNumberOrReference()
            else -> {
                // 检查关键字
                val keyword = peekKeyword()
                when (keyword) {
                    "true", "false" -> parseBoolean()
                    "null" -> parseNull()
                    "stream" -> null // 流应该由上层处理
                    "endstream", "endobj" -> null // 结束标记
                    else -> null
                }
            }
        }
    }
    
    /**
     * 解析布尔值
     */
    private fun parseBoolean(): PdfBoolean {
        skipWhitespaceAndComments()
        return if (matchKeyword("true")) {
            PdfBoolean.TRUE
        } else if (matchKeyword("false")) {
            PdfBoolean.FALSE
        } else {
            throw ObjectParseException("Expected boolean at position $position")
        }
    }
    
    /**
     * 解析 null
     */
    private fun parseNull(): PdfNull {
        skipWhitespaceAndComments()
        if (!matchKeyword("null")) {
            throw ObjectParseException("Expected null at position $position")
        }
        return PdfNull
    }
    
    /**
     * 解析数字或间接引用
     */
    private fun parseNumberOrReference(): PdfObject {
        skipWhitespaceAndComments()
        val startPos = position
        
        // 读取第一个数字
        val num1 = parseNumberValue()
        
        // 检查是否是间接引用
        val savedPos = position
        skipWhitespaceAndComments()
        
        if (!isEof && peek().toChar().isDigit()) {
            val num2Start = position
            val num2 = parseNumberValue()
            
            skipWhitespaceAndComments()
            
            if (!isEof && peek() == 'R'.code) {
                position++ // 跳过 'R'
                // 确认是间接引用
                if (num1.isInteger && num2.isInteger) {
                    return PdfIndirectRef(num1.toInt(), num2.toInt())
                }
            }
            
            // 不是间接引用，回退
            position = savedPos
        }
        
        return num1
    }
    
    /**
     * 解析数字值
     */
    private fun parseNumberValue(): PdfNumber {
        skipWhitespaceAndComments()
        val sb = StringBuilder()
        var hasDecimal = false
        
        // 符号
        if (peek() == '+'.code || peek() == '-'.code) {
            sb.append(read().toChar())
        }
        
        // 整数部分或小数点开头
        while (!isEof) {
            val ch = peek()
            when {
                ch in '0'.code..'9'.code -> sb.append(read().toChar())
                ch == '.'.code && !hasDecimal -> {
                    hasDecimal = true
                    sb.append(read().toChar())
                }
                else -> break
            }
        }
        
        val str = sb.toString()
        if (str.isEmpty() || str == "+" || str == "-" || str == ".") {
            throw ObjectParseException("Invalid number at position $position")
        }
        
        return PdfNumber(str.toDoubleOrNull() ?: 0.0)
    }
    
    /**
     * 解析名称
     */
    private fun parseName(): PdfName {
        if (read() != '/'.code) {
            throw ObjectParseException("Expected '/' at position ${position - 1}")
        }
        
        val sb = StringBuilder()
        while (!isEof) {
            val ch = peek()
            if (isWhitespace(ch) || isDelimiter(ch)) break
            
            position++
            if (ch == '#'.code && remaining >= 2) {
                // 十六进制转义 #XX
                val hex1 = read()
                val hex2 = read()
                if (isHexDigit(hex1) && isHexDigit(hex2)) {
                    val value = (hexValue(hex1) shl 4) or hexValue(hex2)
                    sb.append(value.toChar())
                } else {
                    // 无效转义，保留原样
                    sb.append('#')
                    sb.append(hex1.toChar())
                    sb.append(hex2.toChar())
                }
            } else {
                sb.append(ch.toChar())
            }
        }
        
        return PdfName(sb.toString())
    }
    
    /**
     * 解析文字字符串
     */
    private fun parseLiteralString(): PdfString {
        if (read() != '('.code) {
            throw ObjectParseException("Expected '(' at position ${position - 1}")
        }
        
        val bytes = mutableListOf<Byte>()
        var depth = 1
        
        while (!isEof && depth > 0) {
            when (val ch = read()) {
                '('.code -> {
                    depth++
                    bytes.add('('.code.toByte())
                }
                ')'.code -> {
                    depth--
                    if (depth > 0) bytes.add(')'.code.toByte())
                }
                '\\'.code -> {
                    if (isEof) break
                    when (val escaped = read()) {
                        'n'.code -> bytes.add('\n'.code.toByte())
                        'r'.code -> bytes.add('\r'.code.toByte())
                        't'.code -> bytes.add('\t'.code.toByte())
                        'b'.code -> bytes.add('\b'.code.toByte())
                        'f'.code -> bytes.add('\u000C'.code.toByte())
                        '('.code -> bytes.add('('.code.toByte())
                        ')'.code -> bytes.add(')'.code.toByte())
                        '\\'.code -> bytes.add('\\'.code.toByte())
                        '\r'.code -> {
                            // 行续接：跳过 \r 和可能的 \n
                            if (peek() == '\n'.code) position++
                        }
                        '\n'.code -> {
                            // 行续接：跳过 \n
                        }
                        in '0'.code..'7'.code -> {
                            // 八进制转义
                            var octal = escaped - '0'.code
                            if (peek() in '0'.code..'7'.code) {
                                octal = (octal shl 3) or (read() - '0'.code)
                                if (peek() in '0'.code..'7'.code) {
                                    octal = (octal shl 3) or (read() - '0'.code)
                                }
                            }
                            bytes.add((octal and 0xFF).toByte())
                        }
                        else -> bytes.add(escaped.toByte())
                    }
                }
                else -> bytes.add(ch.toByte())
            }
        }
        
        return PdfString.fromBytes(bytes.toByteArray(), asHex = false)
    }
    
    /**
     * 解析十六进制字符串
     */
    private fun parseHexString(): PdfString {
        if (read() != '<'.code) {
            throw ObjectParseException("Expected '<' at position ${position - 1}")
        }
        
        val hexChars = StringBuilder()
        while (!isEof) {
            val ch = peek()
            if (ch == '>'.code) {
                position++
                break
            }
            position++
            if (isHexDigit(ch)) {
                hexChars.append(ch.toChar())
            }
            // 跳过空白
        }
        
        // 如果长度为奇数，补 0
        val hex = hexChars.toString().uppercase()
        val paddedHex = if (hex.length % 2 != 0) hex + "0" else hex
        
        val bytes = ByteArray(paddedHex.length / 2) { i ->
            paddedHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        
        return PdfString.fromBytes(bytes, asHex = true)
    }
    
    /**
     * 解析数组
     */
    private fun parseArray(): PdfArray {
        if (read() != '['.code) {
            throw ObjectParseException("Expected '[' at position ${position - 1}")
        }
        
        val array = PdfArray()
        
        while (true) {
            skipWhitespaceAndComments()
            if (isEof) break
            if (peek() == ']'.code) {
                position++
                break
            }
            
            val obj = nextObject() ?: break
            array.add(obj)
        }
        
        return array
    }
    
    /**
     * 解析字典
     */
    fun parseDictionary(): PdfDictionary {
        skipWhitespaceAndComments()
        
        if (read() != '<'.code || read() != '<'.code) {
            throw ObjectParseException("Expected '<<' at position ${position - 2}")
        }
        
        val dict = PdfDictionary()
        
        while (true) {
            skipWhitespaceAndComments()
            if (isEof) break
            
            // 检查结束
            if (peek() == '>'.code && peek(1) == '>'.code) {
                position += 2
                break
            }
            
            // 读取键（必须是 Name）
            if (peek() != '/'.code) break
            val key = parseName()
            
            skipWhitespaceAndComments()
            
            // 读取值
            val value = nextObject() ?: break
            dict[key.name] = value
        }
        
        return dict
    }
    
    /**
     * 解析字典和可能的流
     */
    fun parseDictionaryOrStream(): PdfObject {
        val dict = parseDictionary()
        
        skipWhitespaceAndComments()
        
        // 检查是否有 stream 关键字
        if (matchKeyword("stream")) {
            // 跳过行尾
            if (peek() == '\r'.code) position++
            if (peek() == '\n'.code) position++
            
            // 获取长度
            val length = dict.getInt("Length") ?: 0
            
            // 读取流数据
            val streamData = if (length > 0 && position + length <= data.size) {
                val bytes = data.copyOfRange(position, position + length)
                position += length
                bytes
            } else {
                // 长度未知或无效，搜索 endstream
                findStreamEnd()
            }
            
            skipWhitespaceAndComments()
            matchKeyword("endstream")
            
            return PdfStream(dict, streamData)
        }
        
        return dict
    }
    
    /**
     * 搜索 endstream 来确定流数据结束位置
     */
    private fun findStreamEnd(): ByteArray {
        val startPos = position
        val endMarker = "endstream".toByteArray()
        
        while (position < data.size - endMarker.size) {
            var found = true
            for (i in endMarker.indices) {
                if (data[position + i] != endMarker[i]) {
                    found = false
                    break
                }
            }
            if (found) {
                val streamData = data.copyOfRange(startPos, position)
                // 移除可能的尾部空白
                var end = streamData.size
                while (end > 0 && (streamData[end - 1] == '\r'.code.toByte() || 
                                   streamData[end - 1] == '\n'.code.toByte())) {
                    end--
                }
                return streamData.copyOfRange(0, end)
            }
            position++
        }
        
        // 未找到 endstream，返回剩余数据
        return data.copyOfRange(startPos, data.size)
    }
    
    /**
     * 跳过空白字符和注释
     */
    fun skipWhitespaceAndComments() {
        while (!isEof) {
            val ch = peek()
            when {
                isWhitespace(ch) -> position++
                ch == '%'.code -> {
                    // 跳过注释直到行尾
                    while (!isEof && peek() != '\n'.code && peek() != '\r'.code) {
                        position++
                    }
                }
                else -> break
            }
        }
    }
    
    /**
     * 跳过空白字符（不包括注释）
     */
    fun skipWhitespace() {
        while (!isEof && isWhitespace(peek())) {
            position++
        }
    }
    
    /**
     * 检查并消费关键字
     */
    fun matchKeyword(keyword: String): Boolean {
        val bytes = keyword.toByteArray(Charsets.US_ASCII)
        if (position + bytes.size > data.size) return false
        
        for (i in bytes.indices) {
            if (data[position + i] != bytes[i]) return false
        }
        
        // 确保关键字后面是分隔符或结束
        val afterPos = position + bytes.size
        if (afterPos < data.size) {
            val afterCh = data[afterPos].toInt() and 0xFF
            if (!isWhitespace(afterCh) && !isDelimiter(afterCh)) {
                return false
            }
        }
        
        position += bytes.size
        return true
    }
    
    /**
     * 查看下一个关键字（不消费）
     */
    private fun peekKeyword(): String? {
        val savedPos = position
        val sb = StringBuilder()
        
        while (!isEof && remaining > 0) {
            val ch = peek()
            if (isWhitespace(ch) || isDelimiter(ch)) break
            sb.append(ch.toChar())
            position++
        }
        
        position = savedPos
        return if (sb.isEmpty()) null else sb.toString()
    }
    
    /**
     * 读取一行
     */
    fun readLine(): String {
        val sb = StringBuilder()
        while (!isEof) {
            val ch = read()
            if (ch == '\r'.code) {
                if (peek() == '\n'.code) position++
                break
            }
            if (ch == '\n'.code) break
            sb.append(ch.toChar())
        }
        return sb.toString()
    }
    
    /**
     * 读取指定数量的字节
     */
    fun readBytes(count: Int): ByteArray {
        val actualCount = minOf(count, remaining)
        val result = data.copyOfRange(position, position + actualCount)
        position += actualCount
        return result
    }
    
    /**
     * 获取从当前位置到指定位置的字节
     */
    fun getBytes(start: Int, end: Int): ByteArray {
        val s = start.coerceIn(0, data.size)
        val e = end.coerceIn(s, data.size)
        return data.copyOfRange(s, e)
    }
    
    companion object {
        /**
         * 是否是 PDF 空白字符
         * PDF 32000-1:2008 Table 1
         */
        fun isWhitespace(ch: Int): Boolean {
            return ch == 0x00 || ch == 0x09 || ch == 0x0A || 
                   ch == 0x0C || ch == 0x0D || ch == 0x20
        }
        
        /**
         * 是否是 PDF 分隔符
         * PDF 32000-1:2008 Table 2
         */
        fun isDelimiter(ch: Int): Boolean {
            return ch == '('.code || ch == ')'.code ||
                   ch == '<'.code || ch == '>'.code ||
                   ch == '['.code || ch == ']'.code ||
                   ch == '{'.code || ch == '}'.code ||
                   ch == '/'.code || ch == '%'.code
        }
        
        /**
         * 是否是十六进制数字
         */
        fun isHexDigit(ch: Int): Boolean {
            return ch in '0'.code..'9'.code ||
                   ch in 'A'.code..'F'.code ||
                   ch in 'a'.code..'f'.code
        }
        
        /**
         * 获取十六进制数字的值
         */
        fun hexValue(ch: Int): Int {
            return when (ch) {
                in '0'.code..'9'.code -> ch - '0'.code
                in 'A'.code..'F'.code -> ch - 'A'.code + 10
                in 'a'.code..'f'.code -> ch - 'a'.code + 10
                else -> 0
            }
        }
    }
}
