package com.pdfcore.security

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * RC4 密码实现
 * 基于 PDF 32000-1:2008 标准 7.6.2
 * 
 * RC4 是一种流密码，PDF 使用它进行 40-bit 和 128-bit 加密
 */
class RC4Cipher(key: ByteArray) {
    
    private val s = IntArray(256)
    private var i = 0
    private var j = 0
    
    init {
        // KSA (Key-Scheduling Algorithm)
        for (k in 0 until 256) {
            s[k] = k
        }
        
        var jTemp = 0
        for (k in 0 until 256) {
            jTemp = (jTemp + s[k] + (key[k % key.size].toInt() and 0xFF)) and 0xFF
            // Swap
            val temp = s[k]
            s[k] = s[jTemp]
            s[jTemp] = temp
        }
        
        i = 0
        j = 0
    }
    
    /**
     * 加密/解密数据（RC4 是对称的，加解密使用相同操作）
     */
    fun process(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        
        for (k in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            
            // Swap
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            
            // Generate keystream byte
            val t = (s[i] + s[j]) and 0xFF
            val keystreamByte = s[t]
            
            result[k] = ((data[k].toInt() and 0xFF) xor keystreamByte).toByte()
        }
        
        return result
    }
    
    /**
     * 处理数据（原地修改）
     */
    fun processInPlace(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        for (k in offset until offset + length) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            
            // Swap
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            
            // Generate keystream byte
            val t = (s[i] + s[j]) and 0xFF
            val keystreamByte = s[t]
            
            data[k] = ((data[k].toInt() and 0xFF) xor keystreamByte).toByte()
        }
    }
    
    companion object {
        /**
         * 一次性加密/解密
         */
        fun crypt(key: ByteArray, data: ByteArray): ByteArray {
            return RC4Cipher(key).process(data)
        }
    }
}

/**
 * AES 密码封装
 * 基于 PDF 32000-1:2008 标准 7.6.2
 * 
 * PDF 使用 AES-128 (PDF 1.5+) 和 AES-256 (PDF 1.7 Extension Level 3+)
 */
object AESCipher {
    
    /**
     * AES-CBC 解密
     * 
     * @param key 密钥 (16 字节用于 AES-128, 32 字节用于 AES-256)
     * @param data 密文数据，前 16 字节是 IV
     */
    fun decryptCBC(key: ByteArray, data: ByteArray): ByteArray {
        if (data.size < 16) return ByteArray(0)
        
        // 提取 IV（前 16 字节）
        val iv = data.copyOfRange(0, 16)
        val ciphertext = data.copyOfRange(16, data.size)
        
        return try {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decrypted = cipher.doFinal(ciphertext)
            
            // 移除 PKCS#7 填充
            removePadding(decrypted)
        } catch (e: Exception) {
            // 如果失败，尝试不移除填充
            try {
                val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                val keySpec = SecretKeySpec(key, "AES")
                val ivSpec = IvParameterSpec(iv)
                
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                cipher.doFinal(ciphertext)
            } catch (e2: Exception) {
                ByteArray(0)
            }
        }
    }
    
    /**
     * AES-CBC 加密
     */
    fun encryptCBC(key: ByteArray, data: ByteArray, iv: ByteArray? = null): ByteArray {
        // 生成或使用提供的 IV
        val actualIv = iv ?: generateIV()
        
        // 添加 PKCS#7 填充
        val paddedData = addPadding(data)
        
        return try {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(actualIv)
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val ciphertext = cipher.doFinal(paddedData)
            
            // 返回 IV + 密文
            actualIv + ciphertext
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
    
    /**
     * AES-ECB 解密（用于某些密钥计算）
     */
    fun decryptECB(key: ByteArray, data: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
    
    /**
     * AES-ECB 加密
     */
    fun encryptECB(key: ByteArray, data: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            val keySpec = SecretKeySpec(key, "AES")
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            ByteArray(0)
        }
    }
    
    /**
     * 生成随机 IV
     */
    fun generateIV(): ByteArray {
        val iv = ByteArray(16)
        java.security.SecureRandom().nextBytes(iv)
        return iv
    }
    
    /**
     * 添加 PKCS#7 填充
     */
    private fun addPadding(data: ByteArray): ByteArray {
        val blockSize = 16
        val paddingLength = blockSize - (data.size % blockSize)
        val padded = ByteArray(data.size + paddingLength)
        
        System.arraycopy(data, 0, padded, 0, data.size)
        for (i in data.size until padded.size) {
            padded[i] = paddingLength.toByte()
        }
        
        return padded
    }
    
    /**
     * 移除 PKCS#7 填充
     */
    private fun removePadding(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        
        val paddingLength = data.last().toInt() and 0xFF
        
        // 验证填充
        if (paddingLength < 1 || paddingLength > 16 || paddingLength > data.size) {
            return data
        }
        
        for (i in data.size - paddingLength until data.size) {
            if ((data[i].toInt() and 0xFF) != paddingLength) {
                return data
            }
        }
        
        return data.copyOfRange(0, data.size - paddingLength)
    }
}

/**
 * MD5 哈希工具
 */
object MD5 {
    
    /**
     * 计算 MD5 哈希
     */
    fun hash(data: ByteArray): ByteArray {
        return try {
            java.security.MessageDigest.getInstance("MD5").digest(data)
        } catch (e: Exception) {
            ByteArray(16)
        }
    }
    
    /**
     * 计算多个数据块的 MD5 哈希
     */
    fun hash(vararg dataParts: ByteArray): ByteArray {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            for (part in dataParts) {
                md.update(part)
            }
            md.digest()
        } catch (e: Exception) {
            ByteArray(16)
        }
    }
}

/**
 * SHA-256 哈希工具
 */
object SHA256 {
    
    /**
     * 计算 SHA-256 哈希
     */
    fun hash(data: ByteArray): ByteArray {
        return try {
            java.security.MessageDigest.getInstance("SHA-256").digest(data)
        } catch (e: Exception) {
            ByteArray(32)
        }
    }
    
    /**
     * 计算多个数据块的 SHA-256 哈希
     */
    fun hash(vararg dataParts: ByteArray): ByteArray {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            for (part in dataParts) {
                md.update(part)
            }
            md.digest()
        } catch (e: Exception) {
            ByteArray(32)
        }
    }
}

/**
 * SHA-384 哈希工具
 */
object SHA384 {
    
    /**
     * 计算 SHA-384 哈希
     */
    fun hash(data: ByteArray): ByteArray {
        return try {
            java.security.MessageDigest.getInstance("SHA-384").digest(data)
        } catch (e: Exception) {
            ByteArray(48)
        }
    }
}

/**
 * SHA-512 哈希工具
 */
object SHA512 {
    
    /**
     * 计算 SHA-512 哈希
     */
    fun hash(data: ByteArray): ByteArray {
        return try {
            java.security.MessageDigest.getInstance("SHA-512").digest(data)
        } catch (e: Exception) {
            ByteArray(64)
        }
    }
}
