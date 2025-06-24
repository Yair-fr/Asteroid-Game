import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections; // For sorting high scores
import java.util.Comparator;  // For sorting high scores
import java.util.Iterator;
import java.util.List;
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

    // Hyperspace constants
    private static final long HYPER_ACTIVE_DURATION = 2000; // 2 seconds of hyper-speed
    private static final long HYPER_RECHARGE_DURATION = 10000; // 10 seconds to recharge hyper-fuel
    private static final double HYPER_SPEED_MULTIPLIER = 2.0;

    // Starfield constants for the start screen
    private static final int MAX_STARS = 150; // Number of stars in the background
    private final ArrayList<Star> stars = new ArrayList<>(); // Starfield for background

    // Game state variables
    Timer timer;
    Ship ship1, ship2; // Two ships for multiplayer
    ArrayList<Bullet> bullets = new ArrayList<>();
    ArrayList<Asteroid> asteroids = new ArrayList<>();
    ArrayList<PopEffect> effects = new ArrayList<>();
    boolean up1, left1, right1; // Player 1 movement controls
    boolean up2, left2, right2; // Player 2 movement controls

    // Single-press action flags (consumed after one tick)
    boolean shootRequested1, hyperspaceRequested1;
    boolean shootRequested2, hyperspaceRequested2;

    int score = 0; // Single player score, or combined if desired later
    int lives1, lives2; // Lives for each player
    GameState state = GameState.START;
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

    // Hyperspace state for each player
    private boolean hyperSpeedActive1 = false;
    private long hyperspaceActivationTime1 = 0;
    private long hyperFuelRefillTime1 = 0; // When hyper-fuel will be full again

    private boolean hyperSpeedActive2 = false;
    private long hyperspaceActivationTime2 = 0;
    private long hyperFuelRefillTime2 = 0;

    // High Score management
    private List<HighScoreEntry> highScores = new ArrayList<>();
    private static final String HIGHSCORE_FILE = "highscores.dat";

    // Username input for new high scores and multiplayer names
    private String userName1 = "Player 1";
    private String userName2 = "Player 2";
    private String tempUserNameInput = ""; // Used for JOptionPane input

    // Ship pattern configuration
    private Ship.Pattern player1ShipPattern = Ship.Pattern.NONE; // Default pattern
    private Ship.Pattern player2ShipPattern = Ship.Pattern.NONE; // Default pattern

    // Configurable keys for multiplayer
    private int player1ShootKey = KeyEvent.VK_SPACE;
    private int player1HyperspaceKey = KeyEvent.VK_H;
    private int player2ShootKey = KeyEvent.VK_C;
    private int player2HyperspaceKey = KeyEvent.VK_V;

    // Flags to track if shoot/hyperspace keys are currently held down (to prevent repeated triggers from keyPressed)
    private boolean player1ShootKeyHeld = false;
    private boolean player1HyperspaceKeyHeld = false;
    private boolean player2ShootKeyHeld = false;
    private boolean player2HyperspaceKeyHeld = false;


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
        initGame();
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
        asteroids.clear();
        effects.clear();
        score = 0; // Reset score (will be for current player in single, or combined if needed)
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

        hyperSpeedActive1 = false;
        hyperspaceActivationTime1 = 0;
        hyperFuelRefillTime1 = System.currentTimeMillis(); // Hyper-fuel starts full

        hyperSpeedActive2 = false;
        hyperspaceActivationTime2 = 0;
        hyperFuelRefillTime2 = System.currentTimeMillis(); // Hyper-fuel starts full

        up1 = left1 = right1 = false; // Movement flags
        up2 = left2 = right2 = false;

        shootRequested1 = hyperspaceRequested1 = false; // Action flags
        shootRequested2 = hyperspaceRequested2 = false;

        // Reset key held flags
        player1ShootKeyHeld = false;
        player1HyperspaceKeyHeld = false;
        player2ShootKeyHeld = false;
        player2HyperspaceKeyHeld = false;

        // Load high score list at game initialization
        loadHighScores();
        // If high scores were loaded, set username to the top scorer's name for default prompt
        if (!highScores.isEmpty()) {
            tempUserNameInput = highScores.get(0).getUserName();
        } else {
            tempUserNameInput = "Player"; // Default if no scores
        }

        // Populate stars for the start screen background
        stars.clear();
        populateStars(WIDTH, HEIGHT, MAX_STARS);
    }

    /**
     * Spawns the initial set of asteroids for a new game.
     */
    private void spawnInitialAsteroids() {
        for(int i = 0; i < 3; i++) {
            asteroids.add(new Asteroid(random, 0)); // Initial asteroids with score 0
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

            // Single Player button
            drawButton(g2d, "Single Player", WIDTH / 2, HEIGHT / 2 - 40, 350, 50, () -> {
                tempUserNameInput = JOptionPane.showInputDialog(this, "Enter your username:", userName1);
                if (tempUserNameInput != null && !tempUserNameInput.trim().isEmpty()) {
                    userName1 = tempUserNameInput.trim();
                } else {
                    userName1 = "Player 1";
                }
                player1ShipPattern = Ship.Pattern.NONE; // Default for single player
                initGame(); // Initialize common game elements
                spawnInitialAsteroids(); // Spawn asteroids specific to playing
                state = GameState.PLAYING_SINGLE;
            });

            // Multiplayer (Customize Names) button
            drawButton(g2d, "Multiplayer (Customize Names)", WIDTH / 2, HEIGHT / 2 + 30, 350, 50, () -> {
                state = GameState.MULTIPLAYER_SETUP_NAMES;
            });

            // New: Multiplayer (Quick Play) button - directly to QR display
            drawButton(g2d, "Multiplayer (Quick Play)", WIDTH / 2, HEIGHT / 2 + 100, 350, 50, () -> {
                // Usernames remain default ("Player 1", "Player 2") unless previously changed
                player1ShipPattern = Ship.Pattern.ZEBRA; // Default for quick play
                player2ShipPattern = Ship.Pattern.DOTTED; // Default for quick play
                initGame(); // Make sure to init for quick play to apply patterns
                state = GameState.MULTIPLAYER_DISPLAY_QRS;
            });

            // The "HOW TO PLAY" box drawing has been removed.
            // drawHowToPlayBox(g2d, 490, 150, 280, 250); // Adjusted dimensions for better fit

            // Display High Score on Start screen
            g2d.setFont(new Font("Arial", Font.PLAIN, 24));
            drawCenteredString(g2d, "High Scores:", new Font("Arial", Font.BOLD, 28), HEIGHT / 2 + 340);
            int scoreY = HEIGHT / 2 + 370;
            for (int i = 0; i < Math.min(highScores.size(), 5); i++) { // Display top 5 scores
                HighScoreEntry entry = highScores.get(i);
                drawCenteredString(g2d, (i + 1) + ". " + entry.getUserName() + ": " + entry.getScore(),
                                   new Font("Arial", Font.PLAIN, 20), scoreY + (i * 25));
            }


            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            drawCenteredString(g2d, "Yair FR©", new Font("Arial", Font.PLAIN, 16), HEIGHT - 20); // Copyright at bottom middle

        } else if (state == GameState.MULTIPLAYER_SETUP_NAMES) {
            drawCenteredString(g2d, "Multiplayer Setup", new Font("Arial", Font.BOLD, 48), HEIGHT / 4 - 80);

            // Player 1 Name
            g2d.setFont(new Font("Arial", Font.PLAIN, 24));
            drawCenteredString(g2d, "Player 1 Name: " + userName1, new Font("Arial", Font.PLAIN, 24), HEIGHT / 4 - 20);
            drawButton(g2d, "Edit Player 1 Name", WIDTH / 2, HEIGHT / 4 + 20, 200, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Enter Player 1 username:", userName1);
                if (input != null && !input.trim().isEmpty()) {
                    userName1 = input.trim();
                }
            });

            // Player 1 Ship Pattern Selection
            g2d.drawString("Player 1 Ship:", WIDTH / 2 - 100, HEIGHT / 4 + 80);
            drawButton(g2d, "Default", WIDTH / 2 - 150, HEIGHT / 4 + 120, 120, 40, () -> player1ShipPattern = Ship.Pattern.NONE);
            drawButton(g2d, "Zebra", WIDTH / 2, HEIGHT / 4 + 120, 120, 40, () -> player1ShipPattern = Ship.Pattern.ZEBRA);
            drawButton(g2d, "Dotted", WIDTH / 2 + 150, HEIGHT / 4 + 120, 120, 40, () -> player1ShipPattern = Ship.Pattern.DOTTED);
            drawCenteredString(g2d, "Current: " + player1ShipPattern, new Font("Arial", Font.PLAIN, 16), HEIGHT / 4 + 160);

            // Player 1 Control Keys
            g2d.drawString("Player 1 Controls:", WIDTH / 2 - 100, HEIGHT / 2 + 20);
            drawButton(g2d, "Shoot: " + KeyEvent.getKeyText(player1ShootKey), WIDTH / 2 - 100, HEIGHT / 2 + 60, 150, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Press key for Player 1 Shoot:");
                if (input != null && !input.isEmpty() && input.length() == 1) { // Ensure only one character
                    player1ShootKey = input.toUpperCase().charAt(0);
                }
            });
            drawButton(g2d, "Hyper: " + KeyEvent.getKeyText(player1HyperspaceKey), WIDTH / 2 + 100, HEIGHT / 2 + 60, 150, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Press key for Player 1 Hyperspace:");
                if (input != null && !input.isEmpty() && input.length() == 1) { // Ensure only one character
                    player1HyperspaceKey = input.toUpperCase().charAt(0);
                }
            });


            // Player 2 Name
            g2d.setFont(new Font("Arial", Font.PLAIN, 24));
            drawCenteredString(g2d, "Player 2 Name: " + userName2, new Font("Arial", Font.PLAIN, 24), HEIGHT / 2 + 120);
            drawButton(g2d, "Edit Player 2 Name", WIDTH / 2, HEIGHT / 2 + 160, 200, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Enter Player 2 username:", userName2);
                if (input != null && !input.trim().isEmpty()) {
                    userName2 = input.trim();
                }
            });

            // Player 2 Ship Pattern Selection
            g2d.drawString("Player 2 Ship:", WIDTH / 2 - 100, HEIGHT / 2 + 220);
            drawButton(g2d, "Default", WIDTH / 2 - 150, HEIGHT / 2 + 260, 120, 40, () -> player2ShipPattern = Ship.Pattern.NONE);
            drawButton(g2d, "Zebra", WIDTH / 2, HEIGHT / 2 + 260, 120, 40, () -> player2ShipPattern = Ship.Pattern.ZEBRA);
            drawButton(g2d, "Dotted", WIDTH / 2 + 150, HEIGHT / 2 + 260, 120, 40, () -> player2ShipPattern = Ship.Pattern.DOTTED);
            drawCenteredString(g2d, "Current: " + player2ShipPattern, new Font("Arial", Font.PLAIN, 16), HEIGHT / 2 + 300);

            // Player 2 Control Keys
            g2d.drawString("Player 2 Controls:", WIDTH / 2 - 100, HEIGHT - 100);
            drawButton(g2d, "Shoot: " + KeyEvent.getKeyText(player2ShootKey), WIDTH / 2 - 100, HEIGHT - 60, 150, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Press key for Player 2 Shoot:");
                if (input != null && !input.isEmpty() && input.length() == 1) { // Ensure only one character
                    player2ShootKey = input.toUpperCase().charAt(0);
                }
            });
            drawButton(g2d, "Hyper: " + KeyEvent.getKeyText(player2HyperspaceKey), WIDTH / 2 + 100, HEIGHT - 60, 150, 40, () -> {
                String input = JOptionPane.showInputDialog(this, "Press key for Player 2 Hyperspace:");
                if (input != null && !input.isEmpty() && input.length() == 1) { // Ensure only one character
                    player2HyperspaceKey = input.toUpperCase().charAt(0);
                }
            });


            drawButton(g2d, "Generate Controllers", WIDTH - 150, HEIGHT - 30, 250, 60, () -> { // Moved to bottom right
                initGame(); // Initialize game with current settings before displaying QRs
                state = GameState.MULTIPLAYER_DISPLAY_QRS;
            });
            drawButton(g2d, "Back to Main Menu", WIDTH / 2 - 250, HEIGHT - 30, 200, 40, () -> state = GameState.START); // Adjusted position

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
            g2d.drawString("Shoot: " + KeyEvent.getKeyText(player1ShootKey) + ", Hyper: " + KeyEvent.getKeyText(player1HyperspaceKey), WIDTH / 4 - 100, HEIGHT / 3 + 195);


            // Player 2 QR Code and Controls
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 24));
            g2d.drawString(userName2 + "'s Controller", 3 * WIDTH / 4 - 100, HEIGHT / 3 + 20);
            drawQRCodePlaceholder(g2d, 3 * WIDTH / 4 - 50, HEIGHT / 3 + 50, 100, "Player 2 URL");
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.drawString("Controls: W, A, D", 3 * WIDTH / 4 - 100, HEIGHT / 3 + 170);
            g2d.drawString("Shoot: " + KeyEvent.getKeyText(player2ShootKey) + ", Hyper: " + KeyEvent.getKeyText(player2HyperspaceKey), 3 * WIDTH / 4 - 100, HEIGHT / 3 + 195);

            drawButton(g2d, "Start Multiplayer Game", WIDTH / 2, HEIGHT - 100, 300, 60, () -> {
                initGame(); // Initialize common game elements for a new game session
                spawnInitialAsteroids(); // Spawn asteroids now that game is starting
                state = GameState.MULTIPLAYER_PLAYING;
            });
            drawButton(g2d, "Back to Setup", WIDTH / 2, HEIGHT - 30, 200, 40, () -> state = GameState.MULTIPLAYER_SETUP_NAMES);

        } else if (state == GameState.PLAYING_SINGLE || state == GameState.MULTIPLAYER_PLAYING) {
            ship1.draw(g2d);
            if (state == GameState.MULTIPLAYER_PLAYING) {
                ship2.draw(g2d);
            }

            for (Bullet b : bullets) b.draw(g2d);
            for (Asteroid a : asteroids) a.draw(g2d);
            for (PopEffect p : effects) p.draw(g2d);

            // UI elements for Player 1 (top left)
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.drawString("Score: " + score, 10, 20);
            g2d.drawString("Lives: " + lives1, 10, 40);

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

            int player1SectionWidth = barWidth * 2 + 50; // Width for fuel bar + bullet bar + gap
            int player2SectionWidth = barWidth * 2 + 50;
            int totalUIWidth = player1SectionWidth;
            if (state == GameState.MULTIPLAYER_PLAYING) {
                totalUIWidth += player2SectionWidth + 80; // Add space between player sections
            }
            int startX = (WIDTH - totalUIWidth) / 2;

            int fuel1X = startX;
            int bullets1X = fuel1X + barWidth + 50;

            // Draw hyper-fuel bar for Player 1
            g2d.setColor(Color.WHITE);
            g2d.drawString("Hyper-Fuel:", fuel1X, uiBottomY - labelOffset); // Removed player name

            double hyperFuelProgress1;
            if (hyperSpeedActive1) {
                hyperFuelProgress1 = 1.0 - (double)(System.currentTimeMillis() - hyperspaceActivationTime1) / HYPER_ACTIVE_DURATION;
            } else {
                long timeToRefill = hyperFuelRefillTime1 - System.currentTimeMillis();
                if (timeToRefill <= 0) {
                    hyperFuelProgress1 = 1.0; // Full
                } else {
                    hyperFuelProgress1 = 1.0 - (double)timeToRefill / HYPER_RECHARGE_DURATION;
                }
            }
            if (hyperFuelProgress1 < 0) hyperFuelProgress1 = 0;
            if (hyperFuelProgress1 > 1) hyperFuelProgress1 = 1;

            // Draw background of fuel bar
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(fuel1X, uiBottomY - barOffset, barWidth, barHeight);

            // Draw filled portion of fuel bar ("flowing water")
            g2d.setColor(Color.CYAN); // Changed color for fuel fill
            g2d.fillRect(fuel1X, uiBottomY - barOffset, (int)(barWidth * hyperFuelProgress1), barHeight);
            g2d.setColor(Color.YELLOW); // Draw border over filled part
            g2d.drawRect(fuel1X, uiBottomY - barOffset, barWidth, barHeight);

            // Draw refuel message if recharging for Player 1
            if (!hyperSpeedActive1 && System.currentTimeMillis() < hyperFuelRefillTime1) {
                g2d.setColor(Color.ORANGE); // Different color for refuel message
                String msg = "Refueling...";
                FontMetrics fm = g2d.getFontMetrics();
                int msgX = fuel1X + (barWidth - fm.stringWidth(msg)) / 2;
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
                int fuel2X = bullets1X + barWidth + 80; // Adjusted spacing between player sections
                int bullets2X = fuel2X + barWidth + 50;

                // Draw hyper-fuel bar for Player 2
                g2d.setColor(Color.WHITE);
                g2d.drawString("Hyper-Fuel:", fuel2X, uiBottomY - labelOffset); // Removed player name
                double hyperFuelProgress2;
                if (hyperSpeedActive2) {
                    hyperFuelProgress2 = 1.0 - (double)(System.currentTimeMillis() - hyperspaceActivationTime2) / HYPER_ACTIVE_DURATION;
                } else {
                    long timeToRefill = hyperFuelRefillTime2 - System.currentTimeMillis();
                    if (timeToRefill <= 0) {
                        hyperFuelProgress2 = 1.0;
                    } else {
                        hyperFuelProgress2 = 1.0 - (double)timeToRefill / HYPER_RECHARGE_DURATION;
                    }
                }
                if (hyperFuelProgress2 < 0) hyperFuelProgress2 = 0;
                if (hyperFuelProgress2 > 1) hyperFuelProgress2 = 1;

                g2d.setColor(Color.DARK_GRAY);
                g2d.fillRect(fuel2X, uiBottomY - barOffset, barWidth, barHeight);

                g2d.setColor(Color.CYAN);
                g2d.fillRect(fuel2X, uiBottomY - barOffset, (int)(barWidth * hyperFuelProgress2), barHeight);
                g2d.setColor(Color.YELLOW);
                g2d.drawRect(fuel2X, uiBottomY - barOffset, barWidth, barHeight);

                // Draw refuel message if recharging for Player 2
                if (!hyperSpeedActive2 && System.currentTimeMillis() < hyperFuelRefillTime2) {
                    g2d.setColor(Color.ORANGE);
                    String msg = "Refueling...";
                    FontMetrics fm = g2d.getFontMetrics();
                    int msgX = fuel2X + (barWidth - fm.stringWidth(msg)) / 2;
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

            // Display High Score
            g2d.setFont(new Font("Arial", Font.PLAIN, 24));
            if (!highScores.isEmpty()) {
                drawCenteredString(g2d, "High Scores:", new Font("Arial", Font.BOLD, 28), HEIGHT / 2 + 50);
                int scoreY = HEIGHT / 2 + 80;
                for (int i = 0; i < Math.min(highScores.size(), 5); i++) { // Display top 5 scores
                    HighScoreEntry entry = highScores.get(i);
                    drawCenteredString(g2d, (i + 1) + ". " + entry.getUserName() + ": " + entry.getScore(),
                                       new Font("Arial", Font.PLAIN, 20), scoreY + (i * 25));
                }
            }

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
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        FontMetrics metrics = g2d.getFontMetrics();
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent();
        g2d.drawString(text, textX, textY);

        // Store button bounds and action for mouse click handling
        buttonActions.put(new Rectangle(x, y, width, height), action);
    }

    private final java.util.Map<Rectangle, Runnable> buttonActions = new java.util.HashMap<>();

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
     * This method is called repeatedly by the Swing Timer to update game logic.
     * @param e The ActionEvent from the timer.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.currentTimeMillis();

        if (state == GameState.PLAYING_SINGLE || state == GameState.MULTIPLAYER_PLAYING) {
            // --- Player 1 Logic ---

            // Passive reload for Player 1
            if (!reloading1 && currentBullets1 < MAX_BULLETS) {
                if (now - lastReloadTickTime1 >= RELOAD_INCREMENTAL_DURATION) {
                    currentBullets1++;
                    lastReloadTickTime1 = now;
                }
            }

            // Full reload (when empty) for Player 1
            if (reloading1) {
                if (now - reloadStartTime1 > RELOAD_FULL_DURATION) {
                    reloading1 = false;
                    currentBullets1 = MAX_BULLETS; // Reload magazine
                    lastReloadTickTime1 = now; // Reset passive reload timer too
                }
            }

            // Shooting logic for Player 1 (triggered by single press)
            if (shootRequested1) {
                if (!reloading1 && currentBullets1 > 0 && (now - lastShotTime1 > BULLET_COOLDOWN)) {
                    bullets.add(new Bullet(ship1));
                    currentBullets1--;
                    lastShotTime1 = now;
                    if (currentBullets1 == 0) {
                        reloading1 = true;
                        reloadStartTime1 = now;
                    }
                }
                shootRequested1 = false; // Consume the request
            }

            // Hyperspace logic for Player 1 (triggered by single press)
            if (hyperspaceRequested1) {
                if (!hyperSpeedActive1 && now >= hyperFuelRefillTime1) {
                    hyperSpeedActive1 = true;
                    hyperspaceActivationTime1 = now;
                    ship1.setInvincible();
                }
                hyperspaceRequested1 = false; // Consume the request
            }

            // Deactivate hyper-speed after duration for Player 1
            if (hyperSpeedActive1 && now - hyperspaceActivationTime1 > HYPER_ACTIVE_DURATION) {
                hyperSpeedActive1 = false;
                hyperFuelRefillTime1 = now + HYPER_RECHARGE_DURATION; // Start recharge cooldown
            }

            // Update ship 1
            ship1.update(up1, left1, right1, hyperSpeedActive1, HYPER_SPEED_MULTIPLIER, WIDTH, HEIGHT);

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
                        lastShotTime2 = now;
                        if (currentBullets2 == 0) {
                            reloading2 = true;
                            reloadStartTime2 = now;
                        }
                    }
                    shootRequested2 = false; // Consume the request
                }

                // Hyperspace logic for Player 2 (triggered by single press)
                if (hyperspaceRequested2) {
                    if (!hyperSpeedActive2 && now >= hyperFuelRefillTime2) {
                        hyperSpeedActive2 = true;
                        hyperspaceActivationTime2 = now;
                        ship2.setInvincible();
                    }
                    hyperspaceRequested2 = false; // Consume the request
                }

                // Deactivate hyper-speed after duration for Player 2
                if (hyperSpeedActive2 && now - hyperspaceActivationTime2 > HYPER_ACTIVE_DURATION) {
                    hyperSpeedActive2 = false;
                    hyperFuelRefillTime2 = now + HYPER_RECHARGE_DURATION;
                }

                // Update ship 2
                ship2.update(up2, left2, right2, hyperSpeedActive2, HYPER_SPEED_MULTIPLIER, WIDTH, HEIGHT);
            }


            bullets.forEach(b -> b.update(WIDTH, HEIGHT));
            asteroids.forEach(Asteroid::update);
            effects.forEach(PopEffect::update);

            if (state == GameState.PLAYING_SINGLE) {
                checkCollisionsSinglePlayer();
            } else { // MULTIPLAYER_PLAYING
                checkCollisionsMultiplayer();
            }
            removeOffscreen();

            if (random.nextInt(100) < (2 + score / 200)) {
                asteroids.add(new Asteroid(random, score));
            }

            // Check for game over (multiplayer condition)
            if (state == GameState.MULTIPLAYER_PLAYING && lives1 <= 0 && lives2 <= 0) {
                state = GameState.GAME_OVER;
                score = (lives1 > 0 ? score : 0) + (lives2 > 0 ? score : 0); // Combined score
                saveOrUpdateHighScore(userName1, score);
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
     * Handles key press events for player input.
     * @param e The KeyEvent generated.
     */
    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (state == GameState.PLAYING_SINGLE || state == GameState.MULTIPLAYER_PLAYING) {
            // Player 1 Controls
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
            // Handle hyperspace key press for single activation
            if (keyCode == player1HyperspaceKey) {
                if (!player1HyperspaceKeyHeld) {
                    hyperspaceRequested1 = true; // Request hyperspace for the next tick
                    player1HyperspaceKeyHeld = true; // Mark key as held
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
                // Handle hyperspace key press for single activation
                if (keyCode == player2HyperspaceKey) {
                    if (!player2HyperspaceKeyHeld) {
                        hyperspaceRequested2 = true;
                        player2HyperspaceKeyHeld = true;
                    }
                }
            }
        }

        // State transitions
        if (keyCode == KeyEvent.VK_ENTER) {
            if (state == GameState.GAME_OVER) {
                initGame(); // Re-initialize game state
                state = GameState.PLAYING_SINGLE; // Default to single player after game over
                spawnInitialAsteroids(); // Spawn asteroids for the new game
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
        if (state == GameState.PLAYING_SINGLE || state == GameState.MULTIPLAYER_PLAYING) {
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
            if (keyCode == player1HyperspaceKey) {
                player1HyperspaceKeyHeld = false;
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
                if (keyCode == player2HyperspaceKey) {
                    player2HyperspaceKeyHeld = false;
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
                    score += 10;
                    if (asteroid.size > Asteroid.MIN_SPLIT_SIZE) {
                        for (int i = 0; i < 2; i++) {
                            newAsteroids.add(new Asteroid(asteroid.x, asteroid.y, asteroid.size / 2, random));
                        }
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
                    state = GameState.GAME_OVER;
                    saveOrUpdateHighScore(userName1, score);
                } else {
                    ship1.resetPosition(WIDTH / 2, HEIGHT / 2);
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
                    score += 10; // Combined score for now
                    if (asteroid.size > Asteroid.MIN_SPLIT_SIZE) {
                        for (int i = 0; i < 2; i++) {
                            newAsteroids.add(new Asteroid(asteroid.x, asteroid.y, asteroid.size / 2, random));
                        }
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


    void removeOffscreen() {
        bullets.removeIf(b -> !b.onScreen(WIDTH, HEIGHT));
        asteroids.removeIf(a -> !a.onScreen(WIDTH, HEIGHT));
        effects.removeIf(p -> p.life <= 0);
    }

    /**
     * Saves or updates the high score for a given user.
     * @param username The username of the player.
     * @param score The score to save.
     */
    private void saveOrUpdateHighScore(String username, int score) {
        boolean userFound = false;
        for (HighScoreEntry entry : highScores) {
            if (entry.getUserName().equals(username)) {
                userFound = true;
                if (score > entry.getScore()) {
                    entry.score = score; // Update score if higher
                }
                break; // User found, no need to check further
            }
        }
        if (!userFound) {
            // User not in list, add new entry
            highScores.add(new HighScoreEntry(username, score));
        }
        Collections.sort(highScores, Comparator.comparingInt(HighScoreEntry::getScore).reversed());
        // Keep only the top 5 scores
        if (highScores.size() > 5) {
            highScores = highScores.subList(0, 5);
        }
        saveHighScores();
    }


    /**
     * Saves the current high score list to a file.
     */
    private void saveHighScores() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(HIGHSCORE_FILE))) {
            oos.writeObject(highScores);
        } catch (IOException e) {
            System.err.println("Error saving high scores: " + e.getMessage());
        }
    }

    /**
     * Loads the high score list from a file. If no file exists, initializes with an empty list.
     */
    private void loadHighScores() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(HIGHSCORE_FILE))) {
            highScores = (List<HighScoreEntry>) ois.readObject();
            // Ensure the loaded list is sorted, just in case
            Collections.sort(highScores, Comparator.comparingInt(HighScoreEntry::getScore).reversed());
        } catch (FileNotFoundException e) {
            System.out.println("High score file not found. Starting with an empty list.");
            highScores = new ArrayList<>(); // Initialize empty list
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading high scores: " + e.getMessage());
            highScores = new ArrayList<>(); // Fallback to empty list
        }
    }

    /**
     * Enum to define the different states of the game.
     */
    enum GameState { START, PLAYING_SINGLE, MULTIPLAYER_SETUP_NAMES, MULTIPLAYER_DISPLAY_QRS, MULTIPLAYER_PLAYING, GAME_OVER }

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
    public final int SIZE = 20; // Represents roughly half width/height for bounding box and drawing
    private final int INVINCIBILITY_DURATION = 1500; // milliseconds after spawn/hit/hyperspace
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
     * @param hyperSpeedActive Whether hyper-speed is currently active.
     * @param hyperSpeedMultiplier The multiplier to apply for hyper-speed acceleration.
     * @param screenWidth Width of the game screen.
     * @param screenHeight Height of the game screen.
     */
    void update(boolean up, boolean left, boolean right, boolean hyperSpeedActive, double hyperSpeedMultiplier, int screenWidth, int screenHeight) {
        double currentAcceleration = ACCELERATION;
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

            // Red shield circle: a bit bigger than the ship
            int shieldRadius = SIZE + 5; // SIZE is half width. Shield radius is ship's half width + 5.
            int shieldDiameter = shieldRadius * 2;

            g2d.setColor(new Color(255, 0, 0, 150)); // Semi-transparent red
            // Draw the shield centered on the ship's origin (which is currently translated to x,y)
            g2d.fill(new Ellipse2D.Double(-shieldRadius, -shieldRadius, shieldDiameter, shieldDiameter));
            g2d.setColor(Color.RED);
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
    private final int MAX_SPEED = 3; // Maximum base speed for asteroids
    private final int BASE_SIZE = 15; // Base radius for new asteroids
    static final int MIN_SPLIT_SIZE = 10; // Minimum size for an asteroid to split into smaller ones

    /**
     * Constructor for initial asteroids, spawning from screen edges.
     * @param random The Random instance from the game.
     * @param score The current game score, used to influence speed.
     */
    Asteroid(Random random, int score) {
        this.random = random;
        size = BASE_SIZE + random.nextInt(15); // Random size between 15 and 29
        setRandomSpawnLocation(AsteroidGame.WIDTH, AsteroidGame.HEIGHT, score);
        // Random gray-ish color for variety
        this.color = new Color(random.nextInt(156) + 100, random.nextInt(156) + 100, random.nextInt(156) + 100);
    }

    /**
     * Constructor for splitting asteroids, inheriting position and having a new size.
     * @param startX X position of the parent asteroid.
     * @param startY Y position of the parent asteroid.
     * @param newSize New size of the split asteroid.
     * @param random The Random instance from the game.
     */
    Asteroid(double startX, double startY, int newSize, Random random) {
        this.random = random;
        this.x = startX;
        this.y = startY;
        this.size = newSize;

        // Give split asteroids a random initial velocity in a new direction
        double angle = random.nextDouble() * 2 * Math.PI; // Random angle in radians
        double speed = 2 + random.nextDouble(); // Slightly faster than initial asteroids
        this.dx = Math.cos(angle) * speed;
        this.dy = Math.sin(angle) * speed;
        this.color = new Color(random.nextInt(156) + 100, random.nextInt(156) + 100, random.nextInt(156) + 100);
    }

    /**
     * Sets a random spawn location for an asteroid from one of the four screen edges.
     * @param screenWidth Width of the game screen.
     * @param screenHeight Height of the game screen.
     * @param score Current game score, influences speed.
     */
    private void setRandomSpawnLocation(int screenWidth, int screenHeight, int score) {
        int edge = random.nextInt(4); // 0: top, 1: right, 2: bottom, 3: left
        double speedFactor = 1 + (double) score / 500; // Speed increases with score

        switch (edge) {
            case 0: // Top edge
                x = random.nextInt(screenWidth);
                y = -size; // Start just off-screen
                dx = (random.nextDouble() * 2 - 1) * MAX_SPEED * speedFactor; // Random horizontal speed
                dy = (0.5 + random.nextDouble() * 0.5) * MAX_SPEED * speedFactor; // Always move downwards
                break;
            case 1: // Right edge
                x = screenWidth + size; // Start just off-screen
                y = random.nextInt(screenHeight);
                dx = (-0.5 - random.nextDouble() * 0.5) * MAX_SPEED * speedFactor; // Always move leftwards
                dy = (random.nextDouble() * 2 - 1) * MAX_SPEED * speedFactor; // Random vertical speed
                break;
            case 2: // Bottom edge
                x = random.nextInt(screenWidth);
                y = screenHeight + size; // Start just off-screen
                dx = (random.nextDouble() * 2 - 1) * MAX_SPEED * speedFactor;
                dy = (-0.5 - random.nextDouble() * 0.5) * MAX_SPEED * speedFactor; // Always move upwards
                break;
            case 3: // Left edge
                x = -size; // Start just off-screen
                y = random.nextInt(screenHeight);
                dx = (0.5 + random.nextDouble() * 0.5) * MAX_SPEED * speedFactor; // Always move rightwards
                dy = (random.nextDouble() * 2 - 1) * MAX_SPEED * speedFactor;
                break;
        }
    }

    /**
     * Updates the asteroid's position based on its velocity.
     */
    void update() {
        x += dx;
        y += dy;
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
class HighScoreEntry implements Serializable { // Renamed from HighScore
    private static final long serialVersionUID = 1L; // Recommended for Serializable
    String userName;
    int score;

    public HighScoreEntry(String userName, int score) {
        this.userName = userName;
        this.score = score;
    }

    public String getUserName() {
        return userName;
    }

    public int getScore() {
        return score;
    }
}
