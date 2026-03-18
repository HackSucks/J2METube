import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Vector;
public class J2METube extends MIDlet implements CommandListener {

    private static final String API_BASE    = "http://s60tube.io.vn/api/v1";
    private static final int    HISTORY_MAX = 5;

    
    private String appName    = "J2METube";
    private String appVersion = "1.0";
    private String appVendor  = "";

    private Display display;

    
    private Form   splashForm;
    private Form   configForm;
    private Form   searchForm;
    private Form   detailForm;
    private Form   aboutForm;
    private List   resultsList;
    private List   historyList;

    
    private TextField ipField;
    private TextField searchField;

    
    private Command nextCmd, searchCmd, exitCmd, backCmd;
    private Command watchCmd, backToResultsCmd, aboutCmd, backFromAboutCmd;
    private Command historyCmd, backFromHistoryCmd, clearHistoryCmd, clearHistoryConfirmCmd;
    private Command clearSearchCmd, okSplashCmd;

    
    private Vector videoIds      = new Vector();
    private Vector videoTitles   = new Vector();
    private Vector videoDurations = new Vector(); 
    private Vector thumbImages   = new Vector();

    
    private Vector searchHistory = new Vector(); 

    private String serverAddress = "";
    private int    selectedIndex = -1;

    private volatile boolean thumbLoaderRunning = false;

    
    public J2METube() {
        display = Display.getDisplay(this);
        readManifestProps();
        initUI();
    }

   
    private void readManifestProps() {
        String n = getAppProperty("MIDlet-Name");
        String v = getAppProperty("MIDlet-Version");
        String o = getAppProperty("MIDlet-Vendor");
        if (n != null && n.length() > 0) appName    = n;
        if (v != null && v.length() > 0) appVersion = v;
        if (o != null && o.length() > 0) appVendor  = o;
    }

   

    private void initUI() {

        
        exitCmd              = new Command("Exit",    Command.EXIT, 9);
        backCmd              = new Command("Back",    Command.BACK, 1);
        backToResultsCmd     = new Command("Back",    Command.BACK, 1);
        backFromAboutCmd     = new Command("Back",    Command.BACK, 1);
        backFromHistoryCmd   = new Command("Back",    Command.BACK, 1);
        watchCmd             = new Command("Watch",   Command.OK,   1);
        aboutCmd             = new Command("About",   Command.ITEM, 5);
        historyCmd           = new Command("History", Command.ITEM, 4);
        clearHistoryCmd      = new Command("Clear History",   Command.ITEM, 3);
        clearHistoryConfirmCmd= new Command("Confirm Clear",  Command.ITEM, 2);
        clearSearchCmd       = new Command("Clear",   Command.ITEM, 3);
        nextCmd              = new Command("Next \u00bb", Command.OK, 1); 
        searchCmd            = new Command("Search",  Command.OK,   1);
        okSplashCmd          = new Command("Let's Go!", Command.OK, 1);

        
        splashForm = new Form(appName);
        splashForm.append(
            "\n  Welcome to " + appName + "!\n\n" +
            "  Browse and watch YouTube\n" +
            "  videos on your phone via\n" +
            "  a local PC proxy.\n\n" +
            "  \u25ba First, make sure your\n" +
            "  PC server is running.\n\n" +
            "  \u25ba Connect phone + PC to\n" +
            "  the same Wi-Fi network.\n"
        );
        splashForm.addCommand(okSplashCmd);
        splashForm.addCommand(exitCmd);
        splashForm.setCommandListener(this);

        
        configForm = new Form("Server Setup");
        configForm.append(
            new StringItem(null,
                "Enter your PC\u2019s local IP address\n" +
                "and the port the server\n" +
                "is running on.\n\n" +
                "Example:  192.168.1.5:5000\n")
        );
        ipField = new TextField("PC IP & Port:", "192.168.1.X:5000", 100, TextField.ANY);
        configForm.append(ipField);
        configForm.addCommand(nextCmd);
        configForm.addCommand(aboutCmd);
        configForm.addCommand(exitCmd);
        configForm.setCommandListener(this);

        
        searchForm = new Form(appName + " Search");
        searchForm.append(
            new StringItem(null, "Type a search term below.\nTip: try an artist, song, or topic.\n")
        );
        searchField = new TextField("Search:", "", 80, TextField.ANY);
        searchForm.append(searchField);
        searchForm.addCommand(searchCmd);
        searchForm.addCommand(historyCmd);
        searchForm.addCommand(clearSearchCmd);
        searchForm.addCommand(backCmd);
        searchForm.addCommand(aboutCmd);
        searchForm.addCommand(exitCmd);
        searchForm.setCommandListener(this);

        
        resultsList = new List("Results", List.IMPLICIT);
        resultsList.addCommand(backCmd);
        resultsList.addCommand(aboutCmd);
        resultsList.setCommandListener(this);

        
        detailForm = new Form("Video Info");
        detailForm.addCommand(watchCmd);
        detailForm.addCommand(backToResultsCmd);
        detailForm.addCommand(exitCmd);
        detailForm.setCommandListener(this);

        
        historyList = new List("Recent Searches", List.IMPLICIT);
        historyList.addCommand(backFromHistoryCmd);
        historyList.addCommand(clearHistoryCmd);
        historyList.addCommand(exitCmd);
        historyList.setCommandListener(this);

        
        buildAboutScreen();
    }

