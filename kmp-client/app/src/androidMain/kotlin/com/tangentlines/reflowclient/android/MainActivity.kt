package com.tangentlines.reflowclient.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.tangentlines.reflow.BuildConfig
import com.tangentlines.reflowclient.shared.ui.utils.BuildSecretsDefaults
import com.tangentlines.reflowclient.shared.ui.ReflowApp

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        // (You can also set navigation bar similarly if desired.)
        BuildSecretsDefaults.clientKey = BuildConfig.SECRET_API_KEY
        setContent { ReflowApp() }

    }

}
