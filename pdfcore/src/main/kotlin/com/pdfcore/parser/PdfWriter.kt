package com.pdfcore.parser

import com.pdfcore.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * PDF 写入器
 * 基于 PDF 32000-1:2008 标准
 * 
 * 支持：
 * - 完整写入新文档
 * - 增量更新现有文档
 */
class PdfWriter {
    
    companion object {
        private const val NEWLINE = "\n"
        private const val HEADER_COMMENT = "%\u0080\u0081\u0082\u0083"
    }
    
    /**
     * 将文档写入文件
     */
    fun write(document: PdfDocument, filePath: String): Boolean {
        return try {
            File(filePath).outputStream().use { output ->
                write(document, output)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 将文档写入输出流
     */
    fun write(document: PdfDocument, output: OutputStream) {
        val buffer = ByteArrayOutputStream()
        val offsets = mutableMapOf<String, Long>()
        
        // 1. 写入头部
        writeHeader(buffer, document.version)
        
        // 2. 按对象号排序写入所有对象
        val sortedObjects = document.objects.values.sortedBy { it.objectNumber }
        
        for (obj in sortedObjects) {
            val offset = buffer.size().toLong()
            offsets[obj.toKey()] = offset
            writeIndirectObject(buffer, obj)
        }
        
        // 3. 写入交叉引用表
        val xrefOffset = buffer.size().toLong()
        writeXrefTable(buffer, document, offsets)
        
        // 4. 写入 trailer
        writeTrailer(buffer, document, offsets.size)
        
        // 5. 写入 startxref 和 EOF
        buffer.write("startxref$NEWLINE".toByteArray())
        buffer.write("$xrefOffset$NEWLINE".toByteArray())
        buffer.write("%%EOF".toByteArray())
        
        // 输出到流
        output.write(buffer.toByteArray())
    }
    
    /**
     * 增量更新文档（追加到现有文件）
     */
    fun writeIncremental(document: PdfDocument, originalData: ByteArray, output: OutputStream) {
        // 写入原始数据
        output.write(originalData)
        
        val buffer = ByteArrayOutputStream()
        val newOffsets = mutableMapOf<String, Long>()
        val baseOffset = originalData.size.toLong()
        
        // 收集需要写入的对象（新增或修改的）
        val modifiedObjects = document.objects.values.filter { obj ->
            // 检查是否是新对象或已修改
            val xrefEntry = document.xrefEntries.find { 
                it.objectNumber == obj.objectNumber && 
                it.generationNumber == obj.generationNumber 
            }
            // 简化处理：写入所有对象
            true
        }.sortedBy { it.objectNumber }
        
        // 写入修改的对象
        for (obj in modifiedObjects) {
            val offset = baseOffset + buffer.size().toLong()
            newOffsets[obj.toKey()] = offset
            writeIndirectObject(buffer, obj)
        }
        
        // 写入新的交叉引用表
        val xrefOffset = baseOffset + buffer.size().toLong()
        
        buffer.write("xref$NEWLINE".toByteArray())
        buffer.write("0 1$NEWLINE".toByteArray())
        buffer.write("0000000000 65535 f $NEWLINE".toByteArray())
        
        // 写入每个修改对象的条目
        for (obj in modifiedObjects) {
            val offset = newOffsets[obj.toKey()] ?: continue
            buffer.write("${obj.objectNumber} 1$NEWLINE".toByteArray())
            buffer.write(String.format("%010d %05d n $NEWLINE", offset, obj.generationNumber).toByteArray())
        }
        
        // 写入 trailer
        buffer.write("trailer$NEWLINE".toByteArray())
        val trailer = document.trailer.copy()
        trailer["Size"] = PdfNumber(document.objects.size + 1)
        trailer["Prev"] = PdfNumber(document.startXref.toDouble())
        buffer.write(formatObject(trailer).toByteArray())
        buffer.write(NEWLINE.toByteArray())
        
        // 写入 startxref 和 EOF
        buffer.write("startxref$NEWLINE".toByteArray())
        buffer.write("$xrefOffset$NEWLINE".toByteArray())
        buffer.write("%%EOF".toByteArray())
        
        output.write(buffer.toByteArray())
    }
    
    /**
     * 写入 PDF 头部
     */
    private fun writeHeader(buffer: ByteArrayOutputStream, version: String) {
        val versionNum = if (version.startsWith("PDF-")) {
            version.substring(4)
        } else if (version.startsWith("%PDF-")) {
            version.substring(5)
        } else {
            version
        }
        
        buffer.write("%PDF-$versionNum$NEWLINE".toByteArray())
        buffer.write("$HEADER_COMMENT$NEWLINE".toByteArray())
    }
    
    /**
     * 写入间接对象
     */
    private fun writeIndirectObject(buffer: ByteArrayOutputStream, obj: PdfIndirectObject) {
        buffer.write("${obj.objectNumber} ${obj.generationNumber} obj$NEWLINE".toByteArray())
        
        when (val content = obj.obj) {
            is PdfStream -> writeStream(buffer, content)
            else -> buffer.write(formatObject(content).toByteArray())
        }
        
        buffer.write("$NEWLINE endobj$NEWLINE".toByteArray())
    }
    
    /**
     * 写入流对象
     */
    private fun writeStream(buffer: ByteArrayOutputStream, stream: PdfStream) {
        // 更新长度
        stream.dict["Length"] = PdfNumber(stream.rawData.size)
        
        // 写入字典
        buffer.write(formatObject(stream.dict).toByteArray())
        buffer.write("${NEWLINE}stream$NEWLINE".toByteArray())
        
        // 写入流数据
        buffer.write(stream.rawData)
        
        buffer.write("${NEWLINE}endstream".toByteArray())
    }
    
    /**
     * 写入交叉引用表
     */
    private fun writeXrefTable(
        buffer: ByteArrayOutputStream,
        document: PdfDocument,
        offsets: Map<String, Long>
    ) {
        val maxObjNum = document.objects.keys
            .mapNotNull { it.split(" ").firstOrNull()?.toIntOrNull() }
            .maxOrNull() ?: 0
        
        buffer.write("xref$NEWLINE".toByteArray())
        buffer.write("0 ${maxObjNum + 1}$NEWLINE".toByteArray())
        
        // 对象 0 总是 free
        buffer.write("0000000000 65535 f $NEWLINE".toByteArray())
        
        // 写入每个对象的条目
        for (objNum in 1..maxObjNum) {
            val key = "$objNum 0"
            val offset = offsets[key]
            
            if (offset != null) {
                buffer.write(String.format("%010d %05d n $NEWLINE", offset, 0).toByteArray())
            } else {
                // 空闲对象
                buffer.write("0000000000 65535 f $NEWLINE".toByteArray())
            }
        }
    }
    
    /**
     * 写入 trailer
     */
    private fun writeTrailer(
        buffer: ByteArrayOutputStream,
        document: PdfDocument,
        objectCount: Int
    ) {
        buffer.write("trailer$NEWLINE".toByteArray())
        
        val trailer = document.trailer.copy()
        trailer["Size"] = PdfNumber(objectCount + 1)
        
        // 生成文件 ID（如果没有）
        if (trailer["ID"] == null) {
            val id = generateFileId()
            trailer["ID"] = PdfArray(id, id)
        }
        
        buffer.write(formatObject(trailer).toByteArray())
        buffer.write(NEWLINE.toByteArray())
    }
    
    /**
     * 生成文件 ID
     */
    private fun generateFileId(): PdfString {
        val timestamp = System.currentTimeMillis()
        val random = Random().nextLong()
        val data = "$timestamp$random".toByteArray()
        
        val md5 = MessageDigest.getInstance("MD5")
        val hash = md5.digest(data)
        
        return PdfString.fromBytes(hash, asHex = true)
    }
    
    /**
     * 格式化 PDF 对象
     */
    private fun formatObject(obj: PdfObject): String {
        return when (obj) {
            is PdfBoolean -> obj.toPdfString()
            is PdfNumber -> obj.toPdfString()
            is PdfString -> obj.toPdfString()
            is PdfName -> obj.toPdfString()
            is PdfArray -> formatArray(obj)
            is PdfDictionary -> formatDictionary(obj)
            is PdfStream -> formatStream(obj)
            is PdfIndirectRef -> obj.toPdfString()
            is PdfNull -> "null"
            else -> "null"
        }
    }
    
    private fun formatArray(array: PdfArray): String {
        val elements = array.joinToString(" ") { formatObject(it) }
        return "[ $elements ]"
    }
    
    private fun formatDictionary(dict: PdfDictionary): String {
        if (dict.isEmpty()) return "<< >>"
        
        val entries = dict.entries.joinToString(NEWLINE) { (k, v) ->
            "/${escapeName(k)} ${formatObject(v)}"
        }
        return "<<$NEWLINE$entries$NEWLINE>>"
    }
    
    private fun formatStream(stream: PdfStream): String {
        stream.dict["Length"] = PdfNumber(stream.rawData.size)
        return "${formatDictionary(stream.dict)}${NEWLINE}stream$NEWLINE${String(stream.rawData, Charsets.ISO_8859_1)}${NEWLINE}endstream"
    }
    
    private fun escapeName(name: String): String {
        val specialChars = setOf(' ', '\t', '\n', '\r', '(', ')', '<', '>', '[', ']', '{', '}', '/', '%', '#')
        return name.map { c ->
            when {
                c.code < 0x21 || c.code > 0x7E -> "#%02X".format(c.code)
                c in specialChars -> "#%02X".format(c.code)
                else -> c.toString()
            }
        }.joinToString("")
    }
}

/**
 * PDF 文档构建器
 * 
 * 用于从零创建新的 PDF 文档
 */
class PdfDocumentBuilder {
    
    private val document = PdfDocument(version = "1.7")
    private var currentObjectNumber = 1
    
    /**
     * 创建目录和页面树
     */
    fun initialize(): PdfDocumentBuilder {
        // 创建页面树根节点
        val pagesDict = PdfDictionary()
        pagesDict.setName("Type", "Pages")
        pagesDict["Kids"] = PdfArray()
        pagesDict["Count"] = PdfNumber(0)
        val pagesRef = addObject(pagesDict)
        
        // 创建 Catalog
        val catalogDict = PdfDictionary()
        catalogDict.setName("Type", "Catalog")
        catalogDict["Pages"] = pagesRef
        val catalogRef = addObject(catalogDict)
        
        // 设置 trailer
        document.trailer["Root"] = catalogRef
        document.trailer["Size"] = PdfNumber(currentObjectNumber)
        
        return this
    }
    
    /**
     * 添加页面
     */
    fun addPage(
        width: Float = 612f,  // Letter width
        height: Float = 792f,  // Letter height
        contentBuilder: (ContentBuilder) -> Unit = {}
    ): PdfIndirectRef {
        val pagesRoot = document.getPagesRoot() ?: throw IllegalStateException("Document not initialized")
        val pagesRootRef = document.trailer.getRef("Root")?.let { 
            document.getCatalog()?.getRef("Pages") 
        } ?: throw IllegalStateException("Cannot find Pages root")
        
        // 创建资源字典
        val resources = PdfDictionary()
        val fonts = PdfDictionary()
        resources["Font"] = fonts
        
        // 添加默认字体
        val helvetica = PdfDictionary()
        helvetica.setName("Type", "Font")
        helvetica.setName("Subtype", "Type1")
        helvetica.setName("BaseFont", "Helvetica")
        val helveticaRef = addObject(helvetica)
        fonts["F1"] = helveticaRef
        
        val helveticaBold = PdfDictionary()
        helveticaBold.setName("Type", "Font")
        helveticaBold.setName("Subtype", "Type1")
        helveticaBold.setName("BaseFont", "Helvetica-Bold")
        val helveticaBoldRef = addObject(helveticaBold)
        fonts["F2"] = helveticaBoldRef
        
        val resourcesRef = addObject(resources)
        
        // 构建内容
        val contentBuilder2 = ContentBuilder()
        contentBuilder(contentBuilder2)
        val contentData = contentBuilder2.build()
        
        // 创建内容流
        val contentStream = PdfStream(PdfDictionary(), contentData)
        contentStream.dict["Length"] = PdfNumber(contentData.size)
        val contentRef = addObject(contentStream)
        
        // 创建页面字典
        val pageDict = PdfDictionary()
        pageDict.setName("Type", "Page")
        pageDict["Parent"] = pagesRootRef
        pageDict["MediaBox"] = PdfArray(
            PdfNumber(0), PdfNumber(0),
            PdfNumber(width.toDouble()), PdfNumber(height.toDouble())
        )
        pageDict["Resources"] = resourcesRef
        pageDict["Contents"] = contentRef
        val pageRef = addObject(pageDict)
        
        // 更新页面树
        val kids = pagesRoot.getArray("Kids") ?: PdfArray().also { pagesRoot["Kids"] = it }
        kids.add(pageRef)
        pagesRoot["Count"] = PdfNumber(kids.size)
        
        return pageRef
    }
    
    /**
     * 设置文档信息
     */
    fun setInfo(
        title: String? = null,
        author: String? = null,
        subject: String? = null,
        keywords: String? = null,
        creator: String? = null
    ): PdfDocumentBuilder {
        val info = PdfDictionary()
        
        title?.let { info["Title"] = PdfString(it) }
        author?.let { info["Author"] = PdfString(it) }
        subject?.let { info["Subject"] = PdfString(it) }
        keywords?.let { info["Keywords"] = PdfString(it) }
        creator?.let { info["Creator"] = PdfString(it) }
        
        // 添加创建时间
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val dateStr = "D:${dateFormat.format(Date())}"
        info["CreationDate"] = PdfString(dateStr)
        info["ModDate"] = PdfString(dateStr)
        
        val infoRef = addObject(info)
        document.trailer["Info"] = infoRef
        
        return this
    }
    
    /**
     * 构建文档
     */
    fun build(): PdfDocument {
        document.trailer["Size"] = PdfNumber(currentObjectNumber)
        return document
    }
    
    private fun addObject(obj: PdfObject): PdfIndirectRef {
        val objNum = currentObjectNumber++
        val indirectObj = PdfIndirectObject(objNum, 0, obj)
        document.objects[indirectObj.toKey()] = indirectObj
        return indirectObj.toRef()
    }
}

/**
 * 内容流构建器
 */
class ContentBuilder {
    private val instructions = StringBuilder()
    
    fun saveState(): ContentBuilder {
        instructions.append("q\n")
        return this
    }
    
    fun restoreState(): ContentBuilder {
        instructions.append("Q\n")
        return this
    }
    
    fun setFillColor(r: Float, g: Float, b: Float): ContentBuilder {
        instructions.append("$r $g $b rg\n")
        return this
    }
    
    fun setStrokeColor(r: Float, g: Float, b: Float): ContentBuilder {
        instructions.append("$r $g $b RG\n")
        return this
    }
    
    fun setLineWidth(width: Float): ContentBuilder {
        instructions.append("$width w\n")
        return this
    }
    
    fun moveTo(x: Float, y: Float): ContentBuilder {
        instructions.append("$x $y m\n")
        return this
    }
    
    fun lineTo(x: Float, y: Float): ContentBuilder {
        instructions.append("$x $y l\n")
        return this
    }
    
    fun rectangle(x: Float, y: Float, width: Float, height: Float): ContentBuilder {
        instructions.append("$x $y $width $height re\n")
        return this
    }
    
    fun stroke(): ContentBuilder {
        instructions.append("S\n")
        return this
    }
    
    fun fill(): ContentBuilder {
        instructions.append("f\n")
        return this
    }
    
    fun fillAndStroke(): ContentBuilder {
        instructions.append("B\n")
        return this
    }
    
    fun beginText(): ContentBuilder {
        instructions.append("BT\n")
        return this
    }
    
    fun endText(): ContentBuilder {
        instructions.append("ET\n")
        return this
    }
    
    fun setFont(fontName: String, size: Float): ContentBuilder {
        instructions.append("/$fontName $size Tf\n")
        return this
    }
    
    fun moveTextPosition(x: Float, y: Float): ContentBuilder {
        instructions.append("$x $y Td\n")
        return this
    }
    
    fun setTextMatrix(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): ContentBuilder {
        instructions.append("$a $b $c $d $e $f Tm\n")
        return this
    }
    
    fun showText(text: String): ContentBuilder {
        val escaped = text.replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
        instructions.append("($escaped) Tj\n")
        return this
    }
    
    fun nextLine(): ContentBuilder {
        instructions.append("T*\n")
        return this
    }
    
    fun setLeading(leading: Float): ContentBuilder {
        instructions.append("$leading TL\n")
        return this
    }
    
    fun build(): ByteArray {
        return instructions.toString().toByteArray(Charsets.ISO_8859_1)
    }
}
