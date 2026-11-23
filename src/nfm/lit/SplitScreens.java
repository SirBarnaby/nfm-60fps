package nfm.lit;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;

import nfm.lit.audio.Sound3D;

/**
 * Split screen's own screens, drawn as the game's menus are: the car select,
 * where both players choose a car at once and the race's computer cars,
 * screen and sound are set, and the results after a race, with what to do
 * next.
 */
final class SplitScreens {

    private final GameUi ui;

    SplitScreens(GameUi ui) {
        this.ui = ui;
    }

    /* ======================================================= the car select */

    /** Each player's car as chosen so far, and whether they are ready. */
    private final int[] car = new int[2];
    private final boolean[] ready = new boolean[2];
    /** Each player's left, right, accelerate, brake and handbrake as last seen, for the presses. */
    private final boolean[][] was = new boolean[2][5];
    private boolean selecting;
    /** Ticks since both players were ready; the stage select comes a moment after. */
    private int bothReady;
    private int spin;
    private int flicker;

    /* where the mouse can click, as last drawn */
    private final Rectangle[] prevCar = {new Rectangle(), new Rectangle()};
    private final Rectangle[] nextCar = {new Rectangle(), new Rectangle()};
    private final Rectangle[] readyBox = {new Rectangle(), new Rectangle()};
    private final Rectangle[] optionLess = {new Rectangle(), new Rectangle(), new Rectangle()};
    private final Rectangle[] optionMore = {new Rectangle(), new Rectangle(), new Rectangle()};
    private final Rectangle controls = new Rectangle();
    private final Rectangle backBox = new Rectangle();
    /** What the mouse went down on, acted on as it comes up: -1 nothing. */
    private int pressed = -1;

    private static final int OPTION_RIVALS = 0;
    private static final int OPTION_SCREEN = 1;
    private static final int OPTION_SOUND = 2;
    private static final int PRESS_PREV = 10;
    private static final int PRESS_NEXT = 12;
    private static final int PRESS_READY = 14;
    private static final int PRESS_LESS = 20;
    private static final int PRESS_MORE = 30;
    private static final int PRESS_CONTROLS = 40;
    private static final int PRESS_BACK = 41;

    /** The car select is shown again from the start: from the main menu, or to change cars after a race. */
    void openCarSelect() {
        selecting = false;
    }

    /**
     * One tick of the car select: each player steers to choose a car and
     * accelerates when ready (the brake or handbrake takes it back); the
     * mouse can do it all too. When both are ready, the stage select.
     */
    void carSelect(Control[] u, ContO[] models, int mouseX, int mouseY, int mouseState) {
        Graphics2D rd = ui.getTarget();
        int w = GameFacts.screenWidth;
        int h = GameFacts.screenHeight;
        if (!selecting) {
            selecting = true;
            bothReady = 0;
            pressed = -1;
            for (int p = 0; p < 2; p++) {
                car[p] = SplitRace.cars[p];
                if (!open(car[p])) {
                    car[p] = p;
                }
                ready[p] = false;
                remember(p, u[p]);
            }
            SettingsScreen.takeEscape();
            u[0].enter = false;
            ui.switchIntertrack("cars");
        }
        if (GameUi.intertrack != null) {
            GameUi.intertrack.play();
        }
        rd.drawImage(ui.carsBackdrop(), 0, 0, null);
        rd.drawImage(ui.selectcar, Utility.centeredImageX(ui.selectcar), 12, null);
        spin = (spin + 5) % 360;
        flicker++;

        for (int p = 0; p < 2; p++) {
            drive(p, u[p]);
            drawPlayer(rd, p, models, w, h);
        }
        drawOptions(rd, w, h);

        if (ready[0] && ready[1]) {
            bothReady++;
            ui.drawcs((int) (h * 0.9), "Get ready!", 255, 220, 120, 3);
            if (bothReady > 12) {
                begin();
                return;
            }
        } else {
            bothReady = 0;
            ui.drawcs(h - 22, "Steer to choose a car, accelerate when ready.  Esc goes back to the main menu.",
                    181, 120, 40, 3);
        }
        mouse(mouseX, mouseY, mouseState);

        if (SettingsScreen.takeEscape()) {
            u[0].enter = false;
            ui.clickNow();
            selecting = false;
            ui.fase = Phase.MAINMENU;
            return;
        }
        if (u[0].enter) {
            // Enter readies whoever is not yet ready, Player 1 first
            u[0].enter = false;
            setReady(ready[0] ? 1 : 0, true);
        }
    }

