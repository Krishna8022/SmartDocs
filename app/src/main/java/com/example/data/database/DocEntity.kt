package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DocItem
import android.net.Uri

@Entity(tableName = "documents")
data class DocEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uriString: String,
    val path: String,
    val mimeType: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val lastModified: Long,
    val isFavorite: Boolean = false,
    val lastReadPage: Int = 1,
    val lastOpenedTime: Long = System.currentTimeMillis()
) {
    fun toDocItem(): DocItem {
        return DocItem(
            id = id,
            name = name,
            uri = Uri.parse(uriString),
            path = path,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            pageCount = pageCount,
            lastModified = lastModified,
            isFavorite = isFavorite,
            lastReadPage = lastReadPage
        )
    }

    companion object {
        fun fromDocItem(item: DocItem): DocEntity {
            return DocEntity(
                id = item.id,
                name = item.name,
                uriString = item.uri.toString(),
                path = item.path,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                pageCount = item.pageCount,
                lastModified = item.lastModified,
                isFavorite = item.isFavorite,
                lastReadPage = item.lastReadPage,
                lastOpenedTime = System.currentTimeMillis()
            )
        }
    }
}
