package com.pdfcore.font

/**
 * PDF 标准编码
 * 基于 PDF 32000-1:2008 Annex D
 */
object StandardEncodings {
    
    /**
     * WinAnsiEncoding (Windows CP1252)
     */
    private val winAnsiEncoding = mapOf(
        0x80 to '\u20AC', // Euro
        0x82 to '\u201A', // Single Low-9 Quotation Mark
        0x83 to '\u0192', // Latin Small Letter F With Hook
        0x84 to '\u201E', // Double Low-9 Quotation Mark
        0x85 to '\u2026', // Horizontal Ellipsis
        0x86 to '\u2020', // Dagger
        0x87 to '\u2021', // Double Dagger
        0x88 to '\u02C6', // Modifier Letter Circumflex Accent
        0x89 to '\u2030', // Per Mille Sign
        0x8A to '\u0160', // Latin Capital Letter S With Caron
        0x8B to '\u2039', // Single Left-Pointing Angle Quotation Mark
        0x8C to '\u0152', // Latin Capital Ligature OE
        0x8E to '\u017D', // Latin Capital Letter Z With Caron
        0x91 to '\u2018', // Left Single Quotation Mark
        0x92 to '\u2019', // Right Single Quotation Mark
        0x93 to '\u201C', // Left Double Quotation Mark
        0x94 to '\u201D', // Right Double Quotation Mark
        0x95 to '\u2022', // Bullet
        0x96 to '\u2013', // En Dash
        0x97 to '\u2014', // Em Dash
        0x98 to '\u02DC', // Small Tilde
        0x99 to '\u2122', // Trade Mark Sign
        0x9A to '\u0161', // Latin Small Letter S With Caron
        0x9B to '\u203A', // Single Right-Pointing Angle Quotation Mark
        0x9C to '\u0153', // Latin Small Ligature OE
        0x9E to '\u017E', // Latin Small Letter Z With Caron
        0x9F to '\u0178'  // Latin Capital Letter Y With Diaeresis
    )
    
    /**
     * MacRomanEncoding
     */
    private val macRomanEncoding = mapOf(
        0x80 to '\u00C4', // Ä
        0x81 to '\u00C5', // Å
        0x82 to '\u00C7', // Ç
        0x83 to '\u00C9', // É
        0x84 to '\u00D1', // Ñ
        0x85 to '\u00D6', // Ö
        0x86 to '\u00DC', // Ü
        0x87 to '\u00E1', // á
        0x88 to '\u00E0', // à
        0x89 to '\u00E2', // â
        0x8A to '\u00E4', // ä
        0x8B to '\u00E3', // ã
        0x8C to '\u00E5', // å
        0x8D to '\u00E7', // ç
        0x8E to '\u00E9', // é
        0x8F to '\u00E8', // è
        0x90 to '\u00EA', // ê
        0x91 to '\u00EB', // ë
        0x92 to '\u00ED', // í
        0x93 to '\u00EC', // ì
        0x94 to '\u00EE', // î
        0x95 to '\u00EF', // ï
        0x96 to '\u00F1', // ñ
        0x97 to '\u00F3', // ó
        0x98 to '\u00F2', // ò
        0x99 to '\u00F4', // ô
        0x9A to '\u00F6', // ö
        0x9B to '\u00F5', // õ
        0x9C to '\u00FA', // ú
        0x9D to '\u00F9', // ù
        0x9E to '\u00FB', // û
        0x9F to '\u00FC', // ü
        0xA0 to '\u2020', // †
        0xA1 to '\u00B0', // °
        0xA4 to '\u00A7', // §
        0xA5 to '\u2022', // •
        0xA6 to '\u00B6', // ¶
        0xA7 to '\u00DF', // ß
        0xA8 to '\u00AE', // ®
        0xA9 to '\u00A9', // ©
        0xAA to '\u2122', // ™
        0xD0 to '\u2013', // –
        0xD1 to '\u2014', // —
        0xD2 to '\u201C', // "
        0xD3 to '\u201D', // "
        0xD4 to '\u2018', // '
        0xD5 to '\u2019'  // '
    )
    
