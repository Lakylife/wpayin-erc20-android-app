package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.components.WpayinLogo
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary

@Composable
fun AppLockScreen(
    onUnlock: ((Result<Unit>) -> Unit) -> Unit,
) {
    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    WpayinBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        ) {
            WpayinLogo(size = 74)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 26.dp)
                    .size(76.dp)
                    .background(Surface, CircleShape),
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Indigo,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                "Wallet locked",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                "Authenticate to view balances, addresses, and transaction activity.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            PrimaryButton(
                title = if (isAuthenticating) "Authenticating…" else "Unlock Wallet",
                enabled = !isAuthenticating,
                onClick = {
                    isAuthenticating = true
                    errorMessage = null
                    onUnlock { result ->
                        isAuthenticating = false
                        result.onFailure { errorMessage = it.message ?: "Authentication failed" }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
            )
        }
    }
}
