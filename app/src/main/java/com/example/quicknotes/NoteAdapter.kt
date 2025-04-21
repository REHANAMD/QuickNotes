package com.example.quicknotes

import android.app.AlertDialog
import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class NoteAdapter(private val notes: List<Note>, private val context: Context) :
    RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val noteText: TextView = itemView.findViewById(R.id.noteText)
        val noteTime: TextView = itemView.findViewById(R.id.noteTime)
        val checkNote: CheckBox = itemView.findViewById(R.id.checkNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        holder.noteText.text = note.content

        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        val timeFormatted = sdf.format(Date(note.timestamp ?: 0))
        holder.noteTime.text = timeFormatted

        // ✅ Checkbox state
        holder.checkNote.setOnCheckedChangeListener(null) // prevent previous listener firing
        holder.checkNote.isChecked = note.checked

        // ✅ Strike-through based on checkbox
        if (note.checked) {
            holder.noteText.paintFlags = holder.noteText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.noteText.paintFlags = holder.noteText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // ✅ Update checked state in Firebase
        holder.checkNote.setOnCheckedChangeListener { _, isChecked ->
            note.checked = isChecked
            FirebaseDatabase.getInstance()
                .getReference("notes")
                .child(note.id!!)
                .child("checked")
                .setValue(isChecked)

            // 🔁 Update UI immediately
            if (isChecked) {
                holder.noteText.paintFlags = holder.noteText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.noteText.paintFlags = holder.noteText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }

        // ✅ Long press to delete
        holder.itemView.setOnLongClickListener {
            AlertDialog.Builder(context).apply {
                setTitle("Delete Note")
                setMessage("Are you sure you want to delete this note?")
                setPositiveButton("Delete") { _, _ ->
                    note.id?.let {
                        FirebaseDatabase.getInstance().getReference("notes")
                            .child(it)
                            .removeValue()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Note deleted!", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Failed to delete note", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                setNegativeButton("Cancel", null)
                show()
            }
            true
        }
    }

    override fun getItemCount(): Int = notes.size
}
