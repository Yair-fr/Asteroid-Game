# 🚀 Asteroid Game

Welcome to the **Asteroid Game**, a classic arcade-style space shooter developed in Java Swing! Navigate your spaceship through perilous asteroid fields, blast obstacles, collect power-ups, and aim for the high score.

This enhanced version introduces:
- Multiplayer mode  
- AI-controlled opponent ("Jarvis")  
- Ship customization  
- Advanced power-ups  
- Dynamic difficulty and performance stats  

---

## 🎮 Features

### 🔹 Single-Player Mode
- Face continuous waves of asteroids with increasing difficulty.

### 🔹 Multiplayer Mode (Local)
- **Customizable Names & Controls**: Personalize player names and key bindings.
- **Quick Play**: Jump in with default settings.
- **Co-op Gameplay**: Survive together with a friend.
- **Ship-to-Ship Interaction**: Push each other without damage.

### 🤖 ML Mode (Jarvis AI)
- Battle "Jarvis", a learning AI ship.
- Configurable AI lives.
- Adaptive behavior that evolves with gameplay.

### ✨ Power-Ups
- **Freeze (❄️)**: Appears every 100 points. Freezes all asteroids for 2 seconds.
- **AtomBOOM (☢️)**: Appears every 200 points. Creates a blast clearing nearby asteroids.
- **Shield**: Temporary invincibility and asteroid slowdown. Includes cooldown.

### 🔫 Bullet Mechanics
- Limited magazine with reload system:
  - Full reload after empty.
  - Passive incremental reloads.

### 📈 High Scores & Stats
- Saves best scores with difficulty and AI details.
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
javac AsteroidGame.java Ship.java Bullet.java Asteroid.java PopEffect.java \
HighScoreEntry.java AIStats.java ShipAI.java StatisticsDialog.java PowerUp.java
