# Validation record

## Passed locally

- Python source compilation.
- JavaScript syntax check (`node --check`).
- 10 backend integration tests, run again after the final backend changes: all passed.
- 16 lightweight page/language rendering checks (8 screens × English/Hindi).
- Product text HTML escaping and bill rendering checks.

Backend scenarios tested: authentication/origin rejection; idempotent invoice saves; concurrent last-pack sales; rollback when a later bill line is invalid; credit/customer requirements and payment retries; prevention of overpayment; full return restoring stock once; refusal of payment on cancelled bills; cashier restrictions; changed-price rejection; purchase and expense retries; duplicate product rejection; invalid money rejection.

## Not verified

- Android compilation, signing, installation or upgrades.
- Android WebView behavior, Android print menu or actual receipt printer.
- Visual browser layout, accessibility or mobile keyboard behavior: Chromium was unavailable.
- Real deployment, reverse proxy configuration, persistent volume and multi-phone network use.
- Backup restore exercise, long-running load or production security review.
- GST compliance: tax invoices are not implemented.
- Owner password-change route is implemented but not separately covered by these integration tests.

No live shop data was used. Test/sample prices do not represent brand price lists.
