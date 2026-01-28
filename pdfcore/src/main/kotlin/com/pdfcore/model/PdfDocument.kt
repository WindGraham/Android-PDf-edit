package com.pdfcore.model

/**
 * PDF 交叉引用表条目
 * PDF 32000-1:2008 7.5.4, 7.5.8
 */
sealed class XrefEntry {
    abstract val objectNumber: Int
    abstract val generationNumber: Int
    
    /**
     * 在用对象 - 对象存储在文件的指定偏移位置
     */
    data class InUse(
        override val objectNumber: Int,
        override val generationNumber: Int,
        val byteOffset: Long
    ) : XrefEntry()
    
    /**
     * 空闲对象 - 已删除的对象
     */
    data class Free(
        override val objectNumber: Int,
        override val generationNumber: Int,
        val nextFreeObject: Int
    ) : XrefEntry()
    
    /**
     * 压缩对象 (PDF 1.5+) - 存储在对象流中
     */
    data class Compressed(
        override val objectNumber: Int,
        val objectStreamNumber: Int,
        val indexInStream: Int
    ) : XrefEntry() {
        override val generationNumber: Int = 0
    }
}

/**
 * PDF 文档
 * 
 * 表示完整的 PDF 文档结构，包含：
 * - 版本信息
 * - 所有间接对象
 * - 交叉引用表
 * - Trailer 字典
 */
