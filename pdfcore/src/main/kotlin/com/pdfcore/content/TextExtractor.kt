package com.pdfcore.content

import com.pdfcore.model.*
import com.pdfcore.font.FontHandler
import com.pdfcore.font.PdfFont

/**
 * PDF 文本提取器
 * 
 * 从内容流中提取文本及其位置信息
 */
class TextExtractor(private val document: PdfDocument) {
    
    private val fontHandler = FontHandler(document)
    private val contentParser = ContentParser()
    
    /**
     * 从页面提取文本
     */
    fun extractFromPage(page: PdfDictionary): List<TextElement> {
        val contents = document.getPageContents(page)
        if (contents.isEmpty()) return emptyList()
        
        val resources = document.getPageResources(page) ?: PdfDictionary()
        val fonts = loadFonts(resources)
        val mediaBox = document.getPageMediaBox(page) ?: PdfRectangle(0f, 0f, 612f, 792f)
        
        val instructions = contentParser.parse(contents)
        return extractText(instructions, fonts, mediaBox)
    }
    
    /**
     * 从内容流提取文本
     */
    fun extractFromContent(
        content: ByteArray,
        resources: PdfDictionary,
        mediaBox: PdfRectangle = PdfRectangle(0f, 0f, 612f, 792f)
    ): List<TextElement> {
        val fonts = loadFonts(resources)
        val instructions = contentParser.parse(content)
        return extractText(instructions, fonts, mediaBox)
    }
    
    /**
     * 加载字体
     */
    private fun loadFonts(resources: PdfDictionary): Map<String, PdfFont> {
        val fonts = mutableMapOf<String, PdfFont>()
        
        val fontDict = when (val f = resources["Font"]) {
            is PdfDictionary -> f
            is PdfIndirectRef -> document.getObject(f) as? PdfDictionary
            else -> null
        } ?: return fonts
        
        for ((name, value) in fontDict) {
            val font = when (value) {
                is PdfDictionary -> fontHandler.getFont(value)
                is PdfIndirectRef -> {
                    val dict = document.getObject(value) as? PdfDictionary
                    dict?.let { fontHandler.getFont(it) }
                }
                else -> null
            }
            font?.let { fonts[name] = it }
        }
        
        return fonts
    }
    
    /**
     * 提取文本
     */
    private fun extractText(
        instructions: List<ContentInstruction>,
        fonts: Map<String, PdfFont>,
        mediaBox: PdfRectangle
    ): List<TextElement> {
        val elements = mutableListOf<TextElement>()
        val state = GraphicsState(mediaBox)
        
        var inText = false
        
        for (instruction in instructions) {
            when (instruction.operator) {
                // 图形状态
                "q" -> state.save()
                "Q" -> state.restore()
                "cm" -> {
                    if (instruction.operands.size >= 6) {
                        val m = PdfMatrix(
                            instruction.operands[0].asFloat(),
                            instruction.operands[1].asFloat(),
                            instruction.operands[2].asFloat(),
                            instruction.operands[3].asFloat(),
                            instruction.operands[4].asFloat(),
                            instruction.operands[5].asFloat()
                        )
                        state.concatMatrix(m)
                    }
                }
                
                // 文本对象
                "BT" -> {
                    inText = true
                    state.beginText()
                }
                "ET" -> {
                    inText = false
                    state.endText()
                }
                
                // 文本状态
                "Tf" -> {
                    if (instruction.operands.size >= 2) {
                        val fontName = (instruction.operands[0] as? PdfName)?.name ?: ""
                        val fontSize = instruction.operands[1].asFloat()
                        state.setFont(fonts[fontName], fontName, fontSize)
                    }
                }
                "Tc" -> {
                    if (instruction.operands.isNotEmpty()) {
                        state.charSpace = instruction.operands[0].asFloat()
                    }
                }
                "Tw" -> {
                    if (instruction.operands.isNotEmpty()) {
                        state.wordSpace = instruction.operands[0].asFloat()
                    }
                }
                "Tz" -> {
                    if (instruction.operands.isNotEmpty()) {
                        state.hScale = instruction.operands[0].asFloat() / 100f
                    }
                }
                "TL" -> {
                    if (instruction.operands.isNotEmpty()) {
                        state.leading = instruction.operands[0].asFloat()
                    }
                }
                "Tr" -> {
                    if (instruction.operands.isNotEmpty()) {
                        state.renderMode = instruction.operands[0].asInt()
                    }
                }
                "Ts" -> {
                    if (instruction.operands.isNotEmpty()) {
                        state.rise = instruction.operands[0].asFloat()
                    }
                }
                
                // 文本定位
                "Td" -> {
                    if (instruction.operands.size >= 2) {
                        val tx = instruction.operands[0].asFloat()
                        val ty = instruction.operands[1].asFloat()
                        state.moveText(tx, ty)
                    }
                }
                "TD" -> {
                    if (instruction.operands.size >= 2) {
                        val tx = instruction.operands[0].asFloat()
                        val ty = instruction.operands[1].asFloat()
                        state.leading = -ty
                        state.moveText(tx, ty)
                    }
                }
                "Tm" -> {
                    if (instruction.operands.size >= 6) {
                        val m = PdfMatrix(
                            instruction.operands[0].asFloat(),
                            instruction.operands[1].asFloat(),
                            instruction.operands[2].asFloat(),
                            instruction.operands[3].asFloat(),
                            instruction.operands[4].asFloat(),
                            instruction.operands[5].asFloat()
                        )
                        state.updateTextMatrix(m)
                    }
                }
                "T*" -> {
                    state.nextLine()
                }
                
                // 文本显示
                "Tj" -> {
                    if (instruction.operands.isNotEmpty()) {
                        val str = instruction.operands[0] as? PdfString
                        str?.let {
                            val element = state.showText(it)
                            elements.add(element)
                        }
                    }
                }
                "TJ" -> {
                    if (instruction.operands.isNotEmpty()) {
                        val array = instruction.operands[0] as? PdfArray
                        array?.let {
                            val element = state.showTextArray(it)
                            elements.add(element)
                        }
                    }
                }
                "'" -> {
                    state.nextLine()
                    if (instruction.operands.isNotEmpty()) {
                        val str = instruction.operands[0] as? PdfString
                        str?.let {
                            val element = state.showText(it)
                            elements.add(element)
                        }
                    }
                }
                "\"" -> {
                    if (instruction.operands.size >= 3) {
                        state.wordSpace = instruction.operands[0].asFloat()
                        state.charSpace = instruction.operands[1].asFloat()
                        state.nextLine()
                        val str = instruction.operands[2] as? PdfString
                        str?.let {
                            val element = state.showText(it)
                            elements.add(element)
                        }
                    }
                }
            }
        }
        
        return elements
    }
    