    /**
     * PDFDocEncoding
     */
    private val pdfDocEncoding = mapOf(
        0x80 to '\u2022', // Bullet
        0x81 to '\u2020', // Dagger
        0x82 to '\u2021', // Double Dagger
        0x83 to '\u2026', // Horizontal Ellipsis
        0x84 to '\u2014', // Em Dash
        0x85 to '\u2013', // En Dash
        0x86 to '\u0192', // Latin Small Letter F With Hook
        0x87 to '\u2044', // Fraction Slash
        0x88 to '\u2039', // Single Left-Pointing Angle Quotation Mark
        0x89 to '\u203A', // Single Right-Pointing Angle Quotation Mark
        0x8A to '\u2212', // Minus Sign
        0x8B to '\u2030', // Per Mille Sign
        0x8C to '\u201E', // Double Low-9 Quotation Mark
        0x8D to '\u201C', // Left Double Quotation Mark
        0x8E to '\u201D', // Right Double Quotation Mark
        0x8F to '\u2018', // Left Single Quotation Mark
        0x90 to '\u2019', // Right Single Quotation Mark
        0x91 to '\u201A', // Single Low-9 Quotation Mark
        0x92 to '\u2122', // Trade Mark Sign
        0x93 to '\uFB01', // Latin Small Ligature FI
        0x94 to '\uFB02', // Latin Small Ligature FL
        0x95 to '\u0141', // Latin Capital Letter L With Stroke
        0x96 to '\u0152', // Latin Capital Ligature OE
        0x97 to '\u0160', // Latin Capital Letter S With Caron
        0x98 to '\u0178', // Latin Capital Letter Y With Diaeresis
        0x99 to '\u017D', // Latin Capital Letter Z With Caron
        0x9A to '\u0131', // Latin Small Letter Dotless I
        0x9B to '\u0142', // Latin Small Letter L With Stroke
        0x9C to '\u0153', // Latin Small Ligature OE
        0x9D to '\u0161', // Latin Small Letter S With Caron
        0x9E to '\u017E', // Latin Small Letter Z With Caron
        0xA0 to '\u20AC'  // Euro Sign
    )
    
    /**
     * 字符码转 Unicode
     */
    fun toUnicode(encodingName: String, code: Int): Char? {
        // 标准 ASCII 范围
        if (code in 0x20..0x7E) {
            return code.toChar()
        }
        
        // 根据编码名称选择映射
        val specialMapping = when (encodingName) {
            "WinAnsiEncoding" -> winAnsiEncoding[code]
            "MacRomanEncoding" -> macRomanEncoding[code]
            "PDFDocEncoding" -> pdfDocEncoding[code]
            "StandardEncoding" -> standardEncodingSpecial[code]
            else -> winAnsiEncoding[code] // 默认使用 WinAnsi
        }
        
        if (specialMapping != null) return specialMapping
        
        // 0xA0-0xFF 范围在大多数编码中映射到 Latin-1
        if (code in 0xA0..0xFF) {
            return code.toChar()
        }
        
        return null
    }
    
    /**
     * Unicode 转字符码
     */
    fun fromUnicode(encodingName: String, char: Char): Int? {
        val code = char.code
        
        // 标准 ASCII 范围
        if (code in 0x20..0x7E) {
            return code
        }
        
        // Latin-1 Supplement 范围
        if (code in 0xA0..0xFF) {
            return code
        }
        
        // 查找特殊映射
        val mapping = when (encodingName) {
            "WinAnsiEncoding" -> winAnsiEncoding
            "MacRomanEncoding" -> macRomanEncoding
            "PDFDocEncoding" -> pdfDocEncoding
            else -> winAnsiEncoding
        }
        
        // 反向查找
        for ((c, u) in mapping) {
            if (u == char) return c
        }
        
        return null
    }
    
