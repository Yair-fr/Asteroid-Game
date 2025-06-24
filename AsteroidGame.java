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
    private static final long BULLET_COOLDOWN = 1000; // 1 second delay between shots
    private static final int MAX_BULLETS = 5; // Max bullets in magazine
    private static final long RELOAD_DURATION = 5000; // 5 seconds to reload

    // Hyperspace constants
    private static final long HYPER_ACTIVE_DURATION = 2000; // 2 seconds of hyper-speed
    private static final long HYPER_RECHARGE_DURATION = 10000; // 10 seconds to recharge hyper-fuel
    private static final double HYPER_SPEED_MULTIPLIER = 2.0; // Changed from 4.0 to 2.0 as requested

    // Starfield constants for the start screen
    private static final int MAX_STARS = 150; // Number of stars in the background
    private final ArrayList<Star> stars = new ArrayList<>(); // Starfield for background

    // Game state variables
    Timer timer;
    Ship ship;
    ArrayList<Bullet> bullets = new ArrayList<>();
    ArrayList<Asteroid> asteroids = new ArrayList<>();
    ArrayList<PopEffect> effects = new ArrayList<>();
    boolean up, left, right, space, hyperspacePressed;
    int score = 0;
    int lives = INITIAL_LIVES;
    GameState state = GameState.START;
    long lastShotTime = 0;

    // Bullet magazine and reload state
    private int currentBullets = MAX_BULLETS;
    private boolean reloading = false;
    private long reloadStartTime = 0;

    // Hyperspace state
    private boolean hyperSpeedActive = false;
    private long hyperspaceActivationTime = 0;
    private long hyperFuelRefillTime = 0; // Time when hyper-fuel will be ready again

    // High Score management
    private List<HighScoreEntry> highScores = new ArrayList<>(); // Changed to a List
    private static final String HIGHSCORE_FILE = "highscores.dat"; // Changed filename to reflect list

    // Username input for new high scores
    private String userName = "Player"; // Default username

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
     */
    private void initGame() {
        ship = new Ship(WIDTH / 2, HEIGHT / 2);
        bullets.clear();
        asteroids.clear();
        effects.clear();
        score = 0;
        lives = INITIAL_LIVES;
        lastShotTime = 0;
        currentBullets = MAX_BULLETS; // Reset bullets
        reloading = false; // Reset reload state
        reloadStartTime = 0; // Reset reload timer

        // Reset hyperspace state
        hyperSpeedActive = false;
        hyperspaceActivationTime = 0;
        hyperFuelRefillTime = System.currentTimeMillis(); // Hyper-fuel is full at start

        up = left = right = space = hyperspacePressed = false;

        // Load high score list at game initialization
        loadHighScores();
        // If high scores were loaded, set username to the top scorer's name
        if (!highScores.isEmpty()) {
            userName = highScores.get(0).getUserName();
        }

        // Populate stars for the start screen background
        stars.clear();
        populateStars(WIDTH, HEIGHT, MAX_STARS);

        // Asteroids are spawned only when game state becomes PLAYING
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

        // Now draw all game elements using the game's internal WIDTH/HEIGHT (800x600)
        // The Graphics2D context will automatically scale and translate them

        if (state == GameState.START) {
            // Draw moving star background
            for (Star star : stars) {
                star.draw(g2d);
            }

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 60)); // Larger title
            drawCenteredString(g2d, "ASTEROID GAME", new Font("Arial", Font.BOLD, 60), HEIGHT / 3);

            g2d.setFont(new Font("Arial", Font.PLAIN, 24));
            drawCenteredString(g2d, "Press ENTER to Start", new Font("Arial", Font.PLAIN, 24), HEIGHT / 2);

            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            drawCenteredString(g2d, "Controls:", new Font("Arial", Font.PLAIN, 18), HEIGHT / 2 + 50);
            drawCenteredString(g2d, "UP Arrow: Thrust", new Font("Arial", Font.PLAIN, 18), HEIGHT / 2 + 75);
            drawCenteredString(g2d, "LEFT/RIGHT Arrows: Rotate", new Font("Arial", Font.PLAIN, 18), HEIGHT / 2 + 100);
            drawCenteredString(g2d, "SPACE: Fire", new Font("Arial", Font.PLAIN, 18), HEIGHT / 2 + 125);
            drawCenteredString(g2d, "H: Hyper-Speed", new Font("Arial", Font.PLAIN, 18), HEIGHT / 2 + 150);

            // Display High Score on Start screen
            g2d.setFont(new Font("Arial", Font.PLAIN, 24));
            drawCenteredString(g2d, "High Scores:", new Font("Arial", Font.BOLD, 28), HEIGHT / 2 + 200);
            int scoreY = HEIGHT / 2 + 230;
            for (int i = 0; i < Math.min(highScores.size(), 5); i++) { // Display top 5 scores
                HighScoreEntry entry = highScores.get(i);
                drawCenteredString(g2d, (i + 1) + ". " + entry.getUserName() + ": " + entry.getScore(),
                                   new Font("Arial", Font.PLAIN, 20), scoreY + (i * 25));
            }


            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            drawCenteredString(g2d, "Yair FR©", new Font("Arial", Font.PLAIN, 16), HEIGHT - 20); // Copyright at bottom middle

        } else if (state == GameState.PLAYING) {
            ship.draw(g2d);
            for (Bullet b : bullets) b.draw(g2d);
            for (Asteroid a : asteroids) a.draw(g2d);
            for (PopEffect p : effects) p.draw(g2d);

            // UI elements like score and lives (top left)
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.drawString("Score: " + score, 10, 20);
            g2d.drawString("Lives: " + lives, 10, 40);

            // Positioned at bottom middle
            int uiBottomY = HEIGHT - 20; // Slightly above bottom edge
            int barWidth = 100;
            int barHeight = 10;
            int spacing = 40; // Space between fuel bar and bullet count

            // Calculate starting X for the centered block of UI elements
            int totalUIElementsWidth = barWidth + spacing + g2d.getFontMetrics(new Font("Arial", Font.PLAIN, 18)).stringWidth("Bullets: " + MAX_BULLETS + "/" + MAX_BULLETS);
            int startX = (WIDTH - totalUIElementsWidth) / 2;

            int fuelX = startX;
            int bulletsX = fuelX + barWidth + spacing;


            // Draw hyper-fuel bar
            g2d.setColor(Color.WHITE);
            g2d.drawString("Hyper-Fuel:", fuelX, uiBottomY - 30);
            double hyperFuelProgress;
            if (hyperSpeedActive) {
                hyperFuelProgress = 1.0 - (double)(System.currentTimeMillis() - hyperspaceActivationTime) / HYPER_ACTIVE_DURATION;
            } else {
                long timeToRefill = hyperFuelRefillTime - System.currentTimeMillis();
                if (timeToRefill <= 0) {
                    hyperFuelProgress = 1.0; // Full
                } else {
                    hyperFuelProgress = 1.0 - (double)timeToRefill / HYPER_RECHARGE_DURATION;
                }
            }
            if (hyperFuelProgress < 0) hyperFuelProgress = 0;
            if (hyperFuelProgress > 1) hyperFuelProgress = 1;

            g2d.setColor(Color.YELLOW);
            g2d.drawRect(fuelX, uiBottomY - 15, barWidth, barHeight); // Border of hyper-fuel bar
            g2d.fillRect(fuelX, uiBottomY - 15, (int)(barWidth * hyperFuelProgress), barHeight); // Filled part

            if (!hyperSpeedActive && System.currentTimeMillis() < hyperFuelRefillTime) {
                g2d.setColor(Color.WHITE);
                g2d.drawString("Hyper-Recharging...", fuelX, uiBottomY); // Below fuel bar
            }

            // Draw Bullets display
            g2d.setColor(Color.WHITE);
            g2d.drawString("Bullets: " + currentBullets + "/" + MAX_BULLETS, bulletsX, uiBottomY - 30);

            // Draw reload message if reloading
            if (reloading) {
                g2d.setColor(Color.WHITE);
                g2d.drawString("Reloading...", bulletsX, uiBottomY); // Below bullets count
                double progress = (double)(System.currentTimeMillis() - reloadStartTime) / RELOAD_DURATION;
                if (progress > 1.0) progress = 1.0;
                g2d.drawRect(bulletsX, uiBottomY - 15, barWidth, barHeight);
                g2d.fillRect(bulletsX, uiBottomY - 15, (int)(barWidth * progress), barHeight);
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
    }

    /**
     * This method is called repeatedly by the Swing Timer to update game logic.
     * @param e The ActionEvent from the timer.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == GameState.PLAYING) {
            long now = System.currentTimeMillis();

            // Handle reload logic
            if (reloading) {
                if (now - reloadStartTime > RELOAD_DURATION) {
                    reloading = false;
                    currentBullets = MAX_BULLETS; // Reload magazine
                }
            }

            // Handle hyper-speed activation and deactivation
            if (hyperspacePressed && !hyperSpeedActive && now >= hyperFuelRefillTime) {
                hyperSpeedActive = true;
                hyperspaceActivationTime = now;
                ship.setInvincible(); // Grant invincibility when hyper-speed starts
            }
            if (hyperSpeedActive && now - hyperspaceActivationTime > HYPER_ACTIVE_DURATION) {
                hyperSpeedActive = false;
                hyperFuelRefillTime = now + HYPER_RECHARGE_DURATION; // Start recharge cooldown
            }

            // Update game elements, passing hyperspeed status AND multiplier
            ship.update(up, left, right, hyperSpeedActive, HYPER_SPEED_MULTIPLIER, WIDTH, HEIGHT);

            if (space) fireBullet();

            bullets.forEach(b -> b.update(WIDTH, HEIGHT));
            asteroids.forEach(Asteroid::update);
            effects.forEach(PopEffect::update);

            checkCollisions();
            removeOffscreen();

            // Spawn new asteroids (frequency increases with score)
            // Asteroids spawn more frequently and slightly faster as score increases
            if (random.nextInt(100) < (2 + score / 200)) { // Adjusted spawn rate
                asteroids.add(new Asteroid(random, score));
            }
        } else if (state == GameState.START) {
            // Update stars movement only on start screen
            for (Star star : stars) {
                star.update();
            }
            // Add new stars if needed to maintain density
            if (stars.size() < MAX_STARS) {
                stars.add(new Star(random, WIDTH, HEIGHT));
            }
        }
        repaint(); // Request a repaint of the panel
    }

    /**
     * Handles key press events for player input.
     * @param e The KeyEvent generated.
     */
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> up = true;
            case KeyEvent.VK_LEFT -> left = true;
            case KeyEvent.VK_RIGHT -> right = true;
            case KeyEvent.VK_SPACE -> space = true;
            case KeyEvent.VK_H -> hyperspacePressed = true;
            case KeyEvent.VK_ENTER -> {
                if (state == GameState.START) {
                    // Prompt for username only when starting from the initial START state
                    String inputName = JOptionPane.showInputDialog(this, "Enter your username:", userName);
                    if (inputName != null && !inputName.trim().isEmpty()) {
                        userName = inputName.trim();
                    } else {
                        userName = "Player"; // Fallback if input is empty or cancelled
                    }
                    initGame(); // Re-initialize game state with potentially new username
                    state = GameState.PLAYING;
                    // Spawn initial asteroids only when starting playing
                    for(int i = 0; i < 3; i++) {
                        asteroids.add(new Asteroid(random, 0));
                    }
                } else if (state == GameState.GAME_OVER) {
                    initGame();
                    state = GameState.PLAYING;
                    for(int i = 0; i < 3; i++) {
                        asteroids.add(new Asteroid(random, 0));
                    }
                }
            }
        }
    }

    /**
     * Handles key release events to stop actions.
     * @param e The KeyEvent generated.
     */
    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> up = false;
            case KeyEvent.VK_LEFT -> left = false;
            case KeyEvent.VK_RIGHT -> right = false;
            case KeyEvent.VK_SPACE -> space = false;
            case KeyEvent.VK_H -> hyperspacePressed = false;
        }
    }

    /**
     * Not used for this game, but required by KeyListener interface.
     * @param e The KeyEvent generated.
     */
    @Override
    public void keyTyped(KeyEvent e) { /* Not used */ }

    /**
     * Fires a bullet from the ship if the cooldown allows and bullets are available.
     */
    void fireBullet() {
        long now = System.currentTimeMillis();
        // Only fire if not reloading and bullets are available and cooldown is met
        if (!reloading && currentBullets > 0 && (now - lastShotTime > BULLET_COOLDOWN)) {
            bullets.add(new Bullet(ship));
            currentBullets--; // Decrement bullet count
            lastShotTime = now;
            // If out of bullets, start reloading
            if (currentBullets == 0) {
                reloading = true;
                reloadStartTime = now;
            }
        }
    }

    /**
     * Checks for collisions between bullets and asteroids, and ship and asteroids.
     */
    void checkCollisions() {
        // Temporary list to hold new asteroids created from splitting
        List<Asteroid> newAsteroids = new ArrayList<>();

        // Bullet-Asteroid collisions
        Iterator<Bullet> bulletIterator = bullets.iterator();
        while (bulletIterator.hasNext()) {
            Bullet bullet = bulletIterator.next();
            Iterator<Asteroid> asteroidIterator = asteroids.iterator();
            while (asteroidIterator.hasNext()) {
                Asteroid asteroid = asteroidIterator.next();
                if (bullet.getBounds().intersects(asteroid.getBounds())) {
                    bulletIterator.remove(); // Remove the bullet
                    effects.add(new PopEffect(asteroid.x, asteroid.y, random)); // Add explosion effect
                    score += 10;

                    // Asteroid splitting logic: add new asteroids to the temporary list
                    if (asteroid.size > Asteroid.MIN_SPLIT_SIZE) {
                        for (int i = 0; i < 2; i++) {
                            newAsteroids.add(new Asteroid(asteroid.x, asteroid.y, asteroid.size / 2, random));
                        }
                    }
                    asteroidIterator.remove(); // Remove the hit asteroid
                    break; // Bullet can only hit one asteroid, so break inner loop
                }
            }
        }
        // Add all newly created asteroids to the main list AFTER the iteration is complete
        asteroids.addAll(newAsteroids);


        // Ship-Asteroid collisions
        if (ship.isInvincible()) { // Ship is temporarily invincible after respawn/hyperspace
            return; // No collision if invincible
        }

        for (Asteroid asteroid : asteroids) {
            if (asteroid.getBounds().intersects(ship.getBounds())) {
                lives--;
                effects.add(new PopEffect(ship.x, ship.y, random)); // Pop effect at ship location
                if (lives <= 0) {
                    state = GameState.GAME_OVER; // Game over if no lives left
                    // Save high score when game ends
                    // Check if current score is a new high score or updates an existing one
                    boolean userFound = false;
                    for (HighScoreEntry entry : highScores) {
                        if (entry.getUserName().equals(userName)) {
                            userFound = true;
                            if (score > entry.getScore()) {
                                entry.score = score; // Update score if higher
                                Collections.sort(highScores, Comparator.comparingInt(HighScoreEntry::getScore).reversed());
                                // Trim if necessary (e.g., if a new high score pushes others out)
                                if (highScores.size() > 5) {
                                    highScores = highScores.subList(0, 5);
                                }
                                saveHighScores();
                            }
                            break; // User found, no need to check further
                        }
                    }
                    if (!userFound) {
                        // User not in list, add new entry
                        highScores.add(new HighScoreEntry(userName, score));
                        Collections.sort(highScores, Comparator.comparingInt(HighScoreEntry::getScore).reversed());
                        // Keep only the top 5 scores
                        if (highScores.size() > 5) {
                            highScores = highScores.subList(0, 5);
                        }
                        saveHighScores();
                    }
                } else {
                    ship.resetPosition(WIDTH / 2, HEIGHT / 2); // Respawn ship in center
                }
                break; // Ship can only collide with one asteroid at a time (for simplicity)
            }
        }
    }

    /**
     * Removes off-screen bullets, asteroids, and expired pop effects.
     */
    void removeOffscreen() {
        bullets.removeIf(b -> !b.onScreen(WIDTH, HEIGHT)); // Bullets now disappear, not wrap
        asteroids.removeIf(a -> !a.onScreen(WIDTH, HEIGHT));
        effects.removeIf(p -> p.life <= 0); // Remove pop effects when their life runs out
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
    enum GameState { START, PLAYING, GAME_OVER }

    // MouseListener implementations for the close button
    @Override
    public void mouseClicked(MouseEvent e) {
        int buttonSize = 30;
        int padding = 10;
        int buttonX = WIDTH - buttonSize - padding;
        int buttonY = padding;

        // Get actual mouse coordinates relative to the panel
        double mouseX = e.getX();
        double mouseY = e.getY();

        // Calculate scaling factors to convert mouse coordinates back to game's internal coordinates
        double scaleX = (double) getWidth() / WIDTH;
        double scaleY = (double) getHeight() / HEIGHT;
        double scale = Math.min(scaleX, scaleY);

        // Convert mouse coordinates to game's internal coordinates
        // First, undo the translation used to center the scaled content
        double scaledGameAreaXOffset = (getWidth() - WIDTH * scale) / 2;
        double scaledGameAreaYOffset = (getHeight() - HEIGHT * scale) / 2;
        double gameMouseX = (mouseX - scaledGameAreaXOffset) / scale;
        double gameMouseY = (mouseY - scaledGameAreaYOffset) / scale;


        // Check if the click was within the close button bounds (in game's internal coordinates)
        if (gameMouseX >= buttonX && gameMouseX <= buttonX + buttonSize &&
            gameMouseY >= buttonY && gameMouseY <= buttonY + buttonSize) {
            System.exit(0); // Close the application
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
    private final double ROTATION_SPEED = 3; // Degrees per frame
    public final int SIZE = 20; // Represents roughly half width/height for bounding box and drawing
    private final int INVINCIBILITY_DURATION = 1500; // milliseconds after spawn/hit/hyperspace
    private long invincibilityEndTime = 0;

    /**
     * Constructor for the Ship.
     * @param startX Initial X position.
     * @param startY Initial Y position.
     */
    Ship(int startX, int startY) {
        this.x = startX;
        this.y = startY;
        this.dx = 0;
        this.dy = 0;
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
        if (hyperSpeedActive) {
            currentAcceleration *= hyperSpeedMultiplier; // Apply multiplier if hyper-speed is active
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
