# SleepFastTracker

SleepFastTracker is an Android application that helps users track and improve their sleep schedule, intermittent fasting, and weight management through an achievement-based tracking system.

## Features

### Intermittent Fasting Tracking
- Toggle between fasting and eating states with a single tap
- Visual countdown timer showing time remaining in current state
- Automatic tracking of fasting streaks
- Customizable eating window configuration
- Notifications for fasting end and eating end times

### Sleep Schedule Tracking
- Daily bedtime reminders and check-ins
- Track if you went to bed on time
- Visual countdown to bedtime
- Automatic bedtime streak calculation
- Morning wake-up time tracking

### Weight Management
- Record and track weight changes over time
- BMI calculation and categorization
- Visual representation of weight loss progress
- Weight loss achievement system

### Achievement System
- Unlock achievements based on:
  - Sleep consistency streaks
  - Fasting consistency streaks
  - Weight loss milestones
- Point-based scoring system
- Emoticon badges for visual reward

### Additional Features
- Initial setup wizard for personalization
- Statistics and progress visualization
- Dashboard with at-a-glance status of all tracked metrics
- Debug mode for troubleshooting

## App Structure

The app uses modern Android architecture patterns with:
- MVVM (Model-View-ViewModel) architecture
- Room database for local data persistence
- LiveData for reactive UI updates
- Coroutines for asynchronous operations
- Repository pattern for data access

## Data Structure

The app's database consists of the following entities:

### User Settings
- Personal information (name, age, height)
- Sleep schedule settings (bedtime, wake-up time)
- Fasting window settings (eating start time, eating window hours)
- Notification preferences

### Fasting Records
- Fasting state transitions (fasting/eating)
- Timestamps for state changes
- Calculated delta minutes from target fasting window

### Sleep Records
- Daily bedtime adherence tracking
- Timestamps for sleep records

### Weight Records
- Weight measurements with timestamps
- Calculated weight change deltas

### Achievements
- Achievement categories (sleep, fasting, weight)
- Points system
- Threshold values for unlocking

## Getting Started

### Prerequisites
- Android Studio Arctic Fox or newer
- Android SDK 34 or higher
- Gradle 8.0+

### Installation
1. Clone the repository
```
git clone https://github.com/berrygrove/SleepFastTracker.git
```
2. Open the project in Android Studio
3. Build and run on an emulator or physical device

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0) - see the LICENSE file for details.

## Acknowledgments

- Built with Android Jetpack components
- Uses Material Design components for the UI 