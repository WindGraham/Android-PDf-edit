package com.pdfeditor.graphics

import com.pdfcore.content.ContentInstruction
import com.pdfcore.content.ContentParser
import com.pdfcore.model.*

/**
 * PDF 图形编辑器
 * 
 * 支持编辑路径、形状、颜色等图形元素
 */
class GraphicsEditor(private val document: PdfDocument) {
    
    private val contentParser = ContentParser()
    
    /**
     * 获取页面上的所有图形元素
     */
    fun getGraphicsElements(page: PdfDictionary): List<GraphicsElement> {
        val contents = document.getPageContents(page)
        if (contents.isEmpty()) return emptyList()
        
        val elements = mutableListOf<GraphicsElement>()
        
        for (stream in contents) {
            val instructions = contentParser.parse(stream)
            extractGraphics(instructions, elements)
        }
        
        return elements
    }
    
    /**
     * 从指令中提取图形元素
     */
    private fun extractGraphics(
        instructions: List<ContentInstruction>,
        elements: MutableList<GraphicsElement>
    ) {
        var currentPath = mutableListOf<PathSegment>()
        var currentX = 0f
        var currentY = 0f
        var pathStartX = 0f
        var pathStartY = 0f
        var strokeColor = Triple(0f, 0f, 0f)
        var fillColor = Triple(0f, 0f, 0f)
        var lineWidth = 1f
        
        for (instruction in instructions) {
            when (instruction.operator) {
                // 路径构建
                "m" -> {
                    if (instruction.operands.size >= 2) {
                        currentX = instruction.operands[0].asFloat()
                        currentY = instruction.operands[1].asFloat()
                        pathStartX = currentX
                        pathStartY = currentY
                        currentPath.add(PathSegment.MoveTo(currentX, currentY))
                    }
                }
                "l" -> {
                    if (instruction.operands.size >= 2) {
                        currentX = instruction.operands[0].asFloat()
                        currentY = instruction.operands[1].asFloat()
                        currentPath.add(PathSegment.LineTo(currentX, currentY))
                    }
                }
                "c" -> {
                    if (instruction.operands.size >= 6) {
                        val x1 = instruction.operands[0].asFloat()
                        val y1 = instruction.operands[1].asFloat()
                        val x2 = instruction.operands[2].asFloat()
                        val y2 = instruction.operands[3].asFloat()
                        val x3 = instruction.operands[4].asFloat()
                        val y3 = instruction.operands[5].asFloat()
                        currentX = x3
                        currentY = y3
                        currentPath.add(PathSegment.CurveTo(x1, y1, x2, y2, x3, y3))
                    }
                }
                "v" -> {
                    if (instruction.operands.size >= 4) {
                        val x2 = instruction.operands[0].asFloat()
                        val y2 = instruction.operands[1].asFloat()
                        val x3 = instruction.operands[2].asFloat()
                        val y3 = instruction.operands[3].asFloat()
                        currentPath.add(PathSegment.CurveTo(currentX, currentY, x2, y2, x3, y3))
                        currentX = x3
                        currentY = y3
                    }
                }
                "y" -> {
                    if (instruction.operands.size >= 4) {
                        val x1 = instruction.operands[0].asFloat()
                        val y1 = instruction.operands[1].asFloat()
                        val x3 = instruction.operands[2].asFloat()
                        val y3 = instruction.operands[3].asFloat()
                        currentPath.add(PathSegment.CurveTo(x1, y1, x3, y3, x3, y3))
                        currentX = x3
                        currentY = y3
                    }
                }
                "h" -> {
                    currentPath.add(PathSegment.Close)
                    currentX = pathStartX
                    currentY = pathStartY
                }
                "re" -> {
                    if (instruction.operands.size >= 4) {
                        val x = instruction.operands[0].asFloat()
                        val y = instruction.operands[1].asFloat()
                        val w = instruction.operands[2].asFloat()
                        val h = instruction.operands[3].asFloat()
                        currentPath.add(PathSegment.Rectangle(x, y, w, h))
                    }
                }
                
                // 颜色
                "g" -> {
                    if (instruction.operands.isNotEmpty()) {
                        val gray = instruction.operands[0].asFloat()
                        fillColor = Triple(gray, gray, gray)
                    }
                }
                "G" -> {
                    if (instruction.operands.isNotEmpty()) {
                        val gray = instruction.operands[0].asFloat()
                        strokeColor = Triple(gray, gray, gray)
                    }
                }
                "rg" -> {
                    if (instruction.operands.size >= 3) {
                        fillColor = Triple(
                            instruction.operands[0].asFloat(),
                            instruction.operands[1].asFloat(),
                            instruction.operands[2].asFloat()
                        )
                    }
                }
                "RG" -> {
                    if (instruction.operands.size >= 3) {
                        strokeColor = Triple(
                            instruction.operands[0].asFloat(),
                            instruction.operands[1].asFloat(),
                            instruction.operands[2].asFloat()
                        )
                    }
                }
                
                // 线宽
                "w" -> {
                    if (instruction.operands.isNotEmpty()) {
                        lineWidth = instruction.operands[0].asFloat()
                    }
                }
                
                // 路径绘制
                "S" -> {
                    if (currentPath.isNotEmpty()) {
                        elements.add(GraphicsElement.Path(
                            segments = currentPath.toList(),
                            strokeColor = strokeColor,
                            fillColor = null,
                            lineWidth = lineWidth
                        ))
                        currentPath.clear()
                    }
                }
                "s" -> {
                    currentPath.add(PathSegment.Close)
                    if (currentPath.isNotEmpty()) {
                        elements.add(GraphicsElement.Path(
                            segments = currentPath.toList(),
                            strokeColor = strokeColor,
                            fillColor = null,
                            lineWidth = lineWidth
                        ))
                        currentPath.clear()
                    }
                }
                "f", "F" -> {
                    if (currentPath.isNotEmpty()) {
                        elements.add(GraphicsElement.Path(
                            segments = currentPath.toList(),
                            strokeColor = null,
                            fillColor = fillColor,
                            lineWidth = lineWidth
                        ))
                        currentPath.clear()
                    }
                }
                "B" -> {
                    if (currentPath.isNotEmpty()) {
                        elements.add(GraphicsElement.Path(
                            segments = currentPath.toList(),
                            strokeColor = strokeColor,
                            fillColor = fillColor,
                            lineWidth = lineWidth
                        ))
                        currentPath.clear()
                    }
                }
                "n" -> {
                    currentPath.clear()
                }
            }
        }
    }
    
