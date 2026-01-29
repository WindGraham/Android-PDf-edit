package com.pdfcore.parser

import com.pdfcore.model.PdfDictionary
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * PDF 流过滤器
 * 基于 PDF 32000-1:2008 标准 7.4 节
 * 
 * 支持的过滤器:
 * - FlateDecode (zlib/deflate)
 * - LZWDecode
 * - ASCIIHexDecode
 * - ASCII85Decode
 * - RunLengthDecode
 */
object StreamFilters {
    
    /**
     * 解码流数据
     */
    fun decode(data: ByteArray, filterName: String, params: PdfDictionary?): ByteArray {
        return when (filterName) {
            "FlateDecode", "Fl" -> decodeFlateDecode(data, params)
            "LZWDecode", "LZW" -> decodeLZW(data, params)
            "ASCIIHexDecode", "AHx" -> decodeASCIIHex(data)
            "ASCII85Decode", "A85" -> decodeASCII85(data)
            "RunLengthDecode", "RL" -> decodeRunLength(data)
            "DCTDecode", "DCT" -> data // JPEG, 保持原样供图像解码器处理
            "JPXDecode" -> data // JPEG2000, 保持原样
            "CCITTFaxDecode", "CCF" -> data // 传真压缩, 复杂，暂不支持
            "JBIG2Decode" -> data // JBIG2, 复杂，暂不支持
            else -> throw UnsupportedFilterException(filterName)
        }
    }
    
    /**
     * 编码流数据
     */
    fun encode(data: ByteArray, filterName: String, params: PdfDictionary?): ByteArray {
        return when (filterName) {
            "FlateDecode", "Fl" -> encodeFlateDecode(data)
            "ASCIIHexDecode", "AHx" -> encodeASCIIHex(data)
            "ASCII85Decode", "A85" -> encodeASCII85(data)
            else -> throw UnsupportedFilterException(filterName)
        }
    }
    
    // ==================== FlateDecode ====================
    
    /**
     * FlateDecode 解码 (zlib/deflate)
     * PDF 32000-1:2008 7.4.4
     */
    fun decodeFlateDecode(data: ByteArray, params: PdfDictionary?): ByteArray {
        if (data.isEmpty()) return data
        
        val inflater = Inflater()
        val output = ByteArrayOutputStream()
        
        try {
            inflater.setInput(data)
            val buffer = ByteArray(4096)
            
            while (!inflater.finished()) {
                try {
                    val count = inflater.inflate(buffer)
                    if (count == 0) {
                        if (inflater.needsInput()) break
                        if (inflater.needsDictionary()) {
                            throw StreamDecodeException("FlateDecode needs dictionary", "FlateDecode")
                        }
                    }
                    output.write(buffer, 0, count)
                } catch (e: DataFormatException) {
                    // 尝试不带 zlib 头的原始 deflate
                    inflater.end()
                    return decodeRawDeflate(data, params)
                }
            }
            
            var result = output.toByteArray()
            
            // 应用预测器
            val predictor = params?.getInt("Predictor") ?: 1
            if (predictor > 1) {
                result = applyPredictorDecode(result, params!!)
            }
            
            return result
        } finally {
            inflater.end()
        }
    }
    
