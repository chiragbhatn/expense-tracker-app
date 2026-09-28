# Expense Tracker

An Android app for tracking card expenses with merchant cashback, and money
you are owed (udhaar) when you pay for someone else.

## Install on your phone

1. On your phone, open
   **[the latest release](https://github.com/chiragbhatn/expense-tracker-app/releases/latest)**
   and tap `expense-tracker.apk`
   ([direct download](https://github.com/chiragbhatn/expense-tracker-app/releases/latest/download/expense-tracker.apk)).
2. Open the downloaded file. Android asks you to allow your browser to
   install apps the first time; allow it and tap **Install**. If Google Play
   Protect says the app is from an unknown developer, choose
   **More details → Install anyway**.

Requires Android 8.0 or newer. Every build is signed with the same key, so a
newer APK installs over the old one and keeps your data.

## What it does

- **Cashback rules**: a list of merchants where your card gives cashback,
  e.g. Swiggy 10% (on), Zomato 10% (off), Amazon 5% (off). Add a merchant,
  set or edit its percentage, switch it on or off, or delete it.
- **Expenses**: record amount, merchant, payment method (Card, UPI, Cash,
  Other), who it was for, date and a note. Picking a merchant with an
  enabled rule works out the cashback immediately.
- **Udhaar**: everyone who owes you (or whom you owe), with a running balance.
  Paying for someone on the expense screen adds to their udhaar automatically;
  record repayments with **You got** and money you lend with **You gave**.
- **Dashboard**: card spending, cashback received, effective expenses, and
  money to receive, per month or for all time.

## How cashback works

Cashback is **subtracted** from what the card was charged, never added:

```
cashback          = original amount × cashback % / 100
effective expense = original amount − cashback
```

A ₹500 Swiggy card payment with the 10% rule on shows:

```
Original amount      ₹500
Cashback (10%)       −₹50
Effective expense    ₹450
```

Cashback applies only to **card** payments at a merchant whose rule is
**enabled**. Merchant names match ignoring case and spacing ("swiggy" is
Swiggy). Each expense stores the percentage it was saved with, so changing or
deleting a rule later never changes past expenses.

## Paying for someone else

Cashback belongs to the card holder. It lowers your effective expense, but it
never lowers what the other person owes you.

Chirag pays ₹1,000 on Swiggy with his card for Rahul:

| Card / expense side | | Udhaar side | |
|---|---|---|---|
| Card transaction | ₹1,000 | Rahul owes Chirag | **₹1,000** |
| Swiggy cashback (10%) | −₹100 | | |
| Effective expense | ₹900 | | |

The dashboard shows card spending ₹1,000, cashback received ₹100, effective
expenses ₹900 and money to receive from Rahul ₹1,000. Rahul's balance is
₹1,000, not ₹900.

## Data model

Stored with Room (SQLite) on the phone. Amounts are integers in paise and
percentages in basis points (10% = 1000), so there is no rounding drift.

| Table | Columns |
|---|---|
| `expenses` | `original_amount_paise`, `cashback_percentage_bps`, `cashback_amount_paise`, `effective_amount_paise`, `merchant`, `payment_method`, `date_epoch_day`, `note` |
| `cashback_rules` | `merchant`, `cashback_percentage_bps`, `enabled` |
| `people` | `name` |
| `udhaar_entries` | `person_id`, `direction` (`GAVE` / `GOT`), `amount_paise`, `date_epoch_day`, `note`, `expense_id` |

An expense paid for someone has one linked `udhaar_entries` row (`GAVE`, full
original amount). Editing or deleting the expense updates or removes that entry
in the same transaction.

Example `expenses` row for the ₹500 Swiggy payment: original 50000, cashback
percentage 1000, cashback 5000, effective 45000, merchant Swiggy, payment
method CARD.

## Project layout

```
app/src/main/java/io/github/chiragbhatn/expensetracker/
  domain/   Money, Percentage, cashback and udhaar rules (plain Kotlin)
  data/     Room database, DAOs and repositories
  ui/       Jetpack Compose screens and view models
```

## Building

GitHub Actions ([`.github/workflows/android.yml`](.github/workflows/android.yml))
runs on every push:

1. Unit tests (money maths, cashback rules, udhaar), Room repository tests and
   Compose UI tests of the main flows, all on Robolectric.
2. Builds the signed release APK.
3. Installs that APK on an Android emulator, records a Swiggy payment for
   Rahul, a repayment from him and a new cashback rule, and checks the amounts
   on screen. Screenshots are kept as the `device-screenshots` artifact of the
   workflow run.
4. On the default branch, once the walkthrough passes, replaces the `latest`
   release with the new APK.

To build locally you need JDK 17 and the Android SDK:

```
./gradlew testDebugUnitTest assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

### Signing

Release builds are signed with `app/keystore/shared-release.p12`, which is
checked in so every build (local or CI) can update an installed copy. This
repository is public, so anyone can sign an APK with that key; only install
APKs from this repository's releases.

To use a private key instead, add these repository secrets (Settings →
Secrets and variables → Actions): `RELEASE_KEYSTORE_BASE64` (the keystore file,
base64-encoded), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD`. Android refuses updates signed with a different key, so
after switching you have to uninstall the old app once, which deletes its data.
