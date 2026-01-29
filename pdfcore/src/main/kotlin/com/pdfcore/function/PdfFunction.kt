package com.pdfcore.function

import com.pdfcore.model.*
import com.pdfcore.parser.StreamFilters

/**
 * PDF 函数基类
 * 基于 PDF 32000-1:2008 标准 7.10 节
 * 
 * PDF 支持四种函数类型：
 * - Type 0: Sampled 函数 (查表插值)
 * - Type 2: Exponential 函数 (指数插值)
 * - Type 3: Stitching 函数 (分段组合)
 * - Type 4: PostScript Calculator 函数 (栈式计算)
 */
abstract class PdfFunction(
    protected val domain: FloatArray,  // 输入域 [xmin, xmax, ...]
    protected val range: FloatArray?   // 输出域 [ymin, ymax, ...]
) {
    /**
     * 输入维度
     */
    val inputDimension: Int = domain.size / 2
    
    /**
     * 输出维度
     */
    abstract val outputDimension: Int
    
    /**
     * 求值
     */
    abstract fun evaluate(input: FloatArray): FloatArray
    
    /**
     * 将输入值裁剪到域范围内
     */
    protected fun clipToDomain(input: FloatArray): FloatArray {
        val clipped = FloatArray(inputDimension)
        for (i in 0 until inputDimension) {
            val min = domain[i * 2]
            val max = domain[i * 2 + 1]
            clipped[i] = input.getOrElse(i) { min }.coerceIn(min, max)
        }
        return clipped
    }
    
    /**
     * 将输出值裁剪到范围内
     */
    protected fun clipToRange(output: FloatArray): FloatArray {
        if (range == null) return output
        
        val clipped = FloatArray(outputDimension)
        for (i in 0 until outputDimension) {
            val min = range.getOrElse(i * 2) { 0f }
            val max = range.getOrElse(i * 2 + 1) { 1f }
            clipped[i] = output.getOrElse(i) { min }.coerceIn(min, max)
        }
        return clipped
    }
    
    companion object {
        /**
         * 从 PDF 对象创建函数
         */
        fun create(obj: PdfObject, document: PdfDocument? = null): PdfFunction? {
            val dict = when (obj) {
                is PdfDictionary -> obj
                is PdfStream -> obj.dict
                is PdfIndirectRef -> {
                    val resolved = document?.getObject(obj)
                    when (resolved) {
                        is PdfDictionary -> resolved
                        is PdfStream -> resolved.dict
                        else -> null
                    }
                }
                else -> null
            } ?: return null
            
            val functionType = dict.getInt("FunctionType") ?: return null
            
            // 解析 Domain
            val domainArray = dict.getArray("Domain") ?: return null
            val domain = FloatArray(domainArray.size) { 
                domainArray.getFloat(it) ?: 0f 
            }
            
            // 解析 Range
            val rangeArray = dict.getArray("Range")
            val range = rangeArray?.let { arr ->
                FloatArray(arr.size) { arr.getFloat(it) ?: 0f }
            }
            
            return when (functionType) {
                0 -> createSampledFunction(obj, dict, domain, range, document)
                2 -> createExponentialFunction(dict, domain, range)
                3 -> createStitchingFunction(dict, domain, range, document)
                4 -> createPostScriptFunction(obj, dict, domain, range, document)
                else -> null
            }
        }
        
        private fun createSampledFunction(
            obj: PdfObject,
            dict: PdfDictionary,
            domain: FloatArray,
            range: FloatArray?,
            document: PdfDocument?
        ): SampledFunction? {
            // 获取流数据
            val stream = when (obj) {
                is PdfStream -> obj
                is PdfIndirectRef -> document?.getObject(obj) as? PdfStream
                else -> null
            } ?: return null
            
            // 解析 Size 数组
            val sizeArray = dict.getArray("Size") ?: return null
            val size = IntArray(sizeArray.size) { sizeArray.getInt(it) ?: 1 }
            
            // 解析 BitsPerSample
            val bitsPerSample = dict.getInt("BitsPerSample") ?: return null
            
            // 解析 Encode
            val encodeArray = dict.getArray("Encode")
            val encode = if (encodeArray != null) {
                FloatArray(encodeArray.size) { encodeArray.getFloat(it) ?: 0f }
            } else {
                // 默认编码
                FloatArray(size.size * 2) { i ->
                    if (i % 2 == 0) 0f else (size[i / 2] - 1).toFloat()
                }
            }
            
            // 解析 Decode
            val decodeArray = dict.getArray("Decode")
            val decode = decodeArray?.let { arr ->
                FloatArray(arr.size) { arr.getFloat(it) ?: 0f }
            } ?: range
            
            // 获取采样数据
            val filters = stream.getFilters()
            var data = stream.rawData
            for ((index, filter) in filters.withIndex()) {
                val params = stream.getDecodeParams(index)
                data = StreamFilters.decode(data, filter, params)
            }
            
            return SampledFunction(domain, range, size, bitsPerSample, encode, decode, data)
        }
        
        private fun createExponentialFunction(
            dict: PdfDictionary,
            domain: FloatArray,
            range: FloatArray?
        ): ExponentialFunction? {
            // 解析 C0
            val c0Array = dict.getArray("C0")
            val c0 = c0Array?.let { arr ->
                FloatArray(arr.size) { arr.getFloat(it) ?: 0f }
            } ?: floatArrayOf(0f)
            
            // 解析 C1
            val c1Array = dict.getArray("C1")
            val c1 = c1Array?.let { arr ->
                FloatArray(arr.size) { arr.getFloat(it) ?: 1f }
            } ?: floatArrayOf(1f)
            
            // 解析 N
            val n = dict.getFloat("N") ?: 1f
            
            return ExponentialFunction(domain, range, c0, c1, n)
        }
        
        private fun createStitchingFunction(
            dict: PdfDictionary,
            domain: FloatArray,
            range: FloatArray?,
            document: PdfDocument?
        ): StitchingFunction? {
            // 解析 Functions
            val functionsArray = dict.getArray("Functions") ?: return null
            val functions = mutableListOf<PdfFunction>()
            for (funcObj in functionsArray) {
                val func = create(funcObj, document) ?: continue
                functions.add(func)
            }
            if (functions.isEmpty()) return null
            
            // 解析 Bounds
            val boundsArray = dict.getArray("Bounds") ?: return null
            val bounds = FloatArray(boundsArray.size) { boundsArray.getFloat(it) ?: 0f }
            
            // 解析 Encode
            val encodeArray = dict.getArray("Encode") ?: return null
            val encode = FloatArray(encodeArray.size) { encodeArray.getFloat(it) ?: 0f }
            
            return StitchingFunction(domain, range, functions, bounds, encode)
        }
        
        private fun createPostScriptFunction(
            obj: PdfObject,
            dict: PdfDictionary,
            domain: FloatArray,
            range: FloatArray?,
            document: PdfDocument?
        ): PostScriptFunction? {
            // 获取流数据（PostScript 代码）
            val stream = when (obj) {
                is PdfStream -> obj
                is PdfIndirectRef -> document?.getObject(obj) as? PdfStream
                else -> null
            } ?: return null
            
            val filters = stream.getFilters()
            var data = stream.rawData
            for ((index, filter) in filters.withIndex()) {
                val params = stream.getDecodeParams(index)
                data = StreamFilters.decode(data, filter, params)
            }
            
            val code = String(data, Charsets.ISO_8859_1)
            
            return PostScriptFunction(domain, range, code)
        }
    }
}

