package com.pdfrender.engine

import android.graphics.*
import com.pdfcore.content.ContentInstruction
import com.pdfcore.content.ContentParser
import com.pdfcore.font.FontHandler
import com.pdfcore.font.PdfFont
import com.pdfcore.model.*
import com.pdfcore.parser.StreamFilters

/**
 * PDF 渲染引擎
 * 
 * 将 PDF 页面渲染到 Android Bitmap/Canvas
 */
class PdfRenderEngine(private val document: PdfDocument) {
    
    private val fontHandler = FontHandler(document)
    private val contentParser = ContentParser()
    
    /**
     * 渲染页面到 Bitmap
     */
    fun renderPage(
        pageIndex: Int,
        scale: Float = 1f,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        val page = document.getPage(pageIndex) ?: return null
        return renderPage(page, scale, backgroundColor)
    }
    
    /**
     * 渲染页面到 Bitmap
     */
    fun renderPage(
        page: PdfDictionary,
        scale: Float = 1f,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        val mediaBox = document.getPageMediaBox(page) ?: return null
        val rotation = document.getPageRotation(page)
        
        // 计算尺寸
        val width: Int
        val height: Int
        when (rotation) {
            90, 270 -> {
                width = (mediaBox.height * scale).toInt()
                height = (mediaBox.width * scale).toInt()
            }
            else -> {
                width = (mediaBox.width * scale).toInt()
                height = (mediaBox.height * scale).toInt()
            }
        }
        
        if (width <= 0 || height <= 0) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        renderToCanvas(page, canvas, scale, backgroundColor)
        
        return bitmap
    }
    
