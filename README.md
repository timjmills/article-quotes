# Article Quotes

Quotes from Tim's article archive (Leadership, Family, Education) on a Samsung Galaxy A52 lock screen, with one-tap summaries and links to the full articles. New articles flow in automatically every day.

```
Cowork task (09:00)  ──►  PDF archive  ──►  builder (10:00, PC)  ──►  GitHub Pages feed  ──►  phone app
                                              └──►  Google Drive: one document per article
```

## Parts

| Part | Where | What it does |
|---|---|---|
| Archive | `C:\Claude\Claude Cowork\Projects\Article Archive - Education & Leadership` | One PDF per article, written by the Cowork scheduled task `daily-article-archive`. |
| Builder | `builder/build_feed.py` | Parses new PDFs, writes the feed to `docs/feed/`, and writes a Word document per article to `My Drive\Claude\Article Summaries\<Category>\`. Incremental. |
| Daily run | `builder/run_daily.cmd` | Builder + `git push`. Registered in Windows Task Scheduler as **ArticleQuotes feed refresh** (10:00, then every 4 h until 22:00, so a late PC start still catches up). |
| Feed | `https://timjmills.github.io/article-quotes/feed/` | `manifest.json` (shard list with hashes), `quotes/<YYYY-MM>.json` (quote pool), `articles/<id>.json` (summary, points, quotes, URL). |
| App | `android/` | Kotlin + Jetpack Compose. Notification + lock-screen wallpaper + home-screen widget; browse, search, save, share; settings for timing, quiet hours, article types, text size. |

## Phone install (Galaxy A52)

1. On the phone, open the latest APK: <https://github.com/timjmills/article-quotes/releases/latest> and download `ArticleQuotes.apk`.
2. Tap the download. If Samsung asks, allow **Chrome** (or **My Files**) to install unknown apps, then **Install**.
3. Open **Article Quotes**. The welcome card walks through three taps: allow notifications, keep the lock-screen switch on, and *Get my first quote* (downloads the feed on Wi-Fi, about 2 MB).
4. Optional: long-press the home screen → **Widgets** → **Article Quotes** to add the quote widget.

Samsung note: the app sets the lock-screen wallpaper directly. If you use Samsung's *Wallpaper services* / *Dynamic Lock screen*, turn that off (Settings → Wallpaper and style → Lock screen → Wallpaper services → None) or it will overwrite the quote card.

## Defaults the app ships with

- A new quote every 3 hours, quiet from 22:00 to 07:00.
- Notification on (shows on the lock screen; tap it to read the summary), lock-screen wallpaper on, home wallpaper off.
- Card style rotates between four palettes; quote text sizes itself to fit (longer quotes shrink, and quotes over 320 characters stay in the app rather than on the lock screen).
- All six article types on. Technology and AI How-To are never in the phone feed.
- Feed refresh once a day on Wi-Fi only.

Everything above is changeable in **Settings** inside the app.

## What's in 1.6

- **Bible study for Wisdom quotes:** a *Bible study* button on every Wisdom card (Today, full screen, lock-screen reader, Saved). Pressing it has Claude Haiku write a short study on the quote's theme: the quote in its setting, what Scripture says (key passages printed in ESV), Hebrew/Greek words, where Scripture agrees and where it goes further, biblical and systematic theology, application, and questions to think about. Saved per quote; *Refresh* rewrites it.

## What's in 1.5

- **Wisdom section:** 473 curated quotes (Stoics, Tracy & Maxwell, classic Christian writers, statesmen), each with a "why it matters" line. Built from `builder/wisdom/*.json`; only quotes with a `verifiedBy` source ship. Archaic translations are modernised, with the original kept in `original`.
- **Proverbs section:** all 915 verses in 683 readings of 1–3 verses (`docs/feed/proverbs_plan.json`, references only). ESV text is fetched on the phone with Tim's own ESV key and cached (at most 400 verses, plus at most 30 readings in history, to stay under ESV's 500-verse limit). Cache and keys are excluded from backup.
- **Devotionals:** tap *Devotional* on a Proverbs card. Claude Haiku 5.5 writes it (Reformed/evangelical lens; ANE context, Hebrew words, theology, application for work, leadership, father, husband, Christian life; short cross-references printed in ESV). Saved per reading; only *Refresh* pays again. Each card also gets a one-line "why it matters" (about $0.0002 each).
- **Sections in Settings:** Wisdom and Proverbs each Off / Rarely / Sometimes / Often (0/10/25/40% of changes), Wisdom groups on/off, Proverbs in order or random.
- **Keys (Settings → Claude API and Bible text):** Claude API key (console.anthropic.com), ESV key (api.esv.org), optional Admin key for whole-account spend. Stored with Android Keystore encryption.
- **Budget bar:** "$X left of $200" on the Today screen, the devotional screen and Settings. Counts this app's spend; with an Admin key it uses the account's month-to-date cost report. Allowance and reset day are editable.
- **"Why it matters" size slider** in Settings.

## What's in 1.3

- **Grace window:** a scheduled quote never changes while the screen is on, or within 30 seconds (adjustable) of it turning off. Survives Android killing the app in the background.
- **Learns your taste:** more/less like this, mute an author, Less/Normal/More per article type, and time-of-day themes (work topics mornings, family evenings).
- **In-app updates:** checks the GitHub release daily; downloads and installs a newer build in place. The release title must contain `(build N)` matching `versionCode`, which the CI workflow sets.
- **Reading loop:** read later, read marks, weekly Sunday review of saved quotes, search inside quote text, export saved quotes (share to Drive).
- **Share as image:** a 4:5 card picture of any quote.
- **Staleness warnings:** banner and notification when the phone hasn't refreshed for 3 days or no new article has arrived for 5.
- **Lock-screen position:** Middle (default) or Lower, for phones whose clock covers the middle.
- **Tablets:** navigation rail and a centred reading column at 600dp and wider.

## "Why it matters" lines

The builder picks, for each quote, the high-impact point or summary sentence that best matches it, as a complete sentence. If the archive folder contains `QUOTE_CONTEXT.jsonl` (one `{"file": "<pdf name>", "context": [...]}` per line, written by the archive task per its PROJECT_INSTRUCTIONS.md), those Claude-written lines are used instead.

## Updating the app

Push a change under `android/` and the GitHub Actions workflow in `.github/workflows/android.yml` builds a new APK and attaches it to the `latest` release. Building locally instead:

```powershell
$env:JAVA_HOME="C:\Users\losey\.android-build-tools\jdk\jdk-17.0.20.1+1"
$env:ANDROID_HOME="C:\Users\losey\.android-build-tools\sdk"
& "C:\Users\losey\.android-build-tools\gradle\gradle-8.10.2\bin\gradle.bat" -p android assembleRelease
gh release upload latest android\app\build\outputs\apk\release\app-release.apk --clobber
```

## Running the builder by hand

```powershell
python builder\build_feed.py            # incremental
python builder\build_feed.py --reparse  # rebuild everything
python builder\build_feed.py --no-drive # skip Word documents
```

`builder/config.json` (optional, git-ignored) overrides any key in `DEFAULT_CONFIG` at the top of the script, for example the archive path or the feed categories.
