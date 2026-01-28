package com.pdfeditor.text

import com.pdfcore.content.ContentInstruction
import com.pdfcore.content.ContentParser
import com.pdfcore.content.TextElement
import com.pdfcore.content.TextExtractor
import com.pdfcore.font.FontHandler
import com.pdfcore.font.PdfFont
import com.pdfcore.model.*

/**
 * PDF 文本编辑器
 * 
 * 实现真正的文本编辑功能：
 * - 定位文本元素
 * - 修改文本内容
 * - 替换文本
 * - 删除文本
 * - 添加文本
 */
class TextEditor(private val document: PdfDocument) {
    
    private val fontHandler = FontHandler(document)
    private val textExtractor = TextExtractor(document)
    private val contentParser = ContentParser()
    
    /**
     * 获取页面上的所有文本元素
     */
    fun getTextElements(page: PdfDictionary): List<TextElement> {
        return textExtractor.extractFromPage(page)
    }
    
    /**
     * 查找包含指定文本的元素
     */
    fun findText(page: PdfDictionary, searchText: String, ignoreCase: Boolean = false): List<TextElement> {
        val elements = getTextElements(page)
        return elements.filter { element ->
            if (ignoreCase) {
                element.text.contains(searchText, ignoreCase = true)
            } else {
                element.text.contains(searchText)
            }
        }
    }
    
    /**
     * 替换页面上的文本
     * 
     * @return 替换的数量
     */
    fun replaceText(
        page: PdfDictionary,
        searchText: String,
        replaceText: String,
        ignoreCase: Boolean = false,
        replaceAll: Boolean = true
    ): Int {
        val contents = document.getPageContents(page)
        if (contents.isEmpty()) return 0
        
        val resources = document.getPageResources(page) ?: PdfDictionary()
        val fonts = loadFonts(resources)
        
        var totalReplaced = 0
        
        // 处理每个内容流
        for (stream in contents) {
            val replaced = replaceTextInStream(
                stream, fonts, searchText, replaceText, ignoreCase, replaceAll
            )
            totalReplaced += replaced
            
            if (!replaceAll && totalReplaced > 0) break
        }
        
        if (totalReplaced > 0) {
            document.markModified()
        }
        
        return totalReplaced
    }
    
    /**
     * 在内容流中替换文本
     */
    private fun replaceTextInStream(
        stream: PdfStream,
        fonts: Map<String, PdfFont>,
        searchText: String,
        replaceText: String,
        ignoreCase: Boolean,
        replaceAll: Boolean
    ): Int {
        val instructions = contentParser.parse(stream)
        val modifiedInstructions = mutableListOf<ContentInstruction>()
        
        var replaced = 0
        var currentFont: PdfFont? = null
        var currentFontName = ""
        
        for (instruction in instructions) {
            when (instruction.operator) {
                "Tf" -> {
                    currentFontName = (instruction.operands.getOrNull(0) as? PdfName)?.name ?: ""
                    currentFont = fonts[currentFontName]
                    modifiedInstructions.add(instruction)
                }
                "Tj" -> {
                    val str = instruction.operands.getOrNull(0) as? PdfString
                    if (str != null) {
                        val newInstruction = replaceInTj(
                            instruction, str, currentFont,
                            searchText, replaceText, ignoreCase
                        )
                        if (newInstruction != instruction) {
                            replaced++
                            if (!replaceAll && replaced > 0) {
                                modifiedInstructions.add(newInstruction)
                                modifiedInstructions.addAll(instructions.subList(
                                    instructions.indexOf(instruction) + 1,
                                    instructions.size
                                ))
                                break
                            }
                        }
                        modifiedInstructions.add(newInstruction)
                    } else {
                        modifiedInstructions.add(instruction)
                    }
                }
                "TJ" -> {
                    val array = instruction.operands.getOrNull(0) as? PdfArray
                    if (array != null) {
                        val (newInstruction, count) = replaceInTJ(
                            instruction, array, currentFont,
                            searchText, replaceText, ignoreCase
                        )
                        replaced += count
                        if (!replaceAll && replaced > 0) {
                            modifiedInstructions.add(newInstruction)
                            modifiedInstructions.addAll(instructions.subList(
                                instructions.indexOf(instruction) + 1,
                                instructions.size
                            ))
                            break
                        }
                        modifiedInstructions.add(newInstruction)
                    } else {
                        modifiedInstructions.add(instruction)
                    }
                }
                else -> {
                    modifiedInstructions.add(instruction)
                }
            }
        }
        
        if (replaced > 0) {
            // 重新编码内容流
            val newData = contentParser.serialize(modifiedInstructions)
            stream.rawData = newData
            stream.dict["Length"] = PdfNumber(newData.size)
            stream.dict.remove("Filter") // 移除压缩，简化处理
            stream.clearDecodedCache()
        }
        
        return replaced
    }
    
