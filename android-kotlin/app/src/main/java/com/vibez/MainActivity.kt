package com.vibez

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibez.ui.screens.HomeScreen
import com.vibez.ui.screens.PlaylistPreviewScreen
import com.vibez.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: HomeViewModel = viewModel()
            if (vm.playlist == null) {
                HomeScreen(vm)
            } else {
                PlaylistPreviewScreen(playlist = vm.playlist!!, onReset = vm::reset)
            }
        }
    }
}