    private void buildAboutScreen() {
        aboutForm = new Form("About " + appName);

        
        StringBuffer info = new StringBuffer();
        info.append(appName);
        if (appVersion.length() > 0) info.append("  v").append(appVersion);
        info.append("\n");
        if (appVendor.length() > 0) info.append(appVendor).append("\n");
        info.append("\n");

        // -- Device info from System.getProperty() --
        String platform = safeProperty("microedition.platform");
        String config   = safeProperty("microedition.configuration");
        String profiles = safeProperty("microedition.profiles");
        String locale   = safeProperty("microedition.locale");
        String encoding = safeProperty("microedition.encoding");

        info.append("--- Device Info ---\n");
        if (platform.length()  > 0) info.append("Platform: ").append(platform).append("\n");
        if (config.length()    > 0) info.append("Config: ").append(config).append("\n");
        if (profiles.length()  > 0) info.append("Profiles: ").append(profiles).append("\n");
        if (locale.length()    > 0) info.append("Locale: ").append(locale).append("\n");
        if (encoding.length()  > 0) info.append("Encoding: ").append(encoding).append("\n");

        // -- Build info (static; these don't change at runtime) --
        info.append("\n--- Build Info ---\n");
        info.append("Compiled: JDK 6 (1.6.0_45)\n");
        info.append("SDK: Java ME SDK 3.0.5\n");
        info.append("Target: MIDP 2.1 / CLDC 1.1\n");
        info.append("\n--- Data Source ---\n");
        info.append("Invidious API\nvia s60tube.io.vn\n");

        aboutForm.append(info.toString());

        if (backFromAboutCmd == null)
            backFromAboutCmd = new Command("Back", Command.BACK, 1);
        aboutForm.addCommand(backFromAboutCmd);
        aboutForm.addCommand(exitCmd);
        aboutForm.setCommandListener(this);
    }

    /** Null-safe System.getProperty() that never returns null. */
    private String safeProperty(String key) {
        try {
            String v = System.getProperty(key);
            return (v != null) ? v : "";
        } catch (Exception e) {
            return "";
        }
    }

    // =========================================================================
    // MIDlet lifecycle
    // =========================================================================

    public void startApp()          { display.setCurrent(splashForm); }
    public void pauseApp()          {}
    public void destroyApp(boolean u) {}

    // =========================================================================
    // Command dispatcher
    // =========================================================================

