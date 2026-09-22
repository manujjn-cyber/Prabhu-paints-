# Prabhu Paints — Hindi / English shop manager

**Status: working local pilot source, not a finished APK or deployed cloud service.**
Prepared for Prabhu Paints, Modinagar. No real customer records, stock quantities or price lists are bundled. The application starts with an empty shop. Screens shown through development fixtures, if any, are synthetic.

## What works in this pilot

- Hindi / English interface with remembered language choice.
- Owner and cashier accounts; shared server-side records for multiple devices.
- Brand, product, shade, pack size, opening stock, selling price, per-product tax and low-stock threshold.
- Asian Paints, Berger, Nerolac, Birla Opus, Indigo, Decora and Other brand choices.
- Whole sealed packs/pieces; different pack sizes and shades are separate stock records.
- Search stock, create multi-item sales records, customer credit, outstanding balances and payments.
- Server assigns sequential bill numbers and calculates money in paise, with per-line tax rounding.
- Transactions prevent two phones selling the same last pack. Prices changed after selection require review.
- Purchase receipts with supplier/bill reference; physical-count corrections with reason.
- Owner-only selling price changes, expenses and full returns/cancellations.
- Full returns restore stock once and require acknowledgement of refund of all collected money. No automatic bank/UPI refunds are performed.
- Overview, all-time sales totals, customer dues, expenses and CSV export.
- Owner database backup and audit log; password change revokes that user's existing sessions.
- Dashboard refreshes from the server approximately every 15 seconds while idle. Other screens refresh on navigation data reload, a successful save or the Refresh button. This is not instant push synchronization.
- HTTPS-only Android WebView project with print/PDF menu, refresh and browser handoff for export.

## Not yet ready for live accounting

Sales documents are explicitly labelled **sales records, not GST tax invoices**. Tax inputs are manual and have no assumed rate. This pilot does not implement GST registration profiles, HSN/SAC, state/place-of-supply validation, CGST/SGST versus IGST, statutory credit notes, e-invoicing or GST-return exports. Have the shop's actual configuration and applicable requirements verified before tax-invoice use.

Also pending: partial returns, purchase returns and supplier balances, mixed payment methods, discounts/freight rules, quotations, product editing/archival, expiry/batch tracking, tinting/base conversion, fractional loose quantities, barcode scanning, Excel import, receipt sharing, Bluetooth-printer integration, staff deactivation/reset screens, date-filtered reports, costing/profit, automated offsite backups, restore UI and offline transaction synchronization. See PRODUCT_CHECKLIST.md for the completion plan.

## Local developer preview

Requires Python 3.12+. The server uses only Python's standard library.

```sh
cd server
export ADMIN_PASSWORD='choose-a-new-strong-password-at-least-12-characters'
export PUBLIC_ORIGIN='http://localhost:8080'
export DB_PATH='./shop.db'
python3 app.py
```

Open http://localhost:8080 and sign in as **owner** with the chosen password. The initial password is read only when creating the first account. Change the example password before any use. Do not put real passwords in source control. The URL in PUBLIC_ORIGIN must exactly match the browser origin (including scheme and port).

## Cloud deployment preparation

The Dockerfile packages this server. No deployment has been made and no hosting costs have been incurred by this project.

For Railway or an equivalent service:

1. Deploy the project from a private repository using the root Dockerfile.
2. Attach a persistent volume at `/data`. Set `DB_PATH=/data/shop.db`.
3. Set a secret `ADMIN_PASSWORD` and the exact HTTPS `PUBLIC_ORIGIN` with no trailing slash.
4. Use one application replica, because this pilot uses a single SQLite file. Do not put independent copies on multiple replicas.
5. Terminate HTTPS through the hosting platform and use `/health` for readiness.
6. Sign in, add cashier accounts and use the same deployed URL on every phone.
7. Before real operation, replace the standard-library HTTP serving layer with a production-hardened service, add proxy limits/timeouts, independently review security, configure scheduled encrypted offsite backups, and complete the pending business rules. This is a pilot, not a production security certification.

A paid plan or usage charges may apply to the chosen host. Confirm the actual plan and costs before deployment. Nothing in this package signs up for a paid service.

## Build the Android pilot APK

**No APK has been compiled or device-tested in this environment.** The Android SDK and Gradle were absent and the SDK download timed out. The Android project and GitHub Actions workflow are provided for the next build stage.

The project uses JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2 and Android SDK 35. There is no Gradle wrapper binary in this archive; install the stated Gradle version or use the included workflow.

After the server has a working HTTPS URL:

- Publish this source to a private GitHub repository and run **Build Android pilot APK**, passing the shop's HTTPS origin. The workflow downloads dependencies, runs `assembleDebug` and uploads `Prabhu-Paints-Pilot-APK` if successful.
- Alternatively, from the `android` directory:

```sh
gradle assembleDebug -PshopUrl=https://your-shop-host.example
```

The expected output is `android/app/build/outputs/apk/debug/app-debug.apk`. Build success has not yet been verified. The URL is checked at build time and is embedded in the app; changing hosts requires rebuilding. The app needs Android 8 or newer and an updated Android System WebView.

This workflow creates a **debug pilot APK**, not a production-signed release. Before distributing a durable production app, create and retain a private release-signing key, configure release signing, and test installation and updates on the actual phones. Fresh CI debug keys may prevent installing later builds over earlier builds.

Inside Android, use the app's top-right menu for **Print / PDF**. CSV and database downloads use **Open browser / Export** and require signing in again in that browser; sessions are not copied between apps. Browser `Print / PDF` uses the browser's print feature. Bluetooth thermal-printer support is not implemented.

## Backups and restoration

The owner can download a consistent SQLite backup. This includes financial/customer data, password hashes and sessions; protect it as sensitive shop data. It is not encrypted by the application.

To restore, an administrator must stop the service, retain a separate copy of the current database, replace the database from the verified backup, remove obsolete `-wal`/`-shm` companions while the service is stopped, revoke restored sessions (`DELETE FROM sessions;`), then restart and reconcile the restored bill/stock totals. Never restore over a running database. A restore test and scheduled offsite backup process are launch prerequisites.

## Validation

```sh
cd server
python3 -m unittest test_app -v
node test_ui.cjs
```

Backend tests cover authentication, cross-origin protection, duplicate saves, concurrent stock competition, transaction rollback, credit collections, overpayments, full cancellations, cashier restrictions, stale prices and input validation. Lightweight UI tests render eight screens in both languages and check text escaping. These UI checks do not substitute for visual browser or Android-device tests. Chromium was not installed in the available runtime, so visual/device testing is outstanding.

## Technical references

- Android command-line builds: https://developer.android.com/build/building-cmdline
- Android WebView guidance: https://developer.android.com/develop/ui/views/layout/webapps/webview

These references describe the build and wrapper platform; they do not certify this implementation.