    /**
     * 添加矩形
     */
    fun addRectangle(
        page: PdfDictionary,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        strokeColor: Triple<Float, Float, Float>? = null,
        fillColor: Triple<Float, Float, Float>? = null,
        lineWidth: Float = 1f
    ): Boolean {
        val instructions = mutableListOf<ContentInstruction>()
        
        // 保存状态
        instructions.add(ContentInstruction("q", emptyList()))
        
        // 设置线宽
        instructions.add(ContentInstruction("w", listOf(PdfNumber(lineWidth.toDouble()))))
        
        // 设置颜色
        if (strokeColor != null) {
            instructions.add(ContentInstruction("RG", listOf(
                PdfNumber(strokeColor.first.toDouble()),
                PdfNumber(strokeColor.second.toDouble()),
                PdfNumber(strokeColor.third.toDouble())
            )))
        }
        if (fillColor != null) {
            instructions.add(ContentInstruction("rg", listOf(
                PdfNumber(fillColor.first.toDouble()),
                PdfNumber(fillColor.second.toDouble()),
                PdfNumber(fillColor.third.toDouble())
            )))
        }
        
        // 添加矩形
        instructions.add(ContentInstruction("re", listOf(
            PdfNumber(x.toDouble()),
            PdfNumber(y.toDouble()),
            PdfNumber(width.toDouble()),
            PdfNumber(height.toDouble())
        )))
        
        // 绘制
        val paintOp = when {
            strokeColor != null && fillColor != null -> "B"
            fillColor != null -> "f"
            else -> "S"
        }
        instructions.add(ContentInstruction(paintOp, emptyList()))
        
        // 恢复状态
        instructions.add(ContentInstruction("Q", emptyList()))
        
        return appendToPage(page, instructions)
    }
    
