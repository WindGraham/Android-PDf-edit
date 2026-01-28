package com.pdfcore.content

import com.pdfcore.model.PdfObject

/**
 * PDF 内容流操作符
 * 基于 PDF 32000-1:2008 Annex A
 */

/**
 * 内容流指令（操作符 + 操作数）
 */
data class ContentInstruction(
    val operator: String,
    val operands: List<PdfObject>
) {
    /**
     * 获取指定索引的操作数
     */
    inline fun <reified T : PdfObject> getOperand(index: Int): T? {
        return operands.getOrNull(index) as? T
    }
    
    /**
     * 转换为 PDF 内容流格式
     */
    fun toPdfString(): String {
        return if (operands.isEmpty()) {
            operator
        } else {
            operands.joinToString(" ") { it.toPdfString() } + " " + operator
        }
    }
}

/**
 * 操作符类别
 */
enum class OperatorCategory {
    GRAPHICS_STATE,      // 图形状态
    PATH_CONSTRUCTION,   // 路径构建
    PATH_PAINTING,       // 路径绘制
    CLIPPING_PATH,       // 裁剪路径
    TEXT_OBJECT,         // 文本对象
    TEXT_STATE,          // 文本状态
    TEXT_POSITIONING,    // 文本定位
    TEXT_SHOWING,        // 文本显示
    COLOR,               // 颜色
    SHADING,             // 着色
    EXTERNAL_OBJECT,     // 外部对象
    INLINE_IMAGE,        // 内联图像
    MARKED_CONTENT,      // 标记内容
    COMPATIBILITY        // 兼容性
}

/**
 * 操作符定义
 */
data class OperatorDef(
    val name: String,
    val category: OperatorCategory,
    val operandCount: Int = -1,  // -1 表示可变数量
    val description: String = ""
)

/**
 * PDF 内容流操作符表
 */
object ContentOperators {
    
