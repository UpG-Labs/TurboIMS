package io.github.vvb2060.ims

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MainScreen(
                uiState = uiState,
                onFeatureSwitchChange = viewModel::onFeatureSwitchChange,
                onApplyConfiguration = { viewModel.onApplyConfiguration(this) },
                onSelectSim = viewModel::onSelectSim,
                onToggleLanguage = { viewModel.toggleLanguage(this) },
                openSimSelectionDialog = viewModel::openSimSelectionDialog,
                dismissSimSelectionDialog = viewModel::dismissSimSelectionDialog,
                dismissConfigAppliedDialog = viewModel::dismissConfigAppliedDialog,
                dismissShizukuUpdateDialog = viewModel::dismissShizukuUpdateDialog,
                onRequestShizukuPermission = { viewModel.requestShizukuPermission(0) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateShizukuStatus()
    }

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getLanguage(newBase)
        LocaleHelper.updateResources(newBase, language)
        super.attachBaseContext(newBase)
    }
}
