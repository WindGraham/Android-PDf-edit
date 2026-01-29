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
        // Symbol 和 ZapfDingbats 使用专用编码，不使用标准 ASCII 映射
        if (encodingName == "SymbolEncoding") {
            return symbolEncoding[code]
        }
        if (encodingName == "ZapfDingbatsEncoding") {
            return zapfDingbatsEncoding[code]
        }
        
        // Wingdings 符号字体编码
        if (encodingName == "WingdingsEncoding" || encodingName.contains("Wingdings")) {
            return wingdingsEncoding[code]
        }
        
        // TeX 数学字体编码
        if (encodingName == "CMREncoding" || encodingName.contains("CMR")) {
            return cmrEncoding[code]
        }
        if (encodingName == "CMMIEncoding" || encodingName.contains("CMMI")) {
            return cmmiEncoding[code]
        }
        if (encodingName == "CMSYEncoding" || encodingName.contains("CMSY")) {
            return cmsyEncoding[code]
        }
        if (encodingName == "CMEXEncoding" || encodingName.contains("CMEX")) {
            return cmexEncoding[code]
        }
        
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
     * 检测是否为 TeX 数学字体
     */
    fun isTeXMathFont(fontName: String): Boolean {
        val name = fontName.uppercase()
        return name.startsWith("CM") || // Computer Modern 系列
               name.startsWith("CMMI") ||
               name.startsWith("CMSY") ||
               name.startsWith("CMEX") ||
               name.startsWith("CMR") ||
               name.startsWith("CMBX") ||
               name.startsWith("CMTI") ||
               name.contains("MATH") ||
               name.contains("STIX") ||
               name.contains("CAMBRIA") && name.contains("MATH")
    }
    
    /**
     * 获取 TeX 字体的编码名称
     */
    fun getTeXFontEncoding(fontName: String): String? {
        val name = fontName.uppercase()
        return when {
            name.startsWith("CMMI") || name.contains("CMMI") -> "CMMIEncoding"
            name.startsWith("CMSY") || name.contains("CMSY") -> "CMSYEncoding"
            name.startsWith("CMEX") || name.contains("CMEX") -> "CMEXEncoding"
            name.startsWith("CMR") || name.contains("CMR") -> "CMREncoding"
            name.startsWith("CMBX") -> "CMREncoding"
            name.startsWith("CMTI") -> "CMMIEncoding"
            else -> null
        }
    }
    
    /**
     * 检测是否为 Wingdings 符号字体
     * Wingdings 是 Microsoft 的符号字体系列，常用于 PowerPoint 等文档中的项目符号
     */
    fun isWingdingsFont(fontName: String): Boolean {
        val name = fontName.uppercase()
        return name.contains("WINGDINGS") ||
               name.contains("WEBDINGS") ||
               name.contains("WINGDING")
    }
    
    /**
     * Unicode 转字符码
     */
    fun fromUnicode(encodingName: String, char: Char): Int? {
        // Symbol 和 ZapfDingbats 使用专用编码
        if (encodingName == "SymbolEncoding") {
            for ((c, u) in symbolEncoding) {
                if (u == char) return c
            }
            return null
        }
        if (encodingName == "ZapfDingbatsEncoding") {
            for ((c, u) in zapfDingbatsEncoding) {
                if (u == char) return c
            }
            return null
        }
        
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
    
    /**
     * SymbolEncoding - PDF 32000-1:2008 Annex D.5
     * Symbol 字体的内置编码，包含希腊字母和数学符号
     */
    private val symbolEncoding = mapOf(
        // 基本标点和符号
        0x20 to '\u0020', // space
        0x21 to '\u0021', // exclam
        0x22 to '\u2200', // universal (∀)
        0x23 to '\u0023', // numbersign
        0x24 to '\u2203', // existential (∃)
        0x25 to '\u0025', // percent
        0x26 to '\u0026', // ampersand
        0x27 to '\u220B', // suchthat (∋)
        0x28 to '\u0028', // parenleft
        0x29 to '\u0029', // parenright
        0x2A to '\u2217', // asteriskmath (∗)
        0x2B to '\u002B', // plus
        0x2C to '\u002C', // comma
        0x2D to '\u2212', // minus (−)
        0x2E to '\u002E', // period
        0x2F to '\u002F', // slash
        // 数字
        0x30 to '\u0030', // zero
        0x31 to '\u0031', // one
        0x32 to '\u0032', // two
        0x33 to '\u0033', // three
        0x34 to '\u0034', // four
        0x35 to '\u0035', // five
        0x36 to '\u0036', // six
        0x37 to '\u0037', // seven
        0x38 to '\u0038', // eight
        0x39 to '\u0039', // nine
        0x3A to '\u003A', // colon
        0x3B to '\u003B', // semicolon
        0x3C to '\u003C', // less
        0x3D to '\u003D', // equal
        0x3E to '\u003E', // greater
        0x3F to '\u003F', // question
        0x40 to '\u2245', // congruent (≅)
        // 希腊大写字母
        0x41 to '\u0391', // Alpha (Α)
        0x42 to '\u0392', // Beta (Β)
        0x43 to '\u03A7', // Chi (Χ)
        0x44 to '\u0394', // Delta (Δ)
        0x45 to '\u0395', // Epsilon (Ε)
        0x46 to '\u03A6', // Phi (Φ)
        0x47 to '\u0393', // Gamma (Γ)
        0x48 to '\u0397', // Eta (Η)
        0x49 to '\u0399', // Iota (Ι)
        0x4A to '\u03D1', // theta1 (ϑ)
        0x4B to '\u039A', // Kappa (Κ)
        0x4C to '\u039B', // Lambda (Λ)
        0x4D to '\u039C', // Mu (Μ)
        0x4E to '\u039D', // Nu (Ν)
        0x4F to '\u039F', // Omicron (Ο)
        0x50 to '\u03A0', // Pi (Π)
        0x51 to '\u0398', // Theta (Θ)
        0x52 to '\u03A1', // Rho (Ρ)
        0x53 to '\u03A3', // Sigma (Σ)
        0x54 to '\u03A4', // Tau (Τ)
        0x55 to '\u03A5', // Upsilon (Υ)
        0x56 to '\u03C2', // sigma1 (ς)
        0x57 to '\u03A9', // Omega (Ω)
        0x58 to '\u039E', // Xi (Ξ)
        0x59 to '\u03A8', // Psi (Ψ)
        0x5A to '\u0396', // Zeta (Ζ)
        0x5B to '\u005B', // bracketleft
        0x5C to '\u2234', // therefore (∴)
        0x5D to '\u005D', // bracketright
        0x5E to '\u22A5', // perpendicular (⊥)
        0x5F to '\u005F', // underscore
        0x60 to '\uF8E5', // radicalex
        // 希腊小写字母
        0x61 to '\u03B1', // alpha (α)
        0x62 to '\u03B2', // beta (β)
        0x63 to '\u03C7', // chi (χ)
        0x64 to '\u03B4', // delta (δ)
        0x65 to '\u03B5', // epsilon (ε)
        0x66 to '\u03C6', // phi (φ)
        0x67 to '\u03B3', // gamma (γ)
        0x68 to '\u03B7', // eta (η)
        0x69 to '\u03B9', // iota (ι)
        0x6A to '\u03D5', // phi1 (ϕ)
        0x6B to '\u03BA', // kappa (κ)
        0x6C to '\u03BB', // lambda (λ)
        0x6D to '\u03BC', // mu (μ)
        0x6E to '\u03BD', // nu (ν)
        0x6F to '\u03BF', // omicron (ο)
        0x70 to '\u03C0', // pi (π)
        0x71 to '\u03B8', // theta (θ)
        0x72 to '\u03C1', // rho (ρ)
        0x73 to '\u03C3', // sigma (σ)
        0x74 to '\u03C4', // tau (τ)
        0x75 to '\u03C5', // upsilon (υ)
        0x76 to '\u03D6', // omega1 (ϖ)
        0x77 to '\u03C9', // omega (ω)
        0x78 to '\u03BE', // xi (ξ)
        0x79 to '\u03C8', // psi (ψ)
        0x7A to '\u03B6', // zeta (ζ)
        0x7B to '\u007B', // braceleft
        0x7C to '\u007C', // bar
        0x7D to '\u007D', // braceright
        0x7E to '\u223C', // similar (∼)
        // 扩展符号 (0xA0-0xFF)
        0xA0 to '\u20AC', // Euro (€)
        0xA1 to '\u03D2', // Upsilon1 (ϒ)
        0xA2 to '\u2032', // minute (′)
        0xA3 to '\u2264', // lessequal (≤)
        0xA4 to '\u2044', // fraction (⁄)
        0xA5 to '\u221E', // infinity (∞)
        0xA6 to '\u0192', // florin (ƒ)
        0xA7 to '\u2663', // club (♣)
        0xA8 to '\u2666', // diamond (♦)
        0xA9 to '\u2665', // heart (♥)
        0xAA to '\u2660', // spade (♠)
        0xAB to '\u2194', // arrowboth (↔)
        0xAC to '\u2190', // arrowleft (←)
        0xAD to '\u2191', // arrowup (↑)
        0xAE to '\u2192', // arrowright (→)
        0xAF to '\u2193', // arrowdown (↓)
        0xB0 to '\u00B0', // degree (°)
        0xB1 to '\u00B1', // plusminus (±)
        0xB2 to '\u2033', // second (″)
        0xB3 to '\u2265', // greaterequal (≥)
        0xB4 to '\u00D7', // multiply (×)
        0xB5 to '\u221D', // proportional (∝)
        0xB6 to '\u2202', // partialdiff (∂)
        0xB7 to '\u2022', // bullet (•)
        0xB8 to '\u00F7', // divide (÷)
        0xB9 to '\u2260', // notequal (≠)
        0xBA to '\u2261', // equivalence (≡)
        0xBB to '\u2248', // approxequal (≈)
        0xBC to '\u2026', // ellipsis (…)
        0xBD to '\u23D0', // arrowvertex
        0xBE to '\u23AF', // arrowhorizex
        0xBF to '\u21B5', // carriagereturn (↵)
        0xC0 to '\u2135', // aleph (ℵ)
        0xC1 to '\u2111', // Ifraktur (ℑ)
        0xC2 to '\u211C', // Rfraktur (ℜ)
        0xC3 to '\u2118', // weierstrass (℘)
        0xC4 to '\u2297', // circlemultiply (⊗)
        0xC5 to '\u2295', // circleplus (⊕)
        0xC6 to '\u2205', // emptyset (∅)
        0xC7 to '\u2229', // intersection (∩)
        0xC8 to '\u222A', // union (∪)
        0xC9 to '\u2283', // propersuperset (⊃)
        0xCA to '\u2287', // reflexsuperset (⊇)
        0xCB to '\u2284', // notsubset (⊄)
        0xCC to '\u2282', // propersubset (⊂)
        0xCD to '\u2286', // reflexsubset (⊆)
        0xCE to '\u2208', // element (∈)
        0xCF to '\u2209', // notelement (∉)
        0xD0 to '\u2220', // angle (∠)
        0xD1 to '\u2207', // gradient/nabla (∇)
        0xD2 to '\u00AE', // registerserif (®)
        0xD3 to '\u00A9', // copyrightserif (©)
        0xD4 to '\u2122', // trademarkserif (™)
        0xD5 to '\u220F', // product (∏)
        0xD6 to '\u221A', // radical (√)
        0xD7 to '\u22C5', // dotmath (⋅)
        0xD8 to '\u00AC', // logicalnot (¬)
        0xD9 to '\u2227', // logicaland (∧)
        0xDA to '\u2228', // logicalor (∨)
        0xDB to '\u21D4', // arrowdblboth (⇔)
        0xDC to '\u21D0', // arrowdblleft (⇐)
        0xDD to '\u21D1', // arrowdblup (⇑)
        0xDE to '\u21D2', // arrowdblright (⇒)
        0xDF to '\u21D3', // arrowdbldown (⇓)
        0xE0 to '\u25CA', // lozenge (◊)
        0xE1 to '\u2329', // angleleft (〈)
        0xE2 to '\u00AE', // registersans (®)
        0xE3 to '\u00A9', // copyrightsans (©)
        0xE4 to '\u2122', // trademarksans (™)
        0xE5 to '\u2211', // summation (∑)
        0xE6 to '\u239B', // parenlefttp
        0xE7 to '\u239C', // parenleftex
        0xE8 to '\u239D', // parenleftbt
        0xE9 to '\u23A1', // bracketlefttp
        0xEA to '\u23A2', // bracketleftex
        0xEB to '\u23A3', // bracketleftbt
        0xEC to '\u23A7', // bracelefttp
        0xED to '\u23A8', // braceleftmid
        0xEE to '\u23A9', // braceleftbt
        0xEF to '\u23AA', // braceex
        0xF1 to '\u232A', // angleright (〉)
        0xF2 to '\u222B', // integral (∫)
        0xF3 to '\u2320', // integraltp
        0xF4 to '\u23AE', // integralex
        0xF5 to '\u2321', // integralbt
        0xF6 to '\u239E', // parenrighttp
        0xF7 to '\u239F', // parenrightex
        0xF8 to '\u23A0', // parenrightbt
        0xF9 to '\u23A4', // bracketrighttp
        0xFA to '\u23A5', // bracketrightex
        0xFB to '\u23A6', // bracketrightbt
        0xFC to '\u23AB', // bracerighttp
        0xFD to '\u23AC', // bracerightmid
        0xFE to '\u23AD'  // bracerightbt
    )
    
    /**
     * 将 Symbol 字体 PUA 字符 (U+F000-U+F0FF) 转换为标准 Unicode
     * 
     * 许多 PDF 使用 Unicode 私有使用区域 (PUA) 来表示 Symbol 字体字符。
     * 这些 PUA 字符遵循 Symbol 编码规则：U+F0xx 对应 Symbol 编码 0xxx
     * 
     * 例如：
     * - U+F06C → 0x6C → λ (U+03BB)
     * - U+F070 → 0x70 → π (U+03C0)
     * - U+F044 → 0x44 → Δ (U+0394)
     * - U+F02D → 0x2D → − (U+2212)
     * 
     * @param char 可能是 PUA 字符的字符
     * @return 如果是 PUA 字符，返回对应的标准 Unicode 字符；否则返回 null
     */
    fun convertPUAToUnicode(char: Char): Char? {
        val code = char.code
        if (code in 0xF000..0xF0FF) {
            val symbolCode = code - 0xF000
            return symbolEncoding[symbolCode]
        }
        return null
    }
    
    /**
     * 检查字符是否在 PUA 范围内 (U+F000-U+F0FF)
     */
    fun isPUACharacter(char: Char): Boolean {
        return char.code in 0xF000..0xF0FF
    }
    
    /**
     * 检查 code point 是否在 PUA 范围内 (U+F000-U+F0FF)
     */
    fun isPUACodePoint(codePoint: Int): Boolean {
        return codePoint in 0xF000..0xF0FF
    }
    
    /**
     * ZapfDingbatsEncoding - PDF 32000-1:2008 Annex D.6
     * ZapfDingbats 字体的内置编码，包含装饰符号
     */
    private val zapfDingbatsEncoding = mapOf(
        0x20 to '\u0020', // space
        0x21 to '\u2701', // a1 (✁)
        0x22 to '\u2702', // a2 (✂)
        0x23 to '\u2703', // a202 (✃)
        0x24 to '\u2704', // a3 (✄)
        0x25 to '\u260E', // a4 (☎)
        0x26 to '\u2706', // a5 (✆)
        0x27 to '\u2707', // a119 (✇)
        0x28 to '\u2708', // a118 (✈)
        0x29 to '\u2709', // a117 (✉)
        0x2A to '\u261B', // a11 (☛)
        0x2B to '\u261E', // a12 (☞)
        0x2C to '\u270C', // a13 (✌)
        0x2D to '\u270D', // a14 (✍)
        0x2E to '\u270E', // a15 (✎)
        0x2F to '\u270F', // a16 (✏)
        0x30 to '\u2710', // a105 (✐)
        0x31 to '\u2711', // a17 (✑)
        0x32 to '\u2712', // a18 (✒)
        0x33 to '\u2713', // a19 (✓)
        0x34 to '\u2714', // a20 (✔)
        0x35 to '\u2715', // a21 (✕)
        0x36 to '\u2716', // a22 (✖)
        0x37 to '\u2717', // a23 (✗)
        0x38 to '\u2718', // a24 (✘)
        0x39 to '\u2719', // a25 (✙)
        0x3A to '\u271A', // a26 (✚)
        0x3B to '\u271B', // a27 (✛)
        0x3C to '\u271C', // a28 (✜)
        0x3D to '\u271D', // a6 (✝)
        0x3E to '\u271E', // a7 (✞)
        0x3F to '\u271F', // a8 (✟)
        0x40 to '\u2720', // a9 (✠)
        0x41 to '\u2721', // a10 (✡)
        0x42 to '\u2722', // a29 (✢)
        0x43 to '\u2723', // a30 (✣)
        0x44 to '\u2724', // a31 (✤)
        0x45 to '\u2725', // a32 (✥)
        0x46 to '\u2726', // a33 (✦)
        0x47 to '\u2727', // a34 (✧)
        0x48 to '\u2605', // a35 (★)
        0x49 to '\u2729', // a36 (✩)
        0x4A to '\u272A', // a37 (✪)
        0x4B to '\u272B', // a38 (✫)
        0x4C to '\u272C', // a39 (✬)
        0x4D to '\u272D', // a40 (✭)
        0x4E to '\u272E', // a41 (✮)
        0x4F to '\u272F', // a42 (✯)
        0x50 to '\u2730', // a43 (✰)
        0x51 to '\u2731', // a44 (✱)
        0x52 to '\u2732', // a45 (✲)
        0x53 to '\u2733', // a46 (✳)
        0x54 to '\u2734', // a47 (✴)
        0x55 to '\u2735', // a48 (✵)
        0x56 to '\u2736', // a49 (✶)
        0x57 to '\u2737', // a50 (✷)
        0x58 to '\u2738', // a51 (✸)
        0x59 to '\u2739', // a52 (✹)
        0x5A to '\u273A', // a53 (✺)
        0x5B to '\u273B', // a54 (✻)
        0x5C to '\u273C', // a55 (✼)
        0x5D to '\u273D', // a56 (✽)
        0x5E to '\u273E', // a57 (✾)
        0x5F to '\u273F', // a58 (✿)
        0x60 to '\u2740', // a59 (❀)
        0x61 to '\u2741', // a60 (❁)
        0x62 to '\u2742', // a61 (❂)
        0x63 to '\u2743', // a62 (❃)
        0x64 to '\u2744', // a63 (❄)
        0x65 to '\u2745', // a64 (❅)
        0x66 to '\u2746', // a65 (❆)
        0x67 to '\u2747', // a66 (❇)
        0x68 to '\u2748', // a67 (❈)
        0x69 to '\u2749', // a68 (❉)
        0x6A to '\u274A', // a69 (❊)
        0x6B to '\u274B', // a70 (❋)
        0x6C to '\u25CF', // a71 (●)
        0x6D to '\u274D', // a72 (❍)
        0x6E to '\u25A0', // a73 (■)
        0x6F to '\u274F', // a74 (❏)
        0x70 to '\u2750', // a203 (❐)
        0x71 to '\u2751', // a75 (❑)
        0x72 to '\u2752', // a204 (❒)
        0x73 to '\u25B2', // a76 (▲)
        0x74 to '\u25BC', // a77 (▼)
        0x75 to '\u25C6', // a78 (◆)
        0x76 to '\u2756', // a79 (❖)
        0x77 to '\u25D7', // a81 (◗)
        0x78 to '\u2758', // a82 (❘)
        0x79 to '\u2759', // a83 (❙)
        0x7A to '\u275A', // a84 (❚)
        0x7B to '\u275B', // a97 (❛)
        0x7C to '\u275C', // a98 (❜)
        0x7D to '\u275D', // a99 (❝)
        0x7E to '\u275E', // a100 (❞)
        // 扩展区域 0x80-0xFF
        0x80 to '\u2768', // a89 (❨)
        0x81 to '\u2769', // a90 (❩)
        0x82 to '\u276A', // a93 (❪)
        0x83 to '\u276B', // a94 (❫)
        0x84 to '\u276C', // a91 (❬)
        0x85 to '\u276D', // a92 (❭)
        0x86 to '\u276E', // a205 (❮)
        0x87 to '\u276F', // a85 (❯)
        0x88 to '\u2770', // a206 (❰)
        0x89 to '\u2771', // a86 (❱)
        0x8A to '\u2772', // a87 (❲)
        0x8B to '\u2773', // a88 (❳)
        0x8C to '\u2774', // a95 (❴)
        0x8D to '\u2775', // a96 (❵)
        0xA1 to '\u2761', // a101 (❡)
        0xA2 to '\u2762', // a102 (❢)
        0xA3 to '\u2763', // a103 (❣)
        0xA4 to '\u2764', // a104 (❤)
        0xA5 to '\u2765', // a106 (❥)
        0xA6 to '\u2766', // a107 (❦)
        0xA7 to '\u2767', // a108 (❧)
        0xA8 to '\u2663', // a112 (♣)
        0xA9 to '\u2666', // a111 (♦)
        0xAA to '\u2665', // a110 (♥)
        0xAB to '\u2660', // a109 (♠)
        0xAC to '\u2460', // a120 (①)
        0xAD to '\u2461', // a121 (②)
        0xAE to '\u2462', // a122 (③)
        0xAF to '\u2463', // a123 (④)
        0xB0 to '\u2464', // a124 (⑤)
        0xB1 to '\u2465', // a125 (⑥)
        0xB2 to '\u2466', // a126 (⑦)
        0xB3 to '\u2467', // a127 (⑧)
        0xB4 to '\u2468', // a128 (⑨)
        0xB5 to '\u2469', // a129 (⑩)
        0xB6 to '\u2776', // a130 (❶)
        0xB7 to '\u2777', // a131 (❷)
        0xB8 to '\u2778', // a132 (❸)
        0xB9 to '\u2779', // a133 (❹)
        0xBA to '\u277A', // a134 (❺)
        0xBB to '\u277B', // a135 (❻)
        0xBC to '\u277C', // a136 (❼)
        0xBD to '\u277D', // a137 (❽)
        0xBE to '\u277E', // a138 (❾)
        0xBF to '\u277F', // a139 (❿)
        0xC0 to '\u2780', // a140 (➀)
        0xC1 to '\u2781', // a141 (➁)
        0xC2 to '\u2782', // a142 (➂)
        0xC3 to '\u2783', // a143 (➃)
        0xC4 to '\u2784', // a144 (➄)
        0xC5 to '\u2785', // a145 (➅)
        0xC6 to '\u2786', // a146 (➆)
        0xC7 to '\u2787', // a147 (➇)
        0xC8 to '\u2788', // a148 (➈)
        0xC9 to '\u2789', // a149 (➉)
        0xCA to '\u278A', // a150 (➊)
        0xCB to '\u278B', // a151 (➋)
        0xCC to '\u278C', // a152 (➌)
        0xCD to '\u278D', // a153 (➍)
        0xCE to '\u278E', // a154 (➎)
        0xCF to '\u278F', // a155 (➏)
        0xD0 to '\u2790', // a156 (➐)
        0xD1 to '\u2791', // a157 (➑)
        0xD2 to '\u2792', // a158 (➒)
        0xD3 to '\u2793', // a159 (➓)
        0xD4 to '\u2794', // a160 (➔)
        0xD5 to '\u2192', // a161 (→)
        0xD6 to '\u2194', // a163 (↔)
        0xD7 to '\u2195', // a164 (↕)
        0xD8 to '\u2798', // a196 (➘)
        0xD9 to '\u2799', // a165 (➙)
        0xDA to '\u279A', // a192 (➚)
        0xDB to '\u279B', // a166 (➛)
        0xDC to '\u279C', // a167 (➜)
        0xDD to '\u279D', // a168 (➝)
        0xDE to '\u279E', // a169 (➞)
        0xDF to '\u279F', // a170 (➟)
        0xE0 to '\u27A0', // a171 (➠)
        0xE1 to '\u27A1', // a172 (➡)
        0xE2 to '\u27A2', // a173 (➢)
        0xE3 to '\u27A3', // a162 (➣)
        0xE4 to '\u27A4', // a174 (➤)
        0xE5 to '\u27A5', // a175 (➥)
        0xE6 to '\u27A6', // a176 (➦)
        0xE7 to '\u27A7', // a177 (➧)
        0xE8 to '\u27A8', // a178 (➨)
        0xE9 to '\u27A9', // a179 (➩)
        0xEA to '\u27AA', // a193 (➪)
        0xEB to '\u27AB', // a180 (➫)
        0xEC to '\u27AC', // a199 (➬)
        0xED to '\u27AD', // a181 (➭)
        0xEE to '\u27AE', // a200 (➮)
        0xEF to '\u27AF', // a182 (➯)
        0xF1 to '\u27B1', // a201 (➱)
        0xF2 to '\u27B2', // a183 (➲)
        0xF3 to '\u27B3', // a184 (➳)
        0xF4 to '\u27B4', // a197 (➴)
        0xF5 to '\u27B5', // a185 (➵)
        0xF6 to '\u27B6', // a194 (➶)
        0xF7 to '\u27B7', // a198 (➷)
        0xF8 to '\u27B8', // a186 (➸)
        0xF9 to '\u27B9', // a195 (➹)
        0xFA to '\u27BA', // a187 (➺)
        0xFB to '\u27BB', // a188 (➻)
        0xFC to '\u27BC', // a189 (➼)
        0xFD to '\u27BD', // a190 (➽)
        0xFE to '\u27BE'  // a191 (➾)
    )
    
    // ==================== TeX 数学字体编码 ====================
    
    /**
     * CMR (Computer Modern Roman) 编码
     * TeX 默认罗马字体
     */
    private val cmrEncoding = mapOf(
        0x00 to '\u0393', // Gamma
        0x01 to '\u0394', // Delta
        0x02 to '\u0398', // Theta
        0x03 to '\u039B', // Lambda
        0x04 to '\u039E', // Xi
        0x05 to '\u03A0', // Pi
        0x06 to '\u03A3', // Sigma
        0x07 to '\u03A5', // Upsilon
        0x08 to '\u03A6', // Phi
        0x09 to '\u03A8', // Psi
        0x0A to '\u03A9', // Omega
        0x0B to '\uFB00', // ff ligature
        0x0C to '\uFB01', // fi ligature
        0x0D to '\uFB02', // fl ligature
        0x0E to '\uFB03', // ffi ligature
        0x0F to '\uFB04', // ffl ligature
        0x10 to '\u0131', // dotless i
        0x11 to '\u0237', // dotless j (ȷ)
        0x12 to '\u0060', // grave accent
        0x13 to '\u00B4', // acute accent
        0x14 to '\u02C7', // caron
        0x15 to '\u02D8', // breve
        0x16 to '\u00AF', // macron
        0x17 to '\u02DA', // ring above
        0x18 to '\u00B8', // cedilla
        0x19 to '\u00DF', // germandbls
        0x1A to '\u00E6', // ae
        0x1B to '\u0153', // oe
        0x1C to '\u00F8', // oslash
        0x1D to '\u00C6', // AE
        0x1E to '\u0152', // OE
        0x1F to '\u00D8', // Oslash
        // 0x20-0x7E: Standard ASCII (handled by default)
        0x7F to '\u00A8', // dieresis
        // 0x80+: 扩展字符
    )
    
    /**
     * CMMI (Computer Modern Math Italic) 编码
     * TeX 数学斜体字体，用于变量和函数名
     */
    private val cmmiEncoding = mapOf(
        // 希腊大写字母 (斜体)
        0x00 to '\u0393', // Gamma (Γ)
        0x01 to '\u0394', // Delta (Δ)
        0x02 to '\u0398', // Theta (Θ)
        0x03 to '\u039B', // Lambda (Λ)
        0x04 to '\u039E', // Xi (Ξ)
        0x05 to '\u03A0', // Pi (Π)
        0x06 to '\u03A3', // Sigma (Σ)
        0x07 to '\u03A5', // Upsilon (Υ)
        0x08 to '\u03A6', // Phi (Φ)
        0x09 to '\u03A8', // Psi (Ψ)
        0x0A to '\u03A9', // Omega (Ω)
        // 希腊小写字母
        0x0B to '\u03B1', // alpha (α)
        0x0C to '\u03B2', // beta (β)
        0x0D to '\u03B3', // gamma (γ)
        0x0E to '\u03B4', // delta (δ)
        0x0F to '\u03B5', // epsilon (ε)
        0x10 to '\u03B6', // zeta (ζ)
        0x11 to '\u03B7', // eta (η)
        0x12 to '\u03B8', // theta (θ)
        0x13 to '\u03B9', // iota (ι)
        0x14 to '\u03BA', // kappa (κ)
        0x15 to '\u03BB', // lambda (λ)
        0x16 to '\u03BC', // mu (μ)
        0x17 to '\u03BD', // nu (ν)
        0x18 to '\u03BE', // xi (ξ)
        0x19 to '\u03C0', // pi (π)
        0x1A to '\u03C1', // rho (ρ)
        0x1B to '\u03C3', // sigma (σ)
        0x1C to '\u03C4', // tau (τ)
        0x1D to '\u03C5', // upsilon (υ)
        0x1E to '\u03C6', // phi (φ)
        0x1F to '\u03C7', // chi (χ)
        0x20 to '\u03C8', // psi (ψ)
        0x21 to '\u03C9', // omega (ω)
        0x22 to '\u03B5', // varepsilon (ε 变体)
        0x23 to '\u03D1', // vartheta (ϑ)
        0x24 to '\u03D6', // varpi (ϖ)
        0x25 to '\u03F1', // varrho (ϱ)
        0x26 to '\u03C2', // varsigma (ς)
        0x27 to '\u03D5', // varphi (ϕ)
        // 符号
        0x28 to '\u21BC', // leftharpoonup (↼)
        0x29 to '\u21BD', // leftharpoondown (↽)
        0x2A to '\u21C0', // rightharpoonup (⇀)
        0x2B to '\u21C1', // rightharpoondown (⇁)
        0x2C to '\u02CB', // lhook
        0x2D to '\u02CA', // rhook
        0x2E to '\u25B7', // triangleright (▷)
        0x2F to '\u25C1', // triangleleft (◁)
        // 0x30-0x39: 数字 (斜体)
        0x30 to '0',
        0x31 to '1',
        0x32 to '2',
        0x33 to '3',
        0x34 to '4',
        0x35 to '5',
        0x36 to '6',
        0x37 to '7',
        0x38 to '8',
        0x39 to '9',
        // 标点和符号
        0x3A to '\u002E', // period.math
        0x3B to '\u002C', // comma.math
        0x3C to '\u003C', // less
        0x3D to '\u002F', // slash
        0x3E to '\u003E', // greater
        0x3F to '\u22C6', // star (⋆)
        0x40 to '\u2202', // partial (∂)
        // 大写字母 (斜体)
        0x41 to 'A', 0x42 to 'B', 0x43 to 'C', 0x44 to 'D',
        0x45 to 'E', 0x46 to 'F', 0x47 to 'G', 0x48 to 'H',
        0x49 to 'I', 0x4A to 'J', 0x4B to 'K', 0x4C to 'L',
        0x4D to 'M', 0x4E to 'N', 0x4F to 'O', 0x50 to 'P',
        0x51 to 'Q', 0x52 to 'R', 0x53 to 'S', 0x54 to 'T',
        0x55 to 'U', 0x56 to 'V', 0x57 to 'W', 0x58 to 'X',
        0x59 to 'Y', 0x5A to 'Z',
        0x5B to '\u266D', // flat (♭)
        0x5C to '\u266E', // natural (♮)
        0x5D to '\u266F', // sharp (♯)
        0x5E to '\u2323', // smile (⌣)
        0x5F to '\u2322', // frown (⌢)
        0x60 to '\u2113', // ell (ℓ)
        // 小写字母 (斜体)
        0x61 to 'a', 0x62 to 'b', 0x63 to 'c', 0x64 to 'd',
        0x65 to 'e', 0x66 to 'f', 0x67 to 'g', 0x68 to 'h',
        0x69 to 'i', 0x6A to 'j', 0x6B to 'k', 0x6C to 'l',
        0x6D to 'm', 0x6E to 'n', 0x6F to 'o', 0x70 to 'p',
        0x71 to 'q', 0x72 to 'r', 0x73 to 's', 0x74 to 't',
        0x75 to 'u', 0x76 to 'v', 0x77 to 'w', 0x78 to 'x',
        0x79 to 'y', 0x7A to 'z',
        0x7B to '\u0131', // dotlessi (ı)
        0x7C to '\u0237', // dotlessj (ȷ)
        0x7D to '\u2118', // weierstrass (℘)
        0x7E to '\u20D7', // vector accent (⃗)
        0x7F to '\u02D9', // dot accent
    )
    
    /**
     * CMSY (Computer Modern Math Symbols) 编码
     * TeX 数学符号字体
     */
    private val cmsyEncoding = mapOf(
        0x00 to '\u2212', // minus (−)
        0x01 to '\u22C5', // cdot (⋅)
        0x02 to '\u00D7', // times (×)
        0x03 to '\u2217', // ast (∗)
        0x04 to '\u00F7', // div (÷)
        0x05 to '\u22C4', // diamond (⋄)
        0x06 to '\u00B1', // pm (±)
        0x07 to '\u2213', // mp (∓)
        0x08 to '\u2295', // oplus (⊕)
        0x09 to '\u2296', // ominus (⊖)
        0x0A to '\u2297', // otimes (⊗)
        0x0B to '\u2298', // oslash (⊘)
        0x0C to '\u2299', // odot (⊙)
        0x0D to '\u25CB', // bigcirc (○)
        0x0E to '\u2218', // circ (∘)
        0x0F to '\u2219', // bullet (∙)
        0x10 to '\u224D', // asymp (≍)
        0x11 to '\u2261', // equiv (≡)
        0x12 to '\u2286', // subseteq (⊆)
        0x13 to '\u2287', // supseteq (⊇)
        0x14 to '\u2264', // leq (≤)
        0x15 to '\u2265', // geq (≥)
        0x16 to '\u2AAF', // preceq (⪯)
        0x17 to '\u2AB0', // succeq (⪰)
        0x18 to '\u223C', // sim (∼)
        0x19 to '\u2248', // approx (≈)
        0x1A to '\u2282', // subset (⊂)
        0x1B to '\u2283', // supset (⊃)
        0x1C to '\u226A', // ll (≪)
        0x1D to '\u226B', // gg (≫)
        0x1E to '\u227A', // prec (≺)
        0x1F to '\u227B', // succ (≻)
        0x20 to '\u2190', // leftarrow (←)
        0x21 to '\u2192', // rightarrow (→)
        0x22 to '\u2191', // uparrow (↑)
        0x23 to '\u2193', // downarrow (↓)
        0x24 to '\u2194', // leftrightarrow (↔)
        0x25 to '\u2197', // nearrow (↗)
        0x26 to '\u2198', // searrow (↘)
        0x27 to '\u2243', // simeq (≃)
        0x28 to '\u21D0', // Leftarrow (⇐)
        0x29 to '\u21D2', // Rightarrow (⇒)
        0x2A to '\u21D1', // Uparrow (⇑)
        0x2B to '\u21D3', // Downarrow (⇓)
        0x2C to '\u21D4', // Leftrightarrow (⇔)
        0x2D to '\u2196', // nwarrow (↖)
        0x2E to '\u2199', // swarrow (↙)
        0x2F to '\u221D', // propto (∝)
        0x30 to '\u2032', // prime (′)
        0x31 to '\u221E', // infty (∞)
        0x32 to '\u2208', // in (∈)
        0x33 to '\u220B', // ni (∋)
        0x34 to '\u25B3', // bigtriangleup (△)
        0x35 to '\u25BD', // bigtriangledown (▽)
        0x36 to '\u002F', // slash
        0x37 to '\u0027', // mapsto (based on context)
        0x38 to '\u2200', // forall (∀)
        0x39 to '\u2203', // exists (∃)
        0x3A to '\u00AC', // neg (¬)
        0x3B to '\u2205', // emptyset (∅)
        0x3C to '\u211C', // Re (ℜ)
        0x3D to '\u2111', // Im (ℑ)
        0x3E to '\u22A4', // top (⊤)
        0x3F to '\u22A5', // bot (⊥)
        0x40 to '\u2135', // aleph (ℵ)
        // Calligraphic/Script letters A-Z
        // 部分字符在 BMP 内有对应字符，其他使用普通字母作为回退
        // 完整的 Mathematical Script 字符在 U+1D400-1D7FF 范围，超出 Char 范围
        0x41 to 'A', // 𝒜 (Mathematical Script A - 使用回退)
        0x42 to '\u212C', // ℬ (Script Capital B)
        0x43 to 'C', // 𝒞 (使用回退)
        0x44 to 'D', // 𝒟 (使用回退)
        0x45 to '\u2130', // ℰ (Script Capital E)
        0x46 to '\u2131', // ℱ (Script Capital F)
        0x47 to 'G', // 𝒢 (使用回退)
        0x48 to '\u210B', // ℋ (Script Capital H)
        0x49 to '\u2110', // ℐ (Script Capital I)
        0x4A to 'J', // 𝒥 (使用回退)
        0x4B to 'K', // 𝒦 (使用回退)
        0x4C to '\u2112', // ℒ (Script Capital L)
        0x4D to '\u2133', // ℳ (Script Capital M)
        0x4E to 'N', // 𝒩 (使用回退)
        0x4F to 'O', // 𝒪 (使用回退)
        0x50 to 'P', // 𝒫 (使用回退)
        0x51 to 'Q', // 𝒬 (使用回退)
        0x52 to '\u211B', // ℛ (Script Capital R)
        0x53 to 'S', // 𝒮 (使用回退)
        0x54 to 'T', // 𝒯 (使用回退)
        0x55 to 'U', // 𝒰 (使用回退)
        0x56 to 'V', // 𝒱 (使用回退)
        0x57 to 'W', // 𝒲 (使用回退)
        0x58 to 'X', // 𝒳 (使用回退)
        0x59 to 'Y', // 𝒴 (使用回退)
        0x5A to 'Z', // 𝒵 (使用回退)
        0x5B to '\u222A', // cup (∪)
        0x5C to '\u2229', // cap (∩)
        0x5D to '\u228E', // uplus (⊎)
        0x5E to '\u2227', // land/wedge (∧)
        0x5F to '\u2228', // lor/vee (∨)
        0x60 to '\u22A2', // vdash (⊢)
        0x61 to '\u22A3', // dashv (⊣)
        0x62 to '\u230A', // lfloor (⌊)
        0x63 to '\u230B', // rfloor (⌋)
        0x64 to '\u2308', // lceil (⌈)
        0x65 to '\u2309', // rceil (⌉)
        0x66 to '\u007B', // lbrace ({)
        0x67 to '\u007D', // rbrace (})
        0x68 to '\u27E8', // langle (⟨)
        0x69 to '\u27E9', // rangle (⟩)
        0x6A to '\u007C', // vert (|)
        0x6B to '\u2016', // Vert (‖)
        0x6C to '\u2195', // updownarrow (↕)
        0x6D to '\u21D5', // Updownarrow (⇕)
        0x6E to '\u005C', // backslash (\)
        0x6F to '\u2240', // wr (≀)
        0x70 to '\u221A', // surd (√)
        0x71 to '\u2A3F', // amalg (⨿)
        0x72 to '\u2207', // nabla (∇)
        0x73 to '\u222B', // int (∫)
        0x74 to '\u2294', // sqcup (⊔)
        0x75 to '\u2293', // sqcap (⊓)
        0x76 to '\u2291', // sqsubseteq (⊑)
        0x77 to '\u2292', // sqsupseteq (⊒)
        0x78 to '\u00A7', // S (§)
        0x79 to '\u2020', // dag (†)
        0x7A to '\u2021', // ddag (‡)
        0x7B to '\u00B6', // P (¶)
        0x7C to '\u2663', // clubsuit (♣)
        0x7D to '\u2662', // diamondsuit (♢)
        0x7E to '\u2661', // heartsuit (♡)
        0x7F to '\u2660', // spadesuit (♠)
    )
    
    /**
     * CMEX (Computer Modern Math Extension) 编码
     * TeX 大型数学符号字体（积分、求和、大括号等）
     */
    private val cmexEncoding = mapOf(
        // 左括号 (多种大小)
        0x00 to '\u0028', // ( small
        0x10 to '\u0028', // ( medium
        0x12 to '\u0028', // ( large
        0x20 to '\u0028', // ( big
        0x30 to '\u239B', // ⎛ extensible top
        0x32 to '\u239D', // ⎝ extensible bottom
        0x3E to '\u239C', // ⎜ extensible middle
        
        // 右括号 (多种大小)
        0x01 to '\u0029', // ) small
        0x11 to '\u0029', // ) medium
        0x13 to '\u0029', // ) large
        0x21 to '\u0029', // ) big
        0x31 to '\u239E', // ⎞ extensible top
        0x33 to '\u23A0', // ⎠ extensible bottom
        0x3F to '\u239F', // ⎟ extensible middle
        
        // 方括号
        0x02 to '\u005B', // [ small
        0x03 to '\u005D', // ] small
        0x22 to '\u005B', // [ big
        0x23 to '\u005D', // ] big
        0x34 to '\u23A1', // ⎡ extensible top
        0x35 to '\u23A4', // ⎤ extensible top
        0x36 to '\u23A3', // ⎣ extensible bottom
        0x37 to '\u23A6', // ⎦ extensible bottom
        0x3C to '\u23A2', // ⎢ extensible middle
        0x3D to '\u23A5', // ⎥ extensible middle
        
        // 花括号
        0x08 to '\u007B', // { small
        0x09 to '\u007D', // } small
        0x28 to '\u007B', // { big
        0x29 to '\u007D', // } big
        0x38 to '\u23A7', // ⎧ extensible top
        0x39 to '\u23AB', // ⎫ extensible top
        0x3A to '\u23A9', // ⎩ extensible bottom
        0x3B to '\u23AD', // ⎭ extensible bottom
        0x3E to '\u23A8', // ⎨ extensible middle
        0x3F to '\u23AC', // ⎬ extensible middle
        0x40 to '\u23AA', // ⎪ extensible extension
        
        // 尖括号
        0x0A to '\u27E8', // ⟨ small
        0x0B to '\u27E9', // ⟩ small
        0x2A to '\u27E8', // ⟨ big
        0x2B to '\u27E9', // ⟩ big
        
        // 竖线和双竖线
        0x0C to '\u007C', // | small
        0x0D to '\u2016', // ‖ small
        0x2C to '\u007C', // | big
        0x2D to '\u2016', // ‖ big
        
        // 斜线
        0x0E to '\u002F', // / small
        0x0F to '\u005C', // \ small
        0x2E to '\u002F', // / big
        0x2F to '\u005C', // \ big
        
        // 大型运算符
        0x50 to '\u2211', // ∑ (summation) - display
        0x58 to '\u2211', // ∑ (summation) - text
        0x51 to '\u220F', // ∏ (product) - display
        0x59 to '\u220F', // ∏ (product) - text
        0x52 to '\u222B', // ∫ (integral) - display
        0x5A to '\u222B', // ∫ (integral) - text
        0x53 to '\u22C3', // ⋃ (union) - display
        0x5B to '\u22C3', // ⋃ (union) - text
        0x54 to '\u22C2', // ⋂ (intersection) - display
        0x5C to '\u22C2', // ⋂ (intersection) - text
        0x55 to '\u2A04', // ⨄ (multiset union) - display
        0x5D to '\u2A04', // ⨄ (multiset union) - text
        0x56 to '\u2227', // ∧ (logical and) - display
        0x5E to '\u2227', // ∧ (logical and) - text
        0x57 to '\u2228', // ∨ (logical or) - display
        0x5F to '\u2228', // ∨ (logical or) - text
        
        // 更多大型运算符
        0x60 to '\u2A00', // ⨀ (circled dot) - display
        0x68 to '\u2A00', // ⨀ (circled dot) - text
        0x61 to '\u2A01', // ⨁ (circled plus) - display
        0x69 to '\u2A01', // ⨁ (circled plus) - text
        0x62 to '\u2A02', // ⨂ (circled times) - display
        0x6A to '\u2A02', // ⨂ (circled times) - text
        0x63 to '\u2A06', // ⨆ (square union) - display
        0x6B to '\u2A06', // ⨆ (square union) - text
        
        // 积分变体
        0x64 to '\u222E', // ∮ (contour integral)
        0x65 to '\u222F', // ∯ (surface integral)
        0x66 to '\u2230', // ∰ (volume integral)
        
        // 根号
        0x70 to '\u221A', // √ (radical) - small
        0x71 to '\u221A', // √ (radical) - medium
        0x72 to '\u221A', // √ (radical) - large
        0x73 to '\u221A', // √ (radical) - big
        
        // 箭头
        0x78 to '\u2190', // ← extensible
        0x79 to '\u2192', // → extensible
        0x7A to '\u21D0', // ⇐ extensible
        0x7B to '\u21D2', // ⇒ extensible
        0x7C to '\u2194', // ↔ extensible
        0x7D to '\u21D4', // ⇔ extensible
        0x7E to '\u2195', // ↕
        0x7F to '\u21D5', // ⇕
    )
    
    /**
     * Wingdings 编码
     * Microsoft Wingdings 字体的字符映射
     * 常用于 PowerPoint/Word 文档中的项目符号和装饰符号
     * 
     * 参考: https://en.wikipedia.org/wiki/Wingdings
     */
    private val wingdingsEncoding = mapOf(
        // 书写工具 (0x21-0x29)
        0x21 to '\u270F', // ✏ pencil
        0x22 to '\u2702', // ✂ scissors
        0x23 to '\u2701', // ✁ upper blade scissors
        0x24 to '\u2704', // ✄ white scissors
        0x25 to '\u260E', // ☎ telephone
        0x26 to '\u2706', // ✆ telephone location sign
        0x27 to '\u2709', // ✉ envelope
        0x28 to '\u2707', // ✇ tape drive
        0x29 to '\u275D', // ❝ heavy double comma quotation mark ornament
        0x2A to '\u275E', // ❞ heavy double turned comma quotation mark ornament
        
        // 手势 (0x2B-0x30)
        0x2B to '\u270C', // ✌ victory hand
        0x2C to '\u270D', // ✍ writing hand
        0x2D to '\u270E', // ✎ lower right pencil
        0x2E to '\u270A', // ✊ raised fist
        0x2F to '\u270B', // ✋ raised hand
        0x30 to '\u261C', // ☜ white left pointing index
        0x31 to '\u261E', // ☞ white right pointing index
        0x32 to '\u261D', // ☝ white up pointing index
        0x33 to '\u261F', // ☟ white down pointing index
        
        // 手型指示 (0x34-0x37)
        0x34 to '\u270B', // ✋ raised hand (variant)
        0x35 to '\u263A', // ☺ white smiling face
        0x36 to '\u2639', // ☹ white frowning face
        0x37 to '\u263B', // ☻ black smiling face
        
        // 炸弹/骷髅 (0x38-0x3F)
        0x38 to '\u2620', // ☠ skull and crossbones
        0x39 to '\u2690', // ⚐ white flag
        0x3A to '\u2691', // ⚑ black flag
        0x3B to '\u2708', // ✈ airplane
        0x3C to '\u263C', // ☼ white sun with rays
        0x3D to '\u2602', // ☂ umbrella
        0x3E to '\u2744', // ❄ snowflake
        0x3F to '\u271E', // ✞ shadowed white latin cross
        
        // 宗教符号 (0x40-0x47)
        0x40 to '\u271F', // ✟ outlined latin cross
        0x41 to '\u2720', // ✠ maltese cross
        0x42 to '\u2721', // ✡ star of david
        0x43 to '\u262A', // ☪ star and crescent
        0x44 to '\u262F', // ☯ yin yang
        0x45 to '\u2638', // ☸ wheel of dharma
        0x46 to '\u2648', // ♈ aries
        0x47 to '\u2649', // ♉ taurus
        
        // 星座 (0x48-0x53)
        0x48 to '\u264A', // ♊ gemini
        0x49 to '\u264B', // ♋ cancer
        0x4A to '\u264C', // ♌ leo
        0x4B to '\u264D', // ♍ virgo
        0x4C to '\u264E', // ♎ libra
        0x4D to '\u264F', // ♏ scorpio
        0x4E to '\u2650', // ♐ sagittarius
        0x4F to '\u2651', // ♑ capricorn
        0x50 to '\u2652', // ♒ aquarius
        0x51 to '\u2653', // ♓ pisces
        0x52 to '\u0026', // & ampersand
        0x53 to '\u0026', // & ampersand (variant)
        
        // 圆圈数字 (0x54-0x5D)
        0x54 to '\u2460', // ① circled digit one
        0x55 to '\u2461', // ② circled digit two
        0x56 to '\u2462', // ③ circled digit three
        0x57 to '\u2463', // ④ circled digit four
        0x58 to '\u2464', // ⑤ circled digit five
        0x59 to '\u2465', // ⑥ circled digit six
        0x5A to '\u2466', // ⑦ circled digit seven
        0x5B to '\u2467', // ⑧ circled digit eight
        0x5C to '\u2468', // ⑨ circled digit nine
        0x5D to '\u2469', // ⑩ circled digit ten
        
        // 负圆圈数字 (0x5E-0x67)
        0x5E to '\u2776', // ❶ dingbat negative circled digit one
        0x5F to '\u2777', // ❷ dingbat negative circled digit two
        0x60 to '\u2778', // ❸ dingbat negative circled digit three
        0x61 to '\u2779', // ❹ dingbat negative circled digit four
        0x62 to '\u277A', // ❺ dingbat negative circled digit five
        0x63 to '\u277B', // ❻ dingbat negative circled digit six
        0x64 to '\u277C', // ❼ dingbat negative circled digit seven
        0x65 to '\u277D', // ❽ dingbat negative circled digit eight
        0x66 to '\u277E', // ❾ dingbat negative circled digit nine
        0x67 to '\u277F', // ❿ dingbat negative circled digit ten
        
        // 项目符号 - 圆形 (0x6C-0x6F)
        0x6C to '\u25CF', // ● black circle
        0x6D to '\u274D', // ❍ shadowed white circle
        0x6E to '\u25A0', // ■ black square
        0x6F to '\u25A1', // □ white square
        
        // 项目符号 - 方形/菱形 (0x70-0x77)
        0x70 to '\u2751', // ❑ lower right shadowed white square
        0x71 to '\u2752', // ❒ upper right shadowed white square
        0x72 to '\u2B27', // ⬧ black medium lozenge
        0x73 to '\u29EB', // ⧫ black lozenge
        0x74 to '\u25C6', // ◆ black diamond
        0x75 to '\u2756', // ❖ black diamond minus white x
        0x76 to '\u2318', // ⌘ place of interest sign
        0x77 to '\u2714', // ✔ heavy check mark
        
        // 复选框和 X (0x78-0x7F)
        0x78 to '\u2718', // ✘ heavy ballot x
        0x79 to '\u2715', // ✕ multiplication x
        0x7A to '\u2716', // ✖ heavy multiplication x
        0x7B to '\u2717', // ✗ ballot x
        0x7C to '\u2713', // ✓ check mark
        0x7D to '\u2612', // ☒ ballot box with x
        0x7E to '\u2611', // ☑ ballot box with check
        0x7F to '\u2610', // ☐ ballot box
        
        // 星形 (0x80-0x8F)
        0x80 to '\u2605', // ★ black star
        0x81 to '\u2606', // ☆ white star
        0x82 to '\u2736', // ✶ six pointed black star
        0x83 to '\u2735', // ✵ eight pointed pinwheel star
        0x84 to '\u2739', // ✹ twelve pointed black star
        0x85 to '\u2734', // ✴ eight pointed black star
        0x86 to '\u2733', // ✳ eight spoked asterisk
        0x87 to '\u2747', // ❇ sparkle
        0x88 to '\u2748', // ❈ heavy sparkle
        0x89 to '\u274A', // ❊ eight teardrop-spoked propeller asterisk
        0x8A to '\u274B', // ❋ heavy eight teardrop-spoked propeller asterisk
        
        // 更多符号 (0x8B-0x97)
        0x8B to '\u2726', // ✦ black four pointed star
        0x8C to '\u2725', // ✥ four club-spoked asterisk
        0x8D to '\u2749', // ❉ balloon-spoked asterisk
        0x8E to '\u2724', // ✤ heavy four balloon-spoked asterisk
        0x8F to '\u2727', // ✧ white four pointed star
        0x90 to '\u2742', // ❂ circled open centre eight pointed star
        0x91 to '\u2743', // ❃ heavy teardrop-spoked asterisk
        0x92 to '\u2744', // ❄ snowflake
        0x93 to '\u2745', // ❅ tight trifoliate snowflake
        0x94 to '\u2746', // ❆ heavy chevron snowflake
        
        // 箭头 - 常用项目符号 (0xD5-0xEF)
        0xD5 to '\u232B', // ⌫ erase to the left
        0xD6 to '\u2326', // ⌦ erase to the right
        0xD7 to '\u27A2', // ➢ three-d top-lighted rightwards arrowhead
        0xD8 to '\u27A4', // ➤ black rightwards arrowhead (常用项目符号)
        0xD9 to '\u27A6', // ➦ heavy round-tipped rightwards arrow
        0xDA to '\u27A8', // ➨ heavy concave-pointed black rightwards arrow
        0xDB to '\u27A1', // ➡ black rightwards arrow
        0xDC to '\u2794', // ➔ heavy wide-headed rightwards arrow (常用项目符号)
        0xDD to '\u27A7', // ➧ squat black rightwards arrow
        0xDE to '\u27A9', // ➩ white concave-sided diamond
        0xDF to '\u27AA', // ➪ black curved downwards and rightwards arrow
        0xE0 to '\u27AB', // ➫ black curved upwards and rightwards arrow
        0xE1 to '\u27AC', // ➬ squat white rightwards arrow
        0xE2 to '\u27AD', // ➭ white concave-sided diamond arrow
        0xE3 to '\u27AE', // ➮ notched upper right-shadowed white rightwards arrow
        0xE4 to '\u27AF', // ➯ curly loop
        
        // 更多箭头 (0xE5-0xEF)
        0xE5 to '\u27B1', // ➱ notched lower right-shadowed white rightwards arrow
        0xE6 to '\u27B2', // ➲ circled heavy white rightwards arrow
        0xE7 to '\u27B3', // ➳ white-feathered rightwards arrow
        0xE8 to '\u27B4', // ➴ black-feathered south east arrow
        0xE9 to '\u27B5', // ➵ black-feathered rightwards arrow
        0xEA to '\u27B6', // ➶ black-feathered north east arrow
        0xEB to '\u27B7', // ➷ heavy black-feathered south east arrow
        0xEC to '\u27B8', // ➸ heavy black-feathered rightwards arrow
        0xED to '\u27B9', // ➹ heavy black-feathered north east arrow
        0xEE to '\u27BA', // ➺ teardrop-barbed rightwards arrow
        0xEF to '\u27BB', // ➻ heavy teardrop-shanked rightwards arrow
        
        // 方向箭头 (0xF0-0xFB)
        0xF0 to '\u27BC', // ➼ wedge-tailed rightwards arrow
        0xF1 to '\u27BD', // ➽ heavy wedge-tailed rightwards arrow
        0xF2 to '\u27BE', // ➾ open-outlined rightwards arrow
        0xF3 to '\u2190', // ← leftwards arrow
        0xF4 to '\u2191', // ↑ upwards arrow
        0xF5 to '\u2192', // → rightwards arrow
        0xF6 to '\u2193', // ↓ downwards arrow
        0xF7 to '\u2196', // ↖ north west arrow
        0xF8 to '\u2197', // ↗ north east arrow
        0xF9 to '\u2198', // ↘ south east arrow
        0xFA to '\u2199', // ↙ south west arrow
        0xFB to '\u21D4', // ⇔ left right double arrow
        0xFC to '\u21D2', // ⇒ rightwards double arrow
        0xFD to '\u21D1', // ⇑ upwards double arrow
        0xFE to '\u21D0', // ⇐ leftwards double arrow
        0xFF to '\u21D3', // ⇓ downwards double arrow
        
        // 边框/表格元素 (0x9A-0xA7)
        0x9A to '\u25B2', // ▲ black up-pointing triangle
        0x9B to '\u25BC', // ▼ black down-pointing triangle
        0x9C to '\u25C0', // ◀ black left-pointing triangle
        0x9D to '\u25B6', // ▶ black right-pointing triangle
        0x9E to '\u25B3', // △ white up-pointing triangle
        0x9F to '\u25BD', // ▽ white down-pointing triangle
        0xA0 to '\u25C1', // ◁ white left-pointing triangle
        0xA1 to '\u25B7', // ▷ white right-pointing triangle
        
        // 几何形状 (0xA2-0xD4)
        0xA2 to '\u25C9', // ◉ fisheye
        0xA3 to '\u25CE', // ◎ bullseye
        0xA4 to '\u25AA', // ▪ black small square
        0xA5 to '\u25FB', // ◻ white medium square
        0xA6 to '\u25AB', // ▫ white small square
        0xA7 to '\u2B1B', // ⬛ black large square
    )
}

