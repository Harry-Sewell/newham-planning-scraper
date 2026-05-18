# Planning & Companies House Scraper

An Android app that monitors a local council's planning portal and Companies House for changes, sending email and/or WhatsApp alerts to a configurable list of recipients.

## What it does

**Planning portal monitoring**
- Checks hourly for new planning applications matching a list of addresses you configure
- For applications already known, checks for new documents uploaded or status changes
- Sends a summary notification to recipients when anything new is found

**Companies House monitoring**
- Checks hourly for new entries matching a list of person names you configure
- For each known person, checks their current officer appointments for changes
- Sends a summary when new persons or appointments are discovered

## Screenshots / screens

| Screen | Purpose |
|--------|---------|
| Dashboard | Live feed of all recent changes across both data sources |
| Planning | Chronological timeline of all planning applications found |
| People & Companies | All tracked individuals, their appointments and company details |
| Config | Manage addresses, person names, recipients, and API credentials |

## Setup

### 1. Build
Open the project in **Android Studio** (Hedgehog or newer). Gradle will download all dependencies automatically. Then run **Build → Build APK** or:

```
gradle wrapper
./gradlew assembleDebug
```

Minimum Android version: **API 26 (Android 8.0)**.

### 2. Configure the app

On first launch, go to the **Config** tab and fill in:

#### Addresses to watch
Add one or more addresses exactly as you would type them into the council's planning portal search box, e.g.:

> `The Old Pub 1 Example Street Anytown AB1 2CD`

#### People to watch
Add full names as they appear on Companies House, e.g.:

> `SMITH, John Robert`

#### Notification recipients
Add any combination of email addresses and WhatsApp numbers (in international format, e.g. `+447000000000`).

#### Email (SMTP)
| Field | Example |
|-------|---------|
| SMTP Host | `smtp.gmail.com` |
| SMTP Port | `587` |
| Username | `youraddress@gmail.com` |
| Password | Your [Gmail App Password](https://support.google.com/accounts/answer/185833) — **not** your regular password |
| Sender name | `Planning Alerts` |

#### WhatsApp (Twilio) — optional
Sign up at [twilio.com](https://www.twilio.com/) and enable the WhatsApp sandbox or a WhatsApp-enabled number. Then enter your Account SID, Auth Token, and the Twilio From number.

#### Companies House API key
Register for a free key at [developer.company-information.service.gov.uk](https://developer.company-information.service.gov.uk/). Without this, Companies House checks are skipped.

### 3. Run a manual check
Tap the **↻** icon on the Dashboard to trigger an immediate check. Background checks run automatically every hour when the device has a network connection.

## Architecture

```
app/
├── data/
│   ├── db/               # Room database — 8 tables
│   │   ├── entity/       # PlanningApplication, Document, MonitoredAddress,
│   │   │                 # MonitoredPerson, Person, Appointment, Recipient, ChangeLog
│   │   └── dao/          # DAOs for all entities
│   ├── network/
│   │   ├── NewhamPlanningService.kt    # Jsoup HTML scraper for Idox planning portals
│   │   └── CompaniesHouseService.kt   # Companies House REST API client
│   └── repository/       # Diff logic: compares fresh data against DB, writes changelog
├── worker/               # CoroutineWorkers scheduled hourly via WorkManager
├── notification/         # JavaMail SMTP + Twilio WhatsApp API
├── ui/
│   ├── screens/          # 4 Compose screens
│   ├── viewmodel/        # AndroidViewModel per screen, StateFlow from Room Flows
│   ├── navigation/       # Bottom nav + NavHost
│   └── theme/            # Material3 colour scheme
├── domain/Models.kt      # Plain Kotlin data classes + constants
└── AppContainer.kt       # Manual dependency wiring (no Hilt)
```

**Key libraries:** Jetpack Compose · Room · WorkManager · OkHttp · Jsoup · JavaMail · Gson

## Notes on the planning portal scraper

The scraper targets **Idox**-based planning portals (used by many UK councils). It:
1. GETs the search page to acquire a session cookie and optional CSRF token
2. POSTs the address as a keyword search
3. Parses `<li class="searchresult">` elements — uses a regex (`\d{2}/\d{4,6}/[A-Z]{2,5}`) to extract the planning reference reliably regardless of how the portal formats the link text
4. For known applications, fetches the documents tab and finds all `<a href="…/files/…">` links (Newham's Idox instance links document names directly to `/online-applications/files/` paths)
5. Diffs each document list against the database and records new documents in the change log

If the council updates their portal layout, update `NewhamPlanningService.kt`. The `/files/` link selector and reference regex are the two most likely things to need adjustment.

## Troubleshooting

| Symptom | Likely cause |
|---------|-------------|
| No planning results | Council portal layout differs from Idox standard — inspect the HTML and update selectors |
| Planning ref shows as long description text | Old data in DB before the regex-based parser fix — clear applications in Manage Data and re-run |
| Only 1 document shown per application | Documents table selector mismatch — confirmed fix targets `/files/` links directly |
| Companies House returns nothing | API key missing or invalid in Config |
| Email not sending | Check SMTP credentials; Gmail requires an App Password, not your account password |
| WhatsApp not sending | Twilio sandbox requires the recipient to opt in first via WhatsApp |
| Checks not running in background | Some Android manufacturers (Xiaomi, Huawei) restrict background processes — allow the app in battery settings |
