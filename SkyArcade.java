/*
* Asteroid Belt - A simple arcade game built with Java Swing.
*
* Game Structure:
* - LoginPanel: Authenticates user with hardcoded credentials (Neil/Kapoor)
* - MainMenuPanel: Entry point after login, shows "Start Game" button
* - DodgeGamePanel: Main gameplay - dodge asteroids, collect hearts for extra lives
*
* Core Mechanics:
* - Player controls spaceship with arrow keys or WASD to avoid falling asteroids
* - Collecting blue hearts grants extra lives (displays red hearts for first 3, blue for extras)
* - Dodging asteroids rewards 5 points per asteroid
* - Game ends when lives reach 0
* - High scores persist to scores.txt file
*/
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.io.*;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Map;


public class SkyArcade extends JFrame {
   // CardLayout allows seamless switching between Login, Menu, and Game panels
   private CardLayout cardLayout;
   private JPanel mainPanel;
   private MainMenuPanel mainMenuPanel;
   private DodgeGamePanel dodgeGamePanel;
   private LoginPanel loginPanel;
   private RegisterPanel registerPanel;


   public SkyArcade() {
       super("Asteroid Belt");
       cardLayout = new CardLayout();
       mainPanel = new JPanel(cardLayout);


       loginPanel = new LoginPanel(this);
       registerPanel = new RegisterPanel(this);
       mainMenuPanel = new MainMenuPanel(this);
       dodgeGamePanel = new DodgeGamePanel(this);


       mainPanel.add(loginPanel, "Login");
       mainPanel.add(registerPanel, "Register");
       mainPanel.add(mainMenuPanel, "Menu");
       mainPanel.add(dodgeGamePanel, "Dodge");
       add(mainPanel);


       setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
       setSize(800, 600);
       setLocationRelativeTo(null);
       setResizable(false);
       setVisible(true);
       showLogin();
   }


   public void showLogin() { cardLayout.show(mainPanel, "Login"); }
   public void showRegister() { cardLayout.show(mainPanel, "Register"); }
   public void showMenu() { cardLayout.show(mainPanel, "Menu"); }
  
   // Initializes game state and switches to game panel
   public void startDodgeGame() {
       dodgeGamePanel.startGame();
       cardLayout.show(mainPanel, "Dodge");
   }


   public static void main(String[] args) {
       // SwingUtilities.invokeLater ensures GUI construction happens on Event Dispatch Thread (EDT)
       // This prevents threading issues where non-EDT threads might modify Swing components
       SwingUtilities.invokeLater(SkyArcade::new);
   }
}


/**
* Base class for all game objects (player, asteroids, hearts).
* Handles position, velocity, sprite rendering, and collision detection via bounding boxes.
* hitboxScale allows tuning collision tightness (1.0 = full size, 0.6 = 60% for asteroids).
*/
abstract class GameObject {
   protected int x, y, width, height, velX, velY;
   protected Image sprite;
   protected double hitboxScale = 1.0;


   public GameObject(int x, int y, int w, int h, int vx, int vy) {
       this.x = x;
       this.y = y;
       this.width = w;
       this.height = h;
       this.velX = vx;
       this.velY = vy;
   }


   protected Image loadSprite(String fileName) {
       return new ImageIcon(fileName).getImage();
   }


   protected void drawSprite(Graphics2D g2) {
       if (sprite != null) g2.drawImage(sprite, x, y, width, height, null);
   }


   /**
    * Scales object width and maintains aspect ratio from original sprite dimensions.
    * Used during construction to properly size sprites to game coordinates.
    * Prevents image distortion by calculating proportional height based on original aspect ratio.
    */
   protected void scaleToSpriteWidth(int desiredWidth) {
       if (sprite == null) return;
       int origW = sprite.getWidth(null);
       int origH = sprite.getHeight(null);
       if (origW <= 0 || origH <= 0) return;
       this.width = desiredWidth;
       // Calculate height preserving aspect ratio: (origH / origW) * desiredWidth
       // Uses Math.round for rounding, Math.max ensures minimum 1px to avoid rendering issues
       this.height = Math.max(1, (int) Math.round(((double) origH / origW) * desiredWidth));
   }