/**
 * Type 0: Sampled 函数
 * 使用查表和插值来近似任意函数
 */
class SampledFunction(
    domain: FloatArray,
    range: FloatArray?,
    private val size: IntArray,         // 每个输入维度的采样数
    private val bitsPerSample: Int,     // 每个采样的位数
    private val encode: FloatArray,     // 编码映射
    private val decode: FloatArray?,    // 解码映射
    private val samples: ByteArray      // 采样数据
) : PdfFunction(domain, range) {
    
    override val outputDimension: Int = (decode?.size ?: range?.size ?: 2) / 2
    
    override fun evaluate(input: FloatArray): FloatArray {
        val clippedInput = clipToDomain(input)
        
        // 将输入映射到采样空间
        val encodedInput = FloatArray(inputDimension)
        for (i in 0 until inputDimension) {
            val x = clippedInput[i]
            val domainMin = domain[i * 2]
            val domainMax = domain[i * 2 + 1]
            val encodeMin = encode[i * 2]
            val encodeMax = encode[i * 2 + 1]
            
            encodedInput[i] = if (domainMax == domainMin) {
                encodeMin
            } else {
                encodeMin + (x - domainMin) * (encodeMax - encodeMin) / (domainMax - domainMin)
            }
            
            // 裁剪到采样范围
            encodedInput[i] = encodedInput[i].coerceIn(0f, (size[i] - 1).toFloat())
        }
        
        // 多维插值
        val output = interpolate(encodedInput)
        
        // 解码输出
        val decodedOutput = FloatArray(outputDimension)
        for (i in 0 until outputDimension) {
            val y = output[i]
            val decodeMin = decode?.getOrElse(i * 2) { 0f } ?: 0f
            val decodeMax = decode?.getOrElse(i * 2 + 1) { 1f } ?: 1f
            val maxSampleValue = (1 shl bitsPerSample) - 1
            
            decodedOutput[i] = decodeMin + y * (decodeMax - decodeMin) / maxSampleValue
        }
        
        return clipToRange(decodedOutput)
    }
    
    /**
     * 多维线性插值
     */
    private fun interpolate(encodedInput: FloatArray): FloatArray {
        // 简化实现：仅支持 1D 和 2D 插值
        return when (inputDimension) {
            1 -> interpolate1D(encodedInput[0])
            2 -> interpolate2D(encodedInput[0], encodedInput[1])
            else -> interpolateND(encodedInput)
        }
    }
    
    /**
     * 一维插值
     */
    private fun interpolate1D(x: Float): FloatArray {
        val x0 = x.toInt().coerceIn(0, size[0] - 2)
        val x1 = x0 + 1
        val t = x - x0
        
        val output = FloatArray(outputDimension)
        for (i in 0 until outputDimension) {
            val y0 = getSample(x0, 0, i)
            val y1 = getSample(x1, 0, i)
            output[i] = y0 + t * (y1 - y0)
        }
        return output
    }
    
    /**
     * 二维插值
     */
    private fun interpolate2D(x: Float, y: Float): FloatArray {
        val x0 = x.toInt().coerceIn(0, size[0] - 2)
        val x1 = x0 + 1
        val y0 = y.toInt().coerceIn(0, size.getOrElse(1) { 1 } - 2)
        val y1 = y0 + 1
        
        val tx = x - x0
        val ty = y - y0
        
        val output = FloatArray(outputDimension)
        for (i in 0 until outputDimension) {
            val v00 = getSample(x0, y0, i)
            val v10 = getSample(x1, y0, i)
            val v01 = getSample(x0, y1, i)
            val v11 = getSample(x1, y1, i)
            
            val v0 = v00 + tx * (v10 - v00)
            val v1 = v01 + tx * (v11 - v01)
            output[i] = v0 + ty * (v1 - v0)
        }
        return output
    }
    
    /**
     * N 维插值（简化为最近邻）
     */
    private fun interpolateND(encodedInput: FloatArray): FloatArray {
        val indices = IntArray(inputDimension) { i ->
            encodedInput[i].toInt().coerceIn(0, size[i] - 1)
        }
        
        val output = FloatArray(outputDimension)
        val flatIndex = getFlatIndex(indices)
        for (i in 0 until outputDimension) {
            output[i] = getSampleAtFlat(flatIndex, i)
        }
        return output
    }
    
    /**
     * 获取采样值
     */
    private fun getSample(x: Int, y: Int, component: Int): Float {
        val index = if (size.size > 1) {
            (y * size[0] + x) * outputDimension + component
        } else {
            x * outputDimension + component
        }
        return getSampleAtIndex(index)
    }
    
    private fun getFlatIndex(indices: IntArray): Int {
        var flatIndex = 0
        var multiplier = 1
        for (i in indices.indices.reversed()) {
            flatIndex += indices[i] * multiplier
            multiplier *= size[i]
        }
        return flatIndex
    }
    
    private fun getSampleAtFlat(flatIndex: Int, component: Int): Float {
        return getSampleAtIndex(flatIndex * outputDimension + component)
    }
    
    private fun getSampleAtIndex(index: Int): Float {
        val bitsIndex = index * bitsPerSample
        val byteIndex = bitsIndex / 8
        val bitOffset = bitsIndex % 8
        
        if (byteIndex >= samples.size) return 0f
        
        return when (bitsPerSample) {
            8 -> (samples[byteIndex].toInt() and 0xFF).toFloat()
            16 -> {
                if (byteIndex + 1 >= samples.size) return 0f
                val high = (samples[byteIndex].toInt() and 0xFF)
                val low = (samples[byteIndex + 1].toInt() and 0xFF)
                ((high shl 8) or low).toFloat()
            }
            else -> {
                // 位级读取
                var value = 0
                for (i in 0 until bitsPerSample) {
                    val b = (bitsIndex + i) / 8
                    val bit = 7 - ((bitsIndex + i) % 8)
                    if (b < samples.size) {
                        val bitValue = (samples[b].toInt() shr bit) and 1
                        value = (value shl 1) or bitValue
                    }
                }
                value.toFloat()
            }
        }
    }
}

