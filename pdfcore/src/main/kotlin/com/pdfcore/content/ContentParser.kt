package com.pdfcore.content

import com.pdfcore.model.*
import com.pdfcore.parser.ContentStreamException
import com.pdfcore.parser.PdfLexer
import com.pdfcore.parser.StreamFilters

/**
 * PDF 内容流解析器
 * 基于 PDF 32000-1:2008 标准 7.8 节
 * 
 * 将内容流解析为指令列表
 */
class ContentParser {
    
    /**
     * 解析内容流
     * @param data 原始或解码后的内容流数据
     * @return 指令列表
     */
    fun parse(data: ByteArray): List<ContentInstruction> {
        val content = String(data, Charsets.ISO_8859_1)
        return parse(content)
    }
    
    /**
     * 解析内容流字符串
     */
    fun parse(content: String): List<ContentInstruction> {
        val instructions = mutableListOf<ContentInstruction>()
        val operands = mutableListOf<PdfObject>()
        val lexer = ContentLexer(content)
        
        while (!lexer.isEof) {
            val token = lexer.nextToken() ?: break
            
            when (token) {
                is ContentToken.Operand -> {
                    operands.add(token.value)
                }
                is ContentToken.Operator -> {
                    // 处理特殊操作符
                    when (token.name) {
                        "BI" -> {
                            // 内联图像
                            val inlineImage = lexer.parseInlineImage()
                            instructions.add(ContentInstruction("BI", emptyList()))
                            if (inlineImage != null) {
                                instructions.add(ContentInstruction("ID", listOf(inlineImage)))
                            }
                            instructions.add(ContentInstruction("EI", emptyList()))
                            operands.clear()
                        }
                        else -> {
                            instructions.add(ContentInstruction(token.name, operands.toList()))
                            operands.clear()
                        }
                    }
                }
            }
        }
        
        return instructions
    }
    
    /**
     * 解析 PdfStream
     */
    fun parse(stream: PdfStream): List<ContentInstruction> {
        val data = decodeStream(stream)
        return parse(data)
    }
    
    /**
     * 解析多个内容流
     */
    fun parse(streams: List<PdfStream>): List<ContentInstruction> {
        val allInstructions = mutableListOf<ContentInstruction>()
        for (stream in streams) {
            allInstructions.addAll(parse(stream))
        }
        return allInstructions
    }
    
    /**
     * 解码流数据
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
    
    /**
     * 将指令列表转换回内容流数据
     */
    fun serialize(instructions: List<ContentInstruction>): ByteArray {
        val sb = StringBuilder()
        
        for (instruction in instructions) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append(instruction.toPdfString())
        }
        
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }
}

/**
 * 内容流词法分析器
 */
private class ContentLexer(private val content: String) {
    private var pos = 0
    
    val isEof: Boolean get() = pos >= content.length
    
    /**
     * 读取下一个 token
     */
    fun nextToken(): ContentToken? {
        skipWhitespaceAndComments()
        if (isEof) return null
        
        val ch = content[pos]
        
        return when {
            ch == '(' -> ContentToken.Operand(parseLiteralString())
            ch == '<' && peek(1) == '<' -> ContentToken.Operand(parseDictionary())
            ch == '<' -> ContentToken.Operand(parseHexString())
            ch == '[' -> ContentToken.Operand(parseArray())
            ch == '/' -> ContentToken.Operand(parseName())
            ch == '+' || ch == '-' || ch == '.' || ch.isDigit() -> parseNumberOrOperand()
            ch.isLetter() || ch == '\'' || ch == '"' || ch == '*' -> parseOperator()
            else -> {
                pos++
                nextToken()
            }
        }
    }
    
    /**
     * 解析内联图像
     */
    fun parseInlineImage(): PdfStream? {
        skipWhitespaceAndComments()
        
        // 解析图像字典
        val dict = PdfDictionary()
        while (!isEof) {
            skipWhitespaceAndComments()
            if (isEof) break
            
            // 检查 ID 操作符
            if (content[pos] == 'I' && peek(1) == 'D') {
                pos += 2
                // 跳过一个空白字符
                if (pos < content.length && (content[pos] == ' ' || content[pos] == '\n' || content[pos] == '\r')) {
                    pos++
                }
                break
            }
            
            // 读取键
            if (content[pos] != '/') break
            val key = parseName()
            
            skipWhitespaceAndComments()
            
            // 读取值
            val value = when {
                content[pos] == '/' -> parseName()
                content[pos] == '[' -> parseArray()
                content[pos].isDigit() || content[pos] == '-' || content[pos] == '+' -> parseNumber()
                content[pos] == '(' -> parseLiteralString()
                content[pos] == '<' -> parseHexString()
                content[pos] == 't' || content[pos] == 'f' -> parseBoolean()
                else -> {
                    // 可能是名称缩写
                    val nameStr = parseWord()
                    PdfName(expandInlineImageKey(nameStr))
                }
            }
            
            dict[expandInlineImageKey(key.name)] = value
        }
        
        // 读取图像数据直到 EI
        val dataStart = pos
        var dataEnd = pos
        
        while (pos < content.length - 2) {
            // 查找 EI（前面是空白）
            if ((pos == dataStart || isWhitespace(content[pos - 1])) &&
                content[pos] == 'E' && content[pos + 1] == 'I' &&
                (pos + 2 >= content.length || isWhitespace(content[pos + 2]) || !content[pos + 2].isLetter())) {
                dataEnd = pos
                // 移除尾部空白
                while (dataEnd > dataStart && isWhitespace(content[dataEnd - 1])) {
                    dataEnd--
                }
                pos += 2
                break
            }
            pos++
        }
        
        val data = content.substring(dataStart, dataEnd).toByteArray(Charsets.ISO_8859_1)
        return PdfStream(dict, data)
    }
    
