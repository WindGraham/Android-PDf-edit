package com.pdfrender.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.pdfcore.model.PdfDictionary
import com.pdfcore.model.PdfDocument
import com.pdfcore.model.PdfRectangle
import com.pdfrender.engine.PdfRenderEngine
import kotlin.math.max
import kotlin.math.min

/**
 * PDF 页面视图
 * 
 * 支持缩放、平移、页面导航
 */
class PdfPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // PDF 文档
    private var document: PdfDocument? = null
    private var renderEngine: PdfRenderEngine? = null
    
    // 当前页面
    private var currentPageIndex = 0
    private var currentPage: PdfDictionary? = null
    private var pageBitmap: Bitmap? = null
    
    // 缩放和平移
    private var scaleFactor = 1f
    private var minScale = 0.5f
    private var maxScale = 5f
    private var translateX = 0f
    private var translateY = 0f
    
    // 渲染参数
    private var renderScale = 2f // 渲染时的缩放，提高清晰度
    private var backgroundColor = Color.LTGRAY
    private var pageBackgroundColor = Color.WHITE
    
    // 手势检测
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    
    // 监听器
    var onPageChangeListener: ((Int, Int) -> Unit)? = null
    var onTextSelectedListener: ((String, RectF) -> Unit)? = null
    var onTapListener: ((Float, Float) -> Unit)? = null
    
    // 画笔
    private val bitmapPaint = Paint().apply {
        isFilterBitmap = true
    }
    private val shadowPaint = Paint().apply {
        color = Color.argb(50, 0, 0, 0)
    }
    
    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }
    
    /**
     * 设置 PDF 文档
     */
    fun setDocument(doc: PdfDocument) {
        document = doc
        renderEngine = PdfRenderEngine(doc)
        currentPageIndex = 0
        loadPage(currentPageIndex)
        requestLayout()
        invalidate()
    }
    
    /**
     * 获取当前页码（从 0 开始）
     */
    fun getCurrentPage(): Int = currentPageIndex
    
    /**
     * 获取总页数
     */
    fun getPageCount(): Int = document?.getPageCount() ?: 0
    
    /**
     * 跳转到指定页面
     */
    fun goToPage(pageIndex: Int) {
        val pageCount = getPageCount()
        if (pageIndex < 0 || pageIndex >= pageCount) return
        if (pageIndex == currentPageIndex) return
        
        currentPageIndex = pageIndex
        loadPage(pageIndex)
        resetViewport()
        invalidate()
        
        onPageChangeListener?.invoke(currentPageIndex, pageCount)
    }
    
    /**
     * 下一页
     */
    fun nextPage() {
        goToPage(currentPageIndex + 1)
    }
    
    /**
     * 上一页
     */
    fun previousPage() {
        goToPage(currentPageIndex - 1)
    }
    
    /**
     * 设置缩放级别
     */
    fun setScale(scale: Float) {
        scaleFactor = scale.coerceIn(minScale, maxScale)
        invalidate()
    }
    
    /**
     * 重置视图
     */
    fun resetViewport() {
        scaleFactor = 1f
        translateX = 0f
        translateY = 0f
        invalidate()
    }
    
    /**
     * 加载页面
     */
    private fun loadPage(index: Int) {
        val doc = document ?: return
        val engine = renderEngine ?: return
        
        currentPage = doc.getPage(index)
        currentPage?.let { page ->
            pageBitmap?.recycle()
            pageBitmap = engine.renderPage(page, renderScale, pageBackgroundColor)
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(widthSize, 800)
            else -> 800
        }
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(heightSize, 1200)
            else -> 1200
        }
        
        setMeasuredDimension(width, height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制背景
        canvas.drawColor(backgroundColor)
        
        val bitmap = pageBitmap ?: return
        
        // 计算页面在视图中的位置和大小
        val pageWidth = bitmap.width / renderScale
        val pageHeight = bitmap.height / renderScale
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        // 计算适合视图的初始缩放
        val fitScale = min(viewWidth / pageWidth, viewHeight / pageHeight) * 0.9f
        val displayScale = fitScale * scaleFactor
        
        val displayWidth = pageWidth * displayScale
        val displayHeight = pageHeight * displayScale
        
        // 居中显示
        val centerX = (viewWidth - displayWidth) / 2 + translateX
        val centerY = (viewHeight - displayHeight) / 2 + translateY
        
        // 绘制阴影
        val shadowOffset = 4 * resources.displayMetrics.density
        canvas.drawRect(
            centerX + shadowOffset,
            centerY + shadowOffset,
            centerX + displayWidth + shadowOffset,
            centerY + displayHeight + shadowOffset,
            shadowPaint
        )
        
        // 绘制页面
        val destRect = RectF(centerX, centerY, centerX + displayWidth, centerY + displayHeight)
        canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }
    
    /**
     * 将视图坐标转换为页面坐标
     */
    fun viewToPageCoordinates(viewX: Float, viewY: Float): PointF? {
        val bitmap = pageBitmap ?: return null
        val page = currentPage ?: return null
        val mediaBox = document?.getPageMediaBox(page) ?: return null
        
        val pageWidth = bitmap.width / renderScale
        val pageHeight = bitmap.height / renderScale
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        val fitScale = min(viewWidth / pageWidth, viewHeight / pageHeight) * 0.9f
        val displayScale = fitScale * scaleFactor
        
        val displayWidth = pageWidth * displayScale
        val displayHeight = pageHeight * displayScale
        
        val centerX = (viewWidth - displayWidth) / 2 + translateX
        val centerY = (viewHeight - displayHeight) / 2 + translateY
        
        // 检查是否在页面范围内
        if (viewX < centerX || viewX > centerX + displayWidth ||
            viewY < centerY || viewY > centerY + displayHeight) {
            return null
        }
        
        // 转换为页面坐标 (PDF 坐标系，原点在左下角)
        val pageX = (viewX - centerX) / displayScale + mediaBox.llx
        val pageY = mediaBox.ury - (viewY - centerY) / displayScale
        
        return PointF(pageX, pageY)
    }
    
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(minScale, maxScale)
            invalidate()
            return true
        }
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            translateX -= distanceX
            translateY -= distanceY
            invalidate()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scaleFactor > 1.5f) {
                resetViewport()
            } else {
                scaleFactor = 2f
                invalidate()
            }
            return true
        }
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTapListener?.invoke(e.x, e.y)
            return true
        }
        
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            // 快速滑动换页
            if (kotlin.math.abs(velocityX) > kotlin.math.abs(velocityY) * 2 &&
                kotlin.math.abs(velocityX) > 1000) {
                if (velocityX > 0) {
                    previousPage()
                } else {
                    nextPage()
                }
                return true
            }
            return false
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pageBitmap?.recycle()
        pageBitmap = null
    }
}
