package io.noriskservis.wpayin.wallet.model

/**
 * Assets shown by the wallet must have an explicit source of trust. Native
 * coins and the bundled contract catalog are maintained by the app. An ERC-20
 * can also be trusted when the user deliberately adds that exact contract or
 * when the explorer provides a positive market quote for that exact contract.
 *
 * A positive explorer balance alone is intentionally not proof that a token
 * is legitimate: unsolicited scam and advertising tokens are commonly sent
 * to public wallet addresses.
 */
fun Asset.isTrustedWalletAsset(customTokens: Collection<KnownToken>): Boolean {
    if (isNative) return true
    val contract = contractAddress?.trim().orEmpty()
    if (contract.isEmpty()) return false

    val explicitlyTrusted = KnownTokenCatalog.tokens.any { token ->
        token.blockchain == blockchain &&
            token.contractAddress.equals(contract, ignoreCase = true)
    } || customTokens.any { token ->
        token.blockchain == blockchain &&
            token.contractAddress.equals(contract, ignoreCase = true)
    }
    return explicitlyTrusted || (priceUsd.isFinite() && priceUsd > 0.0)
}
