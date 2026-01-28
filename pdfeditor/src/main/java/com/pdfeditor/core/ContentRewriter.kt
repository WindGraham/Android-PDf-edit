package com.pdfeditor.core

import com.pdfcore.content.ContentInstruction
import com.pdfcore.content.ContentParser
import com.pdfcore.model.*
import com.pdfcore.parser.StreamFilters

/**
 * 内容流重写器
 * 
 * 负责修改 PDF 内容流并重新序列化
 */
class ContentRewriter(private val document: PdfDocument) {
    
    private val contentParser = ContentParser()
    
    /**
     * 重写页面的内容流
     */
    fun rewritePageContent(
        page: PdfDictionary,
        modifier: (List<ContentInstruction>) -> List<ContentInstruction>
    ): Boolean {
        val contents = document.getPageContents(page)
        if (contents.isEmpty()) return false
        
        // 合并所有内容流的指令
        val allInstructions = mutableListOf<ContentInstruction>()
        for (stream in contents) {
            allInstructions.addAll(contentParser.parse(stream))
        }
        
        // 应用修改
        val modifiedInstructions = modifier(allInstructions)
        
        // 序列化为新的内容
        val newData = contentParser.serialize(modifiedInstructions)
        
        // 更新第一个内容流，删除其他的
        val firstStream = contents.first()
        firstStream.rawData = newData
        firstStream.dict["Length"] = PdfNumber(newData.size)
        firstStream.dict.remove("Filter") // 移除压缩
        firstStream.dict.remove("DecodeParms")
        firstStream.clearDecodedCache()
        
        // 如果有多个内容流，合并为一个
        if (contents.size > 1) {
            // 找到引用第一个流的对象并更新 Contents
            val firstStreamRef = findStreamRef(firstStream)
            if (firstStreamRef != null) {
                page["Contents"] = firstStreamRef
            }
        }
        
        document.markModified()
        return true
    }
    
    /**
     * 查找流的间接引用
     */
    private fun findStreamRef(stream: PdfStream): PdfIndirectRef? {
        for ((key, obj) in document.objects) {
            if (obj.obj === stream) {
                return PdfIndirectRef.fromKey(key)
            }
        }
        return null
    }
    
    /**
     * 在指定位置插入指令
     */
    fun insertInstructions(
        page: PdfDictionary,
        instructions: List<ContentInstruction>,
        position: InsertPosition = InsertPosition.END
    ): Boolean {
        return rewritePageContent(page) { existing ->
            when (position) {
                InsertPosition.START -> instructions + existing
                InsertPosition.END -> existing + instructions
                is InsertPosition.After -> {
                    val result = mutableListOf<ContentInstruction>()
                    for (inst in existing) {
                        result.add(inst)
                        if (inst.operator == position.operator) {
                            result.addAll(instructions)
                        }
                    }
                    result
                }
                is InsertPosition.Before -> {
                    val result = mutableListOf<ContentInstruction>()
                    for (inst in existing) {
                        if (inst.operator == position.operator) {
                            result.addAll(instructions)
                        }
                        result.add(inst)
                    }
                    result
                }
                is InsertPosition.AtIndex -> {
                    val result = existing.toMutableList()
                    val idx = position.index.coerceIn(0, result.size)
                    result.addAll(idx, instructions)
                    result
                }
            }
        }
    }
    
    /**
     * 删除指定条件的指令
     */
    fun removeInstructions(
        page: PdfDictionary,
        predicate: (ContentInstruction) -> Boolean
    ): Int {
        var removed = 0
        rewritePageContent(page) { existing ->
            val result = mutableListOf<ContentInstruction>()
            for (inst in existing) {
                if (predicate(inst)) {
                    removed++
                } else {
                    result.add(inst)
                }
            }
            result
        }
        return removed
    }
    
    /**
     * 替换指定条件的指令
     */
    fun replaceInstructions(
        page: PdfDictionary,
        predicate: (ContentInstruction) -> Boolean,
        replacement: (ContentInstruction) -> ContentInstruction
    ): Int {
        var replaced = 0
        rewritePageContent(page) { existing ->
            existing.map { inst ->
                if (predicate(inst)) {
                    replaced++
                    replacement(inst)
                } else {
                    inst
                }
            }
        }
        return replaced
    }
    