   public abstract void update();
   public abstract void draw(Graphics2D g2);


   /**
    * Returns collision bounding box, potentially smaller than visual sprite via hitboxScale.
    * Centers the hitbox on the object for more forgiving collision detection.
    * This allows asteroids to have a tighter collision box (0.6 scale) than hearts (1.0 scale).
    */
   public Rectangle getBounds() {
       // Scale hitbox dimensions based on hitboxScale factor
       int bw = Math.max(1, (int) Math.round(width * hitboxScale));
       int bh = Math.max(1, (int) Math.round(height * hitboxScale));
       // Center hitbox on object: offset = (visual_size - hitbox_size) / 2
       // This centers a smaller hitbox within the larger sprite boundaries
       int bx = x + (width - bw) / 2;
       int by = y + (height - bh) / 2;
       return new Rectangle(bx, by, bw, bh);
   }
}


/**
* Player-controlled spaceship that moves horizontally at the bottom of the screen.
* Movement is clamped to panel boundaries so ship cannot leave the visible area.
* Listens for velocity changes from keyboard input and applies constraints.
*/
class PlayerObject extends GameObject {
   private int panelWidth;


   public PlayerObject(int panelWidth, int y) {
       super(panelWidth / 2 - 25, y, 50, 20, 0, 0);
       this.panelWidth = panelWidth;
       this.sprite = loadSprite("ship.png");
       scaleToSpriteWidth(64);
       this.x = panelWidth / 2 - this.width / 2;
   }


   public void setVelX(int velX) { this.velX = velX; }


   @Override
   public void update() {
       x += velX;
       if (x < 0) x = 0;
       if (x + width > panelWidth) x = panelWidth - width;
   }


   @Override
   public void draw(Graphics2D g2) { drawSprite(g2); }
}


// Falling asteroid obstacle - player loses a life on collision
// Asteroids have a reduced hitbox (0.6 scale) for fairer gameplay
class ObstacleObject extends GameObject {
   public ObstacleObject(int x, int y, int size, int speed) {
       super(x, y, size, size, 0, speed);
       this.sprite = loadSprite("asteroid.png");
       scaleToSpriteWidth(size);
       // Reduced hitbox scale makes collisions tighter relative to visual sprite
       this.hitboxScale = 0.6;
   }


   @Override
   public void update() { y += velY; }


   @Override
   public void draw(Graphics2D g2) { drawSprite(g2); }
}


// Falling heart power-up - grants an extra life when collected
// Hearts fall slower than asteroids to give player time to maneuver into position
class HeartObject extends GameObject {
   public HeartObject(int x, int y, int size, int speed) {
       super(x, y, size, size, 0, speed);
       this.sprite = loadSprite("blueheart.png");
       scaleToSpriteWidth(size);
   }


   @Override
   public void update() { y += velY; }


   @Override
   public void draw(Graphics2D g2) { drawSprite(g2); }
}


/**
* Utility class for managing user credentials stored in pass.txt file.
* Handles reading and writing username/password pairs in format: username:password
*/
class CredentialManager {
   private static final String CREDENTIALS_FILE = "pass.txt";
  
   /**
    * Loads all credentials from pass.txt into a HashMap for quick lookup.
    * File format: one line per user, "username:password"
    * Returns empty map if file doesn't exist or can't be read.
    */
   public static Map<String, String> loadCredentials() {
       Map<String, String> credentials = new HashMap<>();
       try {
           File file = new File(CREDENTIALS_FILE);
           if (file.exists()) {
               Scanner scanner = new Scanner(file);
               while (scanner.hasNextLine()) {
                   String line = scanner.nextLine().trim();
                   if (!line.isEmpty() && line.contains(":")) {
                       String[] parts = line.split(":", 2);
                       if (parts.length == 2) {
                           credentials.put(parts[0], parts[1]);
                       }
                   }
               }
               scanner.close();
           }
       } catch (IOException e) {
           System.err.println("Error loading credentials: " + e.getMessage());
       }
       return credentials;
   }
  
