package com.pdfrender.engine

import android.graphics.*
import com.pdfcore.content.ContentInstruction
import com.pdfcore.content.ContentParser
import com.pdfcore.font.FontHandler
import com.pdfcore.font.PdfFont
import com.pdfcore.font.SimpleFont
import com.pdfcore.font.StandardEncodings
import com.pdfcore.font.Type0Font
import com.pdfcore.font.EmbeddedFontType
import com.pdfcore.function.PdfFunction
import com.pdfrender.font.FontAssetManager
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
    
    // 待处理的裁切（延迟应用）
    private var pendingClip: Path.FillType? = null
    
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
            
            // 扩展图形状态
            "gs" -> applyExtGState(instruction.operands)
            
            // 着色（渐变）
            "sh" -> drawShading(instruction.operands)
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
        strokePaint.alpha = (currentState.strokeAlpha * 255).toInt()
        fillPaint.color = currentState.fillColor
        fillPaint.alpha = (currentState.fillAlpha * 255).toInt()
        textPaint.alpha = (currentState.fillAlpha * 255).toInt()
        
        // 应用混合模式
        val xfermode = PorterDuffXfermode(currentState.blendMode)
        strokePaint.xfermode = xfermode
        fillPaint.xfermode = xfermode
    }
    
    /**
     * 应用扩展图形状态 (gs 操作符)
     * PDF 32000-1:2008 8.4.5
     */
    private fun applyExtGState(operands: List<PdfObject>) {
        val name = (operands.firstOrNull() as? PdfName)?.name ?: return
        val gsDict = getExtGState(name) ?: return
        
        // ca - 填充透明度 (非描边)
        gsDict.getFloat("ca")?.let { alpha ->
            currentState.fillAlpha = alpha.coerceIn(0f, 1f)
            fillPaint.alpha = (currentState.fillAlpha * 255).toInt()
            textPaint.alpha = (currentState.fillAlpha * 255).toInt()
        }
        
        // CA - 描边透明度
        gsDict.getFloat("CA")?.let { alpha ->
            currentState.strokeAlpha = alpha.coerceIn(0f, 1f)
            strokePaint.alpha = (currentState.strokeAlpha * 255).toInt()
        }
        
        // LW - 线宽
        gsDict.getFloat("LW")?.let { lineWidth ->
            currentState.lineWidth = lineWidth
            strokePaint.strokeWidth = lineWidth
        }
        
        // LC - 线端点样式
        gsDict.getInt("LC")?.let { lineCap ->
            strokePaint.strokeCap = when (lineCap) {
                1 -> Paint.Cap.ROUND
                2 -> Paint.Cap.SQUARE
                else -> Paint.Cap.BUTT
            }
        }
        
        // LJ - 线连接样式
        gsDict.getInt("LJ")?.let { lineJoin ->
            strokePaint.strokeJoin = when (lineJoin) {
                1 -> Paint.Join.ROUND
                2 -> Paint.Join.BEVEL
                else -> Paint.Join.MITER
            }
        }
        
        // ML - 斜接限制
        gsDict.getFloat("ML")?.let { miterLimit ->
            strokePaint.strokeMiter = miterLimit
        }
        
        // BM - 混合模式
        when (val bm = gsDict["BM"]) {
            is PdfName -> currentState.blendMode = parseBlendMode(bm.name)
            is PdfArray -> {
                // 使用数组中的第一个混合模式
                (bm.firstOrNull() as? PdfName)?.let {
                    currentState.blendMode = parseBlendMode(it.name)
                }
            }
            else -> { /* 保持当前混合模式 */ }
        }
        val xfermode = PorterDuffXfermode(currentState.blendMode)
        strokePaint.xfermode = xfermode
        fillPaint.xfermode = xfermode
        
        // SMask - 软遮罩
        when (val smask = gsDict["SMask"]) {
            is PdfName -> {
                if (smask.name == "None") {
                    currentState.softMask = null
                }
            }
            is PdfDictionary -> currentState.softMask = smask
            is PdfIndirectRef -> {
                currentState.softMask = document.getObject(smask) as? PdfDictionary
            }
            else -> { /* 保持当前软遮罩设置 */ }
        }
        
        // AIS - 透明度是否影响源
        // OP, op - 覆印模式
        // OPM - 覆印模式
        // Font - 字体
        // SA - 自动描边调整
        // TK - 文本挖空
    }
    
    /**
     * 获取 ExtGState 字典
     */
    private fun getExtGState(name: String): PdfDictionary? {
        val extGStateDict = when (val egs = resources["ExtGState"]) {
            is PdfDictionary -> egs
            is PdfIndirectRef -> document.getObject(egs) as? PdfDictionary
            else -> null
        } ?: return null
        
        return when (val gs = extGStateDict[name]) {
            is PdfDictionary -> gs
            is PdfIndirectRef -> document.getObject(gs) as? PdfDictionary
            else -> null
        }
    }
    
    /**
     * 解析混合模式
     * PDF 32000-1:2008 11.3.5
     */
    private fun parseBlendMode(name: String): PorterDuff.Mode {
        return when (name) {
            "Normal", "Compatible" -> PorterDuff.Mode.SRC_OVER
            "Multiply" -> PorterDuff.Mode.MULTIPLY
            "Screen" -> PorterDuff.Mode.SCREEN
            "Overlay" -> PorterDuff.Mode.OVERLAY
            "Darken" -> PorterDuff.Mode.DARKEN
            "Lighten" -> PorterDuff.Mode.LIGHTEN
            "ColorDodge" -> PorterDuff.Mode.ADD  // 近似
            "ColorBurn" -> PorterDuff.Mode.MULTIPLY  // 近似
            "HardLight" -> PorterDuff.Mode.OVERLAY  // 近似
            "SoftLight" -> PorterDuff.Mode.OVERLAY  // 近似
            "Difference" -> PorterDuff.Mode.XOR  // 近似
            "Exclusion" -> PorterDuff.Mode.XOR  // 近似
            else -> PorterDuff.Mode.SRC_OVER
        }
    }
    
    /**
     * 绘制着色（渐变） - sh 操作符
     * PDF 32000-1:2008 8.7.4
     */
    private fun drawShading(operands: List<PdfObject>) {
        val name = (operands.firstOrNull() as? PdfName)?.name ?: return
        val shadingDict = getShading(name) ?: return
        
        val shadingType = shadingDict.getInt("ShadingType") ?: return
        val colorSpace = shadingDict.getNameValue("ColorSpace") ?: "DeviceRGB"
        val bbox = shadingDict.getArray("BBox")?.toRectangle()
        
        when (shadingType) {
            1 -> drawFunctionBasedShading(shadingDict, colorSpace, bbox)
            2 -> drawAxialShading(shadingDict, colorSpace, bbox)
            3 -> drawRadialShading(shadingDict, colorSpace, bbox)
            4 -> drawFreeFormGouraudShading(shadingDict, colorSpace, bbox)
            5 -> drawLatticeFormGouraudShading(shadingDict, colorSpace, bbox)
            6 -> drawCoonsPatchShading(shadingDict, colorSpace, bbox)
            7 -> drawTensorProductPatchShading(shadingDict, colorSpace, bbox)
        }
    }
    
    /**
     * 绘制函数基着色 (ShadingType 1)
     * PDF 32000-1:2008 8.7.4.5.2
     */
    private fun drawFunctionBasedShading(
        shadingDict: PdfDictionary,
        colorSpace: String,
        bbox: PdfRectangle?
    ) {
        // 获取域
        val domainArray = shadingDict.getArray("Domain")
        val xmin = domainArray?.getFloat(0) ?: 0f
        val xmax = domainArray?.getFloat(1) ?: 1f
        val ymin = domainArray?.getFloat(2) ?: 0f
        val ymax = domainArray?.getFloat(3) ?: 1f
        
        // 获取矩阵
        val matrixArray = shadingDict.getArray("Matrix")
        val matrix = if (matrixArray != null && matrixArray.size >= 6) {
            Matrix().apply {
                setValues(floatArrayOf(
                    matrixArray.getFloat(0) ?: 1f,
                    matrixArray.getFloat(2) ?: 0f,
                    matrixArray.getFloat(4) ?: 0f,
                    matrixArray.getFloat(1) ?: 0f,
                    matrixArray.getFloat(3) ?: 1f,
                    matrixArray.getFloat(5) ?: 0f,
                    0f, 0f, 1f
                ))
            }
        } else null
        
        // 获取函数
        val functionObj = shadingDict["Function"] ?: return
        val function = PdfFunction.create(functionObj, document) ?: return
        
        // 确定绘制区域
        val bounds = bbox ?: return
        val width = bounds.width.toInt().coerceAtLeast(1)
        val height = bounds.height.toInt().coerceAtLeast(1)
        
        // 创建位图
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // 计算每个像素的颜色
        for (py in 0 until height) {
            for (px in 0 until width) {
                // 将像素坐标转换为域坐标
                val x = xmin + (px.toFloat() / width) * (xmax - xmin)
                val y = ymin + (py.toFloat() / height) * (ymax - ymin)
                
                // 求值函数
                val colorValues = function.evaluate(floatArrayOf(x, y))
                
                // 转换为颜色
                val color = colorValuesToColor(colorValues, colorSpace)
                bitmap.setPixel(px, py, color)
            }
        }
        
        // 绘制位图
        val paint = Paint().apply {
            isAntiAlias = true
            alpha = (currentState.fillAlpha * 255).toInt()
        }
        
        canvas.save()
        if (matrix != null) {
            canvas.concat(matrix)
        }
        canvas.drawBitmap(bitmap, bounds.llx, bounds.lly, paint)
        canvas.restore()
    }
    
    /**
     * 绘制自由形式 Gouraud 着色 (ShadingType 4)
     * PDF 32000-1:2008 8.7.4.5.5
     */
    private fun drawFreeFormGouraudShading(
        shadingDict: PdfDictionary,
        colorSpace: String,
        bbox: PdfRectangle?
    ) {
        // 获取流数据
        val shadingStream = getShadingStream(shadingDict) ?: return
        
        val bitsPerCoordinate = shadingDict.getInt("BitsPerCoordinate") ?: 8
        val bitsPerComponent = shadingDict.getInt("BitsPerComponent") ?: 8
        val bitsPerFlag = shadingDict.getInt("BitsPerFlag") ?: 8
        
        val decodeArray = shadingDict.getArray("Decode") ?: return
        if (decodeArray.size < 6) return
        
        val xmin = decodeArray.getFloat(0) ?: 0f
        val xmax = decodeArray.getFloat(1) ?: 1f
        val ymin = decodeArray.getFloat(2) ?: 0f
        val ymax = decodeArray.getFloat(3) ?: 1f
        
        // 获取颜色分量数
        val numComponents = getColorSpaceComponents(colorSpace)
        
        // 解码流数据
        val data = decodeStream(shadingStream)
        val reader = BitReader(data, bitsPerCoordinate, bitsPerComponent, bitsPerFlag)
        
        // 读取三角形
        val triangles = mutableListOf<ShadingTriangle>()
        val vertices = mutableListOf<ShadingVertex>()
        
        while (reader.hasMore()) {
            val flag = reader.readFlag()
            val x = reader.readCoordinate(xmin, xmax)
            val y = reader.readCoordinate(ymin, ymax)
            val color = reader.readColor(numComponents, decodeArray, 4)
            
            val vertex = ShadingVertex(x, y, color)
            
            when (flag) {
                0 -> {
                    // 新三角形，需要3个顶点
                    vertices.clear()
                    vertices.add(vertex)
                }
                1 -> {
                    // 使用前一个三角形的最后两个顶点
                    if (vertices.size >= 2) {
                        val v1 = vertices[vertices.size - 2]
                        val v2 = vertices[vertices.size - 1]
                        triangles.add(ShadingTriangle(v1, v2, vertex))
                    }
                    vertices.add(vertex)
                }
                2 -> {
                    // 使用前一个三角形的第一个和最后一个顶点
                    if (vertices.size >= 2) {
                        val v0 = vertices[0]
                        val v2 = vertices[vertices.size - 1]
                        triangles.add(ShadingTriangle(v0, v2, vertex))
                    }
                    vertices.add(vertex)
                }
                else -> vertices.add(vertex)
            }
            
            // 凑够3个顶点形成三角形
            if (vertices.size == 3 && flag == 0) {
                triangles.add(ShadingTriangle(vertices[0], vertices[1], vertices[2]))
            }
        }
        
        // 绘制三角形
        drawGouraudTriangles(triangles, bbox)
    }
    
    /**
     * 绘制格子形式 Gouraud 着色 (ShadingType 5)
     * PDF 32000-1:2008 8.7.4.5.6
     */
    private fun drawLatticeFormGouraudShading(
        shadingDict: PdfDictionary,
        colorSpace: String,
        bbox: PdfRectangle?
    ) {
        val shadingStream = getShadingStream(shadingDict) ?: return
        
        val bitsPerCoordinate = shadingDict.getInt("BitsPerCoordinate") ?: 8
        val bitsPerComponent = shadingDict.getInt("BitsPerComponent") ?: 8
        val verticesPerRow = shadingDict.getInt("VerticesPerRow") ?: return
        
        val decodeArray = shadingDict.getArray("Decode") ?: return
        if (decodeArray.size < 6) return
        
        val xmin = decodeArray.getFloat(0) ?: 0f
        val xmax = decodeArray.getFloat(1) ?: 1f
        val ymin = decodeArray.getFloat(2) ?: 0f
        val ymax = decodeArray.getFloat(3) ?: 1f
        
        val numComponents = getColorSpaceComponents(colorSpace)
        
        val data = decodeStream(shadingStream)
        val reader = BitReader(data, bitsPerCoordinate, bitsPerComponent, 0)
        
        // 读取所有顶点
        val allVertices = mutableListOf<ShadingVertex>()
        while (reader.hasMore()) {
            val x = reader.readCoordinate(xmin, xmax)
            val y = reader.readCoordinate(ymin, ymax)
            val color = reader.readColor(numComponents, decodeArray, 4)
            allVertices.add(ShadingVertex(x, y, color))
        }
        
        // 构建三角形
        val triangles = mutableListOf<ShadingTriangle>()
        val numRows = allVertices.size / verticesPerRow
        
        for (row in 0 until numRows - 1) {
            for (col in 0 until verticesPerRow - 1) {
                val idx = row * verticesPerRow + col
                val v0 = allVertices.getOrNull(idx) ?: continue
                val v1 = allVertices.getOrNull(idx + 1) ?: continue
                val v2 = allVertices.getOrNull(idx + verticesPerRow) ?: continue
                val v3 = allVertices.getOrNull(idx + verticesPerRow + 1) ?: continue
                
                // 每个格子分成两个三角形
                triangles.add(ShadingTriangle(v0, v1, v2))
                triangles.add(ShadingTriangle(v1, v3, v2))
            }
        }
        
        drawGouraudTriangles(triangles, bbox)
    }
    
    /**
     * 绘制 Coons 曲面片着色 (ShadingType 6)
     * PDF 32000-1:2008 8.7.4.5.7
     */
    private fun drawCoonsPatchShading(
        shadingDict: PdfDictionary,
        colorSpace: String,
        bbox: PdfRectangle?
    ) {
        val shadingStream = getShadingStream(shadingDict) ?: return
        
        val bitsPerCoordinate = shadingDict.getInt("BitsPerCoordinate") ?: 8
        val bitsPerComponent = shadingDict.getInt("BitsPerComponent") ?: 8
        val bitsPerFlag = shadingDict.getInt("BitsPerFlag") ?: 8
        
        val decodeArray = shadingDict.getArray("Decode") ?: return
        if (decodeArray.size < 6) return
        
        val xmin = decodeArray.getFloat(0) ?: 0f
        val xmax = decodeArray.getFloat(1) ?: 1f
        val ymin = decodeArray.getFloat(2) ?: 0f
        val ymax = decodeArray.getFloat(3) ?: 1f
        
        val numComponents = getColorSpaceComponents(colorSpace)
        
        val data = decodeStream(shadingStream)
        val reader = BitReader(data, bitsPerCoordinate, bitsPerComponent, bitsPerFlag)
        
        // Coons 曲面片有 12 个控制点和 4 个颜色
        val triangles = mutableListOf<ShadingTriangle>()
        var prevPatch: CoonsPatch? = null
        
        while (reader.hasMore()) {
            val flag = reader.readFlag()
            
            val controlPoints = mutableListOf<PointF>()
            val colors = mutableListOf<Int>()
            
            val numPoints = when (flag) {
                0 -> 12 // 新曲面片
                1, 2, 3 -> 8 // 继续曲面片
                else -> continue
            }
            
            for (i in 0 until numPoints) {
                val x = reader.readCoordinate(xmin, xmax)
                val y = reader.readCoordinate(ymin, ymax)
                controlPoints.add(PointF(x, y))
            }
            
            val numColors = if (flag == 0) 4 else 2
            for (i in 0 until numColors) {
                colors.add(reader.readColor(numComponents, decodeArray, 4))
            }
            
            // 构建完整的曲面片
            val patch = buildCoonsPatch(flag, controlPoints, colors, prevPatch) ?: continue
            prevPatch = patch
            
            // 细分为三角形
            subdividePatch(patch, triangles, 4)
        }
        
        drawGouraudTriangles(triangles, bbox)
    }
    
    /**
     * 绘制张量积曲面片着色 (ShadingType 7)
     * PDF 32000-1:2008 8.7.4.5.8
     */
    private fun drawTensorProductPatchShading(
        shadingDict: PdfDictionary,
        colorSpace: String,
        bbox: PdfRectangle?
    ) {
        val shadingStream = getShadingStream(shadingDict) ?: return
        
        val bitsPerCoordinate = shadingDict.getInt("BitsPerCoordinate") ?: 8
        val bitsPerComponent = shadingDict.getInt("BitsPerComponent") ?: 8
        val bitsPerFlag = shadingDict.getInt("BitsPerFlag") ?: 8
        
        val decodeArray = shadingDict.getArray("Decode") ?: return
        if (decodeArray.size < 6) return
        
        val xmin = decodeArray.getFloat(0) ?: 0f
        val xmax = decodeArray.getFloat(1) ?: 1f
        val ymin = decodeArray.getFloat(2) ?: 0f
        val ymax = decodeArray.getFloat(3) ?: 1f
        
        val numComponents = getColorSpaceComponents(colorSpace)
        
        val data = decodeStream(shadingStream)
        val reader = BitReader(data, bitsPerCoordinate, bitsPerComponent, bitsPerFlag)
        
        // 张量积曲面片有 16 个控制点和 4 个颜色
        val triangles = mutableListOf<ShadingTriangle>()
        var prevPatch: TensorPatch? = null
        
        while (reader.hasMore()) {
            val flag = reader.readFlag()
            
            val controlPoints = mutableListOf<PointF>()
            val colors = mutableListOf<Int>()
            
            val numPoints = when (flag) {
                0 -> 16 // 新曲面片
                1, 2, 3 -> 12 // 继续曲面片
                else -> continue
            }
            
            for (i in 0 until numPoints) {
                val x = reader.readCoordinate(xmin, xmax)
                val y = reader.readCoordinate(ymin, ymax)
                controlPoints.add(PointF(x, y))
            }
            
            val numColors = if (flag == 0) 4 else 2
            for (i in 0 until numColors) {
                colors.add(reader.readColor(numComponents, decodeArray, 4))
            }
            
            // 构建完整的曲面片
            val patch = buildTensorPatch(flag, controlPoints, colors, prevPatch) ?: continue
            prevPatch = patch
            
            // 细分为三角形
            subdivideTensorPatch(patch, triangles, 4)
        }
        
        drawGouraudTriangles(triangles, bbox)
    }
    
    /**
     * 获取颜色空间分量数
     */
    private fun getColorSpaceComponents(colorSpace: String): Int {
        return when {
            colorSpace.contains("Gray") -> 1
            colorSpace.contains("RGB") -> 3
            colorSpace.contains("CMYK") -> 4
            else -> 3
        }
    }
    
    /**
     * 获取着色流
     * 对于 ShadingType 4-7，需要从原始的 Shading 对象中获取流数据
     */
    private fun getShadingStream(shadingDict: PdfDictionary): PdfStream? {
        // 首先检查是否有缓存的流引用
        val cachedStream = shadingStreamCache[System.identityHashCode(shadingDict)]
        if (cachedStream != null) {
            return cachedStream
        }
        
        // 如果没有缓存，尝试从资源中重新获取
        // 注意：这种情况下可能找不到流，因为 shadingDict 可能已经是从流中提取的
        val shadingResources = when (val sh = resources["Shading"]) {
            is PdfDictionary -> sh
            is PdfIndirectRef -> document.getObject(sh) as? PdfDictionary
            else -> null
        } ?: return null
        
        // 遍历所有着色资源，查找匹配的流
        for ((_, value) in shadingResources) {
            val stream = when (value) {
                is PdfStream -> if (value.dict == shadingDict) value else null
                is PdfIndirectRef -> {
                    val obj = document.getObject(value)
                    if (obj is PdfStream && obj.dict == shadingDict) obj else null
                }
                else -> null
            }
            if (stream != null) {
                return stream
            }
        }
        
        return null
    }
    
    // 着色流缓存：用于存储从资源中获取的 PdfStream
    private val shadingStreamCache = mutableMapOf<Int, PdfStream>()
    
    /**
     * 颜色值转换为颜色
     */
    private fun colorValuesToColor(values: FloatArray, colorSpace: String): Int {
        return when {
            colorSpace.contains("Gray") -> {
                val gray = ((values.getOrElse(0) { 0f }) * 255).toInt().coerceIn(0, 255)
                Color.rgb(gray, gray, gray)
            }
            colorSpace.contains("CMYK") -> {
                val c = values.getOrElse(0) { 0f }
                val m = values.getOrElse(1) { 0f }
                val y = values.getOrElse(2) { 0f }
                val k = values.getOrElse(3) { 0f }
                cmykToRgb(c, m, y, k)
            }
            else -> {
                val r = ((values.getOrElse(0) { 0f }) * 255).toInt().coerceIn(0, 255)
                val g = ((values.getOrElse(1) { 0f }) * 255).toInt().coerceIn(0, 255)
                val b = ((values.getOrElse(2) { 0f }) * 255).toInt().coerceIn(0, 255)
                Color.rgb(r, g, b)
            }
        }
    }
    
    /**
     * 绘制 Gouraud 三角形
     */
    private fun drawGouraudTriangles(triangles: List<ShadingTriangle>, bbox: PdfRectangle?) {
        if (triangles.isEmpty()) return
        
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            alpha = (currentState.fillAlpha * 255).toInt()
        }
        
        for (triangle in triangles) {
            drawGouraudTriangle(triangle, paint)
        }
    }
    
    /**
     * 绘制单个 Gouraud 三角形
     */
    private fun drawGouraudTriangle(triangle: ShadingTriangle, paint: Paint) {
        val path = Path().apply {
            moveTo(triangle.v0.x, triangle.v0.y)
            lineTo(triangle.v1.x, triangle.v1.y)
            lineTo(triangle.v2.x, triangle.v2.y)
            close()
        }
        
        // 创建渐变着色器
        val colors = intArrayOf(triangle.v0.color, triangle.v1.color, triangle.v2.color)
        val positions = floatArrayOf(
            triangle.v0.x, triangle.v0.y,
            triangle.v1.x, triangle.v1.y,
            triangle.v2.x, triangle.v2.y
        )
        
        // 使用中心颜色近似（简化实现）
        val avgColor = blendColors(colors[0], colors[1], colors[2])
        paint.color = avgColor
        
        canvas.drawPath(path, paint)
    }
    
    /**
     * 混合三个颜色
     */
    private fun blendColors(c0: Int, c1: Int, c2: Int): Int {
        val r = (Color.red(c0) + Color.red(c1) + Color.red(c2)) / 3
        val g = (Color.green(c0) + Color.green(c1) + Color.green(c2)) / 3
        val b = (Color.blue(c0) + Color.blue(c1) + Color.blue(c2)) / 3
        return Color.rgb(r, g, b)
    }
    
    /**
     * 构建 Coons 曲面片
     */
    private fun buildCoonsPatch(
        flag: Int,
        points: List<PointF>,
        colors: List<Int>,
        prevPatch: CoonsPatch?
    ): CoonsPatch? {
        if (flag == 0) {
            if (points.size < 12 || colors.size < 4) return null
            return CoonsPatch(
                points.toTypedArray(),
                colors.toIntArray()
            )
        }
        
        if (prevPatch == null || points.size < 8 || colors.size < 2) return null
        
        // 从前一个曲面片继承部分控制点和颜色
        val allPoints = Array(12) { PointF(0f, 0f) }
        val allColors = IntArray(4)
        
        when (flag) {
            1 -> {
                // 共享边 3-0
                allPoints[0] = prevPatch.controlPoints[3]
                allPoints[1] = prevPatch.controlPoints[4]
                allPoints[2] = prevPatch.controlPoints[5]
                allPoints[3] = prevPatch.controlPoints[6]
                allColors[0] = prevPatch.colors[1]
                allColors[3] = prevPatch.colors[2]
            }
            2 -> {
                // 共享边 6-3
                allPoints[0] = prevPatch.controlPoints[6]
                allPoints[1] = prevPatch.controlPoints[7]
                allPoints[2] = prevPatch.controlPoints[8]
                allPoints[3] = prevPatch.controlPoints[9]
                allColors[0] = prevPatch.colors[2]
                allColors[3] = prevPatch.colors[3]
            }
            3 -> {
                // 共享边 9-0
                allPoints[0] = prevPatch.controlPoints[9]
                allPoints[1] = prevPatch.controlPoints[10]
                allPoints[2] = prevPatch.controlPoints[11]
                allPoints[3] = prevPatch.controlPoints[0]
                allColors[0] = prevPatch.colors[3]
                allColors[3] = prevPatch.colors[0]
            }
            else -> return null
        }
        
        // 复制新的控制点
        for (i in points.indices) {
            allPoints[4 + i] = points[i]
        }
        
        // 复制新的颜色
        allColors[1] = colors[0]
        allColors[2] = colors[1]
        
        return CoonsPatch(allPoints, allColors)
    }
    
    /**
     * 细分曲面片为三角形
     */
    private fun subdividePatch(
        patch: CoonsPatch,
        triangles: MutableList<ShadingTriangle>,
        subdivisions: Int
    ) {
        // 简化实现：将曲面片近似为两个三角形
        val v0 = ShadingVertex(patch.controlPoints[0].x, patch.controlPoints[0].y, patch.colors[0])
        val v1 = ShadingVertex(patch.controlPoints[3].x, patch.controlPoints[3].y, patch.colors[1])
        val v2 = ShadingVertex(patch.controlPoints[6].x, patch.controlPoints[6].y, patch.colors[2])
        val v3 = ShadingVertex(patch.controlPoints[9].x, patch.controlPoints[9].y, patch.colors[3])
        
        triangles.add(ShadingTriangle(v0, v1, v2))
        triangles.add(ShadingTriangle(v0, v2, v3))
    }
    
    /**
     * 构建张量积曲面片
     */
    private fun buildTensorPatch(
        flag: Int,
        points: List<PointF>,
        colors: List<Int>,
        prevPatch: TensorPatch?
    ): TensorPatch? {
        if (flag == 0) {
            if (points.size < 16 || colors.size < 4) return null
            return TensorPatch(
                points.toTypedArray(),
                colors.toIntArray()
            )
        }
        
        if (prevPatch == null || points.size < 12 || colors.size < 2) return null
        
        // 简化实现
        val allPoints = Array(16) { PointF(0f, 0f) }
        val allColors = IntArray(4)
        
        // 继承部分控制点
        when (flag) {
            1 -> {
                for (i in 0..3) allPoints[i] = prevPatch.controlPoints[12 + i]
                allColors[0] = prevPatch.colors[3]
                allColors[1] = prevPatch.colors[2]
            }
            2 -> {
                for (i in 0..3) allPoints[i * 4] = prevPatch.controlPoints[(3 - i) * 4 + 3]
                allColors[0] = prevPatch.colors[1]
                allColors[3] = prevPatch.colors[2]
            }
            3 -> {
                for (i in 0..3) allPoints[i] = prevPatch.controlPoints[3 - i]
                allColors[0] = prevPatch.colors[0]
                allColors[1] = prevPatch.colors[1]
            }
            else -> return null
        }
        
        // 复制新的控制点
        val maxPoints = minOf(points.size, 12)
        for (i in 0 until maxPoints) {
            allPoints[4 + i] = points[i]
        }
        
        allColors[2] = colors.getOrElse(0) { 0 }
        allColors[3] = colors.getOrElse(1) { 0 }
        
        return TensorPatch(allPoints, allColors)
    }
    
    /**
     * 细分张量积曲面片为三角形
     */
    private fun subdivideTensorPatch(
        patch: TensorPatch,
        triangles: MutableList<ShadingTriangle>,
        subdivisions: Int
    ) {
        // 简化实现：将曲面片近似为两个三角形
        val v0 = ShadingVertex(patch.controlPoints[0].x, patch.controlPoints[0].y, patch.colors[0])
        val v1 = ShadingVertex(patch.controlPoints[3].x, patch.controlPoints[3].y, patch.colors[1])
        val v2 = ShadingVertex(patch.controlPoints[15].x, patch.controlPoints[15].y, patch.colors[2])
        val v3 = ShadingVertex(patch.controlPoints[12].x, patch.controlPoints[12].y, patch.colors[3])
        
        triangles.add(ShadingTriangle(v0, v1, v2))
        triangles.add(ShadingTriangle(v0, v2, v3))
    }
    
    /**
     * 着色三角形
     */
    private data class ShadingTriangle(
        val v0: ShadingVertex,
        val v1: ShadingVertex,
        val v2: ShadingVertex
    )
    
    /**
     * 着色顶点
     */
    private data class ShadingVertex(
        val x: Float,
        val y: Float,
        val color: Int
    )
    
    /**
     * Coons 曲面片
     */
    private data class CoonsPatch(
        val controlPoints: Array<PointF>,
        val colors: IntArray
    )
    
    /**
     * 张量积曲面片
     */
    private data class TensorPatch(
        val controlPoints: Array<PointF>,
        val colors: IntArray
    )
    
    /**
     * 位读取器（用于着色数据）
     */
    private class BitReader(
        private val data: ByteArray,
        private val bitsPerCoordinate: Int,
        private val bitsPerComponent: Int,
        private val bitsPerFlag: Int
    ) {
        private var bytePos = 0
        private var bitPos = 0
        
        fun hasMore(): Boolean = bytePos < data.size
        
        fun readFlag(): Int {
            if (bitsPerFlag == 0) return 0
            return readBits(bitsPerFlag)
        }
        
        fun readCoordinate(min: Float, max: Float): Float {
            val value = readBits(bitsPerCoordinate)
            val maxVal = (1 shl bitsPerCoordinate) - 1
            return min + value.toFloat() / maxVal * (max - min)
        }
        
        fun readColor(numComponents: Int, decode: PdfArray, startIndex: Int): Int {
            val components = FloatArray(numComponents)
            for (i in 0 until numComponents) {
                val value = readBits(bitsPerComponent)
                val maxVal = (1 shl bitsPerComponent) - 1
                val min = decode.getFloat(startIndex + i * 2) ?: 0f
                val max = decode.getFloat(startIndex + i * 2 + 1) ?: 1f
                components[i] = min + value.toFloat() / maxVal * (max - min)
            }
            
            return when (numComponents) {
                1 -> {
                    val gray = (components[0] * 255).toInt().coerceIn(0, 255)
                    Color.rgb(gray, gray, gray)
                }
                4 -> {
                    val c = components[0]
                    val m = components[1]
                    val y = components[2]
                    val k = components[3]
                    val r = ((1 - c) * (1 - k) * 255).toInt().coerceIn(0, 255)
                    val g = ((1 - m) * (1 - k) * 255).toInt().coerceIn(0, 255)
                    val b = ((1 - y) * (1 - k) * 255).toInt().coerceIn(0, 255)
                    Color.rgb(r, g, b)
                }
                else -> {
                    val r = (components.getOrElse(0) { 0f } * 255).toInt().coerceIn(0, 255)
                    val g = (components.getOrElse(1) { 0f } * 255).toInt().coerceIn(0, 255)
                    val b = (components.getOrElse(2) { 0f } * 255).toInt().coerceIn(0, 255)
                    Color.rgb(r, g, b)
                }
            }
        }
        
        private fun readBits(count: Int): Int {
            if (count == 0) return 0
            
            var result = 0
            var remaining = count
            
            while (remaining > 0 && bytePos < data.size) {
                val available = 8 - bitPos
                val toRead = minOf(remaining, available)
                
                val shift = available - toRead
                val mask = ((1 shl toRead) - 1)
                val bits = (data[bytePos].toInt() shr shift) and mask
                
                result = (result shl toRead) or bits
                remaining -= toRead
                bitPos += toRead
                
                if (bitPos >= 8) {
                    bitPos = 0
                    bytePos++
                }
            }
            
            return result
        }
    }
    
    /**
     * 获取 Shading 字典
     * 同时缓存 PdfStream 对象以便后续获取流数据
     */
    private fun getShading(name: String): PdfDictionary? {
        val shadingResources = when (val sh = resources["Shading"]) {
            is PdfDictionary -> sh
            is PdfIndirectRef -> document.getObject(sh) as? PdfDictionary
            else -> null
        } ?: return null
        
        val shadingObj = shadingResources[name] ?: return null
        
        return when (shadingObj) {
            is PdfDictionary -> shadingObj
            is PdfStream -> {
                // 缓存流对象，以便 getShadingStream() 可以获取
                shadingStreamCache[System.identityHashCode(shadingObj.dict)] = shadingObj
                shadingObj.dict
            }
            is PdfIndirectRef -> {
                when (val obj = document.getObject(shadingObj)) {
                    is PdfDictionary -> obj
                    is PdfStream -> {
                        // 缓存流对象
                        shadingStreamCache[System.identityHashCode(obj.dict)] = obj
                        obj.dict
                    }
                    else -> null
                }
            }
            else -> null
        }
    }
    
    /**
     * 绘制轴向渐变 (ShadingType 2)
     * PDF 32000-1:2008 8.7.4.5.3
     */
    private fun drawAxialShading(
        shadingDict: PdfDictionary,
        colorSpace: String,
        bbox: PdfRectangle?
    ) {
        val coords = shadingDict.getArray("Coords") ?: return
        if (coords.size < 4) return
        
        val x0 = coords.getFloat(0) ?: 0f
        val y0 = coords.getFloat(1) ?: 0f
        val x1 = coords.getFloat(2) ?: 0f
        val y1 = coords.getFloat(3) ?: 0f
        
        // 获取函数定义
        val function = shadingDict["Function"] ?: return
        val domain = shadingDict.getArray("Domain")
        val t0 = domain?.getFloat(0) ?: 0f
        val t1 = domain?.getFloat(1) ?: 1f
        
        // 计算渐变颜色
        val colors = evaluateShadingFunction(function, colorSpace, t0, t1)
        
        // 创建 LinearGradient
        val positions = floatArrayOf(0f, 1f)
        val shader = LinearGradient(
            x0, y0, x1, y1,
            colors,
            positions,
            Shader.TileMode.CLAMP
        )
        
        // 检查是否需要扩展
        val extend = shadingDict.getArray("Extend")
        val extendStart = extend?.getBoolean(0)?.value ?: false
        val extendEnd = extend?.getBoolean(1)?.value ?: false
        
        // 绘制渐变
        val paint = Paint().apply {
            this.shader = shader
            isAntiAlias = true
            alpha = (currentState.fillAlpha * 255).toInt()
        }
        
        if (bbox != null) {
            canvas.drawRect(bbox.llx, bbox.lly, bbox.urx, bbox.ury, paint)
        } else {
            // 如果没有边界框，绘制一个足够大的区域
            canvas.save()
            val bounds = Rect()
            canvas.getClipBounds(bounds)
            canvas.drawRect(bounds, paint)
            canvas.restore()
        }
    }
    
    /**
     * 绘制径向渐变 (ShadingType 3)
     * PDF 32000-1:2008 8.7.4.5.4
     */
    private fun drawRadialShading(
        shadingDict: PdfDictionary,
        colorSpace: String,
        bbox: PdfRectangle?
    ) {
        val coords = shadingDict.getArray("Coords") ?: return
        if (coords.size < 6) return
        
        val x0 = coords.getFloat(0) ?: 0f
        val y0 = coords.getFloat(1) ?: 0f
        val r0 = coords.getFloat(2) ?: 0f
        val x1 = coords.getFloat(3) ?: 0f
        val y1 = coords.getFloat(4) ?: 0f
        val r1 = coords.getFloat(5) ?: 0f
        
        // 获取函数定义
        val function = shadingDict["Function"] ?: return
        val domain = shadingDict.getArray("Domain")
        val t0 = domain?.getFloat(0) ?: 0f
        val t1 = domain?.getFloat(1) ?: 1f
        
        // 计算渐变颜色
        val colors = evaluateShadingFunction(function, colorSpace, t0, t1)
        
        // 创建 RadialGradient
        // 注意：Android RadialGradient 只支持单圆心，这里使用较大的圆
        val positions = floatArrayOf(0f, 1f)
        val shader = RadialGradient(
            x1, y1, r1.coerceAtLeast(0.01f),
            colors,
            positions,
            Shader.TileMode.CLAMP
        )
        
        val paint = Paint().apply {
            this.shader = shader
            isAntiAlias = true
            alpha = (currentState.fillAlpha * 255).toInt()
        }
        
        if (bbox != null) {
            canvas.drawRect(bbox.llx, bbox.lly, bbox.urx, bbox.ury, paint)
        } else {
            canvas.save()
            val bounds = Rect()
            canvas.getClipBounds(bounds)
            canvas.drawRect(bounds, paint)
            canvas.restore()
        }
    }
    
    /**
     * 计算着色函数的颜色值
     */
    private fun evaluateShadingFunction(
        function: PdfObject,
        colorSpace: String,
        t0: Float,
        t1: Float
    ): IntArray {
        // 简化处理：直接从函数字典中获取 C0 和 C1
        val funcDict = when (function) {
            is PdfDictionary -> function
            is PdfIndirectRef -> document.getObject(function) as? PdfDictionary
            else -> null
        }
        
        if (funcDict != null) {
            val c0 = funcDict.getArray("C0")
            val c1 = funcDict.getArray("C1")
            
            val startColor = colorFromArray(c0, colorSpace)
            val endColor = colorFromArray(c1, colorSpace)
            
            return intArrayOf(startColor, endColor)
        }
        
        // 默认黑到白渐变
        return intArrayOf(Color.BLACK, Color.WHITE)
    }
    
    /**
     * 从数组中获取颜色
     */
    private fun colorFromArray(array: PdfArray?, colorSpace: String): Int {
        if (array == null) return Color.BLACK
        
        return when (colorSpace) {
            "DeviceGray" -> {
                val gray = (array.getFloat(0) ?: 0f) * 255
                Color.rgb(gray.toInt(), gray.toInt(), gray.toInt())
            }
            "DeviceRGB" -> {
                val r = ((array.getFloat(0) ?: 0f) * 255).toInt().coerceIn(0, 255)
                val g = ((array.getFloat(1) ?: 0f) * 255).toInt().coerceIn(0, 255)
                val b = ((array.getFloat(2) ?: 0f) * 255).toInt().coerceIn(0, 255)
                Color.rgb(r, g, b)
            }
            "DeviceCMYK" -> {
                val c = array.getFloat(0) ?: 0f
                val m = array.getFloat(1) ?: 0f
                val y = array.getFloat(2) ?: 0f
                val k = array.getFloat(3) ?: 0f
                cmykToRgb(c, m, y, k)
            }
            else -> Color.BLACK
        }
    }
    
    /**
     * CMYK 转 RGB
     */
    private fun cmykToRgb(c: Float, m: Float, y: Float, k: Float): Int {
        val r = ((1 - c) * (1 - k) * 255).toInt().coerceIn(0, 255)
        val g = ((1 - m) * (1 - k) * 255).toInt().coerceIn(0, 255)
        val b = ((1 - y) * (1 - k) * 255).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
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
        applyPendingClip()
        canvas.drawPath(currentPath, strokePaint)
        currentPath.reset()
    }
    
    private fun closeAndStroke() {
        currentPath.close()
        applyPendingClip()
        canvas.drawPath(currentPath, strokePaint)
        currentPath.reset()
    }
    
    private fun fill(fillType: Path.FillType) {
        applyPendingClip()
        currentPath.fillType = fillType
        canvas.drawPath(currentPath, fillPaint)
        currentPath.reset()
    }
    
    private fun fillAndStroke(fillType: Path.FillType) {
        applyPendingClip()
        currentPath.fillType = fillType
        canvas.drawPath(currentPath, fillPaint)
        canvas.drawPath(currentPath, strokePaint)
        currentPath.reset()
    }
    
    private fun closeAndFillStroke(fillType: Path.FillType) {
        currentPath.close()
        applyPendingClip()
        currentPath.fillType = fillType
        canvas.drawPath(currentPath, fillPaint)
        canvas.drawPath(currentPath, strokePaint)
        currentPath.reset()
    }
    
    private fun endPath() {
        // 在路径结束时应用待处理的裁切
        applyPendingClip()
        currentPath.reset()
    }
    
    private fun clip(fillType: Path.FillType) {
        // 延迟应用裁切，在路径结束操作符后生效
        pendingClip = fillType
    }
    
    /**
     * 应用待处理的裁切路径
     */
    private fun applyPendingClip() {
        pendingClip?.let { fillType ->
            if (!currentPath.isEmpty) {
                val clipPath = Path(currentPath)
                clipPath.fillType = fillType
                canvas.clipPath(clipPath)
            }
        }
        pendingClip = null
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
        if (operands.isEmpty()) return
        val csName = (operands[0] as? PdfName)?.name ?: return
        currentState.fillColorSpace = parseColorSpace(csName)
    }
    
    private fun setStrokeColorSpace(operands: List<PdfObject>) {
        if (operands.isEmpty()) return
        val csName = (operands[0] as? PdfName)?.name ?: return
        currentState.strokeColorSpace = parseColorSpace(csName)
    }
    
    /**
     * 解析颜色空间
     */
    private fun parseColorSpace(name: String): ColorSpaceInfo {
        return when (name) {
            "DeviceGray", "G" -> ColorSpaceInfo.DeviceGray
            "DeviceRGB", "RGB" -> ColorSpaceInfo.DeviceRGB
            "DeviceCMYK", "CMYK" -> ColorSpaceInfo.DeviceCMYK
            "Pattern" -> ColorSpaceInfo.Pattern(null)
            else -> {
                // 查找资源中的颜色空间定义
                val csDict = when (val cs = resources["ColorSpace"]) {
                    is PdfDictionary -> cs
                    is PdfIndirectRef -> document.getObject(cs) as? PdfDictionary
                    else -> null
                }
                
                val csObj = csDict?.get(name)?.let { resolveObject(it) }
                parseColorSpaceObject(csObj) ?: ColorSpaceInfo.DeviceRGB
            }
        }
    }
    
    /**
     * 解析颜色空间对象
     */
    private fun parseColorSpaceObject(obj: PdfObject?): ColorSpaceInfo? {
        return when (obj) {
            is PdfName -> parseColorSpace(obj.name)
            is PdfArray -> {
                val csType = (obj.firstOrNull() as? PdfName)?.name ?: return null
                when (csType) {
                    "Indexed", "I" -> {
                        if (obj.size < 4) return null
                        val base = parseColorSpaceObject(obj[1])
                        val hival = (obj[2] as? PdfNumber)?.toInt() ?: 255
                        val lookup = when (val lookupObj = resolveObject(obj[3])) {
                            is PdfString -> lookupObj.toBytes()
                            is PdfStream -> decodeStream(lookupObj)
                            else -> ByteArray(0)
                        }
                        if (base != null) {
                            ColorSpaceInfo.Indexed(base, hival, lookup)
                        } else null
                    }
                    "ICCBased" -> {
                        val profileStream = resolveObject(obj.getOrNull(1)) as? PdfStream
                        val n = profileStream?.dict?.getInt("N") ?: 3
                        val alternate = profileStream?.dict?.get("Alternate")?.let {
                            parseColorSpaceObject(resolveObject(it))
                        }
                        ColorSpaceInfo.ICCBased(n, alternate)
                    }
                    "Separation" -> {
                        if (obj.size < 4) return null
                        val sepName = (obj[1] as? PdfName)?.name ?: ""
                        val alternate = parseColorSpaceObject(obj[2])
                        val tintTransform = obj[3]
                        if (alternate != null) {
                            ColorSpaceInfo.Separation(sepName, alternate, tintTransform)
                        } else null
                    }
                    "DeviceN" -> {
                        if (obj.size < 4) return null
                        val names = (obj[1] as? PdfArray)?.mapNotNull { (it as? PdfName)?.name } ?: emptyList()
                        val alternate = parseColorSpaceObject(obj[2])
                        val tintTransform = obj[3]
                        if (alternate != null) {
                            ColorSpaceInfo.DeviceN(names, alternate, tintTransform)
                        } else null
                    }
                    "Pattern" -> {
                        val underlying = if (obj.size > 1) {
                            parseColorSpaceObject(obj[1])
                        } else null
                        ColorSpaceInfo.Pattern(underlying)
                    }
                    "DeviceGray" -> ColorSpaceInfo.DeviceGray
                    "DeviceRGB" -> ColorSpaceInfo.DeviceRGB
                    "DeviceCMYK" -> ColorSpaceInfo.DeviceCMYK
                    "CalGray", "CalRGB", "Lab" -> {
                        // 简化处理：映射到设备颜色空间
                        when (csType) {
                            "CalGray" -> ColorSpaceInfo.DeviceGray
                            else -> ColorSpaceInfo.DeviceRGB
                        }
                    }
                    else -> null
                }
            }
            else -> null
        }
    }
    
    /**
     * 解析对象引用
     */
    private fun resolveObject(obj: PdfObject?): PdfObject? {
        return when (obj) {
            is PdfIndirectRef -> document.getObject(obj)
            else -> obj
        }
    }
    
    private fun setFillColor(operands: List<PdfObject>) {
        val color = convertColorSpace(operands, currentState.fillColorSpace)
        currentState.fillColor = color
        fillPaint.color = color
        fillPaint.alpha = (currentState.fillAlpha * 255).toInt()
        textPaint.color = color
        textPaint.alpha = (currentState.fillAlpha * 255).toInt()
    }
    
    private fun setStrokeColor(operands: List<PdfObject>) {
        val color = convertColorSpace(operands, currentState.strokeColorSpace)
        currentState.strokeColor = color
        strokePaint.color = color
        strokePaint.alpha = (currentState.strokeAlpha * 255).toInt()
    }
    
    /**
     * 根据颜色空间转换颜色
     */
    private fun convertColorSpace(operands: List<PdfObject>, colorSpace: ColorSpaceInfo): Int {
        return when (colorSpace) {
            is ColorSpaceInfo.DeviceGray -> {
                val gray = ((operands.firstOrNull() as? PdfNumber)?.toFloat() ?: 0f)
                val grayInt = (gray * 255).toInt().coerceIn(0, 255)
                Color.rgb(grayInt, grayInt, grayInt)
            }
            is ColorSpaceInfo.DeviceRGB -> {
                if (operands.size < 3) return Color.BLACK
                val r = ((operands[0] as? PdfNumber)?.toFloat() ?: 0f)
                val g = ((operands[1] as? PdfNumber)?.toFloat() ?: 0f)
                val b = ((operands[2] as? PdfNumber)?.toFloat() ?: 0f)
                Color.rgb(
                    (r * 255).toInt().coerceIn(0, 255),
                    (g * 255).toInt().coerceIn(0, 255),
                    (b * 255).toInt().coerceIn(0, 255)
                )
            }
            is ColorSpaceInfo.DeviceCMYK -> {
                if (operands.size < 4) return Color.BLACK
                val c = (operands[0] as? PdfNumber)?.toFloat() ?: 0f
                val m = (operands[1] as? PdfNumber)?.toFloat() ?: 0f
                val y = (operands[2] as? PdfNumber)?.toFloat() ?: 0f
                val k = (operands[3] as? PdfNumber)?.toFloat() ?: 0f
                cmykToRgb(c, m, y, k)
            }
            is ColorSpaceInfo.Indexed -> {
                val index = ((operands.firstOrNull() as? PdfNumber)?.toInt() ?: 0)
                    .coerceIn(0, colorSpace.hival)
                lookupIndexedColor(colorSpace, index)
            }
            is ColorSpaceInfo.ICCBased -> {
                // 简化处理：根据组件数量映射到设备颜色空间
                when (colorSpace.numComponents) {
                    1 -> convertColorSpace(operands, ColorSpaceInfo.DeviceGray)
                    3 -> convertColorSpace(operands, ColorSpaceInfo.DeviceRGB)
                    4 -> convertColorSpace(operands, ColorSpaceInfo.DeviceCMYK)
                    else -> Color.BLACK
                }
            }
            is ColorSpaceInfo.Separation, is ColorSpaceInfo.DeviceN -> {
                // 简化处理：使用灰度近似
                val tint = (operands.firstOrNull() as? PdfNumber)?.toFloat() ?: 0f
                val grayInt = ((1 - tint) * 255).toInt().coerceIn(0, 255)
                Color.rgb(grayInt, grayInt, grayInt)
            }
            is ColorSpaceInfo.Pattern -> {
                // Pattern 颜色空间需要特殊处理
                Color.TRANSPARENT
            }
        }
    }
    
    /**
     * 从索引颜色空间查表获取颜色
     */
    private fun lookupIndexedColor(colorSpace: ColorSpaceInfo.Indexed, index: Int): Int {
        val lookup = colorSpace.lookup
        
        return when (colorSpace.base) {
            is ColorSpaceInfo.DeviceGray -> {
                if (index >= lookup.size) return Color.BLACK
                val gray = lookup[index].toInt() and 0xFF
                Color.rgb(gray, gray, gray)
            }
            is ColorSpaceInfo.DeviceRGB -> {
                val offset = index * 3
                if (offset + 2 >= lookup.size) return Color.BLACK
                val r = lookup[offset].toInt() and 0xFF
                val g = lookup[offset + 1].toInt() and 0xFF
                val b = lookup[offset + 2].toInt() and 0xFF
                Color.rgb(r, g, b)
            }
            is ColorSpaceInfo.DeviceCMYK -> {
                val offset = index * 4
                if (offset + 3 >= lookup.size) return Color.BLACK
                val c = (lookup[offset].toInt() and 0xFF) / 255f
                val m = (lookup[offset + 1].toInt() and 0xFF) / 255f
                val y = (lookup[offset + 2].toInt() and 0xFF) / 255f
                val k = (lookup[offset + 3].toInt() and 0xFF) / 255f
                cmykToRgb(c, m, y, k)
            }
            else -> Color.BLACK
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
        
        // 尝试使用嵌入字体
        val font = currentState.font
        
        // 1. 简单字体 (Type1, TrueType, Type3)
        if (font is SimpleFont && font.hasEmbeddedFont()) {
            val typeface = FontMapper.createFromEmbedded(font)
            if (typeface != null) {
                textPaint.typeface = typeface
                return
            }
        }
        
        // 2. Type0 复合字体 (CID 字体)
        if (font is Type0Font) {
            val typeface = FontMapper.createFromType0Font(font)
            if (typeface != null) {
                textPaint.typeface = typeface
                return
            }
        }
        
        // 3. 回退：根据字体的 BaseFont 名称设置正确的 Typeface（字体样式）
        val baseFont = font?.baseFont ?: ""
        textPaint.typeface = FontMapper.mapToTypeface(baseFont)
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
        
        // 使用字体回退机制绘制文本
        val width = drawTextWithFallback(text, 0f, 0f, textPaint)
        
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
                    
                    // 使用字体回退机制绘制文本
                    val width = drawTextWithFallback(text, 0f, 0f, textPaint)
                    
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
    
    /**
     * 使用字体回退机制绘制文本
     * 对于当前字体无法渲染的字符，使用系统 CJK 字体回退
     * 
     * @return 绘制的文本总宽度
     */
    private fun drawTextWithFallback(text: String, x: Float, y: Float, paint: Paint): Float {
        if (text.isEmpty()) return 0f
        
        // 首先转换 PUA 字符为标准 Unicode（作为安全层）
        val convertedText = convertPUAInText(text)
        
        // 检查是否需要字体回退
        val needsFallback = FontFallback.needsFallback(convertedText, paint)
        
        if (!needsFallback) {
            // 不需要回退，直接绘制
            canvas.drawText(convertedText, x, y, paint)
            return paint.measureText(convertedText)
        }
        
        // 需要回退：分段绘制文本
        val segments = FontFallback.segmentText(convertedText, paint)
        var currentX = x
        var totalWidth = 0f
        
        for (segment in segments) {
            if (segment.useFallback) {
                // 使用回退字体绘制
                val fallbackPaint = Paint(paint)
                fallbackPaint.typeface = FontFallback.getFallbackTypeface(segment.text)
                canvas.drawText(segment.text, currentX, y, fallbackPaint)
                val width = fallbackPaint.measureText(segment.text)
                currentX += width
                totalWidth += width
            } else {
                // 使用当前字体绘制
                canvas.drawText(segment.text, currentX, y, paint)
                val width = paint.measureText(segment.text)
                currentX += width
                totalWidth += width
            }
        }
        
        return totalWidth
    }
    
    /**
     * 将文本中的 PUA 字符 (U+F000-U+F0FF) 转换为标准 Unicode
     * 这是一个安全层，确保即使解码层没有转换，渲染层也会处理
     */
    private fun convertPUAInText(text: String): String {
        var hasChanged = false
        val sb = StringBuilder(text.length)
        
        for (char in text) {
            if (char.code in 0xF000..0xF0FF) {
                val converted = StandardEncodings.convertPUAToUnicode(char)
                if (converted != null) {
                    sb.append(converted)
                    hasChanged = true
                } else {
                    sb.append(char)
                }
            } else {
                sb.append(char)
            }
        }
        
        return if (hasChanged) sb.toString() else text
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
        
        // 解析颜色空间
        val colorSpaceObj = stream.dict["ColorSpace"]
        val colorSpaceInfo = when (colorSpaceObj) {
            is PdfName -> parseColorSpace(colorSpaceObj.name)
            is PdfArray -> parseColorSpaceObject(colorSpaceObj)
            is PdfIndirectRef -> parseColorSpaceObject(document.getObject(colorSpaceObj))
            else -> ColorSpaceInfo.DeviceRGB
        } ?: ColorSpaceInfo.DeviceRGB
        
        // 解码图像数据
        val data = decodeStream(stream)
        
        // 创建 Bitmap
        var bitmap = try {
            when {
                // DCT (JPEG) - 直接解码
                stream.getFilters().any { it in listOf("DCTDecode", "DCT") } -> {
                    BitmapFactory.decodeByteArray(stream.rawData, 0, stream.rawData.size)
                }
                // JPX (JPEG 2000) - 直接解码
                stream.getFilters().any { it in listOf("JPXDecode") } -> {
                    BitmapFactory.decodeByteArray(stream.rawData, 0, stream.rawData.size)
                }
                // 根据颜色空间创建 Bitmap
                else -> createBitmapFromColorSpace(data, width, height, bpc, colorSpaceInfo)
            }
        } catch (e: Exception) {
            null
        }
        
        if (bitmap == null) return
        
        // 处理软遮罩 (SMask) - 实现虚化效果
        val smask = stream.dict["SMask"]
        if (smask != null) {
            bitmap = applyImageSoftMask(bitmap, smask)
        }
        
        // 处理硬遮罩 (Mask)
        val mask = stream.dict["Mask"]
        if (mask != null) {
            bitmap = applyImageMask(bitmap, mask)
        }
        
        // 应用图形状态的软遮罩
        if (currentState.softMask != null) {
            // TODO: 应用图形状态级别的软遮罩
        }
        
        val paint = Paint().apply {
            isAntiAlias = true
            alpha = (currentState.fillAlpha * 255).toInt()
        }
        
        val rect = RectF(0f, 0f, 1f, 1f)
        // 图像坐标系也是 Y 向上的，需要翻转
        canvas.save()
        canvas.scale(1f, -1f)
        canvas.translate(0f, -1f)
        canvas.drawBitmap(bitmap, null, rect, paint)
        canvas.restore()
    }
    
    /**
     * 根据颜色空间创建 Bitmap
     */
    private fun createBitmapFromColorSpace(
        data: ByteArray,
        width: Int,
        height: Int,
        bpc: Int,
        colorSpace: ColorSpaceInfo
    ): Bitmap? {
        return when (colorSpace) {
            is ColorSpaceInfo.DeviceGray -> createGrayBitmap(data, width, height, bpc)
            is ColorSpaceInfo.DeviceRGB -> createRGBBitmap(data, width, height)
            is ColorSpaceInfo.DeviceCMYK -> createCMYKBitmap(data, width, height)
            is ColorSpaceInfo.Indexed -> createIndexedBitmap(data, width, height, bpc, colorSpace)
            is ColorSpaceInfo.ICCBased -> {
                when (colorSpace.numComponents) {
                    1 -> createGrayBitmap(data, width, height, bpc)
                    3 -> createRGBBitmap(data, width, height)
                    4 -> createCMYKBitmap(data, width, height)
                    else -> null
                }
            }
            is ColorSpaceInfo.Separation, is ColorSpaceInfo.DeviceN -> {
                // 简化处理：当作灰度
                createGrayBitmap(data, width, height, bpc)
            }
            is ColorSpaceInfo.Pattern -> null
        }
    }
    
    /**
     * 创建索引颜色空间的 Bitmap
     */
    private fun createIndexedBitmap(
        data: ByteArray,
        width: Int,
        height: Int,
        bpc: Int,
        colorSpace: ColorSpaceInfo.Indexed
    ): Bitmap? {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        when (bpc) {
            8 -> {
                for (i in pixels.indices) {
                    if (i >= data.size) break
                    val index = data[i].toInt() and 0xFF
                    pixels[i] = lookupIndexedColor(colorSpace, index.coerceIn(0, colorSpace.hival))
                }
            }
            4 -> {
                for (i in pixels.indices) {
                    val byteIndex = i / 2
                    if (byteIndex >= data.size) break
                    val b = data[byteIndex].toInt() and 0xFF
                    val index = if (i % 2 == 0) (b shr 4) else (b and 0x0F)
                    pixels[i] = lookupIndexedColor(colorSpace, index.coerceIn(0, colorSpace.hival))
                }
            }
            2 -> {
                for (i in pixels.indices) {
                    val byteIndex = i / 4
                    if (byteIndex >= data.size) break
                    val b = data[byteIndex].toInt() and 0xFF
                    val shift = 6 - (i % 4) * 2
                    val index = (b shr shift) and 0x03
                    pixels[i] = lookupIndexedColor(colorSpace, index.coerceIn(0, colorSpace.hival))
                }
            }
            1 -> {
                for (i in pixels.indices) {
                    val byteIndex = i / 8
                    if (byteIndex >= data.size) break
                    val b = data[byteIndex].toInt() and 0xFF
                    val bit = 7 - (i % 8)
                    val index = (b shr bit) and 0x01
                    pixels[i] = lookupIndexedColor(colorSpace, index.coerceIn(0, colorSpace.hival))
                }
            }
            else -> return null
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    /**
     * 创建 CMYK Bitmap
     */
    private fun createCMYKBitmap(data: ByteArray, width: Int, height: Int): Bitmap? {
        if (data.size < width * height * 4) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        for (i in pixels.indices) {
            val offset = i * 4
            if (offset + 3 >= data.size) break
            val c = (data[offset].toInt() and 0xFF) / 255f
            val m = (data[offset + 1].toInt() and 0xFF) / 255f
            val y = (data[offset + 2].toInt() and 0xFF) / 255f
            val k = (data[offset + 3].toInt() and 0xFF) / 255f
            pixels[i] = cmykToRgb(c, m, y, k)
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    /**
     * 应用软遮罩 (SMask) - 实现虚化/透明效果
     */
    private fun applyImageSoftMask(bitmap: Bitmap, smaskObj: PdfObject): Bitmap {
        val smaskStream = when (smaskObj) {
            is PdfStream -> smaskObj
            is PdfIndirectRef -> document.getObject(smaskObj) as? PdfStream
            else -> null
        } ?: return bitmap
        
        val smaskWidth = smaskStream.dict.getInt("Width") ?: bitmap.width
        val smaskHeight = smaskStream.dict.getInt("Height") ?: bitmap.height
        val smaskBpc = smaskStream.dict.getInt("BitsPerComponent") ?: 8
        
        val smaskData = decodeStream(smaskStream)
        val smaskBitmap = createGrayBitmap(smaskData, smaskWidth, smaskHeight, smaskBpc)
            ?: return bitmap
        
        // 创建结果 Bitmap
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val resultCanvas = Canvas(result)
        
        // 绘制原始图像
        resultCanvas.drawBitmap(bitmap, 0f, 0f, null)
        
        // 使用软遮罩设置透明度
        val scaledMask = Bitmap.createScaledBitmap(smaskBitmap, bitmap.width, bitmap.height, true)
        
        val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        
        // 将灰度遮罩转换为 alpha 遮罩
        val alphaPixels = IntArray(scaledMask.width * scaledMask.height)
        scaledMask.getPixels(alphaPixels, 0, scaledMask.width, 0, 0, scaledMask.width, scaledMask.height)
        
        for (i in alphaPixels.indices) {
            val gray = Color.red(alphaPixels[i])  // 灰度图的 R=G=B
            alphaPixels[i] = Color.argb(gray, 255, 255, 255)
        }
        
        val alphaMask = Bitmap.createBitmap(scaledMask.width, scaledMask.height, Bitmap.Config.ARGB_8888)
        alphaMask.setPixels(alphaPixels, 0, scaledMask.width, 0, 0, scaledMask.width, scaledMask.height)
        
        resultCanvas.drawBitmap(alphaMask, 0f, 0f, maskPaint)
        
        return result
    }
    
    /**
     * 应用硬遮罩 (Mask)
     */
    private fun applyImageMask(bitmap: Bitmap, maskObj: PdfObject): Bitmap {
        return when (maskObj) {
            is PdfArray -> {
                // 颜色键遮罩：指定透明的颜色范围
                applyColorKeyMask(bitmap, maskObj)
            }
            is PdfStream, is PdfIndirectRef -> {
                // 显式遮罩
                val maskStream = when (maskObj) {
                    is PdfStream -> maskObj
                    is PdfIndirectRef -> document.getObject(maskObj) as? PdfStream
                    else -> null
                } ?: return bitmap
                
                applyExplicitMask(bitmap, maskStream)
            }
            else -> bitmap
        }
    }
    
    /**
     * 应用颜色键遮罩
     */
    private fun applyColorKeyMask(bitmap: Bitmap, colorKey: PdfArray): Bitmap {
        if (colorKey.isEmpty()) return bitmap
        
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        
        // 解析颜色键范围
        val ranges = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i + 1 < colorKey.size) {
            val min = (colorKey.getNumber(i)?.toInt() ?: 0)
            val max = (colorKey.getNumber(i + 1)?.toInt() ?: 255)
            ranges.add(min to max)
            i += 2
        }
        
        for (j in pixels.indices) {
            val color = pixels[j]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            
            val components = listOf(r, g, b)
            var transparent = true
            
            for (k in ranges.indices) {
                if (k < components.size) {
                    val (min, max) = ranges[k]
                    if (components[k] < min || components[k] > max) {
                        transparent = false
                        break
                    }
                }
            }
            
            if (transparent) {
                pixels[j] = Color.TRANSPARENT
            }
        }
        
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }
    
    /**
     * 应用显式遮罩
     */
    private fun applyExplicitMask(bitmap: Bitmap, maskStream: PdfStream): Bitmap {
        val maskWidth = maskStream.dict.getInt("Width") ?: bitmap.width
        val maskHeight = maskStream.dict.getInt("Height") ?: bitmap.height
        
        val maskData = decodeStream(maskStream)
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(result.width * result.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // 缩放遮罩到图像尺寸
        val scaleX = maskWidth.toFloat() / bitmap.width
        val scaleY = maskHeight.toFloat() / bitmap.height
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val maskX = (x * scaleX).toInt().coerceIn(0, maskWidth - 1)
                val maskY = (y * scaleY).toInt().coerceIn(0, maskHeight - 1)
                
                val byteIndex = maskY * ((maskWidth + 7) / 8) + maskX / 8
                val bitIndex = 7 - (maskX % 8)
                
                val maskBit = if (byteIndex < maskData.size) {
                    (maskData[byteIndex].toInt() shr bitIndex) and 0x01
                } else 0
                
                val pixelIndex = y * bitmap.width + x
                if (maskBit == 0) {
                    // 遮罩位为 0 表示透明
                    pixels[pixelIndex] = Color.TRANSPARENT
                }
            }
        }
        
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
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
    
    private fun createGrayBitmap(data: ByteArray, width: Int, height: Int, bpc: Int = 8): Bitmap? {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        when (bpc) {
            8 -> {
                if (data.size < width * height) return null
                for (i in pixels.indices) {
                    if (i >= data.size) break
                    val gray = data[i].toInt() and 0xFF
                    pixels[i] = Color.rgb(gray, gray, gray)
                }
            }
            4 -> {
                for (i in pixels.indices) {
                    val byteIndex = i / 2
                    if (byteIndex >= data.size) break
                    val b = data[byteIndex].toInt() and 0xFF
                    val value = if (i % 2 == 0) (b shr 4) else (b and 0x0F)
                    val gray = value * 255 / 15
                    pixels[i] = Color.rgb(gray, gray, gray)
                }
            }
            2 -> {
                for (i in pixels.indices) {
                    val byteIndex = i / 4
                    if (byteIndex >= data.size) break
                    val b = data[byteIndex].toInt() and 0xFF
                    val shift = 6 - (i % 4) * 2
                    val value = (b shr shift) and 0x03
                    val gray = value * 255 / 3
                    pixels[i] = Color.rgb(gray, gray, gray)
                }
            }
            1 -> {
                for (i in pixels.indices) {
                    val byteIndex = i / 8
                    if (byteIndex >= data.size) break
                    val b = data[byteIndex].toInt() and 0xFF
                    val bit = 7 - (i % 8)
                    val value = (b shr bit) and 0x01
                    val gray = value * 255
                    pixels[i] = Color.rgb(gray, gray, gray)
                }
            }
            16 -> {
                for (i in pixels.indices) {
                    val byteIndex = i * 2
                    if (byteIndex + 1 >= data.size) break
                    // 16位灰度，取高8位
                    val gray = data[byteIndex].toInt() and 0xFF
                    pixels[i] = Color.rgb(gray, gray, gray)
                }
            }
            else -> return null
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
    var renderMode: Int = 0,
    // ExtGState 扩展字段
    var fillAlpha: Float = 1f,
    var strokeAlpha: Float = 1f,
    var blendMode: PorterDuff.Mode = PorterDuff.Mode.SRC_OVER,
    var softMask: PdfDictionary? = null,
    // 颜色空间
    var fillColorSpace: ColorSpaceInfo = ColorSpaceInfo.DeviceRGB,
    var strokeColorSpace: ColorSpaceInfo = ColorSpaceInfo.DeviceRGB
)

/**
 * 颜色空间信息
 */
private sealed class ColorSpaceInfo {
    object DeviceGray : ColorSpaceInfo()
    object DeviceRGB : ColorSpaceInfo()
    object DeviceCMYK : ColorSpaceInfo()
    data class Indexed(val base: ColorSpaceInfo, val hival: Int, val lookup: ByteArray) : ColorSpaceInfo()
    data class ICCBased(val numComponents: Int, val alternateSpace: ColorSpaceInfo?) : ColorSpaceInfo()
    data class Separation(val name: String, val alternateSpace: ColorSpaceInfo, val tintTransform: PdfObject?) : ColorSpaceInfo()
    data class DeviceN(val names: List<String>, val alternateSpace: ColorSpaceInfo, val tintTransform: PdfObject?) : ColorSpaceInfo()
    data class Pattern(val underlyingSpace: ColorSpaceInfo?) : ColorSpaceInfo()
}

/**
 * PDF 字体映射器
 * 
 * 将 PDF 标准 14 字体（以及常见字体名称）映射到 Android 系统字体
 * 根据 PDF 32000-1:2008 标准 9.6.2.2 节
 * 
 * 支持:
 * - PDF 标准 14 字体
 * - CJK (中日韩) 字体
 * - 嵌入字体
 * - 字体回退机制
 */
object FontMapper {
    
    // 嵌入字体 Typeface 缓存
    private val embeddedTypefaceCache = mutableMapOf<String, Typeface?>()
    
    // CJK 字体类型
    enum class CJKFontType {
        SIMPLIFIED_CHINESE,  // 简体中文
        TRADITIONAL_CHINESE, // 繁体中文
        JAPANESE,            // 日文
        KOREAN,              // 韩文
        NONE                 // 非 CJK 字体
    }
    
    /**
     * 从嵌入字体数据创建 Typeface
     * 
     * 支持的字体格式：
     * - TrueType (.ttf)
     * - OpenType (.otf)
     * - CFF/Type1C (尝试作为 OTF 加载)
     */
    fun createFromEmbedded(font: SimpleFont): Typeface? {
        val baseFont = font.baseFont
        
        // 检查缓存
        if (embeddedTypefaceCache.containsKey(baseFont)) {
            return embeddedTypefaceCache[baseFont]
        }
        
        val embeddedFont = font.embeddedFont ?: return null
        
        val typeface = when (embeddedFont.type) {
            EmbeddedFontType.TRUETYPE, EmbeddedFontType.OPENTYPE -> {
                // TTF/OTF 格式可以直接加载
                createTypefaceFromBytes(embeddedFont.data, embeddedFont.getFileExtension())
            }
            EmbeddedFontType.TYPE1C, EmbeddedFontType.CID_TYPE0C -> {
                // CFF 格式：尝试作为 OTF 加载
                // 某些 CFF 数据实际上是完整的 OpenType-CFF，可以直接加载
                tryLoadCFF(embeddedFont.data, baseFont)
            }
            else -> null
        }
        
        embeddedTypefaceCache[baseFont] = typeface
        return typeface
    }
    
    /**
     * 从字节数组创建 Typeface
     */
    private fun createTypefaceFromBytes(data: ByteArray, extension: String): Typeface? {
        return try {
            val tempFile = java.io.File.createTempFile(
                "font_${data.hashCode()}", 
                ".$extension"
            )
            tempFile.deleteOnExit()
            tempFile.writeBytes(data)
            Typeface.createFromFile(tempFile)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 尝试加载 CFF 格式字体
     * 
     * CFF 数据有几种可能的情况：
     * 1. 独立的 CFF 数据 - 需要包装成 OpenType 才能使用
     * 2. OpenType-CFF 格式 - 可以直接作为 OTF 加载
     * 
     * Android 不直接支持裸 CFF，但如果 PDF 嵌入的是 OpenType-CFF 格式，
     * 我们可以尝试直接加载。
     */
    private fun tryLoadCFF(data: ByteArray, baseFont: String): Typeface? {
        // 检查是否为 OpenType 格式 (以 "OTTO" 或版本号开头)
        if (data.size >= 4) {
            val isOpenType = (data[0].toInt() and 0xFF == 0x4F && // 'O'
                             data[1].toInt() and 0xFF == 0x54 && // 'T'
                             data[2].toInt() and 0xFF == 0x54 && // 'T'
                             data[3].toInt() and 0xFF == 0x4F)   // 'O'
            val isTrueType = (data[0].toInt() and 0xFF == 0x00 &&
                             data[1].toInt() and 0xFF == 0x01 &&
                             data[2].toInt() and 0xFF == 0x00 &&
                             data[3].toInt() and 0xFF == 0x00)
            
            if (isOpenType || isTrueType) {
                // 这是一个完整的 OpenType/TrueType 字体，可以直接加载
                return createTypefaceFromBytes(data, "otf")
            }
        }
        
        // 对于裸 CFF 数据，Android 无法直接加载
        // 返回 null，让调用者使用备用字体
        return null
    }
    
    /**
     * 从 Type0Font 的 CIDFont 获取嵌入字体
     */
    fun createFromType0Font(font: Type0Font): Typeface? {
        val cidFont = font.cidFont ?: return null
        if (!cidFont.hasEmbeddedFont()) return null
        
        val embeddedFont = cidFont.embeddedFont ?: return null
        val baseFont = font.baseFont
        
        // 检查缓存
        if (embeddedTypefaceCache.containsKey(baseFont)) {
            return embeddedTypefaceCache[baseFont]
        }
        
        val typeface = when (embeddedFont.type) {
            EmbeddedFontType.TRUETYPE, EmbeddedFontType.OPENTYPE -> {
                createTypefaceFromBytes(embeddedFont.data, embeddedFont.getFileExtension())
            }
            EmbeddedFontType.TYPE1C, EmbeddedFontType.CID_TYPE0C -> {
                tryLoadCFF(embeddedFont.data, baseFont)
            }
            else -> null
        }
        
        embeddedTypefaceCache[baseFont] = typeface
        return typeface
    }
    
    /**
     * 检测 CJK 字体类型
     */
    fun detectCJKType(baseFont: String): CJKFontType {
        val name = baseFont.lowercase()
        
        // 简体中文字体
        if (name.contains("simsun") ||      // 宋体 (Windows)
            name.contains("simhei") ||       // 黑体 (Windows)
            name.contains("simkai") ||       // 楷体 (Windows)
            name.contains("simfang") ||      // 仿宋 (Windows)
            name.contains("fangsong") ||     // 仿宋
            name.contains("stsong") ||       // 华文宋体
            name.contains("stxihei") ||      // 华文细黑
            name.contains("stkaiti") ||      // 华文楷体
            name.contains("stfangsong") ||   // 华文仿宋
            name.contains("stzhongsong") ||  // 华文中宋
            name.contains("stheiti") ||      // 华文黑体
            name.contains("nsimsun") ||      // 新宋体
            name.contains("yahei") ||        // 微软雅黑
            name.contains("dengxian") ||     // 等线
            name.contains("fzshusong") ||    // 方正书宋
            name.contains("fzfangsong") ||   // 方正仿宋
            name.contains("fzheiti") ||      // 方正黑体
            name.contains("fzkaiti") ||      // 方正楷体
            name.contains("adobe-gb") ||     // Adobe GB 字体集
            name.contains("adobesongstd") || // Adobe 宋体
            name.contains("adobeheitistd") ||// Adobe 黑体
            name.contains("sourcehansans-sc") ||  // 思源黑体 简体
            name.contains("sourcehanserif-sc") || // 思源宋体 简体
            name.contains("notosanssc") ||   // Noto Sans 简体中文
            name.contains("notoserifsc") ||  // Noto Serif 简体中文
            name.contains("gb1") ||          // GB1 字符集
            name.contains("gbk") ||          // GBK 编码
            name.contains("gb2312") ||       // GB2312 编码
            name.contains("hanyisong") ||    // 汉仪宋体
            name.contains("hanyihei")) {     // 汉仪黑体
            return CJKFontType.SIMPLIFIED_CHINESE
        }
        
        // 繁体中文字体
        if (name.contains("mingliu") ||      // 细明体 (Windows)
            name.contains("pmingliu") ||     // 新细明体
            name.contains("dfkai") ||        // 标楷体
            name.contains("kaiu") ||         // 标楷体
            name.contains("msong") ||        // 明體
            name.contains("mhei") ||         // 黑體
            name.contains("lisong") ||       // 隸書
            name.contains("lihei") ||        // 儷黑
            name.contains("apple lisong") || // Apple 隸書
            name.contains("heiti tc") ||     // 黑體-繁
            name.contains("songti tc") ||    // 宋體-繁
            name.contains("adobe-cns") ||    // Adobe CNS 字体集
            name.contains("cns1") ||         // CNS1 字符集
            name.contains("big5") ||         // Big5 编码
            name.contains("sourcehansans-tc") ||  // 思源黑体 繁体
            name.contains("sourcehanserif-tc") || // 思源宋体 繁体
            name.contains("notosanstc") ||   // Noto Sans 繁体中文
            name.contains("notoseriftc")) {  // Noto Serif 繁体中文
            return CJKFontType.TRADITIONAL_CHINESE
        }
        
        // 日文字体
        if (name.contains("mincho") ||       // 明朝体
            name.contains("gothic") && !name.contains("century") || // ゴシック体
            name.contains("meiryo") ||       // メイリオ
            name.contains("msgothic") ||     // MS ゴシック
            name.contains("msmincho") ||     // MS 明朝
            name.contains("yugo") ||         // 游ゴシック
            name.contains("yumincho") ||     // 游明朝
            name.contains("hiragino") ||     // ヒラギノ
            name.contains("kozuka") ||       // 小塚
            name.contains("kozgo") ||        // 小塚ゴシック
            name.contains("kozmin") ||       // 小塚明朝
            name.contains("ryumin") ||       // リュウミン
            name.contains("adobe-japan") ||  // Adobe Japan 字体集
            name.contains("japan1") ||       // Japan1 字符集
            name.contains("ipagothic") ||    // IPA ゴシック
            name.contains("ipamincho") ||    // IPA 明朝
            name.contains("sourcehansans-jp") ||  // 思源黑体 日文
            name.contains("sourcehanserif-jp") || // 思源宋体 日文
            name.contains("notosansjp") ||   // Noto Sans 日文
            name.contains("notoserifjp")) {  // Noto Serif 日文
            return CJKFontType.JAPANESE
        }
        
        // 韩文字体
        if (name.contains("batang") ||       // 바탕
            name.contains("dotum") ||        // 돋움
            name.contains("gulim") ||        // 굴림
            name.contains("gungsuh") ||      // 궁서
            name.contains("malgun") ||       // 맑은 고딕
            name.contains("nanum") ||        // 나눔
            name.contains("adobe-korea") ||  // Adobe Korea 字体集
            name.contains("korea1") ||       // Korea1 字符集
            name.contains("ksc") ||          // KSC 编码
            name.contains("sourcehansans-kr") ||  // 思源黑体 韩文
            name.contains("sourcehanserif-kr") || // 思源宋体 韩文
            name.contains("notosanskr") ||   // Noto Sans 韩文
            name.contains("notoserifkr")) {  // Noto Serif 韩文
            return CJKFontType.KOREAN
        }
        
        // 通用 CJK 字体 (多语言支持)
        if (name.contains("notosanscjk") ||  // Noto Sans CJK
            name.contains("notoserifcjk") || // Noto Serif CJK
            name.contains("sourcehansans") ||// 思源黑体 (通用)
            name.contains("sourcehanserif") ||// 思源宋体 (通用)
            name.contains("cjk")) {
            // 默认返回简体中文
            return CJKFontType.SIMPLIFIED_CHINESE
        }
        
        return CJKFontType.NONE
    }
    
    /**
     * 判断是否为 CJK 字体
     */
    fun isCJKFont(baseFont: String): Boolean {
        return detectCJKType(baseFont) != CJKFontType.NONE
    }
    
    /**
     * 获取 CJK 字体的推荐样式 (衬线/无衬线)
     * 
     * 优先使用应用内打包的中文字体（思源宋体/思源黑体），
     * 以确保正确的字形显示，特别是宋体风格的字体。
     */
    fun getCJKFontStyle(baseFont: String): Typeface {
        val name = baseFont.lowercase()
        
        // 衬线体 (宋体/明朝体/楷体/仿宋)
        val isSerif = name.contains("song") ||
                      name.contains("ming") ||
                      name.contains("mincho") ||
                      name.contains("batang") ||
                      name.contains("serif") ||
                      name.contains("kai") ||
                      name.contains("fang") ||
                      name.contains("lisong") ||
                      name.contains("gungsuh") ||
                      name.contains("simsun") ||
                      name.contains("nsimsun") ||
                      name.contains("stsong") ||
                      name.contains("stzhongsong") ||
                      name.contains("fzshu") ||
                      name.contains("fzfang") ||
                      name.contains("adobe.*song") ||
                      name.contains("mingliu") ||
                      name.contains("pmingliu")
        
        // 无衬线体 (黑体/哥特体)
        val isSansSerif = name.contains("hei") ||
                         name.contains("gothic") ||
                         name.contains("dotum") ||
                         name.contains("gulim") ||
                         name.contains("malgun") ||
                         name.contains("meiryo") ||
                         name.contains("yahei") ||
                         name.contains("dengxian") ||
                         name.contains("sans") ||
                         name.contains("simhei") ||
                         name.contains("stxihei") ||
                         name.contains("stheiti") ||
                         name.contains("fzhei")
        
        // 检测粗体
        val isBold = name.contains("bold") ||
                     name.contains("heavy") ||
                     name.contains("black") ||
                     name.contains("-b") ||
                     name.endsWith("bd")
        
        // 使用 FontAssetManager 获取中文字体
        val baseTypeface = when {
            isSerif -> FontAssetManager.getSerifCJK()
            isSansSerif -> FontAssetManager.getSansCJK()
            else -> FontAssetManager.getSansCJK() // CJK 默认使用无衬线字体
        }
        
        return if (isBold) {
            Typeface.create(baseTypeface, Typeface.BOLD)
        } else {
            baseTypeface
        }
    }
    
    /**
     * 将 PDF 字体的 BaseFont 名称映射到 Android Typeface
     * 
     * 支持的 PDF 标准 14 字体:
     * - Times-Roman, Times-Bold, Times-Italic, Times-BoldItalic
     * - Helvetica, Helvetica-Bold, Helvetica-Oblique, Helvetica-BoldOblique
     * - Courier, Courier-Bold, Courier-Oblique, Courier-BoldOblique
     * - Symbol, ZapfDingbats
     */
    fun mapToTypeface(baseFont: String): Typeface {
        val name = baseFont.lowercase()
        
        // 首先检查是否为 CJK 字体
        if (isCJKFont(baseFont)) {
            return getCJKFontStyle(baseFont)
        }
        
        // 确定字体家族
        val family = when {
            // 衬线字体 (Serif)
            name.contains("times") -> Typeface.SERIF
            name.contains("georgia") -> Typeface.SERIF
            name.contains("palatino") -> Typeface.SERIF
            name.contains("garamond") -> Typeface.SERIF
            name.contains("bookman") -> Typeface.SERIF
            name.contains("cambria") -> Typeface.SERIF
            name.contains("century") -> Typeface.SERIF
            name.contains("bodoni") -> Typeface.SERIF
            name.contains("baskerville") -> Typeface.SERIF
            
            // 无衬线字体 (Sans-serif)
            name.contains("helvetica") -> Typeface.SANS_SERIF
            name.contains("arial") -> Typeface.SANS_SERIF
            name.contains("verdana") -> Typeface.SANS_SERIF
            name.contains("tahoma") -> Typeface.SANS_SERIF
            name.contains("calibri") -> Typeface.SANS_SERIF
            name.contains("segoe") -> Typeface.SANS_SERIF
            name.contains("roboto") -> Typeface.SANS_SERIF
            name.contains("opensans") -> Typeface.SANS_SERIF
            name.contains("noto sans") -> Typeface.SANS_SERIF
            name.contains("sans") -> Typeface.SANS_SERIF
            name.contains("futura") -> Typeface.SANS_SERIF
            name.contains("avenir") -> Typeface.SANS_SERIF
            name.contains("franklin") -> Typeface.SANS_SERIF
            name.contains("gill") -> Typeface.SANS_SERIF
            
            // 等宽字体 (Monospace)
            name.contains("courier") -> Typeface.MONOSPACE
            name.contains("consolas") -> Typeface.MONOSPACE
            name.contains("monaco") -> Typeface.MONOSPACE
            name.contains("mono") -> Typeface.MONOSPACE
            name.contains("menlo") -> Typeface.MONOSPACE
            name.contains("source code") -> Typeface.MONOSPACE
            name.contains("fira code") -> Typeface.MONOSPACE
            name.contains("jetbrains") -> Typeface.MONOSPACE
            
            // Symbol 和 ZapfDingbats - 使用默认字体（编码已在 StandardEncodings 中处理）
            name.contains("symbol") -> Typeface.SERIF
            name.contains("dingbats") -> Typeface.SERIF
            name.contains("wingdings") -> Typeface.SERIF
            name.contains("webdings") -> Typeface.SERIF
            
            // 默认使用系统默认字体
            else -> Typeface.DEFAULT
        }
        
        // 确定字体样式 (Bold, Italic)
        val style = when {
            // 粗斜体
            (name.contains("bolditalic") || name.contains("boldoblique") ||
             name.contains("bold") && (name.contains("italic") || name.contains("oblique")) ||
             name.contains("bi") && name.length > 2 && name.endsWith("bi") ||
             name.contains("-bi") || name.contains("_bi")) -> Typeface.BOLD_ITALIC
            
            // 粗体
            name.contains("bold") || name.contains("-bd") || 
            name.endsWith("bd") || name.contains("_bd") ||
            name.contains("black") || name.contains("heavy") || 
            name.contains("demi") || name.contains("semibold") ||
            name.contains("extrabold") || name.contains("ultrabold") ||
            name.contains("-b") && !name.contains("-bi") -> Typeface.BOLD
            
            // 斜体
            name.contains("italic") || name.contains("oblique") || 
            name.contains("-it") || name.endsWith("it") ||
            name.contains("_it") || name.contains("-i") && !name.contains("-it") -> Typeface.ITALIC
            
            // 正常
            else -> Typeface.NORMAL
        }
        
        return Typeface.create(family, style)
    }
    
    /**
     * 获取用于 CJK 字符的回退字体
     * 优先使用应用内打包的中文字体
     */
    fun getCJKFallbackTypeface(): Typeface {
        return FontAssetManager.getSansCJK()
    }
    
    /**
     * 检测文本是否包含 CJK 字符
     */
    fun containsCJKCharacters(text: String): Boolean {
        for (c in text) {
            val codePoint = c.code
            // CJK 统一汉字
            if (codePoint in 0x4E00..0x9FFF) return true
            // CJK 扩展 A
            if (codePoint in 0x3400..0x4DBF) return true
            // CJK 兼容汉字
            if (codePoint in 0xF900..0xFAFF) return true
            // 日文假名
            if (codePoint in 0x3040..0x30FF) return true
            // 韩文音节
            if (codePoint in 0xAC00..0xD7AF) return true
            // 韩文字母
            if (codePoint in 0x1100..0x11FF) return true
            // CJK 符号和标点
            if (codePoint in 0x3000..0x303F) return true
            // 全角 ASCII
            if (codePoint in 0xFF00..0xFFEF) return true
        }
        return false
    }
}

/**
 * 字体回退机制
 * 
 * 当 PDF 字体无法渲染某些字符时，使用系统字体进行回退
 * 特别针对 CJK 字符和特殊符号（如表情符号）
 */
object FontFallback {
    
    /**
     * 文本段
     */
    data class TextSegment(
        val text: String,
        val useFallback: Boolean
    )
    
    /**
     * 检查文本是否需要字体回退
     */
    fun needsFallback(text: String, paint: Paint): Boolean {
        if (text.isEmpty()) return false
        
        // 检查每个字符是否可以被当前字体渲染
        for (i in text.indices) {
            val codePoint = text.codePointAt(i)
            
            // 跳过 surrogate 对的低位部分
            if (Character.isLowSurrogate(text[i])) continue
            
            if (!canRenderCodePoint(paint, codePoint)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 检查字体是否可以渲染指定的 code point
     */
    private fun canRenderCodePoint(paint: Paint, codePoint: Int): Boolean {
        // 使用 Paint.hasGlyph() 检查 (API 23+)
        return try {
            val str = String(Character.toChars(codePoint))
            paint.hasGlyph(str)
        } catch (e: Exception) {
            // 如果 hasGlyph 不可用，假设可以渲染
            // 对于特殊字符范围，假设需要回退
            !isSpecialCharacter(codePoint)
        }
    }
    
    /**
     * 检查是否为特殊字符（可能需要回退的字符）
     */
    private fun isSpecialCharacter(codePoint: Int): Boolean {
        // Unicode 私有使用区 (PUA) - Symbol 字体常用
        // 这些字符需要特殊处理或转换
        if (codePoint in 0xF000..0xF0FF) return true
        // CJK 统一汉字
        if (codePoint in 0x4E00..0x9FFF) return true
        // CJK 扩展 A
        if (codePoint in 0x3400..0x4DBF) return true
        // CJK 扩展 B-F
        if (codePoint in 0x20000..0x2EBEF) return true
        // CJK 兼容汉字
        if (codePoint in 0xF900..0xFAFF) return true
        // 日文假名
        if (codePoint in 0x3040..0x30FF) return true
        // 韩文音节
        if (codePoint in 0xAC00..0xD7AF) return true
        // 韩文字母
        if (codePoint in 0x1100..0x11FF) return true
        // CJK 符号和标点
        if (codePoint in 0x3000..0x303F) return true
        // 全角 ASCII
        if (codePoint in 0xFF00..0xFFEF) return true
        // 表情符号
        if (codePoint in 0x1F300..0x1F9FF) return true
        // 其他表情符号
        if (codePoint in 0x2600..0x26FF) return true
        if (codePoint in 0x2700..0x27BF) return true
        // 希腊字母
        if (codePoint in 0x0370..0x03FF) return true
        // 希腊扩展
        if (codePoint in 0x1F00..0x1FFF) return true
        // 数学运算符
        if (codePoint in 0x2200..0x22FF) return true
        // 补充数学运算符
        if (codePoint in 0x2A00..0x2AFF) return true
        // 杂项数学符号 A
        if (codePoint in 0x27C0..0x27EF) return true
        // 杂项数学符号 B
        if (codePoint in 0x2980..0x29FF) return true
        // 数学字母数字符号
        if (codePoint in 0x1D400..0x1D7FF) return true
        // 字母式符号
        if (codePoint in 0x2100..0x214F) return true
        // 箭头
        if (codePoint in 0x2190..0x21FF) return true
        // 补充箭头 A/B/C
        if (codePoint in 0x27F0..0x27FF) return true
        if (codePoint in 0x2900..0x297F) return true
        // 杂项技术符号
        if (codePoint in 0x2300..0x23FF) return true
        
        return false
    }
    
    /**
     * 将文本分割为需要回退和不需要回退的段
     */
    fun segmentText(text: String, paint: Paint): List<TextSegment> {
        if (text.isEmpty()) return emptyList()
        
        val segments = mutableListOf<TextSegment>()
        val currentSegment = StringBuilder()
        var currentNeedsFallback = false
        var isFirst = true
        
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            val charStr = text.substring(i, i + charCount)
            
            val needsFallback = !canRenderCodePoint(paint, codePoint)
            
            if (isFirst) {
                currentNeedsFallback = needsFallback
                isFirst = false
            }
            
            if (needsFallback == currentNeedsFallback) {
                // 相同类型，添加到当前段
                currentSegment.append(charStr)
            } else {
                // 类型变化，保存当前段并开始新段
                if (currentSegment.isNotEmpty()) {
                    segments.add(TextSegment(currentSegment.toString(), currentNeedsFallback))
                    currentSegment.clear()
                }
                currentSegment.append(charStr)
                currentNeedsFallback = needsFallback
            }
            
            i += charCount
        }
        
        // 添加最后一段
        if (currentSegment.isNotEmpty()) {
            segments.add(TextSegment(currentSegment.toString(), currentNeedsFallback))
        }
        
        return segments
    }
    
    /**
     * 获取回退字体
     * 根据文本内容选择合适的字体
     * 
     * 优先使用应用内打包的字体，以确保正确显示：
     * - CJK 字符使用思源黑体/宋体
     * - 数学符号使用 Noto Sans Math
     */
    fun getFallbackTypeface(text: String): Typeface {
        // 检测文本类型
        val charType = detectCharacterType(text)
        
        return when (charType) {
            CharacterType.CJK_SIMPLIFIED_CHINESE,
            CharacterType.CJK_TRADITIONAL_CHINESE,
            CharacterType.CJK_JAPANESE,
            CharacterType.CJK_KOREAN,
            CharacterType.CJK_GENERIC -> {
                // 使用应用内打包的中文字体
                FontAssetManager.getSansCJK()
            }
            CharacterType.EMOJI -> {
                // 表情符号使用系统字体
                Typeface.DEFAULT
            }
            CharacterType.SYMBOL, CharacterType.MATH -> {
                // 数学符号和其他符号使用数学字体
                FontAssetManager.getMathFont()
            }
            CharacterType.GREEK -> {
                // 希腊字母使用数学字体（包含完整的希腊字母）
                FontAssetManager.getMathFont()
            }
            else -> Typeface.DEFAULT
        }
    }
    
    /**
     * 字符类型
     */
    private enum class CharacterType {
        ASCII,
        CJK_SIMPLIFIED_CHINESE,
        CJK_TRADITIONAL_CHINESE,
        CJK_JAPANESE,
        CJK_KOREAN,
        CJK_GENERIC,
        EMOJI,
        SYMBOL,
        MATH,       // 数学符号
        GREEK,      // 希腊字母
        OTHER
    }
    
    /**
     * 检测文本的主要字符类型
     */
    private fun detectCharacterType(text: String): CharacterType {
        if (text.isEmpty()) return CharacterType.OTHER
        
        var cjkCount = 0
        var japaneseKanaCount = 0
        var koreanCount = 0
        var emojiCount = 0
        var symbolCount = 0
        var mathCount = 0
        var greekCount = 0
        var total = 0
        
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            
            when {
                // CJK 汉字
                codePoint in 0x4E00..0x9FFF ||
                codePoint in 0x3400..0x4DBF ||
                codePoint in 0x20000..0x2EBEF ||
                codePoint in 0xF900..0xFAFF -> {
                    cjkCount++
                }
                // 日文假名
                codePoint in 0x3040..0x30FF -> {
                    japaneseKanaCount++
                }
                // 韩文
                codePoint in 0xAC00..0xD7AF ||
                codePoint in 0x1100..0x11FF -> {
                    koreanCount++
                }
                // 希腊字母
                codePoint in 0x0370..0x03FF ||
                codePoint in 0x1F00..0x1FFF -> {
                    greekCount++
                }
                // 数学运算符和符号
                codePoint in 0x2200..0x22FF || // Mathematical Operators
                codePoint in 0x2A00..0x2AFF || // Supplemental Mathematical Operators
                codePoint in 0x27C0..0x27EF || // Miscellaneous Mathematical Symbols-A
                codePoint in 0x2980..0x29FF || // Miscellaneous Mathematical Symbols-B
                codePoint in 0x1D400..0x1D7FF || // Mathematical Alphanumeric Symbols
                codePoint in 0x2100..0x214F -> { // Letterlike Symbols
                    mathCount++
                }
                // 表情符号
                codePoint in 0x1F300..0x1F9FF ||
                codePoint in 0x2600..0x26FF ||
                codePoint in 0x2700..0x27BF -> {
                    emojiCount++
                }
                // 其他技术符号
                codePoint in 0x2000..0x206F || // General Punctuation
                codePoint in 0x2300..0x23FF || // Miscellaneous Technical
                codePoint in 0x2500..0x257F || // Box Drawing
                codePoint in 0x2580..0x259F || // Block Elements
                codePoint in 0x25A0..0x25FF -> { // Geometric Shapes
                    symbolCount++
                }
            }
            
            total++
            i += charCount
        }
        
        return when {
            emojiCount > 0 -> CharacterType.EMOJI
            japaneseKanaCount > 0 -> CharacterType.CJK_JAPANESE
            koreanCount > 0 -> CharacterType.CJK_KOREAN
            cjkCount > 0 -> CharacterType.CJK_GENERIC
            mathCount > 0 -> CharacterType.MATH
            greekCount > 0 -> CharacterType.GREEK
            symbolCount > 0 -> CharacterType.SYMBOL
            else -> CharacterType.OTHER
        }
    }
    
    /**
     * 计算文本的宽度（支持混合字体）
     */
    fun measureTextWithFallback(text: String, paint: Paint): Float {
        if (text.isEmpty()) return 0f
        
        val segments = segmentText(text, paint)
        var totalWidth = 0f
        
        for (segment in segments) {
            totalWidth += if (segment.useFallback) {
                val fallbackPaint = Paint(paint)
                fallbackPaint.typeface = getFallbackTypeface(segment.text)
                fallbackPaint.measureText(segment.text)
            } else {
                paint.measureText(segment.text)
            }
        }
        
        return totalWidth
    }
}
