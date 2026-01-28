package com.pdfeditor.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pdfcore.content.ContentInstruction
import com.pdfcore.content.ContentParser
import com.pdfcore.model.*
import com.pdfcore.parser.StreamFilters
import java.io.ByteArrayOutputStream

/**
 * PDF 图像编辑器
 * 
 * 支持：
 * - 提取图像
 * - 替换图像
 * - 添加图像
 * - 删除图像
 */
class ImageEditor(private val document: PdfDocument) {
    
    private val contentParser = ContentParser()
    
    /**
     * 获取页面上的所有图像
     */
    fun getImages(page: PdfDictionary): List<ImageElement> {
        val images = mutableListOf<ImageElement>()
        val resources = document.getPageResources(page) ?: return images
        
        val xObjectDict = when (val xo = resources["XObject"]) {
            is PdfDictionary -> xo
            is PdfIndirectRef -> document.getObject(xo) as? PdfDictionary
            else -> null
        } ?: return images
        
        for ((name, value) in xObjectDict) {
            val stream = when (value) {
                is PdfStream -> value
                is PdfIndirectRef -> document.getObject(value) as? PdfStream
                else -> null
            } ?: continue
            
            val subtype = stream.dict.getNameValue("Subtype")
            if (subtype == "Image") {
                val width = stream.dict.getInt("Width") ?: continue
                val height = stream.dict.getInt("Height") ?: continue
                val bpc = stream.dict.getInt("BitsPerComponent") ?: 8
                val colorSpace = stream.dict.getNameValue("ColorSpace") ?: "DeviceRGB"
                
                // 查找图像在页面上的位置
                val position = findImagePosition(page, name)
                
                images.add(ImageElement(
                    name = name,
                    width = width,
                    height = height,
                    bitsPerComponent = bpc,
                    colorSpace = colorSpace,
                    stream = stream,
                    position = position
                ))
            }
        }
        
        return images
    }
    