    /** Off to the stage select with both players' cars. */
    private void begin() {
        SplitRace.cars[0] = car[0];
        SplitRace.cars[1] = car[1];
        SplitRace.changed = true;
        ui.sc[0] = car[0];
        ui.sc[1] = car[1];
        selecting = false;
        ui.sm.play("tick");
        ui.switchIntertrack("stages");
        ui.fase = Phase.NPLAYERSCHECK;
    }

    /** A player's presses this tick: steer for another car, accelerate to be ready, brake or handbrake to choose again. */
    private void drive(int p, Control c) {
        boolean left = c.left && !was[p][0];
        boolean right = c.right && !was[p][1];
        boolean go = c.up && !was[p][2];
        boolean stop = c.down && !was[p][3] || c.handb && !was[p][4];
        remember(p, c);
        if (!ready[p] && (left || right)) {
            step(p, left ? -1 : 1);
        }
        if (go) {
            setReady(p, true);
        }
        if (stop) {
            setReady(p, false);
        }
    }

    private void remember(int p, Control c) {
        was[p][0] = c.left;
        was[p][1] = c.right;
        was[p][2] = c.up;
        was[p][3] = c.down;
        was[p][4] = c.handb;
    }

    private void setReady(int p, boolean now) {
        if (ready[p] != now) {
            ready[p] = now;
            ui.sm.play(now ? "powerup" : "tick");
        }
    }

    /** The next car (or the one before) that the player has unlocked. */
    private void step(int p, int by) {
        int c = car[p];
        for (int i = 0; i < GameFacts.numberOfCars; i++) {
            c = Math.floorMod(c + by, GameFacts.numberOfCars);
            if (open(c)) {
                break;
            }
        }
        if (c != car[p]) {
            car[p] = c;
            ui.sm.play("tick");
        }
    }

    /** A car that is not locked: the car select's rule, one car for every two stages completed. */
    private boolean open(int c) {
        return c >= 0 && c < GameFacts.numberOfCars && (c - 7) * 2 < ui.unlocked;
    }

