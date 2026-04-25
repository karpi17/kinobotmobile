Cinema Assistant 🎬📊
A comprehensive, native Android application designed specifically for cinema employees to automate shift scheduling, track working hours, and analyze financial goals.

🚀 Key Features
Automated Schedule Parsing: Integrates with the Gmail API (OAuth 2.0) to asynchronously download and parse .xlsx schedule attachments in the background using Apache POI.
Smart Data Merging: Intelligently merges new shifts from Excel with the user's manual modifications (e.g., shift covers/replacements) without overwriting manual data.
Financial Analytics & Dashboard: Features a dynamic Glassmorphism UI with a ViewPager2 carousel, displaying relative time to the next shift and a Donut Chart tracking the monthly working hours goal.
Local Persistence: Utilizes Room Database for robust local storage, including custom migrations and an archive for monthly financial reports.
System Integrations: Leverages AlarmManager for push notifications before shifts and allows seamless export to the native Google Calendar.
🛠 Tech Stack
Language: Java
Architecture: MVVM (Model-View-ViewModel)
Local Database: Room (SQLite)
Networking & Auth: Retrofit, Gson, Google Play Services Auth (OAuth2), Gmail API
UI/UX: Material Design 3, Glassmorphism aesthetic, MPAndroidChart, ViewPager2
⚙️ Getting Started (Local Setup)
To run this project locally, you need to configure your own Google Cloud Console project:

Clone the repository: git clone https://github.com/YourUsername/cinema-assistant-android.git
Go to Google Cloud Console and create a new project.
Enable the Gmail API.
Configure the OAuth Consent Screen and add your testing email addresses to the "Test users" list.
Create OAuth 2.0 Client IDs for Android and download the credentials.json (ensure this is kept out of version control).
Build and run the app in Android Studio.
🗺 Roadmap (Future Integrations)
 USOS API Integration: Connect the app with the university's REST API (USOS) to automatically correlate work shifts with the student's academic timetable, preventing scheduling conflicts.
 Home Screen Widget: A dynamic widget showing a countdown to the next shift.
