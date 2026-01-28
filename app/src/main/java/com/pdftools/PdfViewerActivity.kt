package com.pdftools

import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pdfcore.content.TextExtractor
import com.pdfcore.model.PdfDocument
import com.pdfcore.parser.PdfParser
import com.pdfcore.parser.PdfWriter
import com.pdfeditor.text.TextEditor
import com.pdfrender.engine.PdfRenderEngine
import com.pdfrender.view.PdfPageView
import com.pdftools.databinding.ActivityPdfViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 查看和编辑界面
 */
class PdfViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPdfViewerBinding
    
    private var document: PdfDocument? = null
    private var pdfPath: String? = null
    private var renderEngine: PdfRenderEngine? = null
    private var textEditor: TextEditor? = null
    
    private var currentPageIndex = 0
    private var isModified = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupPdfView()
        setupControls()
        
        pdfPath = intent.getStringExtra("pdf_path")
        pdfPath?.let { loadPdf(it) }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
    }
    
    private fun setupPdfView() {
        binding.pdfPageView.onPageChangeListener = { page, total ->
            currentPageIndex = page
            updatePageIndicator()
        }
        
        binding.pdfPageView.onTapListener = { x, y ->
            toggleControls()
        }
    }
    
    private fun setupControls() {
        binding.btnPrevPage.setOnClickListener {
            binding.pdfPageView.previousPage()
        }
        
        binding.btnNextPage.setOnClickListener {
            binding.pdfPageView.nextPage()
        }
        
        binding.fabEdit.setOnClickListener {
            showEditOptions()
        }
    }
    
    private fun toggleControls() {
        val newVisibility = if (binding.bottomControls.visibility == View.VISIBLE) {
            View.GONE
        } else {
            View.VISIBLE
        }
        binding.bottomControls.visibility = newVisibility
        binding.fabEdit.visibility = newVisibility
    }
    
    private fun loadPdf(path: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    PdfParser().parse(path)
                }
                
                result.onSuccess { doc ->
                    document = doc
                    renderEngine = PdfRenderEngine(doc)
                    textEditor = TextEditor(doc)
                    
                    binding.pdfPageView.setDocument(doc)
                    
                    supportActionBar?.title = File(path).name
                    updatePageIndicator()
                }
                
                result.onFailure { e ->
                    Toast.makeText(this@PdfViewerActivity, 
                        "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PdfViewerActivity, 
                    "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun updatePageIndicator() {
        val total = document?.getPageCount() ?: 0
        binding.textPageIndicator.text = getString(R.string.page_indicator, currentPageIndex + 1, total)
    }
    
    private fun showEditOptions() {
        val options = arrayOf(
            "查找并替换文本",
            "提取页面文本",
            "添加文本",
            "删除文本"
        )
        
        AlertDialog.Builder(this)
            .setTitle("编辑选项")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFindReplaceDialog()
                    1 -> extractPageText()
                    2 -> showAddTextDialog()
                    3 -> showDeleteTextDialog()
                }
            }
            .show()
    }
    
    private fun showFindReplaceDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_find_replace, null)
        val editFind = view.findViewById<EditText>(R.id.editFind)
        val editReplace = view.findViewById<EditText>(R.id.editReplace)
        
        AlertDialog.Builder(this)
            .setTitle("查找并替换")
            .setView(view)
            .setPositiveButton("替换全部") { _, _ ->
                val findText = editFind.text.toString()
                val replaceText = editReplace.text.toString()
                
                if (findText.isNotEmpty()) {
                    replaceText(findText, replaceText)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun replaceText(findText: String, replaceText: String) {
        val doc = document ?: return
        val editor = textEditor ?: return
        val page = doc.getPage(currentPageIndex) ?: return
        
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            
            try {
                val count = withContext(Dispatchers.IO) {
                    editor.replaceText(page, findText, replaceText, ignoreCase = true)
                }
                
                if (count > 0) {
                    isModified = true
                    // 重新加载页面
                    binding.pdfPageView.setDocument(doc)
                    binding.pdfPageView.goToPage(currentPageIndex)
                    
                    Toast.makeText(this@PdfViewerActivity, 
                        "替换了 $count 处", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PdfViewerActivity, 
                        "未找到匹配文本", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PdfViewerActivity, 
                    "替换失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun extractPageText() {
        val doc = document ?: return
        val page = doc.getPage(currentPageIndex) ?: return
        
        lifecycleScope.launch {
            try {
                val elements = withContext(Dispatchers.IO) {
                    TextExtractor(doc).extractFromPage(page)
                }
                
                val text = elements.joinToString("\n") { it.text }
                
                if (text.isNotEmpty()) {
                    AlertDialog.Builder(this@PdfViewerActivity)
                        .setTitle("页面文本")
                        .setMessage(text)
                        .setPositiveButton("复制") { _, _ ->
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("PDF Text", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@PdfViewerActivity, "已复制", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("关闭", null)
                        .show()
                } else {
                    Toast.makeText(this@PdfViewerActivity, "页面无文本内容", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PdfViewerActivity, 
                    "提取失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showAddTextDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_text, null)
        val editText = view.findViewById<EditText>(R.id.editText)
        val editX = view.findViewById<EditText>(R.id.editX)
        val editY = view.findViewById<EditText>(R.id.editY)
        val editSize = view.findViewById<EditText>(R.id.editFontSize)
        
        editX.setText("100")
        editY.setText("700")
        editSize.setText("12")
        
        AlertDialog.Builder(this)
            .setTitle("添加文本")
            .setView(view)
            .setPositiveButton("添加") { _, _ ->
                val text = editText.text.toString()
                val x = editX.text.toString().toFloatOrNull() ?: 100f
                val y = editY.text.toString().toFloatOrNull() ?: 700f
                val size = editSize.text.toString().toFloatOrNull() ?: 12f
                
                if (text.isNotEmpty()) {
                    addText(text, x, y, size)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun addText(text: String, x: Float, y: Float, fontSize: Float) {
        val doc = document ?: return
        val editor = textEditor ?: return
        val page = doc.getPage(currentPageIndex) ?: return
        
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            
            try {
                val success = withContext(Dispatchers.IO) {
                    editor.addText(page, text, x, y, "F1", fontSize)
                }
                
                if (success) {
                    isModified = true
                    binding.pdfPageView.setDocument(doc)
                    binding.pdfPageView.goToPage(currentPageIndex)
                    
                    Toast.makeText(this@PdfViewerActivity, "已添加文本", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PdfViewerActivity, 
                    "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun showDeleteTextDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_delete_text, null)
        val editText = view.findViewById<EditText>(R.id.editSearchText)
        
        AlertDialog.Builder(this)
            .setTitle("删除包含指定文本的文本块")
            .setView(view)
            .setPositiveButton("删除") { _, _ ->
                val text = editText.text.toString()
                if (text.isNotEmpty()) {
                    deleteText(text)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun deleteText(searchText: String) {
        val doc = document ?: return
        val editor = textEditor ?: return
        val page = doc.getPage(currentPageIndex) ?: return
        
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            
            try {
                val count = withContext(Dispatchers.IO) {
                    editor.deleteText(page, searchText, ignoreCase = true)
                }
                
                if (count > 0) {
                    isModified = true
                    binding.pdfPageView.setDocument(doc)
                    binding.pdfPageView.goToPage(currentPageIndex)
                    
                    Toast.makeText(this@PdfViewerActivity, 
                        "删除了 $count 处", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PdfViewerActivity, 
                        "未找到匹配文本", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PdfViewerActivity, 
                    "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun savePdf() {
        val doc = document ?: return
        val path = pdfPath ?: return
        
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            
            try {
                val success = withContext(Dispatchers.IO) {
                    val outputPath = path.replace(".pdf", "_edited.pdf")
                    PdfWriter().write(doc, outputPath)
                }
                
                if (success) {
                    isModified = false
                    Toast.makeText(this@PdfViewerActivity, 
                        "保存成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PdfViewerActivity, 
                        "保存失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PdfViewerActivity, 
                    "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_viewer, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_save -> {
                savePdf()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onBackPressed() {
        if (isModified) {
            AlertDialog.Builder(this)
                .setTitle("未保存的修改")
                .setMessage("是否保存修改？")
                .setPositiveButton("保存") { _, _ ->
                    savePdf()
                    super.onBackPressed()
                }
                .setNegativeButton("不保存") { _, _ ->
                    super.onBackPressed()
                }
                .setNeutralButton("取消", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
}
