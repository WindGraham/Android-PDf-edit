package com.pdfcore.font

/**
 * 预定义 CMap
 * 基于 PDF 32000-1:2008 标准 9.7.5.2 和 Adobe CMap 资源
 * 
 * 支持的预定义 CMap:
 * - Identity-H: 水平 Identity 映射 (CID = character code)
 * - Identity-V: 垂直 Identity 映射
 * - Adobe-GB1-UCS2: 简体中文 (GB) CID 到 Unicode
 * - Adobe-CNS1-UCS2: 繁体中文 (CNS) CID 到 Unicode
 * - Adobe-Japan1-UCS2: 日文 CID 到 Unicode
 * - Adobe-Korea1-UCS2: 韩文 CID 到 Unicode
 * - UniGB-UCS2-H/V: 简体中文 Unicode 到 CID
 * - UniCNS-UCS2-H/V: 繁体中文 Unicode 到 CID
 * - UniJIS-UCS2-H/V: 日文 Unicode 到 CID
 * - UniKS-UCS2-H/V: 韩文 Unicode 到 CID
 */
object PredefinedCMaps {
    
    // 预定义 CMap 缓存
    private val cmapCache = mutableMapOf<String, CMap>()
    
    /**
     * 获取预定义 CMap
     */
    fun get(name: String): CMap? {
        // 检查缓存
        cmapCache[name]?.let { return it }
        
        // 创建预定义 CMap
        val cmap = createPredefined(name)
        if (cmap != null) {
            cmapCache[name] = cmap
        }
        return cmap
    }
    
    /**
     * 判断是否为预定义 CMap 名称
     */
    fun isPredefined(name: String): Boolean {
        return name in listOf(
            "Identity-H", "Identity-V",
            "Adobe-GB1-UCS2", "Adobe-CNS1-UCS2", "Adobe-Japan1-UCS2", "Adobe-Korea1-UCS2",
            "UniGB-UCS2-H", "UniGB-UCS2-V", "UniGB-UTF16-H", "UniGB-UTF16-V",
            "UniCNS-UCS2-H", "UniCNS-UCS2-V", "UniCNS-UTF16-H", "UniCNS-UTF16-V",
            "UniJIS-UCS2-H", "UniJIS-UCS2-V", "UniJIS-UTF16-H", "UniJIS-UTF16-V",
            "UniKS-UCS2-H", "UniKS-UCS2-V", "UniKS-UTF16-H", "UniKS-UTF16-V",
            "GBK-EUC-H", "GBK-EUC-V", "GBK2K-H", "GBK2K-V",
            "GB-EUC-H", "GB-EUC-V", "GB-H", "GB-V",
            "GBpc-EUC-H", "GBpc-EUC-V",
            "GBT-EUC-H", "GBT-EUC-V", "GBT-H", "GBT-V",
            "GBTpc-EUC-H", "GBTpc-EUC-V",
            "B5pc-H", "B5pc-V", "B5-H", "B5-V",
            "HKscs-B5-H", "HKscs-B5-V",
            "ETen-B5-H", "ETen-B5-V", "ETenms-B5-H", "ETenms-B5-V",
            "CNS-EUC-H", "CNS-EUC-V", "CNS1-H", "CNS1-V", "CNS2-H", "CNS2-V",
            "83pv-RKSJ-H", "90ms-RKSJ-H", "90ms-RKSJ-V", "90msp-RKSJ-H", "90msp-RKSJ-V",
            "90pv-RKSJ-H", "90pv-RKSJ-V",
            "Add-RKSJ-H", "Add-RKSJ-V", "Add-H", "Add-V",
            "EUC-H", "EUC-V", "Ext-RKSJ-H", "Ext-RKSJ-V", "Ext-H", "Ext-V",
            "H", "V", "Hankaku", "Hiragana", "Katakana", "Roman", "WP-Symbol",
            "KSC-EUC-H", "KSC-EUC-V", "KSC-H", "KSC-V",
            "KSCms-UHC-H", "KSCms-UHC-V", "KSCms-UHC-HW-H", "KSCms-UHC-HW-V",
            "KSCpc-EUC-H", "KSCpc-EUC-V"
        ) || name.startsWith("Identity")
    }
    
