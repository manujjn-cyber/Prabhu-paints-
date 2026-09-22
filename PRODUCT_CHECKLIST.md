# Completion checklist — paint-shop operations

This separates implemented pilot behavior from work required to finish the requested shop software. “Pilot” means implemented locally; it does not mean deployed, compiled into an APK or verified on a real phone.

| Area | Necessary details | Current status |
|---|---|---|
| Shop setup | Business name, address, contact, GSTIN, logo, financial year, opening date | Name/city fixed; editable legal profile pending |
| Language | Hindi/English, Unicode names, rupee formatting | Pilot; some validation messages remain bilingual/English |
| Shared data | Same stock and bills on every phone, authenticated users | Pilot server; cloud deployment pending |
| SKU identity | Brand + product + shade/code + pack | Pilot; uniqueness is case-insensitive |
| Stock quantities | Opening packs, stock in/out, reorder level | Pilot whole packs/pieces |
| Product taxonomy | Enamel, emulsion, primer, putty, POP, thinner, tools/accessories | Product naming works; dedicated categories pending |
| Price maintenance | Owner updates rates, audit history, stale-price checks | Pilot |
| Bulk catalog | CSV/Excel import, duplicate review, editable product details | CSV export only; import/edit pending |
| Purchase receipt | Quantity, landed total, supplier invoice reference | Pilot; supplier reference is free text |
| Supplier ledger | Vendor contacts, payable balance, partial payments, debit notes | Pending |
| Billing | Multiple items, sequential numbering, quantities, tax, stock reduction | Pilot sales records |
| GST tax invoice | GSTIN, HSN, taxable value, intra/interstate tax, place of supply, statutory numbering and credit notes | Pending; pilot must not issue GST tax invoices |
| Schemes and discounts | Line/bill discounts, free quantities, dealer schemes, freight and rounding policy | Pending |
| Collections | Paid amount, outstanding amount, later receipt, overpayment rejection | Pilot |
| Payment modes | Cash, UPI, bank/card, split modes, references and cash closing | Pending |
| Customer ledger | Contact and outstanding balance by customer | Pilot; chronological downloadable ledger pending |
| Opening balances | Customer/vendor opening dues with audit trail | Pending |
| Return handling | Full item return, refund acknowledgement, stock restoration | Pilot full returns only |
| Partial returns | Original-rate allocation, tax reversal, credits and partial refunds | Pending |
| Stock corrections | Owner count correction with reason, concurrency check | Pilot |
| Damages and tinting | Saleable vs damaged stock, batch/expiry, base/tint conversion and tint cost | Pending |
| Quotation/order | Estimate validity, convert once to sale, reservation, delivery challan | Pending |
| Expenses | Category, amount, note | Pilot |
| Reports | Sales, dues, low stock, all-time totals, expenses, CSV | Pilot; date filters, margin/valuation/profit pending |
| Permissions | Owner versus cashier, server enforcement | Pilot; granular permissions/deactivation/reset UI pending |
| Audit | User/time/action, original invoices retained on cancellation | Pilot; dedicated retention/pagination/review screens pending |
| Duplicate safety | Retry-safe bills, payments, purchases, expenses | Pilot tested; interrupted form drafts are not persisted across reloads |
| Offline behavior | Clear failure and no local-only confirmed sales | Online-only pilot; offline synchronization pending |
| Backup | Owner consistent database download | Pilot; scheduled encrypted offsite backup and tested restoration pending |
| Security | Hashed passwords, server sessions, HTTPS cookie support, origin checks, cashier restrictions | Pilot; independent security/hosting hardening pending |
| Owner access | Current-password change and session revocation | Pilot; account recovery and staff lifecycle pending |
| Printing/sharing | Browser print/PDF; Android native print menu | Source prepared; real printer/device verification pending |
| Android packaging | App identity, HTTPS host lock, no file access, compile workflow | Source prepared; APK build, signing and device tests pending |
| Deployment | Persistent database, HTTPS, volume backups and monitoring | Not deployed; cloud account connection required |
| Data migration | Load real price list and reconcile stock/customer/vendor openings | Pending actual source files and validation |

## Launch sequence

1. Complete remaining accounting rules, legal profile and GST invoice configuration before real invoicing.
2. Resolve partial-return, discounts, payment-mode, supplier-credit and opening-balance workflows; these should not be hidden inside general notes.
3. Deploy the service with persistent storage, hardened serving, access controls and recoverable backups.
4. Import a copy of stock and balances; reconcile totals with the shop's current records.
5. Build a signed Android release and test on at least two real phones.
6. Run a pilot with test transactions: concurrent last-pack sales, failed network replies, duplicate taps, credit collections, refunds, permissions and restore.
7. Confirm opening balances, invoice sequence and user accounts; then enable real operation.

## Shop-specific choices already applied

Prabhu Paints, Modinagar; retail/wholesale usage; priority brands Asian Paints, Berger, Nerolac, Birla Opus, Indigo and Decora; Hindi and English; several phones sharing one shop database. No prices, GST rates, customer debts, legal registration details or dealer policies were invented or imported from earlier conversation summaries.