    /**
     * 解码原始 deflate 数据（无 zlib 头）
     */
    private fun decodeRawDeflate(data: ByteArray, params: PdfDictionary?): ByteArray {
        val inflater = Inflater(true) // nowrap = true
        val output = ByteArrayOutputStream()
        
        try {
            inflater.setInput(data)
            val buffer = ByteArray(4096)
            
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                output.write(buffer, 0, count)
            }
            
            var result = output.toByteArray()
            
            val predictor = params?.getInt("Predictor") ?: 1
            if (predictor > 1) {
                result = applyPredictorDecode(result, params!!)
            }
            
            return result
        } finally {
            inflater.end()
        }
    }
    
    /**
     * FlateDecode 编码
     */
    fun encodeFlateDecode(data: ByteArray): ByteArray {
        val deflater = java.util.zip.Deflater()
        val output = ByteArrayOutputStream()
        
        try {
            deflater.setInput(data)
            deflater.finish()
            
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                output.write(buffer, 0, count)
            }
            
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }
    
    /**
     * 应用预测器解码
     * PDF 32000-1:2008 7.4.4.4
     */
    private fun applyPredictorDecode(data: ByteArray, params: PdfDictionary): ByteArray {
        val predictor = params.getInt("Predictor") ?: 1
        if (predictor == 1) return data
        
        val columns = params.getInt("Columns") ?: 1
        val colors = params.getInt("Colors") ?: 1
        val bitsPerComponent = params.getInt("BitsPerComponent") ?: 8
        
        val bytesPerPixel = (colors * bitsPerComponent + 7) / 8
        val bytesPerRow = (columns * colors * bitsPerComponent + 7) / 8
        
        return when {
            predictor == 2 -> applyTiffPredictor(data, bytesPerRow, bytesPerPixel)
            predictor >= 10 -> applyPngPredictor(data, bytesPerRow, bytesPerPixel)
            else -> data
        }
    }
    
    /**
     * PNG 预测器解码
     */
    private fun applyPngPredictor(data: ByteArray, bytesPerRow: Int, bytesPerPixel: Int): ByteArray {
        val rowSize = bytesPerRow + 1 // 每行有一个预测器类型字节
        val rows = data.size / rowSize
        if (rows == 0) return data
        
        val output = ByteArray(rows * bytesPerRow)
        val prevRow = ByteArray(bytesPerRow)
        
        for (row in 0 until rows) {
            val srcOffset = row * rowSize
            val dstOffset = row * bytesPerRow
            
            if (srcOffset >= data.size) break
            
            val filterType = data[srcOffset].toInt() and 0xFF
            
            for (col in 0 until bytesPerRow) {
                if (srcOffset + 1 + col >= data.size) break
                
                val x = data[srcOffset + 1 + col].toInt() and 0xFF
                val a = if (col >= bytesPerPixel) (output[dstOffset + col - bytesPerPixel].toInt() and 0xFF) else 0
                val b = prevRow[col].toInt() and 0xFF
                val c = if (col >= bytesPerPixel) (prevRow[col - bytesPerPixel].toInt() and 0xFF) else 0
                
                val result = when (filterType) {
                    0 -> x // None
                    1 -> (x + a) and 0xFF // Sub
                    2 -> (x + b) and 0xFF // Up
                    3 -> (x + (a + b) / 2) and 0xFF // Average
                    4 -> (x + paethPredictor(a, b, c)) and 0xFF // Paeth
                    else -> x
                }
                
                output[dstOffset + col] = result.toByte()
            }
            
            // 保存当前行用于下一行
            System.arraycopy(output, dstOffset, prevRow, 0, bytesPerRow)
        }
        
        return output
    }
    
    /**
     * Paeth 预测器
     */
    private fun paethPredictor(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }
    
    /**
     * TIFF 预测器解码
     */
    private fun applyTiffPredictor(data: ByteArray, bytesPerRow: Int, bytesPerPixel: Int): ByteArray {
        val output = data.copyOf()
        val rows = data.size / bytesPerRow
        
        for (row in 0 until rows) {
            val offset = row * bytesPerRow
            for (col in bytesPerPixel until bytesPerRow) {
                if (offset + col >= output.size) break
                val prev = output[offset + col - bytesPerPixel].toInt() and 0xFF
                val curr = output[offset + col].toInt() and 0xFF
                output[offset + col] = ((prev + curr) and 0xFF).toByte()
            }
        }
        
        return output
    }
    
    // ==================== LZWDecode ====================
    
    /**
     * LZW 解码
     * PDF 32000-1:2008 7.4.4
     */
    fun decodeLZW(data: ByteArray, params: PdfDictionary?): ByteArray {
        if (data.isEmpty()) return data
        
        val earlyChange = params?.getNumber("EarlyChange")?.toInt() ?: 1
        val output = ByteArrayOutputStream()
        
        val table = mutableListOf<ByteArray>()
        // 初始化码表 (0-255 + 256=ClearTable + 257=EOD)
        for (i in 0..257) {
            table.add(if (i < 256) byteArrayOf(i.toByte()) else ByteArray(0))
        }
        
        var codeSize = 9
        var nextCode = 258
        var prevSequence = ByteArray(0)
        
        val bitReader = BitReader(data)
        
        while (true) {
            val code = bitReader.readBits(codeSize)
            if (code < 0) break
            
            when {
                code == 256 -> {
                    // Clear Table
                    table.clear()
                    for (i in 0..257) {
                        table.add(if (i < 256) byteArrayOf(i.toByte()) else ByteArray(0))
                    }
                    codeSize = 9
                    nextCode = 258
                    prevSequence = ByteArray(0)
                }
                code == 257 -> {
                    // End of Data
                    break
                }
                else -> {
                    val sequence: ByteArray
                    
                    if (code < table.size) {
                        sequence = table[code]
                    } else if (code == nextCode && prevSequence.isNotEmpty()) {
                        // 特殊情况：新码等于下一个码
                        sequence = prevSequence + prevSequence[0]
                    } else {
                        break // 无效码
                    }
                    
                    output.write(sequence)
                    
                    if (prevSequence.isNotEmpty()) {
                        val newEntry = prevSequence + sequence[0]
                        table.add(newEntry)
                        nextCode++
                        
                        // 更新码大小
                        val limit = if (earlyChange == 1) nextCode else nextCode + 1
                        if (limit >= (1 shl codeSize) && codeSize < 12) {
                            codeSize++
                        }
                    }
                    
                    prevSequence = sequence
                }
            }
        }
        
        var result = output.toByteArray()
        
        // 应用预测器
        val predictor = params?.getInt("Predictor") ?: 1
        if (predictor > 1) {
            result = applyPredictorDecode(result, params!!)
        }
        
        return result
    }
    
    /**
     * 位读取器
     */
    private class BitReader(private val data: ByteArray) {
        private var bytePos = 0
        private var bitPos = 0
        
        fun readBits(count: Int): Int {
            if (bytePos >= data.size) return -1
            
            var result = 0
            var bitsRemaining = count
            
            while (bitsRemaining > 0) {
                if (bytePos >= data.size) return -1
                
                val bitsAvailable = 8 - bitPos
                val bitsToRead = minOf(bitsRemaining, bitsAvailable)
                
                val mask = (1 shl bitsToRead) - 1
                val shift = bitsAvailable - bitsToRead
                val bits = (data[bytePos].toInt() ushr shift) and mask
                
                result = (result shl bitsToRead) or bits
                bitsRemaining -= bitsToRead
                bitPos += bitsToRead
                
                if (bitPos >= 8) {
                    bitPos = 0
                    bytePos++
                }
            }
            
            return result
        }
    }
    
    // ==================== ASCIIHexDecode ====================
    
    /**
     * ASCIIHex 解码
     * PDF 32000-1:2008 7.4.2
     */
    fun decodeASCIIHex(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var highNibble = -1
        
        for (b in data) {
            val c = b.toInt() and 0xFF
            
            if (c == '>'.code) break // EOD marker
            
            val nibble = when (c) {
                in '0'.code..'9'.code -> c - '0'.code
                in 'A'.code..'F'.code -> c - 'A'.code + 10
                in 'a'.code..'f'.code -> c - 'a'.code + 10
                else -> continue // 跳过空白和无效字符
            }
            
            if (highNibble < 0) {
                highNibble = nibble
            } else {
                output.write((highNibble shl 4) or nibble)
                highNibble = -1
            }
        }
        
        // 如果有剩余的单个 nibble，补 0
        if (highNibble >= 0) {
            output.write(highNibble shl 4)
        }
        
        return output.toByteArray()
    }
    
    /**
     * ASCIIHex 编码
     */
    fun encodeASCIIHex(data: ByteArray): ByteArray {
        val output = StringBuilder()
        for (b in data) {
            output.append("%02X".format(b.toInt() and 0xFF))
        }
        output.append(">") // EOD marker
        return output.toString().toByteArray(Charsets.US_ASCII)
    }
    
    // ==================== ASCII85Decode ====================
    
    /**
     * ASCII85 解码
     * PDF 32000-1:2008 7.4.3
     */
    fun decodeASCII85(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        val tuple = IntArray(5)
        var tupleIndex = 0
        
        var i = 0
        while (i < data.size) {
            val c = data[i].toInt() and 0xFF
            i++
            
            // 检查 EOD
            if (c == '~'.code) {
                if (i < data.size && (data[i].toInt() and 0xFF) == '>'.code) {
                    break
                }
            }
            
            // 跳过空白
            if (c <= ' '.code) continue
            
            // 特殊 'z' 表示 4 个零字节
            if (c == 'z'.code) {
                if (tupleIndex != 0) {
                    throw StreamDecodeException("Invalid 'z' in ASCII85", "ASCII85Decode")
                }
                output.write(0)
                output.write(0)
                output.write(0)
                output.write(0)
                continue
            }
            
            if (c < '!'.code || c > 'u'.code) continue
            
            tuple[tupleIndex++] = c - '!'.code
            
            if (tupleIndex == 5) {
                // 解码 5 字节为 4 字节
                var value = 0L
                for (j in 0 until 5) {
                    value = value * 85 + tuple[j]
                }
                output.write((value shr 24).toInt() and 0xFF)
                output.write((value shr 16).toInt() and 0xFF)
                output.write((value shr 8).toInt() and 0xFF)
                output.write(value.toInt() and 0xFF)
                tupleIndex = 0
            }
        }
        
        // 处理剩余字节
        if (tupleIndex > 1) {
            // 补充 'u' (84)
            for (j in tupleIndex until 5) {
                tuple[j] = 84
            }
            var value = 0L
            for (j in 0 until 5) {
                value = value * 85 + tuple[j]
            }
            for (j in 0 until tupleIndex - 1) {
                output.write((value shr (24 - j * 8)).toInt() and 0xFF)
            }
        }
        
        return output.toByteArray()
    }
    
    /**
     * ASCII85 编码
     */
    fun encodeASCII85(data: ByteArray): ByteArray {
        val output = StringBuilder()
        var i = 0
        
        while (i < data.size) {
            val remaining = data.size - i
            
            if (remaining >= 4) {
                var value = ((data[i].toLong() and 0xFF) shl 24) or
                           ((data[i + 1].toLong() and 0xFF) shl 16) or
                           ((data[i + 2].toLong() and 0xFF) shl 8) or
                           (data[i + 3].toLong() and 0xFF)
                
                if (value == 0L) {
                    output.append('z')
                } else {
                    val encoded = CharArray(5)
                    for (j in 4 downTo 0) {
                        encoded[j] = ('!' + (value % 85).toInt())
                        value /= 85
                    }
                    output.append(encoded)
                }
                i += 4
            } else {
                // 最后不完整的组
                var value = 0L
                for (j in 0 until remaining) {
                    value = (value shl 8) or (data[i + j].toLong() and 0xFF)
                }
                value = value shl (8 * (4 - remaining))
                
                val encoded = CharArray(remaining + 1)
                for (j in (remaining) downTo 0) {
                    encoded[j] = ('!' + (value % 85).toInt())
                    value /= 85
                }
                output.append(encoded)
                i += remaining
            }
        }
        
        output.append("~>") // EOD
        return output.toString().toByteArray(Charsets.US_ASCII)
    }
    
    // ==================== RunLengthDecode ====================
    
    /**
     * RunLength 解码
     * PDF 32000-1:2008 7.4.5
     */
    fun decodeRunLength(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var i = 0
        
        while (i < data.size) {
            val length = data[i].toInt() and 0xFF
            i++
            
            when {
                length < 128 -> {
                    // 复制 length + 1 个字节
                    val count = length + 1
                    for (j in 0 until count) {
                        if (i < data.size) {
                            output.write(data[i].toInt() and 0xFF)
                            i++
                        }
                    }
                }
                length > 128 -> {
                    // 重复下一个字节 257 - length 次
                    val count = 257 - length
                    if (i < data.size) {
                        val b = data[i].toInt() and 0xFF
                        i++
                        for (j in 0 until count) {
                            output.write(b)
                        }
                    }
                }
                else -> {
                    // 128 = EOD
                    break
                }
            }
        }
        
        return output.toByteArray()
    }
}