    /**
     * 渲染到 Canvas
     */
    fun renderToCanvas(
        page: PdfDictionary,
        canvas: Canvas,
        scale: Float = 1f,
        backgroundColor: Int = Color.WHITE
    ) {
        val mediaBox = document.getPageMediaBox(page) ?: return
        val rotation = document.getPageRotation(page)
        
        // 填充背景
        canvas.drawColor(backgroundColor)
        
        canvas.save()
        
        // 应用缩放和坐标变换
        canvas.scale(scale, scale)
        
        // 处理旋转和 PDF 坐标系 (原点在左下角)
        when (rotation) {
            90 -> {
                canvas.translate(mediaBox.height, 0f)
                canvas.rotate(90f)
            }
            180 -> {
                canvas.translate(mediaBox.width, mediaBox.height)
                canvas.rotate(180f)
            }
            270 -> {
                canvas.translate(0f, mediaBox.width)
                canvas.rotate(270f)
            }
            else -> {
                // PDF 坐标系转换：翻转 Y 轴
                canvas.translate(0f, mediaBox.height)
                canvas.scale(1f, -1f)
            }
        }
        
        // 平移到 MediaBox 原点
        canvas.translate(-mediaBox.llx, -mediaBox.lly)
        
        // 加载资源
        val resources = document.getPageResources(page) ?: PdfDictionary()
        val fonts = loadFonts(resources)
        
        // 解析并渲染内容
        val contents = document.getPageContents(page)
        if (contents.isNotEmpty()) {
            val instructions = contentParser.parse(contents)
            render(canvas, instructions, resources, fonts)
        }
        
        canvas.restore()
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
     * 渲染内容
     */
    private fun render(
        canvas: Canvas,
        instructions: List<ContentInstruction>,
        resources: PdfDictionary,
        fonts: Map<String, PdfFont>
    ) {
        val state = RenderState(canvas, resources, fonts, document)
        
        for (instruction in instructions) {
            state.execute(instruction)
        }
    }
}

/**
 * 渲染状态
 */
private class RenderState(
    private val canvas: Canvas,
    private val resources: PdfDictionary,
    private val fonts: Map<String, PdfFont>,
    private val document: PdfDocument
) {
    // 图形状态栈
    private val stateStack = mutableListOf<GraphicsState>()
    private var currentState = GraphicsState()
    
    // 路径
    private val currentPath = Path()
    private var pathStartX = 0f
    private var pathStartY = 0f
    private var currentX = 0f
    private var currentY = 0f
    
    // 文本状态
    private var inText = false
    private var textMatrix = Matrix()
    private var lineMatrix = Matrix()
    
    // 画笔
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.MITER
    }
    
    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    fun execute(instruction: ContentInstruction) {
        when (instruction.operator) {
            // 图形状态
            "q" -> saveState()
            "Q" -> restoreState()
            "cm" -> concatMatrix(instruction.operands)
            "w" -> setLineWidth(instruction.operands)
            "J" -> setLineCap(instruction.operands)
            "j" -> setLineJoin(instruction.operands)
            "M" -> setMiterLimit(instruction.operands)
            "d" -> setDashPattern(instruction.operands)
            
            // 路径构建
            "m" -> moveTo(instruction.operands)
            "l" -> lineTo(instruction.operands)
            "c" -> curveTo(instruction.operands)
            "v" -> curveToV(instruction.operands)
            "y" -> curveToY(instruction.operands)
            "h" -> closePath()
            "re" -> rectangle(instruction.operands)
            
            // 路径绑制
            "S" -> stroke()
            "s" -> closeAndStroke()
            "f", "F" -> fill(Path.FillType.WINDING)
            "f*" -> fill(Path.FillType.EVEN_ODD)
            "B" -> fillAndStroke(Path.FillType.WINDING)
            "B*" -> fillAndStroke(Path.FillType.EVEN_ODD)
            "b" -> closeAndFillStroke(Path.FillType.WINDING)
            "b*" -> closeAndFillStroke(Path.FillType.EVEN_ODD)
            "n" -> endPath()
            
            // 裁剪
            "W" -> clip(Path.FillType.WINDING)
            "W*" -> clip(Path.FillType.EVEN_ODD)
            
            // 颜色
            "g" -> setFillGray(instruction.operands)
            "G" -> setStrokeGray(instruction.operands)
            "rg" -> setFillRGB(instruction.operands)
            "RG" -> setStrokeRGB(instruction.operands)
            "k" -> setFillCMYK(instruction.operands)
            "K" -> setStrokeCMYK(instruction.operands)
            "cs" -> setFillColorSpace(instruction.operands)
            "CS" -> setStrokeColorSpace(instruction.operands)
            "sc", "scn" -> setFillColor(instruction.operands)
            "SC", "SCN" -> setStrokeColor(instruction.operands)
            
            // 文本
            "BT" -> beginText()
            "ET" -> endText()
            "Tf" -> setFont(instruction.operands)
            "Tc" -> setCharSpace(instruction.operands)
            "Tw" -> setWordSpace(instruction.operands)
            "Tz" -> setHScale(instruction.operands)
            "TL" -> setLeading(instruction.operands)
            "Tr" -> setRenderMode(instruction.operands)
            "Ts" -> setRise(instruction.operands)
            "Td" -> moveTextPos(instruction.operands)
            "TD" -> moveTextPosAndSetLeading(instruction.operands)
            "Tm" -> setTextMatrix(instruction.operands)
            "T*" -> nextLine()
            "Tj" -> showText(instruction.operands)
            "TJ" -> showTextArray(instruction.operands)
            "'" -> showTextNextLine(instruction.operands)
            "\"" -> showTextWithSpacing(instruction.operands)
            
            // XObject
            "Do" -> drawXObject(instruction.operands)
        }
    }
    
    // ==================== 图形状态 ====================
    
    private fun saveState() {
        canvas.save()
        stateStack.add(currentState.copy())
    }
    
    private fun restoreState() {
        canvas.restore()
        if (stateStack.isNotEmpty()) {
            currentState = stateStack.removeLast()
            applyState()
        }
    }
    
    private fun applyState() {
        strokePaint.strokeWidth = currentState.lineWidth
        strokePaint.color = currentState.strokeColor
        fillPaint.color = currentState.fillColor
    }
    
    private fun concatMatrix(operands: List<PdfObject>) {
        if (operands.size < 6) return
        val m = Matrix()
        m.setValues(floatArrayOf(
            operands[0].asFloat(), operands[2].asFloat(), operands[4].asFloat(),
            operands[1].asFloat(), operands[3].asFloat(), operands[5].asFloat(),
            0f, 0f, 1f
        ))
        canvas.concat(m)
    }
    
    private fun setLineWidth(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        currentState.lineWidth = operands[0].asFloat()
        strokePaint.strokeWidth = currentState.lineWidth
    }
    
    private fun setLineCap(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        strokePaint.strokeCap = when (operands[0].asInt()) {
            1 -> Paint.Cap.ROUND
            2 -> Paint.Cap.SQUARE
            else -> Paint.Cap.BUTT
        }
    }
    
    private fun setLineJoin(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        strokePaint.strokeJoin = when (operands[0].asInt()) {
            1 -> Paint.Join.ROUND
            2 -> Paint.Join.BEVEL
            else -> Paint.Join.MITER
        }
    }
    
