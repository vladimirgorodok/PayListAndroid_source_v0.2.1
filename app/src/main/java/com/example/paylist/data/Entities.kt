package com.example.paylist.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "payment_templates")
data class PaymentTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "Другое",
    val amountMinor: Long,
    val currency: String = "EUR",
    val recurrence: String = "ONCE", // ONCE, MONTHLY
    val startDate: String,            // ISO yyyy-MM-dd
    val dueDay: Int,
    val endDate: String? = null,      // independent date limit
    val amountLimitMinor: Long? = null, // independent total limit
    val active: Boolean = true,
    val logoUri: String? = null,
    val notes: String = ""
)

@Entity(
    tableName = "payment_occurrences",
    foreignKeys = [ForeignKey(
        entity = PaymentTemplate::class,
        parentColumns = ["id"],
        childColumns = ["templateId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("templateId"), Index(value = ["templateId", "periodKey"], unique = true)]
)
data class PaymentOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val periodKey: String,       // yyyy-MM
    val plannedDate: String,     // ISO yyyy-MM-dd
    val dueDate: String,
    val amountMinor: Long,
    val paidMinor: Long = 0,
    val status: String = "PLANNED" // PLANNED, PARTIAL, PAID, OVERDUE, CANCELLED
)

@Entity(
    tableName = "payment_operations",
    foreignKeys = [ForeignKey(
        entity = PaymentOccurrence::class,
        parentColumns = ["id"],
        childColumns = ["occurrenceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("occurrenceId")]
)
data class PaymentOperation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceId: Long,
    val operationDate: String,
    val amountMinor: Long,
    val comment: String = ""
)
