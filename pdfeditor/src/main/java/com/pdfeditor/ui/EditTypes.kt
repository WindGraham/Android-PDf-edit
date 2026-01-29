package com.pdfeditor.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.UUID

/**
 * UI 编辑操作类型 - 用于 PDF 编辑器 Activity
 */
sealed class EditOperation(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 涂鸦/手绘
     */
    data class DrawPath(
        val pageIdx: Int,
        val path: Path,
        val paint: Paint,
        val points: List<Pair<Float, Float>>
    ) : EditOperation(pageIndex = pageIdx)
    
    /**
     * 文字注释
     */
    data class TextAnnotation(
        val pageIdx: Int,
        val text: String,
        val x: Float,
        val y: Float,
        val paint: Paint,
        val fontSize: Float = 16f
    ) : EditOperation(pageIndex = pageIdx)
    
    /**
     * 高亮/下划线
     */
    data class Highlight(
        val pageIdx: Int,
        val rect: RectF,
        val color: Int,
        val type: HighlightType = HighlightType.HIGHLIGHT
    ) : EditOperation(pageIndex = pageIdx)
    
    /**
     * 图片插入
     */
    data class ImageStamp(
        val pageIdx: Int,
        val imagePath: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    ) : EditOperation(pageIndex = pageIdx)
}

enum class HighlightType {
    HIGHLIGHT,
    UNDERLINE,
    STRIKEOUT,
    SQUIGGLY
}

enum class EditTool {
    NONE,
    PEN,
    ERASER,
    TEXT,
    HIGHLIGHT,
    SIGNATURE
}

/**
 * 编辑配置
 */
data class EditConfig(
    val penColor: Int = Color.RED,
    val penWidth: Float = 5f,
    val textColor: Int = Color.BLACK,
    val textSize: Float = 24f,
    val highlightColor: Int = Color.YELLOW
)