   /**
    * Saves a new username/password pair to pass.txt in append mode.
    * Format: "username:password" followed by newline.
    * Returns true if successful, false if file write fails.
    */
   public static boolean saveCredentials(String username, String password) {
       try {
           FileWriter fw = new FileWriter(CREDENTIALS_FILE, true);
           fw.write(username + ":" + password + "\n");
           fw.close();
           return true;
       } catch (IOException e) {
           System.err.println("Error saving credentials: " + e.getMessage());
           return false;
       }
   }
  
   /**
    * Checks if a username already exists in the credentials file.
    * Used during registration to prevent duplicate usernames.
    */
   public static boolean usernameExists(String username) {
       Map<String, String> credentials = loadCredentials();
       return credentials.containsKey(username);
   }
  
   /**
    * Validates login credentials against stored credentials.
    * Returns true if username exists and password matches.
    */
   public static boolean validateLogin(String username, String password) {
       Map<String, String> credentials = loadCredentials();
       String storedPassword = credentials.get(username);
       return storedPassword != null && storedPassword.equals(password);
   }
}


class LoginPanel extends JPanel {
   public LoginPanel(SkyArcade game) {
       setLayout(new GridBagLayout());
       setBackground(Color.DARK_GRAY);
       GridBagConstraints gbc = new GridBagConstraints();
       gbc.insets = new Insets(12, 12, 12, 12);
       gbc.fill = GridBagConstraints.HORIZONTAL;


       JLabel title = new JLabel("Asteroid Belt");
       title.setForeground(Color.WHITE);
       title.setFont(new Font("Arial", Font.BOLD, 48));


       JLabel userLabel = new JLabel("Username:");
       userLabel.setForeground(Color.WHITE);
       userLabel.setFont(new Font("Arial", Font.BOLD, 18));


       JTextField userField = new JTextField(16);
       userField.setFont(new Font("Arial", Font.PLAIN, 18));


       JLabel passLabel = new JLabel("Password:");
       passLabel.setForeground(Color.WHITE);
       passLabel.setFont(new Font("Arial", Font.BOLD, 18));


       JPasswordField passField = new JPasswordField(16);
       passField.setFont(new Font("Arial", Font.PLAIN, 18));


       JButton loginButton = new JButton("Login");
       loginButton.setFont(new Font("Arial", Font.BOLD, 18));
       loginButton.setPreferredSize(new Dimension(160, 45));


       JButton registerButton = new JButton("Register");
       registerButton.setFont(new Font("Arial", Font.BOLD, 16));
       registerButton.setPreferredSize(new Dimension(160, 40));


       gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
       gbc.anchor = GridBagConstraints.CENTER;
       gbc.insets = new Insets(20, 20, 30, 20);
       add(title, gbc);


       gbc.gridwidth = 1;
       gbc.anchor = GridBagConstraints.WEST;
       gbc.insets = new Insets(10, 20, 5, 10);
       gbc.gridx = 0; gbc.gridy = 1;
       add(userLabel, gbc);


       gbc.gridx = 1; gbc.gridy = 1;
       gbc.insets = new Insets(10, 0, 5, 20);
       add(userField, gbc);


       gbc.gridx = 0; gbc.gridy = 2;
       gbc.insets = new Insets(10, 20, 5, 10);
       add(passLabel, gbc);


       gbc.gridx = 1; gbc.gridy = 2;
       gbc.insets = new Insets(10, 0, 5, 20);
       add(passField, gbc);


       gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
       gbc.insets = new Insets(25, 20, 10, 20);
       gbc.anchor = GridBagConstraints.CENTER;
       add(loginButton, gbc);


       gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
       gbc.insets = new Insets(10, 20, 10, 20);
       gbc.anchor = GridBagConstraints.CENTER;
       add(registerButton, gbc);


       ActionListener login = e -> {
           String username = userField.getText().trim();
           String password = new String(passField.getPassword());
           if (username.isEmpty() || password.isEmpty()) {
               JOptionPane.showMessageDialog(this, "Please enter both username and password", "Login Failed", JOptionPane.ERROR_MESSAGE);
               return;
           }
           if (CredentialManager.validateLogin(username, password)) {
               userField.setText("");
               passField.setText("");
               game.showMenu();
           } else {
               JOptionPane.showMessageDialog(this, "User not found 404", "Login Failed", JOptionPane.ERROR_MESSAGE);
               passField.setText("");
               passField.requestFocusInWindow();
           }
       };
       loginButton.addActionListener(login);
       userField.addActionListener(login);
       passField.addActionListener(login);
       registerButton.addActionListener(e -> game.showRegister());
   }
}