    /**
     * 替换 Tj 操作符中的文本
     */
    private fun replaceInTj(
        instruction: ContentInstruction,
        str: PdfString,
        font: PdfFont?,
        searchText: String,
        replaceText: String,
        ignoreCase: Boolean
    ): ContentInstruction {
        val bytes = str.toBytes()
        val decodedText = font?.decode(bytes) ?: String(bytes, Charsets.ISO_8859_1)
        
        val newText = if (ignoreCase) {
            decodedText.replace(searchText, replaceText, ignoreCase = true)
        } else {
            decodedText.replace(searchText, replaceText)
        }
        
        if (newText == decodedText) return instruction
        
        // 重新编码
        val newBytes = font?.encode(newText)
        val newStr = if (newBytes != null) {
            PdfString.fromBytes(newBytes, asHex = str.isHex)
        } else {
            PdfString(newText, isHex = false)
        }
        
        return ContentInstruction(instruction.operator, listOf(newStr))
    }
    
    /**
     * 替换 TJ 操作符中的文本
     */
    private fun replaceInTJ(
        instruction: ContentInstruction,
        array: PdfArray,
        font: PdfFont?,
        searchText: String,
        replaceText: String,
        ignoreCase: Boolean
    ): Pair<ContentInstruction, Int> {
        var replaced = 0
        val newArray = PdfArray()
        
        // 首先收集所有文本
        val allText = StringBuilder()
        for (item in array) {
            if (item is PdfString) {
                val bytes = item.toBytes()
                val text = font?.decode(bytes) ?: String(bytes, Charsets.ISO_8859_1)
                allText.append(text)
            }
        }
        
        val originalText = allText.toString()
        val newText = if (ignoreCase) {
            originalText.replace(searchText, replaceText, ignoreCase = true)
        } else {
            originalText.replace(searchText, replaceText)
        }
        
        if (newText == originalText) {
            return instruction to 0
        }
        
        replaced = if (ignoreCase) {
            Regex(Regex.escape(searchText), RegexOption.IGNORE_CASE).findAll(originalText).count()
        } else {
            originalText.windowed(searchText.length) { if (it.toString() == searchText) 1 else 0 }.sum()
        }
        
        // 重新编码为单个字符串
        val newBytes = font?.encode(newText)
        val newStr = if (newBytes != null) {
            PdfString.fromBytes(newBytes, asHex = false)
        } else {
            PdfString(newText, isHex = false)
        }
        newArray.add(newStr)
        
        return ContentInstruction(instruction.operator, listOf(newArray)) to replaced
    }
    
    /**
     * 删除包含指定文本的文本块
     */
    fun deleteText(page: PdfDictionary, searchText: String, ignoreCase: Boolean = false): Int {
        val contents = document.getPageContents(page)
        if (contents.isEmpty()) return 0
        
        val resources = document.getPageResources(page) ?: PdfDictionary()
        val fonts = loadFonts(resources)
        
        var totalDeleted = 0
        
        for (stream in contents) {
            totalDeleted += deleteTextInStream(stream, fonts, searchText, ignoreCase)
        }
        
        if (totalDeleted > 0) {
            document.markModified()
        }
        
        return totalDeleted
    }
    