    /**
     * 创建预定义 CMap
     */
    private fun createPredefined(name: String): CMap? {
        return when {
            name == "Identity-H" -> createIdentityH()
            name == "Identity-V" -> createIdentityV()
            name.startsWith("Identity") -> createIdentityH() // 其他 Identity 变体
            
            // Adobe-XX-UCS2: CID 到 Unicode 映射
            name == "Adobe-GB1-UCS2" -> createAdobeGB1UCS2()
            name == "Adobe-CNS1-UCS2" -> createAdobeCNS1UCS2()
            name == "Adobe-Japan1-UCS2" -> createAdobeJapan1UCS2()
            name == "Adobe-Korea1-UCS2" -> createAdobeKorea1UCS2()
            
            // UniXX-UCS2-H/V: Unicode 到 CID 映射 (简体中文)
            name == "UniGB-UCS2-H" || name == "UniGB-UTF16-H" -> createUniGBH()
            name == "UniGB-UCS2-V" || name == "UniGB-UTF16-V" -> createUniGBV()
            
            // UniXX-UCS2-H/V: Unicode 到 CID 映射 (繁体中文)
            name == "UniCNS-UCS2-H" || name == "UniCNS-UTF16-H" -> createUniCNSH()
            name == "UniCNS-UCS2-V" || name == "UniCNS-UTF16-V" -> createUniCNSV()
            
            // UniXX-UCS2-H/V: Unicode 到 CID 映射 (日文)
            name == "UniJIS-UCS2-H" || name == "UniJIS-UTF16-H" -> createUniJISH()
            name == "UniJIS-UCS2-V" || name == "UniJIS-UTF16-V" -> createUniJISV()
            
            // UniXX-UCS2-H/V: Unicode 到 CID 映射 (韩文)
            name == "UniKS-UCS2-H" || name == "UniKS-UTF16-H" -> createUniKSH()
            name == "UniKS-UCS2-V" || name == "UniKS-UTF16-V" -> createUniKSV()
            
            // GBK/GB 相关 CMap
            name.startsWith("GBK") || name.startsWith("GB") -> createGBKCMap(name)
            
            // Big5 相关 CMap
            name.startsWith("B5") || name.startsWith("ETen") || name.startsWith("HKscs") -> createBig5CMap(name)
            
            // CNS 相关 CMap
            name.startsWith("CNS") -> createCNSCMap(name)
            
            // 日文 CMap
            name.endsWith("-RKSJ-H") || name.endsWith("-RKSJ-V") ||
            name == "EUC-H" || name == "EUC-V" ||
            name == "H" || name == "V" -> createJapaneseCMap(name)
            
            // 韩文 CMap
            name.startsWith("KSC") -> createKoreanCMap(name)
            
            else -> null
        }
    }
    
    /**
     * 创建 Identity-H CMap
     * 水平书写模式，CID = character code
     */
    private fun createIdentityH(): CMap {
        return CMap().apply {
            name = "Identity-H"
            registry = "Adobe"
            ordering = "Identity"
            supplement = 0
            wMode = 0  // 水平
            
            // 添加 2 字节 codespace
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
            
            // Identity 映射: CID = code
            // 不需要显式添加映射，因为 getCid() 会返回 code 本身
        }
    }
    
    /**
     * 创建 Identity-V CMap
     * 垂直书写模式，CID = character code
     */
    private fun createIdentityV(): CMap {
        return CMap().apply {
            name = "Identity-V"
            registry = "Adobe"
            ordering = "Identity"
            supplement = 0
            wMode = 1  // 垂直
            
            // 添加 2 字节 codespace
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
        }
    }
    
