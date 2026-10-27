package com.julesybean.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class PromptAdapter(
    private var prompts: List<Prompt>,
    private val onPromptUsed: (Prompt) -> Unit,
    private val onOverflowClicked: (Prompt) -> Unit
) : RecyclerView.Adapter<PromptAdapter.PromptViewHolder>() {

    class PromptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textTitle: TextView = itemView.findViewById(R.id.textPromptTitle)
        val textSnippet: TextView = itemView.findViewById(R.id.textPromptSnippet)
        val textMetadata: TextView = itemView.findViewById(R.id.textPromptMetadata)
        val buttonOverflow: ImageButton = itemView.findViewById(R.id.buttonOverflow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_prompt, parent, false)
        return PromptViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromptViewHolder, position: Int) {
        val prompt = prompts[position]

        holder.textTitle.text = prompt.title
        holder.textSnippet.text = PromptUtils.getSnippet(prompt.content)

        val displayTime = prompt.lastUsedAt ?: prompt.updatedAt
        val timeLabel = if (prompt.lastUsedAt != null) "Last used: " else "Updated: "
        holder.textMetadata.text = timeLabel + PromptUtils.formatTimestamp(displayTime)

        // Single tap: Copy content to clipboard + haptic feedback + Toast + DB timestamp update
        holder.itemView.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

            val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(prompt.title, prompt.content)
            clipboard.setPrimaryClip(clip)

            val toastMsg = view.context.getString(R.string.copied_to_clipboard, prompt.title)
            Toast.makeText(view.context, toastMsg, Toast.LENGTH_SHORT).show()

            onPromptUsed(prompt)
        }

        // Long press: open context menu
        holder.itemView.setOnLongClickListener {
            onOverflowClicked(prompt)
            true
        }

        // 3-dot overflow click: open context menu
        holder.buttonOverflow.setOnClickListener {
            onOverflowClicked(prompt)
        }
    }

    override fun getItemCount(): Int = prompts.size

    fun updatePrompts(newPrompts: List<Prompt>) {
        this.prompts = newPrompts
        notifyDataSetChanged()
    }
}
