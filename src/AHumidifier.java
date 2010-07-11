import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.quirlion.script.Constants;
import com.quirlion.script.Input;
import com.quirlion.script.Script;
import com.quirlion.script.types.GEItem;
import com.quirlion.script.types.Interface;

public class AHumidifier extends Script {
	private boolean hasFireStaff = false, hasSteamStaff = false, hasWaterStaff = false;
	private boolean isStopping = false, isCameraRotating = false;
	
	private int astralRuneID = 9075, fireRuneID = 554, waterRuneID = 555;
	private int fireStaffID = 1387, steamStaffID = 11736, waterStaffID = 1383;
	private int emptyVialID = 229, filledVialID = 227;
	private int filledVialPrice = 0, humidifierCasts = 0, tries = 0, vialsFilled = 0;
	private int emptyVialsInInventory = 0;
	private int startingMagicXP = 0;
	
	private long startTime = 0;
	
	private Antiban antiban = null;
	private Image coinImage, cursorImage, drinkImage, sumImage, timeImage, weatherImage;
	private ImageObserver observer;
	private Interface humidifyInterface = null;
	private Thread antibanThread = null;
	
	@Override
	public void onStart() {
		Constants.WAIT = 1000;
		input.MOUSE_SPEED = 2;
		
		try {
			coinImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/coins.png"));
			cursorImage = ImageIO.read(new URL("http://scripts.allometry.com/app/webroot/img/cursors/cursor-01.png"));
			drinkImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/drink.png"));
			sumImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/sum.png"));
			timeImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/time.png"));
			weatherImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/weather_rain.png"));
		} catch (IOException e) {
			logStackTrace(e);
		}
		
		log("Gathering current market price for filled vials...");
		
		GEItem filledVial = ge.getInfoForItem(filledVialID);
		filledVialPrice = filledVial.getMarketPrice();
		
		log("Filled vial market price is " + new Integer(filledVialPrice).toString() + "!");
		
		if(tabs.getCurrentTab() != Constants.TAB_EQUIP) tabs.openTab(Constants.TAB_EQUIP);
		
		if(equipment.isItemEquipped(fireStaffID))
			hasFireStaff = true;
		
		if(equipment.isItemEquipped(steamStaffID))
			hasSteamStaff = true;
		
		if(equipment.isItemEquipped(waterStaffID))
			hasWaterStaff = true;
		
		antiban = new Antiban();
		antibanThread = new Thread(antiban);
		antibanThread.start();
		
		startingMagicXP = skills.getCurrentSkillXP(Constants.STAT_MAGIC);
		startTime = System.currentTimeMillis();
		
		cam.setAltitude(true);
	}
	
	@Override
	public int loop() {
		if(isCameraRotating) return 1;
		
		if(!canCastHumidify()) {
			log("Unable to cast humidify, exiting...");
			isStopping = true;
			stopScript();
		}
		
		if(inventory.getCount(filledVialID) > 0 || !inventory.containsItem(emptyVialID, filledVialID)) {
			if(bank.open() || bank.isOpen()) {
				if(inventory.containsItem(filledVialID)) {
					tries = 0;
					while(inventory.containsItem(filledVialID) && tries < 5) {
						input.moveMouse(inventory.findItem(filledVialID).getLocation());
						bank.deposit(filledVialID, 0);
						
						if(inventory.containsItem(filledVialID)) {
							wait(1000);
							tries++;
						}
					}
					
					if(tries >= 5) return 1;
				} else {
					if(bank.getItemCount(emptyVialID) <= 1) {
						log("You only have one vial left, exiting...");
						isStopping = true;
						stopScript();
					}
					
					tries = 0;
					while(!inventory.containsItem(emptyVialID) && tries < 5) {
						input.moveMouse(bank.getItem(emptyVialID).getRealX(), bank.getItem(emptyVialID).getRealY());
						
						if(bank.getItemCount(emptyVialID) <= (28 - inventory.getCount()))
							bank.withdraw(emptyVialID, bank.getItemCount(emptyVialID) - 1);
						else
							bank.withdraw(emptyVialID, 0);
						
						if(!inventory.containsItem(emptyVialID)) {
							tries++;
							wait(1000);
						}
					}
					
					tries = 0;
					while(emptyVialsInInventory == 0 && tries < 5) {
						emptyVialsInInventory = inventory.getCount(emptyVialID);
						
						if(emptyVialsInInventory == 0) {
							wait(1000);
							tries++;
						}
					}
					
					bank.close();
					
					if(tries >= 5) return 1;
					
					tries = 0;
					while(bank.isOpen() && tries < 5) {
						bank.close();
						
						if(bank.isOpen()) {
							wait(1000);
							tries++;
						}
					}
					
					return 1;
				}
			}
		}
		
		if(inventory.getCount(emptyVialID) > 0) {
			if(tabs.getCurrentTab() != Constants.TAB_MAGIC) tabs.openTab(Constants.TAB_MAGIC);
			humidifyInterface = interfaces.get(430, 29);
			
			if(humidifyInterface != null) {
				int currentXP = skills.getCurrentSkillXP(Constants.STAT_MAGIC);
				
				input.moveMouse(humidifyInterface.getRealX(), humidifyInterface.getRealY());
				humidifyInterface.click();
				
				while(currentXP == skills.getCurrentSkillXP(Constants.STAT_MAGIC)) {
					wait(1);
				}
				
				if(currentXP < skills.getCurrentSkillXP(Constants.STAT_MAGIC)) {
					while(tabs.getCurrentTab() != Constants.TAB_INVENTORY) {
						tabs.openTab(Constants.TAB_INVENTORY);
						
						if(tabs.getCurrentTab() == Constants.TAB_INVENTORY) {
							while(inventory.containsItem(emptyVialID) && !inventory.containsItem(filledVialID)) {
								wait(1);
							}
						}
					}
				}
				
				if(!inventory.containsItem(emptyVialID) && inventory.containsItem(filledVialID)) {
					vialsFilled += inventory.getCount(filledVialID);
					emptyVialsInInventory = 0;
					humidifierCasts++;
				}
				
				return 1;
			}
		}
		
		return 1;
	}
	
