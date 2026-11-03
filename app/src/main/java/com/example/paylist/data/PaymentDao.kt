package com.example.paylist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DuePayment(
    val id: Long, val title: String, val currency: String, val dueDate: String,
    val amountMinor: Long, val paidMinor: Long
)

data class OccurrenceWithTemplate(
    val id: Long,
    val templateId: Long,
    val periodKey: String,
    val plannedDate: String,
    val dueDate: String,
    val amountMinor: Long,
    val paidMinor: Long,
    val status: String,
    val title: String,
    val category: String,
    val currency: String,
    val logoUri: String?
)

@Dao
interface PaymentDao {
    @Insert suspend fun insertTemplate(template: PaymentTemplate): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOccurrence(occurrence: PaymentOccurrence): Long
    @Insert suspend fun insertOperation(operation: PaymentOperation): Long
    @Update suspend fun updateOperation(operation: PaymentOperation)

    @Query("""
        SELECT o.id, o.templateId, o.periodKey, o.plannedDate, o.dueDate,
               o.amountMinor, o.paidMinor,
               CASE WHEN o.status IN ('PLANNED', 'PARTIAL') AND o.dueDate < date('now', 'localtime') THEN 'OVERDUE' ELSE o.status END AS status,
               t.title, t.category, t.currency, t.logoUri
        FROM payment_occurrences o INNER JOIN payment_templates t ON t.id = o.templateId
        WHERE o.periodKey = :periodKey AND o.status != 'CANCELLED'
        ORDER BY o.plannedDate, t.title
    """) fun observeMonth(periodKey: String): Flow<List<OccurrenceWithTemplate>>

    @Query("""
        SELECT o.id, o.templateId, o.periodKey, o.plannedDate, o.dueDate,
               o.amountMinor, o.paidMinor, o.status, t.title, t.category,
               t.currency, t.logoUri
        FROM payment_occurrences o INNER JOIN payment_templates t ON t.id = o.templateId
        WHERE o.status = 'PAID' ORDER BY o.plannedDate DESC
    """) fun observeHistory(): Flow<List<OccurrenceWithTemplate>>

    @Query("SELECT * FROM payment_templates WHERE id = :id LIMIT 1") suspend fun getTemplate(id: Long): PaymentTemplate?
    @Query("SELECT * FROM payment_occurrences WHERE id = :id LIMIT 1") suspend fun getOccurrence(id: Long): PaymentOccurrence?
    @Query("SELECT COALESCE(SUM(paidMinor), 0) FROM payment_occurrences WHERE templateId = :templateId") suspend fun totalPaid(templateId: Long): Long
    @Query("UPDATE payment_occurrences SET paidMinor = :paid, status = :status WHERE id = :id") suspend fun updatePaid(id: Long, paid: Long, status: String)
    @Query("UPDATE payment_templates SET active = 0 WHERE id = :id") suspend fun deactivateTemplate(id: Long)
    @Query("UPDATE payment_templates SET active = 1 WHERE id = :id") suspend fun activateTemplate(id: Long)
    @Query("SELECT * FROM payment_operations WHERE occurrenceId = :occurrenceId ORDER BY operationDate DESC, id DESC") fun observeOperations(occurrenceId: Long): Flow<List<PaymentOperation>>
    @Query("SELECT * FROM payment_operations WHERE occurrenceId = :occurrenceId ORDER BY operationDate DESC, id DESC") suspend fun getOperations(occurrenceId: Long): List<PaymentOperation>
    @Query("DELETE FROM payment_operations WHERE id = :operationId") suspend fun deleteOperation(operationId: Long)
    @Query("DELETE FROM payment_occurrences WHERE templateId = :templateId AND periodKey > :periodKey AND status != 'PAID' AND NOT EXISTS (SELECT 1 FROM payment_operations WHERE payment_operations.occurrenceId = payment_occurrences.id)") suspend fun deleteFutureUnpaid(templateId: Long, periodKey: String)
    @Query("SELECT COUNT(*) FROM payment_occurrences o WHERE o.templateId = :templateId AND o.periodKey > :periodKey AND EXISTS (SELECT 1 FROM payment_operations p WHERE p.occurrenceId = o.id)") suspend fun countLaterOccurrencesWithOperations(templateId: Long, periodKey: String): Int
    @Query("SELECT * FROM payment_templates ORDER BY id") suspend fun getAllTemplates(): List<PaymentTemplate>
    @Query("SELECT * FROM payment_occurrences ORDER BY id") suspend fun getAllOccurrences(): List<PaymentOccurrence>
    @Query("SELECT * FROM payment_operations ORDER BY id") suspend fun getAllOperations(): List<PaymentOperation>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun importTemplates(items: List<PaymentTemplate>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun importOccurrences(items: List<PaymentOccurrence>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun importOperations(items: List<PaymentOperation>)
    @Query("UPDATE payment_templates SET logoUri = :uri WHERE id = :templateId") suspend fun updateLogo(templateId: Long, uri: String?)
    @Query("""SELECT o.id, t.title, t.currency, o.dueDate, o.amountMinor, o.paidMinor
        FROM payment_occurrences o INNER JOIN payment_templates t ON t.id = o.templateId
        WHERE o.status IN ('PLANNED', 'PARTIAL') AND o.dueDate BETWEEN :fromDate AND :toDate
        ORDER BY o.dueDate""") suspend fun getDueBetween(fromDate: String, toDate: String): List<DuePayment>
}