    public void commandAction(Command c, Displayable d) {

        if (c == exitCmd) {
            notifyDestroyed();

        // ---- Splash ----
        } else if (c == okSplashCmd) {
            display.setCurrent(configForm);

        // ---- About ----
        } else if (c == aboutCmd) {
            buildAboutScreen(); // refresh device info each time
            display.setCurrent(aboutForm);

        } else if (c == backFromAboutCmd) {
            if (d == aboutForm) {
                if (videoIds.isEmpty()) {
                    display.setCurrent(searchForm);
                } else {
                    display.setCurrent(resultsList);
                }
            } else {
                display.setCurrent(configForm);
            }

        // ---- Config ----
        } else if (c == nextCmd && d == configForm) {
            serverAddress = ipField.getString().trim();
            if (serverAddress.length() == 0 || serverAddress.indexOf(':') == -1) {
                showAlert("Missing Info",
                    "Please enter your PC\u2019s IP and port.\nExample: 192.168.1.5:5000",
                    AlertType.WARNING, configForm);
            } else {
                display.setCurrent(searchForm);
            }

        // ---- Search form ----
        } else if (c == backCmd && d == searchForm) {
            display.setCurrent(configForm);

        } else if (c == clearSearchCmd) {
            searchField.setString("");

        } else if (c == historyCmd) {
            rebuildHistoryList();
            display.setCurrent(historyList);

        } else if (c == searchCmd && d == searchForm) {
            String q = searchField.getString().trim();
            if (q.length() == 0) {
                showAlert("Empty Search",
                    "Please type something to search for.", AlertType.WARNING, searchForm);
            } else {
                performSearch(q);
            }

        // ---- History ----
        } else if (c == backFromHistoryCmd) {
            display.setCurrent(searchForm);

        } else if (c == clearHistoryCmd) {
            // Confirm before wiping
            if (searchHistory.isEmpty()) {
                showAlert("History Empty", "There\u2019s nothing to clear.", AlertType.INFO, historyList);
            } else {
                Alert confirm = new Alert("Clear History?",
                    "This will remove all " + searchHistory.size() + " saved search" +
                    (searchHistory.size() == 1 ? "" : "es") + ".\nAre you sure?",
                    null, AlertType.CONFIRMATION);
                confirm.setTimeout(Alert.FOREVER);
                confirm.addCommand(clearHistoryConfirmCmd);
                confirm.addCommand(backFromHistoryCmd);
                confirm.setCommandListener(this);
                display.setCurrent(confirm);
            }

        } else if (c == clearHistoryConfirmCmd) {
            searchHistory.removeAllElements();
            historyList.deleteAll();
            historyList.append("(no recent searches)", null);
            display.setCurrent(searchForm);

        } else if (d == historyList && c == List.SELECT_COMMAND) {
            int idx = historyList.getSelectedIndex();
            if (idx >= 0 && !searchHistory.isEmpty()) {
                String q = (String) searchHistory.elementAt(idx);
                searchField.setString(q);
                display.setCurrent(searchForm);
                performSearch(q);
            }

        // ---- Results ----
        } else if (c == backCmd && d == resultsList) {
            display.setCurrent(searchForm);

        } else if (d == resultsList && c == List.SELECT_COMMAND) {
            int idx = resultsList.getSelectedIndex();
            if (idx >= 0 && idx < videoIds.size()) {
                selectedIndex = idx;
                detailForm.deleteAll();
                detailForm.setTitle("Loading\u2026");
                detailForm.append(new StringItem(null, "Fetching video details,\nplease wait\u2026"));
                detailForm.setTicker(new Ticker("Loading video info\u2026"));
                display.setCurrent(detailForm);
                fetchVideoDetail((String) videoIds.elementAt(idx), idx);
            }

        // ---- Detail ----
        } else if (c == backToResultsCmd && d == detailForm) {
            display.setCurrent(resultsList);

        } else if (c == watchCmd && d == detailForm) {
            if (selectedIndex >= 0) {
                playVideoViaProxy((String) videoIds.elementAt(selectedIndex));
            }
        }
    }

