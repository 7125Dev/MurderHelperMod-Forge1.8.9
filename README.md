# Murder Mystery Helper Mod

A comprehensive Minecraft mod designed to enhance the Murder Mystery gameplay experience with advanced detection, tracking, and visualization features.

## Features

### 🎮 Game Detection & Tracking
- **Automatic Game State Detection**: Packet-based detection system that automatically identifies game start/end without manual commands
- **Advanced Player Role Detection**: Accurately identifies and tracks:
    - Murderers (with knife detection)
    - Detectives (with detective bow detection)
    - Shooters (civilians with bows)
    - Suspects (players near corpses)
    - Innocent civilians

### 🔪 Weapon Detection System
- **Knife Tracking**: Real-time detection of murderer's knife with states:
    - Holding/Not Holding status
    - In-Flight detection when thrown
    - Cooldown timer tracking
    - Ready state indicator

- **Bow Classification & Tracking**: Distinguishes between three bow types:
    - **Detective Bow**: Infinite arrows with cooldown timer
    - **Kali Bow**: Infinite arrows from Kali blessing (no cooldown)
    - **Normal Bow**: Standard bow obtained from gold coins

- **Bow State Detection**:
    - Holding/Not Holding status
    - Drawing state
    - Charged/Ready to shoot indicator
    - Cooldown timer for detective bows

### 🎯 Enhanced HUD Display
- **Real-time Player Information**:
    - Player name and head avatar
    - Role identification with color coding
    - Weapon status and type
    - Distance tracking with color-coded threat levels
    - Player coordinates

- **Proximity Alerts**:
    - Flying knife distance warning
    - Incoming arrow distance warning
    - Color-coded danger levels (Safe/Watch/Alert/DANGER)

- **Customizable Interface**:
    - Draggable window position
    - Adjustable background opacity slider
    - Auto-resizing based on content

### 🕵️ Suspect Detection System
- Automatically marks players as suspects who appear within 10 blocks of a corpse within 5 seconds of death
- Smart filtering to exclude:
    - Dead/invisible players
    - Confirmed detectives
    - Confirmed murderers
- Auto-clears suspects when murderer is identified

### 🎨 Visual Enhancements
- **Color-Coded Nametags**:
    - Red: Murderer
    - Blue: Detective
    - Orange: Shooter
    - Yellow: Suspect
    - Green: Innocent

- **Bow Drop Visualization**: Shows dropped bow locations with type-specific colors
- **Corpse Detection**: Real-time corpse position tracking

### 📦 Advanced Packet System
- Custom packet interceptor for precise game event detection
- Annotation-based packet listener system
- Connection event handling for automatic initialization

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java Development Kit (JDK) 8** - Forge 1.8.9 requires JDK 8
- **Git** (optional, for version control)
- **IntelliJ IDEA** or **Eclipse IDE**

## Forge Version

This mod is built using:
- **Forge Version**: 1.8.9-11.15.1.2318-1.8.9
- **Minecraft Version**: 1.8.9
- **Mixins**: Required for advanced features

## Setup Instructions

### Option 1: IntelliJ IDEA

#### Initial Setup

1. **Extract the mod files** to your desired location
2. **Open a terminal/command prompt** in the mod directory
3. **Run the setup command**:

   On Windows:
```bash
   gradlew setupDecompWorkspace
```

On Linux/Mac:
```bash
   ./gradlew setupDecompWorkspace
```

This process will take several minutes as it downloads dependencies and decompiles Minecraft.

4. **Generate IDE files**:
```bash
   gradlew idea
```

5. **Open the project** in IntelliJ IDEA:
    - Launch IntelliJ IDEA
    - Select "Open" and navigate to your mod folder
    - Open the project

6. **Configure the run configurations**:
    - IntelliJ should automatically detect the Gradle project
    - Go to Run → Edit Configurations
    - You should see "Minecraft Client" and "Minecraft Server" configurations
    - If not, you can create them manually using the Gradle tasks

#### Building the Mod

To build your mod in IntelliJ IDEA:

1. Open the terminal in IDEA (View → Tool Windows → Terminal)
2. Run the build command:
```bash
   gradlew build
```
3. The compiled mod JAR file will be located in `build/libs/`

### Option 2: Eclipse

#### Initial Setup

1. **Extract the mod files** to your desired location
2. **Open a terminal/command prompt** in the mod directory
3. **Run the setup command**:

   On Windows:
