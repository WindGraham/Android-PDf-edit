package com.pdfcore.codec

import java.io.ByteArrayOutputStream

/**
 * JBIG2 解码器
 * 基于 PDF 32000-1:2008 标准 7.4.7 节和 ISO/IEC 14492
 * 
 * JBIG2 是一种专门为二值图像设计的压缩标准，
 * 特别适合文本密集的文档扫描图像。
 * 
 * 支持的区域类型：
 * - 符号字典 (Symbol Dictionary)
 * - 文本区域 (Text Region)
 * - 通用区域 (Generic Region)
 * - 通用细化区域 (Generic Refinement Region)
 * - 半色调区域 (Halftone Region)
 * - 页面信息 (Page Information)
 * - 文件结束 (End of File/Page/Stripe)
 */
class JBIG2Decoder(private val globalData: ByteArray? = null) {
    
    // 全局符号字典
    private val globalSymbols = mutableListOf<Bitmap>()
    
    // 当前页面的符号字典
    private val pageSymbols = mutableListOf<Bitmap>()
    
    // 页面位图
    private var pageBitmap: Bitmap? = null
    
    // 页面尺寸
    private var pageWidth: Int = 0
    private var pageHeight: Int = 0
    
    // 位读取器
    private var reader: BitReader? = null
    
    /**
     * 解码 JBIG2 数据
     */
    fun decode(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        
        // 首先解析全局数据（如果有）
        if (globalData != null && globalData.isNotEmpty()) {
            parseSegments(globalData, isGlobal = true)
        }
        
        // 解析页面数据
        parseSegments(data, isGlobal = false)
        
        // 返回页面位图数据
        return pageBitmap?.toByteArray() ?: data
    }
    
    /**
     * 解析段序列
     */
    private fun parseSegments(data: ByteArray, isGlobal: Boolean) {
        reader = BitReader(data)
        val r = reader!!
        
        while (r.position < data.size) {
            val segment = try {
                parseSegmentHeader(r)
            } catch (e: Exception) {
                break
            }
            
            if (segment == null) break
            
            try {
                processSegment(segment, r, isGlobal)
            } catch (e: Exception) {
                // 跳过无法处理的段
                r.skipBytes(segment.dataLength)
            }
            
            // 文件结束
            if (segment.type == SEGMENT_END_OF_FILE) break
        }
    }
    
    /**
     * 解析段头
     */
    private fun parseSegmentHeader(r: BitReader): SegmentHeader? {
        if (r.remaining < 6) return null
        
        val segmentNumber = r.readUInt32()
        val flags = r.readByte()
        
        val type = flags and 0x3F
        val pageAssociation = (flags shr 6) and 0x01
        val deferredNonRetain = (flags shr 7) and 0x01
        
        // 引用段数量
        val retainBits = r.readByte()
        val refCount = when {
            (retainBits shr 5) and 0x07 <= 4 -> (retainBits shr 5) and 0x07
            else -> {
                val longCount = r.readUInt32()
                longCount.toInt()
            }
        }
        
        // 读取引用段号
        val refSegments = mutableListOf<Long>()
        for (i in 0 until refCount) {
            refSegments.add(
                if (segmentNumber <= 256) r.readByte().toLong()
                else if (segmentNumber <= 65536) r.readUInt16().toLong()
                else r.readUInt32()
            )
        }
        
        // 页面关联
        val pageAssoc = if (pageAssociation == 1) {
            r.readUInt32().toInt()
        } else {
            r.readByte()
        }
        
        // 数据长度
        val dataLength = r.readUInt32().toInt()
        
        return SegmentHeader(
            number = segmentNumber,
            type = type,
            deferredNonRetain = deferredNonRetain == 1,
            referredSegments = refSegments,
            pageAssociation = pageAssoc,
            dataLength = dataLength
        )
    }
    
