# SyncKage – Real-Time Google Drive Sync for Android  
 
Google Drive for Desktop enables seamless folder synchronization, but its Android counterpart lacks automatic sync.  
**SyncKage** bridges this gap by enabling **real-time, two-way synchronization** between local storage and Google Drive, just like Google Drive for Desktop.  

## Features  
- **Real-Time File Monitoring** – Instantly detects changes using FileObserver  
- **Multi-Folder Sync Support** – Synchronizes multiple folders seamlessly  
- **Two Sync Modes:** Hidden (ADB-controlled) & UI Mode  
- **Optimized Uploads & Downloads** – Prevents redundant transfers  
- **Stealth Mode** – No UI, No App Icon, Runs in Background  
- **Auto-Start on Boot** – Ensures continuous operation  
- **Secure Authentication** – Uses Google Drive API with a service account  
- **Parallel Processing** – Uses Coroutines for faster synchronization  

## Tech Stack  
- **Kotlin** – Core language  
- **Android Foreground Service** – Background execution  
- **FileObserver API** – Monitors file changes  
- **Google Drive API** – Cloud synchronization  
- **Coroutines** – Handles parallel tasks  

## Setup & Usage  
 > **Note:** SyncKage requires a Google Drive service account for authentication.  
 > Due to security reasons, the service account JSON key is not included in this repository.  

#### **Usage Modes**  
1. **Hidden Mode (Stealth)** – Runs as a background service, controllable via ADB.  
2. **UI Mode** – Provides Start/Stop buttons and real-time logs.

https://github.com/user-attachments/assets/41497f9d-6aa0-4920-a142-b37149c72c92

## Screenshots - UI Mode
<p align="center" style="display: flex; justify-content: center; gap: 50px;">
  <img src="https://github.com/user-attachments/assets/a890e090-fb9e-4aec-93af-225579e3c880" alt="Sync Started" width="30%">
  <img src="https://github.com/user-attachments/assets/96d0911f-8c31-4cd3-a320-7beb84adc05e" alt="Sync Stopped" width="30%">
</p>

## Limitations  
- **Service Account Requirement** – SyncKage relies on a **Google Drive service account** for authentication. Users must manually create and configure their own service account, which involves setting up a Google Cloud project and generating credentials.  
- **Requires ADB for Hidden Mode** – The **Stealth Mode** runs without a UI and requires **Android Debug Bridge (ADB)** to start, stop, or configure the service, making it suitable only for advanced users.  
- **Background Execution Constraints** – Some Android devices may restrict background services due to **battery optimization policies**, which might delay synchronization unless the app is granted proper permissions.  

## Future Scope  
- **Custom Sync Intervals for Power Efficiency** – Instead of constant real-time monitoring, users could set **sync intervals** (e.g., every 5, 10, or 30 minutes) to optimize battery usage.  
- **Encryption Before Upload for Enhanced Security** – Implementing **AES-256 encryption** for files before upload to protect sensitive data.  
- **Selective Sync Options** – Allow users to choose **specific folders or file types** to sync, reducing unnecessary data transfers.  
- **Sync Logs and Error Reporting** – A basic log system to track sync status and errors for easier debugging.  


## Acknowledgments  
SyncKage was developed to enhance Android file management by providing **effortless, real-time synchronization**, just like Google Drive for Desktop.  

## License  
SyncKage is released under the [MIT License](LICENSE).
