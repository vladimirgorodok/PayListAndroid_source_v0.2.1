package com.example.paylist

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.paylist.data.PayListDatabase
import com.example.paylist.data.PaymentRepository
import com.example.paylist.ui.PayListApp
import com.example.paylist.ui.PayListViewModel
import com.example.paylist.ui.PayListViewModelFactory
import com.example.paylist.ui.theme.PayListTheme
import com.example.paylist.worker.PaymentReminderWorker

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PaymentReminderWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val database = PayListDatabase.getInstance(applicationContext)
        val repository = PaymentRepository(database)
        setContent {
            PayListTheme {
                val vm: PayListViewModel = viewModel(factory = PayListViewModelFactory(repository))
                PayListApp(viewModel = vm)
            }
        }
    }
}
