# Wpayin Wallet for Android 1.1.3

Version 1.1.3, version code 5. Released 2026-10-26.

## Changes

- Disclose the application fee, fee recipient, total asset debit and gas for both
  transactions before a standard EVM send. Check the combined balance before signing.
- Record successfully broadcast application-fee transfers in Activity.
- Reject stale balance/history responses after deleting or importing a wallet.
- Support system device-credential authentication on Android 8–10 and retain strong
  biometrics/device credentials on Android 11 and newer.
- Protect the main wallet window from screen capture in debug and release builds.
- Add LI.FI swap/bridge execution and P2P trading flows, including optional offer-board support.
- Expand asset/history views, account export, saved addresses, appearance, currency,
  time-zone, localization and network settings.
- Add regression tests for fee review and wallet-session isolation.

## Verification and distribution

- Unit tests: 35 passed (`previewDebug`) and 34 passed (`walletCoreDebug`), 0 failed.
- `walletCoreDebug` and unsigned `walletCoreRelease` builds: succeeded.
- Device signing vectors, biometric/device-credential flows and end-to-end
  transfers on a physical device were not run for this release.

No APK is attached. No production signing configuration is included; build from
source with your own signing key. The preview flavor cannot create real wallets.
Optional API keys are read from Gradle properties and are not part of this repository.

iOS is versioned independently in
[its own repository](https://github.com/Lakylife/wpayin-erc20-ios-app).