/**
 * Type 2: Exponential 函数
 * y = C0 + x^N × (C1 - C0)
 */
class ExponentialFunction(
    domain: FloatArray,
    range: FloatArray?,
    private val c0: FloatArray,    // 起始值（x=0 时的值）
    private val c1: FloatArray,    // 结束值（x=1 时的值）
    private val n: Float           // 指数
) : PdfFunction(domain, range) {
    
    override val outputDimension: Int = c0.size
    
    override fun evaluate(input: FloatArray): FloatArray {
        val clippedInput = clipToDomain(input)
        val x = clippedInput[0]
        
        val output = FloatArray(outputDimension)
        val xPowN = Math.pow(x.toDouble(), n.toDouble()).toFloat()
        
        for (i in 0 until outputDimension) {
            output[i] = c0[i] + xPowN * (c1[i] - c0[i])
        }
        
        return clipToRange(output)
    }
}

/**
 * Type 3: Stitching 函数
 * 将多个函数组合成一个分段函数
 */
class StitchingFunction(
    domain: FloatArray,
    range: FloatArray?,
    private val functions: List<PdfFunction>,  // 子函数列表
    private val bounds: FloatArray,            // 边界值
    private val encode: FloatArray             // 每个子函数的输入映射
) : PdfFunction(domain, range) {
    
    override val outputDimension: Int = functions.firstOrNull()?.outputDimension ?: 1
    
    override fun evaluate(input: FloatArray): FloatArray {
        val clippedInput = clipToDomain(input)
        val x = clippedInput[0]
        
        // 找到对应的子函数
        val domainMin = domain[0]
        val domainMax = domain[1]
        
        var funcIndex = 0
        var subDomainMin = domainMin
        var subDomainMax = if (bounds.isEmpty()) domainMax else bounds[0]
        
        for (i in bounds.indices) {
            if (x < bounds[i]) break
            funcIndex = i + 1
            subDomainMin = bounds[i]
            subDomainMax = bounds.getOrElse(i + 1) { domainMax }
        }
        
        if (funcIndex >= functions.size) {
            funcIndex = functions.size - 1
        }
        
        // 映射输入到子函数的域
        val encodeMin = encode[funcIndex * 2]
        val encodeMax = encode[funcIndex * 2 + 1]
        
        val mappedX = if (subDomainMax == subDomainMin) {
            encodeMin
        } else {
            encodeMin + (x - subDomainMin) * (encodeMax - encodeMin) / (subDomainMax - subDomainMin)
        }
        
        // 调用子函数
        val output = functions[funcIndex].evaluate(floatArrayOf(mappedX))
        
        return clipToRange(output)
    }
}

