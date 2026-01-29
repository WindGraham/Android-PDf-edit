package com.pdfcore.codec

import com.pdfcore.model.PdfDictionary
import java.io.ByteArrayOutputStream

/**
 * CCITT Fax 解码器
 * 基于 PDF 32000-1:2008 标准 7.4.6 节
 * 
 * 支持：
 * - Group 3 一维编码 (Modified Huffman, MH)
 * - Group 3 二维编码 (Modified READ, MR)
 * - Group 4 编码 (Modified Modified READ, MMR)
 */
class CCITTFaxDecoder(private val params: PdfDictionary?) {
    
    // 参数
    private val k: Int = params?.getInt("K") ?: 0
    private val columns: Int = params?.getInt("Columns") ?: 1728
    private val rows: Int = params?.getInt("Rows") ?: 0
    private val endOfLine: Boolean = params?.getBoolean("EndOfLine")?.value ?: false
    private val encodedByteAlign: Boolean = params?.getBoolean("EncodedByteAlign")?.value ?: false
    private val endOfBlock: Boolean = params?.getBoolean("EndOfBlock")?.value ?: true
    private val blackIs1: Boolean = params?.getBoolean("BlackIs1")?.value ?: false
    private val damagedRowsBeforeError: Int = params?.getInt("DamagedRowsBeforeError") ?: 0
    
    // 位读取器状态
    private var data: ByteArray = ByteArray(0)
    private var bytePos: Int = 0
    private var bitPos: Int = 0
    
    // 当前行和参考行
    private var currentLine: IntArray = IntArray(0)
    private var referenceLine: IntArray = IntArray(0)
    
    /**
     * 解码 CCITT 压缩数据
     */
    fun decode(encodedData: ByteArray): ByteArray {
        if (encodedData.isEmpty()) return encodedData
        
        data = encodedData
        bytePos = 0
        bitPos = 0
        
        val output = ByteArrayOutputStream()
        currentLine = IntArray(columns)
        referenceLine = IntArray(columns) { 0 } // 初始参考行全白
        
        var rowCount = 0
        var damagedRows = 0
        
        while (true) {
            // 检查是否达到行数限制
            if (rows > 0 && rowCount >= rows) break
            
            // 检查是否到达数据末尾
            if (bytePos >= data.size) break
            
            try {
                // 如果需要字节对齐
                if (encodedByteAlign && bitPos != 0) {
                    bitPos = 0
                    bytePos++
                }
                
                // 检查 EOL
                if (endOfLine) {
                    skipEOL()
                }
                
                // 检查 EOB (RTC for Group 3, EOFB for Group 4)
                if (endOfBlock && checkEndOfBlock()) {
                    break
                }
                
                // 解码一行
                val success = when {
                    k < 0 -> decodeGroup4Line() // Group 4 (MMR)
                    k == 0 -> decodeGroup3_1DLine() // Group 3 1D (MH)
                    else -> decodeGroup3_2DLine(rowCount) // Group 3 2D (MR)
                }
                
                if (!success) {
                    damagedRows++
                    if (damagedRows > damagedRowsBeforeError) {
                        break
                    }
                    // 重置当前行
                    currentLine.fill(0)
                } else {
                    damagedRows = 0
                }
                
                // 输出当前行
                outputLine(output)
                
                // 当前行变成参考行
                val temp = referenceLine
                referenceLine = currentLine
                currentLine = temp
                currentLine.fill(0)
                
                rowCount++
                
            } catch (e: Exception) {
                // 解码错误，尝试继续
                damagedRows++
                if (damagedRows > damagedRowsBeforeError) {
                    break
                }
            }
        }
        
        return output.toByteArray()
    }
    
    /**
     * 跳过 EOL 标记
     */
    private fun skipEOL() {
        // EOL 是 11 个 0 后跟 1 个 1
        var zeros = 0
        while (peekBit() == 0 && zeros < 12) {
            readBit()
            zeros++
        }
        if (peekBit() == 1) {
            readBit()
        }
    }
    
    /**
     * 检查是否到达块结束
     */
    private fun checkEndOfBlock(): Boolean {
        val savedBytePos = bytePos
        val savedBitPos = bitPos
        
        return if (k < 0) {
            // Group 4 EOFB: 两个连续的 EOL
            val isEOFB = checkEOL() && checkEOL()
            if (!isEOFB) {
                bytePos = savedBytePos
                bitPos = savedBitPos
            }
            isEOFB
        } else {
            // Group 3 RTC: 6 个连续的 EOL
            var count = 0
            for (i in 0 until 6) {
                if (checkEOL()) count++ else break
            }
            if (count < 6) {
                bytePos = savedBytePos
                bitPos = savedBitPos
            }
            count >= 6
        }
    }
    
