package com.pdftools

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.pdftools.databinding.ActivityMainBinding
import java.io.File

/**
 * 主界面 - 文件浏览和最近文件
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var recentFilesAdapter: FileAdapter
    
    private val recentFiles = mutableListOf<FileItem>()
    
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { openPdf(it) }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            loadRecentFiles()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupButtons()
        checkPermissions()
    }
    
    override fun onResume() {
        super.onResume()
        loadRecentFiles()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }
    
    private fun setupRecyclerView() {
        recentFilesAdapter = FileAdapter { file ->
            openPdf(file.path)
        }
        
        binding.recyclerRecentFiles.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = recentFilesAdapter
        }
    }
    
    private fun setupButtons() {
        binding.fabOpen.setOnClickListener {
            pickFileLauncher.launch("application/pdf")
        }
        
        binding.btnBrowse.setOnClickListener {
            pickFileLauncher.launch("application/pdf")
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder(this)
                    .setTitle("需要存储权限")
                    .setMessage("为了浏览和保存 PDF 文件，需要授予存储权限")
                    .setPositiveButton("授权") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                loadRecentFiles()
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            
            val needPermission = permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            
            if (needPermission) {
                permissionLauncher.launch(permissions)
            } else {
                loadRecentFiles()
            }
        }
    }
    
    private fun loadRecentFiles() {
        recentFiles.clear()
        
        // 从 SharedPreferences 加载最近文件
        val prefs = getSharedPreferences("recent_files", MODE_PRIVATE)
        val recentPaths = prefs.getStringSet("paths", emptySet()) ?: emptySet()
        
        for (path in recentPaths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                recentFiles.add(FileItem(
                    name = file.name,
                    path = path,
                    size = formatFileSize(file.length()),
                    lastModified = file.lastModified()
                ))
            }
        }
        
        // 按最近修改时间排序
        recentFiles.sortByDescending { it.lastModified }
        
        recentFilesAdapter.submitList(recentFiles.toList())
        
        // 更新 UI 状态
        binding.textEmptyHint.visibility = if (recentFiles.isEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
    
    private fun openPdf(uri: Uri) {
        try {
            // 复制到缓存目录
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val fileName = getFileName(uri) ?: "document.pdf"
            val cacheFile = File(cacheDir, fileName)
            
            cacheFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            
            openPdf(cacheFile.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openPdf(path: String) {
        // 保存到最近文件
        saveRecentFile(path)
        
        // 打开 PDF 查看器
        val intent = Intent(this, PdfViewerActivity::class.java)
        intent.putExtra("pdf_path", path)
        startActivity(intent)
    }
    
    private fun saveRecentFile(path: String) {
        val prefs = getSharedPreferences("recent_files", MODE_PRIVATE)
        val recentPaths = prefs.getStringSet("paths", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        recentPaths.add(path)
        
        // 限制最多保存 20 个
        if (recentPaths.size > 20) {
            val sorted = recentPaths.sortedByDescending { File(it).lastModified() }
            recentPaths.clear()
            recentPaths.addAll(sorted.take(20))
        }
        
        prefs.edit().putStringSet("paths", recentPaths).apply()
    }
    
    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
}

/**
 * 文件项
 */
data class FileItem(
    val name: String,
    val path: String,
    val size: String,
    val lastModified: Long
)