class PdfDocument(
    var version: String = "1.7",
    val objects: MutableMap<String, PdfIndirectObject> = mutableMapOf(),
    val xrefEntries: MutableList<XrefEntry> = mutableListOf(),
    var trailer: PdfDictionary = PdfDictionary(),
    var startXref: Long = 0
) {
    /**
     * 文档是否被修改
     */
    var isModified: Boolean = false
        private set
    
    /**
     * 原始文件路径（如果从文件加载）
     */
    var sourcePath: String? = null
    
    /**
     * 下一个可用的对象号
     */
    private var nextObjectNumber: Int = 1
    
    init {
        updateNextObjectNumber()
    }
    
    private fun updateNextObjectNumber() {
        nextObjectNumber = objects.keys
            .mapNotNull { it.split(" ").firstOrNull()?.toIntOrNull() }
            .maxOrNull()?.plus(1) ?: 1
    }
    
    // ==================== 对象访问 ====================
    
    /**
     * 通过引用获取对象
     */
    fun getObject(ref: PdfIndirectRef): PdfObject? {
        return objects[ref.toKey()]?.obj
    }
    
    /**
     * 通过对象号获取对象
     */
    fun getObject(objectNumber: Int, generationNumber: Int = 0): PdfObject? {
        return objects["$objectNumber $generationNumber"]?.obj
    }
    
    /**
     * 解析引用，如果是间接引用则获取实际对象
     */
    fun resolveObject(obj: PdfObject?): PdfObject? {
        return when (obj) {
            is PdfIndirectRef -> getObject(obj)
            else -> obj
        }
    }
    
    /**
     * 解析为字典
     */
    fun resolveDictionary(obj: PdfObject?): PdfDictionary? {
        return resolveObject(obj) as? PdfDictionary
    }
    
    /**
     * 解析为数组
     */
    fun resolveArray(obj: PdfObject?): PdfArray? {
        return resolveObject(obj) as? PdfArray
    }
    
    /**
     * 解析为流
     */
    fun resolveStream(obj: PdfObject?): PdfStream? {
        return resolveObject(obj) as? PdfStream
    }
    
    /**
     * 添加新对象
     */
    fun addObject(obj: PdfObject): PdfIndirectRef {
        val objNum = nextObjectNumber++
        val indirectObj = PdfIndirectObject(objNum, 0, obj)
        objects[indirectObj.toKey()] = indirectObj
        isModified = true
        return indirectObj.toRef()
    }
    
    /**
     * 更新现有对象
     */
    fun updateObject(ref: PdfIndirectRef, newObj: PdfObject) {
        val key = ref.toKey()
        if (objects.containsKey(key)) {
            objects[key] = PdfIndirectObject(ref.objectNumber, ref.generationNumber, newObj)
            isModified = true
        }
    }
    
    /**
     * 删除对象
     */
    fun removeObject(ref: PdfIndirectRef) {
        if (objects.remove(ref.toKey()) != null) {
            isModified = true
        }
    }
    
    // ==================== 文档结构访问 ====================
    
    /**
     * 获取 Catalog 字典
     */
    fun getCatalog(): PdfDictionary? {
        val rootRef = trailer.getRef("Root") ?: return null
        return getObject(rootRef) as? PdfDictionary
    }
    
    /**
     * 获取 Pages 树根节点
     */
    fun getPagesRoot(): PdfDictionary? {
        val catalog = getCatalog() ?: return null
        val pagesRef = catalog.getRef("Pages") ?: return null
        return getObject(pagesRef) as? PdfDictionary
    }
    
    /**
     * 获取所有页面
     */
    fun getAllPages(): List<PdfDictionary> {
        val pages = mutableListOf<PdfDictionary>()
        val root = getPagesRoot() ?: return pages
        collectPages(root, pages)
        return pages
    }
    
    private fun collectPages(node: PdfDictionary, pages: MutableList<PdfDictionary>) {
        when (node.getType()) {
            "Page" -> pages.add(node)
            "Pages" -> {
                val kids = node.getArray("Kids") ?: return
                for (kid in kids) {
                    val kidDict = when (kid) {
                        is PdfIndirectRef -> getObject(kid) as? PdfDictionary
                        is PdfDictionary -> kid
                        else -> null
                    }
                    kidDict?.let { collectPages(it, pages) }
                }
            }
        }
    }
    
    /**
     * 获取页面数量
     */
    fun getPageCount(): Int {
        val pagesRoot = getPagesRoot() ?: return 0
        return pagesRoot.getInt("Count") ?: getAllPages().size
    }
    
    /**
     * 获取指定页面
     */
    fun getPage(index: Int): PdfDictionary? {
        return getAllPages().getOrNull(index)
    }
    
    /**
     * 获取页面的 MediaBox
     */
    fun getPageMediaBox(page: PdfDictionary): PdfRectangle? {
        // 先查找页面本身的 MediaBox
        var mediaBox = page.getArray("MediaBox")?.toRectangle()
        
        // 如果没有，从父节点继承
        if (mediaBox == null) {
            var parent = page.getRef("Parent")?.let { getObject(it) as? PdfDictionary }
            while (mediaBox == null && parent != null) {
                mediaBox = parent.getArray("MediaBox")?.toRectangle()
                parent = parent.getRef("Parent")?.let { getObject(it) as? PdfDictionary }
            }
        }
        
        return mediaBox
    }
    
    /**
     * 获取页面的 CropBox
     */
    fun getPageCropBox(page: PdfDictionary): PdfRectangle? {
        return page.getArray("CropBox")?.toRectangle() ?: getPageMediaBox(page)
    }
    
    /**
     * 获取页面旋转角度
     */
    fun getPageRotation(page: PdfDictionary): Int {
        var rotation = page.getInt("Rotate")
        
        if (rotation == null) {
            var parent = page.getRef("Parent")?.let { getObject(it) as? PdfDictionary }
            while (rotation == null && parent != null) {
                rotation = parent.getInt("Rotate")
                parent = parent.getRef("Parent")?.let { getObject(it) as? PdfDictionary }
            }
        }
        
        return rotation ?: 0
    }
    
    /**
     * 获取页面的 Contents 流
     */
    fun getPageContents(page: PdfDictionary): List<PdfStream> {
        val contents = mutableListOf<PdfStream>()
        when (val c = page["Contents"]) {
            is PdfIndirectRef -> {
                (getObject(c) as? PdfStream)?.let { contents.add(it) }
            }
            is PdfArray -> {
                for (item in c) {
                    when (item) {
                        is PdfIndirectRef -> (getObject(item) as? PdfStream)?.let { contents.add(it) }
                        is PdfStream -> contents.add(item)
                        else -> { /* ignore other types */ }
                    }
                }
            }
            is PdfStream -> contents.add(c)
            else -> { /* no content */ }
        }
        return contents
    }
    
    /**
     * 获取页面的 Resources 字典
     */
    fun getPageResources(page: PdfDictionary): PdfDictionary? {
        // 先查找页面本身的 Resources
        var resources = when (val r = page["Resources"]) {
            is PdfDictionary -> r
            is PdfIndirectRef -> getObject(r) as? PdfDictionary
            else -> null
        }
        
        // 如果没有，从父节点继承
        if (resources == null) {
            var parent = page.getRef("Parent")?.let { getObject(it) as? PdfDictionary }
            while (resources == null && parent != null) {
                resources = when (val r = parent["Resources"]) {
                    is PdfDictionary -> r
                    is PdfIndirectRef -> getObject(r) as? PdfDictionary
                    else -> null
                }
                parent = parent.getRef("Parent")?.let { getObject(it) as? PdfDictionary }
            }
        }
        
        return resources
    }
    
    /**
     * 获取页面的字体字典
     */
    fun getPageFonts(page: PdfDictionary): Map<String, PdfDictionary> {
        val resources = getPageResources(page) ?: return emptyMap()
        val fontDict = when (val f = resources["Font"]) {
            is PdfDictionary -> f
            is PdfIndirectRef -> getObject(f) as? PdfDictionary
            else -> null
        } ?: return emptyMap()
        
        val fonts = mutableMapOf<String, PdfDictionary>()
        for ((name, value) in fontDict) {
            val font = when (value) {
                is PdfDictionary -> value
                is PdfIndirectRef -> getObject(value) as? PdfDictionary
                else -> null
            }
            font?.let { fonts[name] = it }
        }
        return fonts
    }
    
    /**
     * 获取文档信息字典
     */
    fun getInfo(): PdfDictionary? {
        val infoRef = trailer.getRef("Info") ?: return null
        return getObject(infoRef) as? PdfDictionary
    }
    
    /**
     * 获取文档标题
     */
    fun getTitle(): String? = getInfo()?.getString("Title")?.value
    
    /**
     * 获取文档作者
     */
    fun getAuthor(): String? = getInfo()?.getString("Author")?.value
    
    /**
     * 获取文档主题
     */
    fun getSubject(): String? = getInfo()?.getString("Subject")?.value
    
    /**
     * 标记文档为已修改
     */
    fun markModified() {
        isModified = true
    }
    
    /**
     * 重置修改标记
     */
    fun clearModified() {
        isModified = false
    }
    
    /**
     * 创建文档的深拷贝
     */
    fun copy(): PdfDocument {
        return PdfDocument(
            version = version,
            objects = objects.mapValues { (_, v) -> v.copy() }.toMutableMap(),
            xrefEntries = xrefEntries.toMutableList(),
            trailer = trailer.copy(),
            startXref = startXref
        ).also {
            it.sourcePath = sourcePath
            it.nextObjectNumber = nextObjectNumber
        }
    }
}
