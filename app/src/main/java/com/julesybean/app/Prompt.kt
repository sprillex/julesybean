package com.julesybean.app

data class Prompt(
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val sortOrder: Int = 0
)