    private fun setMiterLimit(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        strokePaint.strokeMiter = operands[0].asFloat()
    }
    
    private fun setDashPattern(operands: List<PdfObject>) {
        if (operands.size < 2) return
        val array = operands[0] as? PdfArray ?: return
        val phase = operands[1].asFloat()
        
        if (array.isEmpty()) {
            strokePaint.pathEffect = null
        } else {
            val intervals = FloatArray(array.size) { array.getNumber(it)?.toFloat() ?: 0f }
            strokePaint.pathEffect = DashPathEffect(intervals, phase)
        }
    }
    
    // ==================== 路径构建 ====================
    
    private fun moveTo(operands: List<PdfObject>) {
        if (operands.size < 2) return
        currentX = operands[0].asFloat()
        currentY = operands[1].asFloat()
        pathStartX = currentX
        pathStartY = currentY
        currentPath.moveTo(currentX, currentY)
    }
    
    private fun lineTo(operands: List<PdfObject>) {
        if (operands.size < 2) return
        currentX = operands[0].asFloat()
        currentY = operands[1].asFloat()
        currentPath.lineTo(currentX, currentY)
    }
    
    private fun curveTo(operands: List<PdfObject>) {
        if (operands.size < 6) return
        val x1 = operands[0].asFloat()
        val y1 = operands[1].asFloat()
        val x2 = operands[2].asFloat()
        val y2 = operands[3].asFloat()
        val x3 = operands[4].asFloat()
        val y3 = operands[5].asFloat()
        currentPath.cubicTo(x1, y1, x2, y2, x3, y3)
        currentX = x3
        currentY = y3
    }
    
    private fun curveToV(operands: List<PdfObject>) {
        if (operands.size < 4) return
        val x2 = operands[0].asFloat()
        val y2 = operands[1].asFloat()
        val x3 = operands[2].asFloat()
        val y3 = operands[3].asFloat()
        currentPath.cubicTo(currentX, currentY, x2, y2, x3, y3)
        currentX = x3
        currentY = y3
    }
    
    private fun curveToY(operands: List<PdfObject>) {
        if (operands.size < 4) return
        val x1 = operands[0].asFloat()
        val y1 = operands[1].asFloat()
        val x3 = operands[2].asFloat()
        val y3 = operands[3].asFloat()
        currentPath.cubicTo(x1, y1, x3, y3, x3, y3)
        currentX = x3
        currentY = y3
    }
    
    private fun closePath() {
        currentPath.close()
        currentX = pathStartX
        currentY = pathStartY
    }
    
    private fun rectangle(operands: List<PdfObject>) {
        if (operands.size < 4) return
        val x = operands[0].asFloat()
        val y = operands[1].asFloat()
        val w = operands[2].asFloat()
        val h = operands[3].asFloat()
        currentPath.addRect(x, y, x + w, y + h, Path.Direction.CW)
        currentX = x
        currentY = y
        pathStartX = x
        pathStartY = y
    }
    
    // ==================== 路径绑制 ====================
    
    private fun stroke() {
        canvas.drawPath(currentPath, strokePaint)
        currentPath.reset()
    }
    
    private fun closeAndStroke() {
        currentPath.close()
        canvas.drawPath(currentPath, strokePaint)
        currentPath.reset()
    }
    
    private fun fill(fillType: Path.FillType) {
        currentPath.fillType = fillType
        canvas.drawPath(currentPath, fillPaint)
        currentPath.reset()
    }
    
    private fun fillAndStroke(fillType: Path.FillType) {
        currentPath.fillType = fillType
        canvas.drawPath(currentPath, fillPaint)
        canvas.drawPath(currentPath, strokePaint)
        currentPath.reset()
    }
    
    private fun closeAndFillStroke(fillType: Path.FillType) {
        currentPath.close()
        currentPath.fillType = fillType
        canvas.drawPath(currentPath, fillPaint)
        canvas.drawPath(currentPath, strokePaint)
        currentPath.reset()
    }
    
    private fun endPath() {
        currentPath.reset()
    }
    
    private fun clip(fillType: Path.FillType) {
        currentPath.fillType = fillType
        canvas.clipPath(currentPath)
    }
    
    // ==================== 颜色 ====================
    
    private fun setFillGray(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        val gray = (operands[0].asFloat() * 255).toInt().coerceIn(0, 255)
        currentState.fillColor = Color.rgb(gray, gray, gray)
        fillPaint.color = currentState.fillColor
        textPaint.color = currentState.fillColor
    }
    