	@Override
	public void onStop() {
		isStopping = true;
		
		if(!antibanThread.isAlive()) {
			antibanThread = null;
			antiban = null;
		}
		
		log("Thanks for using Allometry Humidifier, goodbye!");
	}
	
	@Override
	public void paint(Graphics g2) {
		if(!players.getCurrent().isLoggedIn() || players.getCurrent().isInLobby()) return ;
		
		Graphics2D g = (Graphics2D)g2;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		//Draw Mouse
		g.drawImage(cursorImage, input.getBotMousePosition().x - 16, input.getBotMousePosition().y - 16, observer);
		
		//Draw Scoreboard
		NumberFormat number = NumberFormat.getIntegerInstance(Locale.US);
		int sum = skills.getCurrentSkillXP(Constants.STAT_MAGIC) - startingMagicXP;
		
		Scoreboard leftScoreboard = new Scoreboard(Scoreboard.TOP_LEFT, 128, 5);
		Scoreboard rightScoreboard = new Scoreboard(Scoreboard.TOP_RIGHT, 128, 5);
		
		leftScoreboard.addWidget(new ScoreboardWidget(weatherImage, number.format(humidifierCasts)));
		leftScoreboard.addWidget(new ScoreboardWidget(drinkImage, number.format(vialsFilled)));
		leftScoreboard.addWidget(new ScoreboardWidget(coinImage, "$" + number.format(vialsFilled * filledVialPrice)));
		
		rightScoreboard.addWidget(new ScoreboardWidget(timeImage, millisToClock(System.currentTimeMillis() - startTime)));
		rightScoreboard.addWidget(new ScoreboardWidget(sumImage, new Integer(sum).toString())); 
		
		leftScoreboard.drawScoreboard(g);
		rightScoreboard.drawScoreboard(g);
		
		RoundRectangle2D progressBackground = new RoundRectangle2D.Float(
				Scoreboard.gameCanvasRight - 128,
				rightScoreboard.getHeight() + 30,
				128,
				8,
				5,
				5);
		
		Double percentToWidth = Math.floor(128 * (skills.getPercentToNextLevel(Constants.STAT_MAGIC) / 100));
		
		RoundRectangle2D progressBar = new RoundRectangle2D.Float(
				Scoreboard.gameCanvasRight - 128,
				rightScoreboard.getHeight() + 31,
				percentToWidth.intValue(),
				8,
				5,
				5);
		
		g.setColor(new Color(0, 0, 0, 127));
		g.draw(progressBackground);
		
		g.setColor(new Color(0, 0, 200, 191));
		g.fill(progressBar);
		
		return ;
	}
		
	private boolean canCastHumidify() {
		boolean hasAstralRune = false, hasFireRune = false, hasWaterRune = false;
		
		if(inventory.getCount(astralRuneID) >= 1)
			hasAstralRune = true;
		
		if(inventory.getCount(fireRuneID) >= 1 || hasFireStaff || hasSteamStaff)
			hasFireRune = true;
		
		if(inventory.getCount(waterRuneID) >= 3 || hasWaterStaff || hasSteamStaff)
			hasWaterRune = true;
		
		return (hasAstralRune && hasFireRune && hasWaterRune);
	}
	
