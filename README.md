# 🚀 Asteroid Game

Welcome to the **Asteroid Game**, a classic arcade-style space shooter developed in Java Swing! Navigate your spaceship through perilous asteroid fields, blast obstacles, collect power-ups, and aim for the high score.

## Download and Play

To get started, you can download the latest executable version of the game:

Download [AsteroidGame.exe](AsteroidGame-ver-2.exe)

This enhanced version introduces:
- Advanced power-ups
- Dynamic difficulty and performance stats

---

## 🎮 Features

### 🔹 Single-Player Mode
- Face continuous waves of asteroids with increasing difficulty.

### ✨ Power-Ups
- **Freeze (❄️)**: Appears every 100 points. Freezes all asteroids for 2 seconds.
- **AtomBOOM (☢️)**: Appears every 200 points. Creates a blast clearing nearby asteroids.
- **Shield**: Temporary invincibility and asteroid slowdown. Includes cooldown.

### 🔫 Bullet Mechanics
- Limited magazine with reload system:
  - Full reload after empty.
  - Passive incremental reloads.
- No cooldown between shots—fire as fast as you press the shoot key.

### 📈 High Scores & Stats
- Saves best scores with difficulty settings.
- Detailed post-game statistics:
  - Time played
  - Asteroids destroyed
  - Bullets fired
  - Shields used
- Visual charts:
  - Pie chart for bullet accuracy
  - Bar chart for shield usage

### 🎨 Visual & Customization
- Dynamic starfield background.
- Multiple ship patterns: *Default, Zebra, Dotted*

---

## 🛠️ How to Run

### Prerequisites
- Java Development Kit (JDK 8 or higher)

### Compile
```bash
javac AsteroidGame.java
```
### Playing
- Launch `AsteroidGame` and use the **Difficulty** button on the main menu to choose a level before starting.

### Distribution
This project is written for Java Swing, so it cannot be directly packaged as an Android `.apk` without a full port to Android APIs.