    /** One player's half: who they are, their car turning, its name, the arrows and whether they are ready. */
    private void drawPlayer(Graphics2D rd, int p, ContO[] models, int w, int h) {
        int half = w / 2;
        int x0 = p * half;
        int cx = x0 + half / 2;
        // a dark pane for each player, so their car and its name stand clear of the picture behind
        int paneTop = (int) (h * 0.14);
        rd.setColor(new Color(0, 0, 0, 165));
        rd.fillRoundRect(x0 + 16, paneTop, half - 32, (int) (h * 0.755) - paneTop, 24, 24);
        rd.setColor(ready[p] ? new Color(120, 200, 40) : new Color(120, 70, 20));
        rd.drawRoundRect(x0 + 16, paneTop, half - 32, (int) (h * 0.755) - paneTop, 24, 24);
        SettingsScreen.drawMenuItem(rd, "Player " + (p + 1), cx, (int) (h * 0.2));

        ContO model = models[car[p]];
        model.z = car[p] == 13 ? 900 : 820;
        model.y = -34 - model.grat;
        model.x = 0;
        model.xz = spin;
        model.zy = 0;
        model.wzy -= 10;
        if (model.wzy < -45) {
            model.wzy += 45;
        }
        model.blackout = false;
        AffineTransform transform = rd.getTransform();
        Shape clip = rd.getClip();
        try {
            rd.translate(x0, 0);
            rd.clipRect(0, 0, half, h);
            Medium.crs = true;
            Medium.x = -GameFacts.GAME_WIDTH / 2;
            Medium.y = -(int) (GameFacts.GAME_HEIGHT * 0.83);
            Medium.z = 200;
            Medium.xz = 0;
            Medium.zy = 10;
            Medium.ground = 570;
            try (var _ = Medium.paneView(half, h)) {
                model.d(rd);
            }
        } finally {
            rd.setTransform(transform);
            rd.setClip(clip);
        }

        rd.setFont(new Font("SansSerif", Font.BOLD, 16));
        boolean bright = flicker % 2 == 0;
        centered(rd, ui.names[car[p]], cx, (int) (h * 0.27), Color.WHITE);

        Image back = ui.back[pressed == PRESS_PREV + p ? 1 : 0];
        Image next = ui.next[pressed == PRESS_NEXT + p ? 1 : 0];
        int arrowY = (int) (h * 0.5) - back.getHeight(null) / 2;
        if (!ready[p]) {
            prevCar[p].setBounds(x0 + 30, arrowY, back.getWidth(null), back.getHeight(null));
            nextCar[p].setBounds(x0 + half - 30 - next.getWidth(null), arrowY, next.getWidth(null), next.getHeight(null));
            rd.drawImage(back, prevCar[p].x, prevCar[p].y, null);
            rd.drawImage(next, nextCar[p].x, nextCar[p].y, null);
        } else {
            prevCar[p].setBounds(0, 0, 0, 0);
            nextCar[p].setBounds(0, 0, 0, 0);
        }

        // ready, or how to be
        int boxW = 240;
        int boxH = 26;
        readyBox[p].setBounds(cx - boxW / 2, (int) (h * 0.66), boxW, boxH);
        Rectangle r = readyBox[p];
        rd.setColor(ready[p] ? new Color(60, 140, 30) : new Color(140, 70, 0));
        rd.fillRoundRect(r.x, r.y, r.width, r.height, 7, 20);
        rd.setColor(ready[p] ? new Color(200, 255, 0) : bright ? new Color(255, 128, 0) : new Color(200, 128, 0));
        rd.drawRoundRect(r.x, r.y, r.width, r.height, 7, 20);
        SettingsScreen.drawMenuItem(rd, ready[p] ? "Ready!" : "Ready?", cx, r.y + 19);
        rd.setFont(new Font("SansSerif", Font.BOLD, 11));
        String keys = ready[p]
                ? SplitKeys.name(SplitKeys.key(p, KEY_BRAKE, 0)) + " to choose again"
                : SplitKeys.name(SplitKeys.key(p, KEY_LEFT, 0)) + " / " + SplitKeys.name(SplitKeys.key(p, KEY_RIGHT, 0))
                        + " to choose,  " + SplitKeys.name(SplitKeys.key(p, KEY_ACCELERATE, 0)) + " when ready";
        centered(rd, keys, cx, r.y + r.height + 18, new Color(181, 120, 40));
        centered(rd, "or a controller: steer, then accelerate", cx, r.y + r.height + 33, new Color(130, 90, 30));
    }

    /* the places in SplitKeys.ACTIONS of the keys the car select names */
    private static final int KEY_ACCELERATE = 0;
    private static final int KEY_BRAKE = 1;
    private static final int KEY_LEFT = 2;
    private static final int KEY_RIGHT = 3;

    /** The race's choices under the cars: computer cars, how the screen is shared, where the players are heard. */
    private void drawOptions(Graphics2D rd, int w, int h) {
        int rowH = 24;
        int top = (int) (h * 0.79);
        int labelX = w / 2 - 20;
        rd.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = rd.getFontMetrics();
        String[] labels = {"Computer cars", "Screen", "Sound"};
        String sound = Sound3D.secondSpeaker() == null && SplitRace.sound != SplitRace.SOUND_ONE
                ? "One speaker: no second one is chosen" : soundText(SplitRace.sound);
        String[] values = {rivalsText(SplitRace.rivals), screenText(SplitRace.view), sound};
        for (int i = 0; i < 3; i++) {
            int y = top + i * rowH;
            rd.setColor(new Color(181, 120, 40));
            rd.drawString(labels[i], labelX - fm.stringWidth(labels[i]), y + 13);
            int vx = w / 2;
            int vw = 300;
            optionLess[i].setBounds(vx, y, 18, 18);
            optionMore[i].setBounds(vx + vw - 18, y, 18, 18);
            rd.setColor(new Color(0, 0, 0, 90));
            rd.fillRoundRect(vx, y, vw, 18, 7, 12);
            rd.setColor(new Color(255, 128, 0));
            rd.drawString("<", vx + 6, y + 13);
            rd.drawString(">", vx + vw - 12, y + 13);
            rd.setColor(new Color(240, 240, 240));
            rd.drawString(values[i], vx + (vw - fm.stringWidth(values[i])) / 2, y + 13);
        }
        String c = "Keys and controllers...";
        int cw = fm.stringWidth(c) + 24;
        controls.setBounds(w / 2 - cw / 2, top + 3 * rowH + 2, cw, 20);
        rd.setColor(pressed == PRESS_CONTROLS ? new Color(140, 70, 0) : new Color(0, 0, 0, 90));
        rd.fillRoundRect(controls.x, controls.y, controls.width, controls.height, 7, 12);
        rd.setColor(new Color(255, 128, 0));
        rd.drawRoundRect(controls.x, controls.y, controls.width, controls.height, 7, 12);
        rd.setColor(new Color(240, 240, 240));
        rd.drawString(c, controls.x + 12, controls.y + 14);
        backBox.setBounds(20, h - 46, 90, 26);
        Image back = ui.back[pressed == PRESS_BACK ? 1 : 0];
        backBox.setSize(back.getWidth(null), back.getHeight(null));
        rd.drawImage(back, backBox.x, backBox.y, null);
    }

