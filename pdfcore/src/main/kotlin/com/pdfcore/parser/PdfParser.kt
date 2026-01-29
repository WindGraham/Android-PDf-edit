package com.pdfcore.parser

import com.pdfcore.model.*
import com.pdfcore.security.PdfEncryption
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * PDF 文件解析器
 * 基于 PDF 32000-1:2008 标准
 * 
 * 支持功能：
 * - PDF 1.0 - 2.0 版本
 * - 传统 xref 表和 xref 流 (PDF 1.5+)
 * - Object Streams (压缩对象)
 * - 增量更新
 */
class PdfParser {
    
    private lateinit var raf: RandomAccessFile
    private var fileSize: Long = 0
    private var fileData: ByteArray? = null
    private var password: String = ""
    private var encryption: PdfEncryption? = null
    
    /**
     * 从文件路径解析 PDF
     */
    fun parse(filePath: String, password: String = ""): PdfParseResult<PdfDocument> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return PdfParseResult.failure(InvalidPdfFormatException("File not found: $filePath"))
            }
            if (!file.canRead()) {
                return PdfParseResult.failure(InvalidPdfFormatException("Cannot read file: $filePath"))
            }
            
            this.password = password
            raf = RandomAccessFile(filePath, "r")
            fileSize = raf.length()
            
            if (fileSize < 20) {
                return PdfParseResult.failure(InvalidPdfFormatException("File too small to be a valid PDF"))
            }
            
            val result = parseInternal()
            result.onSuccess { it.sourcePath = filePath }
            result
        } catch (e: PdfException) {
            PdfParseResult.failure(e)
        } catch (e: Exception) {
            PdfParseResult.failure(InvalidPdfFormatException("Failed to parse PDF: ${e.message}", e))
        } finally {
            try { raf.close() } catch (_: Exception) {}
            this.password = ""
            this.encryption = null
        }
    }
    
    /**
     * 从 InputStream 解析 PDF
     */
    fun parse(stream: InputStream, password: String = ""): PdfParseResult<PdfDocument> {
        return try {
            this.password = password
            fileData = stream.readBytes()
            fileSize = fileData!!.size.toLong()
            
            if (fileSize < 20) {
                return PdfParseResult.failure(InvalidPdfFormatException("Data too small to be a valid PDF"))
            }
            
            parseInternal()
        } catch (e: PdfException) {
            PdfParseResult.failure(e)
        } catch (e: Exception) {
            PdfParseResult.failure(InvalidPdfFormatException("Failed to parse PDF: ${e.message}", e))
        } finally {
            fileData = null
            this.password = ""
            this.encryption = null
        }
    }
    
    /**
     * 从字节数组解析 PDF
     */
    fun parse(data: ByteArray, password: String = ""): PdfParseResult<PdfDocument> {
        return try {
            this.password = password
            fileData = data
            fileSize = data.size.toLong()
            
            if (fileSize < 20) {
                return PdfParseResult.failure(InvalidPdfFormatException("Data too small to be a valid PDF"))
            }
            
            parseInternal()
        } catch (e: PdfException) {
            PdfParseResult.failure(e)
        } catch (e: Exception) {
            PdfParseResult.failure(InvalidPdfFormatException("Failed to parse PDF: ${e.message}", e))
        } finally {
            fileData = null
            this.password = ""
            this.encryption = null
        }
    }
    
    private fun parseInternal(): PdfParseResult<PdfDocument> {
        // 1. 读取并验证 PDF 头部
        val version = readHeader()
            ?: return PdfParseResult.failure(InvalidHeaderException("Invalid or missing PDF header"))
        
        val document = PdfDocument(version = version)
        
        // 2. 从文件末尾读取 startxref 和 trailer
        val (xrefOffset, trailer) = readTrailerFromEnd()
            ?: return PdfParseResult.failure(InvalidTrailerException("Cannot find startxref or trailer"))
        
        document.startXref = xrefOffset
        document.trailer = trailer
        
        // 3. 检查是否加密并设置加密处理器
        val encryptRef = trailer["Encrypt"]
        if (encryptRef != null) {
            // 读取 xref 以获取 Encrypt 字典
            val xrefEntries = mutableMapOf<String, XrefEntry>()
            readXrefChain(xrefOffset, xrefEntries, document)
            
            // 获取 Encrypt 字典
            val encryptDict = when (encryptRef) {
                is PdfDictionary -> encryptRef
                is PdfIndirectRef -> {
                    val entry = xrefEntries["${encryptRef.objectNumber} ${encryptRef.generationNumber}"]
                    if (entry is XrefEntry.InUse) {
                        readObjectAt(entry.byteOffset.toInt()) as? PdfDictionary
                    } else null
                }
                else -> null
            }
            
            if (encryptDict != null) {
                encryption = PdfEncryption.create(encryptDict, trailer, document)
                
                if (encryption != null && !encryption!!.authenticate(password)) {
                    return PdfParseResult.failure(InvalidPasswordException())
                }
            } else {
                return PdfParseResult.failure(EncryptedPdfException("Cannot read encryption dictionary"))
            }
            
            // 重置 xref 用于完整解析
            document.xrefEntries.addAll(xrefEntries.values)
            
            // 读取所有对象（带解密）
            readAllObjectsWithDecryption(document, xrefEntries)
        } else {
            // 4. 读取 xref 表（包括增量更新）
            val xrefEntries = mutableMapOf<String, XrefEntry>()
            readXrefChain(xrefOffset, xrefEntries, document)
            document.xrefEntries.addAll(xrefEntries.values)
            
            // 5. 读取所有对象
            readAllObjects(document, xrefEntries)
        }
        
        return PdfParseResult.success(document)
    }
    
    /**
     * 读取 PDF 头部
     * PDF 32000-1:2008 7.5.2
     */
    private fun readHeader(): String? {
        val header = readBytes(0, 20)
        val headerStr = String(header, Charsets.ISO_8859_1)
        
        // 查找 %PDF-x.y
        val pdfIndex = headerStr.indexOf("%PDF-")
        if (pdfIndex < 0) return null
        
        val versionStart = pdfIndex + 5
        val versionEnd = headerStr.indexOfAny(charArrayOf('\r', '\n', ' '), versionStart)
        val version = if (versionEnd > versionStart) {
            headerStr.substring(versionStart, versionEnd)
        } else {
            headerStr.substring(versionStart).take(3)
        }
        
        // 验证版本格式
        if (!version.matches(Regex("\\d+\\.\\d+"))) return null
        
        return "PDF-$version"
    }
    
    /**
     * 从文件末尾读取 trailer 信息
     * PDF 32000-1:2008 7.5.5
     */
    private fun readTrailerFromEnd(): Pair<Long, PdfDictionary>? {
        // 读取文件末尾 1KB
        val bufferSize = minOf(1024, fileSize.toInt())
        val tailData = readBytes((fileSize - bufferSize).toInt(), bufferSize)
        val tailStr = String(tailData, Charsets.ISO_8859_1)
        
        // 查找 %%EOF
        if (!tailStr.contains("%%EOF")) return null
        
        // 查找 startxref
        val startxrefIndex = tailStr.lastIndexOf("startxref")
        if (startxrefIndex < 0) return null
        
        // 解析 xref 偏移
        val afterStartxref = tailStr.substring(startxrefIndex + 9).trim()
        val xrefOffset = afterStartxref.takeWhile { it.isDigit() }.toLongOrNull() ?: return null
        
        // 读取 trailer
        val trailer = readTrailerAt(xrefOffset) ?: return null
        
        return xrefOffset to trailer
    }
    
    /**
     * 读取指定位置的 trailer
     */
    private fun readTrailerAt(xrefOffset: Long): PdfDictionary? {
        val peekData = readBytes(xrefOffset.toInt(), 20)
        val peekStr = String(peekData, Charsets.ISO_8859_1).trim()
        
        return if (peekStr.startsWith("xref")) {
            // 传统 xref 表
            readTraditionalTrailer(xrefOffset)
        } else {
            // xref 流
            readXrefStreamTrailer(xrefOffset)
        }
    }
    
    /**
     * 读取传统 trailer 字典
     */
    private fun readTraditionalTrailer(xrefOffset: Long): PdfDictionary? {
        // 读取 xref 部分到文件末尾
        val data = readBytes(xrefOffset.toInt(), (fileSize - xrefOffset).toInt())
        val content = String(data, Charsets.ISO_8859_1)
        
        // 查找 trailer 关键字
        val trailerIndex = content.indexOf("trailer")
        if (trailerIndex < 0) return null
        
        val afterTrailer = content.substring(trailerIndex + 7)
        val lexer = PdfLexer(afterTrailer)
        
        return try {
            lexer.parseDictionary()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 读取 xref 流的 trailer
     */
    private fun readXrefStreamTrailer(offset: Long): PdfDictionary? {
        return try {
            val obj = readObjectAt(offset.toInt())
            (obj as? PdfStream)?.dict
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 递归读取 xref 链（处理增量更新）
     */
    private fun readXrefChain(
        xrefOffset: Long,
        entries: MutableMap<String, XrefEntry>,
        document: PdfDocument
    ) {
        val peekData = readBytes(xrefOffset.toInt(), 20)
        val peekStr = String(peekData, Charsets.ISO_8859_1).trim()
        
        val (newEntries, prevOffset) = if (peekStr.startsWith("xref")) {
            readTraditionalXref(xrefOffset)
        } else {
            readXrefStream(xrefOffset, document)
        }
        
        // 新条目优先（后写入的覆盖先写入的）
        for ((key, entry) in newEntries) {
            if (!entries.containsKey(key)) {
                entries[key] = entry
            }
        }
        
        // 递归处理 Prev
        if (prevOffset != null && prevOffset > 0) {
            readXrefChain(prevOffset, entries, document)
        }
    }
    
    /**
     * 读取传统 xref 表
     * PDF 32000-1:2008 7.5.4
     */
    private fun readTraditionalXref(xrefOffset: Long): Pair<Map<String, XrefEntry>, Long?> {
        val data = readBytes(xrefOffset.toInt(), (fileSize - xrefOffset).toInt())
        val content = String(data, Charsets.ISO_8859_1)
        val lines = content.lines()
        
        val entries = mutableMapOf<String, XrefEntry>()
        var lineIndex = 0
        
        // 跳过 "xref"
        if (lines.getOrNull(lineIndex)?.trim() == "xref") {
            lineIndex++
        }
        
        // 读取子段
        while (lineIndex < lines.size) {
            val line = lines[lineIndex].trim()
            
            // 到达 trailer
            if (line.startsWith("trailer")) break
            if (line.isEmpty()) {
                lineIndex++
                continue
            }
            
            // 解析子段头部: startObj count
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) {
                lineIndex++
                continue
            }
            
            val startObj = parts[0].toIntOrNull() ?: break
            val count = parts[1].toIntOrNull() ?: break
            lineIndex++
            
            // 读取条目
            for (i in 0 until count) {
                if (lineIndex >= lines.size) break
                val entryLine = lines[lineIndex]
                lineIndex++
                
                if (entryLine.length < 18) continue
                
                val offsetStr = entryLine.substring(0, 10).trim()
                val genStr = entryLine.substring(10, 16).trim()
                val typeChar = entryLine.getOrNull(17) ?: continue
                
                val offset = offsetStr.toLongOrNull() ?: continue
                val gen = genStr.toIntOrNull() ?: continue
                val objNum = startObj + i
                val key = "$objNum $gen"
                
                val entry = when (typeChar) {
                    'n' -> XrefEntry.InUse(objNum, gen, offset)
                    'f' -> XrefEntry.Free(objNum, gen, offset.toInt())
                    else -> continue
                }
                
                entries[key] = entry
            }
        }
        
        // 读取 Prev
        val trailerIndex = content.indexOf("trailer")
        var prevOffset: Long? = null
        if (trailerIndex >= 0) {
            val afterTrailer = content.substring(trailerIndex + 7)
            val lexer = PdfLexer(afterTrailer)
            try {
                val trailer = lexer.parseDictionary()
                prevOffset = trailer.getNumber("Prev")?.toLong()
            } catch (_: Exception) {}
        }
        
        return entries to prevOffset
    }
    
    /**
     * 读取 xref 流
     * PDF 32000-1:2008 7.5.8
     */
    private fun readXrefStream(
        offset: Long, 
        document: PdfDocument
    ): Pair<Map<String, XrefEntry>, Long?> {
        val entries = mutableMapOf<String, XrefEntry>()
        
        val obj = readObjectAt(offset.toInt())
        val stream = obj as? PdfStream ?: return entries to null
        val dict = stream.dict
        
        // 验证类型
        if (dict.getNameValue("Type") != "XRef") return entries to null
        
        // 获取 W 数组
        val wArray = dict.getArray("W") ?: return entries to null
        if (wArray.size < 3) return entries to null
        
        val w1 = wArray.getInt(0) ?: 0
        val w2 = wArray.getInt(1) ?: 0
        val w3 = wArray.getInt(2) ?: 0
        val entrySize = w1 + w2 + w3
        
        if (entrySize == 0) return entries to null
        
        // 获取 Size
        val size = dict.getInt("Size") ?: return entries to null
        
        // 获取 Index 数组
        val indexArray = dict.getArray("Index")
        val subsections = mutableListOf<Pair<Int, Int>>()
        
        if (indexArray != null && indexArray.size >= 2) {
            var i = 0
            while (i + 1 < indexArray.size) {
                val start = indexArray.getInt(i) ?: 0
                val count = indexArray.getInt(i + 1) ?: 0
                subsections.add(start to count)
                i += 2
            }
        } else {
            subsections.add(0 to size)
        }
        
        // 解码流数据
        val decodedData = decodeStream(stream)
        
        // 解析条目
        var dataOffset = 0
        for ((startObjNum, count) in subsections) {
            for (i in 0 until count) {
                if (dataOffset + entrySize > decodedData.size) break
                
                val objNum = startObjNum + i
                
                // 读取字段
                val type = if (w1 > 0) {
                    readBigEndianInt(decodedData, dataOffset, w1)
                } else 1 // 默认类型为 1
                
                val field2 = if (w2 > 0) {
                    readBigEndianInt(decodedData, dataOffset + w1, w2)
                } else 0
                
                val field3 = if (w3 > 0) {
                    readBigEndianInt(decodedData, dataOffset + w1 + w2, w3)
                } else 0
                
                val entry = when (type.toInt()) {
                    0 -> XrefEntry.Free(objNum, field3.toInt(), field2.toInt())
                    1 -> XrefEntry.InUse(objNum, field3.toInt(), field2)
                    2 -> XrefEntry.Compressed(objNum, field2.toInt(), field3.toInt())
                    else -> null
                }
                
                if (entry != null) {
                    entries["$objNum ${entry.generationNumber}"] = entry
                }
                
                dataOffset += entrySize
            }
        }
        
        // 如果这是主 trailer，更新文档的 trailer
        if (document.trailer.isEmpty()) {
            document.trailer = dict.copy()
        }
        
        val prevOffset = dict.getNumber("Prev")?.toLong()
        return entries to prevOffset
    }
    
    /**
     * 读取大端序整数
     */
    private fun readBigEndianInt(data: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        for (i in 0 until length) {
            if (offset + i < data.size) {
                value = (value shl 8) or (data[offset + i].toLong() and 0xFF)
            }
        }
        return value
    }
    
    /**
     * 读取所有对象
     */
    private fun readAllObjects(document: PdfDocument, xrefEntries: Map<String, XrefEntry>) {
        // 读取直接存储的对象
        val inUseEntries = xrefEntries.values.filterIsInstance<XrefEntry.InUse>()
        for (entry in inUseEntries) {
            try {
                val obj = readObjectAt(entry.byteOffset.toInt())
                if (obj != null) {
                    document.objects[entry.objectNumber.toString() + " " + entry.generationNumber] = 
                        PdfIndirectObject(entry.objectNumber, entry.generationNumber, obj)
                }
            } catch (e: Exception) {
                // 继续处理其他对象
            }
        }
        
        // 读取压缩存储的对象
        val compressedEntries = xrefEntries.values.filterIsInstance<XrefEntry.Compressed>()
        val groupedByStream = compressedEntries.groupBy { it.objectStreamNumber }
        
        for ((streamObjNum, entries) in groupedByStream) {
            try {
                readObjectsFromStream(document, streamObjNum, entries)
            } catch (e: Exception) {
                // 继续处理其他流
            }
        }
    }
    
    /**
     * 读取所有对象（带解密）
     */
    private fun readAllObjectsWithDecryption(document: PdfDocument, xrefEntries: Map<String, XrefEntry>) {
        val enc = encryption ?: return readAllObjects(document, xrefEntries)
        
        // 读取直接存储的对象
        val inUseEntries = xrefEntries.values.filterIsInstance<XrefEntry.InUse>()
        for (entry in inUseEntries) {
            try {
                val obj = readObjectAt(entry.byteOffset.toInt())
                if (obj != null) {
                    // 解密对象（Encrypt 字典本身不解密）
                    val key = "${entry.objectNumber} ${entry.generationNumber}"
                    val encryptRef = document.trailer.getRef("Encrypt")
                    val isEncryptDict = encryptRef != null && 
                        encryptRef.objectNumber == entry.objectNumber && 
                        encryptRef.generationNumber == entry.generationNumber
                    
                    val decryptedObj = if (isEncryptDict) {
                        obj
                    } else {
                        enc.decryptObject(entry.objectNumber, entry.generationNumber, obj)
                    }
                    
                    document.objects[key] = 
                        PdfIndirectObject(entry.objectNumber, entry.generationNumber, decryptedObj)
                }
            } catch (e: Exception) {
                // 继续处理其他对象
            }
        }
        
        // 读取压缩存储的对象
        val compressedEntries = xrefEntries.values.filterIsInstance<XrefEntry.Compressed>()
        val groupedByStream = compressedEntries.groupBy { it.objectStreamNumber }
        
        for ((streamObjNum, entries) in groupedByStream) {
            try {
                readObjectsFromStreamWithDecryption(document, streamObjNum, entries, enc)
            } catch (e: Exception) {
                // 继续处理其他流
            }
        }
    }
    
    /**
     * 从 Object Stream 读取对象（带解密）
     */
    private fun readObjectsFromStreamWithDecryption(
        document: PdfDocument,
        streamObjNum: Int,
        entries: List<XrefEntry.Compressed>,
        enc: PdfEncryption
    ) {
        val streamObj = document.objects["$streamObjNum 0"]?.obj as? PdfStream ?: return
        val dict = streamObj.dict
        
        // 验证类型
        if (dict.getNameValue("Type") != "ObjStm") return
        
        // 获取参数
        val n = dict.getInt("N") ?: return
        val first = dict.getInt("First") ?: return
        
        // 解码流（Object Stream 的内容已经在流级别解密了）
        val decodedData = decodeStream(streamObj)
        val content = String(decodedData, Charsets.ISO_8859_1)
        
        // 解析头部：N 对 (objNum offset)
        val headerLexer = PdfLexer(content)
        val objOffsets = mutableListOf<Pair<Int, Int>>()
        
        for (i in 0 until n) {
            val objNum = (headerLexer.nextObject() as? PdfNumber)?.toInt() ?: break
            val offset = (headerLexer.nextObject() as? PdfNumber)?.toInt() ?: break
            objOffsets.add(objNum to offset)
        }
        
        // 读取请求的对象
        for (entry in entries) {
            val objInfo = objOffsets.find { it.first == entry.objectNumber } ?: continue
            val objOffset = first + objInfo.second
            
            if (objOffset >= content.length) continue
            
            val objContent = content.substring(objOffset)
            val lexer = PdfLexer(objContent)
            val obj = lexer.nextObject() ?: continue
            
            // Object Stream 中的对象不需要单独解密
            // 因为整个流已经被解密了
            document.objects["${entry.objectNumber} 0"] = 
                PdfIndirectObject(entry.objectNumber, 0, obj)
        }
    }
    
    /**
     * 从 Object Stream 读取对象
     * PDF 32000-1:2008 7.5.7
     */
    private fun readObjectsFromStream(
        document: PdfDocument,
        streamObjNum: Int,
        entries: List<XrefEntry.Compressed>
    ) {
        val streamObj = document.objects["$streamObjNum 0"]?.obj as? PdfStream ?: return
        val dict = streamObj.dict
        
        // 验证类型
        if (dict.getNameValue("Type") != "ObjStm") return
        
        // 获取参数
        val n = dict.getInt("N") ?: return
        val first = dict.getInt("First") ?: return
        
        // 解码流
        val decodedData = decodeStream(streamObj)
        val content = String(decodedData, Charsets.ISO_8859_1)
        
        // 解析头部：N 对 (objNum offset)
        val headerLexer = PdfLexer(content)
        val objOffsets = mutableListOf<Pair<Int, Int>>()
        
        for (i in 0 until n) {
            val objNum = (headerLexer.nextObject() as? PdfNumber)?.toInt() ?: break
            val offset = (headerLexer.nextObject() as? PdfNumber)?.toInt() ?: break
            objOffsets.add(objNum to offset)
        }
        
        // 读取请求的对象
        for (entry in entries) {
            val objInfo = objOffsets.find { it.first == entry.objectNumber } ?: continue
            val objOffset = first + objInfo.second
            
            if (objOffset >= content.length) continue
            
            val objContent = content.substring(objOffset)
            val lexer = PdfLexer(objContent)
            val obj = lexer.nextObject() ?: continue
            
            document.objects["${entry.objectNumber} 0"] = 
                PdfIndirectObject(entry.objectNumber, 0, obj)
        }
    }
    
    /**
     * 读取指定偏移处的对象
     */
    private fun readObjectAt(offset: Int): PdfObject? {
        // 读取足够的数据
        val chunkSize = minOf(65536, (fileSize - offset).toInt())
        if (chunkSize <= 0) return null
        
        val data = readBytes(offset, chunkSize)
        val lexer = PdfLexer(data)
        
        lexer.skipWhitespaceAndComments()
        
        // 尝试读取对象头: objNum genNum obj
        val num1 = lexer.nextObject() as? PdfNumber ?: return null
        lexer.skipWhitespaceAndComments()
        val num2 = lexer.nextObject() as? PdfNumber ?: return null
        lexer.skipWhitespaceAndComments()
        
        if (!lexer.matchKeyword("obj")) return null
        lexer.skipWhitespaceAndComments()
        
        // 读取对象内容
        return lexer.parseDictionaryOrStream()
    }
    
    /**
     * 解码流数据
     */
    private fun decodeStream(stream: PdfStream): ByteArray {
        val filters = stream.getFilters()
        if (filters.isEmpty()) return stream.rawData
        
        var data = stream.rawData
        for ((index, filter) in filters.withIndex()) {
            val params = stream.getDecodeParams(index)
            data = StreamFilters.decode(data, filter, params)
        }
        
        stream.decodedData = data
        return data
    }
    
    /**
     * 读取指定位置的字节
     */
    private fun readBytes(offset: Int, length: Int): ByteArray {
        val actualLength = minOf(length, (fileSize - offset).toInt())
        if (actualLength <= 0) return ByteArray(0)
        
        return if (fileData != null) {
            fileData!!.copyOfRange(offset, offset + actualLength)
        } else {
            raf.seek(offset.toLong())
            val buffer = ByteArray(actualLength)
            raf.readFully(buffer)
            buffer
        }
    }
}