    /**
     * 查找图像在页面上的位置
     */
    private fun findImagePosition(page: PdfDictionary, imageName: String): ImagePosition? {
        val contents = document.getPageContents(page)
        if (contents.isEmpty()) return null
        
        var currentMatrix = PdfMatrix.IDENTITY
        val matrixStack = mutableListOf<PdfMatrix>()
        
        for (stream in contents) {
            val instructions = contentParser.parse(stream)
            
            for (instruction in instructions) {
                when (instruction.operator) {
                    "q" -> matrixStack.add(currentMatrix)
                    "Q" -> {
                        if (matrixStack.isNotEmpty()) {
                            currentMatrix = matrixStack.removeLast()
                        }
                    }
                    "cm" -> {
                        if (instruction.operands.size >= 6) {
                            val m = PdfMatrix(
                                instruction.operands[0].asFloat(),
                                instruction.operands[1].asFloat(),
                                instruction.operands[2].asFloat(),
                                instruction.operands[3].asFloat(),
                                instruction.operands[4].asFloat(),
                                instruction.operands[5].asFloat()
                            )
                            currentMatrix = m.multiply(currentMatrix)
                        }
                    }
                    "Do" -> {
                        val name = (instruction.operands.getOrNull(0) as? PdfName)?.name
                        if (name == imageName) {
                            return ImagePosition(
                                x = currentMatrix.e,
                                y = currentMatrix.f,
                                width = currentMatrix.a,
                                height = currentMatrix.d,
                                matrix = currentMatrix
                            )
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * 提取图像为 Bitmap
     */
    fun extractImage(image: ImageElement): Bitmap? {
        val stream = image.stream
        val filters = stream.getFilters()
        
        // DCT (JPEG) 图像
        if (filters.any { it in listOf("DCTDecode", "DCT") }) {
            return BitmapFactory.decodeByteArray(stream.rawData, 0, stream.rawData.size)
        }
        
        // 解码数据
        val data = decodeImageData(stream)
        
        return when (image.colorSpace) {
            "DeviceRGB" -> createRGBBitmap(data, image.width, image.height)
            "DeviceGray" -> createGrayBitmap(data, image.width, image.height)
            "DeviceCMYK" -> createCMYKBitmap(data, image.width, image.height)
            else -> createRGBBitmap(data, image.width, image.height)
        }
    }
    
    /**
     * 替换图像
     */
    fun replaceImage(page: PdfDictionary, imageName: String, newBitmap: Bitmap): Boolean {
        val resources = document.getPageResources(page) ?: return false
        
        val xObjectDict = when (val xo = resources["XObject"]) {
            is PdfDictionary -> xo
            is PdfIndirectRef -> document.getObject(xo) as? PdfDictionary
            else -> null
        } ?: return false
        
        val existingRef = xObjectDict[imageName] as? PdfIndirectRef ?: return false
        val existingStream = document.getObject(existingRef) as? PdfStream ?: return false
        
        // 将 Bitmap 编码为 JPEG
        val baos = ByteArrayOutputStream()
        newBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val jpegData = baos.toByteArray()
        
        // 更新流
        existingStream.rawData = jpegData
        existingStream.dict["Length"] = PdfNumber(jpegData.size)
        existingStream.dict["Width"] = PdfNumber(newBitmap.width)
        existingStream.dict["Height"] = PdfNumber(newBitmap.height)
        existingStream.dict["BitsPerComponent"] = PdfNumber(8)
        existingStream.dict.setName("ColorSpace", "DeviceRGB")
        existingStream.dict.setName("Filter", "DCTDecode")
        existingStream.dict.remove("DecodeParms")
        existingStream.clearDecodedCache()
        
        document.markModified()
        return true
    }
    
    /**
     * 添加图像到页面
     */
    fun addImage(
        page: PdfDictionary,
        bitmap: Bitmap,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): Boolean {
        // 将 Bitmap 编码为 JPEG
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val jpegData = baos.toByteArray()
        
        // 创建图像流
        val imageDict = PdfDictionary()
        imageDict.setName("Type", "XObject")
        imageDict.setName("Subtype", "Image")
        imageDict["Width"] = PdfNumber(bitmap.width)
        imageDict["Height"] = PdfNumber(bitmap.height)
        imageDict["BitsPerComponent"] = PdfNumber(8)
        imageDict.setName("ColorSpace", "DeviceRGB")
        imageDict.setName("Filter", "DCTDecode")
        imageDict["Length"] = PdfNumber(jpegData.size)
        
        val imageStream = PdfStream(imageDict, jpegData)
        val imageRef = document.addObject(imageStream)
        
        // 生成唯一的图像名称
        val imageName = "Im${System.currentTimeMillis()}"
        
        // 添加到 Resources
        val resources = document.getPageResources(page) ?: PdfDictionary().also {
            page["Resources"] = document.addObject(it)
        }
        
        val xObjectDict = when (val xo = resources["XObject"]) {
            is PdfDictionary -> xo
            is PdfIndirectRef -> document.getObject(xo) as? PdfDictionary
            else -> null
        } ?: PdfDictionary().also { resources["XObject"] = it }
        
        xObjectDict[imageName] = imageRef
        
        // 添加绘制指令
        val instructions = mutableListOf<ContentInstruction>()
        
        instructions.add(ContentInstruction("q", emptyList()))
        
        // 设置变换矩阵: cm width 0 0 height x y
        instructions.add(ContentInstruction("cm", listOf(
            PdfNumber(width.toDouble()),
            PdfNumber(0),
            PdfNumber(0),
            PdfNumber(height.toDouble()),
            PdfNumber(x.toDouble()),
            PdfNumber(y.toDouble())
        )))
        
        // 绘制图像
        instructions.add(ContentInstruction("Do", listOf(PdfName(imageName))))
        
        instructions.add(ContentInstruction("Q", emptyList()))
        
        // 追加到内容流
        val newData = contentParser.serialize(instructions)
        val contents = document.getPageContents(page)
        
        if (contents.isEmpty()) {
            val newStream = PdfStream(PdfDictionary(), newData)
            newStream.dict["Length"] = PdfNumber(newData.size)
            val streamRef = document.addObject(newStream)
            page["Contents"] = streamRef
        } else {
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
     * 删除图像
     */
    fun deleteImage(page: PdfDictionary, imageName: String): Boolean {
        // 从内容流中移除绘制指令
        val contents = document.getPageContents(page)
        var deleted = false
        
        for (stream in contents) {
            val instructions = contentParser.parse(stream)
            val newInstructions = mutableListOf<ContentInstruction>()
            
            var skipUntilQ = false
            var depth = 0
            
            for (i in instructions.indices) {
                val instruction = instructions[i]
                
                if (instruction.operator == "q") {
                    depth++
                    
                    // 检查这个 q 块是否包含目标图像
                    var containsImage = false
                    var j = i + 1
                    var innerDepth = 1
                    while (j < instructions.size && innerDepth > 0) {
                        val next = instructions[j]
                        when (next.operator) {
                            "q" -> innerDepth++
                            "Q" -> innerDepth--
                            "Do" -> {
                                val name = (next.operands.getOrNull(0) as? PdfName)?.name
                                if (name == imageName && innerDepth == 1) {
                                    containsImage = true
                                }
                            }
                        }
                        j++
                    }
                    
                    if (containsImage) {
                        skipUntilQ = true
                        deleted = true
                        continue
                    }
                }
                
                if (skipUntilQ) {
                    if (instruction.operator == "Q") {
                        depth--
                        if (depth == 0) {
                            skipUntilQ = false
                        }
                    }
                    continue
                }
                
                if (instruction.operator == "Q") {
                    depth--
                }
                
                newInstructions.add(instruction)
            }
            
            if (deleted) {
                val newData = contentParser.serialize(newInstructions)
                stream.rawData = newData
                stream.dict["Length"] = PdfNumber(newData.size)
                stream.dict.remove("Filter")
                stream.clearDecodedCache()
            }
        }
        
        // 从 XObject 字典中移除
        if (deleted) {
            val resources = document.getPageResources(page)
            val xObjectDict = when (val xo = resources?.get("XObject")) {
                is PdfDictionary -> xo
                is PdfIndirectRef -> document.getObject(xo) as? PdfDictionary
                else -> null
            }
            xObjectDict?.remove(imageName)
            
            document.markModified()
        }
        
        return deleted
    }
    
    // ==================== 辅助方法 ====================
    
    private fun decodeImageData(stream: PdfStream): ByteArray {
        val filters = stream.getFilters()
        if (filters.isEmpty()) return stream.rawData
        
        var data = stream.rawData
        for ((index, filter) in filters.withIndex()) {
            // 跳过图像特定的过滤器
            if (filter in listOf("DCTDecode", "DCT", "JPXDecode")) continue
            
            val params = stream.getDecodeParams(index)
            data = StreamFilters.decode(data, filter, params)
        }
        return data
    }
    
    private fun createRGBBitmap(data: ByteArray, width: Int, height: Int): Bitmap? {
        if (data.size < width * height * 3) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        for (i in pixels.indices) {
            val offset = i * 3
            if (offset + 2 >= data.size) break
            val r = data[offset].toInt() and 0xFF
            val g = data[offset + 1].toInt() and 0xFF
            val b = data[offset + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    private fun createGrayBitmap(data: ByteArray, width: Int, height: Int): Bitmap? {
        if (data.size < width * height) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        for (i in pixels.indices) {
            if (i >= data.size) break
            val gray = data[i].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    private fun createCMYKBitmap(data: ByteArray, width: Int, height: Int): Bitmap? {
        if (data.size < width * height * 4) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        for (i in pixels.indices) {
            val offset = i * 4
            if (offset + 3 >= data.size) break
            
            val c = (data[offset].toInt() and 0xFF) / 255f
            val m = (data[offset + 1].toInt() and 0xFF) / 255f
            val y = (data[offset + 2].toInt() and 0xFF) / 255f
            val k = (data[offset + 3].toInt() and 0xFF) / 255f
            
            val r = ((1 - c) * (1 - k) * 255).toInt().coerceIn(0, 255)
            val g = ((1 - m) * (1 - k) * 255).toInt().coerceIn(0, 255)
            val b = ((1 - y) * (1 - k) * 255).toInt().coerceIn(0, 255)
            
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
    
    private fun PdfObject.asFloat(): Float = (this as? PdfNumber)?.toFloat() ?: 0f
}

/**
 * 图像元素
 */
data class ImageElement(
    val name: String,
    val width: Int,
    val height: Int,
    val bitsPerComponent: Int,
    val colorSpace: String,
    val stream: PdfStream,
    val position: ImagePosition?
)

/**
 * 图像位置
 */
data class ImagePosition(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val matrix: PdfMatrix
)
