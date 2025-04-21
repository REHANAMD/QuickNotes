package com.example.quicknotes

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class NoteActivity : AppCompatActivity() {

    private lateinit var notesRef: DatabaseReference
    private lateinit var noteList: ArrayList<Note>
    private lateinit var adapter: NoteAdapter
    private var reminderTimestamp: Long? = null
    private var reminderSwitchListener: CompoundButton.OnCheckedChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        // 🔐 Block unauthenticated users
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 🌙 Apply dark mode before layout
        val sharedPref = getSharedPreferences("settings", MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note)

        // 🔗 UI references
        val switchDarkMode = findViewById<Switch>(R.id.switchDarkMode)
        val switchReminder = findViewById<Switch>(R.id.switchReminder)
        val editNote = findViewById<EditText>(R.id.editNote)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val recyclerView = findViewById<RecyclerView>(R.id.notesRecycler)

        // 🌙 Dark mode logic
        switchDarkMode.isChecked = isDarkMode
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }

        // ⏰ Reminder logic
        reminderSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val calendar = Calendar.getInstance()
                DatePickerDialog(this, { _, year, month, day ->
                    TimePickerDialog(this, { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute)
                        reminderTimestamp = calendar.timeInMillis
                        Toast.makeText(this, "Reminder set!", Toast.LENGTH_SHORT).show()
                    }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            } else {
                reminderTimestamp = null
                Toast.makeText(this, "Reminder cleared", Toast.LENGTH_SHORT).show()
            }
        }

        switchReminder.setOnCheckedChangeListener(reminderSwitchListener)

        // 🔁 RecyclerView + Firebase
        notesRef = FirebaseDatabase.getInstance().getReference("notes")
        noteList = ArrayList()
        adapter = NoteAdapter(noteList, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 💾 Save note
        btnSave.setOnClickListener {
            val noteText = editNote.text.toString()
            if (noteText.isNotEmpty()) {
                val noteId = notesRef.push().key
                val currentTime = System.currentTimeMillis()
                val note = Note(noteId, noteText, currentTime, false, reminderTimestamp)

                notesRef.child(noteId!!).setValue(note)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Note saved!", Toast.LENGTH_SHORT).show()
                        editNote.text.clear()

                        // ❌ Avoid accidental reminder cleared toast
                        switchReminder.setOnCheckedChangeListener(null)
                        switchReminder.isChecked = false
                        reminderTimestamp = null
                        switchReminder.setOnCheckedChangeListener(reminderSwitchListener)

                        // 🔔 If reminder set, trigger alarm
                        if (note.reminderTimestamp != null) {
                            val intent = Intent(this, ReminderReceiver::class.java).apply {
                                putExtra("noteText", noteText)
                            }

                            val pendingIntent = PendingIntent.getBroadcast(
                                this,
                                noteId.hashCode(),
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )

                            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            alarmManager.setExact(
                                AlarmManager.RTC_WAKEUP,
                                note.reminderTimestamp!!,
                                pendingIntent
                            )
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error saving note", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Note is empty!", Toast.LENGTH_SHORT).show()
            }
        }

        // 🔁 Sync with Firebase
        notesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                noteList.clear()
                for (noteSnap in snapshot.children) {
                    val note = noteSnap.getValue(Note::class.java)
                    note?.let { noteList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FIREBASE_READ", "Error: ${error.message}")
            }
        })

        // 🔓 Logout logic
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