/**
* Registration panel allowing users to create new accounts.
* Validates that username doesn't already exist and saves credentials to pass.txt.
* Provides navigation back to login screen after successful registration.
*/
class RegisterPanel extends JPanel {
   public RegisterPanel(SkyArcade game) {
       setLayout(new GridBagLayout());
       setBackground(Color.DARK_GRAY);
       GridBagConstraints gbc = new GridBagConstraints();
       gbc.insets = new Insets(12, 12, 12, 12);
       gbc.fill = GridBagConstraints.HORIZONTAL;


       JLabel title = new JLabel("Register");
       title.setForeground(Color.WHITE);
       title.setFont(new Font("Arial", Font.BOLD, 48));


       JLabel userLabel = new JLabel("Username:");
       userLabel.setForeground(Color.WHITE);
       userLabel.setFont(new Font("Arial", Font.BOLD, 18));


       JTextField userField = new JTextField(16);
       userField.setFont(new Font("Arial", Font.PLAIN, 18));


       JLabel passLabel = new JLabel("Password:");
       passLabel.setForeground(Color.WHITE);
       passLabel.setFont(new Font("Arial", Font.BOLD, 18));


       JPasswordField passField = new JPasswordField(16);
       passField.setFont(new Font("Arial", Font.PLAIN, 18));


       JLabel confirmPassLabel = new JLabel("Confirm Password:");
       confirmPassLabel.setForeground(Color.WHITE);
       confirmPassLabel.setFont(new Font("Arial", Font.BOLD, 18));


       JPasswordField confirmPassField = new JPasswordField(16);
       confirmPassField.setFont(new Font("Arial", Font.PLAIN, 18));


       JButton registerButton = new JButton("Register");
       registerButton.setFont(new Font("Arial", Font.BOLD, 18));
       registerButton.setPreferredSize(new Dimension(160, 45));


       JButton backButton = new JButton("Back to Login");
       backButton.setFont(new Font("Arial", Font.BOLD, 16));
       backButton.setPreferredSize(new Dimension(160, 40));


       gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
       gbc.anchor = GridBagConstraints.CENTER;
       gbc.insets = new Insets(20, 20, 30, 20);
       add(title, gbc);


       gbc.gridwidth = 1;
       gbc.anchor = GridBagConstraints.WEST;
       gbc.insets = new Insets(10, 20, 5, 10);
       gbc.gridx = 0; gbc.gridy = 1;
       add(userLabel, gbc);


       gbc.gridx = 1; gbc.gridy = 1;
       gbc.insets = new Insets(10, 0, 5, 20);
       add(userField, gbc);


       gbc.gridx = 0; gbc.gridy = 2;
       gbc.insets = new Insets(10, 20, 5, 10);
       add(passLabel, gbc);


       gbc.gridx = 1; gbc.gridy = 2;
       gbc.insets = new Insets(10, 0, 5, 20);
       add(passField, gbc);


       gbc.gridx = 0; gbc.gridy = 3;
       gbc.insets = new Insets(10, 20, 5, 10);
       add(confirmPassLabel, gbc);


       gbc.gridx = 1; gbc.gridy = 3;
       gbc.insets = new Insets(10, 0, 5, 20);
       add(confirmPassField, gbc);


       gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
       gbc.insets = new Insets(25, 20, 10, 20);
       gbc.anchor = GridBagConstraints.CENTER;
       add(registerButton, gbc);


       gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
       gbc.insets = new Insets(10, 20, 10, 20);
       gbc.anchor = GridBagConstraints.CENTER;
       add(backButton, gbc);


       ActionListener register = e -> {
           String username = userField.getText().trim();
           String password = new String(passField.getPassword());
           String confirmPassword = new String(confirmPassField.getPassword());


           if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
               JOptionPane.showMessageDialog(this, "Please fill in all fields", "Registration Failed", JOptionPane.ERROR_MESSAGE);
               return;
           }


           if (!password.equals(confirmPassword)) {
               JOptionPane.showMessageDialog(this, "Passwords do not match", "Registration Failed", JOptionPane.ERROR_MESSAGE);
               passField.setText("");
               confirmPassField.setText("");
               passField.requestFocusInWindow();
               return;
           }


           if (CredentialManager.usernameExists(username)) {
               JOptionPane.showMessageDialog(this, "Username already exists. Please choose a different username.", "Registration Failed", JOptionPane.ERROR_MESSAGE);
               userField.setText("");
               userField.requestFocusInWindow();
               return;
           }


           if (CredentialManager.saveCredentials(username, password)) {
               JOptionPane.showMessageDialog(this, "Registration successful! You can now login.", "Registration Success", JOptionPane.INFORMATION_MESSAGE);
               userField.setText("");
               passField.setText("");
               confirmPassField.setText("");
               game.showLogin();
           } else {
               JOptionPane.showMessageDialog(this, "Failed to save credentials. Please try again.", "Registration Failed", JOptionPane.ERROR_MESSAGE);
           }
       };


