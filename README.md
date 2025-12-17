Asteroid Belt

A Java Swing arcade game where the player controls a spaceship, dodges falling asteroids, and collects hearts for extra lives.

Overview
Asteroid Belt is a desktop arcade game built using Java Swing. The game features a login and registration system, persistent high scores, and a progressively difficult dodge based gameplay mode. The player moves horizontally at the bottom of the screen while objects fall from above.

Features
- User login and registration system using a local credentials file
- Dodge mode arcade gameplay
- Progressive difficulty with increasing spawn rate
- Lives system with visual heart indicators
- Persistent high scores saved to file
- Keyboard based controls

Controls
- Move left: Left Arrow or A
- Move right: Right Arrow or D
- Return to main menu: Escape

How to Run

Requirements
- Java 8 or higher

Running in an IDE
1. Open the project in an IDE such as IntelliJ or Eclipse
2. Ensure the file containing the public class is named SkyArcade.java
3. Run the SkyArcade main method

Running from the Command Line
1. Open a terminal in the project directory
2. Compile the program
   javac SkyArcade.java
3. Run the program
   java SkyArcade

Required Assets
The following image files must be located in the same directory as the source file for the game to run correctly:
- ship.png
- asteroid.png
- heart.png
- blueheart.png
- planets.jpg

Data Files
These files are created automatically after the game is used.

pass.txt
Stores registered users in the format:
username:password

scores.txt
Stores one score per line from previous games. The highest va
