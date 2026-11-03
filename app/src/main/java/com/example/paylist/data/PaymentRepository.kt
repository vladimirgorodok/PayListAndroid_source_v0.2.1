package com.example.paylist.data

import androidx.room.withTransaction
import android.content.Context
import android.net.Uri
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

class PaymentRepository(
    private val database: PayListDatabase,
    private val dao: PaymentDao = database.paymentDao()
) {
    fun month(periodKey: String): Flow<List<OccurrenceWithTemplate>> = dao.observeMonth(periodKey)
    fun history(): Flow<List<OccurrenceWithTemplate>> = dao.observeHistory()
    fun operations(id: Long): Flow<List<PaymentOperation>> = dao.observeOperations(id)
    suspend fun updateLogo(templateId: Long, uri: String?) = database.withTransaction { dao.updateLogo(templateId, uri) }
    suspend fun export(context: Context, uri: Uri, format: String) = TransferManager.export(context, uri, format, database)
    suspend fun import(context: Context, uri: Uri) = TransferManager.import(context, uri, database)

    suspend fun createPayment(
        title: String, category: String, amountMinor: Long, currency: String,
        recurrence: String, plannedDate: LocalDate, endDate: LocalDate? = null,
        amountLimitMinor: Long? = null
    ) = database.withTransaction {
        require(title.isNotBlank()) { "Укажите название платежа" }
        require(amountMinor > 0) { "Сумма должна быть больше нуля" }
        require(recurrence == "ONCE" || recurrence == "MONTHLY") { "Неизвестный тип повторения" }
        require(currency.matches(Regex("[A-Za-z]{3}"))) { "Укажите трёхбуквенный код валюты, например EUR" }
        require(endDate == null || !endDate.isBefore(plannedDate)) { "Конечная дата не может быть раньше первого платежа" }
        require(amountLimitMinor == null || amountLimitMinor > 0) { "Лимит суммы должен быть больше нуля" }
        require(recurrence == "MONTHLY" || endDate == null && amountLimitMinor == null) {
            "Ограничения серии применяются к ежемесячным платежам"
        }
        val firstAmount = amountLimitMinor?.let { minOf(amountMinor, it) } ?: amountMinor
        val templateId = dao.insertTemplate(
            PaymentTemplate(
                title = title.trim(), category = category.trim().ifBlank { "Другое" },
                amountMinor = amountMinor, currency = currency.uppercase(), recurrence = recurrence,
                startDate = plannedDate.toString(), dueDay = plannedDate.dayOfMonth,
                endDate = endDate?.toString(), amountLimitMinor = amountLimitMinor,
                active = recurrence == "MONTHLY"
            )
        )
        dao.insertOccurrence(PaymentOccurrence(
            templateId = templateId, periodKey = YearMonth.from(plannedDate).toString(),
            plannedDate = plannedDate.toString(), dueDate = plannedDate.toString(), amountMinor = firstAmount
        ))
    }

    suspend fun addOperation(occurrenceId: Long, amountMinor: Long, date: LocalDate, comment: String = "") = database.withTransaction {
        require(amountMinor > 0) { "Сумма операции должна быть больше нуля" }
        val occurrence = dao.getOccurrence(occurrenceId) ?: error("Платёж не найден")
        check(occurrence.status != "PAID" && occurrence.status != "CANCELLED") { "Этот платёж уже закрыт" }
        val remaining = occurrence.amountMinor - occurrence.paidMinor
        require(amountMinor <= remaining) { "Сумма операции превышает остаток" }
        dao.insertOperation(PaymentOperation(occurrenceId = occurrenceId, operationDate = date.toString(), amountMinor = amountMinor, comment = comment.trim()))
        val newPaid = occurrence.paidMinor + amountMinor
        val fullyPaid = newPaid == occurrence.amountMinor
        dao.updatePaid(occurrenceId, newPaid, if (fullyPaid) "PAID" else "PARTIAL")
        if (fullyPaid) createNextOccurrenceIfAllowed(occurrence.templateId, occurrence)
    }

    suspend fun updateOperation(operation: PaymentOperation, amountMinor: Long, date: LocalDate) = database.withTransaction {
        require(amountMinor > 0) { "Сумма операции должна быть больше нуля" }
        val occurrence = dao.getOccurrence(operation.occurrenceId) ?: error("Платёж не найден")
        val current = dao.getOperations(operation.occurrenceId)
        require(current.any { it.id == operation.id }) { "Операция не найдена" }
        val newTotal = current.filterNot { it.id == operation.id }.sumOf { it.amountMinor } + amountMinor
        require(newTotal <= occurrence.amountMinor) { "Сумма операций превышает сумму платежа" }
        dao.updateOperation(operation.copy(operationDate = date.toString(), amountMinor = amountMinor))
        val status = when {
            newTotal == occurrence.amountMinor -> "PAID"
            newTotal > 0 -> "PARTIAL"
            else -> "PLANNED"
        }
        dao.updatePaid(occurrence.id, newTotal, status)
        if (newTotal == occurrence.amountMinor) createNextOccurrenceIfAllowed(occurrence.templateId, occurrence)
        else {
            require(dao.countLaterOccurrencesWithOperations(occurrence.templateId, occurrence.periodKey) == 0) {
                "Нельзя уменьшить оплату старого периода: в более позднем периоде уже есть операции"
            }
            dao.deleteFutureUnpaid(occurrence.templateId, occurrence.periodKey)
            reopenTemplateIfAllowed(occurrence.templateId, occurrence)
        }
    }

    suspend fun deleteOperation(operationId: Long, occurrenceId: Long) = database.withTransaction {
        val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
        val operations = dao.getOperations(occurrenceId)
        if (operations.none { it.id == operationId }) return@withTransaction
        // Deleting a payment from an old period must not erase later paid history.
        require(dao.countLaterOccurrencesWithOperations(occurrence.templateId, occurrence.periodKey) == 0) {
            "Нельзя удалить операцию из старого периода: в более позднем периоде уже есть операции"
        }
        dao.deleteOperation(operationId)
        val newPaid = dao.getOperations(occurrenceId).sumOf { it.amountMinor }
        val status = when {
            newPaid == 0L -> "PLANNED"
            newPaid >= occurrence.amountMinor -> "PAID"
            else -> "PARTIAL"
        }
        dao.updatePaid(occurrenceId, newPaid, status)
        if (newPaid < occurrence.amountMinor) {
            dao.deleteFutureUnpaid(occurrence.templateId, occurrence.periodKey)
            reopenTemplateIfAllowed(occurrence.templateId, occurrence)
        } else if (newPaid == occurrence.amountMinor) {
            createNextOccurrenceIfAllowed(occurrence.templateId, occurrence)
        }
    }


    private suspend fun reopenTemplateIfAllowed(templateId: Long, occurrence: PaymentOccurrence) {
        val template = dao.getTemplate(templateId) ?: return
        if (template.recurrence != "MONTHLY") return
        val totalPaid = dao.totalPaid(templateId)
        val underLimit = template.amountLimitMinor == null || totalPaid < template.amountLimitMinor
        val nextMonth = YearMonth.parse(occurrence.periodKey).plusMonths(1)
        val nextDate = nextMonth.atDay(template.dueDay.coerceAtMost(nextMonth.lengthOfMonth()))
        val beforeEndDate = template.endDate == null || !nextDate.isAfter(LocalDate.parse(template.endDate))
        if (underLimit && beforeEndDate) dao.activateTemplate(templateId)
    }

    private suspend fun createNextOccurrenceIfAllowed(templateId: Long, previous: PaymentOccurrence) {
        val template = dao.getTemplate(templateId) ?: return
        if (template.recurrence != "MONTHLY" || !template.active) return
        val nextMonth = YearMonth.parse(previous.periodKey).plusMonths(1)
        val nextDate = nextMonth.atDay(template.dueDay.coerceAtMost(nextMonth.lengthOfMonth()))
        if (template.endDate != null && nextDate.isAfter(LocalDate.parse(template.endDate))) {
            dao.deactivateTemplate(templateId)
            return
        }
        val totalPaid = dao.totalPaid(templateId)
        val limit = template.amountLimitMinor
        if (limit != null && totalPaid >= limit) {
            dao.deactivateTemplate(templateId)
            return
        }
        val nextAmount = if (limit == null) template.amountMinor else minOf(template.amountMinor, limit - totalPaid)
        if (nextAmount <= 0) {
            dao.deactivateTemplate(templateId)
            return
        }
        val inserted = dao.insertOccurrence(PaymentOccurrence(
            templateId = templateId, periodKey = nextMonth.toString(), plannedDate = nextDate.toString(),
            dueDate = nextDate.toString(), amountMinor = nextAmount
        ))
        if (inserted == -1L && limit != null && totalPaid >= limit) dao.deactivateTemplate(templateId)
    }
}