```bash
   gradlew setupDecompWorkspace
```

On Linux/Mac:
```bash
   ./gradlew setupDecompWorkspace
```

4. **Generate Eclipse workspace**:
```bash
   gradlew eclipse
```

5. **Import the project into Eclipse**:
    - Launch Eclipse
    - Select File → Import
    - Choose "Existing Projects into Workspace"
    - Browse to your mod folder and select it
    - Click Finish

6. **Refresh the project**:
    - Right-click on the project in Eclipse
    - Select "Refresh" or press F5

#### Building the Mod

To build your mod in Eclipse:

1. Open a terminal/command prompt in your project directory
2. Run the build command:
```bash
   gradlew build
```
3. The compiled mod JAR file will be located in `build/libs/`

## Running the Mod

### In Development

**IntelliJ IDEA:**
- Use the "Minecraft Client" run configuration to launch the game
- Use the "Minecraft Server" run configuration to launch a test server

**Eclipse:**
- Run the `runClient` Gradle task for the client
- Run the `runServer` Gradle task for the server

### In Production

1. Build the mod using `gradlew build`
2. Locate the JAR file in `build/libs/`
3. Copy the JAR file to your Minecraft `mods` folder
4. Launch Minecraft with Forge 1.8.9 installed

## Common Gradle Commands

- `gradlew setupDecompWorkspace` - Sets up the development environment
- `gradlew build` - Compiles and builds the mod
- `gradlew clean` - Cleans the build directory
- `gradlew idea` - Generates IntelliJ IDEA project files
- `gradlew eclipse` - Generates Eclipse project files
- `gradlew runClient` - Runs the Minecraft client
- `gradlew runServer` - Runs the Minecraft server

## Project Structure

The mod follows a standard Forge mod structure with organized packages:
```
src/main/java/me/dev7125/murderhelper/
├── config/               # Configuration management
│   └── ModConfig.java
├── core/                 # Core systems
│   ├── annotation/
│   │   └── PacketListener.java
│   └── listener/
│       ├── ConnectionEventHandler.java
│       ├── MurderMysteryGameListener.java
│       ├── PacketInterceptor.java
│       └── PacketListenerRegistry.java
├── feature/              # Core gameplay features
│   ├── AlarmSystem.java
│   └── ShoutMessageBuilder.java
├── game/                 # Game state and detection
│   ├── BowDropDetector.java
│   ├── BowShotDetector.java
│   ├── CorpseDetector.java
│   ├── GameStateManager.java
│   ├── KnifeThrownDetector.java
│   ├── PlayerTracker.java
│   ├── RoleDetector.java
│   └── SuspectTracker.java
├── gui/                  # Graphical user interface
│   ├── ConfigGUI.java
│   └── ModGuiFactory.java
├── handler/              # Event handlers
│   ├── BowDropRenderHandler.java
│   ├── HUDRenderHandler.java
│   └── NameTagsRenderHandler.java
├── mixins/               # Mixin implementations
│   ├── MixinRenderManager.java
│   └── MixinRendererLivingEntity.java
├── render/               # Rendering components
│   ├── BowDropRenderer.java
│   ├── MurderMysteryHUD.java
│   └── NametagRenderer.java
├── util/                 # Utility classes
│   ├── GameConstants.java
│   └── ItemClassifier.java
└── MurderHelperMod.java  # Main mod class

src/main/resources/
├── mcmod.info            # Mod metadata
└── mixins.murderhelper.json  # Mixin configuration
```

### Package Description

- **config**: Handles mod configuration and settings
- **core**: Core packet interception and event listening systems
- **feature**: Implements core gameplay features and mechanics
- **game**: Advanced detection systems for game state, roles, weapons, and suspects
- **gui**: Provides configuration GUI and user interface elements
- **handler**: Event handlers for various game events
- **mixins**: Mixin classes for modifying vanilla Minecraft behavior
- **render**: Custom rendering logic for HUD, entities, and overlays
- **util**: Helper classes and constants used throughout the mod

## Troubleshooting

### "Java version mismatch" error
Make sure you're using JDK 8. You can check your Java version with:
```bash
java -version
```

### "Out of memory" error during setup
Add more memory to Gradle by creating or editing `gradle.properties` and adding:
```
org.gradle.jvmargs=-Xmx3G
```

