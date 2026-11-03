package com.example.paylist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import coil.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.paylist.data.OccurrenceWithTemplate
import com.example.paylist.data.PaymentOperation
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Screen(val label: String) {
    PAYMENTS("Платежи"), HISTORY("История"), ADD("Добавить"), DATA("Данные")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayListApp(viewModel: PayListViewModel) {
    var screen by remember { mutableStateOf(Screen.PAYMENTS) }
    var selected by remember { mutableStateOf<OccurrenceWithTemplate?>(null) }
    val month by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val payments by viewModel.monthPayments.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val operationsFlow = remember(selected?.id) { selected?.let { viewModel.operations(it.id) } }
    val operationItems = operationsFlow?.collectAsStateWithLifecycle(initialValue = emptyList())?.value ?: emptyList()
    val context = LocalContext.current
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) viewModel.exportData(context, uri, "csv") }
    val xlsxExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri -> if (uri != null) viewModel.exportData(context, uri, "xlsx") }
    val zipExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) viewModel.exportData(context, uri, "zip") }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.importData(context, uri) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selected != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.setLogo(selected!!.templateId, uri.toString())
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error!!)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PayList", fontWeight = FontWeight.SemiBold)
                        Text("Учёт платежей", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { screen = Screen.DATA }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Импорт и экспорт")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item; selected = null },
                        icon = {
                            Icon(
                                when (item) {
                                    Screen.PAYMENTS -> Icons.Default.CalendarMonth
                                    Screen.HISTORY -> Icons.Default.History
                                    Screen.ADD -> Icons.Default.AddCircle
                                    Screen.DATA -> Icons.Default.FolderOpen
                                }, contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                selected != null -> PaymentDetailScreen(
                    payment = selected!!,
                    onBack = { selected = null },
                    operations = operationItems,
                    onPay = { amount, date ->
                        viewModel.addOperation(selected!!.id, amount, date) { selected = null }
                    },
                    onDeleteOperation = { operation -> viewModel.deleteOperation(operation) { selected = null }
                    },
                    onEditOperation = { operation, amount, date -> viewModel.updateOperation(operation, amount, date) { selected = null } },
                    onChooseLogo = { logoPicker.launch(arrayOf("image/*")) },
                    onSearchLogo = {
                        val query = URLEncoder.encode("${selected!!.title} logo", "UTF-8")
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q=$query")))
                    }
                )
                screen == Screen.PAYMENTS -> PaymentsScreen(
                    month, payments, filter,
                    onMonthChange = viewModel::changeMonth,
                    onFilter = viewModel::setFilter,
                    onSelect = { selected = it },
                    onAdd = { screen = Screen.ADD }
                )
                screen == Screen.HISTORY -> HistoryScreen(history, onSelect = { selected = it })
                screen == Screen.ADD -> AddPaymentScreen(
                    onSave = { title, category, amount, currency, monthly, date, endDate, totalLimit ->
                        viewModel.addPayment(title, category, amount, currency, monthly, date, endDate, totalLimit) {
                            screen = Screen.PAYMENTS
                        }
                    }
                )
                else -> DataScreen(
                    onImport = { importPicker.launch(arrayOf("text/*", "application/zip", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
                    onExportCsv = { csvExporter.launch("PayList.csv") },
                    onExportXlsx = { xlsxExporter.launch("PayList.xlsx") },
                    onBackup = { zipExporter.launch("PayList_backup.zip") }
                )
            }
        }
    }
}

@Composable
private fun PaymentsScreen(
    month: YearMonth, payments: List<OccurrenceWithTemplate>, filter: String,
    onMonthChange: (Long) -> Unit, onFilter: (String) -> Unit,
    onSelect: (OccurrenceWithTemplate) -> Unit, onAdd: () -> Unit
) {
    val filtered = payments.filter {
        filter == "Все" || when (filter) {
            "Ожидает" -> it.status == "PLANNED"
            "Оплачено" -> it.status == "PAID"
            "Частично" -> it.status == "PARTIAL"
            "Просрочено" -> it.status == "OVERDUE"
            else -> true
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onMonthChange(-1) }) { Icon(Icons.Default.ChevronLeft, "Предыдущий месяц") }
            Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { onMonthChange(1) }) { Icon(Icons.Default.ChevronRight, "Следующий месяц") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Все", "Ожидает", "Оплачено", "Частично", "Просрочено").forEach { value ->
                FilterChip(selected = filter == value, onClick = { onFilter(value) }, label = { Text(value) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("В этом месяце платежей пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(filtered, key = { it.id }) { payment ->
                    PaymentCard(payment, onClick = { onSelect(payment) })
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Добавить платёж") },
            modifier = Modifier.align(Alignment.End).padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun PaymentCard(payment: OccurrenceWithTemplate, onClick: () -> Unit) {
    val statusLabel = when (payment.status) {
        "PAID" -> "Оплачено"
        "PARTIAL" -> "Частично"
        "OVERDUE" -> "Просрочено"
        else -> "Ожидает"
    }
    val statusColor = when (payment.status) {
        "PAID" -> MaterialTheme.colorScheme.tertiary
        "OVERDUE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (payment.logoUri != null) {
                AsyncImage(model = payment.logoUri, contentDescription = "Логотип ${payment.title}", modifier = Modifier.size(48.dp))
            } else {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.ReceiptLong, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(payment.title, fontWeight = FontWeight.SemiBold)
                Text("${payment.category} · ${payment.plannedDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            Text("${money(payment.amountMinor - payment.paidMinor)} ${currencySymbol(payment.currency)}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PaymentDetailScreen(
    payment: OccurrenceWithTemplate,
    operations: List<PaymentOperation>,
    onBack: () -> Unit,
    onPay: (Double, LocalDate) -> Unit,
    onDeleteOperation: (PaymentOperation) -> Unit,
    onEditOperation: (PaymentOperation, Double, LocalDate) -> Unit,
    onChooseLogo: () -> Unit,
    onSearchLogo: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var showPay by remember { mutableStateOf(false) }
    var editingOperation by remember { mutableStateOf<PaymentOperation?>(null) }
    var editAmount by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Назад") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (payment.logoUri != null) {
                AsyncImage(model = payment.logoUri, contentDescription = "Логотип ${payment.title}", modifier = Modifier.size(52.dp))
            } else {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.ReceiptLong, null, Modifier.padding(14.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(payment.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(payment.category, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSearchLogo) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Найти логотип") }
            OutlinedButton(onClick = onChooseLogo) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("Выбрать") }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Сумма платежа", style = MaterialTheme.typography.labelMedium)
                Text("${money(payment.amountMinor)} ${currencySymbol(payment.currency)}", style = MaterialTheme.typography.headlineMedium)
                HorizontalDivider()
                DetailLine("Дата оплаты", payment.plannedDate)
                DetailLine("Оплачено", "${money(payment.paidMinor)} ${currencySymbol(payment.currency)}")
                DetailLine("Остаток", "${money(payment.amountMinor - payment.paidMinor)} ${currencySymbol(payment.currency)}")
                DetailLine("Статус", payment.status)
            }
        }
        Text("Операции", style = MaterialTheme.typography.titleMedium)
        if (operations.isEmpty()) {
            Text("Операций пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            operations.forEach { operation ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${money(operation.amountMinor)} ${currencySymbol(payment.currency)}", fontWeight = FontWeight.SemiBold)
                            Text(operation.operationDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (operation.comment.isNotBlank()) Text(operation.comment, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { editingOperation = operation; editAmount = money(operation.amountMinor); editDate = operation.operationDate }) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить операцию")
                        }
                        IconButton(onClick = { onDeleteOperation(operation) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить операцию")
                        }
                    }
                }
            }
        }
        if (payment.status != "PAID") {
            Button(onClick = { showPay = !showPay }, modifier = Modifier.fillMaxWidth()) { Text("Добавить оплату") }
        }
        if (editingOperation != null) {
            AlertDialog(
                onDismissRequest = { editingOperation = null },
                title = { Text("Изменить операцию") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(editAmount, { editAmount = it }, label = { Text("Сумма") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(editDate, { editDate = it }, label = { Text("Дата (ГГГГ-ММ-ДД)") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val value = editAmount.replace(',', '.').toDoubleOrNull()
                        val parsed = runCatching { LocalDate.parse(editDate) }.getOrNull()
                        if (value != null && value > 0 && parsed != null) editingOperation?.let { onEditOperation(it, value, parsed) }
                        editingOperation = null
                    }, enabled = (editAmount.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 && runCatching { LocalDate.parse(editDate) }.isSuccess) { Text("Сохранить") }
                },
                dismissButton = { TextButton(onClick = { editingOperation = null }) { Text("Отмена") } }
            )
        }
        if (showPay) {
            OutlinedTextField(amount, { amount = it }, label = { Text("Сумма операции") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(date, { date = it }, label = { Text("Дата (ГГГГ-ММ-ДД)") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { amount.replace(",", ".").toDoubleOrNull()?.let { value -> runCatching { LocalDate.parse(date) }.getOrNull()?.let { onPay(value, it) } } },
                enabled = (amount.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0 && runCatching { LocalDate.parse(date) }.isSuccess,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Сохранить оплату") }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HistoryScreen(historyItems: List<OccurrenceWithTemplate>, onSelect: (OccurrenceWithTemplate) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("История платежей", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (historyItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Оплаченных платежей пока нет") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(historyItems, key = { it.id }) { payment -> PaymentCard(payment, onClick = { onSelect(payment) }) }
            }
        }
    }
}

@Composable
private fun AddPaymentScreen(onSave: (String, String, Double, String, Boolean, LocalDate, LocalDate?, Double?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Другое") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("EUR") }
    var monthly by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var endDate by remember { mutableStateOf("") }
    var totalLimit by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Новый платёж", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(category, { category = it }, label = { Text("Категория") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("Сумма") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(currency, { currency = it.uppercase() }, label = { Text("Валюта (EUR, USD, RUB)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(date, { date = it }, label = { Text("Дата оплаты (ГГГГ-ММ-ДД)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ежемесячный платёж", modifier = Modifier.weight(1f))
            Switch(checked = monthly, onCheckedChange = { monthly = it })
        }
        if (monthly) {
            OutlinedTextField(endDate, { endDate = it }, label = { Text("Последняя дата начисления (необязательно)") }, placeholder = { Text("ГГГГ-ММ-ДД") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(totalLimit, { totalLimit = it }, label = { Text("Общий лимит суммы (необязательно)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Text("Ограничения независимы: можно указать одно, оба или ни одного.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                val value = amount.replace(",", ".").toDoubleOrNull()
                val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                if (title.isNotBlank() && value != null && value > 0 && parsedDate != null) {
                    val parsedEndDate = if (monthly && endDate.isNotBlank()) runCatching { LocalDate.parse(endDate) }.getOrNull() else null
                    val limit: Double? = if (monthly && totalLimit.isNotBlank()) totalLimit.replace(',', '.').toDoubleOrNull() else null
                    if ((endDate.isBlank() || parsedEndDate != null) && (totalLimit.isBlank() || (limit != null && limit > 0.0))) {
                        onSave(title, category, value, currency, monthly, parsedDate, parsedEndDate, limit)
                    }
                }
            },
            enabled = title.isNotBlank() && (amount.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 && runCatching { LocalDate.parse(date) }.isSuccess &&
                (endDate.isBlank() || runCatching { LocalDate.parse(endDate) }.isSuccess) &&
                (totalLimit.isBlank() || (totalLimit.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить платёж") }
    }
}

@Composable
private fun DataScreen(
    onImport: () -> Unit,
    onExportCsv: () -> Unit,
    onExportXlsx: () -> Unit,
    onBackup: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Импорт и экспорт", style = MaterialTheme.typography.headlineSmall)
        Text("Резервная копия содержит шаблоны платежей, периоды и всю историю операций.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Импорт CSV / XLSX / ZIP")
        }
        OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.TableView, null); Spacer(Modifier.width(8.dp)); Text("Экспорт CSV")
        }
        OutlinedButton(onClick = onExportXlsx, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Description, null); Spacer(Modifier.width(8.dp)); Text("Экспорт XLSX")
        }
        Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Archive, null); Spacer(Modifier.width(8.dp)); Text("Создать ZIP-резервную копию")
        }
        Text("При импорте записи с совпадающими идентификаторами заменяются данными файла. Перед восстановлением рекомендуется создать резервную копию.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun money(minor: Long): String = "%.2f".format(Locale.US, minor / 100.0)
private fun currencySymbol(code: String): String = when (code) {
    "EUR" -> "€"; "USD" -> "$"; "RUB" -> "₽"; else -> code
}