    private fun setStrokeGray(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        val gray = (operands[0].asFloat() * 255).toInt().coerceIn(0, 255)
        currentState.strokeColor = Color.rgb(gray, gray, gray)
        strokePaint.color = currentState.strokeColor
    }
    
    private fun setFillRGB(operands: List<PdfObject>) {
        if (operands.size < 3) return
        val r = (operands[0].asFloat() * 255).toInt().coerceIn(0, 255)
        val g = (operands[1].asFloat() * 255).toInt().coerceIn(0, 255)
        val b = (operands[2].asFloat() * 255).toInt().coerceIn(0, 255)
        currentState.fillColor = Color.rgb(r, g, b)
        fillPaint.color = currentState.fillColor
        textPaint.color = currentState.fillColor
    }
    
    private fun setStrokeRGB(operands: List<PdfObject>) {
        if (operands.size < 3) return
        val r = (operands[0].asFloat() * 255).toInt().coerceIn(0, 255)
        val g = (operands[1].asFloat() * 255).toInt().coerceIn(0, 255)
        val b = (operands[2].asFloat() * 255).toInt().coerceIn(0, 255)
        currentState.strokeColor = Color.rgb(r, g, b)
        strokePaint.color = currentState.strokeColor
    }
    
    private fun setFillCMYK(operands: List<PdfObject>) {
        if (operands.size < 4) return
        val c = operands[0].asFloat()
        val m = operands[1].asFloat()
        val y = operands[2].asFloat()
        val k = operands[3].asFloat()
        val r = ((1 - c) * (1 - k) * 255).toInt().coerceIn(0, 255)
        val g = ((1 - m) * (1 - k) * 255).toInt().coerceIn(0, 255)
        val b = ((1 - y) * (1 - k) * 255).toInt().coerceIn(0, 255)
        currentState.fillColor = Color.rgb(r, g, b)
        fillPaint.color = currentState.fillColor
        textPaint.color = currentState.fillColor
    }
    
    private fun setStrokeCMYK(operands: List<PdfObject>) {
        if (operands.size < 4) return
        val c = operands[0].asFloat()
        val m = operands[1].asFloat()
        val y = operands[2].asFloat()
        val k = operands[3].asFloat()
        val r = ((1 - c) * (1 - k) * 255).toInt().coerceIn(0, 255)
        val g = ((1 - m) * (1 - k) * 255).toInt().coerceIn(0, 255)
        val b = ((1 - y) * (1 - k) * 255).toInt().coerceIn(0, 255)
        currentState.strokeColor = Color.rgb(r, g, b)
        strokePaint.color = currentState.strokeColor
    }
    
    private fun setFillColorSpace(operands: List<PdfObject>) {
        // TODO: 实现色彩空间
    }
    
    private fun setStrokeColorSpace(operands: List<PdfObject>) {
        // TODO: 实现色彩空间
    }
    
    private fun setFillColor(operands: List<PdfObject>) {
        when (operands.size) {
            1 -> setFillGray(operands)
            3 -> setFillRGB(operands)
            4 -> setFillCMYK(operands)
        }
    }
    
    private fun setStrokeColor(operands: List<PdfObject>) {
        when (operands.size) {
            1 -> setStrokeGray(operands)
            3 -> setStrokeRGB(operands)
            4 -> setStrokeCMYK(operands)
        }
    }
    
    // ==================== 文本 ====================
    
    private fun beginText() {
        inText = true
        textMatrix.reset()
        lineMatrix.reset()
    }
    
    private fun endText() {
        inText = false
    }
    
    private fun setFont(operands: List<PdfObject>) {
        if (operands.size < 2) return
        val name = (operands[0] as? PdfName)?.name ?: return
        val size = operands[1].asFloat()
        currentState.fontName = name
        currentState.fontSize = size
        currentState.font = fonts[name]
        textPaint.textSize = size
    }
    
    private fun setCharSpace(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        currentState.charSpace = operands[0].asFloat()
    }
    
    private fun setWordSpace(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        currentState.wordSpace = operands[0].asFloat()
    }
    
    private fun setHScale(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        currentState.hScale = operands[0].asFloat() / 100f
        textPaint.textScaleX = currentState.hScale
    }
    
    private fun setLeading(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        currentState.leading = operands[0].asFloat()
    }
    
    private fun setRenderMode(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        currentState.renderMode = operands[0].asInt()
    }
    
    private fun setRise(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        currentState.rise = operands[0].asFloat()
    }
    