### Eclipse: "Project is missing required source folder"
- Right-click on the project → Gradle → Refresh Gradle Project
- If that doesn't work, delete the project (keep contents on disk), then re-import it

### IntelliJ: Run configurations not appearing
- Go to File → Invalidate Caches / Restart
- After restart, reimport the Gradle project

### Changes not reflecting in-game
- Make sure to rebuild the project before launching
- Clean and rebuild if necessary: `gradlew clean build`

### Mixin issues
- Ensure `mixins.murderhelper.json` is properly configured
- Check that the mixin dependency is included in `build.gradle`
- Verify Mixin plugin is loaded correctly in Forge

## Configuration

The mod includes an in-game configuration GUI accessible through the Minecraft Mod Options menu. Configuration options include:

- HUD position and opacity
- Color schemes for different roles
- Alarm system settings
- Proximity warning thresholds

## Additional Resources

- [Forge Documentation](http://mcforge.readthedocs.io/en/latest/)
- [Forge Forums](http://www.minecraftforge.net/forum/)
- [Gradle Documentation](https://docs.gradle.org/)
- [Mixin Documentation](https://github.com/SpongePowered/Mixin/wiki)

## Development Notes

- This mod uses **Mixins** for advanced features - ensure mixin configuration is properly set up
- The mod is specifically designed for **Murder Mystery** game mode on Hypixel
- Main entry point is `MurderHelperMod.java`
- All detection systems run automatically without manual intervention
- Configuration is saved in the Minecraft config folder

## Notes

- Always run `setupDecompWorkspace` after cloning/downloading the project for the first time
- If you switch between IDEs, you may need to run the setup command again
- Keep your workspace separate from your Minecraft installation directory
- The first build will take longer as dependencies are downloaded
- When adding new Mixins, update `mixins.murderhelper.json` accordingly
- The mod includes comprehensive packet interception - ensure you understand the packet system before modifying

## Version History

### v2.0.0 (Latest)

#### Features
- ✨ **Automatic Role Detection**: Removed manual role slot configuration - now auto-detects player role
- ✨ **Packet-Based Bow Drop Detection**: Rewritten from tick-based entity polling to server packet interception
- ✨ **Instant Detective Inheritance**: New detective is locked immediately upon bow pickup, no equip required
- ✨ **Enhanced Hitbox Rendering**: Completely rewritten F3+B player hitbox with distinctive blue outline

#### Bug Fixes
- 🐛 Fixed trap kill false positives - players near trap-killed corpses no longer marked as suspects
- 🐛 Fixed Kali cursed iron sword misidentification as murder weapon
- 🐛 Fixed NPC nametag rendering issue
- 🐛 Fixed duplicate nametag rendering for winner clones after game victory

#### Improvements
- ⚡ HUD now correctly displays Shooter's role item (normal bow) instead of current held item
- ⚡ Optimized packet-based detection for better performance and accuracy

### v1.0.0

- ✨ Refactored game state detection with packet-based system
- ✨ Implemented advanced weapon detection with zero false positives
- ✨ Added comprehensive role detection (Murderer, Detective, Shooter, Suspect, Innocent)
- ✨ Introduced bow classification system (Detective, Kali, Normal)
- ✨ Added suspect detection system based on corpse proximity
- ✨ Enhanced HUD with detailed weapon states and cooldown tracking
- ✨ Added proximity alerts for flying knives and arrows
- ✨ Removed manual game start commands - now fully automatic
- ✨ Improved nametag and bow drop visual rendering

---

**Developed for Minecraft 1.8.9 Forge**

Happy modding!
```

<img width="1069" height="598" alt="image" src="https://github.com/user-attachments/assets/c76ad51c-268e-4f1f-a2e9-673bb6ac5267" />
<img width="1069" height="598" alt="image" src="https://github.com/user-attachments/assets/5f29b4eb-868a-4d5e-bb6a-b6278fb101f1" />
<img width="1069" height="598" alt="image" src="https://github.com/user-attachments/assets/aecb93a4-0a93-4799-8938-5dfecc6295ee" />
<img width="1069" height="598" alt="image" src="https://github.com/user-attachments/assets/b7f31a61-8be5-4d97-8e16-e13493024f7f" />
<img width="1069" height="598" alt="image" src="https://github.com/user-attachments/assets/5c817f56-51c2-44e3-ae8f-1c501efe5ed9" />