    /**
     * 所有操作符映射
     */
    val all: Map<String, OperatorDef> = mapOf(
        // Graphics state (PDF 32000-1:2008 Table 57)
        "q" to OperatorDef("q", OperatorCategory.GRAPHICS_STATE, 0, "Save graphics state"),
        "Q" to OperatorDef("Q", OperatorCategory.GRAPHICS_STATE, 0, "Restore graphics state"),
        "cm" to OperatorDef("cm", OperatorCategory.GRAPHICS_STATE, 6, "Concat matrix"),
        "w" to OperatorDef("w", OperatorCategory.GRAPHICS_STATE, 1, "Set line width"),
        "J" to OperatorDef("J", OperatorCategory.GRAPHICS_STATE, 1, "Set line cap"),
        "j" to OperatorDef("j", OperatorCategory.GRAPHICS_STATE, 1, "Set line join"),
        "M" to OperatorDef("M", OperatorCategory.GRAPHICS_STATE, 1, "Set miter limit"),
        "d" to OperatorDef("d", OperatorCategory.GRAPHICS_STATE, 2, "Set dash pattern"),
        "ri" to OperatorDef("ri", OperatorCategory.GRAPHICS_STATE, 1, "Set rendering intent"),
        "i" to OperatorDef("i", OperatorCategory.GRAPHICS_STATE, 1, "Set flatness"),
        "gs" to OperatorDef("gs", OperatorCategory.GRAPHICS_STATE, 1, "Set from ExtGState"),
        
        // Path construction (PDF 32000-1:2008 Table 59)
        "m" to OperatorDef("m", OperatorCategory.PATH_CONSTRUCTION, 2, "Move to"),
        "l" to OperatorDef("l", OperatorCategory.PATH_CONSTRUCTION, 2, "Line to"),
        "c" to OperatorDef("c", OperatorCategory.PATH_CONSTRUCTION, 6, "Cubic Bézier curve"),
        "v" to OperatorDef("v", OperatorCategory.PATH_CONSTRUCTION, 4, "Cubic Bézier (initial point replicated)"),
        "y" to OperatorDef("y", OperatorCategory.PATH_CONSTRUCTION, 4, "Cubic Bézier (final point replicated)"),
        "h" to OperatorDef("h", OperatorCategory.PATH_CONSTRUCTION, 0, "Close subpath"),
        "re" to OperatorDef("re", OperatorCategory.PATH_CONSTRUCTION, 4, "Rectangle"),
        
        // Path painting (PDF 32000-1:2008 Table 60)
        "S" to OperatorDef("S", OperatorCategory.PATH_PAINTING, 0, "Stroke path"),
        "s" to OperatorDef("s", OperatorCategory.PATH_PAINTING, 0, "Close and stroke"),
        "f" to OperatorDef("f", OperatorCategory.PATH_PAINTING, 0, "Fill (nonzero)"),
        "F" to OperatorDef("F", OperatorCategory.PATH_PAINTING, 0, "Fill (nonzero) - obsolete"),
        "f*" to OperatorDef("f*", OperatorCategory.PATH_PAINTING, 0, "Fill (even-odd)"),
        "B" to OperatorDef("B", OperatorCategory.PATH_PAINTING, 0, "Fill and stroke (nonzero)"),
        "B*" to OperatorDef("B*", OperatorCategory.PATH_PAINTING, 0, "Fill and stroke (even-odd)"),
        "b" to OperatorDef("b", OperatorCategory.PATH_PAINTING, 0, "Close, fill, stroke (nonzero)"),
        "b*" to OperatorDef("b*", OperatorCategory.PATH_PAINTING, 0, "Close, fill, stroke (even-odd)"),
        "n" to OperatorDef("n", OperatorCategory.PATH_PAINTING, 0, "End path (no paint)"),
        
        // Clipping (PDF 32000-1:2008 Table 61)
        "W" to OperatorDef("W", OperatorCategory.CLIPPING_PATH, 0, "Clip (nonzero)"),
        "W*" to OperatorDef("W*", OperatorCategory.CLIPPING_PATH, 0, "Clip (even-odd)"),
        
        // Text object (PDF 32000-1:2008 Table 107)
        "BT" to OperatorDef("BT", OperatorCategory.TEXT_OBJECT, 0, "Begin text object"),
        "ET" to OperatorDef("ET", OperatorCategory.TEXT_OBJECT, 0, "End text object"),
        
        // Text state (PDF 32000-1:2008 Table 105)
        "Tc" to OperatorDef("Tc", OperatorCategory.TEXT_STATE, 1, "Set character spacing"),
        "Tw" to OperatorDef("Tw", OperatorCategory.TEXT_STATE, 1, "Set word spacing"),
        "Tz" to OperatorDef("Tz", OperatorCategory.TEXT_STATE, 1, "Set horizontal scaling"),
        "TL" to OperatorDef("TL", OperatorCategory.TEXT_STATE, 1, "Set leading"),
        "Tf" to OperatorDef("Tf", OperatorCategory.TEXT_STATE, 2, "Set font and size"),
        "Tr" to OperatorDef("Tr", OperatorCategory.TEXT_STATE, 1, "Set rendering mode"),
        "Ts" to OperatorDef("Ts", OperatorCategory.TEXT_STATE, 1, "Set rise"),
        
        // Text positioning (PDF 32000-1:2008 Table 108)
        "Td" to OperatorDef("Td", OperatorCategory.TEXT_POSITIONING, 2, "Move text position"),
        "TD" to OperatorDef("TD", OperatorCategory.TEXT_POSITIONING, 2, "Move text position and set leading"),
        "Tm" to OperatorDef("Tm", OperatorCategory.TEXT_POSITIONING, 6, "Set text matrix"),
        "T*" to OperatorDef("T*", OperatorCategory.TEXT_POSITIONING, 0, "Move to start of next line"),
        
        // Text showing (PDF 32000-1:2008 Table 109)
        "Tj" to OperatorDef("Tj", OperatorCategory.TEXT_SHOWING, 1, "Show text"),
        "TJ" to OperatorDef("TJ", OperatorCategory.TEXT_SHOWING, 1, "Show text with positioning"),
        "'" to OperatorDef("'", OperatorCategory.TEXT_SHOWING, 1, "Move to next line and show text"),
        "\"" to OperatorDef("\"", OperatorCategory.TEXT_SHOWING, 3, "Set spacing, move, show text"),
        
        // Color (PDF 32000-1:2008 Table 74)
        "CS" to OperatorDef("CS", OperatorCategory.COLOR, 1, "Set stroking color space"),
        "cs" to OperatorDef("cs", OperatorCategory.COLOR, 1, "Set nonstroking color space"),
        "SC" to OperatorDef("SC", OperatorCategory.COLOR, -1, "Set stroking color"),
        "SCN" to OperatorDef("SCN", OperatorCategory.COLOR, -1, "Set stroking color (pattern)"),
        "sc" to OperatorDef("sc", OperatorCategory.COLOR, -1, "Set nonstroking color"),
        "scn" to OperatorDef("scn", OperatorCategory.COLOR, -1, "Set nonstroking color (pattern)"),
        "G" to OperatorDef("G", OperatorCategory.COLOR, 1, "Set stroking gray"),
        "g" to OperatorDef("g", OperatorCategory.COLOR, 1, "Set nonstroking gray"),
        "RG" to OperatorDef("RG", OperatorCategory.COLOR, 3, "Set stroking RGB"),
        "rg" to OperatorDef("rg", OperatorCategory.COLOR, 3, "Set nonstroking RGB"),
        "K" to OperatorDef("K", OperatorCategory.COLOR, 4, "Set stroking CMYK"),
        "k" to OperatorDef("k", OperatorCategory.COLOR, 4, "Set nonstroking CMYK"),
        
        // Shading
        "sh" to OperatorDef("sh", OperatorCategory.SHADING, 1, "Paint shading"),
        
        // XObject (PDF 32000-1:2008 Table 87)
        "Do" to OperatorDef("Do", OperatorCategory.EXTERNAL_OBJECT, 1, "Invoke XObject"),
        
        // Inline image (PDF 32000-1:2008 Table 92)
        "BI" to OperatorDef("BI", OperatorCategory.INLINE_IMAGE, 0, "Begin inline image"),
        "ID" to OperatorDef("ID", OperatorCategory.INLINE_IMAGE, 0, "Inline image data"),
        "EI" to OperatorDef("EI", OperatorCategory.INLINE_IMAGE, 0, "End inline image"),
        
        // Marked content (PDF 32000-1:2008 Table 320)
        "MP" to OperatorDef("MP", OperatorCategory.MARKED_CONTENT, 1, "Marked content point"),
        "DP" to OperatorDef("DP", OperatorCategory.MARKED_CONTENT, 2, "Marked content point with properties"),
        "BMC" to OperatorDef("BMC", OperatorCategory.MARKED_CONTENT, 1, "Begin marked content"),
        "BDC" to OperatorDef("BDC", OperatorCategory.MARKED_CONTENT, 2, "Begin marked content with properties"),
        "EMC" to OperatorDef("EMC", OperatorCategory.MARKED_CONTENT, 0, "End marked content"),
        
        // Compatibility (PDF 32000-1:2008 Table 32)
        "BX" to OperatorDef("BX", OperatorCategory.COMPATIBILITY, 0, "Begin compatibility section"),
        "EX" to OperatorDef("EX", OperatorCategory.COMPATIBILITY, 0, "End compatibility section")
    )
    
    /**
     * 获取操作符定义
     */
    fun get(name: String): OperatorDef? = all[name]
    
    /**
     * 是否是有效的操作符
     */
    fun isOperator(name: String): Boolean = all.containsKey(name)
    
    /**
     * 是否是文本相关的操作符
     */
    fun isTextOperator(name: String): Boolean {
        val def = all[name] ?: return false
        return def.category in listOf(
            OperatorCategory.TEXT_OBJECT,
            OperatorCategory.TEXT_STATE,
            OperatorCategory.TEXT_POSITIONING,
            OperatorCategory.TEXT_SHOWING
        )
    }
    
    /**
     * 是否是图形相关的操作符
     */
    fun isGraphicsOperator(name: String): Boolean {
        val def = all[name] ?: return false
        return def.category in listOf(
            OperatorCategory.PATH_CONSTRUCTION,
            OperatorCategory.PATH_PAINTING,
            OperatorCategory.CLIPPING_PATH,
            OperatorCategory.GRAPHICS_STATE
        )
    }
}