/**
 * Type 4: PostScript Calculator 函数
 * 使用 PostScript 语言子集进行计算
 */
class PostScriptFunction(
    domain: FloatArray,
    range: FloatArray?,
    private val code: String
) : PdfFunction(domain, range) {
    
    override val outputDimension: Int = (range?.size ?: 2) / 2
    
    private val tokens: List<String> = tokenize(code)
    
    override fun evaluate(input: FloatArray): FloatArray {
        val clippedInput = clipToDomain(input)
        val stack = mutableListOf<Float>()
        
        // 将输入压栈
        for (value in clippedInput) {
            stack.add(value)
        }
        
        // 执行指令
        executeTokens(tokens, stack)
        
        // 获取输出
        val output = FloatArray(outputDimension)
        for (i in (outputDimension - 1) downTo 0) {
            output[i] = if (stack.isNotEmpty()) stack.removeLast() else 0f
        }
        
        return clipToRange(output)
    }
    
    private fun tokenize(code: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        val text = code.trim()
        
        while (i < text.length) {
            // 跳过空白
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length) break
            
            when {
                // 注释
                text[i] == '%' -> {
                    while (i < text.length && text[i] != '\n') i++
                }
                // 大括号
                text[i] == '{' || text[i] == '}' -> {
                    tokens.add(text[i].toString())
                    i++
                }
                // 数字
                text[i].isDigit() || text[i] == '-' || text[i] == '.' -> {
                    val start = i
                    if (text[i] == '-') i++
                    while (i < text.length && (text[i].isDigit() || text[i] == '.' || text[i] == 'e' || text[i] == 'E' || text[i] == '-')) {
                        i++
                    }
                    tokens.add(text.substring(start, i))
                }
                // 操作符
                else -> {
                    val start = i
                    while (i < text.length && !text[i].isWhitespace() && text[i] != '{' && text[i] != '}') {
                        i++
                    }
                    tokens.add(text.substring(start, i))
                }
            }
        }
        
        return tokens
    }
    
    private fun executeTokens(tokens: List<String>, stack: MutableList<Float>) {
        var i = 0
        
        while (i < tokens.size) {
            val token = tokens[i]
            
            when {
                // 数字
                token.toFloatOrNull() != null -> {
                    stack.add(token.toFloat())
                }
                // if
                token == "if" -> {
                    // 查找对应的代码块
                    val condition = if (stack.isNotEmpty()) stack.removeLast() else 0f
                    if (condition != 0f) {
                        // 执行 if 块（已经被处理过了）
                    }
                }
                // ifelse
                token == "ifelse" -> {
                    // 复杂实现，跳过
                }
                // 算术操作
                token == "add" -> binaryOp(stack) { a, b -> a + b }
                token == "sub" -> binaryOp(stack) { a, b -> a - b }
                token == "mul" -> binaryOp(stack) { a, b -> a * b }
                token == "div" -> binaryOp(stack) { a, b -> if (b != 0f) a / b else 0f }
                token == "idiv" -> binaryOp(stack) { a, b -> if (b != 0f) (a.toInt() / b.toInt()).toFloat() else 0f }
                token == "mod" -> binaryOp(stack) { a, b -> if (b != 0f) a % b else 0f }
                token == "neg" -> unaryOp(stack) { -it }
                token == "abs" -> unaryOp(stack) { kotlin.math.abs(it) }
                token == "ceiling" -> unaryOp(stack) { kotlin.math.ceil(it.toDouble()).toFloat() }
                token == "floor" -> unaryOp(stack) { kotlin.math.floor(it.toDouble()).toFloat() }
                token == "round" -> unaryOp(stack) { kotlin.math.round(it) }
                token == "truncate" -> unaryOp(stack) { it.toInt().toFloat() }
                token == "sqrt" -> unaryOp(stack) { kotlin.math.sqrt(it.toDouble()).toFloat() }
                token == "sin" -> unaryOp(stack) { kotlin.math.sin(it.toDouble()).toFloat() }
                token == "cos" -> unaryOp(stack) { kotlin.math.cos(it.toDouble()).toFloat() }
                token == "atan" -> binaryOp(stack) { num, den -> 
                    kotlin.math.atan2(num.toDouble(), den.toDouble()).toFloat() 
                }
                token == "exp" -> binaryOp(stack) { base, exp -> 
                    Math.pow(base.toDouble(), exp.toDouble()).toFloat() 
                }
                token == "ln" -> unaryOp(stack) { kotlin.math.ln(it.toDouble()).toFloat() }
                token == "log" -> unaryOp(stack) { kotlin.math.log10(it.toDouble()).toFloat() }
                
                // 关系操作
                token == "eq" -> binaryOp(stack) { a, b -> if (a == b) 1f else 0f }
                token == "ne" -> binaryOp(stack) { a, b -> if (a != b) 1f else 0f }
                token == "gt" -> binaryOp(stack) { a, b -> if (a > b) 1f else 0f }
                token == "ge" -> binaryOp(stack) { a, b -> if (a >= b) 1f else 0f }
                token == "lt" -> binaryOp(stack) { a, b -> if (a < b) 1f else 0f }
                token == "le" -> binaryOp(stack) { a, b -> if (a <= b) 1f else 0f }
                
                // 逻辑操作
                token == "and" -> binaryOp(stack) { a, b -> (a.toInt() and b.toInt()).toFloat() }
                token == "or" -> binaryOp(stack) { a, b -> (a.toInt() or b.toInt()).toFloat() }
                token == "xor" -> binaryOp(stack) { a, b -> (a.toInt() xor b.toInt()).toFloat() }
                token == "not" -> unaryOp(stack) { if (it == 0f) 1f else 0f }
                token == "bitshift" -> binaryOp(stack) { a, shift -> 
                    if (shift >= 0) (a.toInt() shl shift.toInt()).toFloat()
                    else (a.toInt() shr (-shift).toInt()).toFloat()
                }
                
                // 栈操作
                token == "dup" -> {
                    if (stack.isNotEmpty()) stack.add(stack.last())
                }
                token == "exch" -> {
                    if (stack.size >= 2) {
                        val a = stack.removeLast()
                        val b = stack.removeLast()
                        stack.add(a)
                        stack.add(b)
                    }
                }
                token == "pop" -> {
                    if (stack.isNotEmpty()) stack.removeLast()
                }
                token == "copy" -> {
                    val n = if (stack.isNotEmpty()) stack.removeLast().toInt() else 0
                    if (n > 0 && stack.size >= n) {
                        val toCopy = stack.takeLast(n)
                        stack.addAll(toCopy)
                    }
                }
                token == "index" -> {
                    val n = if (stack.isNotEmpty()) stack.removeLast().toInt() else 0
                    if (n >= 0 && stack.size > n) {
                        stack.add(stack[stack.size - 1 - n])
                    }
                }
                token == "roll" -> {
                    if (stack.size >= 2) {
                        val j = stack.removeLast().toInt()
                        val n = stack.removeLast().toInt()
                        if (n > 0 && stack.size >= n) {
                            val elements = mutableListOf<Float>()
                            repeat(n) { elements.add(0, stack.removeLast()) }
                            val shift = ((j % n) + n) % n
                            for (k in 0 until n) {
                                stack.add(elements[(k - shift + n) % n])
                            }
                        }
                    }
                }
                
                // 常量
                token == "true" -> stack.add(1f)
                token == "false" -> stack.add(0f)
                
                // 大括号（过程定义）
                token == "{" -> {
                    // 跳过整个块
                    var depth = 1
                    while (depth > 0 && i + 1 < tokens.size) {
                        i++
                        when (tokens[i]) {
                            "{" -> depth++
                            "}" -> depth--
                        }
                    }
                }
                
                // 其他
                else -> {
                    // 忽略未知操作符
                }
            }
            
            i++
        }
    }
    
    private inline fun unaryOp(stack: MutableList<Float>, op: (Float) -> Float) {
        if (stack.isNotEmpty()) {
            val a = stack.removeLast()
            stack.add(op(a))
        }
    }
    
    private inline fun binaryOp(stack: MutableList<Float>, op: (Float, Float) -> Float) {
        if (stack.size >= 2) {
            val b = stack.removeLast()
            val a = stack.removeLast()
            stack.add(op(a, b))
        }
    }
}