    /**
     * 检查单个 EOL
     */
    private fun checkEOL(): Boolean {
        var zeros = 0
        while (bytePos < data.size && zeros < 11) {
            if (readBit() == 0) zeros++ else return false
        }
        return bytePos < data.size && readBit() == 1
    }
    
    /**
     * Group 3 一维解码 (Modified Huffman)
     */
    private fun decodeGroup3_1DLine(): Boolean {
        var x = 0
        var isWhite = true
        
        while (x < columns) {
            val runLength = if (isWhite) {
                decodeWhiteRun()
            } else {
                decodeBlackRun()
            }
            
            if (runLength < 0) return false
            
            // 填充像素
            val endX = minOf(x + runLength, columns)
            val pixelValue = if (isWhite) 0 else 1
            for (i in x until endX) {
                currentLine[i] = pixelValue
            }
            
            x = endX
            isWhite = !isWhite
        }
        
        return true
    }
    
    /**
     * Group 3 二维解码 (Modified READ)
     */
    private fun decodeGroup3_2DLine(rowIndex: Int): Boolean {
        // 每 k 行使用一次一维编码
        if (rowIndex % k == 0) {
            return decodeGroup3_1DLine()
        }
        return decodeGroup4Line()
    }
    
    /**
     * Group 4 解码 (Modified Modified READ)
     */
    private fun decodeGroup4Line(): Boolean {
        var a0 = -1 // 当前位置，-1 表示行开始
        var isWhiteRun = true // 当前运行是否为白色
        
        while (a0 < columns) {
            val code = decode2DCode()
            
            when (code) {
                CODE_PASS -> {
                    // Pass 模式
                    val b1 = findB1(a0, isWhiteRun)
                    val b2 = findB2(b1)
                    a0 = b2
                }
                CODE_HORIZONTAL -> {
                    // Horizontal 模式
                    val run1 = if (isWhiteRun) decodeWhiteRun() else decodeBlackRun()
                    if (run1 < 0) return false
                    
                    val run2 = if (isWhiteRun) decodeBlackRun() else decodeWhiteRun()
                    if (run2 < 0) return false
                    
                    val start = if (a0 < 0) 0 else a0
                    fillRun(start, run1, isWhiteRun)
                    fillRun(start + run1, run2, !isWhiteRun)
                    
                    a0 = start + run1 + run2
                }
                CODE_V0, CODE_VR1, CODE_VR2, CODE_VR3, CODE_VL1, CODE_VL2, CODE_VL3 -> {
                    // Vertical 模式
                    val b1 = findB1(a0, isWhiteRun)
                    val a1 = when (code) {
                        CODE_V0 -> b1
                        CODE_VR1 -> b1 + 1
                        CODE_VR2 -> b1 + 2
                        CODE_VR3 -> b1 + 3
                        CODE_VL1 -> b1 - 1
                        CODE_VL2 -> b1 - 2
                        CODE_VL3 -> b1 - 3
                        else -> b1
                    }
                    
                    val start = if (a0 < 0) 0 else a0
                    val runLength = a1 - start
                    if (runLength > 0) {
                        fillRun(start, runLength, isWhiteRun)
                    }
                    
                    a0 = a1
                    isWhiteRun = !isWhiteRun
                }
                else -> return false
            }
        }
        
        return true
    }
    
    /**
     * 查找 b1 位置（参考行中的变化点）
     */
    private fun findB1(a0: Int, isWhiteRun: Boolean): Int {
        val start = if (a0 < 0) 0 else a0 + 1
        val targetColor = if (isWhiteRun) 1 else 0
        
        // 找到参考行中颜色不同的位置
        var x = start
        while (x < columns && referenceLine[x] != targetColor) {
            x++
        }
        // 然后找到下一个变化点
        while (x < columns && referenceLine[x] == targetColor) {
            x++
        }
        
        return x
    }
    
    /**
     * 查找 b2 位置
     */
    private fun findB2(b1: Int): Int {
        if (b1 >= columns) return columns
        
        val b1Color = if (b1 < columns) referenceLine[b1] else 0
        var x = b1 + 1
        while (x < columns && referenceLine[x] == b1Color) {
            x++
        }
        return x
    }
    
