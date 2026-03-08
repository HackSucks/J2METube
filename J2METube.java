import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Vector;

/**
 * J2METube for Nokia Asha / S40
 * Features PC RTSP Proxy integration & Startup Config
 */
public class J2METube extends MIDlet implements CommandListener {
    private Display display;
    
    // UI Components
    private Form configForm;
    private Form searchForm;
    private List resultsList;
    private TextField ipField;
    private TextField searchField;
    
    // Commands
    private Command nextCmd, searchCmd, exitCmd, backCmd, playCmd;
    
    // Data
    private Vector videoIds = new Vector();
    private String serverAddress = "";

    public J2METube() {
        display = Display.getDisplay(this);
        initUI();
    }

    private void initUI() {
        // Shared Exit Command
        exitCmd = new Command("Exit", Command.EXIT, 2);
        backCmd = new Command("Back", Command.BACK, 1);

        // 1. Config Screen (App Start)
        configForm = new Form("Server Config");
        // Default placeholder so you only have to change the last digits
        ipField = new TextField("PC IP & Port:", "192.168.1.X:5000", 100, TextField.ANY);
        nextCmd = new Command("Next", Command.OK, 1);
        
        configForm.append(ipField);
        configForm.addCommand(nextCmd);
        configForm.addCommand(exitCmd);
        configForm.setCommandListener(this);

        // 2. Search Screen
        searchForm = new Form("J2METube Search");
        searchField = new TextField("Query:", "dankpods", 50, TextField.ANY);
        searchCmd = new Command("Search", Command.OK, 1);
        
        searchForm.append(searchField);
        searchForm.addCommand(searchCmd);
        searchForm.addCommand(backCmd); // Goes back to Config
        searchForm.addCommand(exitCmd);
        searchForm.setCommandListener(this);
        
        // 3. Results Screen
        resultsList = new List("Results", List.IMPLICIT);
        playCmd = new Command("Play", Command.ITEM, 1);
        
        resultsList.addCommand(backCmd); // Goes back to Search
        resultsList.setCommandListener(this);
    }

    public void startApp() {
        // Always start at the Config screen
        display.setCurrent(configForm);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCmd) {
            notifyDestroyed();
        } 
        // Flow: Config -> Search
        else if (c == nextCmd && d == configForm) {
            serverAddress = ipField.getString();
            display.setCurrent(searchForm);
        } 
        // Flow: Search -> Back to Config
        else if (c == backCmd && d == searchForm) {
            display.setCurrent(configForm);
        }
        // Flow: Search -> Run Query
        else if (c == searchCmd && d == searchForm) {
            performSearch(searchField.getString());
        } 
        // Flow: Results -> Back to Search
        else if (c == backCmd && d == resultsList) {
            display.setCurrent(searchForm);
        } 
        // Flow: Results -> Play Video (RTSP Proxy)
        else if (d == resultsList && c == List.SELECT_COMMAND) {
            int index = resultsList.getSelectedIndex();
            if (index != -1) {
                String id = (String) videoIds.elementAt(index);
                playVideoViaProxy(id);
            }
        }
    }

    private void performSearch(final String query) {
        resultsList.deleteAll();
        videoIds.removeAllElements();
        resultsList.setTitle("Searching...");
        display.setCurrent(resultsList);

        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null;
                InputStream is = null;
                try {
                    String url = "http://s60tube.io.vn/api/v1/search?q=" + urlEncode(query);
                    hc = (HttpConnection) Connector.open(url);
                    is = hc.openInputStream();
                    
                    StringBuffer sb = new StringBuffer();
                    int ch;
                    while ((ch = is.read()) != -1) {
                        sb.append((char) ch);
                    }
                    
                    parseAndPopulate(sb.toString());
                } catch (Exception e) {
                    errorMsg("Net Error: " + e.getMessage());
                } finally {
                    try { if(is != null) is.close(); if(hc != null) hc.close(); } catch(Exception ex) {}
                }
            }
        }).start();
    }

    private void parseAndPopulate(String json) {
        int pos = 0;
        int count = 0;
        while ((pos = json.indexOf("\"type\":\"video\"", pos)) != -1) {
            try {
                int titleStart = json.indexOf("\"title\":\"", pos) + 9;
                int titleEnd = json.indexOf("\"", titleStart);
                String title = json.substring(titleStart, titleEnd);

                int idStart = json.indexOf("\"videoId\":\"", pos) + 11;
                int idEnd = json.indexOf("\"", idStart);
                String id = json.substring(idStart, idEnd);

                resultsList.append(title, null);
                videoIds.addElement(id);
                
                pos = idEnd;
                count++;
            } catch (Exception e) {
                pos += 15; 
            }
        }
        
        if (count == 0) {
            resultsList.append("No results found.", null);
        }
        resultsList.setTitle("Results: " + count);
    }

    private void playVideoViaProxy(final String vid) {
        // UI Feedback while PC transcodes
        Alert loadingAlert = new Alert("Connecting", "Waking up PC server...", null, AlertType.INFO);
        loadingAlert.setTimeout(3000);
        display.setCurrent(loadingAlert, resultsList);

        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null;
                InputStream is = null;
                try {
                    // Call the Flask server on the PC
                    String proxyUrl = "http://" + serverAddress + "/play?v=" + vid;
                    hc = (HttpConnection) Connector.open(proxyUrl);
                    is = hc.openInputStream();
                    
                    StringBuffer sb = new StringBuffer();
                    int ch;
                    while ((ch = is.read()) != -1) {
                        sb.append((char) ch);
                    }
                    
                    // The PC returns the RTSP link (e.g., rtsp://192.168.1.5:8554/nokia)
                    String rtspUrl = sb.toString().trim(); 
                    
                    // Hand the true RTSP stream to the Nokia Media Player
                    platformRequest(rtspUrl);
                    
                } catch (Exception e) {
                    errorMsg("Proxy Error: " + e.getMessage());
                } finally {
                    try { if(is != null) is.close(); if(hc != null) hc.close(); } catch(Exception ex) {}
                }
            }
        }).start();
    }

    private String urlEncode(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') sb.append('+');
            else sb.append(c);
        }
        return sb.toString();
    }

    private void errorMsg(String msg) {
        Alert a = new Alert("J2METube Error", msg, null, AlertType.ERROR);
        a.setTimeout(Alert.FOREVER);
        display.setCurrent(a, searchForm);
    }

    public void pauseApp() {}
    public void destroyApp(boolean unconditional) {}
}