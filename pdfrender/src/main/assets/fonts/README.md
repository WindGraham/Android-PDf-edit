# 字体资源目录

此目录用于存放 PDF 渲染所需的备用字体文件。

## 已包含的字体

### 1. 中文衬线字体（宋体风格）
- **文件名**: `NotoSerifCJK-Regular.ttc`
- **来源**: https://github.com/notofonts/noto-cjk
- **用途**: 渲染 PDF 中的宋体、明体、楷体等衬线字体
- **大小**: ~26MB

### 2. 中文无衬线字体（黑体风格）
- **文件名**: `NotoSansCJK-Regular.ttc`
- **来源**: https://github.com/notofonts/noto-cjk
- **用途**: 渲染 PDF 中的黑体、雅黑等无衬线字体
- **大小**: ~19MB

### 3. 数学符号字体
- **文件名**: `NotoSansMath-Regular.ttf`
- **来源**: https://github.com/notofonts/noto-fonts
- **用途**: 渲染 PDF 中的数学公式、希腊字母、数学符号等
- **大小**: ~578KB

## 字体子集化（可选）

完整的 CJK 字体文件较大（约 10-20MB），如果需要减小应用体积，可以使用字体子集化工具：

### 使用 pyftsubset (fonttools)

```bash
# 安装 fonttools
pip install fonttools

# 子集化中文字体（保留常用字符）
pyftsubset NotoSerifCJKsc-Regular.otf \
    --text-file=chinese_chars.txt \
    --output-file=NotoSerifCJKsc-Regular-subset.otf

# 子集化后重命名
mv NotoSerifCJKsc-Regular-subset.otf NotoSerifCJKsc-Regular.otf
```

### 推荐的字符集

建议至少包含以下字符：
- GB2312 基本字符集（6763 个汉字）
- 常用标点符号
- 数字和字母

## 目录结构

```
fonts/
├── README.md                      (本文件)
├── NotoSerifCJK-Regular.ttc       (中文衬线字体 ~26MB)
├── NotoSansCJK-Regular.ttc        (中文无衬线字体 ~19MB)
└── NotoSansMath-Regular.ttf       (数学符号字体 ~578KB)
```

**注意**: TTC (TrueType Collection) 格式包含多种语言变体（简体中文、繁体中文、日文、韩文），
Android 会根据系统语言自动选择合适的字体变体。

## 初始化

字体文件添加后，需要在应用启动时初始化 FontAssetManager：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FontAssetManager.init(this)
        // 可选：预加载字体以避免首次使用时的延迟
        FontAssetManager.preloadAll()
    }
}
```

## 替代方案

如果不想打包字体文件，可以修改 `FontAssetManager.kt` 使用系统字体：

```kotlin
fun getSerifCJK(): Typeface {
    // 尝试加载 assets 字体，失败则使用系统字体
    return serifCJKTypeface ?: Typeface.create("serif", Typeface.NORMAL)
}
```

注意：使用系统字体可能导致不同设备上显示效果不一致。
