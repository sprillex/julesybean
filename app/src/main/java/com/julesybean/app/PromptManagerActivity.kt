package com.julesybean.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.io.BufferedReader
import java.io.InputStreamReader

class PromptManagerActivity : AppCompatActivity() {

    private lateinit var dbHelper: PromptDatabaseHelper
    private lateinit var adapter: PromptAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var fabMain: FloatingActionButton
    private lateinit var layoutFabSpeedDial: LinearLayout
    private lateinit var fabNewPrompt: FloatingActionButton
    private lateinit var fabImport: FloatingActionButton

    private var currentQuery: String? = null
    private var currentSortType: PromptSortType = PromptSortType.DATE_CREATED
    private var isFabExpanded = false

    // Pending export target
    private var pendingExportPrompt: Prompt? = null

    // File Exporter launcher
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val uri: Uri = result.data!!.data!!
            val prompt = pendingExportPrompt
            if (prompt != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(prompt.content.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingExportPrompt = null
    }

    // File Importer launcher (multiple .txt files)
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            var importedCount = 0
            for (uri in uris) {
                try {
                    val content = readUriText(uri)
                    if (content.isNotBlank()) {
                        val derivedTitle = getFilenameFromUri(uri)
                        val titleWithoutExtension = derivedTitle.substringBeforeLast(".txt", derivedTitle)
                        val finalTitle = if (titleWithoutExtension.isBlank()) {
                            PromptUtils.getDefaultTitle()
                        } else {
                            titleWithoutExtension
                        }

                        val now = System.currentTimeMillis()
                        val newPrompt = Prompt(
                            title = finalTitle,
                            content = content,
                            createdAt = now,
                            updatedAt = now
                        )
                        dbHelper.insertPrompt(newPrompt)
                        importedCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (importedCount > 0) {
                loadPrompts()
                Toast.makeText(
                    this,
                    getString(R.string.imported_prompts_count, importedCount),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prompt_manager)

        dbHelper = PromptDatabaseHelper(this)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.prompt_manager)

        recyclerView = findViewById(R.id.recyclerViewPrompts)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        fabMain = findViewById(R.id.fabMain)
        layoutFabSpeedDial = findViewById(R.id.layoutFabSpeedDial)
        fabNewPrompt = findViewById(R.id.fabNewPrompt)
        fabImport = findViewById(R.id.fabImport)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PromptAdapter(
            prompts = emptyList(),
            onPromptUsed = { prompt ->
                dbHelper.updateLastUsed(prompt.id)
                loadPrompts()
            },
            onOverflowClicked = { prompt ->
                showBottomSheetMenu(prompt)
            }
        )
        recyclerView.adapter = adapter

        setupFabSpeedDial()
        loadPrompts()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_prompt_manager, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView
        searchView?.queryHint = getString(R.string.search_prompts)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentQuery = query
                loadPrompts()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText
                loadPrompts()
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sort_alphabetical -> {
                currentSortType = PromptSortType.ALPHABETICAL
                loadPrompts()
                true
            }
            R.id.sort_recently_used -> {
                currentSortType = PromptSortType.RECENTLY_USED
                loadPrompts()
                true
            }
            R.id.sort_date_created -> {
                currentSortType = PromptSortType.DATE_CREATED
                loadPrompts()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadPrompts() {
        val prompts = dbHelper.getAllPrompts(query = currentQuery, sortType = currentSortType)
        adapter.updatePrompts(prompts)

        if (prompts.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun setupFabSpeedDial() {
        fabMain.setOnClickListener {
            toggleFabSpeedDial()
        }

        fabNewPrompt.setOnClickListener {
            collapseFabSpeedDial()
            showPromptEditorDialog(prompt = null)
        }

        fabImport.setOnClickListener {
            collapseFabSpeedDial()
            importLauncher.launch(arrayOf("text/plain", "*/*"))
        }
    }

    private fun toggleFabSpeedDial() {
        if (isFabExpanded) {
            collapseFabSpeedDial()
        } else {
            expandFabSpeedDial()
        }
    }

    private fun expandFabSpeedDial() {
        isFabExpanded = true
        layoutFabSpeedDial.visibility = View.VISIBLE
        fabMain.animate().rotation(45f).setDuration(200).start()
    }

    private fun collapseFabSpeedDial() {
        isFabExpanded = false
        layoutFabSpeedDial.visibility = View.GONE
        fabMain.animate().rotation(0f).setDuration(200).start()
    }

    private fun showBottomSheetMenu(prompt: Prompt) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_prompt_actions, null)

        val textTitle: TextView = view.findViewById(R.id.textBottomSheetPromptTitle)
        textTitle.text = prompt.title

        view.findViewById<View>(R.id.actionEdit).setOnClickListener {
            dialog.dismiss()
            showPromptEditorDialog(prompt)
        }

        view.findViewById<View>(R.id.actionRename).setOnClickListener {
            dialog.dismiss()
            showRenameDialog(prompt)
        }

        view.findViewById<View>(R.id.actionShare).setOnClickListener {
            dialog.dismiss()
            sharePrompt(prompt)
        }

        view.findViewById<View>(R.id.actionExport).setOnClickListener {
            dialog.dismiss()
            exportPrompt(prompt)
        }

        view.findViewById<View>(R.id.actionDelete).setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmDialog(prompt)
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showPromptEditorDialog(prompt: Prompt?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_prompt_editor, null)
        val textDialogTitle: TextView = dialogView.findViewById(R.id.textDialogTitle)
        val editTitle: TextInputEditText = dialogView.findViewById(R.id.editTitle)
        val editContent: TextInputEditText = dialogView.findViewById(R.id.editContent)

        val isEdit = prompt != null
        if (isEdit) {
            textDialogTitle.setText(R.string.edit_prompt)
            editTitle.setText(prompt!!.title)
            editContent.setText(prompt.content)
        } else {
            textDialogTitle.setText(R.string.new_prompt)
        }

        val initialTitle = editTitle.text?.toString() ?: ""
        val initialContent = editContent.text?.toString() ?: ""

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.save, null) // Override in onShow for validation
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveBtn.setOnClickListener {
                val enteredTitle = editTitle.text?.toString()?.trim() ?: ""
                val enteredContent = editContent.text?.toString() ?: ""

                if (enteredContent.isBlank()) {
                    Toast.makeText(this, getString(R.string.error_content_required), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val finalTitle = if (enteredTitle.isBlank()) {
                    PromptUtils.getDefaultTitle()
                } else {
                    enteredTitle
                }

                val now = System.currentTimeMillis()
                if (isEdit) {
                    val updated = prompt!!.copy(
                        title = finalTitle,
                        content = enteredContent,
                        updatedAt = now
                    )
                    dbHelper.updatePrompt(updated)
                } else {
                    val created = Prompt(
                        title = finalTitle,
                        content = enteredContent,
                        createdAt = now,
                        updatedAt = now
                    )
                    dbHelper.insertPrompt(created)
                }

                loadPrompts()
                dialog.dismiss()
            }

            val cancelBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            cancelBtn.setOnClickListener {
                val currentTitle = editTitle.text?.toString() ?: ""
                val currentContent = editContent.text?.toString() ?: ""

                if (currentTitle != initialTitle || currentContent != initialContent) {
                    showUnsavedChangesDialog {
                        dialog.dismiss()
                    }
                } else {
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showUnsavedChangesDialog(onConfirmDiscard: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.discard_changes_title)
            .setMessage(R.string.discard_changes_body)
            .setPositiveButton(R.string.discard) { _, _ ->
                onConfirmDiscard()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(prompt: Prompt) {
        val editText = EditText(this).apply {
            setText(prompt.title)
            setSelection(prompt.title.length)
            setSingleLine(true)
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.rename_prompt)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotBlank()) {
                    val updated = prompt.copy(
                        title = newTitle,
                        updatedAt = System.currentTimeMillis()
                    )
                    dbHelper.updatePrompt(updated)
                    loadPrompts()
                } else {
                    Toast.makeText(this, getString(R.string.error_title_required), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmDialog(prompt: Prompt) {
        val body = getString(R.string.delete_prompt_confirm_body, prompt.title)
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_prompt_confirm_title)
            .setMessage(body)
            .setPositiveButton(R.string.delete) { _, _ ->
                dbHelper.deletePrompt(prompt.id)
                loadPrompts()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sharePrompt(prompt: Prompt) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, prompt.title)
            putExtra(Intent.EXTRA_TEXT, prompt.content)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_prompt)))
    }

    private fun exportPrompt(prompt: Prompt) {
        pendingExportPrompt = prompt
        val filename = PromptUtils.sanitizeFilename(prompt.title) + ".txt"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        exportLauncher.launch(intent)
    }

    private fun readUriText(uri: Uri): String {
        val stringBuilder = StringBuilder()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line).append("\n")
                    line = reader.readLine()
                }
            }
        }
        return stringBuilder.toString().trimEnd()
    }

    private fun getFilenameFromUri(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        result = it.getString(nameIdx)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "imported_prompt.txt"
    }
}
