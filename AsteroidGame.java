import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections; // For sorting high scores
// import java.util.Comparator;  // For sorting high scores
import java.util.Iterator;
import java.util.List;
import java.util.Map; // Import for Map
import java.util.HashMap; // Import for HashMap
import java.util.Random;
import java.awt.geom.AffineTransform;

public class AsteroidGame extends JPanel implements ActionListener, KeyListener, MouseListener {
    // Game constants - remain fixed for internal game logic dimensions
    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;
    private static final int INITIAL_LIVES = 3;
    private static final long BULLET_COOLDOWN = 200; // 0.2 seconds delay between shots, for single press to register
    private static final int MAX_BULLETS = 5; // Max bullets in magazine
    private static final long RELOAD_FULL_DURATION = 5000; // 5 seconds to reload when empty
    private static final long RELOAD_INCREMENTAL_DURATION = 2000; // 2 second per bullet for passive reload

    // Shield (formerly Hyperspace) constants
    private static final long SHIELD_ACTIVE_DURATION = 2000; // 2 seconds of shield active time
    private static final long SHIELD_RECHARGE_DURATION = 10000; // 10 seconds to recharge shield
    private static final double SHIP_SPEED_REDUCTION_FACTOR = 0.5; // Ship moves at 0.5x normal speed when shielded

    // Asteroid slowdown during shield active
    // This value multiplies the asteroid's speed when a player has shield active.
    // 0.5 means asteroids move at half their normal speed.
    // 1.0 means no slowdown. 0.0 means they stop.
    private static final double ASTEROID_SLOWDOWN_MULTIPLIER = 0.5;

    // Power-up constants
    private static final int FREEZE_SCORE_INTERVAL = 100;
    private static final long FREEZE_DURATION = 2000; // 2 seconds
    private static final int ATOM_BOOM_SCORE_INTERVAL = 200;
    private static final long ATOM_BOOM_ANIMATION_DURATION = 2000; // 2 seconds
    private static final long POWERUP_LIFETIME = 10000; // Power-ups disappear after 10 seconds

    // Starfield constants for the start screen
    private static final int MAX_STARS = 150; // Number of stars in the background
    private final ArrayList<Star> stars = new ArrayList<>(); // Starfield for background

    // Game state variables
    Timer timer;
    Ship ship1, ship2; // Two ships for multiplayer
    ArrayList<Bullet> bullets = new ArrayList<>();
    ArrayList<Asteroid> asteroids = new ArrayList<>();
    ArrayList<PopEffect> effects = new ArrayList<>();
    ArrayList<PowerUp> powerUps = new ArrayList<>(); // List of active power-ups
    boolean up1, left1, right1; // Player 1 movement controls
    boolean up2, left2, right2; // Player 2 movement controls

    // Single-press action flags (consumed after one tick)
    boolean shootRequested1, shieldRequested1;
    boolean shootRequested2, shieldRequested2;

    int score = 0; // Single player score, or combined if desired later
    private int difficultyLevel = 0; // Starts at 0 for "extra easy" (dynamically updated in game)
    private int initialDifficulty = 1; // User selected initial difficulty (1 to 10)
    int lives1, lives2; // Lives for each player
    GameState state = GameState.START;
    private GameState lastPlayedState = GameState.PLAYING_SINGLE; // Stores the mode before GAME_OVER
    long lastShotTime1 = 0;
    long lastShotTime2 = 0;

    // Bullet magazine and reload state for each player
    private int currentBullets1 = MAX_BULLETS;
    private boolean reloading1 = false;
    private long reloadStartTime1 = 0;
    private long lastReloadTickTime1 = 0; // For incremental reload

    private int currentBullets2 = MAX_BULLETS;
    private boolean reloading2 = false;
    private long reloadStartTime2 = 0;
    private long lastReloadTickTime2 = 0; // For incremental reload

    // Shield state for each player (formerly Hyperspace)
    private boolean shieldActive1 = false;
    private long shieldActivationTime1 = 0;
    private long shieldRefillTime1 = 0; // When shield will be full again

    private boolean shieldActive2 = false;
    private long shieldActivationTime2 = 0;
    private long shieldRefillTime2 = 0;

    // Power-up specific state variables
    private long freezeEndTime = 0;
    private boolean atomBoomActive = false;
    private long atomBoomStartTime = 0;
    private double atomBoomCenterX, atomBoomCenterY;
    private int lastFreezeScoreTrigger = 0;
    private int lastAtomBoomScoreTrigger = 0;

    // Game Statistics variables
    private long gameStartTime;
    private long gameEndTime;
    private int bulletsFired;
    private int asteroidsDestroyed;
    private long totalShieldDuration1; // Total milliseconds spent with shield active for player 1
    private int totalShieldActivations1; // Total times shield was activated for player 1
    private long totalShieldDuration2; // Total milliseconds spent with shield active for player 2
    private int totalShieldActivations2; // Total times shield was activated for player 2
    private int totalAsteroidsSpawned; // Total asteroids created (initial + splits)
    private final int SHIELD_COST_PER_ACTIVATION = 10; // Configurable in code, not GUI, cost to activate shield

    // High Score management
    private List<HighScoreEntry> highScores = new ArrayList<>();
    private static final String HIGHSCORE_FILE = "highscores.dat";

    // AI Statistics management
    private AIStats aiStats;
    private static final String AI_STATS_FILE = "aistats.dat";
    private ShipAI shipAI; // Instance of the AI for ML mode

    // Username input for new high scores and multiplayer names
    private String userName1 = "Player 1";
    private String userName2 = "Player 2";
    private String tempUserNameInput = ""; // Used for JOptionPane input

    // Ship pattern configuration
    private Ship.Pattern player1ShipPattern = Ship.Pattern.NONE; // Default pattern
    private Ship.Pattern player2ShipPattern = Ship.Pattern.NONE; // Default pattern

    // Configurable keys for multiplayer
    private int player1ShootKey = KeyEvent.VK_SPACE;
    private int player1ShieldKey = KeyEvent.VK_H; // Renamed from player1HyperspaceKey
    private int player2ShootKey = KeyEvent.VK_C;
    private int player2ShieldKey = KeyEvent.VK_V; // Renamed from player2HyperspaceKey

    // Flags to track if shoot/shield keys are currently held down (to prevent repeated triggers from keyPressed)
    private boolean player1ShootKeyHeld = false;
    private boolean player1ShieldKeyHeld = false; // Renamed
    private boolean player2ShootKeyHeld = false;
    private boolean player2ShieldKeyHeld = false; // Renamed

    // Jarvis (AI) specific configurable lives
    private int jarvisLivesInput = INITIAL_LIVES;

    // Map to store button bounds and their corresponding actions for click handling
    private final Map<Rectangle, Runnable> buttonActions = new HashMap<>();


    // Random generator for the game
    private final Random random = new Random();