    static String rivalsText(int rivals) {
        return rivals == 0 ? "None: just the two of you" : String.valueOf(rivals);
    }

    static String screenText(int view) {
        return switch (view) {
            case SplitRace.VIEW_SIDE -> "Side by side";
            case SplitRace.VIEW_STACKED -> "Top and bottom";
            default -> "Auto: as suits the window";
        };
    }

    static String soundText(int sound) {
        return switch (sound) {
            case SplitRace.SOUND_EVENTS -> "Race events on the second speaker";
            case SplitRace.SOUND_EACH -> "A speaker each";
            default -> "One speaker";
        };
    }

    /** A controller choice in words: Auto, None, or the n-th connected controller and its name. */
    static String padName(int pad) {
        if (pad == SplitRace.PAD_AUTO) {
            return "Auto";
        }
        if (pad == SplitRace.PAD_NONE) {
            return "None: keys only";
        }
        java.util.List<String> names = Gamepads.connected();
        return "Controller " + (pad + 1) + (pad < names.size() ? ": " + names.get(pad) : " (not connected)");
    }

    /** The controller a player has now, in words. */
    static String padText(int player) {
        java.util.List<String> names = Gamepads.connected();
        for (int i = 0; i < names.size(); i++) {
            if (SplitRace.padOwner(i, names.size()) == player) {
                return names.get(i);
            }
        }
        return "no controller, the keys only";
    }

    /** Changes a choice by one either way, round. */
    static void changeOption(int option, int by) {
        switch (option) {
            case OPTION_RIVALS -> SplitRace.rivals = Math.floorMod(SplitRace.rivals + by, SplitRace.MAX_RIVALS + 1);
            case OPTION_SCREEN -> SplitRace.view = Math.floorMod(SplitRace.view + by, 3);
            case OPTION_SOUND -> SplitRace.sound = Math.floorMod(SplitRace.sound + by, 3);
            default -> throw new IllegalArgumentException("no option " + option);
        }
        SplitRace.changed = true;
    }

    private void mouse(int x, int y, int state) {
        if (state == 1) {
            pressed = hit(x, y);
        } else if (state == 2 && pressed >= 0) {
            int was = pressed;
            pressed = -1;
            if (hit(x, y) == was) {
                click(was);
            }
        }
    }

