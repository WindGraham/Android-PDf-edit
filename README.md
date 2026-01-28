# Android-PDF-Edit

基于 PDF 32000-1:2008 标准的 Android PDF 解析、渲染和编辑库。

## 功能特性

- **PDF 解析**: 完整解析 PDF 1.7 文件结构
- **渲染引擎**: 在 Android Canvas 上渲染 PDF 页面
- **文本编辑**: 查找、替换、删除、添加文本
- **图形编辑**: 添加矩形、线条、椭圆等形状
- **图像编辑**: 提取、替换、添加、删除图像
- **PDF 写入**: 完整写入和增量更新

## 模块结构

- `pdfcore` - 纯 Kotlin PDF 核心库（不依赖 Android）
- `pdfrender` - Android 渲染模块
- `pdfeditor` - PDF 编辑模块
- `app` - Android 应用示例

## 构建要求

- Android Studio
- JDK 17
- Android SDK 34
- Min SDK 26 (Android 8.0)

## 构建命令

```bash
./gradlew assembleDebug
./gradlew installDebug
```

