package com.example.paylist.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.paylist.data.PaymentOperation
import com.example.paylist.data.PaymentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.math.BigDecimal
import java.math.RoundingMode

class PayListViewModel(private val repository: PaymentRepository) : ViewModel() {
    val selectedMonth = MutableStateFlow(YearMonth.now())
    val filter = MutableStateFlow("Все")
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val monthPayments = selectedMonth.flatMapLatest { repository.month(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = repository.history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun operations(occurrenceId: Long) = repository.operations(occurrenceId)
    fun clearError() { error.value = null }
    fun changeMonth(delta: Long) { selectedMonth.value = selectedMonth.value.plusMonths(delta) }
    fun setMonth(month: YearMonth) { selectedMonth.value = month }
    fun setFilter(value: String) { filter.value = value }

    fun addPayment(
        title: String, category: String, amount: Double, currency: String, monthly: Boolean,
        date: LocalDate, endDate: LocalDate?, totalLimit: Double?, onDone: () -> Unit
    ) = launchSafely(onDone) {
        repository.createPayment(
            title, category, toMinor(amount), currency, if (monthly) "MONTHLY" else "ONCE", date,
            endDate, totalLimit?.let(::toMinor)
        )
    }

    fun addOperation(id: Long, amount: Double, date: LocalDate, onDone: () -> Unit) = launchSafely(onDone) {
        repository.addOperation(id, toMinor(amount), date)
    }

    fun deleteOperation(operation: PaymentOperation, onDone: () -> Unit) = launchSafely(onDone) {
        repository.deleteOperation(operation.id, operation.occurrenceId)
    }

    fun updateOperation(operation: PaymentOperation, amount: Double, date: LocalDate, onDone: () -> Unit) = launchSafely(onDone) {
        repository.updateOperation(operation, toMinor(amount), date)
    }

    fun setLogo(templateId: Long, uri: String?) = launchSafely({}) { repository.updateLogo(templateId, uri) }
    fun exportData(context: Context, uri: Uri, format: String) = launchSafely({}) { repository.export(context, uri, format) }
    fun importData(context: Context, uri: Uri) = launchSafely({}) { repository.import(context, uri) }

    private fun toMinor(amount: Double): Long {
        require(amount.isFinite() && amount > 0) { "Введите корректную сумму" }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact().also { require(it > 0) }
    }

    private fun launchSafely(onDone: () -> Unit, action: suspend () -> Unit) {
        // Set the guard synchronously, before launching the coroutine, so rapid taps cannot enqueue duplicates.
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            error.value = null
            try {
                action()
                onDone()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                error.value = t.message ?: "Не удалось выполнить операцию"
            } finally {
                busy.value = false
            }
        }
    }
}

class PayListViewModelFactory(private val repository: PaymentRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PayListViewModel(repository) as T
}
