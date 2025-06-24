# 🚀 Asteroid Game

## 🎮 Overview
Asteroid Game is a classic arcade-style space shooter developed in Java using Swing. Players navigate through an asteroid field, avoiding collisions and destroying asteroids with precision shooting. The game supports both single-player and local multiplayer modes, complete with customizable controls and ship designs.

## ✨ Features

- **Single-Player Mode** – Face waves of asteroids solo.
- **Local Multiplayer Mode** – Play with a friend on the same keyboard.
- **Customizable Controls** – Configure shoot and hyperspace keys individually.
- **Ship Skins** – Choose from various patterns in multiplayer (Default, Zebra, Dotted).
- **Dynamic Asteroid Spawning** – Asteroids split into smaller pieces upon destruction.
- **Bullet Magazine System** – Limited ammo with cooldown-based reloading.
- **Hyperspace Ability** – Temporary invincibility and speed burst (with cooldown).
- **High Score Tracking** – Best scores are automatically recorded and displayed.
- **Responsive Scaling** – Adapts to window size while maintaining aspect ratio.
- **Visual Effects** – Includes background starfield and explosion animations.

## 🎯 Game Objective
Destroy asteroids to earn points and avoid collisions to preserve your lives. In multiplayer, cooperate or compete for the high score.

## 🕹️ Controls

### Player 1 (Default)
- **Thrust:** `↑` Arrow
- **Rotate Left:** `←` Arrow
- **Rotate Right:** `→` Arrow
- **Shoot:** `SPACE` (Configurable)
- **Hyperspace:** `H` (Configurable)

### Player 2 (Multiplayer Only, Default)
- **Thrust:** `W`
- **Rotate Left:** `A`
- **Rotate Right:** `D`
- **Shoot:** `C` (Configurable)
- **Hyperspace:** `V` (Configurable)

### General
- **Restart Game (after Game Over):** `ENTER`
- **Exit Game:** Click the ❌ button (top right corner)

## 🔧 Gameplay Mechanics

### 🔫 Shooting
- You have a 5-bullet magazine.
- **Full reload:** 5 seconds after all bullets are used.
- **Passive reload:** 1 bullet per second when not full.

### 🌌 Hyperspace
- Grants brief invincibility and speed boost.
- Available with a cooldown.

### ☄️ Asteroids
- Large asteroids split into smaller ones when destroyed.
- Destroying them increases your score.

## 🛠️ Setup & Running the Game

### Requirements
- Java JDK 8 or higher.

### Steps
1. **Save the Code:** `AsteroidGame.java`
2. **Compile:**
   ```bash
   javac AsteroidGame.java
