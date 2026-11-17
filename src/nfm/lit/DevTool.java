package nfm.lit;
import javax.swing.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Developer console for Need For Madness
 *
 * @author oteek
 */
public class DevTool {
    private JFrame frame;
    private JTextArea textArea;
    private JTextField inputField;
    private List<String> commandHistory;
    private int historyIndex;

    public boolean godmode = false;
    public boolean debugstat = false;
    public int oldclrad;

    private Map<String, String> commandDescriptions; // for help command

    public DevTool(CheckPoints checkpoints, Madness madness[], ContO conto[], ContO conto1[], xtGraphics xt) {
        commandHistory = new ArrayList<>();
        historyIndex = -1;

        commandDescriptions = new HashMap<>();
        populateCommandDescriptions();

        frame = new JFrame("DevTool");
        textArea = new JTextArea();
        inputField = new JTextField();

        textArea.setEditable(false);
        textArea.setLineWrap(true);

        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String command = inputField.getText();
                executeCommand(command, checkpoints, madness, conto, conto1, xt);
                if (!command.trim().isEmpty()) {
                    commandHistory.add(command);
                    historyIndex = commandHistory.size();
                }
                inputField.setText("");
            }
        });

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (historyIndex > 0) {
                        historyIndex--;
                        inputField.setText(commandHistory.get(historyIndex));
                    } else if (historyIndex == 0) {
                        inputField.setText(commandHistory.get(historyIndex));
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (historyIndex < commandHistory.size() - 1) {
                        historyIndex++;
                        inputField.setText(commandHistory.get(historyIndex));
                    } else if (historyIndex == commandHistory.size() - 1) {
                        inputField.setText("");
                    }
                }
            }
        });

        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);
        frame.add(inputField, BorderLayout.SOUTH);

        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    }

    public void showConsole() {
        frame.setVisible(true);
    }

    public void print(String s) {
        textArea.append(s + "\n");
    }

    private void executeCommand(String command, CheckPoints checkpoints, Madness madness[], ContO conto[], ContO conto1[], xtGraphics xt) {
        textArea.append("> " + command + "\n");

        String[] parts = command.split(" ");
        String commandName = parts[0];
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        switch (commandName) {
            case "nplayers":
                if (args.length == 1) {
                    try {
                        int nplayers = Integer.parseInt(args[0]);
                        if (GameSparker.gameStateID > 1) {
                            if ((nplayers >= 1 && nplayers <= 51)) {
                                //GameFacts.numberOfPlayers = nplayers;
                                xt.nplayers_debug = true;
                                xt.nplayers_override = nplayers;
                                print("Numbers of players set to " + nplayers + ", overriden for all stages.");
                            } else if (nplayers == 0) {
                                xt.nplayers_debug = false;
                                print("Number of players are now determined by stage\n(xtGraphics, public void carspergame)");
                            } else {
                                print("Invalid player number.");
                            }
                        } else {
                            print("This command only works in menus and before stage select.");
                        }
                    } catch (NumberFormatException e) {
                        print("Invalid number of players.\n");
                    }
                } else {
                    print("nplayers is " + GameFacts.numberOfPlayers + "\nUsage: nplayers <0-51>");
                }
                break;
            case "fix":
                if (args.length == 1) {
                    try {
                        int n = Integer.parseInt(args[0]);
                        if (GameSparker.gameStateID == 0) {
                            madness[n].devFixCar();
                            print("Car " + n + " fixed");
                        } else {
                            print("This command only works in game.");
                        }
                    } catch (NumberFormatException e) {
                        print("Invalid argument.");
                    }
                } else {
                    print("Usage: fix <n>");
                }
                break;
            case "god":
                if (GameSparker.gameStateID == 0) {
                    if (!godmode) {
                        oldclrad = madness[0].stat.clrad;
                        madness[0].stat.clrad = 0;
                        print("Godmode ON");
                        godmode = true;
                    } else {
                        madness[0].stat.clrad = oldclrad;
                        print("Godmode OFF");
                        godmode = false;
                    }
                } else {
                    print("This command only works in game.");
                }
                break;
            case "spectate":
                if (args.length == 1) {
                    try {
                        int n = Integer.parseInt(args[0]);
                        if (GameSparker.gameStateID == 0) {
                            if (n > 0 && n < GameFacts.numberOfPlayers) {
                                xt.spectate = n;
                                print("Spectating [AI]" + xt.names[xt.sc[n]]);
                            } else if (n == 0) {
                                xt.spectate = n;
                                print("Spectating [Player]" + xt.names[xt.sc[n]]);
                            } else {
                                print("Invalid player ID.");
                            }
                        } else {
                            print("This command only works in game.");
                        }
                    } catch (NumberFormatException e) {
                        print("Invalid argument.");
                    }
                } else {
                    print("Usage: spectate <n>");
                }
                break;
            case "debug":
                if (!debugstat) {
                    xt.debugmode = true;
                    print("Debug mode enabled.");
                    debugstat = true;
                } else {
                    xt.debugmode = false;
                    print("Debug mode disabled.");
                    debugstat = false;
                }
                break;
            case "unlocked":
                if (args.length == 1) {
                    try {
                        int n = Integer.parseInt(args[0]);
                        xt.unlocked = n;
                        print("xtGraphics.unlocked set to " + n);
                    } catch (NumberFormatException e) {
                        print("Invalid argument.");
                    }
                } else {
                    print("xtGraphics.unlocked is " + xt.unlocked + "\nUsage: unlocked <n>");
                }
                break;
            case "fase":
                if (args.length == 1) {
                    try {
                        int n = Integer.parseInt(args[0]);
                        xt.fase = Phase.valueOfValue(n);
                        print("Set xtGraphics.fase to " + n);
                    } catch (NumberFormatException e) {
                        print("Invalid argument.");
                    }
                } else {
                    print("fase: " + xt.fase.toString());
                }
                break;
            case "spawn_ai":
                if (GameSparker.gameStateID == 0) {

                    int n = GameFacts.numberOfPlayers + 1;

                    conto[n] = new ContO(conto1[xt.sc[n]], 0, 250 - conto1[xt.sc[n]].grat, -760 + ((n / 3) * 760), 0);
                    madness[n].reseto(xt.sc[n], conto[n], checkpoints);
                    print("Spawned " + xt.names[xt.sc[n]]);
                } else {
                    print("This command only works in game.");
                }
                break;
            case "nfm":
                if (args.length == 1) {
                    try {
                        int n = Integer.parseInt(args[0]);
                        if (GameSparker.gameStateID == 10) {
                            if ((n >= 1 && n <= 3)) {
                                xt.nfmmode = n;
                                if (n < 2) {
                                    print("Need For Madness " + n);
                                } else {
                                    print("Freeplay mode");
                                }
                            } else {
                                print("Prevented from creating a paradox.");
                            }
                        } else {
                            print("This command only works in main menu.");
                        }
                    } catch (NumberFormatException e) {
                        print("Invalid argument.");
                    }
                } else {
                    print("Usage: nfm <n>");
                }
                break;
            case "map":
                if (args.length == 1) {
                    try {
                        String path = args[0];
                        xt.nfmmode = 3;
                        print("Loading stage from: " + path + ".txt");
                        GameSparker.loadStageCus = path;    //idk
                        checkpoints.stage = -1;
                        if (GameSparker.gameStateID == 0) {
                            xt.fase = Phase.LOADSTAGE2;
                        }
                        if (GameSparker.gameStateID == 1 || GameSparker.gameStateID == 3) {
                            xt.fase = Phase.LOADSTAGE;
                        }
                    } catch (NumberFormatException e) {
                        print("Invalid argument.");
                    }
                } else {
                    print("Usage: map <dir to .txt>");
                }
                break;
            case "stagesubdir":
                if (args.length == 1) {
                    try {
                        String sub = args[0];
                            GameSparker.stageSubDir = sub + "/";    //idk
                            xt.nfmmode = 3;
                            print("Set stage subdir to " + sub);
                    } catch (NumberFormatException e) {
                        print("Invalid argument.");
                    }
                } else {
                    print("Usage: stagesubdir <subdir>");
                }
                break;
            case "status":
                print("Game State: " + GameSparker.gameState);
                break;
            case "connect":
                if (args.length == 1) {
                    String sub = args[0];
                    try {
                        // Allow localhost, domains, and IPs
                        String regex = "^([a-zA-Z0-9.-]+):(\\d{1,5})$";
                        Pattern pattern = Pattern.compile(regex);
                        Matcher matcher = pattern.matcher(sub);
            
                        if (matcher.matches()) {
                            String host = matcher.group(1);
                            int port = Integer.parseInt(matcher.group(2));
            
                            if (port >= 0 && port <= 65535) {
                                print("Connecting to " + host + " on port " + port + "...");
            
                                try {
                                    xt.socket = new Socket(host, port);
                                    print("Connected to the server");
            
                                    xt.serverResponse = new BufferedReader(new InputStreamReader(xt.socket.getInputStream()));
                                    String message = xt.serverResponse.readLine();
                                    print("Server says: " + message);
            
                                } catch (java.net.ConnectException e) {
                                    print("Connection refused: " + e.getMessage());
                                } catch (IOException e) {
                                    print("An error occurred:\n" + e.toString());
                                } finally {
                                    try {
                                        if (xt.serverResponse != null) xt.serverResponse.close();
                                        if (xt.socket != null) xt.socket.close();
                                    } catch (IOException e) {
                                        print("An error occurred while closing connection:\n" + e.toString());
                                    }
                                }
            
                            } else {
                                print("Port must be between 0 and 65535.");
                            }
                        } else {
                            print("Invalid host:port format.");
                        }
                    } catch (NumberFormatException e) {
                        print("Invalid port number.");
                    }
                } else {
                    print("Usage: connect <host:port>");
                }
                break;
            case "fps":
                if (args.length == 2 && args[0].equalsIgnoreCase("battery")) {
                    Integer cap = GameSparker.parseFpsLimit(args[1]);
                    if (cap != null && cap != GameSparker.FPS_MATCH_DISPLAY && GameSparker.setBatteryFps(cap)) {
                        print("On battery frames are held to " + GameSparker.describeBatteryFps() + " (saved).");
                    } else {
                        print("Usage: fps battery <off|" + GameSparker.FPS_MIN + "-" + GameSparker.FPS_MAX + ">");
                    }
                } else if (args.length == 1) {
                    Integer limit = GameSparker.parseFpsLimit(args[0]);
                    if (limit != null && GameSparker.setFpsLimit(limit)) {
                        print("Frame rate limit set to " + GameSparker.describeFpsLimit() + " (saved).");
                    } else {
                        print("Usage: fps <auto|unlimited|" + GameSparker.FPS_MIN + "-" + GameSparker.FPS_MAX + ">");
                    }
                } else {
                    print("Frame rate limit is " + GameSparker.describeFpsLimit() + "; on battery "
                            + GameSparker.describeBatteryFps() + ".\nUsage: fps <auto|unlimited|" + GameSparker.FPS_MIN
                            + "-" + GameSparker.FPS_MAX + ">, fps battery <off|" + GameSparker.FPS_MIN + "-"
                            + GameSparker.FPS_MAX + ">");
                }
                break;
            case "scale":
                if (args.length == 1 && (args[0].equalsIgnoreCase("fit") || args[0].equalsIgnoreCase("off"))) {
                    GameSparker.setScaleToWindow(args[0].equalsIgnoreCase("fit"));
                    print("Window scaling " + (GameSparker.isScaleToWindow() ? "fit" : "off") + " (saved).");
                } else {
                    print("Window scaling is " + (GameSparker.isScaleToWindow() ? "fit" : "off")
                            + ".\nUsage: scale <fit|off>");
                }
                break;
            case "runahead":
                if (args.length == 1 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
                    GameSparker.setRunAhead(args[0].equalsIgnoreCase("on"));
                    print("Run-ahead " + (GameSparker.isRunAhead() ? "on" : "off") + " (saved).");
                } else {
                    print("Run-ahead is " + (GameSparker.isRunAhead() ? "on" : "off") + ".\nUsage: runahead <on|off>");
                }
                break;
            case "renderer":
                if (args.length == 1 && (args[0].equalsIgnoreCase("gpu") || args[0].equalsIgnoreCase("software"))) {
                    GameSparker.setGpuRenderer(args[0].equalsIgnoreCase("gpu"));
                    print("Renderer set to " + (GameSparker.isGpuRenderer() ? "gpu" : "software") + " (saved).");
                } else {
                    print("Renderer is " + GameSparker.describeRenderer() + ".\nUsage: renderer <gpu|software>");
                }
                break;
            case "msaa": {
                int samples = args.length == 1 ? GameSparker.parseMultisample(args[0]) : 0;
                if (samples != 0) {
                    GameSparker.setMultisample(samples);
                    print("Smooth edges " + GameSparker.describeMultisample() + " (saved).");
                } else {
                    print("Smooth edges are " + GameSparker.describeMultisample() + ".\nUsage: msaa <off|2|4|8>");
                }
                break;
            }
            case "hdr": {
                int mode = args.length == 1 ? GameSparker.parseHdr(args[0]) : -1;
                if (mode == nfm.lit.gpu.GpuDisplay.HDR_OFF || mode == nfm.lit.gpu.GpuDisplay.HDR_AUTO) {
                    GameSparker.setHdr(mode);
                    print("HDR " + GameSparker.describeHdr() + " (saved).");
                } else {
                    print("HDR is " + GameSparker.describeHdr() + ".\nUsage: hdr <auto|off>");
                }
                break;
            }
            case "gamepad":
                if (args.length == 1 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
                    GameSparker.setGamepad(args[0].equalsIgnoreCase("on"));
                    print(GameSparker.describeGamepads() + " (saved)");
                } else if (args.length == 2 && args[0].equalsIgnoreCase("rumble")
                        && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
                    GameSparker.setRumble(args[1].equalsIgnoreCase("on"));
                    print(GameSparker.describeGamepads() + " (saved)");
                } else {
                    print(GameSparker.describeGamepads() + "\nUsage: gamepad <on|off>, gamepad rumble <on|off>");
                }
                break;
            case "clear":
                textArea.setText("");
                break;
            case "help":
                if (args.length == 0) {
                    print("Available commands:");
                    for (String cmd : commandDescriptions.keySet()) {
                        print(cmd);
                    }
                } else if (args.length == 1) {
                    String helpCommand = args[0];
                    if (commandDescriptions.containsKey(helpCommand)) {
                        print(helpCommand + ": " + commandDescriptions.get(helpCommand));
                    } else {
                        print("No help available for unknown command: " + helpCommand);
                    }
                } else {
                    print("Usage: help <command>");
                }
                break;
            default:
                print("Unknown command: " + commandName);
                break;
        }
    }
    
    private void populateCommandDescriptions() {
        commandDescriptions.put("nplayers", "Sets the number of players. Usage: nplayers <1-51>");
        commandDescriptions.put("fix", "Fixes a specified car. Usage: fix <n>");
        commandDescriptions.put("spectate", "Spectates a specified car. Usage: spectate <n>");
        commandDescriptions.put("god", "Toggles god mode.");
        commandDescriptions.put("unlocked", "Sets the unlocked value in xtGraphics. Usage: unlocked <n>");
        commandDescriptions.put("fase", "Sets the game state (phase). Usage: fase <n>");
        commandDescriptions.put("nfm", "Sets the NFM mode. Usage: nfm <n>");
        commandDescriptions.put("map", "Loads a stage from the specified path. Usage: map <path-to-.txt>");
        commandDescriptions.put("stagesubdir", "Sets the stage subdirectory. Usage: stagesubdir <subdir>");
        commandDescriptions.put("status", "Displays the current game state.");
        commandDescriptions.put("scale", "fit scales the picture to fill the window, keeping its shape; off shows it pixel for pixel. Usage: scale <fit|off>");
        commandDescriptions.put("hdr", "HDR output on a monitor that has HDR turned on in Windows: the game looks as it does in SDR, at the brightness Windows shows SDR white at, and flames, lamps, sparks, stars and lightning shine brighter than white. Needs the gpu renderer. Usage: hdr <auto|off>");
        commandDescriptions.put("gamepad", "Game controllers (Xbox, PlayStation, Switch and others): RT or A accelerates, LT or X brakes, the left stick or d-pad steers, B is the handbrake, LB/RB look back, Y changes the view, Start pauses; in menus the stick moves and A selects. Hard hits shake the pad. Usage: gamepad <on|off>, gamepad rumble <on|off>");
        commandDescriptions.put("msaa", "Smooths the edges of the polygons in races drawn on the graphics card by taking 2, 4 or 8 samples per pixel (multisampling); off keeps the game's own hard edges. Usage: msaa <off|2|4|8>");
        commandDescriptions.put("renderer", "gpu puts frames on screen through the graphics card (Direct3D 11), which scales them for free; software draws them with Java2D. Usage: renderer <gpu|software>");
        commandDescriptions.put("runahead", "Shows race frames one tick ahead with the keys held now, for 53 ms less input lag. Usage: runahead <on|off>");
        commandDescriptions.put("fps", "Sets the frame rate limit. auto matches the monitor, unlimited removes the cap. On battery frames are held to 60 a second unless fps battery says otherwise. Usage: fps <auto|unlimited|30-1000>, fps battery <off|30-1000>");
        commandDescriptions.put("clear", "Clears the console.");
        commandDescriptions.put("connect", "Connects to a test server. Usage: connect <ip:port>");
        commandDescriptions.put("help", "Displays help information. Usage: help <command>");
    }
}