	private class Antiban implements Runnable {
		@Override
		public void run() {
			while(!isStopping) {
				switch(random(1, 11) % 2) {
				case 1:
					log("Antiban: Spinning camera!");
					
					if(random(1,11) % 2 == 0) {
						isCameraRotating = true;
						input.pressArrow(Input.ARROW_LEFT, random(1500, 3500));
						isCameraRotating = false;
					} else {
						isCameraRotating = true;
						input.pressArrow(Input.ARROW_RIGHT, random(1500, 3500));
						isCameraRotating = false;
					}
					
					long c1Timeout = System.currentTimeMillis() + random(30000, 60000);
					while(System.currentTimeMillis() < c1Timeout && !isStopping) {}
					
					break;

				default:
					log("Antiban: No need to rotate this cycle...");
					
					long c2Timeout = System.currentTimeMillis() + random(30000, 60000);
					while(System.currentTimeMillis() < c2Timeout && !isStopping) {}
					
					break;
				}
			}
			
			log("Antiban: Shutting down...");
		}
	}
	
	public class Scoreboard {
		public static final int TOP_LEFT = 1, TOP_RIGHT = 2, BOTTOM_LEFT = 3, BOTTOM_RIGHT = 4;
		public static final int gameCanvasTop = 25, gameCanvasLeft = 25, gameCanvasBottom = 309, gameCanvasRight = 487;
		
		private ImageObserver observer = null;
		
		private int scoreboardLocation, scoreboardX, scoreboardY, scoreboardWidth, scoreboardHeight, scoreboardArc;
		
		private ArrayList<ScoreboardWidget> widgets = new ArrayList<ScoreboardWidget>();
		
		public Scoreboard(int scoreboardLocation, int width, int arc) {
			this.scoreboardLocation = scoreboardLocation;
			scoreboardHeight = 10;
			scoreboardWidth = width;
			scoreboardArc = arc;
			
			switch(scoreboardLocation) {
				case 1:
					scoreboardX = gameCanvasLeft;
					scoreboardY = gameCanvasTop;
				break;
				
				case 2:
					scoreboardX = gameCanvasRight - scoreboardWidth;
					scoreboardY = gameCanvasTop;
				break;
				
				case 3:
					scoreboardX = gameCanvasLeft;
				break;
				
				case 4:
					scoreboardX = gameCanvasRight - scoreboardWidth;
				break;
			}
		}
		
		public void addWidget(ScoreboardWidget widget) {
			widgets.add(widget);
		}
		
		public boolean drawScoreboard(Graphics2D g) {
			try {
				for (ScoreboardWidget widget : widgets) {
					scoreboardHeight += widget.getWidgetImage().getHeight(observer) + 4;
				}
				
				if(scoreboardLocation == 3 || scoreboardLocation == 4) {
					scoreboardY = gameCanvasBottom - scoreboardHeight;
				}
				
				RoundRectangle2D scoreboard = new RoundRectangle2D.Float(
					scoreboardX,
					scoreboardY,
					scoreboardWidth,
					scoreboardHeight,
					scoreboardArc,
					scoreboardArc
				);
				
				g.setColor(new Color(0, 0, 0, 127));
				g.fill(scoreboard);
				
				int x = scoreboardX + 5;
				int y = scoreboardY + 5;
				for (ScoreboardWidget widget : widgets) {
					widget.drawWidget(g, x, y);
					y += widget.getWidgetImage().getHeight(observer) + 4;
				}
				
				return true;
			} catch(Exception e) {}
			
			return false;
		}
		
		public int getHeight() {
			return scoreboardHeight;
		}
	}
	
	public class ScoreboardWidget {
		private ImageObserver observer = null;
		private Image widgetImage;
		private String widgetText;
		
		public ScoreboardWidget(Image widgetImage, String widgetText) {
			this.widgetImage = widgetImage;
			this.widgetText = widgetText;
		}
		
		public Image getWidgetImage() {
			return widgetImage;
		}
		
		public String getWidgetText() {
			return widgetText;
		}
		
		public void drawWidget(Graphics2D g, int x, int y) {
			g.setColor(Color.white);
			g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
			
			g.drawImage(widgetImage, x, y, observer);
			g.drawString(widgetText, x + widgetImage.getWidth(observer) + 4, y + 12);
		}
	}
	
	private String millisToClock(long milliseconds) {
		long seconds = (milliseconds / 1000), minutes = 0, hours = 0;
		
		if (seconds >= 60) {
			minutes = (seconds / 60);
			seconds -= (minutes * 60);
		}
		
		if (minutes >= 60) {
			hours = (minutes / 60);
			minutes -= (hours * 60);
		}
		
		return (hours < 10 ? "0" + hours + ":" : hours + ":")
				+ (minutes < 10 ? "0" + minutes + ":" : minutes + ":")
				+ (seconds < 10 ? "0" + seconds : seconds);
	}
}