    /** What is under the mouse: a PRESS_ value, or -1. */
    private int hit(int x, int y) {
        for (int p = 0; p < 2; p++) {
            if (prevCar[p].contains(x, y)) {
                return PRESS_PREV + p;
            }
            if (nextCar[p].contains(x, y)) {
                return PRESS_NEXT + p;
            }
            if (readyBox[p].contains(x, y)) {
                return PRESS_READY + p;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (optionLess[i].contains(x, y)) {
                return PRESS_LESS + i;
            }
            if (optionMore[i].contains(x, y)) {
                return PRESS_MORE + i;
            }
        }
        if (controls.contains(x, y)) {
            return PRESS_CONTROLS;
        }
        return backBox.contains(x, y) ? PRESS_BACK : -1;
    }

    private void click(int what) {
        if (what == PRESS_PREV || what == PRESS_PREV + 1) {
            step(what - PRESS_PREV, -1);
        } else if (what == PRESS_NEXT || what == PRESS_NEXT + 1) {
            step(what - PRESS_NEXT, 1);
        } else if (what == PRESS_READY || what == PRESS_READY + 1) {
            setReady(what - PRESS_READY, !ready[what - PRESS_READY]);
        } else if (what >= PRESS_LESS && what < PRESS_LESS + 3) {
            ui.clickNow();
            changeOption(what - PRESS_LESS, -1);
        } else if (what >= PRESS_MORE && what < PRESS_MORE + 3) {
            ui.clickNow();
            changeOption(what - PRESS_MORE, 1);
        } else if (what == PRESS_CONTROLS) {
            ui.clickNow();
            ui.openSettings(Phase.SPLITCARSELECT, SettingsScreen.TAB_SPLIT);
        } else if (what == PRESS_BACK) {
            ui.clickNow();
            selecting = false;
            ui.fase = Phase.MAINMENU;
        }
    }

    /* =========================================================== the results */

    private static final String[] RESULT_ITEMS = {"Race Again", "Choose Stage", "Change Cars", "Main Menu"};
    private static final int RESULT_AGAIN = 0;
    private static final int RESULT_STAGE = 1;
    private static final int RESULT_CARS = 2;
    private static final int RESULT_W = 640;
    private static final int ITEM_W = 200;
    private static final int ITEM_H = 24;
    private static final int ITEM_GAP = 31;

    private String heading = "";
    private String[] standing = new String[0];
    private boolean[] standingWasted = new boolean[0];
    private int[] standingCar = new int[0];
    private int choice;
    private boolean shaded;
    /* where the mouse was when it last chose by hovering, as the pause menu keeps it: resting, it leaves the keys' choice be */
    private int hoverX;
    private int hoverY;
    private final Rectangle[] itemBox = {new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle()};

    /**
     * The race just ended: who won, and every car in the order they stood,
     * the wrecked ones marked, kept for the results screen.
     */
    void beginResults(SplitRace split, CheckPoints checkpoints, Madness[] drivers) {
        int cars = GameFacts.numberOfPlayers;
        int won = split.winner;
        heading = won < 0 ? "Both players wasted: nobody wins"
                : won < 2 ? "Player " + (won + 1) + " wins!"
                : ui.names[ui.sc[won]] + " wins the race";
        standing = new String[cars];
        standingWasted = new boolean[cars];
        standingCar = new int[cars];
        Integer[] order = new Integer[cars];
        for (int i = 0; i < cars; i++) {
            order[i] = i;
        }
        // the winner first, then by place; the wrecked after those still racing
        java.util.Arrays.sort(order, (a, b) -> {
            if (a == won || b == won) {
                return a == won ? (b == won ? 0 : -1) : 1;
            }
            if (drivers[a].dest != drivers[b].dest) {
                return drivers[a].dest ? 1 : -1;
            }
            return Integer.compare(checkpoints.pos[a], checkpoints.pos[b]);
        });
        for (int i = 0; i < cars; i++) {
            int c = order[i];
            standingCar[i] = c;
            standingWasted[i] = drivers[c].dest;
            standing[i] = c < 2 ? "Player " + (c + 1) + "  (" + ui.names[ui.sc[c]] + ")" : ui.names[ui.sc[c]];
        }
        choice = RESULT_AGAIN;
        shaded = false;
        hoverX = -10; // the first tick takes the mouse where it is, as the pause menu does
        hoverY = -10;
        SettingsScreen.takeEscape(); // one pressed in the race is not for here
    }

    /** One tick of the results: the standings over the race, and what next, by keys or the mouse. */
    void results(Control control, int mouseX, int mouseY, int mouseState) {
        Graphics2D rd = ui.getTarget();
        int w = GameFacts.screenWidth;
        int h = GameFacts.screenHeight;
        ui.drawPauseBackdrop();
        int rows = standing.length;
        int boxH = 96 + rows * 22 + RESULT_ITEMS.length * ITEM_GAP + 20;
        int x = (w - RESULT_W) / 2;
        int y = Math.max(10, (h - boxH) / 2);
        rd.setColor(new Color(0, 0, 0, 170));
        rd.fillRoundRect(x, y, RESULT_W, boxH, 20, 20);
        rd.setColor(new Color(255, 128, 0));
        rd.drawRoundRect(x, y, RESULT_W, boxH, 20, 20);
        SettingsScreen.drawMenuItem(rd, heading, w / 2F, y + 40);

        rd.setFont(new Font("SansSerif", Font.BOLD, 13));
        FontMetrics fm = rd.getFontMetrics();
        int rowY = y + 74;
        for (int i = 0; i < rows; i++) {
            boolean human = standingCar[i] < 2;
            rd.setColor(human ? new Color(255, 200, 80) : new Color(220, 220, 220));
            String place = GameUi.ordinal(i + 1);
            rd.drawString(place, x + 60, rowY + i * 22);
            rd.drawString(standing[i], x + 110, rowY + i * 22);
            if (standingWasted[i]) {
                String wasted = "Wasted";
                rd.setColor(new Color(230, 70, 50));
                rd.drawString(wasted, x + RESULT_W - 60 - fm.stringWidth(wasted), rowY + i * 22);
            }
        }

        if (control.up) {
            choice = Math.floorMod(choice - 1, RESULT_ITEMS.length);
            control.up = false;
            ui.clickNow();
        }
        if (control.down) {
            choice = Math.floorMod(choice + 1, RESULT_ITEMS.length);
            control.down = false;
            ui.clickNow();
        }
        int itemsTop = rowY + rows * 22 + 6;
        flicker++;
        for (int i = 0; i < RESULT_ITEMS.length; i++) {
            Rectangle r = itemBox[i];
            r.setBounds(w / 2 - ITEM_W / 2, itemsTop + i * ITEM_GAP, ITEM_W, ITEM_H);
            if (i == choice) {
                if (shaded) {
                    rd.setColor(new Color(140, 70, 0));
                    rd.fillRect(r.x, r.y, r.width, r.height);
                }
                rd.setColor(flicker % 2 == 0 ? new Color(255, 128, 0) : new Color(200, 255, 0));
            } else {
                rd.setColor(new Color(90, 90, 90));
            }
            rd.drawRoundRect(r.x, r.y, r.width, r.height, 7, 20);
            SettingsScreen.drawMenuItem(rd, RESULT_ITEMS[i], w / 2F, r.y + 18);
        }

        int over = -1;
        for (int i = 0; i < RESULT_ITEMS.length; i++) {
            if (itemBox[i].contains(mouseX, mouseY)) {
                over = i;
            }
        }
        if (mouseState == 1 && over >= 0) {
            choice = over;
            shaded = true;
        }
        if (mouseState == 0 && (mouseX != hoverX || mouseY != hoverY)) {
            if (over >= 0) {
                choice = over;
            }
            hoverX = mouseX;
            hoverY = mouseY;
        }
        if (SettingsScreen.takeEscape()) {
            // Escape is Enter to the game: here it only goes to Main Menu, which Enter then takes
            control.enter = false;
            choice = RESULT_ITEMS.length - 1;
            ui.clickNow();
        }
        boolean chosen = control.enter || control.handb || mouseState == 2 && shaded;
        if (mouseState == 2) {
            shaded = false;
        }
        if (chosen) {
            control.enter = false;
            control.handb = false;
            ui.clickNow();
            choose(choice);
        }
    }

    private void choose(int item) {
        switch (item) {
            case RESULT_AGAIN -> {
                ui.raceAgain = true; // the stage select starts the same stage at once
                ui.switchIntertrack("stages");
                ui.fase = Phase.NPLAYERSCHECK;
            }
            case RESULT_STAGE -> {
                ui.switchIntertrack("stages");
                ui.fase = Phase.NPLAYERSCHECK;
            }
            case RESULT_CARS -> {
                openCarSelect();
                ui.fase = Phase.SPLITCARSELECT;
            }
            default -> ui.fase = Phase.MAINMENU;
        }
    }

    /* ================================================================ text */

    private static void centered(Graphics2D rd, String text, int cx, int y, Color color) {
        FontMetrics fm = rd.getFontMetrics();
        rd.setColor(color);
        rd.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }
}