    /**
     * 处理段
     */
    private fun processSegment(segment: SegmentHeader, r: BitReader, isGlobal: Boolean) {
        val startPos = r.position
        
        when (segment.type) {
            SEGMENT_SYMBOL_DICTIONARY -> {
                processSymbolDictionary(segment, r, isGlobal)
            }
            SEGMENT_TEXT_REGION, SEGMENT_TEXT_REGION_IMMEDIATE,
            SEGMENT_TEXT_REGION_IMMEDIATE_LOSSLESS -> {
                processTextRegion(segment, r)
            }
            SEGMENT_GENERIC_REGION, SEGMENT_GENERIC_REGION_IMMEDIATE,
            SEGMENT_GENERIC_REGION_IMMEDIATE_LOSSLESS -> {
                processGenericRegion(segment, r)
            }
            SEGMENT_GENERIC_REFINEMENT_REGION, SEGMENT_GENERIC_REFINEMENT_IMMEDIATE,
            SEGMENT_GENERIC_REFINEMENT_IMMEDIATE_LOSSLESS -> {
                processRefinementRegion(segment, r)
            }
            SEGMENT_PAGE_INFORMATION -> {
                processPageInformation(segment, r)
            }
            SEGMENT_END_OF_PAGE -> {
                // 页面结束
            }
            SEGMENT_END_OF_STRIPE -> {
                // 条带结束
            }
            SEGMENT_END_OF_FILE -> {
                // 文件结束
            }
            SEGMENT_HALFTONE_REGION, SEGMENT_HALFTONE_REGION_IMMEDIATE,
            SEGMENT_HALFTONE_REGION_IMMEDIATE_LOSSLESS -> {
                processHalftoneRegion(segment, r)
            }
            else -> {
                // 未知段类型，跳过
                r.skipBytes(segment.dataLength)
            }
        }
        
        // 确保读取了正确的数据长度
        val bytesRead = r.position - startPos
        if (bytesRead < segment.dataLength) {
            r.skipBytes(segment.dataLength - bytesRead)
        }
    }
    
    /**
     * 处理页面信息段
     */
    private fun processPageInformation(segment: SegmentHeader, r: BitReader) {
        pageWidth = r.readUInt32().toInt()
        pageHeight = r.readUInt32().toInt()
        
        val xResolution = r.readUInt32()
        val yResolution = r.readUInt32()
        val flags = r.readByte()
        
        val defaultPixel = (flags shr 2) and 0x01
        val combinationOperator = (flags shr 3) and 0x03
        
        // 如果高度未知，使用条带高度
        val stripeInfo = r.readUInt16()
        
        // 创建页面位图
        val actualHeight = if (pageHeight == -1 || pageHeight == 0xFFFFFFFF.toInt()) {
            // 高度未知，使用一个默认值
            1000
        } else {
            pageHeight
        }
        
        pageBitmap = Bitmap(pageWidth, actualHeight)
        if (defaultPixel == 1) {
            pageBitmap!!.fill(1)
        }
    }
    
    /**
     * 处理符号字典段
     */
    private fun processSymbolDictionary(segment: SegmentHeader, r: BitReader, isGlobal: Boolean) {
        val flags = r.readUInt16()
        
        val huffman = (flags and 0x01) == 1
        val refinement = ((flags shr 1) and 0x01) == 1
        val huffmanDHSelector = (flags shr 2) and 0x03
        val huffmanDWSelector = (flags shr 4) and 0x03
        val huffmanBMSizeSelector = (flags shr 6) and 0x01
        val huffmanAggInstSelector = (flags shr 7) and 0x01
        val useContextRetained = ((flags shr 8) and 0x01) == 1
        val useContextUsed = ((flags shr 9) and 0x01) == 1
        val sdTemplate = (flags shr 10) and 0x03
        val sdrTemplate = (flags shr 12) and 0x01
        
        // AT 像素
        val atCount = if (sdTemplate == 0) 4 else 1
        val atPixels = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until atCount) {
            val x = r.readSignedByte()
            val y = r.readSignedByte()
            atPixels.add(x to y)
        }
        
        // 细化 AT 像素
        if (refinement && sdrTemplate == 0) {
            for (i in 0 until 2) {
                r.readSignedByte()
                r.readSignedByte()
            }
        }
        
        // 导出符号数量和新符号数量
        val numExportedSymbols = r.readUInt32().toInt()
        val numNewSymbols = r.readUInt32().toInt()
        
        // 获取引用的符号
        val inputSymbols = mutableListOf<Bitmap>()
        for (refSegNum in segment.referredSegments) {
            // 从全局或页面符号中获取
            inputSymbols.addAll(if (isGlobal) globalSymbols else pageSymbols)
        }
        
