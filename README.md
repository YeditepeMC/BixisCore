# BixisCore

Central data and API library plugin for YeditepeMC network.
Manages player XP, level progression, and provides a unified API 
for all other Bixis plugins.

## Features
- XP & Level system (cap: 50, custom progression curve)
- Vault economy integration (coin management via EssentialsX)
- Async SQLite storage with MySQL-ready architecture
- LevelUpEvent for inter-plugin communication
- Player data caching with join/quit lifecycle management

## API Usage
```java
BixisCorePlugin.getAPI().addXP(player, 100);
BixisCorePlugin.getAPI().addCoins(player, 150);
BixisCorePlugin.getAPI().removeCoins(player, 50);
BixisCorePlugin.getAPI().getLevel(player);
BixisCorePlugin.getAPI().getPlayerData(player);
```

## Dependencies
- Paper 26.1.2
- Java 25
- Vault (+ an economy provider, e.g. EssentialsX)

## XP Curve
| Level Range | XP Per Level |
|-------------|-------------|
| 1–10        | 500 XP      |
| 11–25       | 1.000 XP    |
| 26–40       | 2.000 XP    |
| 41–50       | 3.500 XP    |

## Required By
- BixisRewards
- BixisAchievements (yakında)

## Installation
1. Install Vault + EssentialsX on your server
2. Drop BixisCore.jar into plugins/
3. Restart server