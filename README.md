# Foundora - Lost and Found Management System

## Project Overview
Foundora is the lost and found board for the De La Salle Lipa community (Group 6, DSA class project). Students and staff report lost or found items, admins review every report before it appears, and approved reports show up in a social-style feed. Items are still turned in and claimed at the SDFO office.

## Tech Stack
- **Backend**: Java 17 with Spring Boot 4
- **Frontend**: Thymeleaf templates, one hand-written stylesheet (`static/css/app.css`) and a small script (`static/js/app.js`). Every page works without JavaScript.
- **Database**: Supabase Postgres via Spring Data JPA (Session pooler), schema managed by Flyway
- **Authentication**: Firebase Authentication (Google sign-in), verified on the server
- **Storage**: private Supabase Storage bucket `item-images`; the app serves each photo after checking who is asking

## Roles
Only `@dlsl.edu.ph` Google accounts can sign in; the server checks this after verifying the Firebase ID token.

| Email | Role | Can do |
| --- | --- | --- |
| On `FOUNDORA_ADMIN_EMAILS` (any @dlsl.edu.ph address, including office accounts) | Admin | Everything below, plus moderation; lands on `/admin` |
| `first_last@dlsl.edu.ph` (underscore) | Student | Browse the feed, report lost/found items, My Reports |
| `first.last@dlsl.edu.ph` (dot) or an office account such as `sdfo@dlsl.edu.ph` | Staff | Same as students |
| Both a dot and an underscore, or any other domain | Refused | |

Students and staff never receive admin pages or controls; `/admin/**` requires the ADMIN role on the server. A role is decided at sign-in, so allowlist changes apply at the person's next sign-in.

## Features
- **Feed** (`/`): approved reports as posts, newest first, 10 at a time with "Load more" (and loading as you scroll). Search (item name, description, location, with prefix matching), All / Lost / Found tabs, category filter, date range, newest/oldest order and "Hide claimed items". Claimed posts stay visible, greyed out. Public pages never show reporter emails.
- **Reporting**: a composer at the top of the feed opens the report form (a dialog on desktop, a full page on phones). Validation messages per field, photo preview, location suggestions, and a confirmation page. Found reports remind the finder to bring the item to the SDFO office.
- **Post page** (`/posts/{id}`): one report with a shareable link. Someone else's Pending or Rejected report is a 404.
- **My Reports** (`/my-reports`): your reports in every status, with the admin's reason for rejections.
- **Admin**: overview counts, a pending queue (oldest first, each previewed exactly as its feed post), All Reports with search and filters, Mark as Claimed / Remove, and an activity log of every action. Every action has a confirmation step.
- **Photos**: re-encoded on the server as JPEG of at most 1600 px (which also strips EXIF data such as GPS location), up to 5 MB, JPEG/PNG/WebP. Deleted from storage when a report is rejected or removed.

## Report statuses
`PENDING` → `APPROVED` or `REJECTED` (reason required); `APPROVED` → `CLAIMED` or `REMOVED`; `CLAIMED` → `REMOVED`. Only Approved and Claimed reports appear on the feed. Remove hides a report rather than deleting it, so the activity log stays complete.

## Data structures and algorithms
The classes in `com.foundora.dsa` do real work in the app; each one's comments explain its structure and running time.

| Class | Structure / algorithm | Used for | Complexity |
| --- | --- | --- | --- |
| `CategoryIndex` | `EnumMap` from category to posts | Category filter | O(1) lookup, O(k) to read k posts |
| `DateMergeSort` | Merge sort (hand-written, stable) | Ordering posts when the feed index is rebuilt | O(n log n) time, O(n) space |
| `SortedInsert` | Binary search + insert | Adding one approved post | O(log n) search + O(n) shift |
| `InvertedIndex` | `HashMap` word → post IDs, smallest-first intersection | Search bar | O(k · m) for k words, smallest match set m |
| `PrefixTrie` | Trie | Search-as-you-type and location suggestions | O(L + r) for an L-letter prefix, r results |
| `PendingQueue` | `ArrayDeque` FIFO queue | Admin pending queue | O(1) enqueue / next |

`feed/FeedIndex` combines them into an in-memory read model of the feed. It's built at startup, updated right after each admin action commits, and rebuilt every 5 minutes (`foundora.feed.rebuild-interval`). The database remains the source of truth; admin pages query it directly.

## Getting Started
### Prerequisites
- Java 17+
- Maven
- Firebase project with Authentication → Google sign-in enabled

### Installation
1. Clone the repository:
   ```
   git clone <repository-url>
   cd Foundora
   ```

2. Download a service account key (Firebase Console → Project settings → Service accounts → Generate new private key) and save it as `src/main/resources/firebase-service-account.json`, or set `FIREBASE_CREDENTIALS_PATH` to the key's file path (e.g. `/path/to/key.json`). Never commit this file; it is git-ignored.

   Admin emails come from the `FOUNDORA_ADMIN_EMAILS` environment variable (comma-separated). If it is unset, the default in `application.properties` is used.

