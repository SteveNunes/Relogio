package application;

import java.awt.Rectangle;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import gameutil.FPSHandler;
import gui.util.ImageUtils;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
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
	private final static float circleY = screenH / 2.40f;
	private final static float circleR = screenW / 3;
	private final static int digitalY = 650;
	private static List<Color> colors = ImageUtils.getColorList();
	private static final String HORARIO_LOCAL = "Horário local";
	
	private Stage mainStage;
	private Scene mainScene;
	MenuBar menuBar;
	StackPane stackPane;
	private Canvas canvas;
	private Image numberSkin;
	private Image[] numberSkins;
	private Color bgDigitalColor;
	private FPSHandler fpsHandler;
	private Map<String, Long> timezones;
	private String selectedTimezone;
	private boolean isWindowOpened;
	private boolean softlySecondsPointerMove;
	private int lastSec;
	private int digitalScrollNumberType;
	private int numberSkinIndex;
	private int lastTranspNum;
	private int[] analogicColorsIndex;
	private int[][] times;
	private int repColorIndex;
	private long currentTimeZone;
	private float lastTranspNumVal;
	private double[] scrollDigit;
	
	@SuppressWarnings("serial")
	@Override
	public void start(Stage stage) {
		mainStage = stage;
		mainStage.setFullScreenExitHint("Pressione F12 para sair do modo tela cheia");
		fpsHandler = new FPSHandler(60);
		isWindowOpened = true;
		lastSec = -1;
		lastTranspNum = -1;
		lastTranspNumVal = 0;
		times = new int[3][];
		timezones = new HashMap<>() {{ put(selectedTimezone = HORARIO_LOCAL, 0L); }};
		loadConfigsFromDisk();
		scrollDigit = new double[] {0, 0, 0, 0, 0, 0};
		canvas = new Canvas(screenW, screenH - 37);
		canvas.getGraphicsContext2D().setImageSmoothing(false);
		loadDigitalSkins();
		replaceColors(false);
		menuBar = new MenuBar();
		stackPane = new StackPane(canvas, menuBar);
		stackPane.setAlignment(Pos.TOP_CENTER);
		stackPane.setBackground(Background.fill(Color.BLACK));
		mainScene = new Scene(stackPane);
		mainScene.setOnMouseEntered(e -> mainScene.setCursor(Cursor.NONE));
    mainScene.setOnMouseExited(e -> mainScene.setCursor(Cursor.DEFAULT));
		refreshMenuBar();
		setOnKeyPressEvents(mainScene);
		stage.setTitle("Relógio");
		stage.setScene(mainScene);
		stage.setWidth(screenW);
		stage.setHeight(screenH);
		stage.setResizable(false);
		stage.setOnCloseRequest(e -> isWindowOpened = false);
		stage.show();
		mainLoop();
	}
	
	private static void addColorListToMenuItens(Menu menu, Predicate<Integer> isSelected, Consumer<Integer> consumerWhenClick) {
		for (int n = 0; n < colors.size(); n++) {
			final int nn = n;
			Color color = colors.get(n);
			WritableImage wi = new WritableImage(80, 30);
      for (int y, x = 0; x < wi.getWidth(); x++)
        for (y = 0; y < wi.getHeight(); y++)
					wi.getPixelWriter().setColor(x, y, color);
			CheckMenuItem checkMenuItem = new CheckMenuItem();
			checkMenuItem.setGraphic(new ImageView(wi));
			checkMenuItem.setSelected(isSelected.test(n));
			checkMenuItem.setOnAction(e -> consumerWhenClick.accept(nn));
			menu.getItems().add(checkMenuItem);
		}
	}
	
	private void refreshMenuBar() {
		if (menuBar == null)
			return;
		menuBar.getMenus().clear();
		Menu menu = new Menu("Menu");
		menuBar.getMenus().add(menu);

		Menu analogDef = new Menu("Definição relógio analógico");
		Menu alalogCores = new Menu("Cores"); 
		analogDef.getItems().add(alalogCores);
		String[] menus = {
			"Ponteiro dos segundos",
			"Ponteiro dos minutos",
			"Ponteiro das horas",
			"Círculo central",
			"Tracinhos finos (apagados)",
			"Tracinhos finos (acesos)",
			"Tracinhos grossos (apagados)",
			"Tracinhos grossos (acesos)",
			"Números (apagados)",
			"Números (acesos)",
			"Fundo"
		};
		for (int i = 0; i < analogicColorsIndex.length; i++) {
			final int ii = i;
			Menu cor = new Menu(menus[i]);
			alalogCores.getItems().add(cor);
			addColorListToMenuItens(cor, 
					n -> n == analogicColorsIndex[ii],
					n -> {
						analogicColorsIndex[ii] = n;
						refreshMenuBar();
					});
			}

		CheckMenuItem menuSoftlyMovePointer = new CheckMenuItem("Mover suavemente ponteiro de segundos (F4)");
		menuSoftlyMovePointer.setOnAction(e -> {
			softlySecondsPointerMove = !softlySecondsPointerMove; 
			menuSoftlyMovePointer.setSelected(softlySecondsPointerMove);
		});
		menuSoftlyMovePointer.setSelected(softlySecondsPointerMove);
		analogDef.getItems().add(menuSoftlyMovePointer);

		Menu digitalDef = new Menu("Definição relógio digital");
		Menu menuF1 = new Menu("Skin do número digital (F1)");
		digitalDef.getItems().add(menuF1);
		for (int n = 0; n < numberSkins.length; n++) {
			final int nn = n;
			WritableImage skin = ImageUtils.copyFromAnotherWritableImageArea(ImageUtils.convertToWritableImage(numberSkins[nn]), new Rectangle(0, 234, 65, 113));
			CheckMenuItem checkMenuItem = new CheckMenuItem();
			checkMenuItem.setGraphic(new ImageView(skin));
			checkMenuItem.setSelected(n == numberSkinIndex);
			checkMenuItem.setOnAction(e -> {
				numberSkinIndex = nn;
				refreshNumberSkin();
			});
			menuF1.getItems().add(checkMenuItem);
		}

		Menu menuF2 = new Menu("Cor do número digital (F2)");
		digitalDef.getItems().add(menuF2);
		for (int n = 2; ; n += 4) {
			if (numberSkins[numberSkinIndex].getPixelReader().getColor(n, 0)
					.equals(numberSkins[numberSkinIndex].getPixelReader().getColor(n + 2, 0)))
						break;
			final int nn = n - 2;
			WritableImage c1 = new WritableImage(20, 20);
			WritableImage c2 = new WritableImage(20, 20);
      for (int y, x = 0; x < 20; x++)
        for (y = 0; y < 20; y++) {
					c1.getPixelWriter().setColor(x, y, numberSkins[numberSkinIndex].getPixelReader().getColor(n, 0));
					c2.getPixelWriter().setColor(x, y, numberSkins[numberSkinIndex].getPixelReader().getColor(n + 2, 0));
				}
			HBox hBox = new HBox(new ImageView(c1), new ImageView(c2));
			hBox.setSpacing(5);
			CheckMenuItem checkMenuItem = new CheckMenuItem();
			checkMenuItem.setGraphic(hBox);
			checkMenuItem.setSelected(nn == repColorIndex);
			checkMenuItem.setOnAction(e -> {
				repColorIndex = nn;
				replaceColors(false);
			});
			menuF2.getItems().add(checkMenuItem);
		}

		Menu menuF3 = new Menu("Estilo de rolagem do número digital (F3)");
		digitalDef.getItems().add(menuF3);
		String[] itens = {"Parado", "Rolagem contínua", "Rolagem rápida ao mudar dígito"};
		for (int n = 0; n < 3; n++) {
			final int nn = n;
			CheckMenuItem checkMenuItem = new CheckMenuItem(itens[n]);
			checkMenuItem.setSelected(n == digitalScrollNumberType);
			checkMenuItem.setOnAction(e -> {
				digitalScrollNumberType = nn;
				for (int i = 0; i < 6; i++)
					scrollDigit[i] = 0;
				refreshMenuBar();
			});
			menuF3.getItems().add(checkMenuItem);
		}

		menu.getItems().add(analogDef);
		menu.getItems().add(digitalDef);

		menu.getItems().add(new SeparatorMenuItem());
		Menu menuTimezone = new Menu("Horários");
		MenuItem menuItem = new MenuItem("Alterar hora atual (F5)");
		menuItem.setOnAction(e -> changeTime());
		menuTimezone.getItems().add(menuItem);
		menuItem = new MenuItem("Adicionar horário");
		menuItem.setOnAction(e -> {
			long i = currentTimeZone;
			String str = Alerts.textPrompt("Prompt", "Adicionar horário", null, "Digite o nome para o novo horário");
			if (str != null) {
				if (timezones.containsKey(str)) {
					Alerts.error("Erro", "Já existe um horário adicionado com o nome:\n" + str);
					return;
				}
				changeTime();
				if (currentTimeZone != i ) {
					timezones.put(str, currentTimeZone);
					refreshMenuBar();
					Alerts.confirmation("Confirmação", "Horário adicionado com sucesso");
				}
			}
			else
				currentTimeZone = i;
		});
		menuTimezone.getItems().add(menuItem);

		Menu menuAddTimezone = new Menu("Selecionar horário");
		for (String str : timezones.keySet()) {
			final long t = currentTimeZone;
			CheckMenuItem item = new CheckMenuItem(str);
			item.setOnAction(e -> changeTime(timezones.get(str)));
			item.setSelected(t == currentTimeZone);
			menuAddTimezone.getItems().add(item);
		}
		menuTimezone.getItems().add(menuAddTimezone);

		Menu menuRemoveTimezone = new Menu("Remove horário");
		menuRemoveTimezone.setDisable(timezones.size() == 1);
		for (String str : timezones.keySet()) {
			MenuItem item = new MenuItem(str);
			item.setOnAction(e -> {
				timezones.remove(str);
				refreshMenuBar();
			});
			menuRemoveTimezone.getItems().add(item);
		}
		menuTimezone.getItems().add(menuRemoveTimezone);
		
		menu.getItems().add(menuTimezone);

		menu.getItems().add(new SeparatorMenuItem());
		menuItem = new MenuItem("Tela inteira (F12)");
		menuItem.setOnAction(e -> setFullScreen());
		menu.getItems().add(menuItem);

		menu.getItems().add(new SeparatorMenuItem());
		menuItem = new MenuItem("Fechar relógio");
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
				if (++digitalScrollNumberType == 3)
					digitalScrollNumberType = 0;
				for (int i = 0; i < 6; i++)
					scrollDigit[i] = 0;
			}
			if (e.getCode() == KeyCode.F4)
				softlySecondsPointerMove = !softlySecondsPointerMove;
			if (e.getCode() == KeyCode.F5)
				changeTime();
			if (e.getCode() == KeyCode.F12)
				setFullScreen();
		});
	}

	private void setFullScreen() {
		mainStage.setFullScreen(!mainStage.isFullScreen());
		menuBar.setVisible(!mainStage.isFullScreen());
		stackPane.setAlignment(mainStage.isFullScreen() ? Pos.CENTER : Pos.TOP_CENTER);
	}

	private void close() {
		mainStage.close();
		isWindowOpened = false;
	}

	private void changeTime(Long timezone) {
		if (timezone != null) {
			currentTimeZone = timezone;
			return;
		}
		String str = Alerts.textPrompt("Prompt", "Alterar hora", null, "Digite o novo horário no formato HH:MM:SS\nOu digite RESET para resetar para a hora do sistema");
		if (str != null) {
			if (str.toLowerCase().equals("reset"))
				currentTimeZone = 0;
			else if (!Pattern.matches("\\d{1,2}:\\d{1,2}:\\d{1,2}", str))
				Alerts.error("Erro", "Formato de hora inválido!");
			else {
				Date date = MyCalendar.changeTimeFromDate(new Date(), str);
				currentTimeZone = date.getTime() - System.currentTimeMillis();
			}
			refreshMenuBar();
		}
	}
	
	private void changeTime()
		{ changeTime(null); }

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
		{ return new Date(System.currentTimeMillis() + currentTimeZone + addTime); }
	
	private Date changedDate()
		{ return changedDate(0); }

	private void drawAnalogicClock(GraphicsContext gc) {
		gc.setFill(colors.get(analogicColorsIndex[10]));
		gc.fillRect(0, 0, screenW, canvas.getHeight() - (canvas.getHeight() - digitalY));
		gc.setFont(Font.font("Arial", 24));
		gc.setTextAlign(TextAlignment.CENTER);
		for (int x, n = 0; n < 60; n++) {
			Position p = Position.circleDot(circleX, circleY, circleR + 10, 60, n - 15);
			Position p2 = Position.circleDot(circleX, circleY, circleR + 20, 60, n - 15);
			Position p3 = Position.circleDot(circleX, circleY, circleR + 50, 60, n - 15);
			for (int z = 0; z < 2; z++) {
				gc.setGlobalAlpha(z == 0 || n != MyCalendar.getSecondFromDate(changedDate()) ? 1 : (float)(1f - MyCalendar.getCurrentMicroSecond() / 1000f));
				if (z == 0 || n != MyCalendar.getSecondFromDate(changedDate()))
					gc.setStroke(n % 5 == 0 ? colors.get(analogicColorsIndex[6]) : colors.get(analogicColorsIndex[4]));
				else
					gc.setStroke(n % 5 == 0 ? colors.get(analogicColorsIndex[7]) : colors.get(analogicColorsIndex[5]));
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
					gc.setStroke(z == 0 || (MyCalendar.getSecondFromDate(changedDate()) != n && lastTranspNum != n) ? colors.get(analogicColorsIndex[8]) : colors.get(analogicColorsIndex[9]));
					gc.setFill(z == 0 || (MyCalendar.getSecondFromDate(changedDate()) != n && lastTranspNum != n) ? colors.get(analogicColorsIndex[8]) : colors.get(analogicColorsIndex[9]));
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
			gc.setStroke(colors.get(analogicColorsIndex[n]));
			gc.setLineWidth(4);
			gc.strokeLine(circleX, circleY, circleX + p.getX(), circleY + p.getY());
		}
		gc.setFill(colors.get(analogicColorsIndex[3]));
		gc.fillOval(circleX - 10, circleY - 10, 20, 20);
	}

	private void drawDigitalHour(GraphicsContext gc) {
		int micro = MyCalendar.getCurrentMicroSecond();
		if (digitalScrollNumberType > 0 && (digitalScrollNumberType == 2 || lastSec != MyCalendar.getCurrentSecond())) {
			lastSec = MyCalendar.getCurrentSecond();
			times[0] = getTimeIntArray();
			times[1] = getTimeIntArray(changedDate(1000L));
			for (int i = 0; i < 6; i++) {
				if (digitalScrollNumberType != 2 || micro < 750)
					scrollDigit[i] = 0;
				if (scrollDigit[i] == 0 && times[0][i] != times[1][i] && (digitalScrollNumberType == 1 || micro >= 750))
					scrollDigit[i] = 0.000001;
			}
		}
		for (int i = 0; i < 6; i++)
			if (scrollDigit[i] > 0)
				scrollDigit[i] += 1.883333 * (digitalScrollNumberType == 1 ? 1 : 4);
		gc.setFill(bgDigitalColor);
		gc.fillRect(0, canvas.getHeight() - (canvas.getHeight() - digitalY), screenW, (canvas.getHeight() - digitalY));
		times[0] = getTimeIntArray(changedDate(-1000L));
		times[1] = getTimeIntArray();
		times[2] = getTimeIntArray(changedDate(1000L));
		for (int y = -1; y < 2; y++) {
			for (int i = 0, x = 75; i < 6; i++) {
				gc.drawImage(numberSkin, 0, 113 * (times[y + 1][i] + 2) + 8, 65, 113, x, (digitalY + 116 * y) - scrollDigit[i], 65, 113);
				x += i == 1 || i == 3 ? 89 : 69;
			}
			for (int i = 0, x = 189; i < 2; i++, x += 158)
				gc.drawImage(numberSkin, 0, 113 * (MyCalendar.getCurrentMicroSecond() >= 500 ? 13 : 14) + 8, 65, 113, x, (digitalY + 116 * y), 65, 113);
		}
		
	}

	private void loadConfigsFromDisk() {
		IniFile ini = IniFile.getNewIniFileInstance("config.ini");
		try {
			int max = 11;
			String[] split = ini.read("CONFIG", "analogicColorsIndex").split(" ");
			if (split.length < max)
				throw new RuntimeException();
			analogicColorsIndex = new int[max];
			for (int n = 0; n < max; n++)
				analogicColorsIndex[n] = Integer.parseInt(split[n]);
		}
		catch (Exception e)
			{ analogicColorsIndex = new int[] {20, 83, 10, 8, 31, 146, 29, 49, 8, 1, 14}; }
		try
			{ repColorIndex = Integer.parseInt(ini.read("CONFIG", "repColorIndex")); }
		catch (Exception e)
			{ repColorIndex = 0; }
		try
			{ digitalScrollNumberType = Integer.parseInt(ini.read("CONFIG", "digitalScrollNumberType")); }
		catch (Exception e)
			{ digitalScrollNumberType = 0; }
		try
			{ numberSkinIndex = Integer.parseInt(ini.read("CONFIG", "numberSkinIndex")); }
		catch (Exception e)
			{ numberSkinIndex = 0; }
		try
			{ softlySecondsPointerMove = Boolean.parseBoolean(ini.read("CONFIG", "softlySecondsPointerMove")); }
		catch (Exception e)
			{ softlySecondsPointerMove = true; }
		try {
			selectedTimezone = ini.read("CONFIG", "softlySecondsPointerMove");
			currentTimeZone = Long.parseLong(ini.read("TIMEZONES", selectedTimezone));
		}
		catch (Exception e) {
			selectedTimezone = HORARIO_LOCAL;
			currentTimeZone = 0;
		}
		try {
			for (String str : ini.getItemList("TIMEZONES"))
				if (!str.equals(HORARIO_LOCAL ))
					timezones.put(str, Long.parseLong(ini.read("TIMEZONES", str)));
		}
		catch (Exception e) {
			
		}
	}

	private void saveConfigsToDisk() {
		try {
			IniFile ini = IniFile.getNewIniFileInstance("config.ini");
			ini.clearFile();
			ini.write("CONFIG", "repColorIndex", "" + repColorIndex);
			ini.write("CONFIG", "digitalScrollNumberType", "" + digitalScrollNumberType);
			ini.write("CONFIG", "numberSkinIndex", "" + numberSkinIndex);
			ini.write("CONFIG", "softlySecondsPointerMove", "" + softlySecondsPointerMove);
			for (String str : timezones.keySet())
				ini.write("TIMEZONES", str, "" + timezones.get(str));
			String s = "" + analogicColorsIndex[0];
			for (int i = 1; i < analogicColorsIndex.length; i++)
				s += " " + analogicColorsIndex[i];
			ini.write("CONFIG", "analogicColorsIndex", s);
		}
		catch (Exception e) {}
	}

	private void mainLoop() {
		GraphicsContext gc = canvas.getGraphicsContext2D();
//		gc.setFill(Color.BLACK);
	//	gc.fillRect(0, 0, DesktopUtils.getHardwareScreenWidth(), DesktopUtils.getHardwareScreenHeight());
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
