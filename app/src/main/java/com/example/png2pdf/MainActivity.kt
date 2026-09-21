package com.example.png2pdf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.png2pdf.ui.MergeScreen
import com.example.png2pdf.ui.MergeViewModel
import com.example.png2pdf.ui.theme.Png2PdfTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MergeViewModel by viewModels { MergeViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            Png2PdfTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                // 从相册/文件管理器"分享"进来的图片（只处理一次）
                LaunchedEffect(Unit) {
                    val uris = viewModel.urisFromIntent(intent)
                    if (uris.isNotEmpty()) viewModel.addUris(uris)
                }

                MergeScreen(state = state, viewModel = viewModel)
            }
        }
    }

    /** 应用已在前台时又被分享一次：走 onNewIntent */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uris = viewModel.urisFromIntent(intent)
        if (uris.isNotEmpty()) viewModel.addUris(uris)
    }
}
