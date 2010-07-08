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
import java.util.Locale;

import javax.imageio.ImageIO;

import com.quirlion.script.Constants;
import com.quirlion.script.Input;
import com.quirlion.script.Script;
import com.quirlion.script.types.GEItem;
import com.quirlion.script.types.Interface;

public class AHumidifier extends Script {
	private boolean hasFireStaff = false, hasSteamStaff = false, hasWaterStaff = false;
	private boolean isStopping = false;
	
	private int astralRuneID = 9075, fireRuneID = 554, waterRuneID = 555;
	private int fireStaffID = 1387, steamStaffID = 11736, waterStaffID = 1383;
	private int emptyVialID = 229, filledVialID = 227;
	private int filledVialPrice = 0, humidifierCasts = 0, tries = 0, vialsFilled = 0;
	private int emptyVialsInInventory = 0;
	private int startingMagicXP = 0;
	
	private long startTime = 0;
	
	private Antiban antiban = null;
	private Image coinImage, drinkImage, sumImage, timeImage, weatherImage;
	private ImageObserver observer = null;
	private Interface humidifyInterface = null;
	private Thread antibanThread = null;
	
	@Override
	public void onStart() {
		try {
			coinImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/coins.png"));
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
	}
	
	@Override
	public int loop() {
		if(!canCastHumidify()) {
			log("Unable to cast humidify, exiting...");
			isStopping = true;
			stopScript();
		}
		
		if(inventory.getCount(filledVialID) > 0 || !inventory.containsItem(emptyVialID, filledVialID)) {
			if(bank.open() || bank.isOpen()) {
				if(inventory.containsItem(filledVialID)) {
					bank.deposit(filledVialID, 0);
					
					tries = 0;
					while(inventory.containsItem(filledVialID) && tries < 5) {
						wait(1000);
						bank.deposit(filledVialID, 0);
						tries++;
					}
					
					if(tries >= 5) return 1;
				} else {
					if(bank.getItemCount(emptyVialID) <= 1) {
						log("You only have one vial left, exiting...");
						isStopping = true;
						stopScript();
					}
					
					bank.withdraw(emptyVialID, 0);
					
					tries = 0;
					while(!inventory.containsItem(emptyVialID) && tries < 5) {
						wait(1000);
						bank.withdraw(emptyVialID, 0);
						tries++;
					}
					
					tries = 0;
					while(emptyVialsInInventory == 0 && tries < 5) {
						wait(1000);
						emptyVialsInInventory = inventory.getCount(emptyVialID);
						tries++;
					}
					
					bank.close();
					
					if(tries >= 5) return 1;
					
					tries = 0;
					while(bank.isOpen() && tries < 5) {
						wait(1000);
						bank.close();
						tries++;
					}
					
					return 1;
				}
			}
		}
		
		if(inventory.getCount(emptyVialID) > 0) {
			if(tabs.getCurrentTab() != Constants.TAB_MAGIC) tabs.openTab(Constants.TAB_MAGIC);
			
			if(humidifyInterface == null)
				humidifyInterface = interfaces.get(430, 29);
			
			if(humidifyInterface != null) {
				input.moveMouse(humidifyInterface.getRealX(), humidifyInterface.getRealY());
				humidifyInterface.click();
				
				wait(3000);
				
				while(tabs.getCurrentTab() != Constants.TAB_INVENTORY) {
					tabs.openTab(Constants.TAB_INVENTORY);
					wait(1000);
					tries++;
				}
				
				if(!inventory.containsItem(emptyVialID) && inventory.containsItem(filledVialID)) {
					vialsFilled += inventory.getCount(filledVialID);
					emptyVialsInInventory = 0;
					humidifierCasts++;
				}
				
				return 3000;
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
		
		int rightBoxX = interfaces.getMinimap().getRealX() - 178;
		
		//Rectangles
		RoundRectangle2D clockBackground = new RoundRectangle2D.Float(
				interfaces.getMinimap().getRealX() - 183,
				20,
				128,
				47,
				5,
				5);
		
		RoundRectangle2D progressBackground = new RoundRectangle2D.Float(
				interfaces.getMinimap().getRealX() - 183,
				72,
				128,
				8,
				5,
				5);
		
		Double percentToWidth = Math.floor(128 * (skills.getPercentToNextLevel(Constants.STAT_MAGIC) / 100));
		
		RoundRectangle2D progressBar = new RoundRectangle2D.Float(
				interfaces.getMinimap().getRealX() - 183,
				72,
				percentToWidth.intValue(),
				8,
				5,
				5);
		
		RoundRectangle2D scoreboardBackground = new RoundRectangle2D.Float(
				20,
				20,
				128,
				71,
				5,
				5);
		
		g.setColor(new Color(0, 0, 0, 127));
		g.fill(clockBackground);
		g.draw(progressBackground);
		g.fill(scoreboardBackground);
		
		g.setColor(new Color(0, 0, 200, 127));
		g.fill(progressBar);
		
		//Text
		g.setColor(Color.white);
		g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		
		NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
		
		g.drawString(nf.format(humidifierCasts), 48, 39);
		g.drawString(nf.format(vialsFilled), 48, 58);
		g.drawString("$" + nf.format(vialsFilled * filledVialPrice), 48, 77);
		
		if(startTime == 0)
			g.drawString("Loading", rightBoxX, 37);
		else
			g.drawString(millisToClock(System.currentTimeMillis() - startTime), rightBoxX, 37);
		
		g.drawString(nf.format(skills.getCurrentSkillXP(Constants.STAT_MAGIC) - startingMagicXP), rightBoxX, 58);
		
		//Images
		g.drawImage(weatherImage, 25, 25, observer);
		g.drawImage(drinkImage, 25, 25 + 16 + 4, observer);
		g.drawImage(coinImage, 25, 25 + 16 + 4 + 16 + 4, observer);
		g.drawImage(timeImage, interfaces.getMinimap().getRealX() - 76, 25, observer);
		g.drawImage(sumImage, interfaces.getMinimap().getRealX() - 76, 25 + 16 + 4, observer);
		
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
					
					if(random(1,11) % 2 == 0)
						input.pressArrow(Input.ARROW_LEFT, random(1500, 3500));
					else
						input.pressArrow(Input.ARROW_RIGHT, random(1500, 3500));
					
					long c1Timeout = System.currentTimeMillis() + random(30000, 60000);
					while(System.currentTimeMillis() < c1Timeout) {}
					
					break;

				default:
					log("Antiban: No need to rotate this cycle...");
					
					long c2Timeout = System.currentTimeMillis() + random(30000, 60000);
					while(System.currentTimeMillis() < c2Timeout) {}
					
					break;
				}
			}
			
			log("Antiban: Shutting down...");
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
