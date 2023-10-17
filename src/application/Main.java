package application;

import java.io.File;
import java.util.Date;
import java.util.regex.Pattern;

import gameutil.FPSHandler;
import gui.util.ImageUtils;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import objmoveutils.Position;
import util.Alerts;
import util.IniFile;
import util.Misc;
import util.MyCalendar;

public class Main extends Application {
	
	private final static int screenW = 600;
	private final static int screenH = 800;
	private final static int[][] clockTicks = {{60000, 3600000, 43200000}, {60, 3600000, 43200000}};
	private final static float circleX = screenW / 2;
	private final static float circleY = screenH / 2.55f;
	private final static float circleR = screenW / 3;
	
	private Stage mainStage;
	MenuBar menuBar;
	private Canvas canvas;
	private Image numberSkin;
	private Image[] numberSkins;
	private Color bgDigitalColor;
	private FPSHandler fpsHandler;
	private boolean isWindowOpened;
	private boolean softlySecondsPointerMove;
	private int lastSec;
	private int digitalScrollNumberType;
	private int numberSkinIndex;
	private int lastTranspNum;
	private int[][] times;
	private int repColorIndex;
	private long changedTime;
	private float lastTranspNumVal;
	private double[] scrollDigit;
	
	@Override
	public void start(Stage stage) {
		mainStage = stage;
		fpsHandler = new FPSHandler(60);
		isWindowOpened = true;
		lastSec = -1;
		lastTranspNum = -1;
		lastTranspNumVal = 0;
		times = new int[3][];
		loadConfigsFromDisk();
		scrollDigit = new double[] {0, 0, 0, 0, 0, 0};
		canvas = new Canvas(screenW, screenH);
		canvas.getGraphicsContext2D().setImageSmoothing(false);
		loadDigitalSkins();
		replaceColors(false);
		menuBar = new MenuBar();
		Scene scene = new Scene(new VBox(menuBar, canvas));
		refreshMenuBar();
		setOnKeyPressEvents(scene);
		stage.setTitle("Relógio");
		stage.setScene(scene);
		stage.setWidth(screenW);
		stage.setHeight(screenH);
		stage.setResizable(false);
		stage.setOnCloseRequest(e -> isWindowOpened = false);
		stage.show();
		mainLoop();
	}
	
	private void refreshMenuBar() {
		if (menuBar == null)
			return;
		menuBar.getMenus().clear();
		Menu menu = new Menu("Menu");
		menuBar.getMenus().add(menu);

		Menu menuF1 = new Menu("Digital Number Skin");
		menu.getItems().add(menuF1);
		for (int n = 0; n < numberSkins.length; n++) {
			final int nn = n;
			CheckMenuItem checkMenuItem = new CheckMenuItem("Skin " + (n + 1));
			checkMenuItem.setSelected(n == numberSkinIndex);
			checkMenuItem.setOnAction(e -> {
				numberSkinIndex = nn;
				refreshNumberSkin();
			});
			menuF1.getItems().add(checkMenuItem);
		}

		Menu menuF2 = new Menu("Digital Number Color");
		menu.getItems().add(menuF2);
		for (int i = 1, n = 2; ; n += 4, i++) {
			if (numberSkins[numberSkinIndex].getPixelReader().getColor(n, 0)
					.equals(numberSkins[numberSkinIndex].getPixelReader().getColor(n + 2, 0)))
						break;
			final int nn = n - 2;
			CheckMenuItem checkMenuItem = new CheckMenuItem("Color " + i);
			checkMenuItem.setSelected(nn == repColorIndex);
			checkMenuItem.setOnAction(e -> {
				repColorIndex = nn;
				replaceColors(false);
			});
			menuF2.getItems().add(checkMenuItem);
		}

		Menu menuF3 = new Menu("Digital Number Scroll Style");
		menu.getItems().add(menuF3);
		for (int n = 1; n <= 3; n++) {
			final int nn = n;
			CheckMenuItem checkMenuItem = new CheckMenuItem("Style " + n);
			checkMenuItem.setSelected(n == digitalScrollNumberType);
			checkMenuItem.setOnAction(e -> {
				digitalScrollNumberType = nn;
				for (int i = 0; i < 6; i++)
					scrollDigit[i] = 0;
				refreshMenuBar();
			});
			menuF3.getItems().add(checkMenuItem);
		}

		menu.getItems().add(new SeparatorMenuItem());
		CheckMenuItem checkMenuItem = new CheckMenuItem("Softly move seconds pointer");
		checkMenuItem.setOnAction(e -> {
			softlySecondsPointerMove = !softlySecondsPointerMove; 
			checkMenuItem.setSelected(softlySecondsPointerMove);
		});
		checkMenuItem.setSelected(softlySecondsPointerMove);
		menu.getItems().add(checkMenuItem);

		menu.getItems().add(new SeparatorMenuItem());
		MenuItem menuItem = new MenuItem("Change time");
		menuItem.setOnAction(e -> changeTime());
		menu.getItems().add(menuItem);

		menu.getItems().add(new SeparatorMenuItem());
		menuItem = new MenuItem("Close");
		menuItem.setOnAction(e -> close());
		menu.getItems().add(menuItem);
	}