    private fun expandInlineImageKey(key: String): String {
        return when (key) {
            "BPC" -> "BitsPerComponent"
            "CS" -> "ColorSpace"
            "D" -> "Decode"
            "DP" -> "DecodeParms"
            "F" -> "Filter"
            "H" -> "Height"
            "IM" -> "ImageMask"
            "I" -> "Interpolate"
            "W" -> "Width"
            // Color space abbreviations
            "G" -> "DeviceGray"
            "RGB" -> "DeviceRGB"
            "CMYK" -> "DeviceCMYK"
            "I" -> "Indexed"
            // Filter abbreviations  
            "AHx" -> "ASCIIHexDecode"
            "A85" -> "ASCII85Decode"
            "LZW" -> "LZWDecode"
            "Fl" -> "FlateDecode"
            "RL" -> "RunLengthDecode"
            "CCF" -> "CCITTFaxDecode"
            "DCT" -> "DCTDecode"
            else -> key
        }
    }
    
    private fun parseNumberOrOperand(): ContentToken {
        val startPos = pos
        val sb = StringBuilder()
        var hasDecimal = false
        
        // 符号
        if (pos < content.length && (content[pos] == '+' || content[pos] == '-')) {
            sb.append(content[pos++])
        }
        
        // 数字和小数点
        while (pos < content.length) {
            val c = content[pos]
            when {
                c.isDigit() -> sb.append(content[pos++])
                c == '.' && !hasDecimal -> {
                    hasDecimal = true
                    sb.append(content[pos++])
                }
                else -> break
            }
        }
        
        val str = sb.toString()
        if (str.isEmpty() || str == "+" || str == "-" || str == ".") {
            // 回退并解析为操作符
            pos = startPos
            return parseOperator()
        }
        
        return ContentToken.Operand(PdfNumber(str.toDoubleOrNull() ?: 0.0))
    }
    
    private fun parseOperator(): ContentToken {
        val sb = StringBuilder()
        
        while (pos < content.length) {
            val c = content[pos]
            if (isWhitespace(c) || isDelimiter(c)) break
            sb.append(c)
            pos++
        }
        
        val name = sb.toString()
        
        // 检查是否是布尔值
        if (name == "true") return ContentToken.Operand(PdfBoolean.TRUE)
        if (name == "false") return ContentToken.Operand(PdfBoolean.FALSE)
        if (name == "null") return ContentToken.Operand(PdfNull)
        
        return ContentToken.Operator(name)
    }
    
    private fun parseLiteralString(): PdfString {
        pos++ // 跳过 '('
        val bytes = mutableListOf<Byte>()
        var depth = 1
        
        while (pos < content.length && depth > 0) {
            when (val c = content[pos++]) {
                '(' -> {
                    depth++
                    bytes.add('('.code.toByte())
                }
                ')' -> {
                    depth--
                    if (depth > 0) bytes.add(')'.code.toByte())
                }
                '\\' -> {
                    if (pos >= content.length) break
                    when (val esc = content[pos++]) {
                        'n' -> bytes.add('\n'.code.toByte())
                        'r' -> bytes.add('\r'.code.toByte())
                        't' -> bytes.add('\t'.code.toByte())
                        'b' -> bytes.add('\b'.code.toByte())
                        'f' -> bytes.add('\u000C'.code.toByte())
                        '(' -> bytes.add('('.code.toByte())
                        ')' -> bytes.add(')'.code.toByte())
                        '\\' -> bytes.add('\\'.code.toByte())
                        '\r' -> { if (pos < content.length && content[pos] == '\n') pos++ }
                        '\n' -> { }
                        in '0'..'7' -> {
                            var octal = esc.code - '0'.code
                            if (pos < content.length && content[pos] in '0'..'7') {
                                octal = (octal shl 3) or (content[pos++].code - '0'.code)
                                if (pos < content.length && content[pos] in '0'..'7') {
                                    octal = (octal shl 3) or (content[pos++].code - '0'.code)
                                }
                            }
                            bytes.add((octal and 0xFF).toByte())
                        }
                        else -> bytes.add(esc.code.toByte())
                    }
                }
                else -> bytes.add(c.code.toByte())
            }
        }
        
        return PdfString.fromBytes(bytes.toByteArray(), asHex = false)
    }
    
