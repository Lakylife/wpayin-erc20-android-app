# Wpayin Wallet for Android 1.2.0

Version 1.2.0, version code 6. Released 2026-11-11.

## Changes

- Add Quintium, the project's own EVM-compatible Proof-of-Work chain: testnet coin
  tQU (chain ID 52402) by default, mainnet coin QU (chain ID 52401) from
  1 January 2027. The same feature set as Wpayin for iOS 1.2.0.
- New **Settings → Quintium** screen: network switch, node RPC URL, optional block
  explorer URL, connection test with chain ID check, and the network parameters.
- Quintium address derivation (Ethereum path `m/44'/60'/0'/0`), native balance,
  EIP-155 sending through Wallet Core, and history from the node's
  `aether_addressTxs` index.
- Quintium coin icon for QU and tQU, and Quintium in Manage networks. Wallets set up
  before this version get Quintium switched on once.
- Quintium is left out of swap and bridge, which do not support the chain yet.
- Optional `QUINTIUM_*_RPC_URL` and `QUINTIUM_*_EXPLORER_URL` Gradle properties for
  builds that ship with a node preset.
- Unit tests for the Quintium parameters, settings and history parsing.

## Verification and distribution

- Sending and history against a live Quintium node were not run for this release,
  because there is no public node yet.
- Device signing vectors, biometric/device-credential flows and end-to-end
  transfers on a physical device were not run for this release.

No APK is attached. No production signing configuration is included; build from
source with your own signing key. The preview flavor cannot create real wallets.
Optional API keys and node URLs are read from Gradle properties and are not part
of this repository.

iOS is versioned independently in
[its own repository](https://github.com/Lakylife/wpayin-erc20-ios-app).
