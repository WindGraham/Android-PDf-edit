package com.pdfcore.mcp

import com.pdfcore.model.*
import kotlinx.serialization.json.*

/**
 * PDF MCP (Model Context Protocol) 服务端
 * 提供 AI 友好的结构化接口来操作 PDF 文档
 * 
 * 协议: MCP/JSON-RPC 2.0
 */
class PdfMcpServer {
    
    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true
    }
    
    /**
     * 处理 MCP 请求
     */
    fun handleRequest(requestJson: String): String {
        return try {
            val request = json.decodeFromString<McpRequest>(requestJson)
            val result = when (request.method) {
                "pdf/getDocumentInfo" -> handleGetDocumentInfo(request.params)
                "pdf/getPages" -> handleGetPages(request.params)
                "pdf/getPageContent" -> handleGetPageContent(request.params)
                "pdf/getTextBlocks" -> handleGetTextBlocks(request.params)
                "pdf/updateText" -> handleUpdateText(request.params)
                "pdf/addAnnotation" -> handleAddAnnotation(request.params)
                "pdf/deleteObject" -> handleDeleteObject(request.params)
                "pdf/getObject" -> handleGetObject(request.params)
                "pdf/updateObject" -> handleUpdateObject(request.params)
                else -> throw McpError(-32601, "Method not found: ${request.method}")
            }
            json.encodeToString(McpResponse.serializer(), McpResponse(id = request.id, result = result))
        } catch (e: McpError) {
            json.encodeToString(McpResponse.serializer(), McpResponse(id = null, error = e))
        } catch (e: Exception) {
            json.encodeToString(McpResponse.serializer(), McpResponse(id = null, error = McpError(-32603, e.message ?: "Internal error")))
        }
    }
    
    private fun handleGetDocumentInfo(params: JsonObject?): JsonObject {
        val doc = getDocument(params) ?: throw McpError(-32001, "Document not found")
        
        val catalog = doc.getCatalog()
        val info = doc.getInfo()
        
        return buildJsonObject {
            put("version", doc.version)
            put("pageCount", doc.getAllPages().size)
            put("objectCount", doc.objects.size)
            put("trailer", serializeObject(doc.trailer))
            catalog?.let { put("catalog", serializeObject(it)) }
            info?.let { put("info", serializeObject(it)) }
        }
    }
    
    private fun handleGetPages(params: JsonObject?): JsonArray {
        val doc = getDocument(params) ?: throw McpError(-32001, "Document not found")
        val pages = doc.getAllPages()
        
        return JsonArray(pages.mapIndexed { index, page ->
            buildJsonObject {
                put("index", index)
                put("object", serializeObject(page))
                
                // 提取 MediaBox (页面尺寸)
                val mediaBox = page.getArray("MediaBox")
                if (mediaBox != null && mediaBox.size >= 4) {
                    putJsonObject("mediaBox") {
                        put("x1", (mediaBox[0] as PdfNumber).value)
                        put("y1", (mediaBox[1] as PdfNumber).value)
                        put("x2", (mediaBox[2] as PdfNumber).value)
                        put("y2", (mediaBox[3] as PdfNumber).value)
                    }
                }
                
                // 资源引用
                page.getRef("Resources")?.let { 
                    put("resourcesRef", it.toKey()) 
                }
            }
        })
    }
    
    private fun handleGetPageContent(params: JsonObject?): JsonArray {
        val doc = getDocument(params) ?: throw McpError(-32001, "Document not found")
        val pageIndex = params?.get("pageIndex")?.jsonPrimitive?.int ?: 0
        
        val pages = doc.getAllPages()
        if (pageIndex >= pages.size) throw McpError(-32002, "Page index out of range")
        
        val page = pages[pageIndex]
        // 获取页面内容流并解码
        val streams = doc.getPageContents(page)
        val contents = streams.map { stream -> decodeStreamData(stream) }
        
        return JsonArray(contents.map { content ->
            JsonPrimitive(String(content, Charsets.ISO_8859_1))
        })
    }
    
    private fun decodeStreamData(stream: PdfStream): ByteArray {
        val filters = stream.getFilters()
        if (filters.isEmpty()) return stream.rawData
        
        var data = stream.rawData
        for ((index, filter) in filters.withIndex()) {
            val params = stream.getDecodeParams(index)
            data = com.pdfcore.parser.StreamFilters.decode(data, filter, params)
        }
        return data
    }
    
    private fun handleGetTextBlocks(params: JsonObject?): JsonArray {
        val doc = getDocument(params) ?: throw McpError(-32001, "Document not found")
        val pageIndex = params?.get("pageIndex")?.jsonPrimitive?.int ?: 0
        
        val pages = doc.getAllPages()
        if (pageIndex >= pages.size) throw McpError(-32002, "Page index out of range")
        
        val page = pages[pageIndex]
        // 获取页面内容流并解码
        val streams = doc.getPageContents(page)
        val contents = streams.map { stream -> decodeStreamData(stream) }
        
        // 解析内容流中的文本操作
        val textBlocks = mutableListOf<JsonObject>()
        contents.forEach { data: ByteArray ->
            val parser = ContentStreamParser(String(data, Charsets.ISO_8859_1))
            textBlocks.addAll(parser.extractTextBlocks().map { block ->
                buildJsonObject {
                    put("text", block.text)
                    put("x", block.x)
                    put("y", block.y)
                    put("font", block.font)
                    put("fontSize", block.fontSize)
                }
            })
        }
        
        return JsonArray(textBlocks)
    }
    
    private fun handleUpdateText(params: JsonObject?): JsonObject {
        val doc = getDocument(params) ?: throw McpError(-32001, "Document not found")
        val pageIndex = params?.get("pageIndex")?.jsonPrimitive?.int ?: 0
        val oldText = params?.get("oldText")?.jsonPrimitive?.content ?: throw McpError(-32602, "oldText required")
        val newText = params?.get("newText")?.jsonPrimitive?.content ?: throw McpError(-32602, "newText required")
        
        // 更新内容流中的文本
        // 实际实现需要更复杂的内容流操作
        return buildJsonObject {
            put("success", true)
            put("message", "Text updated (placeholder)")
        }
    }
    
    private fun handleAddAnnotation(params: JsonObject?): JsonObject {
        val doc = getDocument(params) ?: throw McpError(-32001, "Document not found")
        val pageIndex = params?.get("pageIndex")?.jsonPrimitive?.int ?: 0
        val type = params?.get("type")?.jsonPrimitive?.content ?: "Text"
        
        val pages = doc.getAllPages()
        if (pageIndex >= pages.size) throw McpError(-32002, "Page index out of range")
        
        val page = pages[pageIndex]
        
        // 创建注释字典
        val annot = PdfDictionary().apply {
            this["Type"] = PdfName("Annot")
            this["Subtype"] = PdfName(type)
            this["Rect"] = PdfArray().apply {
                add(PdfNumber((params?.get("x")?.jsonPrimitive?.float ?: 100f).toDouble()))
                add(PdfNumber((params?.get("y")?.jsonPrimitive?.float ?: 100f).toDouble()))
                add(PdfNumber((params?.get("x2")?.jsonPrimitive?.float ?: 200f).toDouble()))
                add(PdfNumber((params?.get("y2")?.jsonPrimitive?.float ?: 150f).toDouble()))
            }
            params?.get("content")?.jsonPrimitive?.content?.let {
                this["Contents"] = PdfString(it)
            }
        }
        
        // 添加注释到页面
        val annots = page.getArray("Annots") ?: PdfArray().also { page["Annots"] = it }
        val annotRef = doc.addObject(annot)
        annots.add(annotRef)
        
        return buildJsonObject {
            put("success", true)
            put("annotRef", annotRef.toKey())
        }
    }
    
    private fun handleDeleteObject(params: JsonObject?): JsonObject {
        val doc = getDocument(params) ?: throw McpError(-32001, "Document not found")
        val key = params?.get("objectKey")?.jsonPrimitive?.content ?: throw McpError(-32602, "objectKey required")
        
        doc.objects.remove(key)
        
        return buildJsonObject {
            put("success", true)
            put("deleted", key)
        }
    }
    
    private fun handleGetObject(params: JsonObject?): JsonElement {
        val doc = getDocument(params) ?: throw McpError(-32001, "Document not found")
        val key = params?.get("objectKey")?.jsonPrimitive?.content ?: throw McpError(-32602, "objectKey required")
        
        val obj = doc.objects[key] ?: throw McpError(-32003, "Object not found: $key")
        return serializeObject(obj.obj)
    }
    
    private fun handleUpdateObject(params: JsonObject?): JsonObject {
        val doc = getDocument(params) ?: throw McpError(-32001, "Document not found")
        val key = params?.get("objectKey")?.jsonPrimitive?.content ?: throw McpError(-32602, "objectKey required")
        
        // 这里可以实现对象的更新逻辑
        return buildJsonObject {
            put("success", true)
            put("message", "Object updated (placeholder)")
        }
    }
    
    private fun getDocument(params: JsonObject?): PdfDocument? {
        val docId = params?.get("documentId")?.jsonPrimitive?.content
        return PdfDocumentStore.getDocument(docId ?: "default")
    }
    
    private fun serializeObject(obj: PdfObject): JsonElement {
        return when (obj) {
            is PdfBoolean -> JsonPrimitive(obj.value)
            is PdfNumber -> JsonPrimitive(obj.value)
            is PdfString -> JsonPrimitive(obj.value)
            is PdfName -> JsonPrimitive("/${obj.name}")
            is PdfArray -> JsonArray(obj.map { serializeObject(it) })
            is PdfDictionary -> {
                buildJsonObject {
                    obj.entries.forEach { (k, v) ->
                        put(k, serializeObject(v))
                    }
                }
            }
            is PdfStream -> {
                buildJsonObject {
                    put("type", "stream")
                    put("dictionary", serializeObject(obj.dict))
                    put("dataLength", obj.rawData.size)
                }
            }
            is PdfIndirectRef -> JsonPrimitive("${obj.objectNumber} ${obj.generationNumber} R")
            is PdfNull -> JsonNull
            else -> JsonNull
        }
    }
}

