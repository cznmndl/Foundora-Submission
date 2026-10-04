# Foundora - Lost and Found Management System

## Project Overview
Foundora is a web-based Lost and Found Management System designed specifically for school communities. The application allows students to report lost or found items, view items in a marketplace-style layout, and provides an admin panel for moderation.

## Tech Stack
- **Backend**: Java 17+ with Spring Boot 3.x
- **Frontend**: Thymeleaf + Tailwind CSS
- **Database**: Supabase (not yet connected; items are kept in memory for now)
- **Authentication**: Firebase Authentication (Google sign-in), verified on the server
- **Storage**: Supabase Storage for images (not yet connected)

## Sign-in Rules
- Only `@dlsl.edu.ph` Google accounts can sign in.
- `first_last@dlsl.edu.ph` (underscore) is a **student** and lands on `/`.
- `first.last@dlsl.edu.ph` (dot) is an **admin** and lands on `/admin`, but only if listed in `foundora.auth.admin-emails`. Dot emails not on the list are refused.
- The browser sends the Firebase ID token to `POST /auth/session`; the server verifies it with the Firebase Admin SDK and starts a session. Students get a 403 on `/admin`.

## Features
1. **Student Reporting Module**: 
   - Students can submit reports for lost or found items with details including title, description, category, location, date, status, contact info, and image upload.
   
2. **Marketplace Dashboard**: 
   - A user-friendly interface displaying items in a grid layout with filtering options by status and category. Each item can be viewed in detail through a modal.

3. **Admin Moderation Module**: 
   - Admins can view pending submissions and take actions such as approve, reject, or delete items.

4. **Dynamic Filtering and Sorting**: 
   - The application utilizes Java Streams and Collections for efficient data handling and presentation.

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

3. Build the project:
   ```
   mvn clean install
   ```

4. Run the application:
   ```
   mvn spring-boot:run
   ```

5. Access the application at `http://localhost:8080`.

## Deploying to Render (Docker)
The repository includes a `Dockerfile`, so Render can build and run it directly.

1. In Render, create a **Web Service** from this GitHub repository. Render detects the Dockerfile and uses the Docker runtime.
2. Under **Environment → Secret Files**, add a file named `firebase-service-account.json` with the contents of your service account key. Render mounts it at `/etc/secrets/firebase-service-account.json`.
3. Under **Environment → Environment Variables**, set:

   | Variable | Value |
   | --- | --- |
   | `FIREBASE_CREDENTIALS_PATH` | `/etc/secrets/firebase-service-account.json` |
   | `FOUNDORA_ADMIN_EMAILS` | Comma-separated `first.last@dlsl.edu.ph` admin addresses |

   `PORT` is provided by Render automatically. The image sets `SPRING_PROFILES_ACTIVE=prod`, which marks the session cookie `Secure` and trusts Render's HTTPS proxy headers.
4. In the Firebase Console → Authentication → Settings → **Authorized domains**, add your Render domain (e.g. `foundora.onrender.com`) so Google sign-in works there.

To try the image locally:
```
docker build -t foundora .
docker run -p 8080:8080 -e PORT=8080 \
  -e FIREBASE_CREDENTIALS_PATH=/secrets/key.json \
  -v /path/to/key.json:/secrets/key.json:ro \
  foundora
```
The `prod` profile requires HTTPS for the session cookie, so sign-in over plain `http://localhost` will not keep you logged in; add `-e SPRING_PROFILES_ACTIVE=default` for local HTTP testing.

## Directory Structure
```
Foundora
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── foundora
│   │   │           ├── FoundoraApplication.java
│   │   │           ├── config
│   │   │           │   └── FirebaseConfig.java
│   │   │           ├── controller
│   │   │           │   ├── AdminController.java
│   │   │           │   ├── MarketplaceController.java
│   │   │           │   └── ReportController.java
│   │   │           ├── model
│   │   │           │   └── Item.java
│   │   │           ├── service
│   │   │           │   ├── FirebaseService.java
│   │   │           │   ├── ItemFilterService.java
│   │   │           │   └── StorageService.java
│   │   │           └── startup
│   │   │               └── DataInitializer.java
│   │   └── resources
│   │       ├── templates
│   │       │   ├── admin.html
│   │       │   ├── index.html
│   │       │   └── report.html
│   │       └── application.properties
│   └── test
│       └── java
│           └── com
│               └── foundora
│                   └── FoundoraApplicationTests.java
├── pom.xml
└── README.md
```

## Contributing
Contributions are welcome! Please open an issue or submit a pull request for any enhancements or bug fixes.

## License
This project is licensed under the MIT License. See the LICENSE file for details.