    /**
     * 填充运行
     */
    private fun fillRun(start: Int, length: Int, isWhite: Boolean) {
        val pixelValue = if (isWhite) 0 else 1
        val end = minOf(start + length, columns)
        for (i in start until end) {
            currentLine[i] = pixelValue
        }
    }
    
    /**
     * 解码白色运行长度
     */
    private fun decodeWhiteRun(): Int {
        var totalRun = 0
        
        // 先解码 makeup 码
        while (true) {
            val makeupRun = decodeWhiteMakeup()
            if (makeupRun < 0) break
            totalRun += makeupRun
        }
        
        // 解码终止码
        val termRun = decodeWhiteTerminating()
        if (termRun < 0) return -1
        
        return totalRun + termRun
    }
    
    /**
     * 解码黑色运行长度
     */
    private fun decodeBlackRun(): Int {
        var totalRun = 0
        
        // 先解码 makeup 码
        while (true) {
            val makeupRun = decodeBlackMakeup()
            if (makeupRun < 0) break
            totalRun += makeupRun
        }
        
        // 解码终止码
        val termRun = decodeBlackTerminating()
        if (termRun < 0) return -1
        
        return totalRun + termRun
    }
    
    /**
     * 解码白色终止码
     */
    private fun decodeWhiteTerminating(): Int {
        return decodeFromTable(WHITE_TERMINATING_CODES)
    }
    
    /**
     * 解码白色 makeup 码
     */
    private fun decodeWhiteMakeup(): Int {
        return decodeFromTable(WHITE_MAKEUP_CODES)
    }
    
    /**
     * 解码黑色终止码
     */
    private fun decodeBlackTerminating(): Int {
        return decodeFromTable(BLACK_TERMINATING_CODES)
    }
    
    /**
     * 解码黑色 makeup 码
     */
    private fun decodeBlackMakeup(): Int {
        return decodeFromTable(BLACK_MAKEUP_CODES)
    }
    
    /**
     * 从码表解码
     */
    private fun decodeFromTable(table: Array<IntArray>): Int {
        val savedBytePos = bytePos
        val savedBitPos = bitPos
        
        var code = 0
        var codeLen = 0
        
        for (entry in table) {
            val entryLen = entry[0]
            val entryCode = entry[1]
            val runLength = entry[2]
            
            // 读取所需位数
            while (codeLen < entryLen && bytePos < data.size) {
                code = (code shl 1) or readBit()
                codeLen++
            }
            
            if (codeLen == entryLen && code == entryCode) {
                return runLength
            }
            
            // 如果当前码比表项短或者不匹配，恢复位置继续尝试
            if (codeLen < entryLen) {
                bytePos = savedBytePos
                bitPos = savedBitPos
                code = 0
                codeLen = 0
            }
        }
        
        // 没有匹配，恢复位置
        bytePos = savedBytePos
        bitPos = savedBitPos
        return -1
    }
    
    /**
     * 解码 2D 模式码
     */
    private fun decode2DCode(): Int {
        // 先尝试匹配最常见的码
        val bit1 = readBit()
        
        if (bit1 == 1) {
            return CODE_V0 // 1 -> V(0)
        }
        
        val bit2 = readBit()
        val bit3 = readBit()
        
        when {
            bit2 == 0 && bit3 == 1 -> return CODE_HORIZONTAL // 001 -> Horizontal
            bit2 == 1 && bit3 == 0 -> return CODE_VL1 // 010 -> VL(1)
            bit2 == 1 && bit3 == 1 -> return CODE_VR1 // 011 -> VR(1)
        }
        
        val bit4 = readBit()
        
        if (bit2 == 0 && bit3 == 0 && bit4 == 1) {
            return CODE_PASS // 0001 -> Pass
        }
        
        val bit5 = readBit()
        val bit6 = readBit()
        
        when {
            bit4 == 0 && bit5 == 1 && bit6 == 0 -> return CODE_VL2 // 000010 -> VL(2)
            bit4 == 0 && bit5 == 1 && bit6 == 1 -> return CODE_VR2 // 000011 -> VR(2)
        }
        
        val bit7 = readBit()
        
        when {
            bit5 == 0 && bit6 == 1 && bit7 == 0 -> return CODE_VL3 // 0000010 -> VL(3)
            bit5 == 0 && bit6 == 1 && bit7 == 1 -> return CODE_VR3 // 0000011 -> VR(3)
        }
        
        return -1
    }
    
