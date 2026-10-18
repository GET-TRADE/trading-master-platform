package com.tradingmaster.core.store

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tradingmaster.core.model.ScanResult

@Entity(tableName = "signal_history")
data class SignalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val generatedAt: Long,
    val isTrade: Boolean,
    val payloadJson: String
)

@Entity(tableName = "scan_runs")
data class ScanRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val marketsScanned: Int,
    val signalsFound: Int
)

@Entity(tableName = "paper_trades")
data class PaperTradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val direction: String,
    val entryPrice: Double,
    val qty: Double,
    val notionalUsdt: Double,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val openedAt: Long,
    val closedAt: Long?,
    val status: String,
    val exitPrice: Double?,
    val realizedPnlUsdt: Double?,
    val feesUsdt: Double,
    val slippageCostUsdt: Double,
    val partialExits: Int,
    val closeReason: String?
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val symbol: String,
    val searchedAt: Long
)

@Dao
interface SignalHistoryDao {
    @Insert suspend fun insert(e: SignalHistoryEntity): Long
    @Query("SELECT * FROM signal_history ORDER BY generatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 200): List<SignalHistoryEntity>
    @Query("SELECT * FROM signal_history WHERE isTrade = 1 ORDER BY generatedAt DESC LIMIT :limit")
    suspend fun recentTrades(limit: Int = 100): List<SignalHistoryEntity>
    @Query("DELETE FROM signal_history WHERE generatedAt < :olderThan")
    suspend fun prune(olderThan: Long)
}

@Dao
interface ScanRunDao {
    @Insert suspend fun insert(e: ScanRunEntity): Long
    @Query("SELECT * FROM scan_runs ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<ScanRunEntity>
}

@Dao
interface PaperTradeDao {
    @Insert suspend fun insert(e: PaperTradeEntity): Long
    @Query("SELECT * FROM paper_trades WHERE status = 'OPEN'")
    suspend fun openTrades(): List<PaperTradeEntity>
    @Query("SELECT * FROM paper_trades ORDER BY openedAt DESC LIMIT :limit")
    suspend fun all(limit: Int = 200): List<PaperTradeEntity>
    @Query("UPDATE paper_trades SET status = :status, closedAt = :closedAt, exitPrice = :exitPrice, realizedPnlUsdt = :pnl, feesUsdt = :fees, partialExits = :partialExits, closeReason = :reason, qty = :qty WHERE id = :id")
    suspend fun updateResult(id: Long, status: String, closedAt: Long?, exitPrice: Double?,
                             pnl: Double?, fees: Double, partialExits: Int, reason: String?, qty: Double)
    @Query("DELETE FROM paper_trades")
    suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(e: SearchHistoryEntity)
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<SearchHistoryEntity>
    @Query("DELETE FROM search_history WHERE searchedAt < :olderThan")
    suspend fun prune(olderThan: Long)
}

@Database(
    entities = [SignalHistoryEntity::class, ScanRunEntity::class, PaperTradeEntity::class, SearchHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TmDatabase : RoomDatabase() {
    abstract fun signalHistoryDao(): SignalHistoryDao
    abstract fun scanRunDao(): ScanRunDao
    abstract fun paperTradeDao(): PaperTradeDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile private var instance: TmDatabase? = null

        fun get(context: Context): TmDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TmDatabase::class.java, "tm_master.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
