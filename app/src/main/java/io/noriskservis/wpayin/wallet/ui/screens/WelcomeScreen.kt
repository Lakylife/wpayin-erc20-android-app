package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.ProductionVariantNotice
import io.noriskservis.wpayin.wallet.ui.components.SecondaryButton
import io.noriskservis.wpayin.wallet.ui.components.WpayinLogo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary

@Composable
fun WelcomeScreen(
    productionWalletEnabled: Boolean,
    onCreate: () -> Unit,
    onImport: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 30.dp, vertical = 22.dp),
        ) {
            Spacer(Modifier.weight(1.1f))
            WpayinLogo(80)
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                stringResource(R.string.secure_simple_decentralized),
                color = TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            if (!productionWalletEnabled) {
                ProductionVariantNotice(Modifier.padding(bottom = 18.dp))
            }
            PrimaryButton(
                title = stringResource(R.string.create_new_wallet),
                onClick = onCreate,
                enabled = productionWalletEnabled,
            )
            SecondaryButton(
                title = stringResource(R.string.import_existing_wallet),
                onClick = onImport,
                enabled = productionWalletEnabled,
                modifier = Modifier.padding(top = 14.dp),
            )
            Spacer(Modifier.weight(0.5f))
        }
    }
}