    private fun deleteTextInStream(
        stream: PdfStream,
        fonts: Map<String, PdfFont>,
        searchText: String,
        ignoreCase: Boolean
    ): Int {
        val instructions = contentParser.parse(stream)
        val modifiedInstructions = mutableListOf<ContentInstruction>()
        
        var deleted = 0
        var currentFont: PdfFont? = null
        var skipUntilET = false
        
        for (instruction in instructions) {
            if (skipUntilET) {
                if (instruction.operator == "ET") {
                    skipUntilET = false
                }
                continue
            }
            
            when (instruction.operator) {
                "Tf" -> {
                    val fontName = (instruction.operands.getOrNull(0) as? PdfName)?.name ?: ""
                    currentFont = fonts[fontName]
                    modifiedInstructions.add(instruction)
                }
                "Tj", "'", "\"" -> {
                    val str = when (instruction.operator) {
                        "Tj" -> instruction.operands.getOrNull(0) as? PdfString
                        "'" -> instruction.operands.getOrNull(0) as? PdfString
                        "\"" -> instruction.operands.getOrNull(2) as? PdfString
                        else -> null
                    }
                    
                    if (str != null) {
                        val bytes = str.toBytes()
                        val text = currentFont?.decode(bytes) ?: String(bytes, Charsets.ISO_8859_1)
                        
                        val contains = if (ignoreCase) {
                            text.contains(searchText, ignoreCase = true)
                        } else {
                            text.contains(searchText)
                        }
                        
                        if (contains) {
                            deleted++
                            // 跳过这个文本操作
                            continue
                        }
                    }
                    modifiedInstructions.add(instruction)
                }
                "TJ" -> {
                    val array = instruction.operands.getOrNull(0) as? PdfArray
                    if (array != null) {
                        val allText = StringBuilder()
                        for (item in array) {
                            if (item is PdfString) {
                                val bytes = item.toBytes()
                                val text = currentFont?.decode(bytes) ?: String(bytes, Charsets.ISO_8859_1)
                                allText.append(text)
                            }
                        }
                        
                        val contains = if (ignoreCase) {
                            allText.contains(searchText, ignoreCase = true)
                        } else {
                            allText.contains(searchText)
                        }
                        
                        if (contains) {
                            deleted++
                            continue
                        }
                    }
                    modifiedInstructions.add(instruction)
                }
                else -> {
                    modifiedInstructions.add(instruction)
                }
            }
        }
        
        if (deleted > 0) {
            val newData = contentParser.serialize(modifiedInstructions)
            stream.rawData = newData
            stream.dict["Length"] = PdfNumber(newData.size)
            stream.dict.remove("Filter")
            stream.clearDecodedCache()
        }
        
        return deleted
    }
    
    /**
     * 在指定位置添加文本
     */
    fun addText(
        page: PdfDictionary,
        text: String,
        x: Float,
        y: Float,
        fontName: String,
        fontSize: Float,
        color: Triple<Float, Float, Float>? = null // RGB, 0-1
    ): Boolean {
        val contents = document.getPageContents(page)
        
        // 构建新的文本指令
        val newInstructions = mutableListOf<ContentInstruction>()
        
        // 保存图形状态
        newInstructions.add(ContentInstruction("q", emptyList()))
        
        // 设置颜色
        if (color != null) {
            newInstructions.add(ContentInstruction("rg", listOf(
                PdfNumber(color.first.toDouble()),
                PdfNumber(color.second.toDouble()),
                PdfNumber(color.third.toDouble())
            )))
        }
        
        // 开始文本对象
        newInstructions.add(ContentInstruction("BT", emptyList()))
        
        // 设置字体
        newInstructions.add(ContentInstruction("Tf", listOf(
            PdfName(fontName),
            PdfNumber(fontSize.toDouble())
        )))
        
        // 设置位置
        newInstructions.add(ContentInstruction("Td", listOf(
            PdfNumber(x.toDouble()),
            PdfNumber(y.toDouble())
        )))
        
        // 显示文本
        newInstructions.add(ContentInstruction("Tj", listOf(
            PdfString(text, isHex = false)
        )))
        
        // 结束文本对象
        newInstructions.add(ContentInstruction("ET", emptyList()))
        
        // 恢复图形状态
        newInstructions.add(ContentInstruction("Q", emptyList()))
        
        // 序列化新指令
        val newData = contentParser.serialize(newInstructions)
        
        if (contents.isEmpty()) {
            // 创建新的内容流
            val newStream = PdfStream(PdfDictionary(), newData)
            newStream.dict["Length"] = PdfNumber(newData.size)
            
            val streamRef = document.addObject(newStream)
            page["Contents"] = streamRef
        } else {
            // 追加到现有内容流
            val lastStream = contents.last()
            val existingData = lastStream.rawData
            val combinedData = existingData + "\n".toByteArray() + newData
            
            lastStream.rawData = combinedData
            lastStream.dict["Length"] = PdfNumber(combinedData.size)
            lastStream.dict.remove("Filter")
            lastStream.clearDecodedCache()
        }
        
        document.markModified()
        return true
    }
    