       registerButton.addActionListener(register);
       userField.addActionListener(register);
       passField.addActionListener(register);
       confirmPassField.addActionListener(register);
       backButton.addActionListener(e -> {
           userField.setText("");
           passField.setText("");
           confirmPassField.setText("");
           game.showLogin();
       });
   }
}


class MainMenuPanel extends JPanel {
   public MainMenuPanel(SkyArcade game) {
       setLayout(new GridBagLayout());
       setBackground(Color.BLACK);
       JLabel title = new JLabel("Asteroid Belt");
       title.setForeground(Color.WHITE);
       title.setFont(new Font("Arial", Font.BOLD, 36));
       JButton dodgeButton = new JButton("Start Game");


       GridBagConstraints gbc = new GridBagConstraints();
       gbc.insets = new Insets(15, 15, 15, 15);
       gbc.gridx = 0;
       gbc.gridy = 0;
       add(title, gbc);
       gbc.gridy = 1;
       add(dodgeButton, gbc);


       dodgeButton.addActionListener(e -> game.startDodgeGame());
   }
}


/**
* Abstract base class for game panels, implements the core game loop via Timer.
* Handles player input (keyboard), object spawning, collision detection, and rendering.
* Subclasses (like DodgeGamePanel) implement spawn and collision logic specific to their game mode.
*/
abstract class BaseGamePanel extends JPanel implements ActionListener, KeyListener {
   protected SkyArcade game;
   protected Timer timer;
   protected PlayerObject player;
   protected ArrayList<GameObject> fallingObjects;
   protected Random random;
   protected boolean leftPressed = false;
   protected boolean rightPressed = false;
   protected boolean running = false;
   protected int score = 0;
   protected int spawnCounter = 0;
   protected int spawnRate = 40;