    /**
     * 在文本对象中替换文本
     */
    fun replaceTextInTextObject(
        page: PdfDictionary,
        textObjectIndex: Int,
        newText: String
    ): Boolean {
        var currentTextObject = 0
        var found = false
        
        rewritePageContent(page) { existing ->
            val result = mutableListOf<ContentInstruction>()
            var inTextObject = false
            
            for (inst in existing) {
                when (inst.operator) {
                    "BT" -> {
                        inTextObject = true
                        if (currentTextObject == textObjectIndex) {
                            found = true
                        }
                        result.add(inst)
                    }
                    "ET" -> {
                        inTextObject = false
                        currentTextObject++
                        result.add(inst)
                    }
                    "Tj" -> {
                        if (found && inTextObject) {
                            // 替换文本
                            result.add(ContentInstruction("Tj", listOf(PdfString(newText))))
                            found = false // 只替换一次
                        } else {
                            result.add(inst)
                        }
                    }
                    else -> result.add(inst)
                }
            }
            result
        }
        
        return found
    }
    
    /**
     * 压缩内容流
     */
    fun compressContent(page: PdfDictionary): Boolean {
        val contents = document.getPageContents(page)
        if (contents.isEmpty()) return false
        
        for (stream in contents) {
            val rawData = stream.rawData
            if (rawData.isEmpty()) continue
            
            // 使用 FlateDecode 压缩
            val compressed = StreamFilters.encode(rawData, "FlateDecode", null)
            
            stream.rawData = compressed
            stream.dict["Length"] = PdfNumber(compressed.size)
            stream.dict.setName("Filter", "FlateDecode")
            stream.clearDecodedCache()
        }
        
        document.markModified()
        return true
    }
    
    /**
     * 解压内容流
     */
    fun decompressContent(page: PdfDictionary): Boolean {
        val contents = document.getPageContents(page)
        if (contents.isEmpty()) return false
        
        for (stream in contents) {
            val filters = stream.getFilters()
            if (filters.isEmpty()) continue
            
            // 解压
            var data = stream.rawData
            for ((index, filter) in filters.withIndex()) {
                val params = stream.getDecodeParams(index)
                data = StreamFilters.decode(data, filter, params)
            }
            
            stream.rawData = data
            stream.dict["Length"] = PdfNumber(data.size)
            stream.dict.remove("Filter")
            stream.dict.remove("DecodeParms")
            stream.clearDecodedCache()
        }
        
        document.markModified()
        return true
    }
    
    /**
     * 优化内容流（移除冗余操作）
     */
    fun optimizeContent(page: PdfDictionary): Boolean {
        return rewritePageContent(page) { existing ->
            val result = mutableListOf<ContentInstruction>()
            var lastColor: List<PdfObject>? = null
            var lastLineWidth: PdfNumber? = null
            
            for (inst in existing) {
                when (inst.operator) {
                    // 跳过重复的颜色设置
                    "rg", "RG", "g", "G", "k", "K" -> {
                        if (inst.operands != lastColor) {
                            lastColor = inst.operands
                            result.add(inst)
                        }
                    }
                    // 跳过重复的线宽设置
                    "w" -> {
                        val width = inst.operands.getOrNull(0) as? PdfNumber
                        if (width != lastLineWidth) {
                            lastLineWidth = width
                            result.add(inst)
                        }
                    }
                    // 移除空的保存/恢复对
                    "q" -> {
                        result.add(inst)
                    }
                    "Q" -> {
                        // 检查是否可以移除空的 q/Q 对
                        if (result.isNotEmpty() && result.last().operator == "q") {
                            result.removeLast()
                        } else {
                            result.add(inst)
                        }
                    }
                    else -> result.add(inst)
                }
            }
            result
        }
    }
}

/**
 * 插入位置
 */
sealed class InsertPosition {
    object START : InsertPosition()
    object END : InsertPosition()
    data class After(val operator: String) : InsertPosition()
    data class Before(val operator: String) : InsertPosition()
    data class AtIndex(val index: Int) : InsertPosition()
}
