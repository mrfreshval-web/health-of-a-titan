package com.healthofatitan.app.core.account

import java.io.File

/**
 * Whose data lives on this phone (A65 §2.3: one HoT account per install). The account id (S1) is
 * written at the first sign-in and kept apart from the session: a sign-out (or a `401` on refresh)
 * leaves it, so signing in to *another* account asks to wipe first. Only the wipe removes it.
 */
interface DataOwnerStore {
    fun get(): String?

    fun set(accountId: String)

    fun clear()
}

/** A plain file in `noBackupFilesDir`: the id alone, never in backups. */
class FileDataOwnerStore(private val file: File) : DataOwnerStore {
    override fun get(): String? = runCatching { file.takeIf { it.isFile }?.readText()?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    @Synchronized
    override fun set(accountId: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(accountId)
        if (!tmp.renameTo(file)) file.writeText(accountId)
    }

    override fun clear() {
        file.delete()
    }

    companion object {
        const val FILE = "data_owner"
    }
}

class InMemoryDataOwnerStore(private var value: String? = null) : DataOwnerStore {
    override fun get(): String? = value

    override fun set(accountId: String) {
        value = accountId
    }

    override fun clear() {
        value = null
    }
}
