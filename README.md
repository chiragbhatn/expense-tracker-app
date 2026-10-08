# Expense Tracker

An offline Android app for your expenses, income, credit cards and merchant
cashback, and for udhaar: money people owe you when you pay for them, and
money you owe them. Everything stays on your phone. Nothing is sent anywhere
unless you share or export it yourself.

Version 2 adds income, credit cards, split expenses, a full udhaar ledger with
settlements and Share Balance, recurring expenses, reports, global search,
receipt scanning, reminders, light/dark themes, an app lock, CSV import and
export, and a full Excel backup you can restore on a new phone.

## Install on your phone

1. On your phone, open
   **[the latest release](https://github.com/chiragbhatn/expense-tracker-app/releases/latest)**
   and tap `expense-tracker.apk`
   ([direct download](https://github.com/chiragbhatn/expense-tracker-app/releases/latest/download/expense-tracker.apk)).
2. Open the downloaded file. The first time, Android asks you to allow your
   browser to install apps: allow it, then tap **Install**. If Google Play
   Protect says the app is from an unknown developer, choose
   **More details → Install anyway**.

Needs Android 8.0 or newer. Every build is signed with the same key, so a
newer APK installs over the old one and keeps your data.

### Updating from version 1

Install version 2 over version 1 the same way. **Your data is kept.** The
database is upgraded in place on first launch: every person, expense, ledger
entry and cashback rule stays, with the same IDs, dates, amounts and notes
(see [Migration from version 1](#migration-from-version-1)).

Version 1 charged a person the **full** amount when you paid for them. Version
2 charges what the expense cost **after cashback** (see
[How cashback works](#how-cashback-works)). Old expenses are not changed
behind your back. **Settings → Data management** lists every version 1
expense that charged the full amount, with the old and new share, for example
"Rahul: ₹1,000 → ₹900". You can apply all the corrections at once (a safety
backup is saved first), or open each expense and fix it yourself.

## What's in the app

The bottom bar has **Home, Expenses, Udhaar, Reports and Settings**. The
**+** button adds an **Expense, Income, Udhaar or Payment** from any tab.

| Area | What you can do |
|---|---|
| Home | This month's balance, income, expenses, cashback and effective expenses; card and other spending; money to receive, money to pay and credit people hold with you; overdue udhaar; card outstanding and the next card bill; upcoming recurring expenses; quick actions (Expense, Income, Udhaar, Payment, Person) and search. Months can be changed. |
| Expenses | Every expense and income entry, by month, with category and payment-method filters. An expense with cashback shows the original amount, the cashback and the effective amount. |
| Expense form | Amount, merchant (with suggestions and rule hints), payment method (Card, UPI, Cash, Other), card, category, date, note, and who it was for: split equally or by custom amounts, with or without your own share. Scan a receipt to fill it in. |
| Income | Amount, source, category (Salary, Business, Freelance, Interest, Gift, Refund, Other or your own), date, note. |
| Udhaar | Everyone with their balance, with totals to receive, to pay and credit held, and who is overdue. Each person has a profile (name, phone, email, address, notes, tags, photo), **Share Balance**, **Record Payment**, **Settle**, a ledger and reminders. |
| Credit cards | Name, bank, last 4 digits, limit, statement day, due day, notes, active. Spending, outstanding, available limit, cashback, spending by merchant and category, spending on behalf of others, monthly spending, bill payments and due-date reminders. |
| Cashback | Cashback today, this month, this year and in total, by merchant (top merchants plus "Other") and by card. |
| Cashback rules | Merchant, percentage, on/off, optional card, created and updated dates. Add, edit, delete, switch on/off. Nothing is hard-coded; Swiggy 10% (on), Zomato 10% (off) and Amazon 5% (off) are only the starting list on a new install. |
| Recurring | Rent, subscriptions and bills: daily, weekly, monthly, yearly or every N days/weeks/months/years, with an optional end date and reminder. Pause, resume, edit, delete. |
| Reports | Monthly summary (income, expenses, cashback, effective expenses, receivable, payable, credit held, net position) for any month, a six-month chart with a table view, and spending by category, merchant and card. |
| Search | One box for everything. "Swiggy" finds its expenses, cashback, people you shared them with, the cards used and totals. "Rahul" finds the profile, balance, ledger, expenses and payments. |
| Settings | Theme (System, Light, Dark), dynamic colour, currency, app lock, credit cards, cashback rules, categories, recurring expenses, reminder settings, Share Balance wording, Excel backup and restore, CSV import and export, data management and about. |

Categories: Food, Groceries, Shopping, Transport, Fuel, Bills, Entertainment,
Health, Travel, Education, Rent, Subscription and Other, plus any you add.
Renaming a category renames it on its expenses; deleting one moves its
expenses to Other.

## How cashback works

Cashback is **subtracted** from what the card was charged:

```
cashback       = original amount × cashback % ÷ 100     (rounded to the paisa)
effective      = original amount − cashback
person's share = their part of the effective amount
```

| Paid at Swiggy by card (10% rule on) | Original | Cashback | Effective | Rahul owes |
|---|---|---|---|---|
| ₹200, all for Rahul | ₹200 | −₹20 | ₹180 | **₹180** |
| ₹500, all for Rahul | ₹500 | −₹50 | ₹450 | **₹450** |
| ₹1,000, split equally between you, Rahul and Amit | ₹1,000 | −₹100 | ₹900 | **₹300** (Amit ₹300, you ₹300) |

The expense form and the expense list show **Original, Cashback and
Effective** for every expense with cashback. Each expense stores the original amount,
cashback percentage, cashback amount, effective amount, merchant, payment
method, card and the share of each person.

When cashback applies:

- the payment method is **Card**;
- the merchant has a rule that is **switched on**. Names match ignoring case
  and spacing, so "swiggy" is Swiggy;
- the rule is for any card, or for the card you chose. A rule for a specific
  card wins over a rule for any card at the same merchant.

The form says why when cashback does not apply ("Swiggy cashback applies to
card payments only.", "… is turned off in Cashback rules.", "… applies to a
different card."). You can also enter a percentage for one expense. An
expense keeps the percentage it was saved with: changing or deleting a rule
later never changes old expenses. When you edit an old expense whose rule has
since changed, the form offers to use the current rule.

## Splitting an expense

Add people to an expense and choose:

- **Equally**: the effective amount is divided between the people, and you too
  if "Include my share" is on. Leftover paise go to the first people, so the
  parts always add up exactly.
- **Custom amounts**: type each share.

The shares must add up to the effective amount. If they do not, the form says
by how much and **Save is blocked** until they do. You can split with one
person, several, or nobody. Each person's share is added to their udhaar as an
"Expense share". Editing or deleting the expense updates or removes those
shares in the same database transaction.

## Udhaar, payments and settlements

Every person has a ledger. Each entry has a type and a direction:

| Type | Direction | Effect |
|---|---|---|
| Expense share | they owe more | Their share of an expense you paid (comes from the expense) |
| Udhaar given | they owe more | Money you lent them |
| Payment made | they owe more | Money you paid them (for example repaying what you borrowed) |
| Udhaar taken | they owe less | Money you borrowed from them |
| Payment received | they owe less | Money they paid you |
| Settlement | either | Settles the balance (they pay you, or you pay them) |
| Adjustment | either | A correction |

`balance = everything they owe − everything they paid`. Balances are worked out
oldest first, and three amounts are kept apart:

- **Receivable**: what they owe you (balance above zero).
- **Payable**: what you owe them because you borrowed from them.
- **Credit**: anything they paid beyond that. Credit is shown as credit, for
  example **"Rahul has ₹500 credit."**, and is used up by their next expense.
  It is never shown as a negative expense.

**Record Payment** opens a payment from them (or to them, if you owe them).
**Settle** offers the full amount or another amount. The page shows what
happens before you confirm:

- full amount: "Full settlement. After this: outstanding ₹0."
- less: a partial settlement, and the rest stays outstanding;
- more: "Extra payment. After this: outstanding ₹0, Rahul has ₹320 credit."

### Share Balance

**Share Balance** on a person's profile writes a message, lets you edit it,
and opens Android's share sheet (WhatsApp, SMS, email and so on). Choose
**Current balance**, **Detailed statement** (every entry with its date and
amount, then the totals) or **Monthly summary**.

| Balance | Headline | Default message |
|---|---|---|
| Rahul owes ₹1,500 | Rahul owes you ₹1,500 | Hi Rahul, your current pending balance is ₹1,500. Please settle it when convenient. Thanks! |
| ₹0 | Account settled ✓ | Hi Rahul, your account is fully settled. Current balance: ₹0. Thanks! |
| Rahul paid ₹500 extra | Rahul has ₹500 extra credit. | Hi Rahul, you've paid ₹500 extra. You currently have a ₹500 credit balance with me, which will be adjusted against your next expense. |
| You owe Rahul ₹500 | You owe Rahul ₹500 | Hi Rahul, I owe you ₹500. I'll settle it soon. Thanks! |

Change the default wording in **Settings → Share message wording**, using
`{name}` and `{amount}`.

## Credit cards

```
outstanding     = charges on the card − cashback − bill payments
available limit = credit limit − outstanding
```

Cashback counts as credited to the card statement. The statement day and
due day give the last statement date, the unpaid statement balance and its
due date. A bill that is due soon or overdue appears on Home, and a reminder
goes off the chosen number of days before. Paying the bill (**Record bill
payment**) lowers the outstanding amount. It is not an expense, because the
spending was already recorded. Spending on behalf of others is the part of
the card's expenses that people owe you.

## Recurring expenses

A recurring expense records itself on its due dates, as an ordinary expense
with the current cashback rule applied. This happens when the app starts and
once a day in the background. Missed dates are caught up, and each date is
recorded only once. Monthly items on the 31st use the last day of shorter
months. Pausing stops new entries, and resuming does not back-fill the paused
period. Reminders can go off a set number of days before each date.

## Reports and the monthly summary

For the chosen month:

```
income
original expenses       everything charged
cashback
effective expenses      original − cashback
money to receive        what people owe you (all people, as of the month's end)
money to pay            what you owe people because you borrowed from them
credit held             what people paid you beyond what they owed
net position            income − effective expenses + money to receive
                        − money to pay − credit held
```

Cashback is counted once, in effective expenses. The six-month chart compares
income and effective expenses, with a table view for exact numbers.

## Reminders and app lock

Reminders are local notifications from a daily background job. They cover card
bills (default 3 days before), recurring expenses (default 1 day before),
people who have owed you money without activity for a while (default 30 days),
and your own reminders on a person ("Ask Amit for ₹500"). Android 13 and newer
ask for permission to show notifications.

App lock asks for your fingerprint, face or screen lock when the app opens,
and again after 5 minutes in the background. It needs Android 9 or newer and a
screen lock on the phone.

## Receipt scanning

On the expense form, **Scan receipt** takes a photo or picks one from the
gallery. Text recognition (Google ML Kit, bundled in the app) runs **on the
phone, offline**. The app picks out the total, the date and a merchant name
and fills in the form for you to check. **Nothing is saved until you tap
Save**, and the photo is kept with the expense.

## Excel full backup

**Settings → Export full Excel backup** writes one `.xlsx` file with
everything. **Save file** puts it where you choose (Downloads, Google Drive and
so on) and **Share** sends it. It opens in Excel, Google Sheets and
LibreOffice.

### Workbook structure (format 2.0)

| Sheet | One row per | Columns |
|---|---|---|
| Metadata | setting | `backup_format_version` (2.0), `app_name`, `app_version`, `app_version_code`, `database_schema_version` (2), `export_date`, `exported_at`, `currency`, row counts per sheet |
| Settings | setting | `key`, `value`: theme, dynamic colour, currency, app lock (recorded, never restored), Share Balance wording, reminder settings |
| People | person | id, name, phone, email, address, notes, tags, photo_file, created_at, updated_at |
| Expenses | expense | id, date, merchant, category, payment_method, card_id, original_amount(_paise), cashback_percentage, cashback_basis_points, cashback_amount(_paise), effective_amount(_paise), note, recurring_id, created_at, updated_at |
| Income | income | id, date, source, category, amount(_paise), note, created_at, updated_at |
| LedgerTransactions | expense share, udhaar given/taken, adjustment | id, person_id, person_name, date, type, direction, amount(_paise), expense_id, note, created_at, updated_at |
| Payments | payment received/made | same columns as LedgerTransactions |
| Settlements | settlement | same columns as LedgerTransactions |
| CreditCards | card | id, name, bank, last_four, credit_limit(_paise), statement_day, due_day, notes, active, reminder_days_before, created_at, updated_at |
| CardPayments | card bill payment | id, card_id, date, amount(_paise), note, created_at, updated_at |
| CashbackRules | rule | id, merchant, cashback_percentage, cashback_basis_points, enabled, card_id, created_at, updated_at |
| RecurringExpenses | recurring expense | id, title, amount(_paise), category, payment_method, card_id, start_date, frequency, interval_count, interval_unit, next_date, occurrence_index, end_date, active, reminder_days_before, note, created_at, updated_at |
| Categories | category | id, name, kind (EXPENSE/INCOME), is_default, created_at, updated_at |
| Reminders | reminder | id, title, note, due_date, person_id, done, created_at, updated_at |
| Attachments | receipt | id, expense_id, person_id, file_name, mime_type, size_bytes, created_at, updated_at |

- `id` is a permanent ID (a UUID) given to every record. Other sheets refer to
  records by it (`person_id`, `card_id`, `expense_id`…), so a restore keeps
  every link.
- Amounts appear twice: in rupees for reading (`original_amount`) and exactly
  in paise (`original_amount_paise`, 1 rupee = 100 paise). A restore uses the
  paise column when present.
- Dates are `YYYY-MM-DD`, and `created_at`/`updated_at` are ISO-8601 UTC
  timestamps. Percentages are also kept in basis points (10% = 1000).

### Restoring a backup

**Settings → Import full Excel backup**, then choose the file. Nothing changes
until you confirm.

1. **Check**: the file is validated in order: file type, workbook structure,
   format version, required sheets and columns, IDs, dates, amounts,
   relationships between records, and duplicates. Errors stop the restore and
   say what is wrong and where, for example
   `Expenses row 12: date "31/02/2026" is not a date (use YYYY-MM-DD).`
   Warnings (for example a link to a missing card, which is dropped) are
   listed but do not stop it.
2. **Preview**: what the backup contains, table by table.
3. If the phone already has data, the app shows **"Existing data detected."**
   and offers:
   - **Merge backup with existing data** (the default): adds new records and
     updates changed ones, and never deletes anything. Records are matched by
     their permanent ID, and also by natural key: people by name, categories
     by name, cards by bank and last 4 digits, rules by merchant and card. When
     both sides changed a record, the newer one (`updated_at`) wins. The same
     backup merged twice adds nothing the second time.
   - **Replace existing data**: the phone ends up holding exactly the backup.
     Asks you to confirm.
   - **Cancel**.
4. **Restore**: before replacing or merging, the app saves a **safety backup**
   of the current data (the five most recent are kept in
   **Settings → Data management**, where they can be shared). The restore then
   runs in a single database transaction, so it either completes or changes
   nothing.

Backups from a newer format (3.x) are refused with "This backup uses format
3.0, made by a newer version of Expense Tracker. Update the app to restore
it; nothing has been changed." A newer 2.x backup restores, and anything this
version does not know about is skipped with a warning. The app lock setting
is never restored, because it belongs to the phone.

## CSV import and export

**Settings → Export CSV / Import CSV** handles five kinds of file. All are
UTF-8 with a header row. Dates are `YYYY-MM-DD`, and `DD/MM/YYYY` is also
read. Amounts are in rupees, such as `1234.50`.

| File | Columns (bold = required to import) |
|---|---|
| Expenses | id, **date**, **merchant**, category, payment_method, card_name, card_last_four, **original_amount**, cashback_percentage, cashback_amount, effective_amount, my_share, shared_with, note |
| Income | id, **date**, source, category, **amount**, note |
| People | id, **name**, phone, email, address, notes, tags, balance, owes_you, you_owe, credit (the last four are exported for reading and ignored on import) |
| Udhaar transactions | id, **date**, **person**, **type**, direction, **amount**, expense_id, merchant, note |
| Payments | id, **date**, **person**, **type**, direction, **amount**, note |

`shared_with` lists the people on an expense with their shares:
`Rahul=180; Amit=300`. Missing cashback and effective amounts are calculated,
and amounts that are given must agree with each other. New people and
categories are created as needed; a card that is not in the app is left off
the expense, with a warning. Text that a spreadsheet would run
as a formula (starting with `=`, `+`, `-` or `@`) is exported with a leading
apostrophe.

Importing runs **pick file → validate → preview**. The preview shows valid
rows, invalid rows with their errors, and possible duplicates (same ID, or for
example the same date, merchant and amount). You choose what to do with the
duplicates: **Skip**, **Update** the existing records, or **Import as new**.
Then you **confirm**. A safety backup is saved before anything is imported.

## Architecture

Kotlin, Jetpack Compose (Material 3), Room, DataStore, WorkManager. One
module, in these packages:

```
app/src/main/java/io/github/chiragbhatn/expensetracker/
  domain/     Plain Kotlin rules with no Android code: Money (paise), Percentage
              (basis points), cashback, splits, ledger and settlements, cards,
              recurring schedules, reports, search, Share Balance messages,
              receipt parsing, reminders
  data/       Room database (entities, DAOs, migration), repositories, settings
              (DataStore), AppData: one snapshot of every table that all
              screens read
  backup/     XLSX writer and reader, backup format, merge, CSV import/export
  reminders/  Daily background job and notifications
  ui/         Compose screens, navigation, theme and components
```

Amounts are whole paise (`Long`) and percentages are basis points (`Int`), so
there is no floating-point drift. The UI observes a single `AppData` flow that
combines every table. Screens work out what they show with the domain
functions, so the dashboard, reports and person pages always agree.

## Database schema (version 2)

| Table | Columns |
|---|---|
| `expenses` | id, original_amount_paise, cashback_percentage_bps, cashback_amount_paise, effective_amount_paise, merchant, payment_method, date_epoch_day, note, category, card_id, recurring_id, uuid, created_at, updated_at |
| `udhaar_entries` | id, person_id → people (cascade), direction (GAVE/GOT), amount_paise, date_epoch_day, note, expense_id → expenses, type, uuid, created_at, updated_at; unique (expense_id, person_id) |
| `people` | id, name, name_key (unique), phone, email, address, notes, tags, photo_path, uuid, created_at, updated_at |
| `cashback_rules` | id, merchant, merchant_key, cashback_percentage_bps, enabled, card_id, uuid, created_at, updated_at |
| `incomes` | id, amount_paise, source, category, date_epoch_day, note, uuid, created_at, updated_at |
| `credit_cards` | id, name, bank, last_four, credit_limit_paise, statement_day, due_day, notes, active, reminder_days_before, uuid, created_at, updated_at |
| `card_payments` | id, card_id → credit_cards (cascade), amount_paise, date_epoch_day, note, uuid, created_at, updated_at |
| `recurring_expenses` | id, title, amount_paise, category, payment_method, card_id → credit_cards (set null), start_epoch_day, frequency, interval_count, interval_unit, next_epoch_day, occurrence_index, end_epoch_day, active, reminder_days_before, note, uuid, created_at, updated_at |
| `categories` | id, name, name_key, kind, is_default, uuid, created_at, updated_at; unique (name_key, kind) |
| `reminders` | id, title, note, due_epoch_day, person_id → people (cascade), done, uuid, created_at, updated_at |
| `attachments` | id, expense_id → expenses (cascade), person_id → people (cascade), file_name, mime_type, size_bytes, uuid, created_at, updated_at |

Every table has a unique `uuid`, which is the permanent ID used by backups. The
exported Room schemas are in [`app/schemas`](app/schemas).

## Migration from version 1

`AppDatabase.MIGRATION_1_2` upgrades the version 1 database in place. It never
drops or recreates a version 1 table, so no row can be lost to a foreign-key
cascade:

1. New columns are added with `ALTER TABLE … ADD COLUMN` and defaults:
   category `Other` on expenses; contact details on people; type, card and
   recurring links; `uuid`, `created_at` and `updated_at` everywhere.
2. Every existing row gets a random UUID. Expenses and ledger entries get
   timestamps from their own date, so merges treat them as old records.
3. Ledger entries get a type. An entry linked to an expense becomes an
   **Expense share**, other "gave" entries become **Udhaar given**, and "got"
   entries become **Payment received**. A "got" entry recorded when the person
   owed you nothing is money you borrowed, so it becomes **Udhaar taken**.
4. The cashback rule index allows card-specific rules, and the ledger gets a
   unique (expense, person) index for split shares.
5. The new tables are created and the default categories are added.

IDs, amounts, dates and notes are not touched. Version 1 shares that charged
the full amount are not changed automatically (see
[Updating from version 1](#updating-from-version-1)).

This is checked in two ways. `MigrationTest` builds a version 1 database from
the exported version 1 schema, migrates it with Room's `MigrationTestHelper`,
validates it against the version 2 schema and checks every row, type and
balance. On CI, the emulator job installs the real version 1 APK, records data
through its UI, installs version 2 over it, and checks that the data is still
there.

## Building and testing

GitHub Actions ([`.github/workflows/android.yml`](.github/workflows/android.yml))
runs on every push:

1. **Unit tests**, all on the JVM with Robolectric for the Android parts:
   - money, cashback (₹200 → ₹180, ₹500 → ₹450, rounding), splits, ledger
     (partial, full and extra payments, credit, payable), cards, recurring
     schedules, reports, search, Share Balance messages, receipt parsing and
     reminders;
   - the 1 → 2 database migration;
   - repositories: saving and editing split expenses, settlements, rules per
     card, deleting people and cards, recurring catch-up and categories;
   - CSV export and import (validation, duplicates, Skip/Update/Import as new);
   - Excel backup: export, every validation step, restore on a new phone,
     restoring twice, merge, a backup from a newer version, and a backup
     opened and saved again by another spreadsheet library (openpyxl);
   - Compose UI flows: the ₹200 Swiggy expense for Rahul, a three-way split,
     custom shares that must add up, a full and an extra settlement, Share
     Balance, switching a rule on, search, dark mode and colour contrast.
2. Builds the signed release APK.
3. On an Android emulator: installs the version 1 APK, records a Swiggy expense
   for Rahul and a repayment, then installs this APK over it. It checks Rahul's
   balance and the rules survived, applies the version 1 correction, and
   walks through the new features: the ₹200 → ₹180 expense, a three-way split,
   payments, an extra payment that becomes credit, Share Balance, reports,
   dark mode, search and the Excel backup. Screenshots are kept as the
   `device-screenshots` artifact.
4. On the default branch, once everything passes, replaces the `latest` release
   with the new APK.

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
Export an Excel backup first and restore it afterwards.

## Known limitations

- Receipt photos and profile photos stay on the phone. The Excel backup lists
  them (Attachments sheet, `photo_file`) but does not contain the images, so
  they are not restored on a new phone.
- One currency at a time. Changing the currency changes the symbol only and
  never converts amounts.
- Receipt scanning reads printed receipts best. Always check the amount,
  date and merchant before saving.
- Reminders run from a daily background job, so they can arrive some hours
  after midnight, and Android may delay them further in battery saver.
- App lock needs Android 9 or newer.
- Full backups are `.xlsx` files. The old `.xls` format is not read.