    private fun PdfObject.asFloat(): Float {
        return (this as? PdfNumber)?.toFloat() ?: 0f
    }
    
    private fun PdfObject.asInt(): Int {
        return (this as? PdfNumber)?.toInt() ?: 0
    }
}

/**
 * 文本元素
 */
data class TextElement(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontName: String,
    val fontSize: Float,
    val renderMode: Int,
    val charSpacing: Float,
    val wordSpacing: Float,
    val matrix: PdfMatrix,
    // 原始指令信息（用于编辑）
    val rawBytes: ByteArray,
    val instructionIndex: Int = -1
) {
    val bounds: PdfRectangle
        get() = PdfRectangle(x, y, x + width, y + height)
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextElement) return false
        return text == other.text && x == other.x && y == other.y
    }
    
    override fun hashCode(): Int {
        return 31 * text.hashCode() + x.hashCode() + y.hashCode()
    }
}

/**
 * 图形状态
 */
private class GraphicsState(private val mediaBox: PdfRectangle) {
    
    // 图形状态栈
    private val stateStack = mutableListOf<State>()
    
    // 当前变换矩阵
    var ctm = PdfMatrix.IDENTITY
    
    // 文本状态
    var textMatrix = PdfMatrix.IDENTITY
    var lineMatrix = PdfMatrix.IDENTITY
    var charSpace = 0f
    var wordSpace = 0f
    var hScale = 1f
    var leading = 0f
    var rise = 0f
    var renderMode = 0
    
    // 当前字体
    var font: PdfFont? = null
    var fontName = ""
    var fontSize = 12f
    
    private data class State(
        val ctm: PdfMatrix,
        val charSpace: Float,
        val wordSpace: Float,
        val hScale: Float,
        val leading: Float,
        val rise: Float,
        val renderMode: Int,
        val font: PdfFont?,
        val fontName: String,
        val fontSize: Float
    )
    
    fun save() {
        stateStack.add(State(
            ctm, charSpace, wordSpace, hScale, leading, rise, renderMode, font, fontName, fontSize
        ))
    }
    
    fun restore() {
        if (stateStack.isNotEmpty()) {
            val state = stateStack.removeLast()
            ctm = state.ctm
            charSpace = state.charSpace
            wordSpace = state.wordSpace
            hScale = state.hScale
            leading = state.leading
            rise = state.rise
            renderMode = state.renderMode
            font = state.font
            fontName = state.fontName
            fontSize = state.fontSize
        }
    }
    
    fun concatMatrix(m: PdfMatrix) {
        ctm = m.multiply(ctm)
    }
    
    fun beginText() {
        textMatrix = PdfMatrix.IDENTITY
        lineMatrix = PdfMatrix.IDENTITY
    }
    
    fun endText() {
        // 重置文本矩阵
    }
    
    fun setFont(f: PdfFont?, name: String, size: Float) {
        font = f
        fontName = name
        fontSize = size
    }
    