    /**
     * 添加直线
     */
    fun addLine(
        page: PdfDictionary,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
        lineWidth: Float = 1f
    ): Boolean {
        val instructions = mutableListOf<ContentInstruction>()
        
        instructions.add(ContentInstruction("q", emptyList()))
        instructions.add(ContentInstruction("w", listOf(PdfNumber(lineWidth.toDouble()))))
        instructions.add(ContentInstruction("RG", listOf(
            PdfNumber(color.first.toDouble()),
            PdfNumber(color.second.toDouble()),
            PdfNumber(color.third.toDouble())
        )))
        
        instructions.add(ContentInstruction("m", listOf(
            PdfNumber(x1.toDouble()),
            PdfNumber(y1.toDouble())
        )))
        instructions.add(ContentInstruction("l", listOf(
            PdfNumber(x2.toDouble()),
            PdfNumber(y2.toDouble())
        )))
        instructions.add(ContentInstruction("S", emptyList()))
        
        instructions.add(ContentInstruction("Q", emptyList()))
        
        return appendToPage(page, instructions)
    }
    
    /**
     * 添加圆形/椭圆
     */
    fun addEllipse(
        page: PdfDictionary,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        strokeColor: Triple<Float, Float, Float>? = null,
        fillColor: Triple<Float, Float, Float>? = null,
        lineWidth: Float = 1f
    ): Boolean {
        val instructions = mutableListOf<ContentInstruction>()
        
        instructions.add(ContentInstruction("q", emptyList()))
        instructions.add(ContentInstruction("w", listOf(PdfNumber(lineWidth.toDouble()))))
        
        if (strokeColor != null) {
            instructions.add(ContentInstruction("RG", listOf(
                PdfNumber(strokeColor.first.toDouble()),
                PdfNumber(strokeColor.second.toDouble()),
                PdfNumber(strokeColor.third.toDouble())
            )))
        }
        if (fillColor != null) {
            instructions.add(ContentInstruction("rg", listOf(
                PdfNumber(fillColor.first.toDouble()),
                PdfNumber(fillColor.second.toDouble()),
                PdfNumber(fillColor.third.toDouble())
            )))
        }
        
        // 使用贝塞尔曲线近似椭圆
        val k = 0.5522847498f // 4 * (sqrt(2) - 1) / 3
        val kx = k * rx
        val ky = k * ry
        
        // 移动到右边中点
        instructions.add(ContentInstruction("m", listOf(
            PdfNumber((cx + rx).toDouble()),
            PdfNumber(cy.toDouble())
        )))
        
        // 四段贝塞尔曲线
        instructions.add(ContentInstruction("c", listOf(
            PdfNumber((cx + rx).toDouble()), PdfNumber((cy + ky).toDouble()),
            PdfNumber((cx + kx).toDouble()), PdfNumber((cy + ry).toDouble()),
            PdfNumber(cx.toDouble()), PdfNumber((cy + ry).toDouble())
        )))
        instructions.add(ContentInstruction("c", listOf(
            PdfNumber((cx - kx).toDouble()), PdfNumber((cy + ry).toDouble()),
            PdfNumber((cx - rx).toDouble()), PdfNumber((cy + ky).toDouble()),
            PdfNumber((cx - rx).toDouble()), PdfNumber(cy.toDouble())
        )))
        instructions.add(ContentInstruction("c", listOf(
            PdfNumber((cx - rx).toDouble()), PdfNumber((cy - ky).toDouble()),
            PdfNumber((cx - kx).toDouble()), PdfNumber((cy - ry).toDouble()),
            PdfNumber(cx.toDouble()), PdfNumber((cy - ry).toDouble())
        )))
        instructions.add(ContentInstruction("c", listOf(
            PdfNumber((cx + kx).toDouble()), PdfNumber((cy - ry).toDouble()),
            PdfNumber((cx + rx).toDouble()), PdfNumber((cy - ky).toDouble()),
            PdfNumber((cx + rx).toDouble()), PdfNumber(cy.toDouble())
        )))
        
        val paintOp = when {
            strokeColor != null && fillColor != null -> "B"
            fillColor != null -> "f"
            else -> "S"
        }
        instructions.add(ContentInstruction(paintOp, emptyList()))
        
        instructions.add(ContentInstruction("Q", emptyList()))
        
        return appendToPage(page, instructions)
    }
    