    /**
     * StandardEncoding 特殊字符
     */
    private val standardEncodingSpecial = mapOf(
        0x60 to '\u2018', // quoteleft
        0x91 to '\u2018', // quoteleft
        0x92 to '\u2019', // quoteright
        0x93 to '\u201C', // quotedblleft
        0x94 to '\u201D', // quotedblright
        0xA1 to '\u00A1', // exclamdown
        0xA2 to '\u00A2', // cent
        0xBF to '\u00BF', // questiondown
        0xC1 to '\u0060', // grave
        0xC2 to '\u00B4', // acute
        0xC3 to '\u02C6', // circumflex
        0xC4 to '\u02DC', // tilde
        0xC5 to '\u00AF', // macron
        0xC6 to '\u02D8', // breve
        0xC7 to '\u02D9', // dotaccent
        0xC8 to '\u00A8', // dieresis
        0xCA to '\u02DA', // ring
        0xCB to '\u00B8', // cedilla
        0xCD to '\u02DD', // hungarumlaut
        0xCE to '\u02DB', // ogonek
        0xCF to '\u02C7'  // caron
    )
}

/**
 * Adobe Glyph List - 字形名到 Unicode 的映射
 */
object GlyphList {
    
    private val glyphToUnicode = mapOf(
        // 常用字形名
        "space" to '\u0020',
        "exclam" to '\u0021',
        "quotedbl" to '\u0022',
        "numbersign" to '\u0023',
        "dollar" to '\u0024',
        "percent" to '\u0025',
        "ampersand" to '\u0026',
        "quotesingle" to '\u0027',
        "parenleft" to '\u0028',
        "parenright" to '\u0029',
        "asterisk" to '\u002A',
        "plus" to '\u002B',
        "comma" to '\u002C',
        "hyphen" to '\u002D',
        "period" to '\u002E',
        "slash" to '\u002F',
        "zero" to '\u0030',
        "one" to '\u0031',
        "two" to '\u0032',
        "three" to '\u0033',
        "four" to '\u0034',
        "five" to '\u0035',
        "six" to '\u0036',
        "seven" to '\u0037',
        "eight" to '\u0038',
        "nine" to '\u0039',
        "colon" to '\u003A',
        "semicolon" to '\u003B',
        "less" to '\u003C',
        "equal" to '\u003D',
        "greater" to '\u003E',
        "question" to '\u003F',
        "at" to '\u0040',
        // A-Z
        "A" to 'A', "B" to 'B', "C" to 'C', "D" to 'D', "E" to 'E',
        "F" to 'F', "G" to 'G', "H" to 'H', "I" to 'I', "J" to 'J',
        "K" to 'K', "L" to 'L', "M" to 'M', "N" to 'N', "O" to 'O',
        "P" to 'P', "Q" to 'Q', "R" to 'R', "S" to 'S', "T" to 'T',
        "U" to 'U', "V" to 'V', "W" to 'W', "X" to 'X', "Y" to 'Y', "Z" to 'Z',
        // a-z
        "a" to 'a', "b" to 'b', "c" to 'c', "d" to 'd', "e" to 'e',
        "f" to 'f', "g" to 'g', "h" to 'h', "i" to 'i', "j" to 'j',
        "k" to 'k', "l" to 'l', "m" to 'm', "n" to 'n', "o" to 'o',
        "p" to 'p', "q" to 'q', "r" to 'r', "s" to 's', "t" to 't',
        "u" to 'u', "v" to 'v', "w" to 'w', "x" to 'x', "y" to 'y', "z" to 'z',
        // 特殊字符
        "bracketleft" to '\u005B',
        "backslash" to '\u005C',
        "bracketright" to '\u005D',
        "asciicircum" to '\u005E',
        "underscore" to '\u005F',
        "grave" to '\u0060',
        "braceleft" to '\u007B',
        "bar" to '\u007C',
        "braceright" to '\u007D',
        "asciitilde" to '\u007E',
        // 重音字符
        "Agrave" to '\u00C0', "Aacute" to '\u00C1', "Acircumflex" to '\u00C2',
        "Atilde" to '\u00C3', "Adieresis" to '\u00C4', "Aring" to '\u00C5',
        "AE" to '\u00C6', "Ccedilla" to '\u00C7', "Egrave" to '\u00C8',
        "Eacute" to '\u00C9', "Ecircumflex" to '\u00CA', "Edieresis" to '\u00CB',
        "Igrave" to '\u00CC', "Iacute" to '\u00CD', "Icircumflex" to '\u00CE',
        "Idieresis" to '\u00CF', "Eth" to '\u00D0', "Ntilde" to '\u00D1',
        "Ograve" to '\u00D2', "Oacute" to '\u00D3', "Ocircumflex" to '\u00D4',
        "Otilde" to '\u00D5', "Odieresis" to '\u00D6', "Ugrave" to '\u00D9',
        "Uacute" to '\u00DA', "Ucircumflex" to '\u00DB', "Udieresis" to '\u00DC',
        "Yacute" to '\u00DD', "Thorn" to '\u00DE', "germandbls" to '\u00DF',
        "agrave" to '\u00E0', "aacute" to '\u00E1', "acircumflex" to '\u00E2',
        "atilde" to '\u00E3', "adieresis" to '\u00E4', "aring" to '\u00E5',
        "ae" to '\u00E6', "ccedilla" to '\u00E7', "egrave" to '\u00E8',
        "eacute" to '\u00E9', "ecircumflex" to '\u00EA', "edieresis" to '\u00EB',
        "igrave" to '\u00EC', "iacute" to '\u00ED', "icircumflex" to '\u00EE',
        "idieresis" to '\u00EF', "eth" to '\u00F0', "ntilde" to '\u00F1',
        "ograve" to '\u00F2', "oacute" to '\u00F3', "ocircumflex" to '\u00F4',
        "otilde" to '\u00F5', "odieresis" to '\u00F6', "ugrave" to '\u00F9',
        "uacute" to '\u00FA', "ucircumflex" to '\u00FB', "udieresis" to '\u00FC',
        "yacute" to '\u00FD', "thorn" to '\u00FE', "ydieresis" to '\u00FF',
        // 标点和符号
        "bullet" to '\u2022',
        "ellipsis" to '\u2026',
        "emdash" to '\u2014',
        "endash" to '\u2013',
        "quoteleft" to '\u2018',
        "quoteright" to '\u2019',
        "quotedblleft" to '\u201C',
        "quotedblright" to '\u201D',
        "fi" to '\uFB01',
        "fl" to '\uFB02',
        "trademark" to '\u2122',
        "copyright" to '\u00A9',
        "registered" to '\u00AE',
        "degree" to '\u00B0',
        "plusminus" to '\u00B1',
        "multiply" to '\u00D7',
        "divide" to '\u00F7',
        "Euro" to '\u20AC',
        "sterling" to '\u00A3',
        "yen" to '\u00A5',
        "cent" to '\u00A2'
    )
    
    /**
     * 字形名转 Unicode
     */
    fun toUnicode(glyphName: String): Char? {
        // 直接查找
        glyphToUnicode[glyphName]?.let { return it }
        
        // 尝试解析 uniXXXX 格式
        if (glyphName.startsWith("uni") && glyphName.length == 7) {
            val hex = glyphName.substring(3)
            hex.toIntOrNull(16)?.let { return it.toChar() }
        }
        
        // 尝试解析 uXXXX 或 uXXXXX 格式
        if (glyphName.startsWith("u") && glyphName.length in 5..6) {
            val hex = glyphName.substring(1)
            hex.toIntOrNull(16)?.let { 
                if (Character.isValidCodePoint(it)) {
                    return it.toChar()
                }
            }
        }
        
        return null
    }
}