   public BaseGamePanel(SkyArcade game) {
       this.game = game;
       setFocusable(true);
       setDoubleBuffered(true);
       fallingObjects = new ArrayList<>();
       random = new Random();
       // Timer fires actionPerformed every 16 milliseconds, approximately 60 FPS (1000ms / 16ms ≈ 62.5 FPS)
       timer = new Timer(16, this);
       addKeyListener(this);
   }


   public void startGame() {
       fallingObjects.clear();
       score = 0;
       spawnCounter = 0;
       spawnRate = 40;
       running = true;
       // Use ternary operator to handle case where panel hasn't been rendered yet (size = 0)
       int panelHeight = getHeight() > 0 ? getHeight() : 600;
       int panelWidth = getWidth() > 0 ? getWidth() : 800;
       player = new PlayerObject(panelWidth, panelHeight - 60);
       timer.start();
       SwingUtilities.invokeLater(() -> {
           setFocusable(true);
           requestFocus();
       });
   }


   public void endGame() {
       running = false;
       timer.stop();
       String message = "Game Over! Your score: " + score;
       JOptionPane.showMessageDialog(this, message, "Game Over", JOptionPane.INFORMATION_MESSAGE);
       game.showMenu();
   }


   /**
    * Called every 16ms by Timer (~60 FPS). Updates player velocity from key input,
    * moves player, spawns new objects, updates all falling objects with collision checks,
    * then requests a repaint of the panel to display the new frame.
    * This is the heartbeat of the game - controls all frame-by-frame updates.
    */
   @Override
   public void actionPerformed(ActionEvent e) {
       if (!running) return;


       // Apply player velocity based on current key state (prevents diagonal movement when both pressed)
       // Exclusive OR logic: if both left and right are pressed, player doesn't move (neutral)
       if (leftPressed && !rightPressed) {
           player.setVelX(-6);
       } else if (rightPressed && !leftPressed) {
           player.setVelX(6);
       } else {
           player.setVelX(0);
       }


       player.update();


       // Spawn objects at decreasing intervals as game progresses for difficulty scaling
       // spawnRate starts at 40 frames between spawns and decreases to 10 (minimum)
       spawnCounter++;
       if (spawnCounter >= spawnRate) {
           spawnCounter = 0;
           spawnNewObject();
           // Lower spawn rate = more frequent spawning = harder game
           if (spawnRate > 10) {
               spawnRate--;
           }
       }


       updateFallingObjects();
       repaint();
   }


   protected abstract void spawnNewObject();
   protected abstract void handleCollision(GameObject obj, Iterator<GameObject> iterator);


   /**
    * Updates all falling objects: moves each one, checks collision with player,
    * and removes objects that fell off the bottom of the screen.
    * Uses Iterator for safe removal during iteration to avoid ConcurrentModificationException.
    * This pattern is essential when removing elements from a collection while iterating over it.
    */
   private void updateFallingObjects() {
       // Iterator allows safe removal: calling it.remove() modifies the list safely
       // Regular for-each or index-based loops would throw ConcurrentModificationException
       Iterator<GameObject> it = fallingObjects.iterator();
       while (it.hasNext()) {
           GameObject obj = it.next();
           obj.update();


           // Check if object intersects with player using Rectangle.intersects()
           // This performs AABB (Axis-Aligned Bounding Box) collision detection
           if (obj.getBounds().intersects(player.getBounds())) {
               handleCollision(obj, it);
           }


           // Remove object if it has fallen below the visible game area
           if (obj.y > getHeight()) {
               it.remove();
               handleMissedObject(obj);
           }
       }
   }


   protected void handleMissedObject(GameObject obj) {}