    /**
     * 创建 Adobe-GB1-UCS2 CMap (简体中文)
     * 将 CID 映射到 Unicode
     */
    private fun createAdobeGB1UCS2(): CMap {
        return CMap().apply {
            name = "Adobe-GB1-UCS2"
            registry = "Adobe"
            ordering = "GB1"
            supplement = 5
            wMode = 0
            
            // 添加 2 字节 codespace
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
            
            // 添加基本的 CID 到 Unicode 映射
            // CID 0-94 映射到 ASCII
            for (cid in 1..94) {
                addUnicodeMapping(cid, (cid + 32).toChar().toString())
            }
            
            // 常用汉字映射（示例，完整映射需要完整的 CID 表）
            addCommonChineseSimplifiedMappings(this)
        }
    }
    
    /**
     * 创建 Adobe-CNS1-UCS2 CMap (繁体中文)
     */
    private fun createAdobeCNS1UCS2(): CMap {
        return CMap().apply {
            name = "Adobe-CNS1-UCS2"
            registry = "Adobe"
            ordering = "CNS1"
            supplement = 6
            wMode = 0
            
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
            
            // 添加基本映射
            for (cid in 1..94) {
                addUnicodeMapping(cid, (cid + 32).toChar().toString())
            }
            
            addCommonChineseTraditionalMappings(this)
        }
    }
    
    /**
     * 创建 Adobe-Japan1-UCS2 CMap (日文)
     */
    private fun createAdobeJapan1UCS2(): CMap {
        return CMap().apply {
            name = "Adobe-Japan1-UCS2"
            registry = "Adobe"
            ordering = "Japan1"
            supplement = 6
            wMode = 0
            
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
            
            // 添加基本映射
            for (cid in 1..94) {
                addUnicodeMapping(cid, (cid + 32).toChar().toString())
            }
            
            addCommonJapaneseMappings(this)
        }
    }
    
    /**
     * 创建 Adobe-Korea1-UCS2 CMap (韩文)
     */
    private fun createAdobeKorea1UCS2(): CMap {
        return CMap().apply {
            name = "Adobe-Korea1-UCS2"
            registry = "Adobe"
            ordering = "Korea1"
            supplement = 2
            wMode = 0
            
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
            
            // 添加基本映射
            for (cid in 1..94) {
                addUnicodeMapping(cid, (cid + 32).toChar().toString())
            }
            
            addCommonKoreanMappings(this)
        }
    }
    
    /**
     * 创建 UniGB-UCS2-H (简体中文 Unicode 到 CID，水平)
     */
    private fun createUniGBH(): CMap {
        return CMap().apply {
            name = "UniGB-UCS2-H"
            registry = "Adobe"
            ordering = "GB1"
            supplement = 5
            wMode = 0
            
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
            
            // Unicode 到 CID 的反向映射
            addUnicodeToGBMappings(this)
        }
    }
    
    /**
     * 创建 UniGB-UCS2-V (简体中文 Unicode 到 CID，垂直)
     */
    private fun createUniGBV(): CMap {
        val cmap = createUniGBH()
        cmap.name = "UniGB-UCS2-V"
        cmap.wMode = 1
        return cmap
    }
    
    /**
     * 创建 UniCNS-UCS2-H (繁体中文 Unicode 到 CID，水平)
     */
    private fun createUniCNSH(): CMap {
        return CMap().apply {
            name = "UniCNS-UCS2-H"
            registry = "Adobe"
            ordering = "CNS1"
            supplement = 6
            wMode = 0
            
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
            
            addUnicodeToCNSMappings(this)
        }
    }
    
    /**
     * 创建 UniCNS-UCS2-V
     */
    private fun createUniCNSV(): CMap {
        val cmap = createUniCNSH()
        cmap.name = "UniCNS-UCS2-V"
        cmap.wMode = 1
        return cmap
    }
    
    /**
     * 创建 UniJIS-UCS2-H (日文 Unicode 到 CID，水平)
     */
    private fun createUniJISH(): CMap {
        return CMap().apply {
            name = "UniJIS-UCS2-H"
            registry = "Adobe"
            ordering = "Japan1"
            supplement = 6
            wMode = 0
            
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
            
            addUnicodeToJISMappings(this)
        }
    }
    
