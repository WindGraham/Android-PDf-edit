package com.pdfcore.security

import com.pdfcore.model.*

/**
 * PDF 加密处理器
 * 基于 PDF 32000-1:2008 标准 7.6 节
 * 
 * 支持的加密版本：
 * - V=1: 40-bit RC4 (Algorithm 1)
 * - V=2: RC4 with key length > 40 bits (usually 128-bit)
 * - V=3: 不推荐使用的 128-bit RC4
 * - V=4: AES-128 或 RC4 (由 CF 字典决定)
 * - V=5: AES-256 (PDF 1.7 Extension Level 3)
 */
class PdfEncryption(
    private val encryptDict: PdfDictionary,
    private val documentId: ByteArray
) {
    
    // 加密字典参数
    private val filter: String = encryptDict.getNameValue("Filter") ?: "Standard"
    private val version: Int = encryptDict.getInt("V") ?: 0
    private val revision: Int = encryptDict.getInt("R") ?: 2
    private val keyLength: Int = encryptDict.getInt("Length") ?: 40
    private val permissions: Int = encryptDict.getInt("P") ?: 0
    
    // O 和 U 值
    private val oValue: ByteArray = encryptDict.getString("O")?.toBytes() ?: ByteArray(32)
    private val uValue: ByteArray = encryptDict.getString("U")?.toBytes() ?: ByteArray(32)
    
    // AES-256 扩展 (R=5, R=6)
    private val oeValue: ByteArray? = encryptDict.getString("OE")?.toBytes()
    private val ueValue: ByteArray? = encryptDict.getString("UE")?.toBytes()
    private val perms: ByteArray? = encryptDict.getString("Perms")?.toBytes()
    
    // 加密过滤器 (V=4, V=5)
    private val cfDict: PdfDictionary? = encryptDict.getDictionary("CF")
    private val stmF: String = encryptDict.getNameValue("StmF") ?: "Identity"
    private val strF: String = encryptDict.getNameValue("StrF") ?: "Identity"
    
    // 计算的加密密钥
    private var encryptionKey: ByteArray? = null
    
    // 是否使用 AES
    private val useAES: Boolean
        get() = when {
            version >= 5 -> true
            version == 4 -> {
                val cfm = getCryptFilterMethod(stmF)
                cfm == "AESV2" || cfm == "AESV3"
            }
            else -> false
        }
    
    /**
     * 验证用户密码
     */
    fun authenticateUser(password: String = ""): Boolean {
        val passwordBytes = preparePassword(password)
        
        return when {
            revision >= 5 -> authenticateUserR5(passwordBytes)
            else -> authenticateUserR2_4(passwordBytes)
        }
    }
    
    /**
     * 验证所有者密码
     */
    fun authenticateOwner(password: String): Boolean {
        val passwordBytes = preparePassword(password)
        
        return when {
            revision >= 5 -> authenticateOwnerR5(passwordBytes)
            else -> authenticateOwnerR2_4(passwordBytes)
        }
    }
    
    /**
     * 尝试使用空密码或提供的密码进行身份验证
     */
    fun authenticate(password: String = ""): Boolean {
        // 先尝试用户密码
        if (authenticateUser(password)) return true
        
        // 再尝试所有者密码
        if (password.isNotEmpty() && authenticateOwner(password)) return true
        
        return false
    }
    
    /**
     * 解密对象
     */
    fun decryptObject(objNum: Int, genNum: Int, obj: PdfObject): PdfObject {
        return when (obj) {
            is PdfString -> PdfString.fromBytes(decryptString(objNum, genNum, obj), obj.isHex)
            is PdfStream -> decryptStream(objNum, genNum, obj)
            is PdfDictionary -> {
                val decrypted = PdfDictionary()
                for ((key, value) in obj) {
                    decrypted[key] = decryptObject(objNum, genNum, value)
                }
                decrypted
            }
            is PdfArray -> {
                val decrypted = PdfArray()
                for (item in obj) {
                    decrypted.add(decryptObject(objNum, genNum, item))
                }
                decrypted
            }
            else -> obj
        }
    }
    
    /**
     * 解密字符串
     */
    fun decryptString(objNum: Int, genNum: Int, str: PdfString): ByteArray {
        val key = encryptionKey ?: return str.toBytes()
        val data = str.toBytes()
        
        return if (strF == "Identity") {
            data
        } else {
            decryptData(objNum, genNum, data, strF)
        }
    }
    
    /**
     * 解密流
     */
    fun decryptStream(objNum: Int, genNum: Int, stream: PdfStream): PdfStream {
        val key = encryptionKey ?: return stream
        
        // 检查是否是特殊流（不加密）
        val streamType = stream.dict.getNameValue("Type")
        if (streamType == "XRef" || streamType == "Metadata") {
            // XRef 流不加密，Metadata 可能不加密
            val encryptMetadata = encryptDict.getBoolean("EncryptMetadata")?.value ?: true
            if (streamType == "XRef" || (!encryptMetadata && streamType == "Metadata")) {
                return stream
            }
        }
        
        if (stmF == "Identity") {
            return stream
        }
        
        val decryptedData = decryptData(objNum, genNum, stream.rawData, stmF)
        
        return PdfStream(stream.dict, decryptedData)
    }
    
    /**
     * 解密数据
     */
    private fun decryptData(objNum: Int, genNum: Int, data: ByteArray, filter: String): ByteArray {
        val key = encryptionKey ?: return data
        
        val cfm = getCryptFilterMethod(filter)
        
        return when (cfm) {
            "V2" -> {
                // RC4
                val objectKey = computeObjectKey(objNum, genNum, false)
                RC4Cipher.crypt(objectKey, data)
            }
            "AESV2" -> {
                // AES-128
                val objectKey = computeObjectKey(objNum, genNum, true)
                AESCipher.decryptCBC(objectKey, data)
            }
            "AESV3" -> {
                // AES-256
                AESCipher.decryptCBC(key, data)
            }
            else -> data
        }
    }
    
    /**
     * 获取加密过滤器方法
     */
    private fun getCryptFilterMethod(filterName: String): String {
        if (filterName == "Identity") return "Identity"
        
        val cf = cfDict?.getDictionary(filterName)
        return cf?.getNameValue("CFM") ?: when {
            version >= 5 -> "AESV3"
            version == 4 -> "AESV2"
            else -> "V2"
        }
    }
    
    /**
     * 计算对象密钥
     * PDF 32000-1:2008 Algorithm 1
     */
    private fun computeObjectKey(objNum: Int, genNum: Int, aes: Boolean): ByteArray {
        val key = encryptionKey ?: return ByteArray(0)
        
        if (version >= 5) {
            // AES-256 直接使用文件加密密钥
            return key
        }
        
        // 构建输入
        val input = ByteArray(key.size + 5 + if (aes) 4 else 0)
        System.arraycopy(key, 0, input, 0, key.size)
        
        input[key.size] = (objNum and 0xFF).toByte()
        input[key.size + 1] = ((objNum shr 8) and 0xFF).toByte()
        input[key.size + 2] = ((objNum shr 16) and 0xFF).toByte()
        input[key.size + 3] = (genNum and 0xFF).toByte()
        input[key.size + 4] = ((genNum shr 8) and 0xFF).toByte()
        
        if (aes) {
            // AES "sAlT"
            input[key.size + 5] = 0x73 // 's'
            input[key.size + 6] = 0x41 // 'A'
            input[key.size + 7] = 0x6C // 'l'
            input[key.size + 8] = 0x54 // 'T'
        }
        
        val hash = MD5.hash(input)
        
        // 密钥长度
        val keyLen = minOf(key.size + 5, 16)
        return hash.copyOf(keyLen)
    }
    
    // ==================== R2-R4 认证 ====================
    
    /**
     * R2-R4 用户密码验证
     * PDF 32000-1:2008 Algorithm 6
     */
    private fun authenticateUserR2_4(password: ByteArray): Boolean {
        val key = computeEncryptionKeyR2_4(password)
        val computedU = computeUValueR2_4(key)
        
        // 比较 U 值
        val compareLength = if (revision >= 3) 16 else 32
        for (i in 0 until compareLength) {
            if (computedU.getOrElse(i) { 0 } != uValue.getOrElse(i) { 0 }) {
                return false
            }
        }
        
        encryptionKey = key
        return true
    }
    
    /**
     * R2-R4 所有者密码验证
     * PDF 32000-1:2008 Algorithm 7
     */
    private fun authenticateOwnerR2_4(password: ByteArray): Boolean {
        // 计算所有者密钥
        val ownerKey = computeOwnerKeyR2_4(password)
        
        // 使用所有者密钥解密 O 值得到用户密码
        val userPassword = if (revision == 2) {
            RC4Cipher.crypt(ownerKey, oValue)
        } else {
            var result = oValue.copyOf()
            for (i in 19 downTo 0) {
                val tempKey = ByteArray(ownerKey.size) { j -> (ownerKey[j].toInt() xor i).toByte() }
                result = RC4Cipher.crypt(tempKey, result)
            }
            result
        }
        
        // 使用解密的用户密码验证
        return authenticateUserR2_4(userPassword)
    }
    
    /**
     * 计算加密密钥 (R2-R4)
     * PDF 32000-1:2008 Algorithm 2
     */
    private fun computeEncryptionKeyR2_4(password: ByteArray): ByteArray {
        val paddedPassword = padPassword(password)
        
        // 步骤 a-d: MD5(password + O + P + ID[0])
        val permBytes = byteArrayOf(
            (permissions and 0xFF).toByte(),
            ((permissions shr 8) and 0xFF).toByte(),
            ((permissions shr 16) and 0xFF).toByte(),
            ((permissions shr 24) and 0xFF).toByte()
        )
        
        var hash = MD5.hash(paddedPassword, oValue, permBytes, documentId)
        
        // 步骤 e: 如果 R >= 3，重复 MD5 50 次
        if (revision >= 3) {
            val keyBytes = keyLength / 8
            for (i in 0 until 50) {
                hash = MD5.hash(hash.copyOf(keyBytes))
            }
        }
        
        // 步骤 f: 返回前 n 字节
        val keyBytes = if (revision == 2) 5 else keyLength / 8
        return hash.copyOf(keyBytes)
    }
    
    /**
     * 计算所有者密钥
     * PDF 32000-1:2008 Algorithm 3 (部分)
     */
    private fun computeOwnerKeyR2_4(password: ByteArray): ByteArray {
        val paddedPassword = padPassword(password)
        
        var hash = MD5.hash(paddedPassword)
        
        if (revision >= 3) {
            for (i in 0 until 50) {
                hash = MD5.hash(hash)
            }
        }
        
        val keyBytes = if (revision == 2) 5 else keyLength / 8
        return hash.copyOf(keyBytes)
    }
    
    /**
     * 计算 U 值 (R2-R4)
     * PDF 32000-1:2008 Algorithm 4, 5
     */
    private fun computeUValueR2_4(key: ByteArray): ByteArray {
        return if (revision == 2) {
            // Algorithm 4
            RC4Cipher.crypt(key, PASSWORD_PADDING)
        } else {
            // Algorithm 5
            val hash = MD5.hash(PASSWORD_PADDING, documentId)
            var result = RC4Cipher.crypt(key, hash)
            
            for (i in 1..19) {
                val tempKey = ByteArray(key.size) { j -> (key[j].toInt() xor i).toByte() }
                result = RC4Cipher.crypt(tempKey, result)
            }
            
            // 追加 16 字节任意数据
            result + ByteArray(16)
        }
    }
    
    // ==================== R5-R6 认证 (AES-256) ====================
    
    /**
     * R5/R6 用户密码验证
     * ISO 32000-2 Algorithm 11
     */
    private fun authenticateUserR5(password: ByteArray): Boolean {
        if (uValue.size < 48) return false
        
        // U 值的前 32 字节是验证哈希
        // 字节 32-39 是验证盐
        // 字节 40-47 是密钥盐
        val validationHash = uValue.copyOfRange(0, 32)
        val validationSalt = uValue.copyOfRange(32, 40)
        val keySalt = uValue.copyOfRange(40, 48)
        
        // 计算验证哈希
        val computedHash = computeHashR5(password, validationSalt, ByteArray(0))
        
        if (!computedHash.contentEquals(validationHash)) {
            return false
        }
        
        // 计算文件加密密钥
        val keyHash = computeHashR5(password, keySalt, ByteArray(0))
        
        if (ueValue != null && ueValue.size >= 32) {
            encryptionKey = AESCipher.decryptCBC(keyHash, byteArrayOf(*ByteArray(16), *ueValue))
        }
        
        return encryptionKey != null
    }
    
    /**
     * R5/R6 所有者密码验证
     * ISO 32000-2 Algorithm 12
     */
    private fun authenticateOwnerR5(password: ByteArray): Boolean {
        if (oValue.size < 48) return false
        
        val validationHash = oValue.copyOfRange(0, 32)
        val validationSalt = oValue.copyOfRange(32, 40)
        val keySalt = oValue.copyOfRange(40, 48)
        
        // 计算验证哈希（包含 U 值的前 48 字节）
        val uFirst48 = uValue.copyOf(minOf(48, uValue.size))
        val computedHash = computeHashR5(password, validationSalt, uFirst48)
        
        if (!computedHash.contentEquals(validationHash)) {
            return false
        }
        
        // 计算文件加密密钥
        val keyHash = computeHashR5(password, keySalt, uFirst48)
        
        if (oeValue != null && oeValue.size >= 32) {
            encryptionKey = AESCipher.decryptCBC(keyHash, byteArrayOf(*ByteArray(16), *oeValue))
        }
        
        return encryptionKey != null
    }
    
    /**
     * R5/R6 哈希计算
     * ISO 32000-2 Algorithm 2.B
     */
    private fun computeHashR5(password: ByteArray, salt: ByteArray, userData: ByteArray): ByteArray {
        // 简化实现：仅支持 R5
        if (revision >= 6) {
            return computeHashR6(password, salt, userData)
        }
        
        // R5: SHA-256(password + salt + userData)
        return SHA256.hash(password, salt, userData)
    }
    
    /**
     * R6 哈希计算
     * ISO 32000-2 Algorithm 2.B (完整版)
     */
    private fun computeHashR6(password: ByteArray, salt: ByteArray, userData: ByteArray): ByteArray {
        // 初始哈希: SHA-256(password + salt + userData)
        var k = SHA256.hash(password, salt, userData)
        
        var lastE = 0
        var round = 0
        
        while (round < 64 || lastE > round - 32) {
            // K1 = password + K + userData, 重复 64 次
            val k1Builder = mutableListOf<Byte>()
            for (i in 0 until 64) {
                k1Builder.addAll(password.toList())
                k1Builder.addAll(k.toList())
                k1Builder.addAll(userData.toList())
            }
            val k1 = k1Builder.toByteArray()
            
            // E = AES-CBC(K[0:16], K[16:32], K1)
            val aesKey = k.copyOfRange(0, 16)
            val iv = k.copyOfRange(16, 32)
            val e = try {
                val cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding")
                val keySpec = javax.crypto.spec.SecretKeySpec(aesKey, "AES")
                val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, ivSpec)
                cipher.doFinal(k1)
            } catch (ex: Exception) {
                k1
            }
            
            // 根据 E 的前 16 字节求和决定使用哪个哈希
            val sum = e.take(16).sumOf { (it.toInt() and 0xFF) }
            lastE = (e.lastOrNull()?.toInt() ?: 0) and 0xFF
            
            k = when (sum % 3) {
                0 -> SHA256.hash(e)
                1 -> SHA384.hash(e)
                else -> SHA512.hash(e)
            }
            
            round++
        }
        
        return k.copyOf(32)
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 准备密码
     */
    private fun preparePassword(password: String): ByteArray {
        return if (revision >= 5) {
            // PDF 2.0: UTF-8 编码，最多 127 字节
            val utf8 = password.toByteArray(Charsets.UTF_8)
            if (utf8.size > 127) utf8.copyOf(127) else utf8
        } else {
            // PDF 1.x: PDFDocEncoding 或 Latin-1
            password.toByteArray(Charsets.ISO_8859_1)
        }
    }
    
    /**
     * 填充密码到 32 字节
     */
    private fun padPassword(password: ByteArray): ByteArray {
        val result = ByteArray(32)
        val len = minOf(password.size, 32)
        System.arraycopy(password, 0, result, 0, len)
        System.arraycopy(PASSWORD_PADDING, 0, result, len, 32 - len)
        return result
    }
    
    companion object {
        /**
         * 标准密码填充
         */
        private val PASSWORD_PADDING = byteArrayOf(
            0x28.toByte(), 0xBF.toByte(), 0x4E.toByte(), 0x5E.toByte(), 
            0x4E.toByte(), 0x75.toByte(), 0x8A.toByte(), 0x41.toByte(),
            0x64.toByte(), 0x00.toByte(), 0x4E.toByte(), 0x56.toByte(), 
            0xFF.toByte(), 0xFA.toByte(), 0x01.toByte(), 0x08.toByte(), 
            0x2E.toByte(), 0x2E.toByte(), 0x00.toByte(), 0xB6.toByte(),
            0xD0.toByte(), 0x68.toByte(), 0x3E.toByte(), 0x80.toByte(), 
            0x2F.toByte(), 0x0C.toByte(), 0xA9.toByte(), 0xFE.toByte(), 
            0x64.toByte(), 0x53.toByte(), 0x69.toByte(), 0x7A.toByte()
        )
        
        /**
         * 从 Encrypt 字典和文档 ID 创建加密处理器
         */
        fun create(
            encryptDict: PdfDictionary,
            trailer: PdfDictionary,
            document: PdfDocument? = null
        ): PdfEncryption? {
            // 获取文档 ID
            val idArray = when (val id = trailer["ID"]) {
                is PdfArray -> id
                is PdfIndirectRef -> document?.getObject(id) as? PdfArray
                else -> null
            }
            
            val documentId = if (idArray != null && idArray.isNotEmpty()) {
                (idArray[0] as? PdfString)?.toBytes() ?: ByteArray(0)
            } else {
                ByteArray(0)
            }
            
            return PdfEncryption(encryptDict, documentId)
        }
    }
}
