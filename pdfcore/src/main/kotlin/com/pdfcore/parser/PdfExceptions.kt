package com.pdfcore.parser

/**
 * PDF 解析相关异常
 */

/**
 * PDF 解析基础异常
 */
open class PdfException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 无效的 PDF 格式
 */
class InvalidPdfFormatException(
    message: String,
    cause: Throwable? = null
) : PdfException(message, cause)

/**
 * 无效的 PDF 头部
 */
class InvalidHeaderException(
    message: String,
    cause: Throwable? = null
) : PdfException(message, cause)

/**
 * 无效的 Trailer
 */
class InvalidTrailerException(
    message: String,
    cause: Throwable? = null
) : PdfException(message, cause)

/**
 * 无效的交叉引用表
 */
class InvalidXrefException(
    message: String,
    val offset: Long? = null,
    cause: Throwable? = null
) : PdfException(message, cause)

/**
 * 加密的 PDF (暂不支持)
 */
class EncryptedPdfException(
    message: String = "Encrypted PDF files are not supported",
    cause: Throwable? = null
) : PdfException(message, cause)

/**
 * 对象解析错误
 */
class ObjectParseException(
    message: String,
    val objectNumber: Int? = null,
    val generationNumber: Int? = null,
    cause: Throwable? = null
) : PdfException(message, cause)

/**
 * 流解码错误
 */
class StreamDecodeException(
    message: String,
    val filterName: String? = null,
    cause: Throwable? = null
) : PdfException(message, cause)

/**
 * 不支持的过滤器
 */
class UnsupportedFilterException(
    val filterName: String,
    cause: Throwable? = null
) : PdfException("Unsupported filter: $filterName", cause)

/**
 * 内容流解析错误
 */
class ContentStreamException(
    message: String,
    val position: Int? = null,
    cause: Throwable? = null
) : PdfException(message, cause)

/**
 * 字体解析错误
 */
class FontException(
    message: String,
    val fontName: String? = null,
    cause: Throwable? = null
) : PdfException(message, cause)

/**
 * PDF 解析结果
 */
sealed class PdfParseResult<out T> {
    data class Success<T>(val value: T) : PdfParseResult<T>()
    data class Failure(val exception: PdfException) : PdfParseResult<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
    
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw exception
    }
    
    fun exceptionOrNull(): PdfException? = when (this) {
        is Success -> null
        is Failure -> exception
    }
    
    inline fun <R> map(transform: (T) -> R): PdfParseResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
    
    inline fun onSuccess(action: (T) -> Unit): PdfParseResult<T> {
        if (this is Success) action(value)
        return this
    }
    
    inline fun onFailure(action: (PdfException) -> Unit): PdfParseResult<T> {
        if (this is Failure) action(exception)
        return this
    }
    
    companion object {
        fun <T> success(value: T): PdfParseResult<T> = Success(value)
        fun failure(exception: PdfException): PdfParseResult<Nothing> = Failure(exception)
    }
}