    private fun moveTextPos(operands: List<PdfObject>) {
        if (operands.size < 2) return
        val tx = operands[0].asFloat()
        val ty = operands[1].asFloat()
        lineMatrix.preTranslate(tx, ty)
        textMatrix.set(lineMatrix)
    }
    
    private fun moveTextPosAndSetLeading(operands: List<PdfObject>) {
        if (operands.size < 2) return
        currentState.leading = -operands[1].asFloat()
        moveTextPos(operands)
    }
    
    private fun setTextMatrix(operands: List<PdfObject>) {
        if (operands.size < 6) return
        val m = Matrix()
        m.setValues(floatArrayOf(
            operands[0].asFloat(), operands[2].asFloat(), operands[4].asFloat(),
            operands[1].asFloat(), operands[3].asFloat(), operands[5].asFloat(),
            0f, 0f, 1f
        ))
        textMatrix.set(m)
        lineMatrix.set(m)
    }
    
    private fun nextLine() {
        lineMatrix.preTranslate(0f, -currentState.leading)
        textMatrix.set(lineMatrix)
    }
    
    private fun showText(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        val str = operands[0] as? PdfString ?: return
        
        val bytes = str.toBytes()
        val text = currentState.font?.decode(bytes) ?: String(bytes, Charsets.ISO_8859_1)
        
        canvas.save()
        canvas.concat(textMatrix)
        
        // 由于 PDF Y 轴向上，文本需要翻转
        canvas.scale(1f, -1f)
        canvas.translate(0f, currentState.rise)
        
        canvas.drawText(text, 0f, 0f, textPaint)
        
        // 更新文本位置
        val width = textPaint.measureText(text)
        
        canvas.restore()
        
        textMatrix.preTranslate(width / currentState.hScale, 0f)
    }
    
    private fun showTextArray(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        val array = operands[0] as? PdfArray ?: return
        
        for (item in array) {
            when (item) {
                is PdfString -> {
                    val bytes = item.toBytes()
                    val text = currentState.font?.decode(bytes) ?: String(bytes, Charsets.ISO_8859_1)
                    
                    canvas.save()
                    canvas.concat(textMatrix)
                    canvas.scale(1f, -1f)
                    canvas.translate(0f, currentState.rise)
                    
                    canvas.drawText(text, 0f, 0f, textPaint)
                    
                    val width = textPaint.measureText(text)
                    canvas.restore()
                    
                    textMatrix.preTranslate(width / currentState.hScale, 0f)
                }
                is PdfNumber -> {
                    val adjustment = -item.toFloat() * currentState.fontSize / 1000f
                    textMatrix.preTranslate(adjustment, 0f)
                }
                else -> { /* ignore other types */ }
            }
        }
    }
    
    private fun showTextNextLine(operands: List<PdfObject>) {
        nextLine()
        showText(operands)
    }
    
    private fun showTextWithSpacing(operands: List<PdfObject>) {
        if (operands.size < 3) return
        currentState.wordSpace = operands[0].asFloat()
        currentState.charSpace = operands[1].asFloat()
        nextLine()
        showText(listOf(operands[2]))
    }
    
    // ==================== XObject ====================
    
    private fun drawXObject(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        val name = (operands[0] as? PdfName)?.name ?: return
        
        val xObjectDict = when (val xo = resources["XObject"]) {
            is PdfDictionary -> xo
            is PdfIndirectRef -> document.getObject(xo) as? PdfDictionary
            else -> null
        } ?: return
        
        val xObject = when (val obj = xObjectDict[name]) {
            is PdfStream -> obj
            is PdfIndirectRef -> document.getObject(obj) as? PdfStream
            else -> null
        } ?: return
        
        val subtype = xObject.dict.getNameValue("Subtype")
        when (subtype) {
            "Image" -> drawImage(xObject)
            "Form" -> drawForm(xObject)
        }
    }
    