    /**
     * 读取一位
     */
    private fun readBit(): Int {
        if (bytePos >= data.size) return 0
        
        val bit = (data[bytePos].toInt() shr (7 - bitPos)) and 1
        bitPos++
        if (bitPos >= 8) {
            bitPos = 0
            bytePos++
        }
        return bit
    }
    
    /**
     * 查看下一位但不消费
     */
    private fun peekBit(): Int {
        if (bytePos >= data.size) return 0
        return (data[bytePos].toInt() shr (7 - bitPos)) and 1
    }
    
    /**
     * 输出一行
     */
    private fun outputLine(output: ByteArrayOutputStream) {
        val bytesPerRow = (columns + 7) / 8
        val rowData = ByteArray(bytesPerRow)
        
        for (x in 0 until columns) {
            val byteIndex = x / 8
            val bitIndex = 7 - (x % 8)
            
            val pixelValue = if (blackIs1) {
                currentLine[x]
            } else {
                1 - currentLine[x] // 反转：白色=1，黑色=0
            }
            
            if (pixelValue == 1) {
                rowData[byteIndex] = (rowData[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        
        output.write(rowData)
    }
    
    companion object {
        // 2D 模式码
        private const val CODE_PASS = 0
        private const val CODE_HORIZONTAL = 1
        private const val CODE_V0 = 2
        private const val CODE_VR1 = 3
        private const val CODE_VR2 = 4
        private const val CODE_VR3 = 5
        private const val CODE_VL1 = 6
        private const val CODE_VL2 = 7
        private const val CODE_VL3 = 8
        
        // 白色终止码表 [码长, 码值, 运行长度]
        private val WHITE_TERMINATING_CODES = arrayOf(
            intArrayOf(4, 0b0111, 2),
            intArrayOf(4, 0b1000, 3),
            intArrayOf(4, 0b1011, 4),
            intArrayOf(4, 0b1100, 5),
            intArrayOf(4, 0b1110, 6),
            intArrayOf(4, 0b1111, 7),
            intArrayOf(5, 0b10011, 8),
            intArrayOf(5, 0b10100, 9),
            intArrayOf(5, 0b00111, 10),
            intArrayOf(5, 0b01000, 11),
            intArrayOf(6, 0b001000, 12),
            intArrayOf(6, 0b000011, 13),
            intArrayOf(6, 0b110100, 14),
            intArrayOf(6, 0b110101, 15),
            intArrayOf(6, 0b101010, 16),
            intArrayOf(6, 0b101011, 17),
            intArrayOf(7, 0b0100111, 18),
            intArrayOf(7, 0b0001100, 19),
            intArrayOf(7, 0b0001000, 20),
            intArrayOf(7, 0b0010111, 21),
            intArrayOf(7, 0b0000011, 22),
            intArrayOf(7, 0b0000100, 23),
            intArrayOf(7, 0b0101000, 24),
            intArrayOf(7, 0b0101011, 25),
            intArrayOf(7, 0b0010011, 26),
            intArrayOf(7, 0b0100100, 27),
            intArrayOf(7, 0b0011000, 28),
            intArrayOf(8, 0b00000010, 29),
            intArrayOf(8, 0b00000011, 30),
            intArrayOf(8, 0b00011010, 31),
            intArrayOf(8, 0b00011011, 32),
            intArrayOf(8, 0b00010010, 33),
            intArrayOf(8, 0b00010011, 34),
            intArrayOf(8, 0b00010100, 35),
            intArrayOf(8, 0b00010101, 36),
            intArrayOf(8, 0b00010110, 37),
            intArrayOf(8, 0b00010111, 38),
            intArrayOf(8, 0b00101000, 39),
            intArrayOf(8, 0b00101001, 40),
            intArrayOf(8, 0b00101010, 41),
            intArrayOf(8, 0b00101011, 42),
            intArrayOf(8, 0b00101100, 43),
            intArrayOf(8, 0b00101101, 44),
            intArrayOf(8, 0b00000100, 45),
            intArrayOf(8, 0b00000101, 46),
            intArrayOf(8, 0b00001010, 47),
            intArrayOf(8, 0b00001011, 48),
            intArrayOf(8, 0b01010010, 49),
            intArrayOf(8, 0b01010011, 50),
            intArrayOf(8, 0b01010100, 51),
            intArrayOf(8, 0b01010101, 52),
            intArrayOf(8, 0b00100100, 53),
            intArrayOf(8, 0b00100101, 54),
            intArrayOf(8, 0b01011000, 55),
            intArrayOf(8, 0b01011001, 56),
            intArrayOf(8, 0b01011010, 57),
            intArrayOf(8, 0b01011011, 58),
            intArrayOf(8, 0b01001010, 59),
            intArrayOf(8, 0b01001011, 60),
            intArrayOf(8, 0b00110010, 61),
            intArrayOf(8, 0b00110011, 62),
            intArrayOf(8, 0b00110100, 63),
            intArrayOf(6, 0b110111, 0),
            intArrayOf(4, 0b1001, 1)
        )
        
        // 白色 makeup 码表
        private val WHITE_MAKEUP_CODES = arrayOf(
            intArrayOf(5, 0b11011, 64),
            intArrayOf(5, 0b10010, 128),
            intArrayOf(6, 0b010111, 192),
            intArrayOf(7, 0b0110111, 256),
            intArrayOf(8, 0b00110110, 320),
            intArrayOf(8, 0b00110111, 384),
            intArrayOf(8, 0b01100100, 448),
            intArrayOf(8, 0b01100101, 512),
            intArrayOf(8, 0b01101000, 576),
            intArrayOf(8, 0b01100111, 640),
            intArrayOf(9, 0b011001100, 704),
            intArrayOf(9, 0b011001101, 768),
            intArrayOf(9, 0b011010010, 832),
            intArrayOf(9, 0b011010011, 896),
            intArrayOf(9, 0b011010100, 960),
            intArrayOf(9, 0b011010101, 1024),
            intArrayOf(9, 0b011010110, 1088),
            intArrayOf(9, 0b011010111, 1152),
            intArrayOf(9, 0b011011000, 1216),
            intArrayOf(9, 0b011011001, 1280),
            intArrayOf(9, 0b011011010, 1344),
            intArrayOf(9, 0b011011011, 1408),
            intArrayOf(9, 0b010011000, 1472),
            intArrayOf(9, 0b010011001, 1536),
            intArrayOf(9, 0b010011010, 1600),
            intArrayOf(6, 0b011000, 1664),
            intArrayOf(9, 0b010011011, 1728),
            intArrayOf(11, 0b00000001000, 1792),
            intArrayOf(11, 0b00000001100, 1856),
            intArrayOf(11, 0b00000001101, 1920),
            intArrayOf(12, 0b000000010010, 1984),
            intArrayOf(12, 0b000000010011, 2048),
            intArrayOf(12, 0b000000010100, 2112),
            intArrayOf(12, 0b000000010101, 2176),
            intArrayOf(12, 0b000000010110, 2240),
            intArrayOf(12, 0b000000010111, 2304),
            intArrayOf(12, 0b000000011100, 2368),
            intArrayOf(12, 0b000000011101, 2432),
            intArrayOf(12, 0b000000011110, 2496),
            intArrayOf(12, 0b000000011111, 2560)
        )
        
        // 黑色终止码表
        private val BLACK_TERMINATING_CODES = arrayOf(
            intArrayOf(2, 0b11, 2),
            intArrayOf(3, 0b10, 3),
            intArrayOf(4, 0b011, 4),
            intArrayOf(4, 0b0011, 5),
            intArrayOf(4, 0b0010, 6),
            intArrayOf(5, 0b00011, 7),
            intArrayOf(6, 0b000101, 8),
            intArrayOf(6, 0b000100, 9),
            intArrayOf(7, 0b0000100, 10),
            intArrayOf(7, 0b0000101, 11),
            intArrayOf(7, 0b0000111, 12),
            intArrayOf(8, 0b00000100, 13),
            intArrayOf(8, 0b00000111, 14),
            intArrayOf(9, 0b000011000, 15),
            intArrayOf(10, 0b0000010111, 16),
            intArrayOf(10, 0b0000011000, 17),
            intArrayOf(10, 0b0000001000, 18),
            intArrayOf(11, 0b00001100111, 19),
            intArrayOf(11, 0b00001101000, 20),
            intArrayOf(11, 0b00001101100, 21),
            intArrayOf(11, 0b00000110111, 22),
            intArrayOf(11, 0b00000101000, 23),
            intArrayOf(11, 0b00000010111, 24),
            intArrayOf(11, 0b00000011000, 25),
            intArrayOf(12, 0b000011001010, 26),
            intArrayOf(12, 0b000011001011, 27),
            intArrayOf(12, 0b000011001100, 28),
            intArrayOf(12, 0b000011001101, 29),
            intArrayOf(12, 0b000001101000, 30),
            intArrayOf(12, 0b000001101001, 31),
            intArrayOf(12, 0b000001101010, 32),
            intArrayOf(12, 0b000001101011, 33),
            intArrayOf(12, 0b000011010010, 34),
            intArrayOf(12, 0b000011010011, 35),
            intArrayOf(12, 0b000011010100, 36),
            intArrayOf(12, 0b000011010101, 37),
            intArrayOf(12, 0b000011010110, 38),
            intArrayOf(12, 0b000011010111, 39),
            intArrayOf(12, 0b000001101100, 40),
            intArrayOf(12, 0b000001101101, 41),
            intArrayOf(12, 0b000011011010, 42),
            intArrayOf(12, 0b000011011011, 43),
            intArrayOf(12, 0b000001010100, 44),
            intArrayOf(12, 0b000001010101, 45),
            intArrayOf(12, 0b000001010110, 46),
            intArrayOf(12, 0b000001010111, 47),
            intArrayOf(12, 0b000001100100, 48),
            intArrayOf(12, 0b000001100101, 49),
            intArrayOf(12, 0b000001010010, 50),
            intArrayOf(12, 0b000001010011, 51),
            intArrayOf(12, 0b000000100100, 52),
            intArrayOf(12, 0b000000110111, 53),
            intArrayOf(12, 0b000000111000, 54),
            intArrayOf(12, 0b000000100111, 55),
            intArrayOf(12, 0b000000101000, 56),
            intArrayOf(12, 0b000001011000, 57),
            intArrayOf(12, 0b000001011001, 58),
            intArrayOf(12, 0b000000101011, 59),
            intArrayOf(12, 0b000000101100, 60),
            intArrayOf(12, 0b000001011010, 61),
            intArrayOf(12, 0b000001100110, 62),
            intArrayOf(12, 0b000001100111, 63),
            intArrayOf(10, 0b0000110111, 0),
            intArrayOf(3, 0b010, 1)
        )
        
        // 黑色 makeup 码表
        private val BLACK_MAKEUP_CODES = arrayOf(
            intArrayOf(10, 0b0000001111, 64),
            intArrayOf(12, 0b000011001000, 128),
            intArrayOf(12, 0b000011001001, 192),
            intArrayOf(12, 0b000001011011, 256),
            intArrayOf(12, 0b000000110011, 320),
            intArrayOf(12, 0b000000110100, 384),
            intArrayOf(12, 0b000000110101, 448),
            intArrayOf(13, 0b0000001101100, 512),
            intArrayOf(13, 0b0000001101101, 576),
            intArrayOf(13, 0b0000001001010, 640),
            intArrayOf(13, 0b0000001001011, 704),
            intArrayOf(13, 0b0000001001100, 768),
            intArrayOf(13, 0b0000001001101, 832),
            intArrayOf(13, 0b0000001110010, 896),
            intArrayOf(13, 0b0000001110011, 960),
            intArrayOf(13, 0b0000001110100, 1024),
            intArrayOf(13, 0b0000001110101, 1088),
            intArrayOf(13, 0b0000001110110, 1152),
            intArrayOf(13, 0b0000001110111, 1216),
            intArrayOf(13, 0b0000001010010, 1280),
            intArrayOf(13, 0b0000001010011, 1344),
            intArrayOf(13, 0b0000001010100, 1408),
            intArrayOf(13, 0b0000001010101, 1472),
            intArrayOf(13, 0b0000001011010, 1536),
            intArrayOf(13, 0b0000001011011, 1600),
            intArrayOf(13, 0b0000001100100, 1664),
            intArrayOf(13, 0b0000001100101, 1728),
            intArrayOf(11, 0b00000001000, 1792),
            intArrayOf(11, 0b00000001100, 1856),
            intArrayOf(11, 0b00000001101, 1920),
            intArrayOf(12, 0b000000010010, 1984),
            intArrayOf(12, 0b000000010011, 2048),
            intArrayOf(12, 0b000000010100, 2112),
            intArrayOf(12, 0b000000010101, 2176),
            intArrayOf(12, 0b000000010110, 2240),
            intArrayOf(12, 0b000000010111, 2304),
            intArrayOf(12, 0b000000011100, 2368),
            intArrayOf(12, 0b000000011101, 2432),
            intArrayOf(12, 0b000000011110, 2496),
            intArrayOf(12, 0b000000011111, 2560)
        )
    }
}