    /**
     * 修改文本元素的内容
     */
    fun modifyTextElement(
        page: PdfDictionary,
        element: TextElement,
        newText: String
    ): Boolean {
        val contents = document.getPageContents(page)
        if (contents.isEmpty()) return false
        
        val resources = document.getPageResources(page) ?: PdfDictionary()
        val fonts = loadFonts(resources)
        
        for (stream in contents) {
            if (modifyTextInStream(stream, fonts, element, newText)) {
                document.markModified()
                return true
            }
        }
        
        return false
    }
    
    private fun modifyTextInStream(
        stream: PdfStream,
        fonts: Map<String, PdfFont>,
        element: TextElement,
        newText: String
    ): Boolean {
        val instructions = contentParser.parse(stream)
        val modifiedInstructions = mutableListOf<ContentInstruction>()
        
        var found = false
        var currentFont: PdfFont? = null
        var currentFontName = ""
        
        for (instruction in instructions) {
            if (found) {
                modifiedInstructions.add(instruction)
                continue
            }
            
            when (instruction.operator) {
                "Tf" -> {
                    currentFontName = (instruction.operands.getOrNull(0) as? PdfName)?.name ?: ""
                    currentFont = fonts[currentFontName]
                    modifiedInstructions.add(instruction)
                }
                "Tj" -> {
                    val str = instruction.operands.getOrNull(0) as? PdfString
                    if (str != null && currentFontName == element.fontName) {
                        val bytes = str.toBytes()
                        val text = currentFont?.decode(bytes) ?: String(bytes, Charsets.ISO_8859_1)
                        
                        if (text == element.text) {
                            val newBytes = currentFont?.encode(newText)
                            val newStr = if (newBytes != null) {
                                PdfString.fromBytes(newBytes, asHex = str.isHex)
                            } else {
                                PdfString(newText, isHex = false)
                            }
                            modifiedInstructions.add(ContentInstruction("Tj", listOf(newStr)))
                            found = true
                            continue
                        }
                    }
                    modifiedInstructions.add(instruction)
                }
                else -> {
                    modifiedInstructions.add(instruction)
                }
            }
        }
        
        if (found) {
            val newData = contentParser.serialize(modifiedInstructions)
            stream.rawData = newData
            stream.dict["Length"] = PdfNumber(newData.size)
            stream.dict.remove("Filter")
            stream.clearDecodedCache()
        }
        
        return found
    }
    
    /**
     * 加载字体
     */
    private fun loadFonts(resources: PdfDictionary): Map<String, PdfFont> {
        val fonts = mutableMapOf<String, PdfFont>()
        
        val fontDict = when (val f = resources["Font"]) {
            is PdfDictionary -> f
            is PdfIndirectRef -> document.getObject(f) as? PdfDictionary
            else -> null
        } ?: return fonts
        
        for ((name, value) in fontDict) {
            val font = when (value) {
                is PdfDictionary -> fontHandler.getFont(value)
                is PdfIndirectRef -> {
                    val dict = document.getObject(value) as? PdfDictionary
                    dict?.let { fontHandler.getFont(it) }
                }
                else -> null
            }
            font?.let { fonts[name] = it }
        }
        
        return fonts
    }
}

/**
 * 文本编辑操作
 */
sealed class TextEditOperation {
    data class Replace(
        val pageIndex: Int,
        val searchText: String,
        val replaceText: String,
        val ignoreCase: Boolean = false
    ) : TextEditOperation()
    
    data class Delete(
        val pageIndex: Int,
        val searchText: String,
        val ignoreCase: Boolean = false
    ) : TextEditOperation()
    
    data class Add(
        val pageIndex: Int,
        val text: String,
        val x: Float,
        val y: Float,
        val fontName: String,
        val fontSize: Float,
        val color: Triple<Float, Float, Float>? = null
    ) : TextEditOperation()
    
    data class Modify(
        val pageIndex: Int,
        val element: TextElement,
        val newText: String
    ) : TextEditOperation()
}