/**
 * MCP 请求/响应数据类
 */
@kotlinx.serialization.Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String,
    val params: JsonObject? = null
)

@kotlinx.serialization.Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: String?,
    val result: JsonElement? = null,
    val error: McpError? = null
)

@kotlinx.serialization.Serializable
data class McpError(
    val code: Int,
    override val message: String,
    val data: JsonElement? = null
) : Exception(message)

/**
 * 文档存储 (简化版，实际应使用数据库或文件系统)
 */
object PdfDocumentStore {
    private val documents = mutableMapOf<String, PdfDocument>()
    
    fun storeDocument(id: String, document: PdfDocument) {
        documents[id] = document
    }
    
    fun getDocument(id: String): PdfDocument? = documents[id]
    
    fun removeDocument(id: String) {
        documents.remove(id)
    }
}

/**
 * 内容流解析器 - 提取文本块
 * 基于 PDF 32000-1:2008 第 9 章
 */
class ContentStreamParser(private val content: String) {
    
    data class TextBlock(
        val text: String,
        val x: Float,
        val y: Float,
        val font: String,
        val fontSize: Float
    )
    
    /**
     * 文本状态 - 跟踪文本操作符的状态
     */
    private data class TextState(
        var font: String = "",
        var fontSize: Float = 12f,
        var charSpace: Float = 0f,      // Tc
        var wordSpace: Float = 0f,      // Tw
        var horizontalScale: Float = 100f, // Tz
        var leading: Float = 0f,        // TL
        var rise: Float = 0f,           // Ts
        // 文本矩阵 [a b c d e f] - e=tx, f=ty
        var tm: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f),
        // 行矩阵 - 用于 T*, ', " 操作符
        var tlm: FloatArray = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    ) {
        fun getX(): Float = tm[4]
        fun getY(): Float = tm[5]
        
        fun setTextMatrix(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
            tm = floatArrayOf(a, b, c, d, e, f)
            tlm = tm.copyOf()
        }
        
        fun moveTextPosition(tx: Float, ty: Float) {
            tm[4] = tlm[4] + tx
            tm[5] = tlm[5] + ty
            tlm = tm.copyOf()
        }
        
        fun nextLine() {
            moveTextPosition(0f, -leading)
        }
        
        fun advanceX(width: Float) {
            tm[4] += width * tm[0] * horizontalScale / 100f
            tm[5] += width * tm[1] * horizontalScale / 100f
        }
        
        fun copy(): TextState = TextState(
            font, fontSize, charSpace, wordSpace, horizontalScale,
            leading, rise, tm.copyOf(), tlm.copyOf()
        )
    }
    
    fun extractTextBlocks(): List<TextBlock> {
        val blocks = mutableListOf<TextBlock>()
        val textState = TextState()
        var inTextObject = false
        val currentText = StringBuilder()
        var textStartX = 0f
        var textStartY = 0f
        var currentFont = ""
        var currentFontSize = 12f
        
        // 使用更完整的 tokenizer
        val tokens = tokenize(content)
        val operandStack = mutableListOf<Any>()
        
        for (token in tokens) {
            when {
                // 数字
                token.matches(Regex("-?\\d+\\.?\\d*")) -> {
                    operandStack.add(token.toFloatOrNull() ?: 0f)
                }
                // 名称
                token.startsWith("/") -> {
                    operandStack.add(token.substring(1))
                }
                // 字符串
                token.startsWith("(") && token.endsWith(")") -> {
                    operandStack.add(unescapeString(token.substring(1, token.length - 1)))
                }
                // 十六进制字符串
                token.startsWith("<") && token.endsWith(">") && !token.startsWith("<<") -> {
                    operandStack.add(decodeHexString(token.substring(1, token.length - 1)))
                }
                // 数组 (简化处理 - 用于 TJ)
                token.startsWith("[") -> {
                    operandStack.add(parseArray(token))
                }
                // 操作符
                else -> {
                    when (token) {
                        // 文本对象
                        "BT" -> {
                            inTextObject = true
                            textState.tm = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
                            textState.tlm = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
                        }
                        "ET" -> {
                            if (currentText.isNotBlank()) {
                                blocks.add(TextBlock(
                                    currentText.toString(),
                                    textStartX,
                                    textStartY,
                                    currentFont,
                                    currentFontSize
                                ))
                            }
                            currentText.clear()
                            inTextObject = false
                        }
                        
                        // 文本状态操作符
                        "Tc" -> {
                            textState.charSpace = getFloat(operandStack, 0)
                            operandStack.clear()
                        }
                        "Tw" -> {
                            textState.wordSpace = getFloat(operandStack, 0)
                            operandStack.clear()
                        }
                        "Tz" -> {
                            textState.horizontalScale = getFloat(operandStack, 0)
                            operandStack.clear()
                        }
                        "TL" -> {
                            textState.leading = getFloat(operandStack, 0)
                            operandStack.clear()
                        }
                        "Tf" -> {
                            if (operandStack.size >= 2) {
                                textState.fontSize = getFloat(operandStack, operandStack.size - 1)
                                textState.font = getString(operandStack, operandStack.size - 2)
                            }
                            operandStack.clear()
                        }
                        "Ts" -> {
                            textState.rise = getFloat(operandStack, 0)
                            operandStack.clear()
                        }
                        
                        // 文本定位操作符
                        "Td" -> {
                            if (operandStack.size >= 2) {
                                val tx = getFloat(operandStack, 0)
                                val ty = getFloat(operandStack, 1)
                                textState.moveTextPosition(tx, ty)
                            }
                            operandStack.clear()
                        }
                        "TD" -> {
                            if (operandStack.size >= 2) {
                                val tx = getFloat(operandStack, 0)
                                val ty = getFloat(operandStack, 1)
                                textState.leading = -ty
                                textState.moveTextPosition(tx, ty)
                            }
                            operandStack.clear()
                        }
                        "Tm" -> {
                            if (operandStack.size >= 6) {
                                textState.setTextMatrix(
                                    getFloat(operandStack, 0),
                                    getFloat(operandStack, 1),
                                    getFloat(operandStack, 2),
                                    getFloat(operandStack, 3),
                                    getFloat(operandStack, 4),
                                    getFloat(operandStack, 5)
                                )
                            }
                            operandStack.clear()
                        }
                        "T*" -> {
                            textState.nextLine()
                            operandStack.clear()
                        }
                        
                        // 文本显示操作符
                        "Tj" -> {
                            if (inTextObject && operandStack.isNotEmpty()) {
                                val text = getString(operandStack, 0)
                                if (currentText.isEmpty()) {
                                    textStartX = textState.getX()
                                    textStartY = textState.getY()
                                    currentFont = textState.font
                                    currentFontSize = textState.fontSize
                                }
                                currentText.append(text)
                            }
                            operandStack.clear()
                        }
                        "TJ" -> {
                            if (inTextObject && operandStack.isNotEmpty()) {
                                val array = operandStack.lastOrNull()
                                if (array is List<*>) {
                                    if (currentText.isEmpty()) {
                                        textStartX = textState.getX()
                                        textStartY = textState.getY()
                                        currentFont = textState.font
                                        currentFontSize = textState.fontSize
                                    }
                                    for (item in array) {
                                        when (item) {
                                            is String -> currentText.append(item)
                                            is Float -> {
                                                // 负数表示向右移动（字距调整）
                                                // 大的负数可能表示空格
                                                if (item < -100) {
                                                    currentText.append(" ")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            operandStack.clear()
                        }
                        "'" -> {
                            // 移动到下一行并显示文本
                            textState.nextLine()
                            if (inTextObject && operandStack.isNotEmpty()) {
                                val text = getString(operandStack, 0)
                                if (currentText.isNotEmpty()) {
                                    blocks.add(TextBlock(
                                        currentText.toString(),
                                        textStartX,
                                        textStartY,
                                        currentFont,
                                        currentFontSize
                                    ))
                                    currentText.clear()
                                }
                                textStartX = textState.getX()
                                textStartY = textState.getY()
                                currentFont = textState.font
                                currentFontSize = textState.fontSize
                                currentText.append(text)
                            }
                            operandStack.clear()
                        }
                        "\"" -> {
                            // 设置字距和词距，移动到下一行并显示文本
                            if (operandStack.size >= 3) {
                                textState.wordSpace = getFloat(operandStack, 0)
                                textState.charSpace = getFloat(operandStack, 1)
                                textState.nextLine()
                                val text = getString(operandStack, 2)
                                if (currentText.isNotEmpty()) {
                                    blocks.add(TextBlock(
                                        currentText.toString(),
                                        textStartX,
                                        textStartY,
                                        currentFont,
                                        currentFontSize
                                    ))
                                    currentText.clear()
                                }
                                textStartX = textState.getX()
                                textStartY = textState.getY()
                                currentFont = textState.font
                                currentFontSize = textState.fontSize
                                currentText.append(text)
                            }
                            operandStack.clear()
                        }
                        
                        else -> {
                            // 其他操作符，清空操作数栈
                            operandStack.clear()
                        }
                    }
                }
            }
        }
        
        // 处理剩余文本
        if (currentText.isNotBlank()) {
            blocks.add(TextBlock(
                currentText.toString(),
                textStartX,
                textStartY,
                currentFont,
                currentFontSize
            ))
        }
        
        return blocks
    }
    
    /**
     * 简单的 tokenizer
     */
    private fun tokenize(content: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        
        while (i < content.length) {
            val ch = content[i]
            
            when {
                ch.isWhitespace() -> i++
                ch == '%' -> {
                    // 跳过注释
                    while (i < content.length && content[i] != '\n' && content[i] != '\r') i++
                }
                ch == '(' -> {
                    // 字符串
                    val start = i
                    i++
                    var depth = 1
                    while (i < content.length && depth > 0) {
                        when (content[i]) {
                            '(' -> depth++
                            ')' -> depth--
                            '\\' -> i++ // 跳过转义字符
                        }
                        i++
                    }
                    tokens.add(content.substring(start, i))
                }
                ch == '<' -> {
                    if (i + 1 < content.length && content[i + 1] == '<') {
                        // 字典开始
                        tokens.add("<<")
                        i += 2
                    } else {
                        // 十六进制字符串
                        val start = i
                        i++
                        while (i < content.length && content[i] != '>') i++
                        i++
                        tokens.add(content.substring(start, i))
                    }
                }
                ch == '>' -> {
                    if (i + 1 < content.length && content[i + 1] == '>') {
                        tokens.add(">>")
                        i += 2
                    } else {
                        i++
                    }
                }
                ch == '[' -> {
                    // 数组
                    val start = i
                    i++
                    var depth = 1
                    while (i < content.length && depth > 0) {
                        when (content[i]) {
                            '[' -> depth++
                            ']' -> depth--
                            '(' -> {
                                // 跳过字符串内容
                                i++
                                var strDepth = 1
                                while (i < content.length && strDepth > 0) {
                                    when (content[i]) {
                                        '(' -> strDepth++
                                        ')' -> strDepth--
                                        '\\' -> i++
                                    }
                                    i++
                                }
                                continue
                            }
                        }
                        i++
                    }
                    tokens.add(content.substring(start, i))
                }
                ch == ']' -> {
                    tokens.add("]")
                    i++
                }
                ch == '/' -> {
                    // 名称
                    val start = i
                    i++
                    while (i < content.length && !content[i].isWhitespace() && 
                           content[i] !in "[]<>(){}/%") {
                        i++
                    }
                    tokens.add(content.substring(start, i))
                }
                ch.isDigit() || ch == '-' || ch == '+' || ch == '.' -> {
                    // 数字
                    val start = i
                    i++
                    while (i < content.length && (content[i].isDigit() || content[i] == '.')) {
                        i++
                    }
                    tokens.add(content.substring(start, i))
                }
                ch.isLetter() -> {
                    // 操作符
                    val start = i
                    while (i < content.length && (content[i].isLetterOrDigit() || content[i] == '*' || content[i] == '\'')) {
                        i++
                    }
                    tokens.add(content.substring(start, i))
                }
                else -> i++
            }
        }
        
        return tokens
    }
    
    private fun getFloat(stack: List<Any>, index: Int): Float {
        return when (val v = stack.getOrNull(index)) {
            is Float -> v
            is Number -> v.toFloat()
            is String -> v.toFloatOrNull() ?: 0f
            else -> 0f
        }
    }
    
    private fun getString(stack: List<Any>, index: Int): String {
        return when (val v = stack.getOrNull(index)) {
            is String -> v
            else -> v?.toString() ?: ""
        }
    }
    
    private fun parseArray(token: String): List<Any> {
        val result = mutableListOf<Any>()
        val content = token.substring(1, token.length - 1).trim()
        var i = 0
        
        while (i < content.length) {
            val ch = content[i]
            when {
                ch.isWhitespace() -> i++
                ch == '(' -> {
                    // 字符串
                    val start = i + 1
                    i++
                    var depth = 1
                    while (i < content.length && depth > 0) {
                        when (content[i]) {
                            '(' -> depth++
                            ')' -> depth--
                            '\\' -> i++
                        }
                        i++
                    }
                    result.add(unescapeString(content.substring(start, i - 1)))
                }
                ch == '<' -> {
                    // 十六进制字符串
                    val start = i + 1
                    i++
                    while (i < content.length && content[i] != '>') i++
                    result.add(decodeHexString(content.substring(start, i)))
                    i++
                }
                ch.isDigit() || ch == '-' || ch == '+' || ch == '.' -> {
                    val start = i
                    i++
                    while (i < content.length && (content[i].isDigit() || content[i] == '.')) i++
                    val numStr = content.substring(start, i)
                    result.add(numStr.toFloatOrNull() ?: 0f)
                }
                else -> i++
            }
        }
        
        return result
    }
    
    private fun unescapeString(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                i++
                when (s[i]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    '(' -> sb.append('(')
                    ')' -> sb.append(')')
                    '\\' -> sb.append('\\')
                    in '0'..'7' -> {
                        var oct = s[i].toString()
                        i++
                        while (i < s.length && s[i] in '0'..'7' && oct.length < 3) {
                            oct += s[i]
                            i++
                        }
                        sb.append(oct.toIntOrNull(8)?.toChar() ?: '?')
                        continue
                    }
                    '\n', '\r' -> {} // 行续接
                    else -> sb.append(s[i])
                }
                i++
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }
    
    private fun decodeHexString(hex: String): String {
        val sb = StringBuilder()
        val cleanHex = hex.filter { it.isLetterOrDigit() }
        var i = 0
        while (i + 1 < cleanHex.length) {
            val high = cleanHex[i].digitToIntOrNull(16) ?: 0
            val low = cleanHex[i + 1].digitToIntOrNull(16) ?: 0
            sb.append(((high shl 4) or low).toChar())
            i += 2
        }
        if (i < cleanHex.length) {
            // 奇数个字符，最后一个按高位处理
            val high = cleanHex[i].digitToIntOrNull(16) ?: 0
            sb.append((high shl 4).toChar())
        }
        return sb.toString()
    }
}
