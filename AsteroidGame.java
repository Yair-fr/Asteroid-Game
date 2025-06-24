import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class AsteroidGame extends JPanel implements ActionListener, KeyListener {
    Timer timer;
    Ship ship;
    ArrayList<Bullet> bullets = new ArrayList<>();
    ArrayList<Asteroid> asteroids = new ArrayList<>();
    boolean up, left, right, space;
    int score = 0;
    GameState state = GameState.START;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Asteroid Game");
        AsteroidGame game = new AsteroidGame();
        frame.add(game);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    public AsteroidGame() {
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        ship = new Ship();
        timer = new Timer(15, this);
        timer.start();
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (state == GameState.START) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("Press ENTER to Start", 250, 300);
        } else if (state == GameState.PLAYING) {
            ship.draw(g);
            for (Bullet b : bullets) b.draw(g);
            for (Asteroid a : asteroids) a.draw(g);

            g.setColor(Color.WHITE);
            g.drawString("Score: " + score, 10, 20);
        } else if (state == GameState.GAME_OVER) {
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.drawString("Game Over", 300, 250);
            g.setFont(new Font("Arial", Font.PLAIN, 24));
            g.drawString("Final Score: " + score, 320, 300);
            g.drawString("Press ENTER to Restart", 270, 350);
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (state == GameState.PLAYING) {
            if (up) ship.move();
            if (left) ship.rotate(-5);
            if (right) ship.rotate(5);
            if (space) fireBullet();

            for (Bullet b : bullets) b.update();
            for (Asteroid a : asteroids) a.update();

            checkCollisions();
            removeOffscreen();

            if (new Random().nextInt(100) < 2) asteroids.add(new Asteroid());
        }
        repaint();
    }

    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> up = true;
            case KeyEvent.VK_LEFT -> left = true;
            case KeyEvent.VK_RIGHT -> right = true;
            case KeyEvent.VK_SPACE -> space = true;
            case KeyEvent.VK_ENTER -> {
                if (state != GameState.PLAYING) {
                    state = GameState.PLAYING;
                    score = 0;
                    bullets.clear();
                    asteroids.clear();
                    ship = new Ship();
                }
            }
        }
    }

    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> up = false;
            case KeyEvent.VK_LEFT -> left = false;
            case KeyEvent.VK_RIGHT -> right = false;
            case KeyEvent.VK_SPACE -> space = false;
        }
    }

    public void keyTyped(KeyEvent e) {}

    void fireBullet() {
        if (bullets.size() < 10) bullets.add(new Bullet(ship));
    }

    void checkCollisions() {
        Iterator<Bullet> bi = bullets.iterator();
        while (bi.hasNext()) {
            Bullet b = bi.next();
            Iterator<Asteroid> ai = asteroids.iterator();
            while (ai.hasNext()) {
                Asteroid a = ai.next();
                if (b.getBounds().intersects(a.getBounds())) {
                    bi.remove();
                    ai.remove();
                    score += 10;
                    break;
                }
            }
        }

        for (Asteroid a : asteroids) {
            if (a.getBounds().intersects(ship.getBounds())) {
                state = GameState.GAME_OVER;
                break;
            }
        }
    }

    void removeOffscreen() {
        bullets.removeIf(b -> !b.onScreen());
        asteroids.removeIf(a -> !a.onScreen());
    }

    enum GameState { START, PLAYING, GAME_OVER }
}

class Ship {
    int x = 400, y = 300, angle = 0;

    void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.WHITE);
        g2.translate(x, y);
        g2.rotate(Math.toRadians(angle));
        g2.drawPolygon(new int[]{-10, 10, -10}, new int[]{-10, 0, 10}, 3);
        g2.rotate(-Math.toRadians(angle));
        g2.translate(-x, -y);
    }

    void move() {
        x += (int) (Math.cos(Math.toRadians(angle)) * 5);
        y += (int) (Math.sin(Math.toRadians(angle)) * 5);
    }

    void rotate(int delta) {
        angle += delta;
    }

    Rectangle getBounds() {
        return new Rectangle(x - 10, y - 10, 20, 20);
    }
}

class Bullet {
    int x, y, dx, dy;

    Bullet(Ship ship) {
        x = ship.x;
        y = ship.y;
        dx = (int) (Math.cos(Math.toRadians(ship.angle)) * 10);
        dy = (int) (Math.sin(Math.toRadians(ship.angle)) * 10);
    }

    void update() {
        x += dx;
        y += dy;
    }

    void draw(Graphics g) {
        g.setColor(Color.YELLOW);
        g.fillOval(x - 2, y - 2, 4, 4);
    }

    boolean onScreen() {
        return x >= 0 && x <= 800 && y >= 0 && y <= 600;
    }

    Rectangle getBounds() {
        return new Rectangle(x - 2, y - 2, 4, 4);
    }
}

class Asteroid {
    int x, y, dx, dy;
    static Random rand = new Random();

    Asteroid() {
        x = rand.nextInt(800);
        y = -20;
        dx = rand.nextInt(5) - 2;
        dy = rand.nextInt(3) + 1;
    }

    void update() {
        x += dx;
        y += dy;
    }

    void draw(Graphics g) {
        g.setColor(Color.GRAY);
        g.fillOval(x - 15, y - 15, 30, 30);
    }

    boolean onScreen() {
        return y <= 600;
    }

    Rectangle getBounds() {
        return new Rectangle(x - 15, y - 15, 30, 30);
    }
}