    /**
     * 创建 UniJIS-UCS2-V
     */
    private fun createUniJISV(): CMap {
        val cmap = createUniJISH()
        cmap.name = "UniJIS-UCS2-V"
        cmap.wMode = 1
        return cmap
    }
    
    /**
     * 创建 UniKS-UCS2-H (韩文 Unicode 到 CID，水平)
     */
    private fun createUniKSH(): CMap {
        return CMap().apply {
            name = "UniKS-UCS2-H"
            registry = "Adobe"
            ordering = "Korea1"
            supplement = 2
            wMode = 0
            
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
            
            addUnicodeToKSMappings(this)
        }
    }
    
    /**
     * 创建 UniKS-UCS2-V
     */
    private fun createUniKSV(): CMap {
        val cmap = createUniKSH()
        cmap.name = "UniKS-UCS2-V"
        cmap.wMode = 1
        return cmap
    }
    
    /**
     * 创建 GBK 相关 CMap
     */
    private fun createGBKCMap(name: String): CMap {
        return CMap().apply {
            this.name = name
            registry = "Adobe"
            ordering = "GB1"
            supplement = 5
            wMode = if (name.endsWith("-V")) 1 else 0
            
            // GBK 使用 1-2 字节编码
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00),
                endBytes = byteArrayOf(0x80.toByte())
            ))
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x81.toByte(), 0x40),
                endBytes = byteArrayOf(0xFE.toByte(), 0xFE.toByte())
            ))
            
            addGBKMappings(this)
        }
    }
    
    /**
     * 创建 Big5 相关 CMap
     */
    private fun createBig5CMap(name: String): CMap {
        return CMap().apply {
            this.name = name
            registry = "Adobe"
            ordering = "CNS1"
            supplement = 6
            wMode = if (name.endsWith("-V")) 1 else 0
            
            // Big5 使用 1-2 字节编码
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00),
                endBytes = byteArrayOf(0x80.toByte())
            ))
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0xA1.toByte(), 0x40),
                endBytes = byteArrayOf(0xF9.toByte(), 0xFE.toByte())
            ))
            
            addBig5Mappings(this)
        }
    }
    
    /**
     * 创建 CNS 相关 CMap
     */
    private fun createCNSCMap(name: String): CMap {
        return CMap().apply {
            this.name = name
            registry = "Adobe"
            ordering = "CNS1"
            supplement = 6
            wMode = if (name.endsWith("-V")) 1 else 0
            
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00, 0x00),
                endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            ))
        }
    }
    
    /**
     * 创建日文 CMap
     */
    private fun createJapaneseCMap(name: String): CMap {
        return CMap().apply {
            this.name = name
            registry = "Adobe"
            ordering = "Japan1"
            supplement = 6
            wMode = if (name.endsWith("-V")) 1 else 0
            
            // Shift-JIS 编码
            if (name.contains("RKSJ")) {
                addCodespaceRange(CodespaceRange(
                    startBytes = byteArrayOf(0x00),
                    endBytes = byteArrayOf(0x80.toByte())
                ))
                addCodespaceRange(CodespaceRange(
                    startBytes = byteArrayOf(0xA0.toByte()),
                    endBytes = byteArrayOf(0xDF.toByte())
                ))
                addCodespaceRange(CodespaceRange(
                    startBytes = byteArrayOf(0x81.toByte(), 0x40),
                    endBytes = byteArrayOf(0x9F.toByte(), 0xFC.toByte())
                ))
                addCodespaceRange(CodespaceRange(
                    startBytes = byteArrayOf(0xE0.toByte(), 0x40),
                    endBytes = byteArrayOf(0xFC.toByte(), 0xFC.toByte())
                ))
            } else {
                // EUC-JP 或其他编码
                addCodespaceRange(CodespaceRange(
                    startBytes = byteArrayOf(0x00, 0x00),
                    endBytes = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
                ))
            }
            
            addJapaneseMappings(this)
        }
    }
    
    /**
     * 创建韩文 CMap
     */
    private fun createKoreanCMap(name: String): CMap {
        return CMap().apply {
            this.name = name
            registry = "Adobe"
            ordering = "Korea1"
            supplement = 2
            wMode = if (name.endsWith("-V")) 1 else 0
            
            // UHC (Unified Hangul Code) 或 EUC-KR 编码
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x00),
                endBytes = byteArrayOf(0x80.toByte())
            ))
            addCodespaceRange(CodespaceRange(
                startBytes = byteArrayOf(0x81.toByte(), 0x41),
                endBytes = byteArrayOf(0xFE.toByte(), 0xFE.toByte())
            ))
            
            addKoreanMappings(this)
        }
    }
    
    // ==================== 映射数据 ====================
    
    /**
     * 添加常用简体中文映射
     */
    private fun addCommonChineseSimplifiedMappings(cmap: CMap) {
        // 基本汉字区域 (U+4E00 - U+9FFF)
        // 这里只添加部分常用字作为示例
        // 完整的映射需要完整的 Adobe-GB1 CID 表
        
        // CID 到 Unicode 映射示例
        val commonMappings = mapOf(
            // 标点符号
            95 to "\u3000",   // 全角空格
            96 to "\u3001",   // 、
            97 to "\u3002",   // 。
            98 to "\uFF0C",   // ，
            99 to "\uFF0E",   // ．
            100 to "\uFF1A",  // ：
            101 to "\uFF1B",  // ；
            102 to "\uFF1F",  // ？
            103 to "\uFF01",  // ！
            
            // 常用汉字 (按 GB2312 顺序的部分常用字)
            814 to "\u4E00",  // 一
            815 to "\u4E01",  // 丁
            816 to "\u4E03",  // 七
            817 to "\u4E07",  // 万
            818 to "\u4E08",  // 丈
            819 to "\u4E09",  // 三
            820 to "\u4E0A",  // 上
            821 to "\u4E0B",  // 下
            822 to "\u4E0D",  // 不
            823 to "\u4E0E",  // 与
            
            // 更多常用字
            7716 to "\u5B8B",  // 宋 (Song)
            7717 to "\u4F53",  // 体
        )
        
        for ((cid, unicode) in commonMappings) {
            cmap.addUnicodeMapping(cid, unicode)
        }
    }
    
    /**
     * 添加常用繁体中文映射
     */
    private fun addCommonChineseTraditionalMappings(cmap: CMap) {
        val commonMappings = mapOf(
            95 to "\u3000",
            96 to "\u3001",
            97 to "\u3002",
            // ... 繁体中文特有字符
        )
        
        for ((cid, unicode) in commonMappings) {
            cmap.addUnicodeMapping(cid, unicode)
        }
    }
    
    /**
     * 添加常用日文映射
     */
    private fun addCommonJapaneseMappings(cmap: CMap) {
        val commonMappings = mapOf(
            // 平假名 (U+3040 - U+309F)
            842 to "\u3042",  // あ
            843 to "\u3044",  // い
            844 to "\u3046",  // う
            845 to "\u3048",  // え
            846 to "\u304A",  // お
            
            // 片假名 (U+30A0 - U+30FF)
            911 to "\u30A2",  // ア
            912 to "\u30A4",  // イ
            913 to "\u30A6",  // ウ
            914 to "\u30A8",  // エ
            915 to "\u30AA",  // オ
        )
        
        for ((cid, unicode) in commonMappings) {
            cmap.addUnicodeMapping(cid, unicode)
        }
    }
    
    /**
     * 添加常用韩文映射
     */
    private fun addCommonKoreanMappings(cmap: CMap) {
        // 韩文音节 (U+AC00 - U+D7A3)
        val commonMappings = mapOf(
            95 to "\u3000",
            // 韩文字母和音节
        )
        
        for ((cid, unicode) in commonMappings) {
            cmap.addUnicodeMapping(cid, unicode)
        }
    }
    
    /**
     * 添加 Unicode 到 GB1 CID 映射
     */
    private fun addUnicodeToGBMappings(cmap: CMap) {
        // Unicode code point 到 CID 的映射
        // 这里添加常用字符的反向映射
    }
    
    /**
     * 添加 Unicode 到 CNS1 CID 映射
     */
    private fun addUnicodeToCNSMappings(cmap: CMap) {
        // Unicode code point 到 CID 的映射
    }
    
    /**
     * 添加 Unicode 到 Japan1 CID 映射
     */
    private fun addUnicodeToJISMappings(cmap: CMap) {
        // Unicode code point 到 CID 的映射
    }
    
    /**
     * 添加 Unicode 到 Korea1 CID 映射
     */
    private fun addUnicodeToKSMappings(cmap: CMap) {
        // Unicode code point 到 CID 的映射
    }
    
    /**
     * 添加 GBK 编码映射
     */
    private fun addGBKMappings(cmap: CMap) {
        // GBK 编码到 Unicode 的映射
        // GBK 是 GB2312 的扩展，覆盖了大部分简体中文字符
        
        // 单字节区域 (ASCII)
        for (i in 0x00..0x7F) {
            cmap.addUnicodeMapping(i, i.toChar().toString())
        }
        
        // 双字节区域需要完整的 GBK 映射表
        // 这里只提供框架，实际使用时应加载完整数据
    }
    
    /**
     * 添加 Big5 编码映射
     */
    private fun addBig5Mappings(cmap: CMap) {
        // Big5 编码到 Unicode 的映射
        
        // 单字节区域 (ASCII)
        for (i in 0x00..0x7F) {
            cmap.addUnicodeMapping(i, i.toChar().toString())
        }
        
        // 双字节区域需要完整的 Big5 映射表
    }
    
    /**
     * 添加日文编码映射
     */
    private fun addJapaneseMappings(cmap: CMap) {
        // Shift-JIS/EUC-JP 到 Unicode 的映射
    }
    
    /**
     * 添加韩文编码映射
     */
    private fun addKoreanMappings(cmap: CMap) {
        // UHC/EUC-KR 到 Unicode 的映射
    }
}