   @Override
   protected void paintComponent(Graphics g) {
       super.paintComponent(g);
       g.setColor(Color.BLACK);
       g.fillRect(0, 0, getWidth(), getHeight());


       Graphics2D g2 = (Graphics2D) g;


       if (player != null) {
           player.draw(g2);
       }


       for (GameObject obj : fallingObjects) {
           obj.draw(g2);
       }


       g2.setColor(Color.WHITE);
       g2.setFont(new Font("Arial", Font.BOLD, 18));
       g2.drawString("Score: " + score, 10, 20);
       g2.drawString("Press ESC to return to menu", 10, getHeight() - 10);
   }


   @Override
   public void keyPressed(KeyEvent e) {
       int code = e.getKeyCode();


       if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) {
           leftPressed = true;
       } else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) {
           rightPressed = true;
       } else if (code == KeyEvent.VK_ESCAPE) {
           running = false;
           timer.stop();
           game.showMenu();
       }
   }


   @Override
   public void keyReleased(KeyEvent e) {
       int code = e.getKeyCode();


       if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) {
           leftPressed = false;
       } else if (code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) {
           rightPressed = false;
       }
   }


   @Override
   public void keyTyped(KeyEvent e) {}
}


/**
* Dodge Mode implementation: player avoids asteroids and collects hearts.
*
* Difficulty increases as spawn rate decreases each time player dodges an asteroid.
* Lives system: starts at 3 (red hearts), can exceed 3 (extra blue hearts displayed).
* Scoring: +5 points for each dodged asteroid.
* High scores are persisted to scores.txt and compared with current session.
* Game features progressive difficulty: objects spawn faster as time goes on.
*/
class DodgeGamePanel extends BaseGamePanel {
   private int lives = 3;
   private Image heartSprite;
   private Image blueHeartSprite;
   private int highScore = 0;
   private Image backgroundImage;


   public DodgeGamePanel(SkyArcade game) {
       super(game);
       setBackground(Color.BLACK);
       heartSprite = new ImageIcon("heart.png").getImage();
       blueHeartSprite = new ImageIcon("blueheart.png").getImage();
       backgroundImage = new ImageIcon("planets.jpg").getImage();
       loadHighScore();
   }


   /**
    * Loads highest score from scores.txt file to display as high score.
    * Called during panel initialization and after game ends if new high score achieved.
    * Scans all lines in file and tracks the maximum value found.
    */
   private void loadHighScore() {
       try {
           File file = new File("scores.txt");
           if (file.exists()) {
               Scanner scanner = new Scanner(file);
               highScore = 0;
               // Compare each score in file to find and store the maximum
               while (scanner.hasNextLine()) {
                   int score = Integer.parseInt(scanner.nextLine());
                   if (score > highScore) {
                       highScore = score;
                   }
               }
               scanner.close();
           }
       } catch (IOException e) {
           highScore = 0;
       }
   }


   /**
    * Appends current score to scores.txt for persistence across game sessions.
    * FileWriter with true parameter enables append mode instead of overwriting.
    * Each game's score is added as a new line to build a history of all attempts.
    */
   private void saveScore(int newScore) {
       try {
           // true parameter = append mode (FileWriter(filename, append))
           // Without true, FileWriter would overwrite the entire file
           FileWriter fw = new FileWriter("scores.txt", true);
           fw.write(newScore + "\n");
           fw.close();
       } catch (IOException e) {
           System.err.println("Error saving score");
       }
   }


   public void endGame() {
       running = false;
       timer.stop();
       saveScore(score);
       if (score > highScore) {
           highScore = score;
       }
       String message = "Game Over! Your score: " + score;
       JOptionPane.showMessageDialog(this, message, "Game Over", JOptionPane.INFORMATION_MESSAGE);
       game.showMenu();
   }


   @Override
   public void startGame() {
       lives = 3;
       super.startGame();
   }


