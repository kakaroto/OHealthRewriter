package ca.kakaroto.ohealthrewriter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.IOException

class LogAdapter(private val context: Context) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    private val logLines = mutableListOf<String>()
    private val logFile = File(context.filesDir, LOG_FILE_NAME)
    private var linesToSkip = 0
    private var isLoading = false
    private var hasMoreLogs = true

    private val LINES_TO_LOAD = 50

    fun loadMoreLogs(notify: Boolean = true) {
        if (isLoading || !hasMoreLogs) return
        isLoading = true

        val newLines = mutableListOf<String>()
        if (logFile.exists()) {
            try {
                val allLines = logFile.readLines()
                val endIndex = allLines.size - linesToSkip
                val startIndex = (endIndex - LINES_TO_LOAD).coerceAtLeast(0) // Load 20 items per page

                if (startIndex < endIndex) {
                    // The UI shows newest first, so we read the file backwards
                    // and add to the end of our list.
                    for (i in (endIndex - 1) downTo startIndex) {
                        newLines.add(allLines[i])
                    }
                }

                if (startIndex == 0) {
                    hasMoreLogs = false
                }
            } catch (e: IOException) {
                // File could be in use or other read errors
                hasMoreLogs = false
            }
        } else {
            hasMoreLogs = false
        }

        if (newLines.isNotEmpty()) {
            val positionStart = logLines.size
            logLines.addAll(newLines)
            linesToSkip += newLines.size
            if (notify) {
                notifyItemRangeInserted(positionStart, newLines.size)
            }
        }
        isLoading = false
    }

    fun reloadLogs() {
        isLoading = false // Cancel any ongoing load
        val previousSize = logLines.size
        logLines.clear()
        if (previousSize > 0) {
            notifyItemRangeRemoved(0, previousSize)
        }
        linesToSkip = 0
        hasMoreLogs = true
        loadMoreLogs()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.log_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.logText.text = HtmlCompat.fromHtml(logLines[position], HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    override fun getItemCount(): Int {
        return logLines.size
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logText: TextView = view.findViewById(R.id.log_text)
    }
}