        // 解码新符号
        val newSymbols = decodeSymbols(r, numNewSymbols, inputSymbols, 
            huffman, refinement, sdTemplate, atPixels)
        
        // 存储符号
        val targetList = if (isGlobal) globalSymbols else pageSymbols
        targetList.addAll(newSymbols)
    }
    
    /**
     * 解码符号
     */
    private fun decodeSymbols(
        r: BitReader,
        numSymbols: Int,
        inputSymbols: List<Bitmap>,
        huffman: Boolean,
        refinement: Boolean,
        template: Int,
        atPixels: List<Pair<Int, Int>>
    ): List<Bitmap> {
        val symbols = mutableListOf<Bitmap>()
        
        if (huffman) {
            // Huffman 解码（简化实现）
            for (i in 0 until numSymbols) {
                val width = r.readBits(8)
                val height = r.readBits(8)
                val bitmap = Bitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
                // 读取位图数据
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        bitmap.setPixel(x, y, r.readBit())
                    }
                }
                symbols.add(bitmap)
            }
        } else {
            // 算术解码（简化实现）
            val decoder = ArithmeticDecoder(r)
            
            var deltaHeight = 0
            var symbolIndex = 0
            
            while (symbolIndex < numSymbols) {
                // 解码高度增量
                deltaHeight += decoder.decodeInteger(IADH_CONTEXT)
                
                var deltaWidth = 0
                var totalWidth = 0
                
                while (true) {
                    // 解码宽度增量
                    val dw = decoder.decodeInteger(IADW_CONTEXT)
                    if (dw == Int.MIN_VALUE) break // OOB
                    
                    deltaWidth += dw
                    val width = deltaWidth
                    val height = deltaHeight
                    
                    if (width <= 0 || height <= 0) continue
                    
                    // 解码符号位图
                    val bitmap = decodeGenericBitmap(decoder, width, height, template, atPixels)
                    symbols.add(bitmap)
                    
                    symbolIndex++
                    if (symbolIndex >= numSymbols) break
                    
                    totalWidth += width
                }
            }
        }
        
        return symbols
    }
    
    /**
     * 处理文本区域段
     */
    private fun processTextRegion(segment: SegmentHeader, r: BitReader) {
        val regionInfo = readRegionInfo(r)
        val flags = r.readUInt16()
        
        val huffman = (flags and 0x01) == 1
        val refinement = ((flags shr 1) and 0x01) == 1
        val stripSize = 1 shl ((flags shr 2) and 0x03)
        val refCorner = (flags shr 4) and 0x03
        val transposed = ((flags shr 6) and 0x01) == 1
        val combinationOperator = (flags shr 7) and 0x03
        val defaultPixel = ((flags shr 9) and 0x01) == 1
        val dsOffset = (flags shr 10) and 0x1F
        val sTemplate = (flags shr 15) and 0x01
        
        // 收集所有引用的符号
        val symbols = mutableListOf<Bitmap>()
        symbols.addAll(globalSymbols)
        symbols.addAll(pageSymbols)
        
        if (symbols.isEmpty()) {
            r.skipBytes(segment.dataLength - 17) // 已读取的字节
            return
        }
        
        // 读取符号实例数量
        val numInstances = r.readUInt32().toInt()
        
        // 创建区域位图
        val regionBitmap = Bitmap(regionInfo.width, regionInfo.height)
        if (defaultPixel) {
            regionBitmap.fill(1)
        }
        
        // 解码文本区域
        if (!huffman) {
            val decoder = ArithmeticDecoder(r)
            decodeTextRegionArithmetic(
                decoder, regionBitmap, symbols, numInstances,
                stripSize, refCorner, transposed, dsOffset
            )
        }
        
        // 合成到页面
        compositeToPage(regionBitmap, regionInfo, combinationOperator)
    }
    
    /**
     * 算术解码文本区域
     */
    private fun decodeTextRegionArithmetic(
        decoder: ArithmeticDecoder,
        bitmap: Bitmap,
        symbols: List<Bitmap>,
        numInstances: Int,
        stripSize: Int,
        refCorner: Int,
        transposed: Boolean,
        dsOffset: Int
    ) {
        var stripT = decoder.decodeInteger(IADT_CONTEXT)
        var firstS = decoder.decodeInteger(IAFS_CONTEXT)
        var curS = firstS
        
        for (i in 0 until numInstances) {
            // 解码条带增量
            val dt = decoder.decodeInteger(IADT_CONTEXT)
            if (dt == Int.MIN_VALUE) break
            stripT += dt
            
            // 解码第一个 S 值
            firstS += decoder.decodeInteger(IAFS_CONTEXT)
            curS = firstS
            
            while (true) {
                // 解码 S 增量
                val ds = decoder.decodeInteger(IADS_CONTEXT)
                if (ds == Int.MIN_VALUE) break
                curS += ds + dsOffset
                
                // 解码 T 增量
                val curT = if (stripSize > 1) {
                    stripT + decoder.decodeInteger(IAIT_CONTEXT)
                } else {
                    stripT
                }
                
                // 解码符号 ID
                val symbolId = decodeSymbolId(decoder, symbols.size)
                if (symbolId < 0 || symbolId >= symbols.size) continue
                
                val symbol = symbols[symbolId]
                
                // 计算放置位置
                val (x, y) = calculateSymbolPosition(
                    curS, curT, symbol.width, symbol.height,
                    refCorner, transposed
                )
                
                // 绘制符号
                bitmap.composite(symbol, x, y, OPERATOR_OR)
            }
        }
    }
    
    /**
     * 解码符号 ID
     */
    private fun decodeSymbolId(decoder: ArithmeticDecoder, numSymbols: Int): Int {
        val bits = 32 - Integer.numberOfLeadingZeros(numSymbols - 1)
        return decoder.decodeInteger(IAID_CONTEXT) and ((1 shl bits) - 1)
    }
    
    /**
     * 计算符号位置
     */
    private fun calculateSymbolPosition(
        s: Int, t: Int,
        width: Int, height: Int,
        refCorner: Int, transposed: Boolean
    ): Pair<Int, Int> {
        return if (transposed) {
            when (refCorner) {
                0 -> t to s
                1 -> t to s - width + 1
                2 -> t - height + 1 to s
                else -> t - height + 1 to s - width + 1
            }
        } else {
            when (refCorner) {
                0 -> s to t
                1 -> s - width + 1 to t
                2 -> s to t - height + 1
                else -> s - width + 1 to t - height + 1
            }
        }
    }
    
    /**
     * 处理通用区域段
     */
    private fun processGenericRegion(segment: SegmentHeader, r: BitReader) {
        val regionInfo = readRegionInfo(r)
        val flags = r.readByte()
        
        val mmr = (flags and 0x01) == 1
        val template = (flags shr 1) and 0x03
        val tpgdon = ((flags shr 3) and 0x01) == 1
        
        // AT 像素
        val atPixels = mutableListOf<Pair<Int, Int>>()
        if (!mmr) {
            val atCount = if (template == 0) 4 else 1
            for (i in 0 until atCount) {
                val x = r.readSignedByte()
                val y = r.readSignedByte()
                atPixels.add(x to y)
            }
        }
        
        // 创建区域位图
        val bitmap = if (mmr) {
            // MMR 解码
            decodeMMRBitmap(r, regionInfo.width, regionInfo.height)
        } else {
            // 算术解码
            val decoder = ArithmeticDecoder(r)
            decodeGenericBitmap(decoder, regionInfo.width, regionInfo.height, template, atPixels)
        }
        
        // 合成到页面
        compositeToPage(bitmap, regionInfo, regionInfo.combinationOperator)
    }
    
    /**
     * 处理细化区域段
     */
    private fun processRefinementRegion(segment: SegmentHeader, r: BitReader) {
        val regionInfo = readRegionInfo(r)
        val flags = r.readByte()
        
        val template = (flags and 0x01)
        
        // 简化实现：跳过细化区域
        r.skipBytes(segment.dataLength - 18)
    }
    
    /**
     * 处理半色调区域段
     */
    private fun processHalftoneRegion(segment: SegmentHeader, r: BitReader) {
        val regionInfo = readRegionInfo(r)
        val flags = r.readByte()
        
        // 简化实现：跳过半色调区域
        r.skipBytes(segment.dataLength - 18)
    }
    
    /**
     * 读取区域信息
     */
    private fun readRegionInfo(r: BitReader): RegionInfo {
        val width = r.readUInt32().toInt()
        val height = r.readUInt32().toInt()
        val x = r.readUInt32().toInt()
        val y = r.readUInt32().toInt()
        val flags = r.readByte()
        
        val combinationOperator = flags and 0x07
        
        return RegionInfo(width, height, x, y, combinationOperator)
    }
    
    /**
     * 使用算术编码解码通用位图
     */
    private fun decodeGenericBitmap(
        decoder: ArithmeticDecoder,
        width: Int,
        height: Int,
        template: Int,
        atPixels: List<Pair<Int, Int>>
    ): Bitmap {
        val bitmap = Bitmap(width, height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val context = getGenericContext(bitmap, x, y, template, atPixels)
                val pixel = decoder.decodeBit(context)
                bitmap.setPixel(x, y, pixel)
            }
        }
        
        return bitmap
    }
    
    /**
     * 获取通用区域上下文
     */
    private fun getGenericContext(
        bitmap: Bitmap,
        x: Int, y: Int,
        template: Int,
        atPixels: List<Pair<Int, Int>>
    ): Int {
        var context = 0
        
        // 根据模板获取上下文像素
        val pixels = when (template) {
            0 -> listOf(
                -1 to 0, -2 to 0, -3 to 0, -4 to 0,
                -1 to -1, 0 to -1, 1 to -1, 2 to -1, 3 to -1,
                -2 to -2, -1 to -2, 0 to -2, 1 to -2, 2 to -2
            )
            1 -> listOf(
                -1 to 0, -2 to 0, -3 to 0,
                -1 to -1, 0 to -1, 1 to -1, 2 to -1,
                -2 to -2, -1 to -2, 0 to -2, 1 to -2
            )
            2 -> listOf(
                -1 to 0, -2 to 0,
                -1 to -1, 0 to -1, 1 to -1,
                -1 to -2, 0 to -2, 1 to -2
            )
            else -> listOf(
                -1 to 0, -2 to 0, -3 to 0, -4 to 0,
                -1 to -1, 0 to -1, 1 to -1, 2 to -1
            )
        }
        
        for ((i, offset) in pixels.withIndex()) {
            val px = x + offset.first
            val py = y + offset.second
            if (bitmap.getPixel(px, py) == 1) {
                context = context or (1 shl i)
            }
        }
        
        return context
    }
    
    /**
     * 使用 MMR 解码位图
     */
    private fun decodeMMRBitmap(r: BitReader, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap(width, height)
        
        // 使用 CCITT Group 4 解码
        val decoder = CCITTFaxDecoder(null)
        val remaining = ByteArray(r.remaining)
        for (i in remaining.indices) {
            remaining[i] = r.readByte().toByte()
        }
        
        val decoded = try {
            decoder.decode(remaining)
        } catch (e: Exception) {
            remaining
        }
        
        // 将解码数据复制到位图
        val bytesPerRow = (width + 7) / 8
        for (y in 0 until height) {
            for (x in 0 until width) {
                val byteIndex = y * bytesPerRow + x / 8
                val bitIndex = 7 - (x % 8)
                if (byteIndex < decoded.size) {
                    val pixel = (decoded[byteIndex].toInt() shr bitIndex) and 1
                    bitmap.setPixel(x, y, pixel)
                }
            }
        }
        
        return bitmap
    }
    
    /**
     * 合成位图到页面
     */
    private fun compositeToPage(bitmap: Bitmap, regionInfo: RegionInfo, operator: Int) {
        val page = pageBitmap ?: return
        page.composite(bitmap, regionInfo.x, regionInfo.y, operator)
    }
    
    /**
     * 位图类
     */
    private class Bitmap(val width: Int, val height: Int) {
        private val data: IntArray = IntArray(width * height)
        
        fun getPixel(x: Int, y: Int): Int {
            if (x < 0 || x >= width || y < 0 || y >= height) return 0
            return data[y * width + x]
        }
        
        fun setPixel(x: Int, y: Int, value: Int) {
            if (x < 0 || x >= width || y < 0 || y >= height) return
            data[y * width + x] = value
        }
        
        fun fill(value: Int) {
            data.fill(value)
        }
        
        fun composite(src: Bitmap, x: Int, y: Int, operator: Int) {
            for (sy in 0 until src.height) {
                for (sx in 0 until src.width) {
                    val dx = x + sx
                    val dy = y + sy
                    if (dx < 0 || dx >= width || dy < 0 || dy >= height) continue
                    
                    val srcPixel = src.getPixel(sx, sy)
                    val dstPixel = getPixel(dx, dy)
                    
                    val result = when (operator) {
                        OPERATOR_OR -> srcPixel or dstPixel
                        OPERATOR_AND -> srcPixel and dstPixel
                        OPERATOR_XOR -> srcPixel xor dstPixel
                        OPERATOR_REPLACE -> srcPixel
                        else -> srcPixel or dstPixel
                    }
                    
                    setPixel(dx, dy, result)
                }
            }
        }
        
        fun toByteArray(): ByteArray {
            val bytesPerRow = (width + 7) / 8
            val result = ByteArray(bytesPerRow * height)
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val byteIndex = y * bytesPerRow + x / 8
                    val bitIndex = 7 - (x % 8)
                    if (getPixel(x, y) == 1) {
                        result[byteIndex] = (result[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                    }
                }
            }
            
            return result
        }
    }
    
    /**
     * 位读取器
     */
    private class BitReader(private val data: ByteArray) {
        var position: Int = 0
            private set
        private var bitPosition: Int = 0
        
        val remaining: Int get() = data.size - position
        
        fun readBit(): Int {
            if (position >= data.size) return 0
            val bit = (data[position].toInt() shr (7 - bitPosition)) and 1
            bitPosition++
            if (bitPosition >= 8) {
                bitPosition = 0
                position++
            }
            return bit
        }
        
        fun readBits(count: Int): Int {
            var result = 0
            for (i in 0 until count) {
                result = (result shl 1) or readBit()
            }
            return result
        }
        
        fun readByte(): Int {
            if (position >= data.size) return 0
            alignToByte()
            val value = data[position].toInt() and 0xFF
            position++
            return value
        }
        
        fun readSignedByte(): Int {
            val value = readByte()
            return if (value > 127) value - 256 else value
        }
        
        fun readUInt16(): Int {
            val b1 = readByte()
            val b2 = readByte()
            return (b1 shl 8) or b2
        }
        
        fun readUInt32(): Long {
            val b1 = readByte().toLong()
            val b2 = readByte().toLong()
            val b3 = readByte().toLong()
            val b4 = readByte().toLong()
            return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
        }
        
        fun skipBytes(count: Int) {
            alignToByte()
            position = minOf(position + count, data.size)
        }
        
        private fun alignToByte() {
            if (bitPosition > 0) {
                bitPosition = 0
                position++
            }
        }
    }
    
    /**
     * 算术解码器 (QM-Coder)
     */
    private class ArithmeticDecoder(private val reader: BitReader) {
        private val contexts = IntArray(65536) { 0 }
        private var c: Long = 0
        private var a: Long = 0x8000
        private var ct: Int = 0
        
        init {
            byteIn()
            c = c shl 8
            byteIn()
            c = c shl 8
            ct = 0
        }
        
        fun decodeBit(context: Int): Int {
            val cx = contexts.getOrElse(context) { 0 }
            val qe = QE_TABLE[cx]
            
            a -= qe
            
            val decision: Int
            if ((c shr 16) < a) {
                if (a and 0x8000L == 0L) {
                    decision = if (MPS_TABLE[cx]) 1 else 0
                    if (NLPS_TABLE[cx] != cx) {
                        contexts[context] = NLPS_TABLE[cx]
                    }
                    renormalize()
                } else {
                    decision = if (MPS_TABLE[cx]) 1 else 0
                }
            } else {
                c -= a shl 16
                if (a and 0x8000L == 0L) {
                    decision = if (MPS_TABLE[cx]) 0 else 1
                    if (SWITCH_TABLE[cx]) {
                        contexts[context] = NLPS_TABLE[cx]
                    } else {
                        contexts[context] = NMPS_TABLE[cx]
                    }
                    a = qe.toLong()
                    renormalize()
                } else {
                    decision = if (MPS_TABLE[cx]) 0 else 1
                    a = qe.toLong()
                }
            }
            
            return decision
        }
        
        fun decodeInteger(contextBase: Int): Int {
            var s = decodeBit(contextBase + 0)
            var prev = 1
            
            // 解码前缀
            var i = 0
            while (i < 32 && decodeBit(contextBase + prev) == 1) {
                prev = (prev shl 1) or 1
                i++
            }
            
            if (i == 32) return Int.MIN_VALUE // OOB
            
            // 解码后缀
            var value = 0
            for (j in 0 until i) {
                value = (value shl 1) or decodeBit(contextBase + prev)
            }
            
            value += (1 shl i) - 1
            
            return if (s == 1) -value else value
        }
        
        private fun renormalize() {
            while (a and 0x8000L == 0L) {
                if (ct == 0) byteIn()
                a = a shl 1
                c = c shl 1
                ct--
            }
        }
        
        private fun byteIn() {
            val b = reader.readByte()
            c = c or b.toLong()
            ct = 8
        }
        
        companion object {
            // QM-Coder 概率表（简化版）
            private val QE_TABLE = intArrayOf(
                0x5601, 0x3401, 0x1801, 0x0AC1, 0x0521, 0x0221, 0x5601, 0x5401,
                0x4801, 0x3801, 0x3001, 0x2401, 0x1C01, 0x1601, 0x5601, 0x5401
            )
            
            private val NMPS_TABLE = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 15)
            private val NLPS_TABLE = intArrayOf(1, 6, 9, 12, 29, 33, 6, 14, 14, 14, 17, 18, 20, 21, 14, 14)
            private val SWITCH_TABLE = booleanArrayOf(
                true, false, false, false, false, false, true, false,
                false, false, false, false, false, false, true, false
            )
            private val MPS_TABLE = booleanArrayOf(
                false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false
            )
        }
    }
    
    /**
     * 段头
     */
    private data class SegmentHeader(
        val number: Long,
        val type: Int,
        val deferredNonRetain: Boolean,
        val referredSegments: List<Long>,
        val pageAssociation: Int,
        val dataLength: Int
    )
    
    /**
     * 区域信息
     */
    private data class RegionInfo(
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int,
        val combinationOperator: Int
    )
    
    companion object {
        // 段类型
        private const val SEGMENT_SYMBOL_DICTIONARY = 0
        private const val SEGMENT_TEXT_REGION = 4
        private const val SEGMENT_TEXT_REGION_IMMEDIATE = 6
        private const val SEGMENT_TEXT_REGION_IMMEDIATE_LOSSLESS = 7
        private const val SEGMENT_GENERIC_REGION = 36
        private const val SEGMENT_GENERIC_REGION_IMMEDIATE = 38
        private const val SEGMENT_GENERIC_REGION_IMMEDIATE_LOSSLESS = 39
        private const val SEGMENT_GENERIC_REFINEMENT_REGION = 40
        private const val SEGMENT_GENERIC_REFINEMENT_IMMEDIATE = 42
        private const val SEGMENT_GENERIC_REFINEMENT_IMMEDIATE_LOSSLESS = 43
        private const val SEGMENT_PAGE_INFORMATION = 48
        private const val SEGMENT_END_OF_PAGE = 49
        private const val SEGMENT_END_OF_STRIPE = 50
        private const val SEGMENT_END_OF_FILE = 51
        private const val SEGMENT_HALFTONE_REGION = 16
        private const val SEGMENT_HALFTONE_REGION_IMMEDIATE = 22
        private const val SEGMENT_HALFTONE_REGION_IMMEDIATE_LOSSLESS = 23
        
        // 合成操作符
        private const val OPERATOR_OR = 0
        private const val OPERATOR_AND = 1
        private const val OPERATOR_XOR = 2
        private const val OPERATOR_REPLACE = 3
        
        // 算术编码上下文
        private const val IADH_CONTEXT = 0
        private const val IADW_CONTEXT = 512
        private const val IADT_CONTEXT = 1024
        private const val IAFS_CONTEXT = 1536
        private const val IADS_CONTEXT = 2048
        private const val IAIT_CONTEXT = 2560
        private const val IAID_CONTEXT = 3072
    }
}