    fun updateTextMatrix(m: PdfMatrix) {
        textMatrix = m
        lineMatrix = m
    }
    
    fun moveText(tx: Float, ty: Float) {
        val m = PdfMatrix.translation(tx, ty)
        lineMatrix = m.multiply(lineMatrix)
        textMatrix = lineMatrix.copy()
    }
    
    fun nextLine() {
        moveText(0f, -leading)
    }
    
    fun showText(str: PdfString): TextElement {
        val bytes = str.toBytes()
        val text = font?.decode(bytes) ?: String(bytes, Charsets.ISO_8859_1)
        
        // 计算位置
        val renderMatrix = textMatrix.multiply(ctm)
        val (x, y) = renderMatrix.transform(0f, rise)
        
        // 计算宽度
        var width = 0f
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            val charWidth = (font?.getWidth(code) ?: 1000) * fontSize / 1000f
            width += charWidth * hScale + charSpace
            if (code == 0x20) {
                width += wordSpace
            }
        }
        
        // 更新文本位置
        val tx = width * textMatrix.a
        val ty = width * textMatrix.b
        textMatrix = PdfMatrix(
            textMatrix.a, textMatrix.b,
            textMatrix.c, textMatrix.d,
            textMatrix.e + tx, textMatrix.f + ty
        )
        
        val height = fontSize * kotlin.math.abs(renderMatrix.d)
        
        return TextElement(
            text = text,
            x = x,
            y = y,
            width = width,
            height = height,
            fontName = fontName,
            fontSize = fontSize * kotlin.math.abs(renderMatrix.d),
            renderMode = renderMode,
            charSpacing = charSpace,
            wordSpacing = wordSpace,
            matrix = renderMatrix,
            rawBytes = bytes
        )
    }
    
    fun showTextArray(array: PdfArray): TextElement {
        val sb = StringBuilder()
        val allBytes = mutableListOf<Byte>()
        var totalWidth = 0f
        var startX = 0f
        var startY = 0f
        var first = true
        
        for (item in array) {
            when (item) {
                is PdfString -> {
                    val bytes = item.toBytes()
                    allBytes.addAll(bytes.toList())
                    val text = font?.decode(bytes) ?: String(bytes, Charsets.ISO_8859_1)
                    sb.append(text)
                    
                    if (first) {
                        val renderMatrix = textMatrix.multiply(ctm)
                        val (x, y) = renderMatrix.transform(0f, rise)
                        startX = x
                        startY = y
                        first = false
                    }
                    
                    // 计算宽度
                    for (b in bytes) {
                        val code = b.toInt() and 0xFF
                        val charWidth = (font?.getWidth(code) ?: 1000) * fontSize / 1000f
                        totalWidth += charWidth * hScale + charSpace
                        if (code == 0x20) {
                            totalWidth += wordSpace
                        }
                    }
                    
                    // 更新文本位置
                    val tw = totalWidth * textMatrix.a
                    val th = totalWidth * textMatrix.b
                    textMatrix = PdfMatrix(
                        textMatrix.a, textMatrix.b,
                        textMatrix.c, textMatrix.d,
                        textMatrix.e + tw - totalWidth * textMatrix.a, textMatrix.f + th - totalWidth * textMatrix.b
                    )
                }
                is PdfNumber -> {
                    // 位置调整（以千分之一 em 为单位）
                    val adjustment = -item.toFloat() * fontSize / 1000f * hScale
                    totalWidth += adjustment
                    
                    val tx = adjustment * textMatrix.a
                    val ty = adjustment * textMatrix.b
                    textMatrix = PdfMatrix(
                        textMatrix.a, textMatrix.b,
                        textMatrix.c, textMatrix.d,
                        textMatrix.e + tx, textMatrix.f + ty
                    )
                }
                else -> { /* ignore other types */ }
            }
        }
        
        val renderMatrix = PdfMatrix(
            textMatrix.a * ctm.a + textMatrix.b * ctm.c,
            textMatrix.a * ctm.b + textMatrix.b * ctm.d,
            textMatrix.c * ctm.a + textMatrix.d * ctm.c,
            textMatrix.c * ctm.b + textMatrix.d * ctm.d,
            textMatrix.e * ctm.a + textMatrix.f * ctm.c + ctm.e,
            textMatrix.e * ctm.b + textMatrix.f * ctm.d + ctm.f
        )
        
        val height = fontSize * kotlin.math.abs(renderMatrix.d)
        
        return TextElement(
            text = sb.toString(),
            x = startX,
            y = startY,
            width = totalWidth,
            height = height,
            fontName = fontName,
            fontSize = fontSize * kotlin.math.abs(renderMatrix.d),
            renderMode = renderMode,
            charSpacing = charSpace,
            wordSpacing = wordSpace,
            matrix = renderMatrix,
            rawBytes = allBytes.toByteArray()
        )
    }
}
