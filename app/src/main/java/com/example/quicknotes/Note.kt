package com.example.quicknotes

data class Note(
    val id: String? = null,
    val content: String? = null,
    val timestamp: Long? = null,
    var checked: Boolean = false,
    var reminderTime: Long? = null
)
