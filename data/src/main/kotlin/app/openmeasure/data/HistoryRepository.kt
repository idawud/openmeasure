package app.openmeasure.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** History storage facade over Room. */
class HistoryRepository(private val dao: MeasurementDao) {
    constructor(context: Context) : this(AppDatabase.get(context).measurementDao())

    fun observeAll(): Flow<List<HistoryItem>> = dao.observeAll().map { list -> list.map { it.toItem() } }

    suspend fun save(item: HistoryItem): Long = dao.insert(item.toEntity().copy(id = 0))

    suspend fun get(id: Long): HistoryItem? = dao.get(id)?.toItem()

    suspend fun updateNote(id: Long, note: String?) = dao.updateNote(id, note?.takeIf { it.isNotBlank() })

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()

    /** Undo for delete: re-inserts [item] keeping its original id and timestamp. */
    suspend fun restore(item: HistoryItem) { dao.insert(item.toEntity()) }
}
