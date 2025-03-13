# SyncKage – Real-Time Google Drive Sync for Android  

## 📌 Overview  
Google Drive for Desktop enables seamless folder synchronization, but its Android counterpart lacks automatic sync.  
**SyncKage** bridges this gap by enabling **real-time, two-way synchronization** between local storage and Google Drive, just like Google Drive for Desktop.  

## ⚡ Features  
✔ **Real-Time File Monitoring** – Instantly detects changes using FileObserver  
✔ **Multi-Folder Sync Support** – Synchronizes multiple folders seamlessly  
✔ **Two Sync Modes:** Hidden (ADB-controlled) & UI Mode  
✔ **Optimized Uploads & Downloads** – Prevents redundant transfers  
✔ **Stealth Mode** – No UI, No App Icon, Runs in Background  
✔ **Auto-Start on Boot** – Ensures continuous operation  
✔ **Secure Authentication** – Uses Google Drive API with a service account  
✔ **Parallel Processing** – Uses Coroutines for faster synchronization  

## 🛠 Tech Stack  
- **Kotlin** – Core language  
- **Android Foreground Service** – Background execution  
- **FileObserver API** – Monitors file changes  
- **Google Drive API** – Cloud synchronization  
- **Coroutines** – Handles parallel tasks  

## 🔧 Setup & Usage  
> **Note:** SyncKage requires a Google Drive service account for authentication.  
> Due to security reasons, the service account JSON key is not included in this repository.  

### **Usage Modes**  
1. **Hidden Mode (Stealth)** – Runs as a background service, controllable via ADB.  
2. **UI Mode** – Provides Start/Stop buttons and real-time logs.

https://github.com/user-attachments/assets/41497f9d-6aa0-4920-a142-b37149c72c92

## ❗ Limitations  
- **Service Account Requirement** – Users must create and configure their own Google Drive service account.  
- **No Installation Guide** – Due to authentication constraints, installation steps are skipped.  
- **Requires ADB for Hidden Mode** – Stealth mode is designed for power users with ADB knowledge.  

## 🔮 Future Scope  
- Custom sync intervals for better power efficiency  
- Encryption before upload for enhanced security  

## 🤝 Acknowledgments  
SyncKage was developed to enhance Android file management by providing **effortless, real-time synchronization**, just like Google Drive for Desktop.  

## 📜 License  
SyncKage is released under the [MIT License](LICENSE).
