package com.pdfrender.font

import android.content.Context
import android.graphics.Typeface
import android.util.Log

/**
 * 字体资源管理器
 * 
 * 管理应用内打包的字体资源，提供 CJK 字体和数学字体的加载和缓存。
 * 
 * 支持的字体类型：
 * - 中文衬线字体（宋体风格）：Noto Serif CJK SC
 * - 中文无衬线字体（黑体风格）：Noto Sans CJK SC
 * - 数学符号字体：Noto Sans Math
 */
object FontAssetManager {
    
    private const val TAG = "FontAssetManager"
    
    // 字体文件路径
    // TTC (TrueType Collection) 包含多种语言变体，Android 会自动选择简体中文
    private const val FONT_SERIF_CJK = "fonts/NotoSerifCJK-Regular.ttc"
    private const val FONT_SANS_CJK = "fonts/NotoSansCJK-Regular.ttc"
    private const val FONT_MATH = "fonts/NotoSansMath-Regular.ttf"
    
    // 字体缓存
    private var serifCJKTypeface: Typeface? = null
    private var sansCJKTypeface: Typeface? = null
    private var mathTypeface: Typeface? = null
    
    // 初始化状态
    private var initialized = false
    private var context: Context? = null
    
    /**
     * 初始化字体资源
     * 
     * 应在应用启动时调用，例如在 Application.onCreate() 中。
     * 使用懒加载策略，只有在首次请求字体时才会实际加载。
     * 
     * @param context 应用上下文
     */
    fun init(context: Context) {
        if (initialized) return
        
        this.context = context.applicationContext
        initialized = true
        
        Log.d(TAG, "FontAssetManager initialized")
    }
    
    /**
     * 获取中文衬线字体（宋体风格）
     * 
     * 用于渲染 PDF 中的宋体、明体等衬线字体。
     * 如果字体文件不存在或加载失败，返回系统默认衬线字体。
     */
    fun getSerifCJK(): Typeface {
        if (serifCJKTypeface == null) {
            serifCJKTypeface = loadTypeface(FONT_SERIF_CJK)
        }
        return serifCJKTypeface ?: Typeface.SERIF
    }
    
    /**
     * 获取中文无衬线字体（黑体风格）
     * 
     * 用于渲染 PDF 中的黑体、雅黑等无衬线字体。
     * 如果字体文件不存在或加载失败，返回系统默认无衬线字体。
     */
    fun getSansCJK(): Typeface {
        if (sansCJKTypeface == null) {
            sansCJKTypeface = loadTypeface(FONT_SANS_CJK)
        }
        return sansCJKTypeface ?: Typeface.SANS_SERIF
    }
    
    /**
     * 获取数学符号字体
     * 
     * 用于渲染 PDF 中的数学公式和符号。
     * 包含希腊字母、运算符、积分符号等数学相关字符。
     * 如果字体文件不存在或加载失败，返回系统默认字体。
     */
    fun getMathFont(): Typeface {
        if (mathTypeface == null) {
            mathTypeface = loadTypeface(FONT_MATH)
        }
        return mathTypeface ?: Typeface.DEFAULT
    }
    
    /**
     * 根据字体类型获取对应的 Typeface
     * 
     * @param fontType 字体类型
     * @param isBold 是否粗体
     */
    fun getTypeface(fontType: FontType, isBold: Boolean = false): Typeface {
        val baseTypeface = when (fontType) {
            FontType.SERIF_CJK -> getSerifCJK()
            FontType.SANS_CJK -> getSansCJK()
            FontType.MATH -> getMathFont()
            FontType.SERIF -> Typeface.SERIF
            FontType.SANS_SERIF -> Typeface.SANS_SERIF
            FontType.MONOSPACE -> Typeface.MONOSPACE
            FontType.DEFAULT -> Typeface.DEFAULT
        }
        
        return if (isBold) {
            Typeface.create(baseTypeface, Typeface.BOLD)
        } else {
            baseTypeface
        }
    }
    
    /**
     * 从 assets 加载字体文件
     */
    private fun loadTypeface(fontPath: String): Typeface? {
        val ctx = context ?: run {
            Log.w(TAG, "FontAssetManager not initialized, call init() first")
            return null
        }
        
        return try {
            Typeface.createFromAsset(ctx.assets, fontPath)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load font: $fontPath", e)
            null
        }
    }
    
    /**
     * 检查字体资源是否可用
     */
    fun isSerifCJKAvailable(): Boolean {
        return getSerifCJK() != Typeface.SERIF
    }
    
    fun isSansCJKAvailable(): Boolean {
        return getSansCJK() != Typeface.SANS_SERIF
    }
    
    fun isMathFontAvailable(): Boolean {
        return getMathFont() != Typeface.DEFAULT
    }
    
    /**
     * 预加载所有字体
     * 
     * 可以在后台线程调用，以避免首次使用时的加载延迟。
     */
    fun preloadAll() {
        getSerifCJK()
        getSansCJK()
        getMathFont()
        Log.d(TAG, "All fonts preloaded")
    }
    
    /**
     * 释放字体缓存
     * 
     * 在内存紧张时可以调用此方法释放字体缓存。
     */
    fun release() {
        serifCJKTypeface = null
        sansCJKTypeface = null
        mathTypeface = null
        Log.d(TAG, "Font cache released")
    }
    
    /**
     * 字体类型枚举
     */
    enum class FontType {
        SERIF_CJK,      // 中文衬线（宋体）
        SANS_CJK,       // 中文无衬线（黑体）
        MATH,           // 数学符号
        SERIF,          // 西文衬线
        SANS_SERIF,     // 西文无衬线
        MONOSPACE,      // 等宽
        DEFAULT         // 系统默认
    }
}
