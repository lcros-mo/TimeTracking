package com.timetracking.app.core.data.db

import androidx.room.*
import com.timetracking.app.core.data.model.OvertimeBalance

@Dao
interface OvertimeBalanceDao {
    @Query("SELECT * FROM overtime_balance WHERE id = 1")
    suspend fun getBalance(): OvertimeBalance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBalance(balance: OvertimeBalance)

    @Query("UPDATE overtime_balance SET totalMinutes = totalMinutes + :minutesToAdd WHERE id = 1")
    suspend fun addToBalance(minutesToAdd: Long)
}