3. Copy `.env.example` to `.env` and fill in your Supabase values. `.env` is git-ignored and Docker-ignored; the app reads it at startup, and real environment variables take precedence over it.

   | Variable | Where to find it |
   | --- | --- |
   | `SUPABASE_DB_URL` | Dashboard → Connect → **Session pooler**, as a JDBC URL without credentials (`jdbc:postgresql://<region>.pooler.supabase.com:5432/postgres`) |
   | `SUPABASE_DB_USER` | Session pooler user, `postgres.<project-ref>` |
   | `SUPABASE_DB_PASSWORD` | Your database password |
   | `SUPABASE_URL` | `https://<project-ref>.supabase.co` |
   | `SUPABASE_SECRET_KEY` | Project Settings → API Keys → secret key (`sb_secret_...`). Server-only; never put it in a template or browser code. |

   **Use a separate Supabase project for development** (the team uses `foundora-dev`), never the live one: running a branch locally applies its migrations to whatever database `.env` points at.

   On start, Flyway runs `src/main/resources/db/migration`:

   | Migration | What it does |
   | --- | --- |
   | V1 | `items` table with Row Level Security on and no policies, so Supabase's public Data API can't read or write it |
   | V2 | `item-images` bucket |
   | V3 | Report model: type, fixed categories, statuses, review fields, photo path |
   | V4 | `activity_log` table (RLS on, no policies) |
   | V5 | Makes the `item-images` bucket private (JPEG only, 5 MB) |

   Flyway's own history table lives in a separate `flyway` schema, outside the API-exposed `public` schema. Never edit a migration that has already run; add a new one.

   Optional settings in `application.properties`: `foundora.sdfo.room` and `foundora.sdfo.hours` (SDFO office details; shown as marked placeholders while blank) and `foundora.campus-locations` (location suggestions; currently a placeholder list to replace with real DLSL places).

4. Build the project (tests use an in-memory H2 database, not Supabase):
   ```
   mvn clean install
   ```

5. Run the application:
   ```
   mvn spring-boot:run
   ```

6. Access the application at `http://localhost:8080`.

## Deploying to Render (Docker)
The repository includes a `Dockerfile`, so Render can build and run it directly.

1. In Render, create a **Web Service** from this GitHub repository. Render detects the Dockerfile and uses the Docker runtime.
2. Under **Environment → Secret Files**, add a file named `firebase-service-account.json` with the contents of your service account key. Render mounts it at `/etc/secrets/firebase-service-account.json`.
3. Under **Environment → Environment Variables**, set:

   | Variable | Value |
   | --- | --- |
   | `FIREBASE_CREDENTIALS_PATH` | `/etc/secrets/firebase-service-account.json` |
   | `FOUNDORA_ADMIN_EMAILS` | Comma-separated @dlsl.edu.ph admin addresses (office accounts allowed) |
   | `SUPABASE_DB_URL` | Session pooler JDBC URL (see Installation step 3) |
   | `SUPABASE_DB_USER` | `postgres.<project-ref>` |
   | `SUPABASE_DB_PASSWORD` | Database password |
   | `SUPABASE_URL` | `https://<project-ref>.supabase.co` |
   | `SUPABASE_SECRET_KEY` | Supabase secret key (`sb_secret_...`) |

   `PORT` is provided by Render automatically. The image sets `SPRING_PROFILES_ACTIVE=prod`, which marks the session cookie `Secure` and trusts Render's HTTPS proxy headers.
4. In the Firebase Console → Authentication → Settings → **Authorized domains**, add your Render domain (e.g. `foundora.onrender.com`) so Google sign-in works there.

To try the image locally:
```
docker build -t foundora .
docker run -p 8080:8080 -e PORT=8080 --env-file .env \
  -e FIREBASE_CREDENTIALS_PATH=/secrets/key.json \
  -v /path/to/key.json:/secrets/key.json:ro \
  foundora
```
The `prod` profile requires HTTPS for the session cookie, so sign-in over plain `http://localhost` will not keep you logged in; add `-e SPRING_PROFILES_ACTIVE=default` for local HTTP testing.

## Directory Structure
```
src/main/java/com/foundora
├── auth/         Firebase sign-in, roles (EmailRoleResolver, UserRole, FoundoraUser)
├── config/       Security, Firebase, Supabase, clock/time zone, template helpers
├── controller/   Feed, posts, reports, My Reports, photos, suggestions, admin
├── dsa/          The data structures and algorithms listed above
├── feed/         FeedIndex (in-memory feed read model) and its records
├── model/        Item, ActivityLogEntry and enums (ReportType, ReportStatus, Category, ActivityAction)
├── repository/   Spring Data repositories
└── service/      Reporting, moderation, admin search, image processing, storage
src/main/resources
├── db/migration/ Flyway migrations V1–V5
├── static/       app.css, app.js, favicon.svg
└── templates/    fragments/ (layout, post card, report form, icons), feed, post, report,
                  my-reports, admin/, error/ (403, 404, 500), login
src/test/java     MockMvc tests on H2 (Firebase and Storage mocked) and unit tests for dsa/ and services
```

## Contributing
Contributions are welcome! Please open an issue or submit a pull request for any enhancements or bug fixes.

## License
This project is licensed under the MIT License. See the LICENSE file for details.