/**
 * CID 字符集信息
 * 用于识别 CJK 字体的字符集
 */
object CIDCharsets {
    
    /**
     * 根据 Registry 和 Ordering 获取字符集类型
     */
    fun getCharsetType(registry: String, ordering: String): CharsetType {
        return when {
            ordering == "Identity" -> CharsetType.IDENTITY
            registry == "Adobe" && ordering == "GB1" -> CharsetType.SIMPLIFIED_CHINESE
            registry == "Adobe" && ordering == "CNS1" -> CharsetType.TRADITIONAL_CHINESE
            registry == "Adobe" && ordering == "Japan1" -> CharsetType.JAPANESE
            registry == "Adobe" && ordering == "Korea1" -> CharsetType.KOREAN
            else -> CharsetType.UNKNOWN
        }
    }
    
    /**
     * 获取字符集对应的 UCS2 CMap 名称
     */
    fun getUCS2CMapName(registry: String, ordering: String): String? {
        return when {
            ordering == "Identity" -> null
            registry == "Adobe" && ordering == "GB1" -> "Adobe-GB1-UCS2"
            registry == "Adobe" && ordering == "CNS1" -> "Adobe-CNS1-UCS2"
            registry == "Adobe" && ordering == "Japan1" -> "Adobe-Japan1-UCS2"
            registry == "Adobe" && ordering == "Korea1" -> "Adobe-Korea1-UCS2"
            else -> null
        }
    }
}

/**
 * 字符集类型
 */
enum class CharsetType {
    IDENTITY,           // Identity 映射
    SIMPLIFIED_CHINESE, // 简体中文 (GB)
    TRADITIONAL_CHINESE,// 繁体中文 (CNS/Big5)
    JAPANESE,           // 日文
    KOREAN,             // 韩文
    UNKNOWN             // 未知
}