    /**
     * 将指令追加到页面
     */
    private fun appendToPage(page: PdfDictionary, instructions: List<ContentInstruction>): Boolean {
        val newData = contentParser.serialize(instructions)
        val contents = document.getPageContents(page)
        
        if (contents.isEmpty()) {
            val newStream = PdfStream(PdfDictionary(), newData)
            newStream.dict["Length"] = PdfNumber(newData.size)
            val streamRef = document.addObject(newStream)
            page["Contents"] = streamRef
        } else {
            val lastStream = contents.last()
            val existingData = lastStream.rawData
            val combinedData = existingData + "\n".toByteArray() + newData
            
            lastStream.rawData = combinedData
            lastStream.dict["Length"] = PdfNumber(combinedData.size)
            lastStream.dict.remove("Filter")
            lastStream.clearDecodedCache()
        }
        
        document.markModified()
        return true
    }
    
    private fun PdfObject.asFloat(): Float = (this as? PdfNumber)?.toFloat() ?: 0f
}

/**
 * 路径段
 */
sealed class PathSegment {
    data class MoveTo(val x: Float, val y: Float) : PathSegment()
    data class LineTo(val x: Float, val y: Float) : PathSegment()
    data class CurveTo(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x3: Float, val y3: Float
    ) : PathSegment()
    data class Rectangle(val x: Float, val y: Float, val w: Float, val h: Float) : PathSegment()
    object Close : PathSegment()
}

/**
 * 图形元素
 */
sealed class GraphicsElement {
    data class Path(
        val segments: List<PathSegment>,
        val strokeColor: Triple<Float, Float, Float>?,
        val fillColor: Triple<Float, Float, Float>?,
        val lineWidth: Float
    ) : GraphicsElement() {
        fun getBounds(): PdfRectangle {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            
            for (segment in segments) {
                when (segment) {
                    is PathSegment.MoveTo -> {
                        minX = minOf(minX, segment.x)
                        minY = minOf(minY, segment.y)
                        maxX = maxOf(maxX, segment.x)
                        maxY = maxOf(maxY, segment.y)
                    }
                    is PathSegment.LineTo -> {
                        minX = minOf(minX, segment.x)
                        minY = minOf(minY, segment.y)
                        maxX = maxOf(maxX, segment.x)
                        maxY = maxOf(maxY, segment.y)
                    }
                    is PathSegment.CurveTo -> {
                        minX = minOf(minX, segment.x1, segment.x2, segment.x3)
                        minY = minOf(minY, segment.y1, segment.y2, segment.y3)
                        maxX = maxOf(maxX, segment.x1, segment.x2, segment.x3)
                        maxY = maxOf(maxY, segment.y1, segment.y2, segment.y3)
                    }
                    is PathSegment.Rectangle -> {
                        minX = minOf(minX, segment.x, segment.x + segment.w)
                        minY = minOf(minY, segment.y, segment.y + segment.h)
                        maxX = maxOf(maxX, segment.x, segment.x + segment.w)
                        maxY = maxOf(maxY, segment.y, segment.y + segment.h)
                    }
                    is PathSegment.Close -> {}
                }
            }
            
            return PdfRectangle(minX, minY, maxX, maxY)
        }
    }
}