/**
 * Adobe Glyph List - 字形名到 Unicode 的映射
 * 基于 Adobe Glyph List For New Fonts (AGLFN)
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
        
        // ==================== 希腊字母 ====================
        // 希腊大写字母
        "Alpha" to '\u0391',
        "Beta" to '\u0392',
        "Gamma" to '\u0393',
        "Delta" to '\u0394',
        "Epsilon" to '\u0395',
        "Zeta" to '\u0396',
        "Eta" to '\u0397',
        "Theta" to '\u0398',
        "Iota" to '\u0399',
        "Kappa" to '\u039A',
        "Lambda" to '\u039B',
        "Mu" to '\u039C',
        "Nu" to '\u039D',
        "Xi" to '\u039E',
        "Omicron" to '\u039F',
        "Pi" to '\u03A0',
        "Rho" to '\u03A1',
        "Sigma" to '\u03A3',
        "Tau" to '\u03A4',
        "Upsilon" to '\u03A5',
        "Phi" to '\u03A6',
        "Chi" to '\u03A7',
        "Psi" to '\u03A8',
        "Omega" to '\u03A9',
        // 希腊小写字母
        "alpha" to '\u03B1',
        "beta" to '\u03B2',
        "gamma" to '\u03B3',
        "delta" to '\u03B4',
        "epsilon" to '\u03B5',
        "zeta" to '\u03B6',
        "eta" to '\u03B7',
        "theta" to '\u03B8',
        "iota" to '\u03B9',
        "kappa" to '\u03BA',
        "lambda" to '\u03BB',
        "mu" to '\u03BC',
        "nu" to '\u03BD',
        "xi" to '\u03BE',
        "omicron" to '\u03BF',
        "pi" to '\u03C0',
        "rho" to '\u03C1',
        "sigma" to '\u03C3',
        "sigma1" to '\u03C2',  // 词尾 sigma (ς)
        "tau" to '\u03C4',
        "upsilon" to '\u03C5',
        "phi" to '\u03C6',
        "chi" to '\u03C7',
        "psi" to '\u03C8',
        "omega" to '\u03C9',
        // 希腊字母变体
        "theta1" to '\u03D1',  // ϑ
        "Upsilon1" to '\u03D2', // ϒ
        "phi1" to '\u03D5',    // ϕ
        "omega1" to '\u03D6',  // ϖ
        
        // ==================== 数学符号 ====================
        // 基本运算符
        "minus" to '\u2212',
        "plusminus" to '\u00B1',
        "multiply" to '\u00D7',
        "divide" to '\u00F7',
        "fraction" to '\u2044',
        "asteriskmath" to '\u2217',
        "dotmath" to '\u22C5',
        
        // 关系运算符
        "equal" to '\u003D',
        "notequal" to '\u2260',
        "equivalence" to '\u2261',
        "approxequal" to '\u2248',
        "lessequal" to '\u2264',
        "greaterequal" to '\u2265',
        "congruent" to '\u2245',
        "similar" to '\u223C',
        "proportional" to '\u221D',
        
        // 集合符号
        "element" to '\u2208',
        "notelement" to '\u2209',
        "suchthat" to '\u220B',
        "emptyset" to '\u2205',
        "propersubset" to '\u2282',
        "propersuperset" to '\u2283',
        "reflexsubset" to '\u2286',
        "reflexsuperset" to '\u2287',
        "notsubset" to '\u2284',
        "intersection" to '\u2229',
        "union" to '\u222A',
        
        // 逻辑符号
        "logicaland" to '\u2227',
        "logicalor" to '\u2228',
        "logicalnot" to '\u00AC',
        "therefore" to '\u2234',
        "existential" to '\u2203',
        "universal" to '\u2200',
        
        // 大型运算符
        "summation" to '\u2211',
        "product" to '\u220F',
        "integral" to '\u222B',
        "radical" to '\u221A',
        
        // 微积分符号
        "partialdiff" to '\u2202',
        "gradient" to '\u2207',
        "nabla" to '\u2207',
        
        // 无穷和其他
        "infinity" to '\u221E',
        "aleph" to '\u2135',
        "weierstrass" to '\u2118',
        "Ifraktur" to '\u2111',
        "Rfraktur" to '\u211C',
        
        // 几何符号
        "angle" to '\u2220',
        "perpendicular" to '\u22A5',
        "angleleft" to '\u2329',
        "angleright" to '\u232A',
        "degree" to '\u00B0',
        "minute" to '\u2032',
        "second" to '\u2033',
        
        // 箭头符号
        "arrowleft" to '\u2190',
        "arrowup" to '\u2191',
        "arrowright" to '\u2192',
        "arrowdown" to '\u2193',
        "arrowboth" to '\u2194',
        "arrowdblleft" to '\u21D0',
        "arrowdblup" to '\u21D1',
        "arrowdblright" to '\u21D2',
        "arrowdbldown" to '\u21D3',
        "arrowdblboth" to '\u21D4',
        "carriagereturn" to '\u21B5',
        
        // 圆形运算符
        "circleplus" to '\u2295',
        "circlemultiply" to '\u2297',
        
        // 扑克牌符号
        "spade" to '\u2660',
        "club" to '\u2663',
        "heart" to '\u2665',
        "diamond" to '\u2666',
        
        // 其他符号
        "lozenge" to '\u25CA',
        
        // ==================== 标点和符号 ====================
        "bullet" to '\u2022',
        "ellipsis" to '\u2026',
        "emdash" to '\u2014',
        "endash" to '\u2013',
        "quoteleft" to '\u2018',
        "quoteright" to '\u2019',
        "quotedblleft" to '\u201C',
        "quotedblright" to '\u201D',
        "quotesinglbase" to '\u201A',
        "quotedblbase" to '\u201E',
        "dagger" to '\u2020',
        "daggerdbl" to '\u2021',
        "perthousand" to '\u2030',
        "guilsinglleft" to '\u2039',
        "guilsinglright" to '\u203A',
        "fi" to '\uFB01',
        "fl" to '\uFB02',
        "ff" to '\uFB00',
        "ffi" to '\uFB03',
        "ffl" to '\uFB04',
        "trademark" to '\u2122',
        "copyright" to '\u00A9',
        "copyrightsans" to '\u00A9',
        "copyrightserif" to '\u00A9',
        "registered" to '\u00AE',
        "registersans" to '\u00AE',
        "registerserif" to '\u00AE',
        "trademarksans" to '\u2122',
        "trademarkserif" to '\u2122',
        "Euro" to '\u20AC',
        "sterling" to '\u00A3',
        "yen" to '\u00A5',
        "cent" to '\u00A2',
        "florin" to '\u0192',
        "section" to '\u00A7',
        "paragraph" to '\u00B6',
        "brokenbar" to '\u00A6',
        "ordfeminine" to '\u00AA',
        "ordmasculine" to '\u00BA',
        "exclamdown" to '\u00A1',
        "questiondown" to '\u00BF',
        
        // 括号相关
        "parenlefttp" to '\u239B',
        "parenleftex" to '\u239C',
        "parenleftbt" to '\u239D',
        "parenrighttp" to '\u239E',
        "parenrightex" to '\u239F',
        "parenrightbt" to '\u23A0',
        "bracketlefttp" to '\u23A1',
        "bracketleftex" to '\u23A2',
        "bracketleftbt" to '\u23A3',
        "bracketrighttp" to '\u23A4',
        "bracketrightex" to '\u23A5',
        "bracketrightbt" to '\u23A6',
        "bracelefttp" to '\u23A7',
        "braceleftmid" to '\u23A8',
        "braceleftbt" to '\u23A9',
        "bracerighttp" to '\u23AB',
        "bracerightmid" to '\u23AC',
        "bracerightbt" to '\u23AD',
        "braceex" to '\u23AA',
        "integraltp" to '\u2320',
        "integralbt" to '\u2321',
        "integralex" to '\u23AE'
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
    
    /**
     * 将 Symbol 字体 PUA 字符 (U+F000-U+F0FF) 转换为标准 Unicode
     * 委托给 StandardEncodings 的实现
     * 
     * @see StandardEncodings.convertPUAToUnicode
     */
    fun convertPUAToUnicode(char: Char): Char? {
        return StandardEncodings.convertPUAToUnicode(char)
    }
}
