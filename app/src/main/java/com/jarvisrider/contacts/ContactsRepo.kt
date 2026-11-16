package com.jarvisrider.contacts

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.jarvisrider.core.Perms

/** One dialable entry per contact (best number chosen). */
data class ContactEntry(val contactId: Long, val name: String, val number: String)

/** Loads contacts on demand only; nothing is cached between interactions. */
object ContactsRepo {

    fun loadDialable(context: Context): List<ContactEntry> {
        if (!Perms.has(context, Manifest.permission.READ_CONTACTS)) return emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        // contactId -> (rank, entry); lower rank wins
        val best = HashMap<Long, Pair<Int, ContactEntry>>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(projection[0])
                val iName = c.getColumnIndexOrThrow(projection[1])
                val iNum = c.getColumnIndexOrThrow(projection[2])
                val iSuper = c.getColumnIndexOrThrow(projection[3])
                val iPrim = c.getColumnIndexOrThrow(projection[4])
                val iType = c.getColumnIndexOrThrow(projection[5])
                while (c.moveToNext()) {
                    val name = c.getString(iName)?.trim().orEmpty()
                    val number = c.getString(iNum)?.trim().orEmpty()
                    if (name.isEmpty() || number.isEmpty()) continue
                    val id = c.getLong(iId)
                    val rank = when {
                        c.getInt(iSuper) != 0 -> 0
                        c.getInt(iPrim) != 0 -> 1
                        c.getInt(iType) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> 2
                        else -> 3
                    }
                    val existing = best[id]
                    if (existing == null || rank < existing.first) best[id] = rank to ContactEntry(id, name, number)
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: RuntimeException) {
            return emptyList()
        }
        return best.values.map { it.second }
    }

    /** Caller-ID lookup for incoming calls / redial announcements. */
    fun nameForNumber(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        if (!Perms.has(context, Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: RuntimeException) {
            null
        }
    }
}