    private fun drawImage(stream: PdfStream) {
        val width = stream.dict.getInt("Width") ?: return
        val height = stream.dict.getInt("Height") ?: return
        val bpc = stream.dict.getInt("BitsPerComponent") ?: 8
        val colorSpace = stream.dict.getNameValue("ColorSpace") ?: "DeviceRGB"
        
        // 解码图像数据
        val data = decodeStream(stream)
        
        // 创建 Bitmap
        val bitmap = try {
            when {
                // DCT (JPEG) - 直接解码
                stream.getFilters().any { it in listOf("DCTDecode", "DCT") } -> {
                    BitmapFactory.decodeByteArray(stream.rawData, 0, stream.rawData.size)
                }
                // RGB
                colorSpace == "DeviceRGB" && bpc == 8 -> {
                    createRGBBitmap(data, width, height)
                }
                // Gray
                colorSpace == "DeviceGray" && bpc == 8 -> {
                    createGrayBitmap(data, width, height)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        
        bitmap?.let {
            val rect = RectF(0f, 0f, 1f, 1f)
            // 图像坐标系也是 Y 向上的，需要翻转
            canvas.save()
            canvas.scale(1f, -1f)
            canvas.translate(0f, -1f)
            canvas.drawBitmap(it, null, rect, null)
            canvas.restore()
        }
    }
    
    private fun createRGBBitmap(data: ByteArray, width: Int, height: Int): Bitmap? {
        if (data.size < width * height * 3) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        for (i in pixels.indices) {
            val offset = i * 3
            if (offset + 2 >= data.size) break
            val r = data[offset].toInt() and 0xFF
            val g = data[offset + 1].toInt() and 0xFF
            val b = data[offset + 2].toInt() and 0xFF
            pixels[i] = Color.rgb(r, g, b)
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    private fun createGrayBitmap(data: ByteArray, width: Int, height: Int): Bitmap? {
        if (data.size < width * height) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        for (i in pixels.indices) {
            if (i >= data.size) break
            val gray = data[i].toInt() and 0xFF
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    private fun drawForm(stream: PdfStream) {
        val formResources = when (val r = stream.dict["Resources"]) {
            is PdfDictionary -> r
            is PdfIndirectRef -> document.getObject(r) as? PdfDictionary
            else -> resources
        } ?: resources
        
        val matrix = stream.dict.getArray("Matrix")?.toMatrix()
        
        canvas.save()
        
        if (matrix != null) {
            val m = Matrix()
            m.setValues(floatArrayOf(
                matrix.a, matrix.c, matrix.e,
                matrix.b, matrix.d, matrix.f,
                0f, 0f, 1f
            ))
            canvas.concat(m)
        }
        
        // 解析并渲染 Form 内容
        val data = decodeStream(stream)
        val formFonts = loadFormFonts(formResources)
        val instructions = ContentParser().parse(data)
        
        val formState = RenderState(canvas, formResources, formFonts, document)
        for (instruction in instructions) {
            formState.execute(instruction)
        }
        
        canvas.restore()
    }
    
    private fun loadFormFonts(formResources: PdfDictionary): Map<String, PdfFont> {
        val formFonts = mutableMapOf<String, PdfFont>()
        
        val fontDict = when (val f = formResources["Font"]) {
            is PdfDictionary -> f
            is PdfIndirectRef -> document.getObject(f) as? PdfDictionary
            else -> null
        } ?: return fonts // 继承父级字体
        
        for ((name, value) in fontDict) {
            val font = when (value) {
                is PdfDictionary -> FontHandler(document).getFont(value)
                is PdfIndirectRef -> {
                    val dict = document.getObject(value) as? PdfDictionary
                    dict?.let { FontHandler(document).getFont(it) }
                }
                else -> null
            }
            font?.let { formFonts[name] = it }
        }
        
        return formFonts
    }
    
    private fun decodeStream(stream: PdfStream): ByteArray {
        val filters = stream.getFilters()
        if (filters.isEmpty()) return stream.rawData
        
        var data = stream.rawData
        for ((index, filter) in filters.withIndex()) {
            // 跳过图像格式的过滤器
            if (filter in listOf("DCTDecode", "DCT", "JPXDecode")) continue
            
            val params = stream.getDecodeParams(index)
            data = StreamFilters.decode(data, filter, params)
        }
        return data
    }
    
    private fun PdfObject.asFloat(): Float = (this as? PdfNumber)?.toFloat() ?: 0f
    private fun PdfObject.asInt(): Int = (this as? PdfNumber)?.toInt() ?: 0
}

/**
 * 图形状态
 */
private data class GraphicsState(
    var lineWidth: Float = 1f,
    var strokeColor: Int = Color.BLACK,
    var fillColor: Int = Color.BLACK,
    var fontName: String = "",
    var fontSize: Float = 12f,
    var font: PdfFont? = null,
    var charSpace: Float = 0f,
    var wordSpace: Float = 0f,
    var hScale: Float = 1f,
    var leading: Float = 0f,
    var rise: Float = 0f,
    var renderMode: Int = 0
)