	private void loadDigitalSkins() {
		int i = 0;
		while (new File(".\\src\\digitos" + ++i + ".png").exists());
		numberSkins = new Image[i - 1];
		for (int n = 1; n < i ; n++)
			numberSkins[n - 1] = ImageUtils.removeBgColor(new Image("digitos" + n + ".png"));
	}

	private void setOnKeyPressEvents(Scene scene) {
		scene.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ESCAPE)
				close();
			if (e.getCode() == KeyCode.F1) {
				if (++numberSkinIndex == numberSkins.length)
					numberSkinIndex = 0;
				refreshNumberSkin();
			}
			if (e.getCode() == KeyCode.F2)
				replaceColors();
			if (e.getCode() == KeyCode.F3) {
				if (++digitalScrollNumberType == 4)
					digitalScrollNumberType = 1;
				for (int i = 0; i < 6; i++)
					scrollDigit[i] = 0;
			}
			if (e.getCode() == KeyCode.F4)
				softlySecondsPointerMove = !softlySecondsPointerMove;
			if (e.getCode() == KeyCode.F5)
				changeTime();
		});
	}

	private void close() {
		mainStage.close();
		isWindowOpened = false;
	}

	private void changeTime() {
		String str = Alerts.textPrompt("Prompt", "Alterar hora", null, "Digite o novo horário no formato HH:MM:SS\nOu digite RESET para resetar para a hora do sistema");
		if (str != null) {
			if (str.toLowerCase().equals("reset"))
				changedTime = 0;
			else if (!Pattern.matches("\\d{1,2}:\\d{1,2}:\\d{1,2}", str))
				Alerts.error("Erro", "Formato de hora inválido!");
			else {
				Date date = MyCalendar.changeTimeFromDate(new Date(), str);
				changedTime = date.getTime() - System.currentTimeMillis();
			}
		}
	}

	private void refreshNumberSkin() {
		numberSkin = numberSkins[numberSkinIndex];
		repColorIndex = 0;
		replaceColors(false);
	}

	private void replaceColors(boolean changeToNext) {
		if (changeToNext)
			repColorIndex += 4;
		PixelReader pr = numberSkins[numberSkinIndex].getPixelReader();
		if (pr.getArgb(2 + repColorIndex, 0) == pr.getArgb(4 + repColorIndex, 0))
			repColorIndex = 0;
		int[] before = {pr.getArgb(2, 0), pr.getArgb(4, 0)};
		int[] after = {pr.getArgb(2 + repColorIndex, 0), pr.getArgb(4 + repColorIndex, 0)};
		bgDigitalColor = numberSkins[numberSkinIndex].getPixelReader().getColor(4 + repColorIndex,  2);
		numberSkin = ImageUtils.replaceColor(numberSkins[numberSkinIndex], before, after);
		refreshMenuBar();
	}
	
	private void replaceColors()
		{ replaceColors(true); }

	private int[] getTimeIntArray(Date date) {
		String horaStr = MyCalendar.dateToString(date, "HHmmss");
		int[] time = new int[6];
		for (int i = 0; i < 6; i++)
			time[i] = horaStr.charAt(i) - '0';
		return time;
	}
	
	private int[] getTimeIntArray()
		{ return getTimeIntArray(changedDate()); }
	
	private Date changedDate(long addTime)
		{ return new Date(System.currentTimeMillis() + changedTime + addTime); }
	
	private Date changedDate()
		{ return changedDate(0); }

	private void drawAnalogicClock(GraphicsContext gc) {
		gc.setFill(Color.CADETBLUE);
		gc.fillRect(0, 0, screenW, screenH - 180);
		gc.setFont(Font.font("Arial", 24));
		gc.setTextAlign(TextAlignment.CENTER);
		for (int x, n = 0; n < 60; n++) {
			Position p = Position.circleDot(circleX, circleY, circleR + 10, 60, n - 15);
			Position p2 = Position.circleDot(circleX, circleY, circleR + 20, 60, n - 15);
			Position p3 = Position.circleDot(circleX, circleY, circleR + 50, 60, n - 15);
			for (int z = 0; z < 2; z++) {
				gc.setGlobalAlpha(z == 0 || n != MyCalendar.getSecondFromDate(changedDate()) ? 1 : (float)(1f - MyCalendar.getCurrentMicroSecond() / 1000f));
				if (z == 0 || n != MyCalendar.getSecondFromDate(changedDate()))
					gc.setStroke(n % 5 == 0 ? Color.BROWN : Color.LIGHTGRAY);
				else
					gc.setStroke(n % 5 == 0 ? Color.SANDYBROWN : Color.WHITE);
				gc.setLineWidth(n % 5 == 0 ? 3 : 1);
				gc.strokeLine(circleX + p.getX(), circleY + p.getY(), circleX + p2.getX(), circleY + p2.getY());
			}
			gc.setGlobalAlpha(1);
			if (n % 5 == 0) {
				if (lastTranspNumVal < 0.003333 && MyCalendar.getSecondFromDate(changedDate()) == n) {
					lastTranspNum = n;
					lastTranspNumVal = 1;
				}
				gc.setLineWidth(1);
				for (int z = 0; z < 2; z++) {
					gc.setGlobalAlpha(z == 1 && n == lastTranspNum ? lastTranspNumVal : 1);
					gc.setStroke(z == 0 || (MyCalendar.getSecondFromDate(changedDate()) != n && lastTranspNum != n) ? Color.BLACK : Color.YELLOW);
					gc.setFill(z == 0 || (MyCalendar.getSecondFromDate(changedDate()) != n && lastTranspNum != n) ? Color.BLACK : Color.YELLOW);
					x = n == 0 ? 12 : n / 5;
					gc.fillText((x < 10 ? " " : "") + x, circleX + p3.getX(), circleY + p3.getY() + gc.getFont().getSize() / 3);
					gc.strokeText((x < 10 ? " " : "") + x, circleX + p3.getX(), circleY + p3.getY() + gc.getFont().getSize() / 3);
				}
			}
		}
		lastTranspNumVal -= 0.003333;
		int[] hour = new int[3];
		hour[0] = (!softlySecondsPointerMove ? MyCalendar.getSecondFromDate(changedDate()) : MyCalendar.getSecondFromDate(changedDate()) * 1000 + MyCalendar.getCurrentMicroSecond());
		hour[1] = MyCalendar.getMinuteFromDate(changedDate()) * 60000 + MyCalendar.getSecondFromDate(changedDate()) * 1000 + MyCalendar.getCurrentMicroSecond();
		hour[2] = MyCalendar.getHourFromDate(changedDate()) * 3600000 + hour[1];
		for (int n = 0; n < 3; n++) {
			Position p = Position.circleDot(circleX, circleY, circleR - 40 * n, clockTicks[softlySecondsPointerMove ? 0 : 1][n], hour[n] - clockTicks[softlySecondsPointerMove ? 0 : 1][n] / 4);
			gc.setStroke(n == 0 ? Color.RED : n == 1 ? Color.GREEN : Color.BLUE);
			gc.setLineWidth(4);
			gc.strokeLine(circleX, circleY, circleX + p.getX(), circleY + p.getY());
		}
		gc.setFill(Color.BLACK);
		gc.fillOval(circleX - 10, circleY - 10, 20, 20);
	}

	private void drawDigitalHour(GraphicsContext gc) {
		int micro = MyCalendar.getCurrentMicroSecond();
		if (digitalScrollNumberType > 1 && (digitalScrollNumberType == 3 || lastSec != MyCalendar.getCurrentSecond())) {
			lastSec = MyCalendar.getCurrentSecond();
			times[0] = getTimeIntArray();
			times[1] = getTimeIntArray(changedDate(1000L));
			for (int i = 0; i < 6; i++) {
				if (digitalScrollNumberType != 3 || micro < 750)
					scrollDigit[i] = 0;
				if (scrollDigit[i] == 0 && times[0][i] != times[1][i] && (digitalScrollNumberType == 2 || micro >= 750))
					scrollDigit[i] = 0.000001;
			}
		}
		for (int i = 0; i < 6; i++)
			if (scrollDigit[i] > 0)
				scrollDigit[i] += 1.883333 * (digitalScrollNumberType == 2 ? 1 : 4);
		gc.setFill(bgDigitalColor);
		gc.fillRect(0, screenH - 222, screenW, 222);
		times[0] = getTimeIntArray(changedDate(-1000L));
		times[1] = getTimeIntArray();
		times[2] = getTimeIntArray(changedDate(1000L));
		for (int y = -1; y < 2; y++) {
			for (int i = 0, x = 75; i < 6; i++) {
				gc.drawImage(numberSkin, 0, 113 * (times[y + 1][i] + 2) + 8, 65, 113, x, (622 + 116 * y) - scrollDigit[i], 65, 113);
				x += i == 1 || i == 3 ? 89 : 69;
			}
			for (int i = 0, x = 189; i < 2; i++, x += 158)
				gc.drawImage(numberSkin, 0, 113 * (MyCalendar.getCurrentMicroSecond() >= 500 ? 13 : 14) + 8, 65, 113, x, (622 + 116 * y), 65, 113);
		}
	}

	private void loadConfigsFromDisk() {
		IniFile ini = IniFile.getNewIniFileInstance("config.ini");
		try
			{ repColorIndex = Integer.parseInt(ini.read("CONFIG", "repColorIndex")); }
		catch (Exception e)
			{ repColorIndex = 0; }
		try
			{ changedTime = Long.parseLong(ini.read("CONFIG", "changedTime")); }
		catch (Exception e)
			{ changedTime = 0; }
		try
			{ digitalScrollNumberType = Integer.parseInt(ini.read("CONFIG", "digitalScrollNumberType")); }
		catch (Exception e)
			{ digitalScrollNumberType = 1; }
		try
			{ numberSkinIndex = Integer.parseInt(ini.read("CONFIG", "numberSkinIndex")); }
		catch (Exception e)
			{ numberSkinIndex = 0; }
		try
			{ softlySecondsPointerMove = Boolean.parseBoolean(ini.read("CONFIG", "softlySecondsPointerMove")); }
		catch (Exception e)
			{ softlySecondsPointerMove = true; }
	}

	private void saveConfigsToDisk() {
		try {
			IniFile ini = IniFile.getNewIniFileInstance("config.ini");
			ini.clearFile();
			ini.write("CONFIG", "repColorIndex", "" + repColorIndex);
			ini.write("CONFIG", "changedTime", "" + changedTime);
			ini.write("CONFIG", "digitalScrollNumberType", "" + digitalScrollNumberType);
			ini.write("CONFIG", "numberSkinIndex", "" + numberSkinIndex);
			ini.write("CONFIG", "softlySecondsPointerMove", "" + softlySecondsPointerMove);
		}
		catch (Exception e) {}
	}

	private void mainLoop() {
		GraphicsContext gc = canvas.getGraphicsContext2D();
		drawDigitalHour(gc);		
		drawAnalogicClock(gc);
		fpsHandler.fpsCounter();
		if (isWindowOpened)
			Misc.runLater(() -> mainLoop());
		else
			saveConfigsToDisk();
	}

	public static void main(String[] args)
		{ launch(args); }

}