    public static void main(String[] args) {
        JFrame frame = new JFrame("Asteroid Game");
        AsteroidGame game = new AsteroidGame();
        frame.add(game);

        // Make window full screen
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH); // Set to full screen
        frame.setUndecorated(true); // Remove window decorations for full screen experience (optional)

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Close operation for the frame
        frame.setVisible(true);
    }

    public AsteroidGame() {
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this); // Add mouse listener for the close button
        initGame(); // Call initGame from constructor
        timer = new Timer(15, this); // ~60 FPS
        timer.start();
    }

    /**
     * Initializes or resets the game state for a new game.
     * This method resets common game elements, but does not start a game or spawn asteroids.
     */
    private void initGame() {
        ship1 = new Ship(WIDTH / 4, HEIGHT / 2, player1ShipPattern); // Ship 1 with chosen pattern
        ship2 = new Ship(3 * WIDTH / 4, HEIGHT / 2, player2ShipPattern); // Ship 2 with chosen pattern

        bullets.clear();
        asteroids.clear(); // Clear asteroids here, they'll be spawned by game mode specific logic
        effects.clear();
        powerUps.clear(); // Clear power-ups on new game
        score = 0; // Reset score (will be for current player in single, or combined if needed)
        difficultyLevel = initialDifficulty; // Initialize difficulty with user selection
        lives1 = INITIAL_LIVES;
        lives2 = INITIAL_LIVES;

        lastShotTime1 = 0;
        lastShotTime2 = 0;

        currentBullets1 = MAX_BULLETS;
        reloading1 = false;
        reloadStartTime1 = 0;
        lastReloadTickTime1 = System.currentTimeMillis(); // Initialize for passive reload

        currentBullets2 = MAX_BULLETS;
        reloading2 = false;
        reloadStartTime2 = 0;
        lastReloadTickTime2 = System.currentTimeMillis(); // Initialize for passive reload

        shieldActive1 = false; // Renamed
        shieldActivationTime1 = 0; // Renamed
        shieldRefillTime1 = System.currentTimeMillis(); // Shield starts full (Renamed)

        shieldActive2 = false; // Renamed
        shieldActivationTime2 = 0; // Renamed
        shieldRefillTime2 = System.currentTimeMillis(); // Shield starts full (Renamed)

        // Reset power-up specific states
        freezeEndTime = 0;
        atomBoomActive = false;
        atomBoomStartTime = 0;
        lastFreezeScoreTrigger = 0;
        lastAtomBoomScoreTrigger = 0;


        up1 = left1 = right1 = false; // Movement flags
        up2 = left2 = right2 = false;

        shootRequested1 = shieldRequested1 = false; // Action flags (Renamed)
        shootRequested2 = shieldRequested2 = false; // Action flags (Renamed)

        // Reset key held flags
        player1ShootKeyHeld = false;
        player1ShieldKeyHeld = false; // Renamed
        player2ShootKeyHeld = false;
        player2ShieldKeyHeld = false; // Renamed

        // Reset game statistics
        gameStartTime = 0;
        gameEndTime = 0;
        bulletsFired = 0;
        asteroidsDestroyed = 0;
        totalShieldDuration1 = 0; // Renamed
        totalShieldActivations1 = 0; // Renamed
        totalShieldDuration2 = 0; // Renamed
        totalShieldActivations2 = 0; // Renamed
        totalAsteroidsSpawned = 0;


        // Load high score list and AI stats at game initialization
        loadHighScores();
        loadAIStats();

        // If high scores were loaded, set username to the top scorer's name for default prompt
        if (!highScores.isEmpty()) {
            tempUserNameInput = highScores.get(0).getUserName();
        } else {
            tempUserNameInput = "Player"; // Default if no scores
        }

        // Initialize AI instance, passing the initial AI stats
        shipAI = new ShipAI(random, WIDTH, HEIGHT, aiStats);

        // Populate stars for the start screen background
        stars.clear();
        populateStars(WIDTH, HEIGHT, MAX_STARS);
    }

    /**
     * Spawns a specified number of initial asteroids for a new game.
     * @param count The number of asteroids to spawn.
     */
    private void spawnAsteroids(int count) {
        for(int i = 0; i < count; i++) {
            asteroids.add(new Asteroid(random, difficultyLevel));
            totalAsteroidsSpawned++; // Count initial asteroids
        }
    }

    /**
     * Populates the starfield for the background.
     * @param screenWidth The width of the game area.
     * @param screenHeight The height of the game area.
     * @param numStars The number of stars to generate.
     */
    private void populateStars(int screenWidth, int screenHeight, int numStars) {
        for (int i = 0; i < numStars; i++) {
            stars.add(new Star(random, screenWidth, screenHeight));
        }
    }

    /**
     * Paints the game components based on the current game state.
     * @param g The Graphics object for drawing.
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Save the original transform
        AffineTransform originalTransform = g2d.getTransform();

        // Calculate scaling factors to fit the game content to the current window size
        double scaleX = (double) getWidth() / WIDTH;
        double scaleY = (double) getHeight() / HEIGHT;
        double scale = Math.min(scaleX, scaleY); // Use the smaller scale to maintain aspect ratio

        // Translate to center the scaled game area within the panel
        g2d.translate((getWidth() - WIDTH * scale) / 2, (getHeight() - HEIGHT * scale) / 2);
        // Apply scaling
        g2d.scale(scale, scale);

        // Always draw moving star background in all states
        for (Star star : stars) {
            star.draw(g2d);
        }

        if (state == GameState.START) {

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 60)); // Larger title
            drawCenteredString(g2d, "ASTEROID GAME", new Font("Arial", Font.BOLD, 60), HEIGHT / 3 - 50);

            // Button dimensions reduced by 20%
            int buttonWidth = (int)(350 * 0.8);
            int buttonHeight = (int)(50 * 0.8);
            int smallButtonHeight = (int)(40 * 0.8);

            // Calculate vertical spacing
            int spacing = (int)(70 * 0.8); // Reduced spacing
            int initialY = HEIGHT / 2 - 40; // Initial Y for the first button

            // Single Player button
            drawButton(g2d, "Single Player", WIDTH / 2, initialY, buttonWidth, buttonHeight, () -> {
                tempUserNameInput = JOptionPane.showInputDialog(this, "Enter your username:", userName1);
                if (tempUserNameInput != null && !tempUserNameInput.trim().isEmpty()) {
                    userName1 = tempUserNameInput.trim();
                } else {
                    userName1 = "Player 1";
                }
                player1ShipPattern = Ship.Pattern.NONE; // Default for single player
                initGame(); // Initialize common game elements
                // Fewer initial asteroids for single player, especially on easy
                int initialAsteroidsSinglePlayer = 3;
                if (initialDifficulty <= 2) { // For Easy/Very Easy
                    initialAsteroidsSinglePlayer = 1; // Start with just one asteroid
                } else if (initialDifficulty <= 5) { // For Normal
                    initialAsteroidsSinglePlayer = 2;
                }
                spawnAsteroids(initialAsteroidsSinglePlayer);
                state = GameState.PLAYING_SINGLE;
                gameStartTime = System.currentTimeMillis(); // Record game start time
            });

            // Multiplayer (Customize Names) button
            drawButton(g2d, "Multiplayer (Customize Names)", WIDTH / 2, initialY + spacing, buttonWidth, buttonHeight, () -> {
                state = GameState.MULTIPLAYER_SETUP_NAMES;
            });

            // New: Multiplayer (Quick Play) button - directly to QR display
            drawButton(g2d, "Multiplayer (Quick Play)", WIDTH / 2, initialY + 2 * spacing, buttonWidth, buttonHeight, () -> {
                // Usernames remain default ("Player 1", "Player 2") unless previously changed
                player1ShipPattern = Ship.Pattern.ZEBRA; // Default for quick play
                player2ShipPattern = Ship.Pattern.DOTTED; // Default for quick play
                initGame(); // Make sure to init for quick play to apply patterns
                // Fewer initial asteroids for multiplayer quick play, especially on easy
                int initialAsteroidsMultiplayer = 5;
                if (initialDifficulty <= 2) { // For Easy/Very Easy
                    initialAsteroidsMultiplayer = 3;
                } else if (initialDifficulty <= 5) { // For Normal
                    initialAsteroidsMultiplayer = 4;
                }
                spawnAsteroids(initialAsteroidsMultiplayer);
                state = GameState.MULTIPLAYER_PLAYING; // Directly start playing
                gameStartTime = System.currentTimeMillis(); // Record game start time
            });

            // ML Mode button
            drawButton(g2d, "ML Mode (Jarvis)", WIDTH / 2, initialY + 3 * spacing, buttonWidth, buttonHeight, () -> {
                String inputLives = JOptionPane.showInputDialog(this, "Enter Jarvis's initial lives:", String.valueOf(jarvisLivesInput));
                if (inputLives != null && !inputLives.trim().isEmpty()) {
                    try {
                        int parsedLives = Integer.parseInt(inputLives.trim());
                        if (parsedLives > 0) {
                            jarvisLivesInput = parsedLives;
                        } else {
                            JOptionPane.showMessageDialog(this, "Lives must be a positive number. Using default.", "Invalid Input", JOptionPane.WARNING_MESSAGE);
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "Invalid number format for lives. Using default.", "Invalid Input", JOptionPane.WARNING_MESSAGE);
                    }
                }
                initGame(); // Re-initialize for new game
                lives1 = jarvisLivesInput; // AI starts with configurable lives
                player1ShipPattern = Ship.Pattern.DOTTED; // AI uses a distinct pattern
                spawnAsteroids(7); // Spawn 7 initial asteroids for ML mode (remains constant)
                aiStats.totalGames++; // Increment total games for AI
                saveAIStats();
                state = GameState.ML_PLAYING;
                gameStartTime = System.currentTimeMillis(); // Record game start time
            });


            // Button to select difficulty
            drawButton(g2d, "Difficulty: " + initialDifficulty + " (Click to Change)", WIDTH / 2, initialY + 4 * spacing, buttonWidth, smallButtonHeight, this::showDifficultySelection);


            // Removed High Score display from Start screen as per request
            // g2d.setFont(new Font("Arial", Font.PLAIN, 24));
            // drawCenteredString(g2d, "High Scores:", new Font("Arial", Font.BOLD, 28), initialY + 5 * spacing + 30);
            // int scoreY = initialY + 5 * spacing + 60;
            // for (int i = 0; i < Math.min(highScores.size(), 3); i++) { // Display top 3 scores
            //     HighScoreEntry entry = highScores.get(i);
            //     String scoreText = entry.getUserName() + ": " + entry.getScore() + " (D:" + entry.getDifficulty() + ")";
            //     if (entry.isAI()) {
            //         scoreText += " [AI v" + entry.getAiVersion() + "]";
            //     }
            //     drawCenteredString(g2d, (i + 1) + ". " + scoreText,
            //                        new Font("Arial", Font.PLAIN, 20), scoreY + (i * 25));
            // }

            // New button to view all high scores, repositioned slightly
            drawButton(g2d, "View All Scores", WIDTH / 2, initialY + 5 * spacing, (int)(250*0.8), smallButtonHeight, this::showAllHighScores);


            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            drawCenteredString(g2d, "Yair FR©", new Font("Arial", Font.PLAIN, 16), HEIGHT - 20); // Copyright at bottom middle

        } else if (state == GameState.MULTIPLAYER_SETUP_NAMES) {
            int currentY = 70; // Start Y for "Multiplayer Setup" title
            drawCenteredString(g2d, "Multiplayer Setup", new Font("Arial", Font.BOLD, 48), currentY);

            currentY += 80; // Space after title
            // Player 1 Section
            g2d.setFont(new Font("Arial", Font.PLAIN, 22)); // Slightly smaller font for labels
            drawCenteredString(g2d, "Player 1 Name: " + userName1, new Font("Arial", Font.PLAIN, 24), currentY);

            currentY += 40; // Space for button
            drawButton(g2d, "Edit Player 1 Name", WIDTH / 2, currentY, 200, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Enter Player 1 username:", userName1);
                if (input != null && !input.trim().isEmpty()) {
                    userName1 = input.trim();
                }
            });

            currentY += 60; // Space after button
            g2d.drawString("Player 1 Ship:", WIDTH / 2 - 100, currentY); // Left align this text

            currentY += 40; // Space for buttons
            drawButton(g2d, "Default", WIDTH / 2 - 150, currentY, 120, 40, () -> player1ShipPattern = Ship.Pattern.NONE);
            drawButton(g2d, "Zebra", WIDTH / 2, currentY, 120, 40, () -> player1ShipPattern = Ship.Pattern.ZEBRA);
            drawButton(g2d, "Dotted", WIDTH / 2 + 150, currentY, 120, 40, () -> player1ShipPattern = Ship.Pattern.DOTTED);

            currentY += 40; // Space for small current pattern text
            drawCenteredString(g2d, "Current: " + player1ShipPattern, new Font("Arial", Font.PLAIN, 16), currentY);

            currentY += 60;
            g2d.drawString("Player 1 Controls:", WIDTH / 2 - 100, currentY);

            currentY += 40;
            drawButton(g2d, "Shoot: " + KeyEvent.getKeyText(player1ShootKey), WIDTH / 2 - 100, currentY, 150, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Press key for Player 1 Shoot:");
                if (input != null && !input.isEmpty() && input.length() == 1) {
                    player1ShootKey = input.toUpperCase().charAt(0);
                }
            });
            drawButton(g2d, "Shield: " + KeyEvent.getKeyText(player1ShieldKey), WIDTH / 2 + 100, currentY, 150, 40, () -> { // Renamed
                String input = JOptionPane.showInputDialog(this, "Press key for Player 1 Shield:");
                if (input != null && !input.isEmpty() && input.length() == 1) {
                    player1ShieldKey = input.toUpperCase().charAt(0);
                }
            });

            // Player 2 Section
            currentY += 70; // Larger space before Player 2 section
            g2d.setFont(new Font("Arial", Font.PLAIN, 22)); // Slightly smaller font for labels
            drawCenteredString(g2d, "Player 2 Name: " + userName2, new Font("Arial", Font.PLAIN, 24), currentY);

            currentY += 40;
            drawButton(g2d, "Edit Player 2 Name", WIDTH / 2, currentY, 200, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Enter Player 2 username:", userName2);
                if (input != null && !input.trim().isEmpty()) {
                    userName2 = input.trim();
                }
            });

            currentY += 60;
            g2d.drawString("Player 2 Ship:", WIDTH / 2 - 100, currentY);

            currentY += 40;
            drawButton(g2d, "Default", WIDTH / 2 - 150, currentY, 120, 40, () -> player2ShipPattern = Ship.Pattern.NONE);
            drawButton(g2d, "Zebra", WIDTH / 2, currentY, 120, 40, () -> player2ShipPattern = Ship.Pattern.ZEBRA);
            drawButton(g2d, "Dotted", WIDTH / 2 + 150, currentY, 120, 40, () -> player2ShipPattern = Ship.Pattern.DOTTED);

            currentY += 40;
            drawCenteredString(g2d, "Current: " + player2ShipPattern, new Font("Arial", Font.PLAIN, 16), currentY);

            currentY += 60;
            g2d.drawString("Player 2 Controls:", WIDTH / 2 - 100, currentY);

            currentY += 40;
            drawButton(g2d, "Shoot: " + KeyEvent.getKeyText(player2ShootKey), WIDTH / 2 - 100, currentY, 150, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Press key for Player 2 Shoot:");
                if (input != null && !input.isEmpty() && input.length() == 1) {
                    player2ShootKey = input.toUpperCase().charAt(0);
                }
            });
            drawButton(g2d, "Shield: " + KeyEvent.getKeyText(player2ShieldKey), WIDTH / 2 + 100, currentY, 150, 40, () -> { // Renamed
                String input = JOptionPane.showInputDialog(this, "Press key for Player 2 Shield:");
                if (input != null && !input.isEmpty() && input.length() == 1) {
                    player2ShieldKey = input.toUpperCase().charAt(0);
                }
            });

            currentY = HEIGHT - 80; // Reposition bottom buttons to be above the copyright
            drawButton(g2d, "Start Multiplayer Game", WIDTH / 2 + 100, currentY, 250, 60, () -> { // Adjusted position
                initGame();
                // Fewer initial asteroids for multiplayer, especially on easy
                int initialAsteroidsMultiplayer = 5;
                if (initialDifficulty <= 2) { // For Easy/Very Easy
                    initialAsteroidsMultiplayer = 3;
                } else if (initialDifficulty <= 5) { // For Normal
                    initialAsteroidsMultiplayer = 4;
                }
                spawnAsteroids(initialAsteroidsMultiplayer);
                state = GameState.MULTIPLAYER_PLAYING;
                gameStartTime = System.currentTimeMillis(); // Record game start time
            });
            drawButton(g2d, "Back to Main Menu", WIDTH / 2 - 150, currentY, 200, 40, () -> state = GameState.START); // Adjusted position

        } else if (state == GameState.MULTIPLAYER_DISPLAY_QRS) {
            drawCenteredString(g2d, "Multiplayer Controllers (Local Demo)", new Font("Arial", Font.BOLD, 40), HEIGHT / 8);
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            drawCenteredString(g2d, "In a real game, you would scan these QR codes on your phones.", new Font("Arial", Font.PLAIN, 18), HEIGHT / 8 + 40);
            drawCenteredString(g2d, "For this demo, players use the same keyboard with different keys:", new Font("Arial", Font.PLAIN, 18), HEIGHT / 8 + 65);


            // Player 1 QR Code and Controls
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 24));
            g2d.drawString(userName1 + "'s Controller", WIDTH / 4 - 100, HEIGHT / 3 + 20);
            drawQRCodePlaceholder(g2d, WIDTH / 4 - 50, HEIGHT / 3 + 50, 100, "Player 1 URL");
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.drawString("Controls: ARROW Keys", WIDTH / 4 - 100, HEIGHT / 3 + 170);
            g2d.drawString("Shoot: " + KeyEvent.getKeyText(player1ShootKey) + ", Shield: " + KeyEvent.getKeyText(player1ShieldKey), WIDTH / 4 - 100, HEIGHT / 3 + 195); // Renamed


            // Player 2 QR Code and Controls
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 24));
            g2d.drawString(userName2 + "'s Controller", 3 * WIDTH / 4 - 100, HEIGHT / 3 + 20);
            drawQRCodePlaceholder(g2d, 3 * WIDTH / 4 - 50, HEIGHT / 3 + 50, 100, "Player 2 URL");
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.drawString("Controls: W, A, D", 3 * WIDTH / 4 - 100, HEIGHT / 3 + 170);
            g2d.drawString("Shoot: " + KeyEvent.getKeyText(player2ShootKey) + ", Shield: " + KeyEvent.getKeyText(player2ShieldKey), 3 * WIDTH / 4 - 100, HEIGHT / 3 + 195); // Renamed

            drawButton(g2d, "Start Multiplayer Game", WIDTH / 2, HEIGHT - 100, 300, 60, () -> {
                initGame(); // Initialize common game elements for a new game session
                // Fewer initial asteroids for multiplayer quick play, especially on easy
                int initialAsteroidsMultiplayer = 5;
                if (initialDifficulty <= 2) { // For Easy/Very Easy
                    initialAsteroidsMultiplayer = 3;
                } else if (initialDifficulty <= 5) { // For Normal
                    initialAsteroidsMultiplayer = 4;
                }
                spawnAsteroids(initialAsteroidsMultiplayer);
                state = GameState.MULTIPLAYER_PLAYING;
                gameStartTime = System.currentTimeMillis(); // Record game start time
            });
            drawButton(g2d, "Back to Setup", WIDTH / 2, HEIGHT - 30, 200, 40, () -> state = GameState.MULTIPLAYER_SETUP_NAMES);

        } else if (state == GameState.PLAYING_SINGLE || state == GameState.MULTIPLAYER_PLAYING || state == GameState.ML_PLAYING) {
            ship1.draw(g2d);
            if (state == GameState.MULTIPLAYER_PLAYING) {
                ship2.draw(g2d);
            }

            for (Bullet b : bullets) b.draw(g2d);
            
            // Apply Freeze background effect
            if (System.currentTimeMillis() < freezeEndTime) {
                // Changed freeze effect to a more distinct light blue
                g2d.setColor(new Color(100, 150, 255, 100)); // Light blue translucent
                g2d.fillRect(0, 0, WIDTH, HEIGHT);
            }

            for (Asteroid a : asteroids) a.draw(g2d);
            
            // Draw power-ups
            for (PowerUp p : powerUps) p.draw(g2d);


            // Draw AtomBOOM animation
            if (atomBoomActive) {
                long elapsedTime = System.currentTimeMillis() - atomBoomStartTime;
                double progress = (double) elapsedTime / ATOM_BOOM_ANIMATION_DURATION; // 0 to 1
                if (progress > 1.0) progress = 1.0;

                int maxRadius = (int) Math.sqrt(WIDTH * WIDTH + HEIGHT * HEIGHT); // Covers entire screen
                int currentRadius = (int) (maxRadius * progress);

                // Start green and fade out
                int alpha = (int) (200 * (1.0 - progress)); // From opaque to transparent
                if (alpha < 0) alpha = 0;
                g2d.setColor(new Color(0, 255, 0, alpha)); // Light green translucent

                g2d.fillOval((int) atomBoomCenterX - currentRadius, (int) atomBoomCenterY - currentRadius,
                             currentRadius * 2, currentRadius * 2);
            }


            for (PopEffect p : effects) p.draw(g2d);

            // UI elements for Player 1 (top left)
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.drawString("Score: " + score, 10, 20);
            if (state != GameState.ML_PLAYING) { // Only show lives for human player
                g2d.drawString("Lives: " + lives1, 10, 40);
            } else { // For ML mode, show AI status
                g2d.drawString("Jarvis Lives: " + lives1, 10, 40);
                g2d.drawString("Jarvis Version: " + aiStats.getFormattedVersion(), 10, 60);
            }


            // UI elements for Player 2 (top right) in multiplayer mode
            if (state == GameState.MULTIPLAYER_PLAYING) {
                g2d.drawString("Lives (" + userName2 + "): " + lives2, WIDTH - 150, 40);
            }

            // Positioned at bottom middle for both players
            int uiBottomY = HEIGHT - 20; // Slightly above bottom edge
            int barWidth = 100;
            int barHeight = 10;
            int labelOffset = 45; // Distance from bottom for labels
            int barOffset = 30;   // Distance from bottom for bars

            int player1SectionWidth = barWidth * 2 + 50; // Width for shield bar + bullet bar + gap
            int player2SectionWidth = barWidth * 2 + 50;
            int totalUIWidth = player1SectionWidth;
            if (state == GameState.MULTIPLAYER_PLAYING) {
                totalUIWidth += player2SectionWidth + 80; // Add space between player sections
            }
            int startX = (WIDTH - totalUIWidth) / 2;

            int shield1X = startX; // Renamed
            int bullets1X = shield1X + barWidth + 50;

            // Draw Shield bar for Player 1 (Renamed)
            g2d.setColor(Color.WHITE);
            g2d.drawString("Shield:", shield1X, uiBottomY - labelOffset); // Removed player name

            double shieldProgress1; // Renamed
            if (shieldActive1) { // Renamed
                shieldProgress1 = 1.0 - (double)(System.currentTimeMillis() - shieldActivationTime1) / SHIELD_ACTIVE_DURATION; // Renamed
            } else {
                long timeToRefill = shieldRefillTime1 - System.currentTimeMillis(); // Renamed
                if (timeToRefill <= 0) {
                    shieldProgress1 = 1.0; // Full
                } else {
                    shieldProgress1 = 1.0 - (double)timeToRefill / SHIELD_RECHARGE_DURATION; // Renamed
                }
            }
            if (shieldProgress1 < 0) shieldProgress1 = 0;
            if (shieldProgress1 > 1) shieldProgress1 = 1;

            // Draw background of shield bar
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(shield1X, uiBottomY - barOffset, barWidth, barHeight);

            // Draw filled portion of shield bar ("flowing water")
            g2d.setColor(Color.CYAN); // Changed color for shield fill
            g2d.fillRect(shield1X, uiBottomY - barOffset, (int)(barWidth * shieldProgress1), barHeight);
            g2d.setColor(Color.YELLOW); // Draw border over filled part
            g2d.drawRect(shield1X, uiBottomY - barOffset, barWidth, barHeight);

            // Draw refuel message if recharging for Player 1
            if (!shieldActive1 && System.currentTimeMillis() < shieldRefillTime1) { // Renamed
                g2d.setColor(Color.ORANGE); // Different color for refuel message
                String msg = "Recharging...";
                FontMetrics fm = g2d.getFontMetrics();
                int msgX = shield1X + (barWidth - fm.stringWidth(msg)) / 2;
                int msgY = uiBottomY - barOffset + barHeight / 2 + fm.getAscent() / 2; // Centered vertically in bar
                g2d.drawString(msg, msgX, msgY);
            }


            // Draw Bullets display for Player 1
            g2d.setColor(Color.WHITE);
            g2d.drawString("Bullets: " + currentBullets1 + "/" + MAX_BULLETS, bullets1X, uiBottomY - labelOffset); // Removed player name

            // Draw background of bullet bar
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(bullets1X, uiBottomY - barOffset, barWidth, barHeight);

            // Draw filled portion of bullet bar (reload progress: 1/5 at a time)
            double bulletReloadProgress = (double)currentBullets1 / MAX_BULLETS;
            g2d.setColor(Color.GREEN.darker()); // Different color for bullet fill
            g2d.fillRect(bullets1X, uiBottomY - barOffset, (int)(barWidth * bulletReloadProgress), barHeight);
            g2d.setColor(Color.YELLOW); // Draw border over filled part
            g2d.drawRect(bullets1X, uiBottomY - barOffset, barWidth, barHeight);

            // Draw reload message if reloading for Player 1
            if (reloading1) {
                g2d.setColor(Color.RED); // Different color for reload message
                String msg = "Reloading (Full)...";
                FontMetrics fm = g2d.getFontMetrics();
                int msgX = bullets1X + (barWidth - fm.stringWidth(msg)) / 2;
                int msgY = uiBottomY - barOffset + barHeight / 2 + fm.getAscent() / 2; // Centered vertically in bar
                g2d.drawString(msg, msgX, msgY);
            }
            // Removed the incremental reload message (purple message) for Player 1


            // UI for Player 2 in multiplayer mode
            if (state == GameState.MULTIPLAYER_PLAYING) {
                int shield2X = bullets1X + barWidth + 80; // Adjusted spacing between player sections (Renamed)
                int bullets2X = shield2X + barWidth + 50;

                // Draw Shield bar for Player 2 (Renamed)
                g2d.setColor(Color.WHITE);
                g2d.drawString("Shield:", shield2X, uiBottomY - labelOffset); // Removed player name
                double shieldProgress2; // Renamed
                if (shieldActive2) { // Renamed
                    shieldProgress2 = 1.0 - (double)(System.currentTimeMillis() - shieldActivationTime2) / SHIELD_ACTIVE_DURATION; // Renamed
                } else {
                    long timeToRefill = shieldRefillTime2 - System.currentTimeMillis(); // Renamed
                    if (timeToRefill <= 0) {
                        shieldProgress2 = 1.0;
                    } else {
                        shieldProgress2 = 1.0 - (double)timeToRefill / SHIELD_RECHARGE_DURATION; // Renamed
                    }
                }
                if (shieldProgress2 < 0) shieldProgress2 = 0;
                if (shieldProgress2 > 1) shieldProgress2 = 1;

                g2d.setColor(Color.DARK_GRAY);
                g2d.fillRect(shield2X, uiBottomY - barOffset, barWidth, barHeight);

                g2d.setColor(Color.CYAN);
                g2d.fillRect(shield2X, uiBottomY - barOffset, (int)(barWidth * shieldProgress2), barHeight);
                g2d.setColor(Color.YELLOW);
                g2d.drawRect(shield2X, uiBottomY - barOffset, barWidth, barHeight);

                // Draw refuel message if recharging for Player 2
                if (!shieldActive2 && System.currentTimeMillis() < shieldRefillTime2) { // Renamed
                    g2d.setColor(Color.ORANGE);
                    String msg = "Recharging...";
                    FontMetrics fm = g2d.getFontMetrics();
                    int msgX = shield2X + (barWidth - fm.stringWidth(msg)) / 2;
                    int msgY = uiBottomY - barOffset + barHeight / 2 + fm.getAscent() / 2;
                    g2d.drawString(msg, msgX, msgY);
                }

                // Draw Bullets display for Player 2
                g2d.setColor(Color.WHITE);
                g2d.drawString("Bullets: " + currentBullets2 + "/" + MAX_BULLETS, bullets2X, uiBottomY - labelOffset); // Removed player name

                g2d.setColor(Color.DARK_GRAY);
                g2d.fillRect(bullets2X, uiBottomY - barOffset, barWidth, barHeight);

                double bulletReloadProgress2 = (double)currentBullets2 / MAX_BULLETS;
                g2d.setColor(Color.GREEN.darker());
                g2d.fillRect(bullets2X, uiBottomY - barOffset, (int)(barWidth * bulletReloadProgress2), barHeight);
                g2d.setColor(Color.YELLOW);
                g2d.drawRect(bullets2X, uiBottomY - barOffset, barWidth, barHeight);

                // Draw reload message if reloading for Player 2
                if (reloading2) {
                    g2d.setColor(Color.RED);
                    String msg = "Reloading (Full)...";
                    FontMetrics fm = g2d.getFontMetrics();
                    int msgX = bullets2X + (barWidth - fm.stringWidth(msg)) / 2;
                    int msgY = uiBottomY - barOffset + barHeight / 2 + fm.getAscent() / 2;
                    g2d.drawString(msg, msgX, msgY);
                }
                // Removed the incremental reload message (purple message) for Player 2
            }


        } else if (state == GameState.GAME_OVER) {
            drawCenteredString(g2d, "GAME OVER", new Font("Arial", Font.BOLD, 48), HEIGHT / 3);
            drawCenteredString(g2d, "Final Score: " + score, new Font("Arial", Font.PLAIN, 24), HEIGHT / 2);

            // High Score display on Game Over screen - now only top 3
            g2d.setFont(new Font("Arial", Font.PLAIN, 24));
            if (!highScores.isEmpty()) {
                drawCenteredString(g2d, "High Scores:", new Font("Arial", Font.BOLD, 28), HEIGHT / 2 + 50);
                int scoreY = HEIGHT / 2 + 80;
                for (int i = 0; i < Math.min(highScores.size(), 3); i++) { // Display top 3 scores
                    HighScoreEntry entry = highScores.get(i);
                    String scoreText = entry.getUserName() + ": " + entry.getScore() + " (D:" + entry.getDifficulty() + ")";
                    if (entry.isAI()) {
                        scoreText += " [AI v" + entry.getAiVersion() + "]";
                    }
                    drawCenteredString(g2d, (i + 1) + ". " + scoreText,
                                       new Font("Arial", Font.PLAIN, 20), scoreY + (i * 25));
                }
            }
            
            // Adjusted button positions to avoid overlay on Game Over screen
            int buttonRow1Y = HEIGHT - 180; // First row of buttons
            int buttonRow2Y = HEIGHT - 120; // Second row of buttons
            int buttonWidth = 250;
            int buttonHeight = 40;
            int buttonSpacing = 20; // Horizontal spacing between buttons

            // Row 1 buttons
            drawButton(g2d, "View All Scores", WIDTH / 2 - (buttonWidth + buttonSpacing), buttonRow1Y, buttonWidth, buttonHeight, this::showAllHighScores);
            drawButton(g2d, "Game Statistics", WIDTH / 2, buttonRow1Y, buttonWidth, buttonHeight, this::showGameStatistics);
            drawButton(g2d, "Back to Main Menu", WIDTH / 2 + (buttonWidth + buttonSpacing), buttonRow1Y, buttonWidth, buttonHeight, () -> {
                initGame(); // Re-initialize state, but don't spawn asteroids
                state = GameState.START;
            });

            // Row 2 button
            drawButton(g2d, "View Visual Statistics", WIDTH / 2, buttonRow2Y, buttonWidth, buttonHeight, () -> {
                // Pass relevant statistics to the new dialog
                StatisticsDialog statsDialog = new StatisticsDialog(
                    (JFrame) SwingUtilities.getWindowAncestor(this), // Parent frame
                    bulletsFired,
                    asteroidsDestroyed,
                    totalShieldActivations1,
                    totalShieldActivations2,
                    lastPlayedState == GameState.ML_PLAYING, // Indicates if AI was playing
                    aiStats,
                    state == GameState.MULTIPLAYER_PLAYING
                );
                statsDialog.setVisible(true);
            });


            g2d.setFont(new Font("Arial", Font.PLAIN, 24));
            drawCenteredString(g2d, "Press ENTER to Restart", new Font("Arial", Font.PLAIN, 24), HEIGHT - 50);
        }

        // Draw the close button (always visible)
        drawCloseButton(g2d);


        // Restore the original transform so subsequent painting (if any) is not affected
        g2d.setTransform(originalTransform);
    }

    /**
     * Helper method to draw centered strings relative to the game's internal WIDTH.
     * @param g2d The Graphics2D object.
     * @param text The text to draw.
     * @param font The font to use for drawing.
     * @param y The y-coordinate for the baseline of the text (relative to game's internal height).
     */
    private void drawCenteredString(Graphics2D g2d, String text, Font font, int y) {
        g2d.setFont(font); // Set the font
        FontMetrics metrics = g2d.getFontMetrics(font); // Get metrics for the specific font
        int x = (WIDTH - metrics.stringWidth(text)) / 2; // Calculate x to center in the game WIDTH
        g2d.setColor(Color.WHITE); // Ensure color is set
        g2d.drawString(text, x, y);
    }

    /**
     * Draws a rectangular button with text, and handles click action.
     * Note: Mouse listener for this button is handled generically in mouseClicked.
     * @param g2d Graphics2D context.
     * @param text Text to display on the button.
     * @param centerX Center X coordinate of the button.
     * @param centerY Center Y coordinate of the button.
     * @param width Button width.
     * @param height Button height.
     * @param action Action to perform when button is clicked.
     */
    private void drawButton(Graphics2D g2d, String text, int centerX, int centerY, int width, int height, Runnable action) {
        int x = centerX - width / 2;
        int y = centerY - height / 2;

        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRoundRect(x, y, width, height, 15, 15); // Rounded rectangle
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawRoundRect(x, y, width, height, 15, 15);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, (int)(20 * 0.8))); // Reduced font size with button
        FontMetrics metrics = g2d.getFontMetrics();
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent();
        g2d.drawString(text, textX, textY);

        // Store button bounds and action for mouse click handling
        buttonActions.put(new Rectangle(x, y, width, height), action);
    }

    /**
     * Draws a simple 'X' button for closing the application in the top-right corner
     * of the *scaled* game area.
     * @param g2d The Graphics2D object.
     */
    private void drawCloseButton(Graphics2D g2d) {
        int buttonSize = 30; // Size of the square button area
        int padding = 10;    // Padding from the edges

        // Calculate position relative to the internal game dimensions
        int buttonX = WIDTH - buttonSize - padding;
        int buttonY = padding;

        g2d.setColor(Color.RED.darker()); // Darker red background for the button
        g2d.fillRect(buttonX, buttonY, buttonSize, buttonSize);
        g2d.setColor(Color.RED);
        g2d.drawRect(buttonX, buttonY, buttonSize, buttonSize);

        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2)); // Thicker lines for the X
        g2d.drawLine(buttonX + padding / 2, buttonY + padding / 2,
                     buttonX + buttonSize - padding / 2, buttonY + buttonSize - padding / 2);
        g2d.drawLine(buttonX + buttonSize - padding / 2, buttonY + padding / 2,
                     buttonX + padding / 2, buttonY + buttonSize - padding / 2);
        g2d.setStroke(new BasicStroke(1)); // Reset stroke

        // Store close button bounds and action
        buttonActions.put(new Rectangle(buttonX, buttonY, buttonSize, buttonSize), () -> System.exit(0));
    }

    /**
     * Draws a placeholder for a QR code.
     * @param g2d Graphics2D context.
     * @param x X coordinate.
     * @param y Y coordinate.
     * @param size Size of the QR code square.
     * @param text Optional text to display inside.
     */
    private void drawQRCodePlaceholder(Graphics2D g2d, int x, int y, int size, String text) {
        g2d.setColor(Color.WHITE);
        g2d.fillRect(x, y, size, size);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(x, y, size, size);

        // Draw some random patterns to simulate QR code complexity
        for (int i = 0; i < 5; i++) {
            g2d.fillRect(x + random.nextInt(size - 10), y + random.nextInt(size - 10), 5 + random.nextInt(5), 5 + random.nextInt(5));
        }

        // Add text "Scan Me!" or placeholder URL
        g2d.setColor(Color.RED); // Make text stand out
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics metrics = g2d.getFontMetrics();
        int textX = x + (size - metrics.stringWidth("Scan Me!")) / 2;
        int textY = y + (size / 2) + (metrics.getAscent() / 2);
        g2d.drawString("Scan Me!", textX, textY);
        if (text != null && !text.isEmpty()) {
            g2d.drawString(text, x + (size - metrics.stringWidth(text)) / 2, y + size - 5);
        }
    }

    /**
     * Displays a JOptionPane for the user to select a difficulty level.
     */
    private void showDifficultySelection() {
        String[] difficulties = {"1 (Very Easy)", "2 (Easy)", "3", "4", "5 (Normal)", "6", "7", "8", "9", "10 (Hard)"};
        // Pre-select the current difficulty level
        int initialSelection = initialDifficulty - 1;
        if (initialSelection < 0) initialSelection = 0;
        if (initialSelection >= difficulties.length) initialSelection = difficulties.length - 1;

        String selectedValue = (String) JOptionPane.showInputDialog(
                this,
                "Select Difficulty Level:",
                "Difficulty Selection",
                JOptionPane.QUESTION_MESSAGE,
                null,
                difficulties,
                difficulties[initialSelection]
        );

        if (selectedValue != null) {
            try {
                // Extract the number from the selected string (e.g., "1 (Very Easy)" -> "1")
                initialDifficulty = Integer.parseInt(selectedValue.split(" ")[0]);
                // Ensure difficulty is within bounds
                if (initialDifficulty < 1) initialDifficulty = 1;
                if (initialDifficulty > 10) initialDifficulty = 10;
            } catch (NumberFormatException ex) {
                System.err.println("Invalid difficulty selection: " + selectedValue);
                // Keep the current difficulty if parsing fails
            }
        }
    }


    /**
     * This method is called repeatedly by the Swing Timer to update game logic.
     * @param e The ActionEvent from the timer.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.currentTimeMillis();

        if (state == GameState.PLAYING_SINGLE || state == GameState.MULTIPLAYER_PLAYING || state == GameState.ML_PLAYING) {
            // --- Player 1 (Human or AI) Logic ---
            // Process AI input for Player 1 if in ML_PLAYING state
            if (state == GameState.ML_PLAYING) {
                ShipAI.AIAction aiAction = shipAI.determineAction(ship1, asteroids, bullets, currentBullets1, reloading1, shieldActive1, now, lastShotTime1, shieldRefillTime1, SHIELD_ACTIVE_DURATION, BULLET_COOLDOWN); // Renamed shieldActive1
                up1 = aiAction.thrust;
                left1 = aiAction.turnLeft;
                right1 = aiAction.turnRight;

                if (aiAction.shoot && !player1ShootKeyHeld) {
                    shootRequested1 = true;
                    player1ShootKeyHeld = true;
                } else if (!aiAction.shoot) {
                    player1ShootKeyHeld = false;
                }

                if (aiAction.shield && !player1ShieldKeyHeld) { // Renamed aiAction.hyperspace to aiAction.shield
                    shieldRequested1 = true; // Request shield for the next tick (Renamed)
                    player1ShieldKeyHeld = true; // Mark key as held (Renamed)
                } else if (!aiAction.shield) { // Renamed aiAction.hyperspace
                    player1ShieldKeyHeld = false; // Renamed player1HyperspaceKeyHeld
                }
            }

            // Passive reload for Player 1 (applies to both Human and AI)
            if (!reloading1 && currentBullets1 < MAX_BULLETS) {
                if (now - lastReloadTickTime1 >= RELOAD_INCREMENTAL_DURATION) {
                    currentBullets1++;
                    lastReloadTickTime1 = now;
                }
            }

            // Full reload (when empty) for Player 1 (applies to both Human and AI)
            if (reloading1) {
                if (now - reloadStartTime1 > RELOAD_FULL_DURATION) {
                    reloading1 = false;
                    currentBullets1 = MAX_BULLETS; // Reload magazine
                    lastReloadTickTime1 = now; // Reset passive reload timer too
                }
            }

            // Shooting logic for Player 1 (triggered by single press, applies to both Human and AI)
            if (shootRequested1) {
                if (!reloading1 && currentBullets1 > 0 && (now - lastShotTime1 > BULLET_COOLDOWN)) {
                    bullets.add(new Bullet(ship1));
                    currentBullets1--;
                    bulletsFired++; // Increment bullets fired
                    lastShotTime1 = now;
                    if (currentBullets1 == 0) {
                        reloading1 = true;
                        reloadStartTime1 = now;
                    }
                }
                shootRequested1 = false; // Consume the request
            }

            // Shield logic for Player 1 (triggered by single press, formerly Hyperspace)
            if (shieldRequested1) { // Renamed hyperspaceRequested1
                if (!shieldActive1 && now >= shieldRefillTime1) { // Renamed hyperSpeedActive1, hyperFuelRefillTime1
                    shieldActive1 = true; // Renamed hyperSpeedActive1
                    shieldActivationTime1 = now; // Renamed hyperspaceActivationTime1
                    ship1.setInvincible();
                    totalShieldActivations1++; // Increment shield activations (Renamed)
                }
                shieldRequested1 = false; // Consume the request (Renamed)
            }

            // Deactivate shield after duration for Player 1 (formerly Hyperspace)
            if (shieldActive1 && now - shieldActivationTime1 > SHIELD_ACTIVE_DURATION) { // Renamed hyperSpeedActive1, hyperspaceActivationTime1, HYPER_ACTIVE_DURATION
                shieldActive1 = false; // Renamed hyperSpeedActive1
                shieldRefillTime1 = now + SHIELD_RECHARGE_DURATION; // Start recharge cooldown (Renamed)
                totalShieldDuration1 += SHIELD_ACTIVE_DURATION; // Add duration to total (Renamed)
            }
            
            // Update ship 1
            // Pass ship speed reduction factor if shield is active
            ship1.update(up1, left1, right1, shieldActive1, SHIP_SPEED_REDUCTION_FACTOR, WIDTH, HEIGHT);

            // --- Player 2 Logic (if multiplayer) ---
            if (state == GameState.MULTIPLAYER_PLAYING) {
                // Passive reload for Player 2
                if (!reloading2 && currentBullets2 < MAX_BULLETS) {
                    if (now - lastReloadTickTime2 >= RELOAD_INCREMENTAL_DURATION) {
                        currentBullets2++;
                        lastReloadTickTime2 = now;
                    }
                }

                // Full reload (when empty) for Player 2
                if (reloading2) {
                    if (now - reloadStartTime2 > RELOAD_FULL_DURATION) {
                        reloading2 = false;
                        currentBullets2 = MAX_BULLETS;
                        lastReloadTickTime2 = now;
                    }
                }

                // Shooting logic for Player 2 (triggered by single press)
                if (shootRequested2) {
                    if (!reloading2 && currentBullets2 > 0 && (now - lastShotTime2 > BULLET_COOLDOWN)) {
                        bullets.add(new Bullet(ship2));
                        currentBullets2--;
                        bulletsFired++; // Increment bullets fired
                        lastShotTime2 = now;
                        if (currentBullets2 == 0) {
                            reloading2 = true;
                            reloadStartTime2 = now;
                        }
                    }
                    shootRequested2 = false; // Consume the request
                }

                // Shield logic for Player 2 (formerly Hyperspace)
                if (shieldRequested2) { // Renamed hyperspaceRequested2
                    if (!shieldActive2 && now >= shieldRefillTime2) { // Renamed hyperSpeedActive2, hyperFuelRefillTime2
                        shieldActive2 = true; // Renamed hyperSpeedActive2
                        shieldActivationTime2 = now; // Renamed hyperspaceActivationTime2
                        ship2.setInvincible();
                        totalShieldActivations2++; // Increment shield activations (Renamed)
                    }
                    shieldRequested2 = false; // Consume the request (Renamed)
                }

                // Deactivate shield after duration for Player 2 (formerly Hyperspace)
                if (shieldActive2 && now - shieldActivationTime2 > SHIELD_ACTIVE_DURATION) { // Renamed hyperSpeedActive2, hyperspaceActivationTime1, HYPER_ACTIVE_DURATION
                    shieldActive2 = false; // Renamed hyperSpeedActive2
                    shieldRefillTime2 = now + SHIELD_RECHARGE_DURATION; // Renamed hyperFuelRefillTime2, HYPER_RECHARGE_DURATION
                    totalShieldDuration2 += SHIELD_ACTIVE_DURATION; // Add duration to total (Renamed)
                }

                // Update ship 2
                // Pass ship speed reduction factor if shield is active
                ship2.update(up2, left2, right2, shieldActive2, SHIP_SPEED_REDUCTION_FACTOR, WIDTH, HEIGHT);
            }


            bullets.forEach(b -> b.update(WIDTH, HEIGHT));
            
            // Determine asteroid speed multiplier based on shield active status OR Freeze
            double currentAsteroidSpeedMultiplier = 1.0; // Default to normal speed
            if (shieldActive1 || shieldActive2) { // Check both players for slowdown effect
                currentAsteroidSpeedMultiplier = ASTEROID_SLOWDOWN_MULTIPLIER;
            }
            if (now < freezeEndTime) { // If Freeze is active, override speed to 0 (or very close to 0)
                currentAsteroidSpeedMultiplier = 0.0;
            }
            // Update asteroids with the current speed multiplier
            for (Asteroid a : asteroids) {
                a.update(currentAsteroidSpeedMultiplier);
            }

            effects.forEach(PopEffect::update);

            // Update Power-ups: Check collisions and lifetime
            Iterator<PowerUp> powerUpIterator = powerUps.iterator();
            while (powerUpIterator.hasNext()) {
                PowerUp powerUp = powerUpIterator.next();
                // Check for lifetime expiration
                if (now - powerUp.creationTime > POWERUP_LIFETIME) {
                    powerUpIterator.remove(); // Remove if lifetime expired
                    continue; // Skip collision check for expired power-up
                }

                if (powerUp.getBounds().intersects(ship1.getBounds())) {
                    applyPowerUpEffect(powerUp.type, ship1); // Apply effect for Player 1
                    powerUpIterator.remove(); // Remove power-up after collection
                } else if (state == GameState.MULTIPLAYER_PLAYING && powerUp.getBounds().intersects(ship2.getBounds())) {
                    applyPowerUpEffect(powerUp.type, ship2); // Apply effect for Player 2
                    powerUpIterator.remove(); // Remove power-up after collection
                }
            }

            // AtomBOOM collision detection during its active animation
            if (atomBoomActive) {
                long elapsedTime = now - atomBoomStartTime;
                double progress = (double) elapsedTime / ATOM_BOOM_ANIMATION_DURATION;
                if (progress > 1.0) {
                    atomBoomActive = false; // End the animation once duration is over
                } else {
                    int maxRadius = (int) Math.sqrt(WIDTH * WIDTH + HEIGHT * HEIGHT);
                    int currentRadius = (int) (maxRadius * progress);
                    
                    // Create a circular bounds for the current blast radius
                    Ellipse2D currentBlastBounds = new Ellipse2D.Double(
                        atomBoomCenterX - currentRadius,
                        atomBoomCenterY - currentRadius,
                        currentRadius * 2,
                        currentRadius * 2
                    );

                    Iterator<Asteroid> asteroidBlastIterator = asteroids.iterator();
                    while (asteroidBlastIterator.hasNext()) {
                        Asteroid asteroid = asteroidBlastIterator.next();
                        // Check if the asteroid's bounds intersect with the blast radius
                        if (currentBlastBounds.intersects(asteroid.getBounds())) {
                            effects.add(new PopEffect(asteroid.x, asteroid.y, random)); // Add pop effect
                            asteroidBlastIterator.remove(); // Remove asteroid
                        }
                    }
                }
            }


            if (state == GameState.PLAYING_SINGLE) {
                checkCollisionsSinglePlayer();
            } else if (state == GameState.MULTIPLAYER_PLAYING) {
                checkCollisionsMultiplayer();
            } else { // ML_PLAYING
                checkCollisionsMLPlayer();
            }
            removeOffscreen();

            // Check and update difficulty level based on score
            // The score-based difficulty progression applies *on top of* the initialDifficulty
            // Example: if initialDifficulty is 1, and score is 50, dynamic_level becomes 1 + (50/50) = 2.
            // If initialDifficulty is 5, and score is 50, dynamic_level becomes 5 + (50/50) = 6.
            int dynamicDifficultyIncrease = score / 50; // Every 50 points, increase dynamic difficulty by 1
            int newDifficulty = initialDifficulty + dynamicDifficultyIncrease;

            // Cap the dynamic difficulty level if it exceeds a reasonable maximum
            if (newDifficulty > 20) newDifficulty = 20; // Cap at 20 for extreme difficulty

            if (newDifficulty > difficultyLevel) {
                difficultyLevel = newDifficulty;
                System.out.println("Difficulty increased to level: " + difficultyLevel); // For debugging
            }


            // Asteroid spawning logic: more frequent with higher difficultyLevel
            // The base chance and scaling are adjusted for a smoother curve starting easier.
            // At difficulty 1, base chance is very low, making it easier.
            // At difficulty 1: random.nextInt(100) < (0 + 1*0.5) -> < 0.5 (very rare)
            // At difficulty 2: random.nextInt(100) < (0 + 2*0.5) -> < 1.0 (still rare)
            // At difficulty 5: random.nextInt(100) < (0 + 5*0.5) -> < 2.5
            // At difficulty 10: random.nextInt(100) < (0 + 10*0.5) -> < 5.0 (5% chance)
            int spawnChance = (int) (difficultyLevel * 0.5); // Smoother, lower chance for easy levels
            if (spawnChance < 1 && difficultyLevel >=1) spawnChance = 1; // Ensure at least a minimal chance at difficulty 1
            if (random.nextInt(100) < spawnChance) {
                asteroids.add(new Asteroid(random, difficultyLevel));
                totalAsteroidsSpawned++; // Count newly spawned asteroids
            }

            // Power-up spawning based on score
            if (score >= lastFreezeScoreTrigger + FREEZE_SCORE_INTERVAL) { // Ensure it triggers exactly at intervals
                powerUps.add(new PowerUp(PowerUp.Type.FREEZE, random, WIDTH, HEIGHT));
                lastFreezeScoreTrigger = score;
            }
            if (score >= lastAtomBoomScoreTrigger + ATOM_BOOM_SCORE_INTERVAL) {
                powerUps.add(new PowerUp(PowerUp.Type.ATOM_BOOM, random, WIDTH, HEIGHT));
                lastAtomBoomScoreTrigger = score;
            }


            // Check for game over (multiplayer condition)
            if (state == GameState.MULTIPLAYER_PLAYING && lives1 <= 0 && lives2 <= 0) {
                gameEndTime = now; // Record game end time
                lastPlayedState = state; // Store current state before GAME_OVER
                state = GameState.GAME_OVER;
                saveOrUpdateHighScore(userName1, score, initialDifficulty, false, null); // Human player
            } else if (state == GameState.PLAYING_SINGLE && lives1 <= 0) {
                gameEndTime = now; // Record game end time
                lastPlayedState = state; // Store current state before GAME_OVER
                state = GameState.GAME_OVER;
                saveOrUpdateHighScore(userName1, score, initialDifficulty, false, null); // Human player
            } else if (state == GameState.ML_PLAYING && lives1 <= 0) {
                gameEndTime = now; // Record game end time
                lastPlayedState = state; // Store current state before GAME_OVER
                state = GameState.GAME_OVER;
                // Check if this AI score is a new high score for this difficulty
                boolean newHighScoreAchieved = true;
                for (HighScoreEntry entry : highScores) {
                    if (entry.isAI() && entry.getDifficulty() == initialDifficulty && score <= entry.getScore()) {
                        newHighScoreAchieved = false;
                        break;
                    }
                }
                if (newHighScoreAchieved) {
                    aiStats.highScoreCount++;
                }
                saveAIStats(); // Always save AI stats on game over for ML mode
                saveOrUpdateHighScore("Jarvis", score, initialDifficulty, true, aiStats.getFormattedVersion()); // AI player name changed to Jarvis
            }


        } else if (state == GameState.START || state == GameState.MULTIPLAYER_SETUP_NAMES || state == GameState.MULTIPLAYER_DISPLAY_QRS) {
            // Update stars movement only on start screen and setup screens
            for (Star star : stars) {
                star.update();
            }
            // Add new stars if needed to maintain density
            if (stars.size() < MAX_STARS) {
                stars.add(new Star(random, WIDTH, HEIGHT));
            }
        }
        repaint(); // Request a repaint of the panel
        buttonActions.clear(); // Clear actions after each repaint
    }

    /**
     * Applies the effect of a collected power-up.
     * @param type The type of power-up.
     * @param collectingShip The ship that collected the power-up.
     */
    private void applyPowerUpEffect(PowerUp.Type type, Ship collectingShip) {
        long now = System.currentTimeMillis();
        switch (type) {
            case FREEZE:
                freezeEndTime = now + FREEZE_DURATION;
                System.out.println("Freeze activated!");
                break;
            case ATOM_BOOM:
                // Asteroids will be cleared gradually based on collision with expanding circle
                atomBoomActive = true;
                atomBoomStartTime = now;
                atomBoomCenterX = collectingShip.x;
                atomBoomCenterY = collectingShip.y;
                System.out.println("AtomBOOM activated!");
                break;
        }
    }


    /**
     * Handles key press events for player input.
     * @param e The KeyEvent generated.
     */
    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        // Key presses for both human players and AI in ML_PLAYING state
        if (state == GameState.PLAYING_SINGLE || state == GameState.MULTIPLAYER_PLAYING || state == GameState.ML_PLAYING) {
            // Player 1 Controls (Human or AI in ML_PLAYING)
            switch (keyCode) {
                case KeyEvent.VK_UP -> up1 = true;
                case KeyEvent.VK_LEFT -> left1 = true;
                case KeyEvent.VK_RIGHT -> right1 = true;
            }
            // Handle shoot key press for single shot
            if (keyCode == player1ShootKey) {
                if (!player1ShootKeyHeld) {
                    shootRequested1 = true; // Request a shot for the next tick
                    player1ShootKeyHeld = true; // Mark key as held
                }
            }
            // Handle shield key press for single activation
            if (keyCode == player1ShieldKey) { // Renamed player1HyperspaceKey
                if (!player1ShieldKeyHeld) { // Renamed player1HyperspaceKeyHeld
                    shieldRequested1 = true; // Request shield for the next tick (Renamed)
                    player1ShieldKeyHeld = true; // Mark key as held (Renamed)
                }
            }

            // Player 2 Controls (only in multiplayer)
            if (state == GameState.MULTIPLAYER_PLAYING) {
                switch (keyCode) {
                    case KeyEvent.VK_W -> up2 = true;
                    case KeyEvent.VK_A -> left2 = true;
                    case KeyEvent.VK_D -> right2 = true;
                }
                // Handle shoot key press for single shot
                if (keyCode == player2ShootKey) {
                    if (!player2ShootKeyHeld) {
                        shootRequested2 = true;
                        player2ShootKeyHeld = true;
                    }
                }
                // Handle shield key press for single activation
                if (keyCode == player2ShieldKey) { // Renamed player2HyperspaceKey
                    if (!player2ShieldKeyHeld) { // Renamed player2HyperspaceKeyHeld
                        shieldRequested2 = true; // Renamed
                        player2ShieldKeyHeld = true; // Renamed
                    }
                }
            }
        }

        // State transitions
        if (keyCode == KeyEvent.VK_ENTER) {
            if (state == GameState.GAME_OVER) {
                initGame(); // Re-initialize game state
                // Restart into the mode that was just played
                if (lastPlayedState == GameState.ML_PLAYING) {
                    lives1 = jarvisLivesInput; // Set AI lives back to configured value on restart
                    spawnAsteroids(7); // Restart ML mode with appropriate asteroids
                    state = GameState.ML_PLAYING;
                } else if (lastPlayedState == GameState.MULTIPLAYER_PLAYING) {
                    // Fewer initial asteroids for multiplayer quick play, especially on easy
                    int initialAsteroidsMultiplayer = 5;
                    if (initialDifficulty <= 2) { // For Easy/Very Easy
                        initialAsteroidsMultiplayer = 3;
                    } else if (initialDifficulty <= 5) { // For Normal
                        initialAsteroidsMultiplayer = 4;
                    }
                    spawnAsteroids(initialAsteroidsMultiplayer); // Restart multiplayer with appropriate asteroids
                    state = GameState.MULTIPLAYER_PLAYING;
                } else { // Default to single player if lastPlayedState is unknown or single player
                    // Fewer initial asteroids for single player, especially on easy
                    int initialAsteroidsSinglePlayer = 3;
                    if (initialDifficulty <= 2) { // For Easy/Very Easy
                        initialAsteroidsSinglePlayer = 1; // Start with just one asteroid
                    } else if (initialDifficulty <= 5) { // For Normal
                        initialAsteroidsSinglePlayer = 2;
                    }
                    spawnAsteroids(initialAsteroidsSinglePlayer);
                    state = GameState.PLAYING_SINGLE;
                }
                gameStartTime = System.currentTimeMillis(); // Record game start time on restart
            }
        }
    }

    /**
     * Handles key release events to stop actions.
     * @param e The KeyEvent generated.
     */
    @Override
    public void keyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        // Key releases for both human players and AI in ML_PLAYING state
        if (state == GameState.PLAYING_SINGLE || state == GameState.MULTIPLAYER_PLAYING || state == GameState.ML_PLAYING) {
            // Player 1 Controls
            switch (keyCode) {
                case KeyEvent.VK_UP -> up1 = false;
                case KeyEvent.VK_LEFT -> left1 = false;
                case KeyEvent.VK_RIGHT -> right1 = false;
            }
            // Reset key held flags on release
            if (keyCode == player1ShootKey) {
                player1ShootKeyHeld = false;
            }
            if (keyCode == player1ShieldKey) { // Renamed player1HyperspaceKey
                player1ShieldKeyHeld = false; // Renamed player1HyperspaceKeyHeld
            }

            // Player 2 Controls
            if (state == GameState.MULTIPLAYER_PLAYING) {
                switch (keyCode) {
                    case KeyEvent.VK_W -> up2 = false;
                    case KeyEvent.VK_A -> left2 = false;
                    case KeyEvent.VK_D -> right2 = false;
                }
                // Reset key held flags on release
                if (keyCode == player2ShootKey) {
                    player2ShootKeyHeld = false;
                }
                if (keyCode == player2ShieldKey) { // Renamed player2HyperspaceKey
                    player2ShieldKeyHeld = false; // Renamed player2HyperspaceKeyHeld
                }
            }
        }
    }

    /**
     * Not used for this game, but required by KeyListener interface.
     * @param e The KeyEvent generated.
     */
    @Override
    public void keyTyped(KeyEvent e) { /* Not used */ }

    /**
     * Checks for collisions in single-player mode.
     */
    void checkCollisionsSinglePlayer() {
        List<Asteroid> newAsteroids = new ArrayList<>();

        Iterator<Bullet> bulletIterator = bullets.iterator();
        while (bulletIterator.hasNext()) {
            Bullet bullet = bulletIterator.next();
            Iterator<Asteroid> asteroidIterator = asteroids.iterator();
            while (asteroidIterator.hasNext()) {
                Asteroid asteroid = asteroidIterator.next();
                if (bullet.getBounds().intersects(asteroid.getBounds())) {
                    bulletIterator.remove();
                    effects.add(new PopEffect(asteroid.x, asteroid.y, random));
                    // Score based on asteroid size
                    score += getScoreForAsteroid(asteroid.size);
                    asteroidsDestroyed++; // Increment asteroids destroyed
                    if (asteroid.size > Asteroid.MIN_SPLIT_SIZE) {
                        // Pass asteroid's original direction for recoil effect
                        newAsteroids.add(new Asteroid(asteroid.x, asteroid.y, asteroid.size / 2, random, difficultyLevel, asteroid.dx, asteroid.dy, Math.PI / 3));
                        newAsteroids.add(new Asteroid(asteroid.x, asteroid.y, asteroid.size / 2, random, difficultyLevel, asteroid.dx, asteroid.dy, -Math.PI / 3));
                        totalAsteroidsSpawned += 2; // Count two new split asteroids
                    }
                    asteroidIterator.remove();
                    break;
                }
            }
        }
        asteroids.addAll(newAsteroids);

        if (ship1.isInvincible()) return;

        for (Asteroid asteroid : asteroids) {
            if (asteroid.getBounds().intersects(ship1.getBounds())) {
                lives1--;
                effects.add(new PopEffect(ship1.x, ship1.y, random));
                if (lives1 <= 0) {
                    gameEndTime = System.currentTimeMillis(); // Record game end time
                    lastPlayedState = state; // Store current state before GAME_OVER
                    state = GameState.GAME_OVER;
                    saveOrUpdateHighScore(userName1, score, initialDifficulty, false, null);
                } else {
                    ship1.resetPosition(WIDTH / 2, HEIGHT / 2);
                    // Reset ammo and shield charge on hit/respawn for Player 1
                    currentBullets1 = MAX_BULLETS;
                    reloading1 = false;
                    shieldActive1 = false;
                    shieldRefillTime1 = System.currentTimeMillis(); // Shield immediately available
                }
                break;
            }
        }
    }

    /**
     * Checks for collisions in multiplayer mode.
     */
    void checkCollisionsMultiplayer() {
        List<Asteroid> newAsteroids = new ArrayList<>();

        Iterator<Bullet> bulletIterator = bullets.iterator();
        while (bulletIterator.hasNext()) {
            Bullet bullet = bulletIterator.next();
            Iterator<Asteroid> asteroidIterator = asteroids.iterator();
            while (asteroidIterator.hasNext()) {
                Asteroid asteroid = asteroidIterator.next();
                if (bullet.getBounds().intersects(asteroid.getBounds())) {
                    bulletIterator.remove();
                    effects.add(new PopEffect(asteroid.x, asteroid.y, random));
                    // Score based on asteroid size for multiplayer as well
                    score += getScoreForAsteroid(asteroid.size);
                    asteroidsDestroyed++; // Increment asteroids destroyed
                    if (asteroid.size > Asteroid.MIN_SPLIT_SIZE) {
                        // Pass asteroid's original direction for recoil effect
                        newAsteroids.add(new Asteroid(asteroid.x, asteroid.y, asteroid.size / 2, random, difficultyLevel, asteroid.dx, asteroid.dy, Math.PI / 3));
                        newAsteroids.add(new Asteroid(asteroid.x, asteroid.y, asteroid.size / 2, random, difficultyLevel, asteroid.dx, asteroid.dy, -Math.PI / 3));
                        totalAsteroidsSpawned += 2; // Count two new split asteroids
                    }
                    asteroidIterator.remove();
                    break;
                }
            }
        }
        asteroids.addAll(newAsteroids);

        // Ship 1 - Asteroid collisions
        if (!ship1.isInvincible()) {
            for (Asteroid asteroid : asteroids) {
                if (asteroid.getBounds().intersects(ship1.getBounds())) {
                    lives1--;
                    effects.add(new PopEffect(ship1.x, ship1.y, random));
                    if (lives1 <= 0) {
                        // Ship 1 destroyed, but game continues if ship 2 is alive
                    } else {
                        ship1.resetPosition(WIDTH / 4, HEIGHT / 2); // Respawn ship 1
                        // Reset ammo and shield charge on hit/respawn for Player 1
                        currentBullets1 = MAX_BULLETS;
                        reloading1 = false;
                        shieldActive1 = false;
                        shieldRefillTime1 = System.currentTimeMillis(); // Shield immediately available
                    }
                    // Do not remove asteroid here, as it might hit ship 2
                    break; // Ship can only collide with one asteroid at a time (for simplicity)
                }
            }
        }

        // Ship 2 - Asteroid collisions
        if (!ship2.isInvincible()) {
            for (Asteroid asteroid : asteroids) {
                if (asteroid.getBounds().intersects(ship2.getBounds())) {
                    lives2--;
                    effects.add(new PopEffect(ship2.x, ship2.y, random));
                    if (lives2 <= 0) {
                        // Ship 2 destroyed, but game continues if ship 1 is alive
                    } else {
                        ship2.resetPosition(3 * WIDTH / 4, HEIGHT / 2); // Respawn ship 2
                        // Reset ammo and shield charge on hit/respawn for Player 2
                        currentBullets2 = MAX_BULLETS;
                        reloading2 = false;
                        shieldActive2 = false;
                        shieldRefillTime2 = System.currentTimeMillis(); // Shield immediately available
                    }
                    // Do not remove asteroid here, as it might hit ship 1
                    break;
                }
            }
        }

        // Ship-to-ship collision (simple bounce, no damage)
        if (ship1.getBounds().intersects(ship2.getBounds())) {
            // Simple recoil/bounce effect for demonstration
            double tempDx1 = ship1.dx;
            double tempDy1 = ship1.dy;
            ship1.dx = ship2.dx * 0.5; // Transfer some momentum
            ship1.dy = ship2.dy * 0.5;
            ship2.dx = tempDx1 * 0.5;
            ship2.dy = tempDy1 * 0.5;
        }
    }

    /**
     * Checks for collisions in ML mode (AI controlled ship).
     */
    void checkCollisionsMLPlayer() {
        List<Asteroid> newAsteroids = new ArrayList<>();

        Iterator<Bullet> bulletIterator = bullets.iterator();
        while (bulletIterator.hasNext()) {
            Bullet bullet = bulletIterator.next();
            Iterator<Asteroid> asteroidIterator = asteroids.iterator();
            while (asteroidIterator.hasNext()) {
                Asteroid asteroid = asteroidIterator.next();
                if (bullet.getBounds().intersects(asteroid.getBounds())) {
                    bulletIterator.remove();
                    effects.add(new PopEffect(asteroid.x, asteroid.y, random));
                    score += getScoreForAsteroid(asteroid.size);
                    asteroidsDestroyed++; // Increment asteroids destroyed
                    // Provide positive feedback to AI when it hits an asteroid
                    shipAI.receiveFeedback(true, false);
                    if (asteroid.size > Asteroid.MIN_SPLIT_SIZE) {
                        // Pass asteroid's original direction for recoil effect
                        newAsteroids.add(new Asteroid(asteroid.x, asteroid.y, asteroid.size / 2, random, difficultyLevel, asteroid.dx, asteroid.dy, Math.PI / 3));
                        newAsteroids.add(new Asteroid(asteroid.x, asteroid.y, asteroid.size / 2, random, difficultyLevel, asteroid.dx, asteroid.dy, -Math.PI / 3));
                        totalAsteroidsSpawned += 2; // Count two new split asteroids
                    }
                    asteroidIterator.remove();
                    break;
                }
            }
        }
        asteroids.addAll(newAsteroids);

        if (ship1.isInvincible()) return;

        for (Asteroid asteroid : asteroids) {
            if (asteroid.getBounds().intersects(ship1.getBounds())) {
                lives1--;
                effects.add(new PopEffect(ship1.x, ship1.y, random));
                // Provide negative feedback to AI when its ship is hit by an asteroid
                shipAI.receiveFeedback(false, true);
                if (lives1 <= 0) {
                    gameEndTime = System.currentTimeMillis(); // Record game end time
                    lastPlayedState = state; // Store current state before GAME_OVER
                    state = GameState.GAME_OVER; // Will handle AI score saving in actionPerformed
                } else {
                    ship1.resetPosition(WIDTH / 2, HEIGHT / 2);
                    // Reset ammo and shield charge on hit/respawn for Player 1 (AI)
                    currentBullets1 = MAX_BULLETS;
                    reloading1 = false;
                    shieldActive1 = false;
                    shieldRefillTime1 = System.currentTimeMillis(); // Shield immediately available
                }
                break;
            }
        }
    }

    /**
     * Calculates the score awarded for destroying an asteroid based on its size.
     * Smaller asteroids give more points.
     * @param asteroidSize The size of the asteroid.
     * @return The score awarded.
     */
    private int getScoreForAsteroid(int asteroidSize) {
        if (asteroidSize <= Asteroid.MIN_SPLIT_SIZE) { // Smallest asteroids
            return 30;
        } else if (asteroidSize <= Asteroid.BASE_SIZE * 1.5) { // Medium asteroids
            return 20;
        } else { // Large asteroids
            return 10;
        }
    }


    void removeOffscreen() {
        bullets.removeIf(b -> !b.onScreen(WIDTH, HEIGHT));
        asteroids.removeIf(a -> !a.onScreen(WIDTH, HEIGHT));
        effects.removeIf(p -> p.life <= 0);
        // This line was already removing power-ups that drift off-screen,
        // but now we also need to consider their lifetime.
        // The lifetime check is now integrated directly in actionPerformed.
        // powerUps.removeIf(p -> !p.onScreen(WIDTH, HEIGHT)); 
    }

    /**
     * Saves or updates the high score for a given user/AI.
     * @param username The username/name of the player.
     * @param score The score to save.
     * @param difficulty The difficulty level at which the score was achieved.
     * @param isAI True if this is an AI score, false for human.
     * @param aiVersion The AI version string, if applicable (null otherwise).
     */
    private void saveOrUpdateHighScore(String username, int score, int difficulty, boolean isAI, String aiVersion) {
        boolean entryUpdated = false;
        for (HighScoreEntry entry : highScores) {
            // Check if user, difficulty, and AI status match for updating
            if (entry.getUserName().equals(username) && entry.getDifficulty() == difficulty && entry.isAI() == isAI) {
                if (score > entry.getScore()) {
                    entry.score = score; // Update score if higher
                    entry.aiVersion = aiVersion; // Update AI version if applicable
                }
                entryUpdated = true;
                break;
            }
        }
        if (!entryUpdated) {
            // Add new entry if no matching one was found
            highScores.add(new HighScoreEntry(username, score, difficulty, isAI, aiVersion));
        }
        // Sort by score (descending), then by difficulty (ascending for same score), then by AI status (human first)
        Collections.sort(highScores, (e1, e2) -> {
            int scoreCompare = Integer.compare(e2.getScore(), e1.getScore());
            if (scoreCompare != 0) return scoreCompare;
            int difficultyCompare = Integer.compare(e1.getDifficulty(), e2.getDifficulty()); // Higher difficulty means better for same score
            if (difficultyCompare != 0) return difficultyCompare;
            // Human scores before AI scores if all else is equal
            return Boolean.compare(e1.isAI(), e2.isAI());
        });

        // Keep only the top 20 scores (or whatever limit you want for saving)
        if (highScores.size() > 20) {
            highScores = highScores.subList(0, 20);
        }
        saveHighScores();
    }


    /**
     * Saves the current high score list to a file.
     */
    private void saveHighScores() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(HIGHSCORE_FILE))) {
            oos.writeObject(highScores);
        }
        catch (InvalidClassException e) {
            System.err.println("InvalidClassException when saving high scores. This might mean the HighScoreEntry class structure has changed. Deleting old scores file.");
            // Attempt to delete the corrupted file and then save new scores
            new File(HIGHSCORE_FILE).delete();
            try (ObjectOutputStream oos2 = new ObjectOutputStream(new FileOutputStream(HIGHSCORE_FILE))) {
                oos2.writeObject(highScores);
            } catch (IOException e2) {
                System.err.println("Error saving high scores after cleanup: " + e2.getMessage());
            }
        }
        catch (IOException e) {
            System.err.println("Error saving high scores: " + e.getMessage());
        }
    }

    /**
     * Loads the high score list from a file. If no file exists, initializes with an empty list.
     */
    @SuppressWarnings("unchecked") // Suppress unchecked cast warning for readObject
    private void loadHighScores() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(HIGHSCORE_FILE))) {
            highScores = (List<HighScoreEntry>) ois.readObject();
            // Ensure the loaded list is sorted, just in case
            Collections.sort(highScores, (e1, e2) -> {
                int scoreCompare = Integer.compare(e2.getScore(), e1.getScore());
                if (scoreCompare != 0) return scoreCompare;
                int difficultyCompare = Integer.compare(e1.getDifficulty(), e2.getDifficulty());
                if (difficultyCompare != 0) return difficultyCompare;
                return Boolean.compare(e1.isAI(), e2.isAI());
            });
        } catch (FileNotFoundException e) {
            System.out.println("High score file not found. Starting with an empty list.");
            highScores = new ArrayList<>(); // Initialize empty list
        } catch (InvalidClassException e) {
            System.err.println("InvalidClassException when loading high scores. This might mean the HighScoreEntry class structure has changed. Deleting old scores file and starting new.");
            // If the class structure changed, the old file is unreadable. Delete it.
            new File(HIGHSCORE_FILE).delete();
            highScores = new ArrayList<>();
        }
        catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading high scores: " + e.getMessage());
            highScores = new ArrayList<>(); // Fallback to empty list
        }
    }

    /**
     * Loads AI statistics from a file. If no file exists, initializes new stats.
     */
    private void loadAIStats() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(AI_STATS_FILE))) {
            aiStats = (AIStats) ois.readObject();
        } catch (FileNotFoundException e) {
            System.out.println("AI stats file not found. Initializing new AI stats (v1.0.0).");
            aiStats = new AIStats(); // New stats, starts at 1.0.0
        } catch (InvalidClassException e) {
            System.err.println("InvalidClassException when loading AI stats. AIStats class structure might have changed. Deleting old stats file and starting new.");
            new File(AI_STATS_FILE).delete();
            aiStats = new AIStats();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading AI stats: " + e.getMessage());
            aiStats = new AIStats(); // Fallback to new stats
        }
    }

    /**
     * Saves AI statistics to a file.
     */
    private void saveAIStats() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(AI_STATS_FILE))) {
            oos.writeObject(aiStats);
        } catch (IOException e) {
            System.err.println("Error saving AI stats: " + e.getMessage());
        }
    }


    /**
     * Displays a popup window showing all high scores, with an option to filter by difficulty and player type.
     */
    private void showAllHighScores() {
        String[] difficultyOptions = new String[11];
        difficultyOptions[0] = "All Difficulties";
        for (int i = 1; i <= 10; i++) {
            difficultyOptions[i] = String.valueOf(i);
        }

        String selectedDifficultyStr = (String) JOptionPane.showInputDialog(
                this,
                "Select Difficulty to View:",
                "Filter High Scores",
                JOptionPane.QUESTION_MESSAGE,
                null,
                difficultyOptions,
                difficultyOptions[0] // Default to "All Difficulties"
        );

        if (selectedDifficultyStr == null) return; // User cancelled

        int filterDifficulty = -1; // -1 means no filter (show all)
        if (!selectedDifficultyStr.equals("All Difficulties")) {
            try {
                filterDifficulty = Integer.parseInt(selectedDifficultyStr);
            } catch (NumberFormatException e) {
                System.err.println("Invalid difficulty selected for filter: " + selectedDifficultyStr);
            }
        }

        String[] playerTypeOptions = {"All Players", "Human Players", "AI Players"};
        String selectedPlayerTypeStr = (String) JOptionPane.showInputDialog(
                this,
                "Select Player Type to View:",
                "Filter High Scores",
                JOptionPane.QUESTION_MESSAGE,
                null,
                playerTypeOptions,
                playerTypeOptions[0] // Default to "All Players"
        );

        if (selectedPlayerTypeStr == null) return; // User cancelled

        Boolean filterIsAI = null; // null for all, true for AI, false for Human
        if (selectedPlayerTypeStr.equals("Human Players")) {
            filterIsAI = false;
        } else if (selectedPlayerTypeStr.equals("AI Players")) {
            filterIsAI = true;
        }


        StringBuilder sb = new StringBuilder("All High Scores:\n\n");
        sb.append(String.format("%-4s %-15s %-7s %-5s %s\n", "Rank", "Player", "Score", "Diff", "AI Version")); // Adjusted table header
        sb.append("----------------------------------------------------\n");

        List<HighScoreEntry> filteredScores = new ArrayList<>();
        for (HighScoreEntry entry : highScores) {
            boolean matchesDifficulty = (filterDifficulty == -1 || entry.getDifficulty() == filterDifficulty);
            boolean matchesPlayerType = (filterIsAI == null || entry.isAI() == filterIsAI);

            if (matchesDifficulty && matchesPlayerType) {
                filteredScores.add(entry);
            }
        }


        if (filteredScores.isEmpty()) {
            sb.append("No high scores recorded yet for the selected filters.\nPlay a game!");
        } else {
            for (int i = 0; i < filteredScores.size(); i++) {
                HighScoreEntry entry = filteredScores.get(i);
                String aiVersionDisplay = entry.isAI() ? "v" + entry.getAiVersion() : "-";
                sb.append(String.format("%-4d %-15s %-7d %-5d %s\n",
                                        (i + 1),
                                        entry.getUserName(),
                                        entry.getScore(),
                                        entry.getDifficulty(),
                                        aiVersionDisplay));
            }
        }
        // Use a JTextArea inside a JScrollPane for potentially long lists
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); // Use monospaced font for alignment
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(550, 400)); // Increased width for new column

        JOptionPane.showMessageDialog(this, scrollPane, "All High Scores", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Displays a popup window with detailed game statistics.
     */
    private void showGameStatistics() {
        StringBuilder sb = new StringBuilder("Game Statistics:\n\n");

        double timePlayedSeconds = (gameEndTime - gameStartTime) / 1000.0;
        sb.append(String.format("Time Played: %.2f seconds\n", timePlayedSeconds));
        sb.append(String.format("Final Score: %d\n", score));
        sb.append(String.format("Difficulty: %d\n", initialDifficulty));
        sb.append(String.format("Total Asteroids Spawned: %d\n", totalAsteroidsSpawned));
        sb.append(String.format("Asteroids Destroyed: %d\n", asteroidsDestroyed));

        String hitMissRatioStr;
        if (bulletsFired > 0) {
            double ratio = (double) asteroidsDestroyed / bulletsFired;
            hitMissRatioStr = String.format("%.2f (Hits / Shots)", ratio);
        } else {
            hitMissRatioStr = "N/A (No bullets fired)";
        }
        sb.append(String.format("Hit/Miss Ratio: %s\n", hitMissRatioStr));

        // Shield Activations for Player 1 / AI (Renamed)
        long totalShieldCost1 = (long)totalShieldActivations1 * SHIELD_COST_PER_ACTIVATION; // Renamed
        sb.append(String.format("Player 1 Shield Activations: %d\n", totalShieldActivations1)); // Renamed
        sb.append(String.format("Player 1 Total Shield Duration: %.2f seconds\n", totalShieldDuration1 / 1000.0)); // Renamed
        sb.append(String.format("Player 1 Estimated Shield Cost: %d units\n", totalShieldCost1)); // Renamed

        // Shield Activations for Player 2 (if multiplayer) (Renamed)
        if (lastPlayedState == GameState.MULTIPLAYER_PLAYING) {
            long totalShieldCost2 = (long)totalShieldActivations2 * SHIELD_COST_PER_ACTIVATION; // Renamed
            sb.append(String.format("Player 2 Shield Activations: %d\n", totalShieldActivations2)); // Renamed
            sb.append(String.format("Player 2 Total Shield Duration: %.2f seconds\n", totalShieldDuration2 / 1000.0)); // Renamed
            sb.append(String.format("Player 2 Estimated Shield Cost: %d units\n", totalShieldCost2)); // Renamed
        }


        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(450, 300)); // Adjusted size for statistics window

        JOptionPane.showMessageDialog(this, scrollPane, "Game Statistics", JOptionPane.INFORMATION_MESSAGE);
    }


    /**
     * Enum to define the different states of the game.
     */
    enum GameState { START, PLAYING_SINGLE, MULTIPLAYER_SETUP_NAMES, MULTIPLAYER_DISPLAY_QRS, MULTIPLAYER_PLAYING, ML_PLAYING, GAME_OVER }

    // MouseListener implementations for button clicks
    @Override
    public void mouseClicked(MouseEvent e) {
        // Convert mouse coordinates to game's internal coordinates
        double scaleX = (double) getWidth() / WIDTH;
        double scaleY = (double) getHeight() / HEIGHT;
        double scale = Math.min(scaleX, scaleY);
        double scaledGameAreaXOffset = (getWidth() - WIDTH * scale) / 2;
        double scaledGameAreaYOffset = (getHeight() - HEIGHT * scale) / 2;

        double gameMouseX = (e.getX() - scaledGameAreaXOffset) / scale;
        double gameMouseY = (e.getY() - scaledGameAreaYOffset) / scale;

        for (java.util.Map.Entry<Rectangle, Runnable> entry : buttonActions.entrySet()) {
            if (entry.getKey().contains(gameMouseX, gameMouseY)) {
                entry.getValue().run();
                break; // Only one button can be clicked at a time
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {}
    @Override
    public void mouseReleased(MouseEvent e) {}
    @Override
    public void mouseEntered(MouseEvent e) {}
    @Override
    public void mouseExited(MouseEvent e) {}

    /**
     * Inner class representing a star in the background.
     */
    private class Star {
        double x, y;
        int size;
        double speed;
        Color color;

        Star(Random random, int screenWidth, int screenHeight) {
            this.x = random.nextDouble() * screenWidth;
            this.y = random.nextDouble() * screenHeight; // Start anywhere on screen
            this.size = 1 + random.nextInt(2); // Small stars (1-2 pixels)
            this.speed = 0.5 + random.nextDouble() * 2.5; // Varying speeds
            this.color = new Color(255, 255, 255, 150 + random.nextInt(100)); // Fading white
        }

        void update() {
            y += speed;
            // Wrap around top if goes off bottom
            if (y > HEIGHT) { // Use AsteroidGame.HEIGHT (or just HEIGHT since it's an inner class)
                y = 0; // Reset to top
                x = random.nextDouble() * WIDTH; // New random X
                speed = 0.5 + random.nextDouble() * 2.5; // New random speed
                color = new Color(255, 255, 255, 150 + random.nextInt(100)); // New random transparency
            }
        }

        void draw(Graphics2D g2d) {
            g2d.setColor(color);
            g2d.fillOval((int) x, (int) y, size, size);
        }
    }
}

/**
 * Represents the player's spaceship.
 */
class Ship {
    double x, y, dx, dy;
    int angle = 270; // 270 degrees is straight up (standard for top-down games)
    private final double ACCELERATION = 0.2;
    private final double FRICTION = 0.98; // Reduces velocity over time
    private final double ROTATION_SPEED = 4.5; // Increased for faster/smoother rotation
    public final int SIZE = 14; // Represents roughly half width/height for bounding box and drawing (20 * 0.7 = 14)
    private final int INVINCIBILITY_DURATION = 1500; // milliseconds after spawn/hit/shield
    private long invincibilityEndTime = 0;
    private final Pattern pattern; // New field for ship pattern

    enum Pattern { NONE, ZEBRA, DOTTED } // Enum for different patterns

    /**
     * Constructor for the Ship.
     * @param startX Initial X position.
     * @param startY Initial Y position.
     * @param pattern The visual pattern for the ship's interior.
     */
    Ship(int startX, int startY, Pattern pattern) {
        this.x = startX;
        this.y = startY;
        this.dx = 0;
        this.dy = 0;
        this.pattern = pattern;
        setInvincible(); // Start as invincible to prevent immediate collision
    }

    /**
     * Resets the ship's position and grants temporary invincibility.
     * @param startX New X position.
     * @param startY New Y position.
     */
    void resetPosition(int startX, int startY) {
        this.x = startX;
        this.y = startY;
        this.dx = 0; // Reset velocity
        this.dy = 0;
        setInvincible();
    }

    /**
     * Sets the ship to be temporarily invincible.
     */
    void setInvincible() {
        invincibilityEndTime = System.currentTimeMillis() + INVINCIBILITY_DURATION;
    }

    /**
     * Checks if the ship is currently invincible.
     * @return true if invincible, false otherwise.
     */
    boolean isInvincible() {
        return System.currentTimeMillis() < invincibilityEndTime;
    }

    /**
     * Updates the ship's position, velocity, and rotation based on input.
     * Also handles screen wrapping.
     * @param up Whether the up arrow key is pressed.
     * @param left Whether the left arrow key is pressed.
     * @param right Whether the right arrow key is pressed.
     * @param shieldActive Whether shield is currently active.
     * @param shipSpeedReductionFactor The factor to apply for ship speed reduction (e.g., 0.5 for half speed).
     * @param screenWidth Width of the game screen.
     * @param screenHeight Height of the game screen.
     */
    void update(boolean up, boolean left, boolean right, boolean shieldActive, double shipSpeedReductionFactor, int screenWidth, int screenHeight) {
        double currentAcceleration = ACCELERATION;
        // If shield is active, apply the speed reduction multiplier (ship moves slower)
        if (shieldActive) {
            currentAcceleration *= shipSpeedReductionFactor;
        }

        if (up) {
            // Apply thrust in the direction of the ship's angle
            dx += Math.cos(Math.toRadians(angle)) * currentAcceleration;
            dy += Math.sin(Math.toRadians(angle)) * currentAcceleration;
        }

        // Apply friction to gradually slow down the ship
        dx *= FRICTION;
        dy *= FRICTION;

        // Update position based on velocity
        x += dx;
        y += dy;

        // Rotate the ship
        if (left) angle -= ROTATION_SPEED;
        if (right) angle += ROTATION_SPEED;

        // Screen wrapping logic
        if (x < 0) x = screenWidth;
        else if (x > screenWidth) x = 0;
        if (y < 0) y = screenHeight;
        else if (y > screenHeight) y = 0;
    }

    /**
     * Draws the spaceship on the screen, including thrust and invincibility effects.
     * @param g2d The Graphics2D object for drawing.
     */
    void draw(Graphics2D g2d) {
        // Save current transform to prevent affecting other drawing operations
        AffineTransform oldTransform = g2d.getTransform();

        g2d.translate(x, y); // Translate to ship's position
        // Rotate the ship. Adding 90 degrees aligns the drawing with the angle calculation
        // (0 degrees is right, 270 is up for Math.cos/sin conventions)
        g2d.rotate(Math.toRadians(angle + 90));

        // Ship Body Shape (a simple triangle pointing up)
        Path2D shipBody = new Path2D.Double();
        shipBody.moveTo(0, -SIZE); // Top point
        shipBody.lineTo(-SIZE / 2, SIZE / 2); // Bottom-left point
        shipBody.lineTo(SIZE / 2, SIZE / 2); // Bottom-right point
        shipBody.closePath();

        g2d.setColor(Color.WHITE);
        g2d.fill(shipBody);

        // Draw internal patterns
        g2d.clip(shipBody); // Clip drawing to the ship's body
        drawPattern(g2d, pattern);
        g2d.setClip(null); // Reset clip


        // Thrust effect visualization (only if accelerating significantly)
        if (Math.abs(dx) > 0.1 || Math.abs(dy) > 0.1) { // Check if ship is moving
            Path2D thrust = new Path2D.Double();
            thrust.moveTo(0, SIZE / 2);
            // Randomize thrust flame appearance
            thrust.lineTo(-SIZE / 4, SIZE / 2 + 10 + Math.random() * 5);
            thrust.lineTo(0, SIZE / 2 + Math.random() * 10);
            thrust.lineTo(SIZE / 4, SIZE / 2 + 10 + Math.random() * 5);
            thrust.closePath();
            g2d.setColor(Color.ORANGE);
            g2d.fill(thrust);
            g2d.setColor(Color.RED);
            g2d.draw(thrust);
        }

        // Invincibility visual cue (flickering effect and shield)
        if (isInvincible()) {
            long remainingInvincibility = invincibilityEndTime - System.currentTimeMillis();
            // Blinking effect for the ship
            if (remainingInvincibility % 200 < 100) { // Flicker every 100ms
                g2d.setColor(new Color(255, 255, 255, 100)); // Semi-transparent white
                g2d.fill(shipBody);
            }

            // Green shield circle: a bit bigger than the ship
            int shieldRadius = SIZE + 5; // SIZE is half width. Shield radius is ship's half width + 5.
            int shieldDiameter = shieldRadius * 2;

            g2d.setColor(new Color(0, 255, 0, 150)); // Semi-transparent green for shield
            // Draw the shield centered on the ship's origin (which is currently translated to x,y)
            g2d.fill(new Ellipse2D.Double(-shieldRadius, -shieldRadius, shieldDiameter, shieldDiameter));
            g2d.setColor(Color.GREEN);
            g2d.draw(new Ellipse2D.Double(-shieldRadius, -shieldRadius, shieldDiameter, shieldDiameter));
        }

        // Restore the previous transform state
        g2d.setTransform(oldTransform);
    }

    /**
     * Draws the specified pattern inside the ship's body.
     * @param g2d Graphics2D context.
     * @param pattern The pattern to draw.
     */
    private void drawPattern(Graphics2D g2d, Pattern pattern) {
        g2d.setColor(Color.BLACK); // Pattern color

        switch (pattern) {
            case ZEBRA:
                // Draw horizontal stripes
                for (int i = -SIZE; i <= SIZE; i += 4) { // Adjust stripe thickness
                    g2d.drawLine(-SIZE / 2, i, SIZE / 2, i);
                }
                break;
            case DOTTED:
                // Draw dots
                for (int y = -SIZE; y <= SIZE; y += 6) { // Adjust dot spacing
                    for (int x = -SIZE / 2; x <= SIZE / 2; x += 6) {
                        g2d.fillOval(x - 1, y - 1, 2, 2); // Small dots
                    }
                }
                break;
            case NONE:
            default:
                // No pattern
                break;
        }
    }

    /**
     * Gets the bounding box of the ship for collision detection.
     * @return A Rectangle representing the ship's bounds.
     */
    Rectangle getBounds() {
        return new Rectangle((int) x - SIZE / 2, (int) y - SIZE / 2, SIZE, SIZE);
    }
}

/**
 * Represents a bullet fired by the ship.
 */
class Bullet {
    double x, y;
    final double dx, dy;
    private final int BULLET_SPEED = 15;
    private final int SIZE = 4; // Radius of the bullet

    /**
     * Constructor for a Bullet.
     * @param ship The ship that fired the bullet.
     */
    Bullet(Ship ship) {
        // Bullet starts from the tip of the ship, offset by ship's size
        x = ship.x + Math.cos(Math.toRadians(ship.angle)) * ship.SIZE;
        y = ship.y + Math.sin(Math.toRadians(ship.angle)) * ship.SIZE;
        dx = Math.cos(Math.toRadians(ship.angle)) * BULLET_SPEED;
        dy = Math.sin(Math.toRadians(ship.angle)) * BULLET_SPEED;
    }

    /**
     * Updates the bullet's position. Bullets no longer wrap, they disappear.
     * @param screenWidth Width of the game screen.
     * @param screenHeight Height of the game screen.
     */
    void update(int screenWidth, int screenHeight) {
        x += dx;
        y += dy;
        // Removed wrapping logic here. Bullet will be removed by onScreen() check
    }

    /**
     * Draws the bullet as a yellow oval.
     * @param g2d The Graphics2D object for drawing.
     */
    void draw(Graphics2D g2d) {
        g2d.setColor(Color.YELLOW);
        g2d.fillOval((int) x - SIZE / 2, (int) y - SIZE / 2, SIZE, SIZE);
    }

    /**
     * Checks if the bullet is within the screen bounds (with a small buffer for removal).
     * @param screenWidth Width of the game screen.
     * @param screenHeight Height of the game screen.
     * @return true if on screen, false otherwise.
     */
    boolean onScreen(int screenWidth, int screenHeight) {
        // Bullet disappears when it leaves the screen
        return x >= -SIZE && x <= screenWidth + SIZE && y >= -SIZE && y <= screenHeight + SIZE;
    }

    /**
     * Gets the bounding box of the bullet for collision detection.
     * @return A Rectangle representing the bullet's bounds.
     */
    Rectangle getBounds() {
        return new Rectangle((int) x - SIZE / 2, (int) y - SIZE / 2, SIZE, SIZE);
    }
}

/**
 * Represents an asteroid, which is an obstacle for the ship.
 */
class Asteroid {
    double x, y, dx, dy;
    int size; // Radius of the asteroid
    Color color; // Color of the asteroid
    private final Random random;
    private final int MAX_BASE_SPEED = 3; // Maximum base speed for asteroids
    public static final int BASE_SIZE = 15; // Base radius for new asteroids
    static final int MIN_SPLIT_SIZE = 10; // Minimum size for an asteroid to split into smaller ones
    private final int difficultyLevel; // Store the difficulty level at which this asteroid was spawned

    /**
     * Constructor for initial asteroids, spawning from screen edges.
     * @param random The Random instance from the game.
     * @param currentDifficulty The current difficulty level of the game.
     */
    Asteroid(Random random, int currentDifficulty) {
        this.random = random;
        this.difficultyLevel = currentDifficulty; // Store the current difficulty
        size = BASE_SIZE + random.nextInt(15); // Random size between 15 and 29
        setRandomSpawnLocation(AsteroidGame.WIDTH, AsteroidGame.HEIGHT);
        // Random gray-ish color for variety
        this.color = new Color(random.nextInt(156) + 100, random.nextInt(156) + 100, random.nextInt(156) + 100);
    }

    /**
     * Constructor for splitting asteroids, inheriting position and having a new size.
     * The new asteroids will scatter generally away from the direction of the incoming bullet.
     * @param startX X position of the parent asteroid.
     * @param startY Y position of the parent asteroid.
     * @param newSize New size of the split asteroid.
     * @param random The Random instance from the game.
     * @param currentDifficulty The current difficulty level of the game.
     * @param parentAsteroidDx X component of the parent asteroid's velocity.
     * @param parentAsteroidDy Y component of the parent asteroid's velocity.
     * @param angleOffsetRadians A fixed angle offset (e.g., PI/3 or -PI/3) for the new fragment.
     */
    Asteroid(double startX, double startY, int newSize, Random random, int currentDifficulty,
             double parentAsteroidDx, double parentAsteroidDy, double angleOffsetRadians) {
        this.random = random;
        this.difficultyLevel = currentDifficulty; // Store the current difficulty
        this.x = startX;
        this.y = startY;
        this.size = newSize;

        double sizeSpeedMultiplier = getSizeSpeedMultiplier(this.size);
        double baseSplitSpeed = 2 + random.nextDouble();
        double effectiveSplitSpeed = baseSplitSpeed * sizeSpeedMultiplier;

        // Apply difficulty level to split asteroid speed as well
        effectiveSplitSpeed *= (0.5 + (double) this.difficultyLevel * 0.05);

        // Calculate the direction opposite to the parent asteroid's original movement
        // If parent asteroid was stationary, pick a random opposite direction
        double oppositeParentAngleRadians;
        if (parentAsteroidDx == 0 && parentAsteroidDy == 0) {
            oppositeParentAngleRadians = random.nextDouble() * 2 * Math.PI; // Random direction
        } else {
            oppositeParentAngleRadians = Math.atan2(-parentAsteroidDy, -parentAsteroidDx);
        }

        // Apply the fixed angle offset for each new fragment
        double finalAngleRadians = oppositeParentAngleRadians + angleOffsetRadians;

        this.dx = Math.cos(finalAngleRadians) * effectiveSplitSpeed;
        this.dy = Math.sin(finalAngleRadians) * effectiveSplitSpeed;
        this.color = new Color(random.nextInt(156) + 100, random.nextInt(156) + 100, random.nextInt(156) + 100);
    }

    /**
     * Helper method to get speed multiplier based on asteroid size.
     * Larger asteroids are slower, smaller ones are faster.
     * @param asteroidSize The current size of the asteroid.
     * @return A double representing the speed multiplier.
     */
    private double getSizeSpeedMultiplier(int asteroidSize) {
        if (asteroidSize >= 20) { // Large asteroid (e.g., 20-29)
            return 0.7; // Slower
        } else if (asteroidSize >= 10) { // Medium asteroid (e.g., 10-19)
            return 1.2; // Faster
        } else { // Small asteroid (e.g., < 10)
            return 1.5; // Fastest
        }
    }


    /**
     * Sets a random spawn location for an asteroid from one of the four screen edges.
     * Uses the internally stored difficultyLevel for speed calculation.
     * @param screenWidth Width of the game screen.
     * @param screenHeight Height of the game screen.
     */
    private void setRandomSpawnLocation(int screenWidth, int screenHeight) {
        int edge = random.nextInt(4); // 0: top, 1: right, 2: bottom, 3: left
        // Adjusted gameSpeedFactor for a gentler start.
        // At difficulty 1: 0.5 + 1 * 0.05 = 0.55 (55% of MAX_BASE_SPEED)
        // At difficulty 10: 0.5 + 10 * 0.05 = 1.0 (100% of MAX_BASE_SPEED)
        double gameSpeedFactor = 0.5 + (double) this.difficultyLevel * 0.05;
        double sizeSpeedMultiplier = getSizeSpeedMultiplier(this.size);

        // Combine overall game difficulty with asteroid size characteristics
        double effectiveSpeed = MAX_BASE_SPEED * gameSpeedFactor * sizeSpeedMultiplier;

        switch (edge) {
            case 0: // Top edge
                x = random.nextInt(screenWidth);
                y = -size; // Start just off-screen
                dx = (random.nextDouble() * 2 - 1) * effectiveSpeed; // Random horizontal speed
                dy = (0.5 + random.nextDouble() * 0.5) * effectiveSpeed; // Always move downwards
                break;
            case 1: // Right edge
                x = screenWidth + size; // Start just off-screen
                y = random.nextInt(screenHeight);
                dx = (-0.5 - random.nextDouble() * 0.5) * effectiveSpeed; // Always move leftwards
                dy = (random.nextDouble() * 2 - 1) * effectiveSpeed; // Random vertical speed
                break;
            case 2: // Bottom edge
                x = random.nextInt(screenWidth);
                y = screenHeight + size; // Start just off-screen
                dx = (random.nextDouble() * 2 - 1) * effectiveSpeed;
                dy = (-0.5 - random.nextDouble() * 0.5) * effectiveSpeed; // Always move upwards
                break;
            case 3: // Left edge
                x = -size; // Start just off-screen
                y = random.nextInt(screenHeight);
                dx = (0.5 + random.nextDouble() * 0.5) * effectiveSpeed; // Always move rightwards
                dy = (random.nextDouble() * 2 - 1) * effectiveSpeed;
                break;
        }
    }

    /**
     * Updates the asteroid's position based on its velocity.
     * @param speedMultiplier A multiplier to apply to the asteroid's movement speed.
     */
    void update(double speedMultiplier) {
        x += dx * speedMultiplier;
        y += dy * speedMultiplier;
    }

    /**
     * Draws the asteroid as a filled oval with a dark gray border.
     * @param g2d The Graphics2D object for drawing.
     */
    void draw(Graphics2D g2d) {
        g2d.setColor(color);
        g2d.fill(new Ellipse2D.Double(x - size, y - size, size * 2, size * 2));
        g2d.setColor(Color.DARK_GRAY); // Border color
        g2d.draw(new Ellipse2D.Double(x - size, y - size, size * 2, size * 2));
    }

    /**
     * Checks if the asteroid is within the screen bounds (with a larger buffer).
     * @param screenWidth Width of the game screen.
     * @param screenHeight Height of the game screen.
     * @return true if on screen, false otherwise.
     */
    boolean onScreen(int screenWidth, int screenHeight) {
        // Consider a larger buffer (size * 2) so asteroids are fully off-screen before removal
        return x >= -size * 2 && x <= screenWidth + size * 2 && y >= -size * 2 && y <= screenHeight + size * 2;
    }

    /**
     * Gets the bounding box of the asteroid for collision detection.
     * @return A Rectangle representing the asteroid's bounds.
     */
    Rectangle getBounds() {
        return new Rectangle((int) x - size, (int) y - size, size * 2, size * 2);
    }
}

/**
 * Represents a visual explosion effect that fades and expands.
 */
class PopEffect {
    double x, y; // Use double for consistent positioning
    int life = 30; // Life duration of the effect (frames)
    private final int MAX_RADIUS = 20; // Maximum radius the effect expands to
    private final Random random;
    private final Color startColor; // Initial color of the effect

    /**
     * Constructor for a PopEffect.
     * @param x Initial X position of the effect.
     * @param y Initial Y position of the effect.
     * @param random The Random instance from the game.
     */
    PopEffect(double x, double y, Random random) {
        this.x = x;
        this.y = y;
        this.random = random;
        // Orange-yellow explosion color
        this.startColor = new Color(255, random.nextInt(150) + 100, 0);
    }

    /**
     * Updates the life of the effect, causing it to fade out.
     */
    void update() {
        life--;
    }

    /**
     * Draws the fading and expanding explosion effect.
     * @param g2d The Graphics2D object for drawing.
     */
    void draw(Graphics2D g2d) {
        // Calculate alpha (transparency) for fading effect
        float alpha = (float) life / 30;
        if (alpha < 0) alpha = 0; // Clamp alpha to be non-negative
        if (alpha > 1) alpha = 1; // Clamp alpha to be max 1

        // Set the color with fading alpha. Corrected to use getBlue() for the blue component.
        g2d.setColor(new Color(startColor.getRed(), startColor.getGreen(), startColor.getBlue(), (int)(255 * alpha)));

        // Calculate current radius for expanding effect
        int currentRadius = (int) (MAX_RADIUS * (1 - (float) life / 30));

        // Draw the main expanding oval
        g2d.drawOval((int)x - currentRadius / 2, (int)y - currentRadius / 2, currentRadius, currentRadius);

        // Draw multiple smaller circles for a more dynamic "exploding" look
        for (int i = 0; i < 3; i++) {
            // Randomize size and slight position for fragmented look
            int smallRadius = currentRadius / 2 + random.nextInt(currentRadius / 2);
            g2d.drawOval((int)x - smallRadius / 2 + random.nextInt(5) - 2, (int)y - smallRadius / 2 + random.nextInt(5) - 2, smallRadius, smallRadius);
        }
    }
}

/**
 * Represents a high score entry, which is serializable for saving to a file.
 */
class HighScoreEntry implements Serializable {
    private static final long serialVersionUID = 3L; // Updated serialVersionUID
    String userName;
    int score;
    int difficulty;
    boolean isAI; // New field to indicate if the score is from an AI
    String aiVersion; // New field to store AI version if applicable

    public HighScoreEntry(String userName, int score, int difficulty, boolean isAI, String aiVersion) {
        this.userName = userName;
        this.score = score;
        this.difficulty = difficulty;
        this.isAI = isAI;
        this.aiVersion = aiVersion;
    }

    public String getUserName() {
        return userName;
    }

    public int getScore() {
        return score;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public boolean isAI() {
        return isAI;
    }

    public String getAiVersion() {
        return aiVersion;
    }
}

/**
 * Stores AI specific statistics for versioning.
 * This class is serializable to persist AI progress.
 */
class AIStats implements Serializable {
    private static final long serialVersionUID = 1L;
    int majorVersion;
    int highScoreCount;
    int totalGames;
    double aggressionBias; // New: To store a bias for aggression over games

    public AIStats() {
        this.majorVersion = 1;
        this.highScoreCount = 0;
        this.totalGames = 0;
        this.aggressionBias = 0.0; // Starts neutral
    }

    public void incrementMajorVersion() {
        this.majorVersion++;
        this.highScoreCount = 0; // Reset high score count on major update
        this.totalGames = 0; // Reset total games on major update
        this.aggressionBias = 0.0; // Reset aggression bias too
    }

    public String getFormattedVersion() {
        return majorVersion + "." + highScoreCount + "." + totalGames;
    }

    public double getAggressionBias() {
        return aggressionBias;
    }

    public void setAggressionBias(double aggressionBias) {
        this.aggressionBias = aggressionBias;
    }
}

/**
 * Represents the Artificial Intelligence player logic.
 * This is a rule-based AI.
 */
class ShipAI {
    private final Random random;
    private final int screenWidth;
    private final int screenHeight;
    private AIStats aiStats; // Reference to the persistent AI statistics

    // Base AI tuning parameters (these are now constants within ShipAI)
    // These base values are used before applying the aggression factor.
    private static final double BASE_ASTEROID_DANGER_DISTANCE = 100; // Increased danger distance for better evasion
    private static final double BASE_COLLISION_AVOID_ANGLE_THRESHOLD = 45; // Wider angle to start turning
    private static final double BASE_SHIELD_THREAT_COUNT = 2; // Shield when 2 threats are very close
    private static final double BASE_SHOOT_ALIGN_THRESHOLD = 5; // Tighter alignment for shooting
    private static final double BASE_MAX_AI_APPROACH_SPEED = 4.0; // Maximum speed AI will try to reach
    private static final double BASE_MIN_THRUST_DISTANCE_TO_TARGET = 80; // Distance to stop thrusting towards target
    private static final double STOP_THRUST_SPEED_THRESHOLD = 1.0; // Speed at which AI considers stopping thrust

    // Dynamic AI parameters, influenced by aggressionFactor
    private double current_asteroid_danger_distance;
    private double current_shield_threat_count;
    private double current_shoot_align_threshold;
    private double current_min_thrust_distance_to_target;

    // Aggression state - this is *per game session* and derived from a cumulative score
    private double current_ai_score = 0; // Cumulative score for AI performance in current game
    private double aiAggressionFactor = 1.0; // Influences AI behavior, default to neutral

    // Constants for mapping current_ai_score to aiAggressionFactor
    private static final double MIN_SCORE_FOR_CAUTIOUS = -1000; // Score at which AI becomes most cautious
    private static final double MAX_SCORE_FOR_AGGRESSIVE = 1000;  // Score at which AI becomes most aggressive
    private static final double MIN_AGGRESSION_FACTOR = 0.3;    // Most cautious aggression multiplier (more evasive, less shooting)
    private static final double MAX_AGGRESSION_FACTOR = 2.5;     // Most aggressive aggression multiplier (less evasive, more shooting)


    public ShipAI(Random random, int screenWidth, int screenHeight, AIStats aiStats) {
        this.random = random;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.aiStats = aiStats; // Set reference to shared AIStats object

        // Initialize current_ai_score based on aggression bias for a smoother start across games
        // This makes past performance subtly influence starting aggression
        // Convert aggression bias (which is already a factor) back to a score equivalent for scaling
        this.current_ai_score = (aiStats.getAggressionBias() - MIN_AGGRESSION_FACTOR) / (MAX_AGGRESSION_FACTOR - MIN_AGGRESSION_FACTOR) * (MAX_SCORE_FOR_AGGRESSIVE - MIN_SCORE_FOR_CAUTIOUS) + MIN_SCORE_FOR_CAUTIOUS;
        this.current_ai_score = Math.max(MIN_SCORE_FOR_CAUTIOUS, Math.min(MAX_SCORE_FOR_AGGRESSIVE, this.current_ai_score));

        updateAggressionParameters(); // Set initial aggression parameters
    }

    /**
     * Updates the aiAggressionFactor and all derived AI parameters based on the current_ai_score.
     */
    private void updateAggressionParameters() {
        // Normalize current_ai_score to a [0, 1] range based on defined min/max scores
        double normalizedScore = (current_ai_score - MIN_SCORE_FOR_CAUTIOUS) / (MAX_SCORE_FOR_AGGRESSIVE - MIN_SCORE_FOR_CAUTIOUS);
        normalizedScore = Math.max(0, Math.min(1, normalizedScore)); // Clamp to ensure it's within [0, 1]

        // Map normalized score to the aggression factor range [MIN_AGGRESSION_FACTOR, MAX_AGGRESSION_FACTOR]
        aiAggressionFactor = MIN_AGGRESSION_FACTOR + normalizedScore * (MAX_AGGRESSION_FACTOR - MIN_AGGRESSION_FACTOR);

        // Update derived parameters using the aggression factor
        // Higher aggressionFactor means:
        // - Smaller danger distance (takes more risks)
        // - Fewer threats needed to shield (more reactive/proactive with shield)
        // - Smaller shoot alignment threshold (more precise/demanding for shooting)
        // - Smaller min thrust distance (approaches closer before stopping thrust)
        current_asteroid_danger_distance = BASE_ASTEROID_DANGER_DISTANCE / aiAggressionFactor;
        current_shield_threat_count = BASE_SHIELD_THREAT_COUNT / aiAggressionFactor;
        current_shoot_align_threshold = BASE_SHOOT_ALIGN_THRESHOLD / aiAggressionFactor;
        current_min_thrust_distance_to_target = BASE_MIN_THRUST_DISTANCE_TO_TARGET / aiAggressionFactor;

        // Ensure shield threat count is at least 1 (can't shield on zero threats)
        current_shield_threat_count = Math.max(1, current_shield_threat_count);
        // Ensure shoot align threshold is not too small
        current_shoot_align_threshold = Math.max(1, current_shoot_align_threshold);
    }


    /**
     * Represents the actions the AI wants to take.
     */
    static class AIAction {
        boolean thrust;
        boolean turnLeft;
        boolean turnRight;
        boolean shoot;
        boolean shield; // Renamed from hyperspace

        AIAction(boolean thrust, boolean turnLeft, boolean turnRight, boolean shoot, boolean shield) { // Renamed
            this.thrust = thrust;
            this.turnLeft = turnLeft;
            this.turnRight = turnRight;
            this.shoot = shoot;
            this.shield = shield; // Renamed
        }
    }

    /**
     * Determines the AI's next action based on the current game state.
     * @param ship The AI's ship.
     * @param asteroids List of current asteroids.
     * @param bullets List of current bullets (for knowing bullet count).
     * @param currentBullets Current bullets in magazine.
     * @param reloading Is the ship reloading.
     * @param shieldActive Is shield active. (Renamed)
     * @param now Current time.
     * @param lastShotTime Last time a shot was fired.
     * @param shieldRefillTime When shield will be ready. (Renamed)
     * @param shieldActiveDuration How long shield lasts. (Renamed)
     * @param bulletCooldown How long between shots.
     * @return An AIAction object with desired controls.
     */
    public AIAction determineAction(Ship ship, ArrayList<Asteroid> asteroids, ArrayList<Bullet> bullets,
                                    int currentBullets, boolean reloading, boolean shieldActive, // Renamed shieldActive
                                    long now, long lastShotTime, long shieldRefillTime, // Renamed shieldRefillTime
                                    long shieldActiveDuration, // Renamed shieldActiveDuration
                                    long bulletCooldown) {

        boolean thrust = false;
        boolean turnLeft = false;
        boolean turnRight = false;
        boolean shoot = false;
        boolean shield = false; // Renamed

        // --- Shield Logic ---
        // Count threatening asteroids based on dynamically adjusted danger distance
        int threateningAsteroids = 0;
        for (Asteroid asteroid : asteroids) {
            double dist = Math.sqrt(Math.pow(ship.x - asteroid.x, 2) + Math.pow(ship.y - asteroid.y, 2));
            if (dist < current_asteroid_danger_distance * 1.5) { // A bit wider threat radius for shield
                // Also consider if asteroid is moving towards ship
                double dotProduct = (asteroid.dx * (ship.x - asteroid.x) + asteroid.dy * (ship.y - asteroid.y));
                if (dotProduct > 0) { // If asteroid velocity is generally towards the ship
                    threateningAsteroids++;
                }
            }
        }
        // If many threats and shield is available, use it (threshold is dynamic)
        if (threateningAsteroids >= current_shield_threat_count && !shieldActive && now >= shieldRefillTime) {
            shield = true;
        }

        if (ship.isInvincible()) { // If invincible (from shield or respawn), just thrust away from center
            thrust = true;
            // Simple evasion: move away from center if too close to edge
            if (ship.x < screenWidth * 0.2) turnRight = true;
            else if (ship.x > screenWidth * 0.8) turnLeft = true;
            else if (ship.y < screenHeight * 0.2) turnRight = true;
            else if (ship.y > screenHeight * 0.8) turnLeft = true;
            return new AIAction(thrust, turnLeft, turnRight, false, shield);
        }

        // --- Avoidance Logic (High Priority) ---
        Asteroid closestThreat = null;
        double minThreatDistance = Double.MAX_VALUE;

        for (Asteroid asteroid : asteroids) {
            double dist = Math.sqrt(Math.pow(ship.x - asteroid.x, 2) + Math.pow(ship.y - asteroid.y, 2));
            // Only consider threatening if within dynamic danger distance
            if (dist < current_asteroid_danger_distance && dist < minThreatDistance) {
                // Check if asteroid is generally moving towards the ship
                double dotProduct = (asteroid.dx * (ship.x - asteroid.x) + asteroid.dy * (ship.y - asteroid.y));
                if (dotProduct > 0) { // If positive, asteroid is moving towards ship
                    closestThreat = asteroid;
                    minThreatDistance = dist;
                }
            }
        }

        if (closestThreat != null) {
            // Calculate angle from ship to closest threat
            double angleToAsteroid = Math.toDegrees(Math.atan2(closestThreat.y - ship.y, closestThreat.x - ship.x));
            angleToAsteroid = (angleToAsteroid + 360) % 360;

            // Calculate current heading of the ship
            double shipHeading = (ship.angle + 360) % 360;

            // Difference between ship's heading and direction to asteroid
            double angleDiff = Math.abs(shipHeading - angleToAsteroid);
            if (angleDiff > 180) angleDiff = 360 - angleDiff; // Smallest angle difference

            // If an asteroid is close AND ship is heading towards it (within threshold)
            if (minThreatDistance < current_asteroid_danger_distance && angleDiff < BASE_COLLISION_AVOID_ANGLE_THRESHOLD) {
                thrust = true; // Thrust to change direction/move away

                // Calculate two evasion angles: 90 degrees left and 90 degrees right from threat direction
                double evasionAngleLeft = (angleToAsteroid - 90 + 360) % 360;
                double evasionAngleRight = (angleToAsteroid + 90 + 360) % 360;

                // Determine which evasion angle is closer to ship's current heading
                double diffLeft = Math.abs(shipHeading - evasionAngleLeft);
                if (diffLeft > 180) diffLeft = 360 - diffLeft;

                double diffRight = Math.abs(shipHeading - evasionAngleRight);
                if (diffRight > 180) diffRight = 360 - diffRight;

                if (diffLeft < diffRight) {
                    turnLeft = true;
                } else {
                    turnRight = true;
                }
                return new AIAction(thrust, turnLeft, turnRight, shoot, shield);
            }
        }

        // --- Offensive Logic (Medium Priority) if no immediate threats ---
        Asteroid targetAsteroid = null;
        double minDistance = Double.MAX_VALUE;
        for (Asteroid asteroid : asteroids) {
            double dist = Math.sqrt(Math.pow(ship.x - asteroid.x, 2) + Math.pow(ship.y - asteroid.y, 2));
            if (dist < minDistance) {
                targetAsteroid = asteroid;
                minDistance = dist;
            }
        }

        if (targetAsteroid != null) {
            // Calculate angle to target
            double angleToTarget = Math.toDegrees(Math.atan2(targetAsteroid.y - ship.y, targetAsteroid.x - ship.x));
            angleToTarget = (angleToTarget + 360) % 360;

            double shipAngle = (ship.angle + 360) % 360; // Normalize ship angle to 0-360

            double angleDifference = (angleToTarget - shipAngle + 360) % 360;

            // Adjust ship angle
            if (angleDifference > 180) { // Turn left is shorter
                turnLeft = true;
            } else { // Turn right is shorter
                turnRight = true;
            }

            // Conditional thrusting:
            double currentSpeed = Math.sqrt(ship.dx * ship.dx + ship.dy * ship.dy);

            // AI decides to thrust or stop based on distance to target and current speed
            if (minDistance > current_min_thrust_distance_to_target) {
                // If far from target and not too fast, thrust towards it
                if (currentSpeed < BASE_MAX_AI_APPROACH_SPEED * aiAggressionFactor) { // Higher aggression -> higher max speed
                    thrust = true;
                } else {
                    // If far from target but already fast, stop thrusting to coast
                    thrust = false;
                }
            } else {
                // If close to target:
                // Only thrust if speed is very low to make fine adjustments, otherwise stop.
                // Aggressive AI will allow a slightly higher speed before stopping
                if (currentSpeed < STOP_THRUST_SPEED_THRESHOLD * aiAggressionFactor) {
                    thrust = true;
                } else {
                    thrust = false; // Stop thrusting to slow down using friction
                }
            }

            // Shoot if aligned and bullets available
            if (Math.abs(angleDifference) < current_shoot_align_threshold || Math.abs(angleDifference - 360) < current_shoot_align_threshold) {
                if (currentBullets > 0 && !reloading && (now - lastShotTime > bulletCooldown)) {
                    shoot = true;
                }
            }

        } else {
            // If no asteroids, AI should not necessarily always thrust.
            // It can drift, or make small adjustments to its position.
            // For now, if no asteroids:
            // 1. If speed is very low, briefly thrust to prevent getting stuck
            double currentSpeed = Math.sqrt(ship.dx * ship.dx + ship.dy * ship.dy);
            if (currentSpeed < 0.5) { // If almost stationary
                thrust = true;
            }

            // 2. If near edge, try to move towards center
            double centerX = screenWidth / 2.0;
            double centerY = screenHeight / 2.0;
            double distToCenter = Math.sqrt(Math.pow(ship.x - centerX, 2) + Math.pow(ship.y - centerY, 2));
            double edgeThreshold = Math.min(screenWidth, screenHeight) * 0.3; // 30% from edge
            if (distToCenter > edgeThreshold) {
                double angleToCenter = Math.toDegrees(Math.atan2(centerY - ship.y, centerX - ship.x));
                angleToCenter = (angleToCenter + 360) % 360;
                double shipAngle = (ship.angle + 360) % 360;
                double angleDifference = (angleToCenter - shipAngle + 360) % 360;

                if (angleDifference > 180) { turnLeft = true; } else { turnRight = true; }
                thrust = true;
            }
        }

        return new AIAction(thrust, turnLeft, turnRight, shoot, shield);
    }

    /**
     * Provides feedback to the AI about game events.
     * This updates the AI's internal score, which influences its behavior in subsequent decisions.
     * @param hitAsteroid True if an AI bullet hit an asteroid.
     * @param hitByAsteroid True if the AI ship was hit by an asteroid.
     */
    public void receiveFeedback(boolean hitAsteroid, boolean hitByAsteroid) {
        if (hitAsteroid) {
            current_ai_score += 10; // Good feedback: +10
        }
        if (hitByAsteroid) {
            current_ai_score -= 100; // Bad feedback: -100
        }

        // Clamp the AI score to prevent extreme aggression factors
        current_ai_score = Math.max(MIN_SCORE_FOR_CAUTIOUS, Math.min(MAX_SCORE_FOR_AGGRESSIVE, current_ai_score));

        // Update aggression parameters immediately after receiving feedback
        updateAggressionParameters();

        // Log to console the real-time feedback and AI state
        System.out.printf("AI Log: Score: %.0f, Aggression: %.2f, Danger Dist: %.2f, Thrust Dist: %.2f, Shoot Threshold: %.2f, Shield Threat: %.0f\n",
                          current_ai_score, aiAggressionFactor,
                          current_asteroid_danger_distance,
                          current_min_thrust_distance_to_target,
                          current_shoot_align_threshold,
                          current_shield_threat_count);

        // Store current aggression as bias for next game
        aiStats.setAggressionBias(aiAggressionFactor);
    }
}


/**
 * Dialog for displaying visual game statistics.
 */
class StatisticsDialog extends JDialog {
    // The fields below are now accessed directly by non-static inner classes,
    // so the "value not used" warning will resolve.
    private final int bulletsFired;
    private final int asteroidsDestroyed;
    private final int totalShieldActivations1;
    private final int totalShieldActivations2;
    private final boolean isMLMode;
    private final AIStats aiStats;
    private final boolean isMultiplayer;

    public StatisticsDialog(JFrame parent, int bulletsFired, int asteroidsDestroyed,
                            int totalShieldActivations1, int totalShieldActivations2,
                            boolean isMLMode, AIStats aiStats, boolean isMultiplayer) {
        super(parent, "Game Statistics", true); // Modal dialog
        this.bulletsFired = bulletsFired;
        this.asteroidsDestroyed = asteroidsDestroyed;
        this.totalShieldActivations1 = totalShieldActivations1;
        this.totalShieldActivations2 = totalShieldActivations2;
        this.isMLMode = isMLMode;
        this.aiStats = aiStats;
        this.isMultiplayer = isMultiplayer;

        setSize(600, 500); // Fixed size for the dialog
        setLocationRelativeTo(parent); // Center relative to the main game window
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Main panel with a BoxLayout for vertical arrangement
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(Color.DARK_GRAY); // Dark background for contrast

        // Title Label
        JLabel titleLabel = new JLabel("Visual Game Statistics", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(Box.createVerticalStrut(20)); // Spacing
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(20));

        // Panel to hold the charts
        JPanel chartsPanel = new JPanel();
        chartsPanel.setLayout(new GridLayout(1, 2, 10, 0)); // 1 row, 2 columns, 10px horizontal gap
        chartsPanel.setOpaque(false); // Make it transparent
        chartsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding

        // Add Hit/Miss Pie Chart Panel
        chartsPanel.add(new PieChartPanel("Bullet Accuracy")); // Removed parameters, now uses outer class fields

        // Add Shield Activations Bar Chart Panel
        chartsPanel.add(new BarChartPanel("Shield Activations")); // Removed parameters, now uses outer class fields

        mainPanel.add(chartsPanel);

        // Add AI Statistics (if ML mode)
        if (this.isMLMode) { // Accessing outer class field directly
            JPanel aiStatsPanel = new JPanel();
            aiStatsPanel.setLayout(new BoxLayout(aiStatsPanel, BoxLayout.Y_AXIS));
            aiStatsPanel.setOpaque(false);
            aiStatsPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

            JLabel aiTitle = new JLabel("AI Performance Metrics:", SwingConstants.LEFT);
            aiTitle.setFont(new Font("Arial", Font.BOLD, 20));
            aiTitle.setForeground(Color.WHITE);
            aiTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            aiStatsPanel.add(aiTitle);
            aiStatsPanel.add(Box.createVerticalStrut(10));

            JLabel aiVersionLabel = new JLabel("Jarvis Version: " + this.aiStats.getFormattedVersion(), SwingConstants.LEFT); // Accessing outer class field directly
            aiVersionLabel.setFont(new Font("Arial", Font.PLAIN, 16));
            aiVersionLabel.setForeground(Color.LIGHT_GRAY);
            aiVersionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            aiStatsPanel.add(aiVersionLabel);

            JLabel aiGamesLabel = new JLabel("Total Games Played (AI): " + this.aiStats.totalGames, SwingConstants.LEFT); // Accessing outer class field directly
            aiGamesLabel.setFont(new Font("Arial", Font.PLAIN, 16));
            aiGamesLabel.setForeground(Color.LIGHT_GRAY);
            aiGamesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            aiStatsPanel.add(aiGamesLabel);

            JLabel aiHighScoresLabel = new JLabel("AI High Score Count: " + this.aiStats.highScoreCount, SwingConstants.LEFT); // Accessing outer class field directly
            aiHighScoresLabel.setFont(new Font("Arial", Font.PLAIN, 16));
            aiHighScoresLabel.setForeground(Color.LIGHT_GRAY);
            aiHighScoresLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            aiStatsPanel.add(aiHighScoresLabel);

            JLabel aiAggressionLabel = new JLabel(String.format("AI Aggression Bias: %.2f", this.aiStats.getAggressionBias()), SwingConstants.LEFT); // Accessing outer class field directly
            aiAggressionLabel.setFont(new Font("Arial", Font.PLAIN, 16));
            aiAggressionLabel.setForeground(Color.LIGHT_GRAY);
            aiAggressionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            aiStatsPanel.add(aiAggressionLabel);


            mainPanel.add(aiStatsPanel);
        }

        mainPanel.add(Box.createVerticalGlue()); // Push content up

        // Close Button
        JButton closeButton = new JButton("Close");
        closeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        closeButton.setFont(new Font("Arial", Font.BOLD, 18));
        closeButton.setBackground(new Color(50, 50, 50)); // Darker button
        closeButton.setForeground(Color.WHITE);
        closeButton.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> dispose());
        mainPanel.add(closeButton);
        mainPanel.add(Box.createVerticalStrut(20));

        add(mainPanel);
    }

    /**
     * Inner class for drawing a pie chart.
     */
    private class PieChartPanel extends JPanel { // Removed static
        private final String title;

        public PieChartPanel(String title) {
            this.title = title;
            setOpaque(false); // Make transparent for parent layout
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int diameter = Math.min(width, height - 50) - 20; // Leave space for title/labels
            int x = (width - diameter) / 2;
            int y = (height - diameter) / 2 + 20; // Adjusted y to make space for title

            // Draw title
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            FontMetrics fm = g2d.getFontMetrics();
            int titleX = (width - fm.stringWidth(title)) / 2;
            g2d.drawString(title, titleX, 20);

            // Access data from outer class fields
            int totalValue = StatisticsDialog.this.bulletsFired;
            int sliceValue = StatisticsDialog.this.asteroidsDestroyed;

            if (totalValue == 0) {
                g2d.setColor(Color.GRAY);
                g2d.fillArc(x, y, diameter, diameter, 0, 360);
                drawCenteredString(g2d, "N/A", new Font("Arial", Font.PLAIN, 20), width / 2, height / 2 + 20);
                g2d.dispose();
                return;
            }

            int otherValue = totalValue - sliceValue;
            double slicePercentage = (double) sliceValue / totalValue;
            double otherPercentage = (double) otherValue / totalValue;

            int startAngle = 90; // Start at the top

            // Draw slice 1 (e.g., Hits)
            int arcAngle1 = (int) (slicePercentage * 360);
            g2d.setColor(Color.GREEN); // Color for hits
            g2d.fillArc(x, y, diameter, diameter, startAngle, arcAngle1);

            // Draw slice 2 (e.g., Misses)
            int arcAngle2 = 360 - arcAngle1; // Remaining angle
            g2d.setColor(Color.RED); // Color for misses
            g2d.fillArc(x, y, diameter, diameter, startAngle + arcAngle1, arcAngle2);

            // Draw border
            g2d.setColor(Color.WHITE);
            g2d.drawOval(x, y, diameter, diameter);

            // Add percentages text - repositioned for better visibility
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            // Calculate center of pie for text placement
            int centerX = x + diameter / 2;
            int centerY = y + diameter / 2;

            // Text for Slice 1 (Hits) - positioned further out
            if (sliceValue > 0) {
                double textAngle1 = Math.toRadians(startAngle + arcAngle1 / 2.0);
                // Increased multiplier for text radius to move labels further from center
                int textX1 = (int) (centerX + diameter / 2.5 * Math.cos(textAngle1)); // Changed 3.0 to 2.5
                int textY1 = (int) (centerY + diameter / 2.5 * Math.sin(textAngle1)); // Changed 3.0 to 2.5
                String text1 = String.format("Hits: %.1f%%", slicePercentage * 100);
                drawCenteredString(g2d, text1, g2d.getFont(), textX1, textY1);
            }

            // Text for Slice 2 (Misses)
            if (otherValue > 0) {
                double textAngle2 = Math.toRadians(startAngle + arcAngle1 + arcAngle2 / 2.0);
                // Increased multiplier for text radius
                int textX2 = (int) (centerX + diameter / 2.5 * Math.cos(textAngle2)); // Changed 3.0 to 2.5
                int textY2 = (int) (centerY + diameter / 2.5 * Math.sin(textAngle2)); // Changed 3.0 to 2.5
                String text2 = String.format("Misses: %.1f%%", otherPercentage * 100);
                drawCenteredString(g2d, text2, g2d.getFont(), textX2, textY2);
            }
            g2d.dispose();
        }

        private void drawCenteredString(Graphics2D g2d, String text, Font font, int x, int y) {
            g2d.setFont(font);
            FontMetrics metrics = g2d.getFontMetrics(font);
            int textX = x - metrics.stringWidth(text) / 2;
            int textY = y + metrics.getAscent() / 2;
            g2d.drawString(text, textX, textY);
        }
    }

    /**
     * Inner class for drawing a bar chart for shield activations.
     */
    private class BarChartPanel extends JPanel { // Removed static
        private final String title;

        public BarChartPanel(String title) {
            this.title = title;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int barWidth = 60;
            int padding = 40; // Padding from left/right

            // Draw title
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            FontMetrics fm = g2d.getFontMetrics();
            int titleX = (width - fm.stringWidth(title)) / 2;
            g2d.drawString(title, titleX, 20);

            // Draw x-axis (base line)
            g2d.setColor(Color.LIGHT_GRAY);
            int xAxisY = height - 50;
            g2d.drawLine(padding, xAxisY, width - padding, xAxisY);

            // Access data from outer class fields
            int value1 = StatisticsDialog.this.totalShieldActivations1;
            int value2 = StatisticsDialog.this.totalShieldActivations2;
            boolean isMultiplayer = StatisticsDialog.this.isMultiplayer;
            boolean isMLMode = StatisticsDialog.this.isMLMode; // Also get isMLMode for labeling

            // Bar 1 (Player 1 / AI)
            int bar1X = width / 2 - (isMultiplayer ? barWidth + 20 : barWidth / 2);
            double scaleFactor = (double)(xAxisY - 40) / Math.max(value1, isMultiplayer ? value2 : 0); // Max height of bar area
            if (value1 == 0 && (isMultiplayer ? value2 == 0 : true)) scaleFactor = 1.0; // Avoid division by zero if all values are 0

            int bar1Height = (int) (value1 * scaleFactor);
            if (bar1Height < 0) bar1Height = 0; // Clamp min height
            g2d.setColor(Color.ORANGE);
            g2d.fillRect(bar1X, xAxisY - bar1Height, barWidth, bar1Height);
            g2d.setColor(Color.WHITE);
            g2d.drawRect(bar1X, xAxisY - bar1Height, barWidth, bar1Height);
            g2d.setFont(new Font("Arial", Font.PLAIN, 12));
            String label1 = isMLMode ? "Jarvis" : "P1"; // Use isMLMode to label as Jarvis
            drawCenteredString(g2d, label1, g2d.getFont(), bar1X + barWidth / 2, xAxisY + 15);
            drawCenteredString(g2d, String.valueOf(value1), g2d.getFont(), bar1X + barWidth / 2, xAxisY - bar1Height - 5); // Value above bar

            // Bar 2 (Player 2, if multiplayer)
            if (isMultiplayer) {
                int bar2X = width / 2 + 20;
                int bar2Height = (int) (value2 * scaleFactor);
                if (bar2Height < 0) bar2Height = 0; // Clamp min height
                g2d.setColor(Color.MAGENTA);
                g2d.fillRect(bar2X, xAxisY - bar2Height, barWidth, bar2Height);
                g2d.setColor(Color.WHITE);
                g2d.drawRect(bar2X, xAxisY - bar2Height, barWidth, bar2Height);
                String label2 = "P2";
                drawCenteredString(g2d, label2, g2d.getFont(), bar2X + barWidth / 2, xAxisY + 15);
                drawCenteredString(g2d, String.valueOf(value2), g2d.getFont(), bar2X + barWidth / 2, xAxisY - bar2Height - 5); // Value above bar
            }
            g2d.dispose();
        }

        private void drawCenteredString(Graphics2D g2d, String text, Font font, int x, int intY) {
            g2d.setFont(font);
            FontMetrics metrics = g2d.getFontMetrics(font);
            int textX = x - metrics.stringWidth(text) / 2;
            int textY = intY + metrics.getAscent() / 2;
            g2d.drawString(text, textX, textY);
        }
    }
}


/**
 * Represents a power-up that appears in the game.
 */
class PowerUp {
    double x, y;
    final Type type;
    private final int SIZE = 20; // Radius of the power-up
    public final long creationTime; // New field to store creation time

    public enum Type {
        FREEZE,
        ATOM_BOOM
    }

    public PowerUp(Type type, Random random, int screenWidth, int screenHeight) {
        this.type = type;
        // Spawn randomly within the screen, with some padding from edges
        this.x = random.nextInt(screenWidth - 2 * SIZE) + SIZE;
        this.y = random.nextInt(screenHeight - 2 * SIZE) + SIZE;
        this.creationTime = System.currentTimeMillis(); // Initialize creation time
    }

    public void draw(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw the base circle
        g2d.setColor(Color.GRAY.darker());
        g2d.fillOval((int) x - SIZE / 2, (int) y - SIZE / 2, SIZE, SIZE);
        g2d.setColor(Color.WHITE);
        g2d.drawOval((int) x - SIZE / 2, (int) y - SIZE / 2, SIZE, SIZE);

        // Draw type-specific symbol/color
        switch (type) {
            case FREEZE:
                g2d.setColor(Color.CYAN); // Ice blue
                // Draw a simple snowflake/ice symbol
                g2d.drawLine((int)x, (int)y - SIZE/4, (int)x, (int)y + SIZE/4); // Vertical line
                g2d.drawLine((int)x - SIZE/4, (int)y, (int)x + SIZE/4, (int)y); // Horizontal line
                g2d.drawLine((int)(x - SIZE/4 * 0.7), (int)(y - SIZE/4 * 0.7), (int)(x + SIZE/4 * 0.7), (int)(y + SIZE/4 * 0.7)); // Diagonal 1
                g2d.drawLine((int)(x + SIZE/4 * 0.7), (int)(y - SIZE/4 * 0.7), (int)(x - SIZE/4 * 0.7), (int)(y + SIZE/4 * 0.7)); // Diagonal 2
                break;
            case ATOM_BOOM:
                g2d.setColor(Color.GREEN.brighter()); // Explosive green
                // Draw a simple radiation symbol
                int symbolSize = (int)(SIZE * 0.6);
                g2d.setColor(Color.GREEN.brighter());
                g2d.fillOval((int)x - symbolSize/2, (int)y - symbolSize/2, symbolSize, symbolSize);
                g2d.setColor(Color.BLACK);
                g2d.fillOval((int)x - symbolSize/4, (int)y - symbolSize/4, symbolSize/2, symbolSize/2);
                g2d.drawArc((int)x - symbolSize/2, (int)y - symbolSize/2, symbolSize, symbolSize, 30, 60);
                g2d.drawArc((int)x - symbolSize/2, (int)y - symbolSize/2, symbolSize, symbolSize, 150, 60);
                g2d.drawArc((int)x - symbolSize/2, (int)y - symbolSize/2, symbolSize, symbolSize, 270, 60);
                break;
        }
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x - SIZE / 2, (int) y - SIZE / 2, SIZE, SIZE);
    }

    /**
     * Checks if the power-up is within the screen bounds.
     * @param screenWidth Width of the game screen.
     * @param screenHeight Height of the game screen.
     * @return true if on screen, false otherwise.
     */
    boolean onScreen(int screenWidth, int screenHeight) {
        return x >= -SIZE && x <= screenWidth + SIZE && y >= -SIZE && y <= screenHeight + SIZE;
    }
}
