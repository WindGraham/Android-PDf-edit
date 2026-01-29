package com.pdftools

import android.app.Application
import com.pdfrender.font.FontAssetManager

/**
 * PDFtools 应用程序类
 */
class PDFtoolsApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化字体资源管理器
        FontAssetManager.init(this)
    }
}