   /**
    * Spawns asteroids (93% chance) or hearts (7% chance) at random locations.
    * Asteroids: size 60-119px, speed 5-10 px/frame (fall quickly).
    * Hearts: size 30-49px, speed 2-4 px/frame (slower to give player time to collect).
    * Random selection uses random.nextInt(100) to determine object type based on percentage.
    */
   @Override
   protected void spawnNewObject() {
       int size = 60 + random.nextInt(60);
       int x = random.nextInt(Math.max(getWidth() - size, 1));
       int speed = 5 + random.nextInt(6);


       // 7% chance for heart (if random value < 7 out of 100)
       if (random.nextInt(100) < 7) {
           int heartSize = 30 + random.nextInt(20);
           int heartX = random.nextInt(Math.max(getWidth() - heartSize, 1));
           int heartSpeed = 2 + random.nextInt(3);
           // Spawn hearts above the top of screen (negative y) so they fall into view
           fallingObjects.add(new HeartObject(heartX, -heartSize, heartSize, heartSpeed));
       } else {
           // 93% chance for asteroid
           fallingObjects.add(new ObstacleObject(x, -size, size, speed));
       }
   }


   /**
    * Handles collisions: hearts increment lives, asteroids decrement lives and may end game.
    * Objects are removed from list after collision (iterator prevents concurrent modification).
    * Uses instanceof to distinguish between HeartObject and ObstacleObject at runtime.
    */
   @Override
   protected void handleCollision(GameObject obj, Iterator<GameObject> iterator) {
       iterator.remove();
       // instanceof check determines object type at runtime for polymorphic behavior
       if (obj instanceof HeartObject) {
           lives++;
       } else {
           lives--;
           if (lives <= 0) {
               endGame();
           }
       }
   }


   // Awards points for dodging asteroids (when they fall off screen)
   // Hearts do not award points when missed - only asteroids count toward score
   @Override
   protected void handleMissedObject(GameObject obj) {
       if (obj instanceof ObstacleObject) {
           score += 5;
       }
   }


   /**
    * Rendering: draws background image, all game objects, HUD text, and life indicators.
    * First 3 lives shown as red hearts, additional lives as blue hearts.
    * Hearts drawn right-to-left from top-right corner: startX - (i+1) * (size+spacing)
    * This prevents overlap and keeps hearts visible even with many lives collected.
    * The formula creates decreasing X positions: rightmost heart is at startX - (size+spacing),
    * next is at startX - 2*(size+spacing), etc., creating a left-ward sequence.
    */
   @Override
   protected void paintComponent(Graphics g) {
       super.paintComponent(g);


       if (backgroundImage != null) {
           g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
       } else {
           g.setColor(Color.BLACK);
           g.fillRect(0, 0, getWidth(), getHeight());
       }


       Graphics2D g2 = (Graphics2D) g;


       if (player != null) {
           player.draw(g2);
       }


       for (GameObject obj : fallingObjects) {
           obj.draw(g2);
       }


       g2.setColor(Color.WHITE);
       g2.setFont(new Font("Arial", Font.BOLD, 18));
       g2.drawString("High Score: " + highScore, getWidth() / 2 - 70, 20);
       g2.drawString("Score: " + score, 10, 20);
       g2.drawString("Press ESC to return to menu", 10, getHeight() - 10);


       // Draw life indicators: red hearts for first 3, blue for any beyond 3
       if (heartSprite != null && blueHeartSprite != null) {
           int heartSize = 48;
           int spacing = 10;
           int startX = getWidth() - 10;
           int startY = 10;


           for (int i = 0; i < lives; i++) {
               // Ternary operator selects sprite based on position: red if < 3, blue if >= 3
               Image currentHeart = (i < 3) ? heartSprite : blueHeartSprite;
               // Calculate position moving right-to-left: each subsequent heart moves left by (size+spacing)
               // i=0: startX - 1*(48+10) = startX - 58 (rightmost)
               // i=1: startX - 2*(48+10) = startX - 116 (next to left)
               // i=2: startX - 3*(48+10) = startX - 174 (leftmost of first 3)
               int x = startX - (i + 1) * (heartSize + spacing);
               g.drawImage(currentHeart, x, startY, heartSize, heartSize, null);
           }
       }
   }
}



