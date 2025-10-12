# Minecraft Forge Mod - Build Guide

This guide will help you set up your development environment and compile this Minecraft mod using Forge 1.8.9.

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java Development Kit (JDK) 8** - Forge 1.8.9 requires JDK 8
- **Git** (optional, for version control)
- **IntelliJ IDEA** or **Eclipse IDE**

## Forge Version

This mod is built using:
- **Forge Version**: 1.8.9-11.15.1.2318-1.8.9
- **Minecraft Version**: 1.8.9

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
   
   This process will take several minutes as it downloads dependencies and decompiles Minecraft.

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

## Additional Resources

- [Forge Documentation](http://mcforge.readthedocs.io/en/latest/)
- [Forge Forums](http://www.minecraftforge.net/forum/)
- [Gradle Documentation](https://docs.gradle.org/)

## Project Structure

The mod follows a standard Forge mod structure with organized packages:

```
src/main/java/me/dev7125/murderhelper/
├── command/              # Command implementations
│   └── MurderHelperCommands.java
├── config/               # Configuration management
│   └── ModConfig.java
├── feature/              # Core gameplay features
│   ├── AlarmSystem.java
│   └── ShoutMessageBuilder.java
├── game/                 # Game state and player management
│   ├── GameStateManager.java
│   ├── PlayerTracker.java
│   └── RoleDetector.java
├── gui/                  # Graphical user interface
│   ├── ConfigGUI.java
│   └── ModGuiFactory.java
├── handler/              # Event handlers
│   ├── BowDropTracker.java
│   ├── HUDHandler.java
│   └── RenderHandler.java
├── mixins/               # Mixin implementations
│   ├── MixinNetHandlerPlayClient.java
│   └── MixinRendererLivingEntity.java
├── render/               # Rendering components
│   ├── BowDropRenderer.java
│   ├── MurderMysteryHUD.java
│   └── NametagRenderer.java
├── util/                 # Utility classes
│   ├── GameConstants.java
│   ├── GameStateDetector.java
│   └── ItemClassifier.java
└── MurderHelperMod.java  # Main mod class

src/main/resources/
├── mcmod.info            # Mod metadata
└── mixins.murderhelper.json  # Mixin configuration
```

### Package Description

- **command**: Contains all custom commands for the mod
- **config**: Handles mod configuration and settings
- **feature**: Implements core gameplay features and mechanics
- **game**: Manages game state detection and player tracking
- **gui**: Provides configuration GUI and user interface elements
- **handler**: Event handlers for various game events
- **mixins**: Mixin classes for modifying vanilla Minecraft behavior
- **render**: Custom rendering logic for HUD, entities, and overlays
- **util**: Helper classes and constants used throughout the mod

## Development Notes

- This mod uses **Mixins** for some features - make sure the mixin configuration is properly set up
- The mod is designed for **Murder Mystery** game mode
- Main entry point is `MurderHelperMod.java` - this is where the mod initializes
- Configuration is saved in the Minecraft config folder

## Notes

- Always run `setupDecompWorkspace` after cloning/downloading the project for the first time
- If you switch between IDEs, you may need to run the setup command again
- Keep your workspace separate from your Minecraft installation directory
- The first build will take longer as dependencies are downloaded
- Make sure to include all required dependencies in your `build.gradle` file
- When adding new Mixins, update `mixins.murderhelper.json` accordingly

---

Happy modding!