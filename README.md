# JSClub Codefest Bot

## Overview
This repository contains a Java bot for the JSClub Codefest game, designed to automate player actions such as **movement**, **combat**, **item collection**, and **strategic decision-making**. The bot interacts with the game server, **processes map updates**, and **executes optimal actions** based on the current game state.

---

##  🚀 Features
- **Pathfinding**: Uses shortest path algorithms to navigate the map, avoiding obstacles, enemies, and unsafe zones.
- **Combat Logic**: Prioritizes attacking weak or nearby players, uses weapons and support items intelligently.
- **Item Management**: Automatically loots nearby items, opens chests, and uses healing/support items when needed.
- **Retreat Strategy**: Detects crowded or dangerous areas and retreats to safer zones when health is low
- **Dynamic Targeting**: Locks onto targets based on proximity, health, and strategic value.
- **Safe Zone Awareness**: KEnsures the bot stays within the safe zone and moves to safety when outside.

---

##  📁 Project Structure
    src/
    ├── Main.java               # Entry point and main loop
    ├── MapManager.java         # Danger analysis & movement planning
    ├── ItemManager.java        # Item detection and looting
    ├── Attack.java             # Combat and target selection
    ├── Health.java             # Healing and support logic
    ├── EnemyTrajectoryCollector.java  # Enemy movement prediction
    └── jsclub/codefest/sdk/    # Game SDK models and utilities


---

## 🛠️ Setup

### ✅ Prerequisites
- Java 11 or higher
- IntelliJ IDEA (recommended)
- Internet connection

### ⚙️ Installation

1. Open the project in **IntelliJ IDEA**.
2. Set project SDK to **Java 11+**.
3. Update the constants in `src/Main.java`:
    - `SERVER_URL`
    - `GAME_ID`
    - `PLAYER_NAME`
    - `SECRET_KEY`

---

## ▶️ Usage

1. Run `Main.java` in IntelliJ.
2. The bot will connect to the server and act automatically.
3. Console logs will display all decisions and actions in real-time.

---

## 🧠 Key Algorithms

- **Pathfinding**: Uses a shortest path algorithm (optionally A\*) to find optimal routes.
- **Danger Assessment**: Calculates area danger based on player density, enemy proximity, and obstacles.
- **Fallback Logic**: If no path is found, uses a cached last path for safe fallback movement.

---

## 🛠️ Customization

- **Strategy Tuning**: Adjust thresholds and logic in Main.java and MapManager.java for different play styles.
- **Item/Combat Priorities**: Modify item and combat selection logic to suit your strategy.

---

## 🐛 Troubleshooting

| Issue                  | Solution                                                              |
|------------------------|-----------------------------------------------------------------------|
| Connection errors      | Check `SERVER_URL` and authentication constants.                     |
| SDK not found          | Ensure `src/jsclub/codefest/sdk/` is correctly imported.             |
| Strange behavior       | Use console logs to debug logic in movement or combat decisions.     |

---

## 📜 License

For **educational and competition use only**. Please follow JSClub Codefest’s terms of service.

---

## 📬 Contact

For questions or issues, please open an issue in this repository.