    // =========================================================================
    // Search history helpers
    // =========================================================================

    private void addToHistory(String query) {
        for (int i = 0; i < searchHistory.size(); i++) {
            if (query.equalsIgnoreCase((String) searchHistory.elementAt(i))) {
                searchHistory.removeElementAt(i);
                break;
            }
        }
        searchHistory.insertElementAt(query, 0);
        while (searchHistory.size() > HISTORY_MAX)
            searchHistory.removeElementAt(searchHistory.size() - 1);
    }

    private void rebuildHistoryList() {
        historyList.deleteAll();
        if (searchHistory.isEmpty()) {
            historyList.append("(no recent searches yet)", null);
        } else {
            for (int i = 0; i < searchHistory.size(); i++) {
                String label = (i == 0 ? "\u25ba " : "   ") +
                               (String) searchHistory.elementAt(i);
                historyList.append(label, null);
            }
        }
    }

    // =========================================================================
    // Search
    // =========================================================================

    private void performSearch(final String query) {
        resultsList.deleteAll();
        videoIds       .removeAllElements();
        videoTitles    .removeAllElements();
        videoDurations .removeAllElements();
        thumbImages    .removeAllElements();

        String shortQ = query.length() > 20 ? query.substring(0, 17) + "\u2026" : query;
        resultsList.setTitle("Searching\u2026");
        resultsList.setTicker(new Ticker("Searching for: " + query + "   \u25ba"));
        display.setCurrent(resultsList);
        addToHistory(query);

        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null;
                InputStream    is = null;
                try {
                    String url = API_BASE + "/search?q=" + urlEncode(query);
                    hc = (HttpConnection) Connector.open(url);
                    hc.setRequestMethod(HttpConnection.GET);
                    int rc = hc.getResponseCode();
                    if (rc != HttpConnection.HTTP_OK) {
                        errorMsg("Server returned " + rc + ". Try again later.");
                        return;
                    }
                    is = hc.openInputStream();
                    StringBuffer sb = new StringBuffer();
                    int ch;
                    while ((ch = is.read()) != -1) sb.append((char) ch);
                    parseSearchResults(sb.toString(), query);
                } catch (Exception e) {
                    errorMsg("Network error: " + friendlyNetError(e.getMessage()));
                } finally {
                    closeQuiet(is, hc);
                }
            }
        }).start();
    }

    private void parseSearchResults(String json, String query) {
        int pos = 0, count = 0;
        while ((pos = json.indexOf("\"type\":\"video\"", pos)) != -1) {
            try {
                String block = json.substring(pos, Math.min(pos + 1500, json.length()));
                String title = extractField(block, "\"title\":\"");
                String id    = extractField(block, "\"videoId\":\"");
                if (id == null || id.length() == 0) { pos += 15; continue; }
                if (title == null) title = "(untitled)";

                // Try to grab lengthSeconds from the search result block too
                String dur = "";
                String rawSecs = extractLong(block, "\"lengthSeconds\":");
                if (rawSecs != null) {
                    try {
                        int secs = Integer.parseInt(rawSecs);
                        dur = "[" + (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60) + "]";
                    } catch (Exception ignored) {}
                }

                String listLabel = dur.length() > 0 ? title + "  " + dur : title;
                resultsList.append(listLabel, null);
                videoIds       .addElement(id);
                videoTitles    .addElement(title);
                videoDurations .addElement(dur);
                thumbImages    .addElement(null);
                count++;
            } catch (Exception e) {}
            pos += 15;
        }

        if (count == 0) {
            resultsList.append("No results found for \u201c" + query + "\u201d.", null);
            resultsList.append("Try different keywords.", null);
        }

        String title = count + " result" + (count == 1 ? "" : "s");
        resultsList.setTitle(title);
        resultsList.setTicker(count > 0
            ? new Ticker("Found " + count + " videos  \u2014  tap one to view details   \u25ba")
            : null);

        if (count > 0) startThumbLoader();
    }

    // =========================================================================
    // Detail screen
    // =========================================================================

    private void fetchVideoDetail(final String id, final int idx) {
        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null;
                InputStream    is = null;
                try {
                    String url = API_BASE + "/videos/" + id;
                    hc = (HttpConnection) Connector.open(url);
                    hc.setRequestMethod(HttpConnection.GET);
                    int rc = hc.getResponseCode();
                    if (rc != HttpConnection.HTTP_OK) {
                        buildDetailScreenFallback(idx, "Server returned " + rc + ".");
                        return;
                    }
                    is = hc.openInputStream();
                    StringBuffer sb = new StringBuffer();
                    int ch;
                    while ((ch = is.read()) != -1) sb.append((char) ch);
                    buildDetailScreen(sb.toString(), idx);
                } catch (Exception e) {
                    buildDetailScreenFallback(idx,
                        "Could not load details.\n(" + friendlyNetError(e.getMessage()) + ")");
                } finally {
                    closeQuiet(is, hc);
                }
            }
        }).start();
    }

    private void buildDetailScreen(String json, int idx) {
        String title     = extractField(json, "\"title\":\"");
        String author    = extractField(json, "\"author\":\"");
        String views     = extractLong (json, "\"viewCount\":");
        String likes     = extractLong (json, "\"likeCount\":");
        String published = extractField(json, "\"publishedText\":\"");
        String desc      = extractField(json, "\"description\":\"");

        if (title     == null) title     = (String) videoTitles.elementAt(idx);
        if (author    == null) author    = "";
        if (views     == null) views     = "";
        if (likes     == null) likes     = "";
        if (published == null) published = "";
        if (desc      == null) desc      = "";

        views = formatNumber(views);
        likes = formatNumber(likes);

        // Duration: prefer full detail endpoint, fall back to search-derived
        String lengthStr = (String) videoDurations.elementAt(idx);
        try {
            int secs = Integer.parseInt(extractLong(json, "\"lengthSeconds\":"));
            lengthStr = (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
        } catch (Exception e) {}

        populateDetailForm(idx, title, author, views, likes, lengthStr, published, desc);
    }

    private void buildDetailScreenFallback(int idx, String note) {
        populateDetailForm(idx, (String) videoTitles.elementAt(idx),
            "", "", "", (String) videoDurations.elementAt(idx), "", note);
    }

    private void populateDetailForm(final int idx,
                                    final String title,
                                    final String author,
                                    final String views,
                                    final String likes,
                                    final String length,
                                    final String published,
                                    final String desc) {
        detailForm.deleteAll();
        detailForm.setTicker(null);

        // Thumbnail
        Image thumb = (Image) thumbImages.elementAt(idx);
        if (thumb != null) {
            // Show a slightly larger version on the detail screen
            Image big = loadImage("http://i.ytimg.com/vi/" +
                                  videoIds.elementAt(idx) + "/mqdefault.jpg");
            if (big != null) big = scaleImage(big, 160, 90);
            detailForm.append(big != null ? big : thumb);
            detailForm.append("\n");
        }

        // Title (bold)
        StringItem t = new StringItem(null, title + "\n");
        t.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        detailForm.append(t);

        // Metadata rows — only show fields we actually have
        if (author.length()    > 0) detailForm.append(new StringItem("\u25b6 Channel:  ", author + "\n"));
        if (published.length() > 0) detailForm.append(new StringItem("\uD83D\uDCC5 Uploaded: ", published + "\n"));
        if (length.length()    > 0) detailForm.append(new StringItem("\u23F1 Length:   ", length + "\n"));
        if (views.length()     > 0) detailForm.append(new StringItem("\uD83D\uDC41 Views:    ", views + "\n"));
        if (likes.length()     > 0) detailForm.append(new StringItem("\uD83D\uDC4D Likes:    ", likes + "\n"));

        // Description
        if (desc.length() > 0) {
            detailForm.append("\n");
            String s = desc.length() > 500 ? desc.substring(0, 497) + "\u2026" : desc;
            detailForm.append(new StringItem("Description:\n", s));
        }

        String shortTitle = title.length() > 22 ? title.substring(0, 19) + "\u2026" : title;
        detailForm.setTitle(shortTitle);

        // Helpful hint in the ticker
        detailForm.setTicker(new Ticker(
            "Press \u2018Watch\u2019 to play on your PC   \u25ba   " + shortTitle
        ));
    }

    // =========================================================================
    // Thumbnail loader
    // =========================================================================

    private void startThumbLoader() {
        if (thumbLoaderRunning) return;
        thumbLoaderRunning = true;
        final int total = videoIds.size();
        new Thread(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < videoIds.size(); i++) {
                        if (thumbImages.elementAt(i) != null) continue;
                        String id  = (String) videoIds.elementAt(i);

                        // Update ticker with progress
                        try {
                            resultsList.setTicker(new Ticker(
                                "Loading thumbnails " + (i + 1) + "/" + total +
                                "  \u2014  " + (String) videoTitles.elementAt(i)
                            ));
                        } catch (Exception ignored) {}

                        Image img = loadImage("http://i.ytimg.com/vi/" + id + "/default.jpg");
                        if (img != null) {
                            img = scaleImage(img, 48, 36);
                            thumbImages.setElementAt(img, i);
                            // Rebuild list entry to show thumb + duration label
                            String dur = (String) videoDurations.elementAt(i);
                            String lbl = (String) videoTitles.elementAt(i);
                            if (dur.length() > 0) lbl += "  " + dur;
                            try { resultsList.set(i, lbl, img); } catch (Exception ex) {}
                        }
                        try { Thread.sleep(80); } catch (InterruptedException e) {}
                    }
                    // Clear progress ticker when done
                    try { resultsList.setTicker(null); } catch (Exception ignored) {}
                } finally {
                    thumbLoaderRunning = false;
                }
            }
        }).start();
    }

    // =========================================================================
    // Proxy + RTSP playback
    // =========================================================================

    private void playVideoViaProxy(final String vid) {
        String title = selectedIndex >= 0 ?
            (String) videoTitles.elementAt(selectedIndex) : "video";
        String shortTitle = title.length() > 25 ? title.substring(0, 22) + "\u2026" : title;

        Alert a = new Alert("Opening on PC",
            "Sending \u201c" + shortTitle + "\u201d to your PC player.\n\nThis may take a moment\u2026",
            null, AlertType.INFO);
        a.setTimeout(5000);
        display.setCurrent(a, detailForm);

        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null;
                InputStream    is = null;
                try {
                    hc = (HttpConnection) Connector.open(
                        "http://" + serverAddress + "/play?v=" + vid);
                    hc.setRequestMethod(HttpConnection.GET);
                    is = hc.openInputStream();
                    StringBuffer sb = new StringBuffer();
                    int ch;
                    while ((ch = is.read()) != -1) sb.append((char) ch);
                    String result = sb.toString().trim();
                    if (result.length() > 0) platformRequest(result);
                } catch (Exception e) {
                    errorMsg("Couldn\u2019t reach your PC server.\n(" +
                             friendlyNetError(e.getMessage()) + ")\n\nCheck that the server is running\nand both devices share the same network.");
                } finally {
                    closeQuiet(is, hc);
                }
            }
        }).start();
    }

    // =========================================================================
    // JSON helpers
    // =========================================================================

    private String extractField(String json, String key) {
        int start = json.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        StringBuffer val = new StringBuffer();
        while (start < json.length()) {
            char c = json.charAt(start);
            if (c == '"') break;
            if (c == '\\' && start + 1 < json.length()) {
                char n = json.charAt(start + 1);
                if      (n == '"')  { val.append('"');  start += 2; continue; }
                else if (n == 'n')  { val.append('\n'); start += 2; continue; }
                else if (n == '\\') { val.append('\\'); start += 2; continue; }
                else if (n == 'r')  { start += 2; continue; }
                else if (n == 't')  { val.append(' ');  start += 2; continue; }
            }
            val.append(c);
            start++;
        }
        return val.toString();
    }

    private String extractLong(String json, String key) {
        int start = json.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        StringBuffer val = new StringBuffer();
        while (start < json.length()) {
            char c = json.charAt(start);
            if (c >= '0' && c <= '9') { val.append(c); start++; }
            else break;
        }
        return val.length() > 0 ? val.toString() : null;
    }

    private String formatNumber(String raw) {
        if (raw == null || raw.length() == 0) return "";
        StringBuffer out = new StringBuffer();
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) out.append(',');
            out.append(raw.charAt(i));
        }
        return out.toString();
    }

    // =========================================================================
    // Network / image helpers
    // =========================================================================

    private Image loadImage(String url) {
        HttpConnection hc = null;
        InputStream    is = null;
        try {
            hc = (HttpConnection) Connector.open(url);
            hc.setRequestMethod(HttpConnection.GET);
            if (hc.getResponseCode() != HttpConnection.HTTP_OK) return null;
            is = hc.openInputStream();
            int len = (int) hc.getLength();
            byte[] data;
            if (len > 0) {
                data = new byte[len];
                int off = 0, n;
                while (off < len && (n = is.read(data, off, len - off)) != -1) off += n;
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[256];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                data = baos.toByteArray();
            }
            return Image.createImage(data, 0, data.length);
        } catch (Exception e) {
            return null;
        } finally {
            closeQuiet(is, hc);
        }
    }

    private Image scaleImage(Image src, int tw, int th) {
        try {
            int sw = src.getWidth(), sh = src.getHeight();
            if (sw == tw && sh == th) return src;
            int[] sp = new int[sw * sh];
            src.getRGB(sp, 0, sw, 0, 0, sw, sh);
            int[] dp = new int[tw * th];
            for (int y = 0; y < th; y++) {
                int sy = (y * sh) / th;
                for (int x = 0; x < tw; x++)
                    dp[y * tw + x] = sp[sy * sw + (x * sw) / tw];
            }
            return Image.createRGBImage(dp, tw, th, false);
        } catch (Exception e) {
            return src;
        }
    }

    private String urlEncode(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == ' ')  sb.append('+');
            else if (c == '&')  sb.append("%26");
            else if (c == '=')  sb.append("%3D");
            else if (c == '+')  sb.append("%2B");
            else if (c == '#')  sb.append("%23");
            else if (c == '?')  sb.append("%3F");
            else                sb.append(c);
        }
        return sb.toString();
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    /**
     * Turn a raw exception message into something a non-technical user
     * can act on.
     */
    private String friendlyNetError(String raw) {
        if (raw == null) return "Unknown error";
        String lo = raw.toLowerCase();
        if (lo.indexOf("connect") != -1 || lo.indexOf("refused") != -1)
            return "Cannot reach server \u2014 is it running?";
        if (lo.indexOf("timeout") != -1)
            return "Connection timed out \u2014 check your Wi-Fi";
        if (lo.indexOf("host") != -1)
            return "Host not found \u2014 check the IP address";
        return raw.length() > 60 ? raw.substring(0, 57) + "\u2026" : raw;
    }

    private void showAlert(String title, String msg, AlertType type, Displayable next) {
        Alert a = new Alert(title, msg, null, type);
        a.setTimeout(Alert.FOREVER);
        display.setCurrent(a, next);
    }

    private void errorMsg(String msg) {
        Alert a = new Alert("Oops!", msg, null, AlertType.ERROR);
        a.setTimeout(Alert.FOREVER);
        display.setCurrent(a, searchForm);
    }

    private void closeQuiet(InputStream is, HttpConnection hc) {
        try { if (is != null) is.close(); } catch (Exception e) {}
        try { if (hc != null) hc.close(); } catch (Exception e) {}
    }
}