    private fun parseHexString(): PdfString {
        pos++ // 跳过 '<'
        val hexChars = StringBuilder()
        
        while (pos < content.length) {
            val c = content[pos++]
            if (c == '>') break
            if (c.isDigit() || c in 'A'..'F' || c in 'a'..'f') {
                hexChars.append(c)
            }
        }
        
        val hex = hexChars.toString().uppercase()
        val paddedHex = if (hex.length % 2 != 0) hex + "0" else hex
        val bytes = ByteArray(paddedHex.length / 2) { i ->
            paddedHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        
        return PdfString.fromBytes(bytes, asHex = true)
    }
    
    private fun parseArray(): PdfArray {
        pos++ // 跳过 '['
        val array = PdfArray()
        
        while (pos < content.length) {
            skipWhitespaceAndComments()
            if (pos >= content.length || content[pos] == ']') {
                pos++
                break
            }
            
            val token = nextToken()
            if (token is ContentToken.Operand) {
                array.add(token.value)
            } else {
                break
            }
        }
        
        return array
    }
    
    private fun parseDictionary(): PdfDictionary {
        pos += 2 // 跳过 '<<'
        val dict = PdfDictionary()
        
        while (pos < content.length) {
            skipWhitespaceAndComments()
            if (pos >= content.length) break
            if (content[pos] == '>' && peek(1) == '>') {
                pos += 2
                break
            }
            
            // 读取键
            if (content[pos] != '/') break
            val key = parseName()
            
            skipWhitespaceAndComments()
            
            // 读取值
            val token = nextToken()
            if (token is ContentToken.Operand) {
                dict[key.name] = token.value
            } else {
                break
            }
        }
        
        return dict
    }
    
    private fun parseName(): PdfName {
        pos++ // 跳过 '/'
        val sb = StringBuilder()
        
        while (pos < content.length) {
            val c = content[pos]
            if (isWhitespace(c) || isDelimiter(c)) break
            pos++
            if (c == '#' && pos + 1 < content.length) {
                val hex = content.substring(pos, pos + 2)
                try {
                    sb.append(hex.toInt(16).toChar())
                    pos += 2
                } catch (e: NumberFormatException) {
                    sb.append('#')
                }
            } else {
                sb.append(c)
            }
        }
        
        return PdfName(sb.toString())
    }
    
    private fun parseNumber(): PdfNumber {
        val sb = StringBuilder()
        
        if (pos < content.length && (content[pos] == '+' || content[pos] == '-')) {
            sb.append(content[pos++])
        }
        
        var hasDecimal = false
        while (pos < content.length) {
            val c = content[pos]
            when {
                c.isDigit() -> sb.append(content[pos++])
                c == '.' && !hasDecimal -> {
                    hasDecimal = true
                    sb.append(content[pos++])
                }
                else -> break
            }
        }
        
        return PdfNumber(sb.toString().toDoubleOrNull() ?: 0.0)
    }
    
    private fun parseBoolean(): PdfBoolean {
        return if (content.substring(pos).startsWith("true")) {
            pos += 4
            PdfBoolean.TRUE
        } else if (content.substring(pos).startsWith("false")) {
            pos += 5
            PdfBoolean.FALSE
        } else {
            PdfBoolean.FALSE
        }
    }
    
    private fun parseWord(): String {
        val sb = StringBuilder()
        while (pos < content.length && !isWhitespace(content[pos]) && !isDelimiter(content[pos])) {
            sb.append(content[pos++])
        }
        return sb.toString()
    }
    
    private fun skipWhitespaceAndComments() {
        while (pos < content.length) {
            val c = content[pos]
            when {
                isWhitespace(c) -> pos++
                c == '%' -> {
                    while (pos < content.length && content[pos] != '\n' && content[pos] != '\r') {
                        pos++
                    }
                }
                else -> break
            }
        }
    }
    
    private fun peek(offset: Int): Char? {
        val p = pos + offset
        return if (p >= 0 && p < content.length) content[p] else null
    }
    
    private fun isWhitespace(c: Char): Boolean {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C' || c == '\u0000'
    }
    
    private fun isDelimiter(c: Char): Boolean {
        return c in "()[]<>{}/%"
    }
}

/**
 * 内容流 Token
 */
private sealed class ContentToken {
    data class Operand(val value: PdfObject) : ContentToken()
    data class Operator(val name: String) : ContentToken()
}
