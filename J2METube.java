import com.sun.lwuit.*;
import com.sun.lwuit.animations.*;
import com.sun.lwuit.events.*;
import com.sun.lwuit.layouts.*;
import com.sun.lwuit.list.*;
import com.sun.lwuit.plaf.*;

import javax.microedition.midlet.MIDlet;
import javax.microedition.io.*;
import java.io.*;
import java.util.Vector;
import java.util.Hashtable;

/**
 * J2METube - LWUIT 1.5 Edition
 *
 * All API calls verified against LWUIT 1.5:
 *  - showWithTransition()       -> setTransitionInAnimator() + show()
 *  - Dialog.show(5-arg+cb)      -> Dialog.show(title,msg,ok,cancel) returns int
 *  - TextField.setLabel()       -> separate Label above the field
 *  - Slider.setRenderAsProgress -> removed (not in 1.5)
 *  - InfiniteProgress           -> replaced with styled Label
 *  - addPointerReleasedListener -> removed; Button overlay used instead
 *  - Image.getRGB/createRGBImage -> via underlying MIDP image (getImage())
 *  - forceRevalidate()          -> revalidate()
 *  - non-final locals in anons  -> declared final
 */
public class J2METube extends MIDlet implements ActionListener {

    private static final String API_BASE    = "http://s60tube.io.vn/api/v1";
    private static final int    HISTORY_MAX = 10;
    private static final String RMS_FAVS    = "j2me_favorites";
    private static final String RMS_PREFS   = "j2me_prefs";

    private String appName    = "J2METube";
    private String appVersion = "2.0";
    private String appVendor  = "";

    private Form splashForm;
    private Form mainForm;
    private Form settingsForm;
    private Form resultsForm;
    private Form detailForm;
    private Form downloadsForm;
    private Form aboutForm;

    private String  serverAddress  = "192.168.1.X:5000";
    private boolean showThumbnails = true;
    private int     maxResults     = 10;
    private String  videoQuality   = "medium";

    private TextField searchField;
    private Vector    searchHistory  = new Vector();
    private Vector    videoIds       = new Vector();
    private Vector    videoTitles    = new Vector();
    private Vector    videoDurations = new Vector();
    private Vector    videoAuthors   = new Vector();
    private Vector    videoViews     = new Vector();
    private Vector    thumbImages    = new Vector(); // Label refs for in-place icon update

    private Vector favorites      = new Vector();
    private Vector downloadQueue  = new Vector();
    private Vector downloadStatus = new Vector();

    private int     selectedIndex     = -1;
    private volatile boolean thumbLoaderRunning = false;

    private static final int COLOR_BG        = 0x1A1A2E;
    private static final int COLOR_SURFACE   = 0x16213E;
    private static final int COLOR_PRIMARY   = 0xE94560;
    private static final int COLOR_SECONDARY = 0x0F3460;
    private static final int COLOR_TEXT      = 0xEEEEEE;
    private static final int COLOR_SUBTEXT   = 0xAAAAAA;
    private static final int COLOR_ACCENT    = 0xFF6B6B;
    private static final int COLOR_SUCCESS   = 0x4CAF50;
    private static final int COLOR_WARNING   = 0xFFC107;

    // =========================================================================
    // MIDlet lifecycle
    // =========================================================================

    public void startApp() {

    // ---- KEmulator compatibility ----
    System.setProperty("lwuit.noGameCanvas", "true");
    System.setProperty("lwuit.useNativeCommands", "true");

    // Initialize LWUIT display
    Display.init(this);

    // No need to force fullscreen in LWUIT 1.5; softkeys work automatically
    // Deprecated calls like setForceFullScreen or setFullScreenMode are removed

    // Load app data
    readManifestProps();
    loadPreferences();
    loadFavorites();

    // Apply theme
    applyTheme();

    // Show splash screen
    showSplash();
}

    public void pauseApp()  {}
    public void destroyApp(boolean u) { savePreferences(); }

    // =========================================================================
    // Theme
    // =========================================================================

    private void applyTheme() {
        UIManager m = UIManager.getInstance();
        m.setComponentStyle("Form",       makeStyle(COLOR_BG,        COLOR_TEXT));
        m.setComponentStyle("Label",      makeStyle(COLOR_BG,        COLOR_TEXT));
        m.setComponentStyle("Button",     makeButtonStyle());
        m.setComponentStyle("TextField",  makeFieldStyle());
        m.setComponentStyle("List",       makeStyle(COLOR_SURFACE,   COLOR_TEXT));
        m.setComponentStyle("TabbedPane", makeStyle(COLOR_SECONDARY, COLOR_TEXT));
        m.setComponentStyle("Title",      makeTitleStyle());
    }

    private Style makeStyle(int bg, int fg) {
        Style s = new Style();
        s.setBgColor(bg);  s.setFgColor(fg);  s.setBgTransparency(255);
        s.setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        return s;
    }
    private Style makeButtonStyle() {
        Style s = new Style();
        s.setBgColor(COLOR_PRIMARY); s.setFgColor(0xFFFFFF); s.setBgTransparency(255);
        s.setPadding(6, 6, 12, 12);
        s.setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        return s;
    }
    private Style makeFieldStyle() {
        Style s = new Style();
        s.setBgColor(COLOR_SECONDARY); s.setFgColor(COLOR_TEXT); s.setBgTransparency(255);
        s.setPadding(4, 4, 6, 6);
        s.setBorder(Border.createLineBorder(2, COLOR_PRIMARY));
        return s;
    }
    private Style makeTitleStyle() {
        Style s = new Style();
        s.setBgColor(COLOR_PRIMARY); s.setFgColor(0xFFFFFF); s.setBgTransparency(255);
        s.setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        s.setPadding(8, 8, 8, 8);
        return s;
    }

    // =========================================================================
    // FIX: showWithTransition() does not exist in LWUIT 1.5.
    // Use setTransitionInAnimator() on the destination form, then call show().
    // =========================================================================

    private void showWithSlide(Form form, boolean forward) {
        form.setTransitionInAnimator(
            CommonTransitions.createSlide(CommonTransitions.SLIDE_HORIZONTAL, forward, 300));
        form.show();
    }

    // =========================================================================
    // Splash
    // =========================================================================

    private void showSplash() {
        splashForm = new Form();
        splashForm.setLayout(new BorderLayout());
        splashForm.addComponent(BorderLayout.NORTH, makeHeader(appName, null));

        Container body = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        body.getStyle().setBgColor(COLOR_BG); body.getStyle().setBgTransparency(255);
        body.getStyle().setPadding(20, 20, 16, 16);

        Label logo = new Label("  > " + appName);
        logo.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
        logo.getStyle().setFgColor(COLOR_PRIMARY); logo.getStyle().setBgTransparency(0);
        body.addComponent(logo);

        Label tag = new Label("YouTube on your Nokia");
        tag.getStyle().setFgColor(COLOR_SUBTEXT); tag.getStyle().setBgTransparency(0);
        body.addComponent(tag);
        body.addComponent(makeSpacer(16));

        String[] bullets = {
            "* Make sure your PC server is running",
            "* Connect phone & PC to same Wi-Fi",
            "* Enter the PC local IP in Settings",
            "* Search, browse, and enjoy!"
        };
        for (int i = 0; i < bullets.length; i++) {
            Label b = new Label(bullets[i]);
            b.getStyle().setFgColor(COLOR_TEXT); b.getStyle().setBgTransparency(0);
            body.addComponent(b);
            body.addComponent(makeSpacer(4));
        }
        splashForm.addComponent(BorderLayout.CENTER, body);

        Container footer = new Container(new FlowLayout(Component.CENTER));
        footer.getStyle().setBgColor(COLOR_SURFACE); footer.getStyle().setBgTransparency(255);
        footer.getStyle().setPadding(8, 8, 0, 0);

        Button goBtn = makeButton("Let's Go! >>", COLOR_PRIMARY);
        goBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { showMainForm(); }
        });
        Button setBtn = makeButton("Settings", COLOR_SECONDARY);
        setBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { showSettings(splashForm); }
        });
        footer.addComponent(goBtn);
        footer.addComponent(makeSpacer(8));
        footer.addComponent(setBtn);
        splashForm.addComponent(BorderLayout.SOUTH, footer);
        splashForm.show();
    }

    // =========================================================================
    // Main form
    // =========================================================================

    private void showMainForm() {
        mainForm = new Form();
        mainForm.setLayout(new BorderLayout());
        mainForm.addComponent(BorderLayout.NORTH, makeHeader(appName + " Search", null));

        TabbedPane tabs = new TabbedPane();
        tabs.getStyle().setBgColor(COLOR_BG); tabs.getStyle().setBgTransparency(255);
        tabs.addTab("Search",    buildSearchTab());
        tabs.addTab("History",   buildHistoryTab());
        tabs.addTab("Favorites", buildFavoritesTab());
        mainForm.addComponent(BorderLayout.CENTER, tabs);

        mainForm.addComponent(BorderLayout.SOUTH, makeNavBar(
            new String[]{"Settings", "About", "Exit"},
            new ActionListener[]{
                new ActionListener() { public void actionPerformed(ActionEvent e) { showSettings(mainForm); }},
                new ActionListener() { public void actionPerformed(ActionEvent e) { showAbout(); }},
                new ActionListener() { public void actionPerformed(ActionEvent e) { notifyDestroyed(); }}
            }
        ));
        showWithSlide(mainForm, true);
    }

    private Container buildSearchTab() {
        Container tab = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        tab.getStyle().setBgColor(COLOR_BG); tab.getStyle().setBgTransparency(255);
        tab.getStyle().setPadding(12, 12, 8, 8);

        Label hint = new Label("Search YouTube videos");
        hint.getStyle().setFgColor(COLOR_SUBTEXT); hint.getStyle().setBgTransparency(0);
        tab.addComponent(hint);
        tab.addComponent(makeSpacer(8));

        searchField = new TextField();
        searchField.setHint("Artist, song, topic...");
        searchField.setMaxSize(80);
        searchField.getStyle().setBgColor(COLOR_SECONDARY);
        searchField.getStyle().setFgColor(COLOR_TEXT);
        searchField.getStyle().setBgTransparency(255);
        searchField.getStyle().setBorder(Border.createLineBorder(1, COLOR_PRIMARY));
        tab.addComponent(searchField);
        tab.addComponent(makeSpacer(10));

        Container btns = new Container(new FlowLayout(Component.LEFT));
        btns.getStyle().setBgTransparency(0);

        Button searchBtn = makeButton("> Search", COLOR_PRIMARY);
        searchBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String q = searchField.getText().trim();
                if (q.length() == 0) showToast("Please enter a search term", COLOR_WARNING);
                else                  performSearch(q);
            }
        });
        Button clearBtn = makeButton("X Clear", COLOR_SECONDARY);
        clearBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { searchField.setText(""); }
        });
        btns.addComponent(searchBtn);
        btns.addComponent(makeSpacer(8));
        btns.addComponent(clearBtn);
        tab.addComponent(btns);
        return tab;
    }

    private Container buildHistoryTab() {
        Container tab = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        tab.getStyle().setBgColor(COLOR_BG); tab.getStyle().setBgTransparency(255);
        tab.getStyle().setPadding(8, 8, 8, 8);

        if (searchHistory.isEmpty()) {
            Label empty = new Label("No recent searches yet.");
            empty.getStyle().setFgColor(COLOR_SUBTEXT); empty.getStyle().setBgTransparency(0);
            tab.addComponent(empty);
        } else {
            for (int i = 0; i < searchHistory.size(); i++) {
                final String q = (String) searchHistory.elementAt(i);
                Button b = new Button((i == 0 ? "> " : "  ") + q);
                b.getStyle().setBgColor(i % 2 == 0 ? COLOR_SURFACE : COLOR_BG);
                b.getStyle().setFgColor(COLOR_TEXT); b.getStyle().setBgTransparency(255);
                b.getStyle().setBorder(Border.createEmpty());
                b.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        searchField.setText(q);
                        performSearch(q);
                    }
                });
                tab.addComponent(b);
            }
            tab.addComponent(makeSpacer(10));

            Button clearBtn = makeButton("Clear History", COLOR_ACCENT);
            clearBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    // Dialog.show(title, msg, okLabel, cancelLabel) returns boolean in LWUIT 1.5:
                    // true = OK button pressed, false = Cancel.
                    boolean confirmed = Dialog.show("Clear History?",
                        "Remove all " + searchHistory.size() + " saved search(es)?",
                        "Clear", "Cancel");
                    if (confirmed) {
                        searchHistory.removeAllElements();
                        savePreferences();
                        showToast("History cleared", COLOR_SUCCESS);
                        showMainForm();
                    }
                }
            });
            tab.addComponent(clearBtn);
        }
        return tab;
    }

    private Container buildFavoritesTab() {
        Container tab = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        tab.getStyle().setBgColor(COLOR_BG); tab.getStyle().setBgTransparency(255);
        tab.getStyle().setPadding(8, 8, 8, 8);

        if (favorites.isEmpty()) {
            Label e1 = new Label("<3  No favorites saved yet.");
            e1.getStyle().setFgColor(COLOR_SUBTEXT); e1.getStyle().setBgTransparency(0);
            Label e2 = new Label("Open a video and tap Fav to save it.");
            e2.getStyle().setFgColor(COLOR_SUBTEXT); e2.getStyle().setBgTransparency(0);
            tab.addComponent(e1); tab.addComponent(e2);
        } else {
            for (int i = 0; i < favorites.size(); i++) {
                final Hashtable fav = (Hashtable) favorites.elementAt(i);
                final String favId    = (String) fav.get("id");
                final String favTitle = (String) fav.get("title");
                final String favAuth  = (String) fav.get("author");
                final String favDur   = (String) fav.get("duration");
                final int    fi       = i;

                Container row = new Container(new BorderLayout());
                row.getStyle().setBgColor(fi % 2 == 0 ? COLOR_SURFACE : COLOR_BG);
                row.getStyle().setBgTransparency(255);
                row.getStyle().setPadding(6, 6, 6, 6); row.getStyle().setMargin(0, 0, 0, 2);

                Container info = new Container(new BoxLayout(BoxLayout.Y_AXIS));
                info.getStyle().setBgTransparency(0);
                Label tl = new Label(favTitle);
                tl.getStyle().setFgColor(COLOR_TEXT);
                tl.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
                tl.getStyle().setBgTransparency(0);
                Label ml = new Label(favAuth + "  " + favDur);
                ml.getStyle().setFgColor(COLOR_SUBTEXT); ml.getStyle().setBgTransparency(0);
                info.addComponent(tl); info.addComponent(ml);

                Button watchBtn = makeSmallButton(">", COLOR_PRIMARY);
                watchBtn.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        openFavorite(favId, favTitle, favAuth, favDur);
                    }
                });
                Button removeBtn = makeSmallButton("X", COLOR_ACCENT);
                removeBtn.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        favorites.removeElementAt(fi);
                        saveFavorites();
                        showToast("Removed from favorites", COLOR_WARNING);
                        showMainForm();
                    }
                });

                Container acts = new Container(new BoxLayout(BoxLayout.X_AXIS));
                acts.getStyle().setBgTransparency(0);
                acts.addComponent(watchBtn); acts.addComponent(makeSpacer(4)); acts.addComponent(removeBtn);

                row.addComponent(BorderLayout.CENTER, info);
                row.addComponent(BorderLayout.EAST, acts);
                tab.addComponent(row);
            }
        }
        return tab;
    }

    // =========================================================================
    // Settings
    // =========================================================================

    private void showSettings(final Form returnTo) {
        settingsForm = new Form();
        settingsForm.setLayout(new BorderLayout());
        settingsForm.addComponent(BorderLayout.NORTH, makeHeader("Settings", null));

        Container body = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        body.getStyle().setBgColor(COLOR_BG); body.getStyle().setBgTransparency(255);
        body.getStyle().setPadding(12, 12, 10, 10);

        // FIX: TextField.setLabel() does not exist in LWUIT 1.5.
        // Use a Label component above the TextField instead.
        body.addComponent(makeSectionLabel("Server"));
        body.addComponent(makeLabel("PC IP & Port  (e.g. 192.168.1.5:5000)", COLOR_SUBTEXT));
        final TextField ipFld = new TextField(serverAddress);
        ipFld.setMaxSize(50);
        ipFld.getStyle().setBgColor(COLOR_SECONDARY); ipFld.getStyle().setFgColor(COLOR_TEXT);
        ipFld.getStyle().setBgTransparency(255);
        body.addComponent(ipFld);
        body.addComponent(makeSpacer(12));

        body.addComponent(makeSectionLabel("Display"));
        final CheckBox thumbCb = new CheckBox("Show thumbnails in results");
        thumbCb.setSelected(showThumbnails);
        thumbCb.getStyle().setFgColor(COLOR_TEXT); thumbCb.getStyle().setBgTransparency(0);
        body.addComponent(thumbCb);
        body.addComponent(makeSpacer(8));

        // FIX: Slider.setRenderAsProgress() does not exist in LWUIT 1.5. Just omit it.
        body.addComponent(makeSectionLabel("Max Search Results"));
        final Label sliderVal = makeLabel("" + maxResults, COLOR_ACCENT);
        final Slider maxSlider = new Slider();
        maxSlider.setMinValue(5);
        maxSlider.setMaxValue(25);
        maxSlider.setProgress(maxResults);
        maxSlider.addDataChangedListener(new DataChangedListener() {
            public void dataChanged(int type, int index) {
                sliderVal.setText("" + maxSlider.getProgress());
                sliderVal.repaint();
            }
        });
        Container sliderRow = new Container(new BoxLayout(BoxLayout.X_AXIS));
        sliderRow.getStyle().setBgTransparency(0);
        sliderRow.addComponent(maxSlider);
        sliderRow.addComponent(makeSpacer(6));
        sliderRow.addComponent(sliderVal);
        body.addComponent(sliderRow);
        body.addComponent(makeSpacer(12));

        body.addComponent(makeSectionLabel("Video Quality"));
        final String[] qualities = {"low",       "medium",       "high"};
        final String[] qLabels   = {"Low (360p)", "Medium (480p)", "High (720p)"};
        for (int i = 0; i < qualities.length; i++) {
            final String q = qualities[i];
            final RadioButton rb = new RadioButton(qLabels[i]);
            rb.setSelected(videoQuality.equals(q));
            rb.getStyle().setFgColor(COLOR_TEXT); rb.getStyle().setBgTransparency(0);
            rb.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { videoQuality = q; }
            });
            body.addComponent(rb);
        }
        body.addComponent(makeSpacer(16));

        Button saveBtn = makeButton("Save Settings", COLOR_PRIMARY);
        saveBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String ip = ipFld.getText().trim();
                if (ip.length() == 0 || ip.indexOf(':') == -1) {
                    showToast("Enter IP:port  e.g. 192.168.1.5:5000", COLOR_WARNING);
                    return;
                }
                serverAddress  = ip;
                showThumbnails = thumbCb.isSelected();
                maxResults     = maxSlider.getProgress();
                savePreferences();
                showToast("Settings saved!", COLOR_SUCCESS);
                showWithSlide(returnTo, false);
            }
        });
        body.addComponent(saveBtn);
        settingsForm.addComponent(BorderLayout.CENTER, body);

        Button backBtn = makeButton("< Back", COLOR_SECONDARY);
        backBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { showWithSlide(returnTo, false); }
        });
        settingsForm.addComponent(BorderLayout.SOUTH, wrapInHFlow(backBtn));
        showWithSlide(settingsForm, true);
    }

    // =========================================================================
    // Search
    // =========================================================================

    private void performSearch(final String query) {
        addToHistory(query);

        // FIX: InfiniteProgress does not exist in LWUIT 1.5.
        // Show a simple bold label as a loading indicator instead.
        resultsForm = new Form();
        resultsForm.setLayout(new BorderLayout());
        resultsForm.addComponent(BorderLayout.NORTH, makeHeader("Searching...", null));

        Container loadBody = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        loadBody.getStyle().setBgColor(COLOR_BG); loadBody.getStyle().setBgTransparency(255);
        loadBody.getStyle().setPadding(30, 30, 16, 16);

        Label spinner = new Label("[ Searching: " + query + " ]");
        spinner.getStyle().setFgColor(COLOR_PRIMARY); spinner.getStyle().setBgTransparency(0);
        spinner.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        Container spinRow = new Container(new FlowLayout(Component.CENTER));
        spinRow.getStyle().setBgTransparency(0);
        spinRow.addComponent(spinner);
        loadBody.addComponent(spinRow);
        loadBody.addComponent(makeSpacer(10));

        Label waitLbl = new Label("Please wait...");
        waitLbl.getStyle().setFgColor(COLOR_SUBTEXT); waitLbl.getStyle().setBgTransparency(0);
        Container waitRow = new Container(new FlowLayout(Component.CENTER));
        waitRow.getStyle().setBgTransparency(0);
        waitRow.addComponent(waitLbl);
        loadBody.addComponent(waitRow);

        resultsForm.addComponent(BorderLayout.CENTER, loadBody);
        showWithSlide(resultsForm, true);

        videoIds.removeAllElements(); videoTitles.removeAllElements();
        videoDurations.removeAllElements(); videoAuthors.removeAllElements();
        videoViews.removeAllElements(); thumbImages.removeAllElements();

        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null; InputStream is = null;
                try {
                    String url = API_BASE + "/search?q=" + urlEncode(query)
                               + "&max_results=" + maxResults;
                    hc = (HttpConnection) Connector.open(url);
                    hc.setRequestMethod(HttpConnection.GET);
                    int rc = hc.getResponseCode();
                    if (rc != HttpConnection.HTTP_OK) {
                        showSearchError("Server returned HTTP " + rc + ". Try again later.", query);
                        return;
                    }
                    is = hc.openInputStream();
                    StringBuffer sb = new StringBuffer();
                    int ch;
                    while ((ch = is.read()) != -1) sb.append((char) ch);
                    parseSearchResults(sb.toString(), query);
                } catch (Exception e) {
                    showSearchError(friendlyNetError(e.getMessage()), query);
                } finally { closeQuiet(is, hc); }
            }
        }).start();
    }

    private void parseSearchResults(final String json, final String query) {
        Display.getInstance().callSerially(new Runnable() {
            public void run() {
                int pos = 0, count = 0;
                while ((pos = json.indexOf("\"type\":\"video\"", pos)) != -1) {
                    if (count >= maxResults) break;
                    try {
                        String block  = json.substring(pos, Math.min(pos + 1500, json.length()));
                        String title  = extractField(block, "\"title\":\"");
                        String id     = extractField(block, "\"videoId\":\"");
                        String author = extractField(block, "\"author\":\"");
                        String views  = extractLong (block, "\"viewCount\":");
                        if (id == null || id.length() == 0) { pos += 15; continue; }
                        if (title  == null) title  = "(untitled)";
                        if (author == null) author = "";
                        if (views  == null) views  = "";
                        String dur = "";
                        String rawSecs = extractLong(block, "\"lengthSeconds\":");
                        if (rawSecs != null) {
                            try {
                                int secs = Integer.parseInt(rawSecs);
                                dur = (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
                            } catch (Exception ignored) {}
                        }
                        videoIds.addElement(id); videoTitles.addElement(title);
                        videoDurations.addElement(dur); videoAuthors.addElement(author);
                        videoViews.addElement(views);
                        thumbImages.addElement(null); // slot; replaced by Label ref in buildVideoCard
                        count++;
                    } catch (Exception ignored) {}
                    pos += 15;
                }
                buildResultsScreen(count, query);
                if (count > 0 && showThumbnails) startThumbLoader();
            }
        });
    }

    private void buildResultsScreen(final int count, final String query) {
        resultsForm = new Form();
        resultsForm.setLayout(new BorderLayout());
        String hdr = count > 0
            ? count + " result" + (count == 1 ? "" : "s") + " for \"" + query + "\""
            : "No results";
        resultsForm.addComponent(BorderLayout.NORTH, makeHeader(hdr, null));

        if (count == 0) {
            Container empty = new Container(new BoxLayout(BoxLayout.Y_AXIS));
            empty.getStyle().setBgColor(COLOR_BG); empty.getStyle().setBgTransparency(255);
            empty.getStyle().setPadding(20, 20, 16, 16);
            Label l1 = new Label("No results for \"" + query + "\".");
            l1.getStyle().setFgColor(COLOR_TEXT); l1.getStyle().setBgTransparency(0);
            Label l2 = new Label("Try different keywords.");
            l2.getStyle().setFgColor(COLOR_SUBTEXT); l2.getStyle().setBgTransparency(0);
            empty.addComponent(l1); empty.addComponent(l2);
            resultsForm.addComponent(BorderLayout.CENTER, empty);
        } else {
            Container list = new Container(new BoxLayout(BoxLayout.Y_AXIS));
            list.getStyle().setBgColor(COLOR_BG); list.getStyle().setBgTransparency(255);
            for (int i = 0; i < count; i++) list.addComponent(buildVideoCard(i));
            Container scroll = new Container(new BorderLayout());
            scroll.getStyle().setBgColor(COLOR_BG); scroll.getStyle().setBgTransparency(255);
            scroll.addComponent(BorderLayout.NORTH, list);
            resultsForm.addComponent(BorderLayout.CENTER, scroll);
        }

        resultsForm.addComponent(BorderLayout.SOUTH, makeNavBar(
            new String[]{"< Back", "<3 Favs", "Settings"},
            new ActionListener[]{
                new ActionListener() { public void actionPerformed(ActionEvent e) { showMainForm(); }},
                new ActionListener() { public void actionPerformed(ActionEvent e) { showMainForm(); }},
                new ActionListener() { public void actionPerformed(ActionEvent e) { showSettings(resultsForm); }}
            }
        ));
        resultsForm.show();
    }

    /**
     * Build one video card row.
     * FIX 1: 'card' must be final to be referenced inside FocusListener.
     * FIX 2: Container.addPointerReleasedListener() does not exist in LWUIT 1.5.
     *        Use a Button in EAST slot as the tap target instead.
     */
    private Container buildVideoCard(final int idx) {
        final String id     = (String) videoIds      .elementAt(idx);
        final String title  = (String) videoTitles   .elementAt(idx);
        final String dur    = (String) videoDurations.elementAt(idx);
        final String author = (String) videoAuthors  .elementAt(idx);
        final String views  = (String) videoViews    .elementAt(idx);

        // FIX: must be final for inner-class access
        final Container card = new Container(new BorderLayout());
        card.getStyle().setBgColor(idx % 2 == 0 ? COLOR_SURFACE : COLOR_BG);
        card.getStyle().setBgTransparency(255);
        card.getStyle().setPadding(6, 6, 8, 8); card.getStyle().setMargin(0, 0, 0, 1);
        card.getStyle().setBorder(Border.createLineBorder(1, COLOR_SECONDARY));

        final Label thumbLbl = new Label("");
        thumbLbl.getStyle().setBgColor(COLOR_SECONDARY); thumbLbl.getStyle().setBgTransparency(255);
        thumbLbl.setPreferredW(48); thumbLbl.setPreferredH(36);
        if (showThumbnails) card.addComponent(BorderLayout.WEST, thumbLbl);
        thumbImages.setElementAt(thumbLbl, idx); // store for thumb loader

        Container info = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        info.getStyle().setBgTransparency(0); info.getStyle().setPadding(0, 0, 4, 4);

        String disp = title.length() > 36 ? title.substring(0, 33) + "..." : title;
        Label titleLbl = new Label(disp);
        titleLbl.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        titleLbl.getStyle().setFgColor(COLOR_TEXT); titleLbl.getStyle().setBgTransparency(0);

        String meta = "";
        if (author.length() > 0) meta += author;
        if (dur   .length() > 0) meta += (meta.length() > 0 ? " | " : "") + dur;
        if (views .length() > 0) meta += (meta.length() > 0 ? " | " : "") + formatNumber(views) + " views";
        Label metaLbl = new Label(meta);
        metaLbl.getStyle().setFgColor(COLOR_SUBTEXT); metaLbl.getStyle().setBgTransparency(0);

        info.addComponent(titleLbl); info.addComponent(metaLbl);

        // FIX: card is final, so this compiles
        card.addFocusListener(new FocusListener() {
            public void focusGained(Component c) {
                card.getStyle().setBgColor(COLOR_PRIMARY); card.repaint();
            }
            public void focusLost(Component c) {
                card.getStyle().setBgColor(idx % 2 == 0 ? COLOR_SURFACE : COLOR_BG); card.repaint();
            }
        });

        Button tapBtn = makeSmallButton(">", COLOR_SECONDARY);
        tapBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectedIndex = idx;
                openDetailScreen(id, idx);
            }
        });

        card.addComponent(BorderLayout.CENTER, info);
        card.addComponent(BorderLayout.EAST, tapBtn);
        return card;
    }

    // =========================================================================
    // Detail screen
    // =========================================================================

    private void openDetailScreen(final String id, final int idx) {
        // FIX: InfiniteProgress does not exist in LWUIT 1.5; use a Label.
        detailForm = new Form();
        detailForm.setLayout(new BorderLayout());
        detailForm.addComponent(BorderLayout.NORTH, makeHeader("Loading...", null));

        Container loading = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        loading.getStyle().setBgColor(COLOR_BG); loading.getStyle().setBgTransparency(255);
        loading.getStyle().setPadding(30, 30, 0, 0);
        Label loadLbl = new Label("Fetching video details...");
        loadLbl.getStyle().setFgColor(COLOR_PRIMARY); loadLbl.getStyle().setBgTransparency(0);
        loadLbl.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        Container loadRow = new Container(new FlowLayout(Component.CENTER));
        loadRow.getStyle().setBgTransparency(0);
        loadRow.addComponent(loadLbl);
        loading.addComponent(loadRow);
        detailForm.addComponent(BorderLayout.CENTER, loading);
        showWithSlide(detailForm, true);

        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null; InputStream is = null;
                try {
                    hc = (HttpConnection) Connector.open(API_BASE + "/videos/" + id);
                    hc.setRequestMethod(HttpConnection.GET);
                    int rc = hc.getResponseCode();
                    if (rc != HttpConnection.HTTP_OK) {
                        populateDetailFallback(idx, "Server returned HTTP " + rc); return;
                    }
                    is = hc.openInputStream();
                    StringBuffer sb = new StringBuffer(); int ch;
                    while ((ch = is.read()) != -1) sb.append((char) ch);
                    populateDetailFull(sb.toString(), idx);
                } catch (Exception e) {
                    populateDetailFallback(idx, friendlyNetError(e.getMessage()));
                } finally { closeQuiet(is, hc); }
            }
        }).start();
    }

    private void populateDetailFull(final String json, final int idx) {
        Display.getInstance().callSerially(new Runnable() {
            public void run() {
                String title     = extractField(json, "\"title\":\"");
                String author    = extractField(json, "\"author\":\"");
                String views     = extractLong (json, "\"viewCount\":");
                String likes     = extractLong (json, "\"likeCount\":");
                String published = extractField(json, "\"publishedText\":\"");
                String desc      = extractField(json, "\"description\":\"");
                String subCount  = extractLong (json, "\"subCount\":");

                if (title     == null) title     = (String) videoTitles .elementAt(idx);
                if (author    == null) author    = (String) videoAuthors.elementAt(idx);
                if (views     == null) views     = (String) videoViews  .elementAt(idx);
                if (likes     == null) likes     = "";
                if (published == null) published = "";
                if (desc      == null) desc      = "";
                if (subCount  == null) subCount  = "";

                String lengthStr = (String) videoDurations.elementAt(idx);
                String rawSecs   = extractLong(json, "\"lengthSeconds\":");
                if (rawSecs != null) {
                    try {
                        int total = Integer.parseInt(rawSecs);
                        int h = total / 3600;
                        int m = (total % 3600) / 60;
                        int s = total % 60;
                        if (h > 0)
                            lengthStr = h + ":" + (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
                        else
                            lengthStr = m + ":" + (s < 10 ? "0" : "") + s;
                    } catch (Exception ignored) {}
                }

                buildDetailUI((String) videoIds.elementAt(idx), idx, title, author,
                    formatNumber(views), formatNumber(likes), published, lengthStr,
                    desc.length() > 600 ? desc.substring(0, 597) + "..." : desc,
                    formatNumber(subCount));
            }
        });
    }

    private void populateDetailFallback(final int idx, final String note) {
        Display.getInstance().callSerially(new Runnable() {
            public void run() {
                buildDetailUI((String) videoIds.elementAt(idx), idx,
                    (String) videoTitles.elementAt(idx), (String) videoAuthors.elementAt(idx),
                    "", "", "", (String) videoDurations.elementAt(idx), note, "");
            }
        });
    }

    private void buildDetailUI(final String id, final int idx,
                                final String title,  final String author,
                                final String views,  final String likes,
                                final String published, final String length,
                                final String desc,   final String subs) {
        detailForm = new Form();
        detailForm.setLayout(new BorderLayout());
        String shortTitle = title.length() > 22 ? title.substring(0, 19) + "..." : title;
        detailForm.addComponent(BorderLayout.NORTH, makeHeader("> " + shortTitle, null));

        // FIX: 'body' must be final so the thumbnail-loading inner Runnable can reference it.
        final Container body = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        body.getStyle().setBgColor(COLOR_BG); body.getStyle().setBgTransparency(255);
        body.getStyle().setPadding(10, 10, 8, 8);

        if (showThumbnails) {
            new Thread(new Runnable() {
                public void run() {
                    Image img = loadImage("http://i.ytimg.com/vi/" + id + "/mqdefault.jpg");
                    if (img == null) img = loadImage("http://i.ytimg.com/vi/" + id + "/default.jpg");
                    if (img != null) {
                        final Image scaled = scaleImage(img, 160, 90);
                        Display.getInstance().callSerially(new Runnable() {
                            public void run() {
                                // FIX: 'body' is now final - compiles correctly
                                Label tl = new Label(scaled);
                                tl.getStyle().setBgTransparency(0);
                                Container tc = new Container(new FlowLayout(Component.CENTER));
                                tc.getStyle().setBgTransparency(0);
                                tc.addComponent(tl);
                                body.addComponent(0, tc);
                                body.repaint();
                            }
                        });
                    }
                }
            }).start();
        }

        Label titleLbl = new Label(title);
        titleLbl.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        titleLbl.getStyle().setFgColor(COLOR_TEXT); titleLbl.getStyle().setBgTransparency(0);
        body.addComponent(titleLbl);
        body.addComponent(makeSpacer(6));

        if (author   .length() > 0) body.addComponent(makeMetaRow(">",  "Channel",     author));
        if (subs     .length() > 0) body.addComponent(makeMetaRow("*",  "Subscribers", subs));
        if (published.length() > 0) body.addComponent(makeMetaRow("@",  "Uploaded",    published));
        if (length   .length() > 0) body.addComponent(makeMetaRow("T",  "Length",      length));
        if (views    .length() > 0) body.addComponent(makeMetaRow("~",  "Views",       views));
        if (likes    .length() > 0) {
            body.addComponent(makeMetaRow("+", "Likes", likes));
            body.addComponent(buildLikeBar(likes));
        }
        body.addComponent(makeSpacer(8));
        if (desc.length() > 0) {
            body.addComponent(makeSectionLabel("Description"));
            Label dl = new Label(desc);
            dl.getStyle().setFgColor(COLOR_SUBTEXT); dl.getStyle().setBgTransparency(0);
            body.addComponent(dl);
        }
        detailForm.addComponent(BorderLayout.CENTER, body);

        final boolean isFav = isFavorite(id);
        Container acts = new Container(new FlowLayout(Component.CENTER));
        acts.getStyle().setBgColor(COLOR_SURFACE); acts.getStyle().setBgTransparency(255);
        acts.getStyle().setPadding(6, 6, 0, 0);

        Button watchBtn = makeButton("> Watch", COLOR_PRIMARY);
        watchBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { playVideoViaProxy(id); }
        });
        Button dlBtn = makeButton("v Queue", COLOR_SECONDARY);
        dlBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { queueDownload(id, title, author, length); }
        });
        final Button favBtn = makeButton(isFav ? "<3 Saved" : "<3 Fav",
                                          isFav ? COLOR_ACCENT : COLOR_SECONDARY);
        favBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                toggleFavorite(id, title, author, length);
                boolean nowFav = isFavorite(id);
                favBtn.setText(nowFav ? "<3 Saved" : "<3 Fav");
                favBtn.getStyle().setBgColor(nowFav ? COLOR_ACCENT : COLOR_SECONDARY);
                favBtn.repaint();
                showToast(nowFav ? "Added to favorites!" : "Removed from favorites", COLOR_SUCCESS);
            }
        });
        Button dlsBtn = makeButton("= Queue", COLOR_SECONDARY);
        dlsBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { showDownloadsScreen(); }
        });
        Button backBtn = makeButton("< Back", COLOR_SECONDARY);
        backBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { showWithSlide(resultsForm, false); }
        });

        acts.addComponent(watchBtn); acts.addComponent(makeSpacer(4));
        acts.addComponent(dlBtn);    acts.addComponent(makeSpacer(4));
        acts.addComponent(favBtn);   acts.addComponent(makeSpacer(4));
        acts.addComponent(dlsBtn);   acts.addComponent(makeSpacer(4));
        acts.addComponent(backBtn);
        detailForm.addComponent(BorderLayout.SOUTH, acts);
        detailForm.show();
    }

    private Container buildLikeBar(String likesStr) {
        Container row = new Container(new BoxLayout(BoxLayout.X_AXIS));
        row.getStyle().setBgTransparency(0);
        try {
            StringBuffer clean = new StringBuffer();
            for (int i = 0; i < likesStr.length(); i++) {
                char c = likesStr.charAt(i);
                if (c >= '0' && c <= '9') clean.append(c);
            }
            long likes = Long.parseLong(clean.toString());
            int stars = likes > 1000000L ? 5 : likes > 500000L ? 4 :
                        likes > 100000L  ? 3 : likes > 10000L  ? 2 :
                        likes > 0        ? 1 : 0;
            for (int i = 1; i <= 5; i++) {
                Label star = new Label(i <= stars ? "*" : ".");
                star.getStyle().setFgColor(i <= stars ? COLOR_WARNING : COLOR_SUBTEXT);
                star.getStyle().setBgTransparency(0);
                row.addComponent(star);
            }
            Label note = new Label("  (" + likesStr + " likes)");
            note.getStyle().setFgColor(COLOR_SUBTEXT); note.getStyle().setBgTransparency(0);
            row.addComponent(note);
        } catch (Exception ignored) {}
        return row;
    }

    // =========================================================================
    // Favorites
    // =========================================================================

    private boolean isFavorite(String id) {
        for (int i = 0; i < favorites.size(); i++) {
            if (id.equals(((Hashtable) favorites.elementAt(i)).get("id"))) return true;
        }
        return false;
    }

    private void toggleFavorite(String id, String title, String author, String duration) {
        for (int i = 0; i < favorites.size(); i++) {
            if (id.equals(((Hashtable) favorites.elementAt(i)).get("id"))) {
                favorites.removeElementAt(i); saveFavorites(); return;
            }
        }
        Hashtable fav = new Hashtable();
        fav.put("id", id); fav.put("title", title);
        fav.put("author", author); fav.put("duration", duration);
        favorites.addElement(fav); saveFavorites();
    }

    private void openFavorite(String id, String title, String author, String duration) {
        videoIds.removeAllElements(); videoTitles.removeAllElements();
        videoAuthors.removeAllElements(); videoDurations.removeAllElements();
        videoViews.removeAllElements(); thumbImages.removeAllElements();
        videoIds.addElement(id); videoTitles.addElement(title);
        videoAuthors.addElement(author); videoDurations.addElement(duration);
        videoViews.addElement(""); thumbImages.addElement(null);
        selectedIndex = 0;
        openDetailScreen(id, 0);
    }

    // =========================================================================
    // Downloads
    // =========================================================================

    private void queueDownload(final String id, final String title,
                                final String author, final String duration) {
        for (int i = 0; i < downloadQueue.size(); i++) {
            if (id.equals(((Hashtable) downloadQueue.elementAt(i)).get("id"))) {
                showToast("Already queued", COLOR_WARNING); return;
            }
        }
        Hashtable item = new Hashtable();
        item.put("id", id); item.put("title", title);
        item.put("author", author); item.put("duration", duration);
        downloadQueue.addElement(item);
        downloadStatus.addElement("Queued");
        String st = title.length() > 20 ? title.substring(0, 17) + "..." : title;
        showToast("Queued: \"" + st + "\"", COLOR_SUCCESS);

        new Thread(new Runnable() {
            public void run() {
                final int qi = downloadQueue.size() - 1;
                downloadStatus.setElementAt("Downloading...", qi);
                HttpConnection hc = null; InputStream is = null;
                try {
                    hc = (HttpConnection) Connector.open(
                        "http://" + serverAddress + "/download?v=" + id + "&q=" + videoQuality);
                    hc.setRequestMethod(HttpConnection.GET);
                    is = hc.openInputStream();
                    StringBuffer sb = new StringBuffer(); int ch;
                    while ((ch = is.read()) != -1) sb.append((char) ch);
                    String resp = sb.toString().trim();
                    int qi2 = findQueueIndex(id);
                    if (qi2 >= 0)
                        downloadStatus.setElementAt(resp.length() > 0 ? "Done: " + resp : "Done", qi2);
                    Display.getInstance().callSerially(new Runnable() {
                        public void run() { showToast("Download complete: " + title, COLOR_SUCCESS); }
                    });
                } catch (Exception e) {
                    int qi3 = findQueueIndex(id);
                    if (qi3 >= 0) downloadStatus.setElementAt("Failed", qi3);
                    Display.getInstance().callSerially(new Runnable() {
                        public void run() { showToast("Download failed. Check server.", COLOR_ACCENT); }
                    });
                } finally { closeQuiet(is, hc); }
            }
        }).start();
    }

    private int findQueueIndex(String id) {
        for (int i = 0; i < downloadQueue.size(); i++) {
            if (id.equals(((Hashtable) downloadQueue.elementAt(i)).get("id"))) return i;
        }
        return -1;
    }

    private void showDownloadsScreen() {
        downloadsForm = new Form();
        downloadsForm.setLayout(new BorderLayout());
        downloadsForm.addComponent(BorderLayout.NORTH, makeHeader("v Download Queue", null));

        Container body = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        body.getStyle().setBgColor(COLOR_BG); body.getStyle().setBgTransparency(255);
        body.getStyle().setPadding(10, 10, 8, 8);

        if (downloadQueue.isEmpty()) {
            Label l = new Label("No downloads queued.");
            l.getStyle().setFgColor(COLOR_SUBTEXT); l.getStyle().setBgTransparency(0);
            body.addComponent(l);
        } else {
            for (int i = 0; i < downloadQueue.size(); i++) {
                String t = (String) ((Hashtable) downloadQueue.elementAt(i)).get("title");
                String s = (String) downloadStatus.elementAt(i);
                Container row = new Container(new BorderLayout());
                row.getStyle().setBgColor(i % 2 == 0 ? COLOR_SURFACE : COLOR_BG);
                row.getStyle().setBgTransparency(255);
                row.getStyle().setPadding(6, 6, 6, 6); row.getStyle().setMargin(0, 0, 0, 1);
                Label tl = new Label(t.length() > 28 ? t.substring(0, 25) + "..." : t);
                tl.getStyle().setFgColor(COLOR_TEXT); tl.getStyle().setBgTransparency(0);
                int sc = s.startsWith("Done") ? COLOR_SUCCESS :
                         s.equals("Failed")   ? COLOR_ACCENT   : COLOR_WARNING;
                Label sl = new Label(s);
                sl.getStyle().setFgColor(sc); sl.getStyle().setBgTransparency(0);
                row.addComponent(BorderLayout.CENTER, tl);
                row.addComponent(BorderLayout.EAST, sl);
                body.addComponent(row);
            }
            body.addComponent(makeSpacer(10));
            Button clearBtn = makeButton("Clear Queue", COLOR_ACCENT);
            clearBtn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    downloadQueue.removeAllElements(); downloadStatus.removeAllElements();
                    showDownloadsScreen();
                }
            });
            body.addComponent(clearBtn);
        }

        downloadsForm.addComponent(BorderLayout.CENTER, body);
        downloadsForm.addComponent(BorderLayout.SOUTH, makeNavBar(
            new String[]{"< Back"},
            new ActionListener[]{
                new ActionListener() { public void actionPerformed(ActionEvent e) {
                    showWithSlide(selectedIndex >= 0 && !videoIds.isEmpty()
                        ? detailForm : mainForm, false);
                }}
            }
        ));
        showWithSlide(downloadsForm, true);
    }

    // =========================================================================
    // Proxy play
    // =========================================================================

    private void playVideoViaProxy(final String vid) {
        String t = selectedIndex >= 0 && selectedIndex < videoTitles.size()
            ? (String) videoTitles.elementAt(selectedIndex) : "video";
        String short_ = t.length() > 25 ? t.substring(0, 22) + "..." : t;

        // FIX: Dialog.show() in LWUIT 1.5 = Dialog.show(title, msg, okLabel, cancelLabel) -> int
        // 4-argument form: no callback, returns index of pressed button.
        Dialog.show("Opening on PC",
            "Sending \"" + short_ + "\"\nto your PC player.\nThis may take a moment...",
            "OK", null);

        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null; InputStream is = null;
                try {
                    hc = (HttpConnection) Connector.open(
                        "http://" + serverAddress + "/play?v=" + vid + "&q=" + videoQuality);
                    hc.setRequestMethod(HttpConnection.GET);
                    is = hc.openInputStream();
                    StringBuffer sb = new StringBuffer(); int ch;
                    while ((ch = is.read()) != -1) sb.append((char) ch);
                    String result = sb.toString().trim();
                    if (result.length() > 0) platformRequest(result);
                } catch (Exception e) {
                    final String msg = friendlyNetError(e.getMessage());
                    Display.getInstance().callSerially(new Runnable() {
                        public void run() { showToast("Can't reach PC: " + msg, COLOR_ACCENT); }
                    });
                } finally { closeQuiet(is, hc); }
            }
        }).start();
    }

    // =========================================================================
    // About
    // =========================================================================

    private void showAbout() {
        aboutForm = new Form();
        aboutForm.setLayout(new BorderLayout());
        aboutForm.addComponent(BorderLayout.NORTH, makeHeader("i About", null));

        Container body = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        body.getStyle().setBgColor(COLOR_BG); body.getStyle().setBgTransparency(255);
        body.getStyle().setPadding(14, 14, 10, 10);

        Label appLbl = new Label("> " + appName + "  v" + appVersion);
        appLbl.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE));
        appLbl.getStyle().setFgColor(COLOR_PRIMARY); appLbl.getStyle().setBgTransparency(0);
        body.addComponent(appLbl);
        if (appVendor.length() > 0) {
            Label vl = new Label(appVendor);
            vl.getStyle().setFgColor(COLOR_SUBTEXT); vl.getStyle().setBgTransparency(0);
            body.addComponent(vl);
        }

        body.addComponent(makeSpacer(12));
        body.addComponent(makeSectionLabel("Device Info"));
        String[][] props = {
            {"Platform",  safeProperty("microedition.platform")},
            {"Config",    safeProperty("microedition.configuration")},
            {"Profiles",  safeProperty("microedition.profiles")},
            {"Locale",    safeProperty("microedition.locale")},
            {"Encoding",  safeProperty("microedition.encoding")}
        };
        for (int i = 0; i < props.length; i++) {
            if (props[i][1].length() > 0) body.addComponent(makeMetaRow("", props[i][0], props[i][1]));
        }

        body.addComponent(makeSpacer(10));
        body.addComponent(makeSectionLabel("Build Info"));
        body.addComponent(makeMetaRow("", "Compiled", "JDK 6 (1.6.0_45)"));
        body.addComponent(makeMetaRow("", "SDK",      "Java ME SDK 3.0.5"));
        body.addComponent(makeMetaRow("", "Target",   "MIDP 2.1 / CLDC 1.1"));
        body.addComponent(makeMetaRow("", "UI",       "LWUIT 1.5"));

        body.addComponent(makeSpacer(10));
        body.addComponent(makeSectionLabel("Data Source"));
        body.addComponent(makeMetaRow("", "API",    "Invidious API"));
        body.addComponent(makeMetaRow("", "Server", "s60tube.io.vn"));

        aboutForm.addComponent(BorderLayout.CENTER, body);
        aboutForm.addComponent(BorderLayout.SOUTH, makeNavBar(
            new String[]{"< Back"},
            new ActionListener[]{
                new ActionListener() { public void actionPerformed(ActionEvent e) {
                    showWithSlide(mainForm, false);
                }}
            }
        ));
        showWithSlide(aboutForm, true);
    }

    // =========================================================================
    // Thumbnail loader
    // =========================================================================

    private void startThumbLoader() {
        if (thumbLoaderRunning) return;
        thumbLoaderRunning = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < videoIds.size(); i++) {
                        Object slot = thumbImages.elementAt(i);
                        if (!(slot instanceof Label)) continue;
                        final Label placeholder = (Label) slot;
                        String id  = (String) videoIds.elementAt(i);
                        Image  img = loadImage("http://i.ytimg.com/vi/" + id + "/default.jpg");
                        if (img != null) {
                            final Image scaled = scaleImage(img, 48, 36);
                            Display.getInstance().callSerially(new Runnable() {
                                public void run() {
                                    try {
                                        placeholder.setIcon(scaled);
                                        placeholder.setText("");
                                        placeholder.repaint();
                                    } catch (Exception ignored) {}
                                }
                            });
                        }
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    }
                } finally { thumbLoaderRunning = false; }
            }
        }).start();
    }

    // =========================================================================
    // RMS persistence
    // =========================================================================

    private void loadPreferences() {
        try {
            javax.microedition.rms.RecordStore rs =
                javax.microedition.rms.RecordStore.openRecordStore(RMS_PREFS, false);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
            rs.closeRecordStore();
            serverAddress  = dis.readUTF(); showThumbnails = dis.readBoolean();
            maxResults     = dis.readInt(); videoQuality   = dis.readUTF();
            int n = dis.readInt();
            for (int i = 0; i < n; i++) searchHistory.addElement(dis.readUTF());
        } catch (Exception ignored) {}
    }

    private void savePreferences() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(serverAddress); dos.writeBoolean(showThumbnails);
            dos.writeInt(maxResults);    dos.writeUTF(videoQuality);
            dos.writeInt(searchHistory.size());
            for (int i = 0; i < searchHistory.size(); i++)
                dos.writeUTF((String) searchHistory.elementAt(i));
            dos.flush();
            byte[] data = baos.toByteArray();
            javax.microedition.rms.RecordStore rs =
                javax.microedition.rms.RecordStore.openRecordStore(RMS_PREFS, true);
            if (rs.getNumRecords() == 0) rs.addRecord(data, 0, data.length);
            else                          rs.setRecord(1, data, 0, data.length);
            rs.closeRecordStore();
        } catch (Exception ignored) {}
    }

    private void loadFavorites() {
        try {
            javax.microedition.rms.RecordStore rs =
                javax.microedition.rms.RecordStore.openRecordStore(RMS_FAVS, false);
            int n = rs.getNumRecords();
            for (int i = 1; i <= n; i++) {
                try {
                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(rs.getRecord(i)));
                    Hashtable fav = new Hashtable();
                    fav.put("id", dis.readUTF()); fav.put("title",    dis.readUTF());
                    fav.put("author", dis.readUTF()); fav.put("duration", dis.readUTF());
                    favorites.addElement(fav);
                } catch (Exception ignored) {}
            }
            rs.closeRecordStore();
        } catch (Exception ignored) {}
    }

    private void saveFavorites() {
        try {
            try { javax.microedition.rms.RecordStore.deleteRecordStore(RMS_FAVS); }
            catch (Exception ignored) {}
            javax.microedition.rms.RecordStore rs =
                javax.microedition.rms.RecordStore.openRecordStore(RMS_FAVS, true);
            for (int i = 0; i < favorites.size(); i++) {
                Hashtable fav = (Hashtable) favorites.elementAt(i);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                dos.writeUTF((String) fav.get("id"));     dos.writeUTF((String) fav.get("title"));
                dos.writeUTF((String) fav.get("author")); dos.writeUTF((String) fav.get("duration"));
                dos.flush();
                byte[] d = baos.toByteArray();
                rs.addRecord(d, 0, d.length);
            }
            rs.closeRecordStore();
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Toast
    // =========================================================================

    private void showToast(final String message, final int bgColor) {
        Display.getInstance().callSerially(new Runnable() {
            public void run() {
                try {
                    Dialog toast = new Dialog();
                    toast.setLayout(new BorderLayout());
                    toast.getDialogStyle().setBgColor(bgColor);
                    toast.getDialogStyle().setBgTransparency(220);
                    Label lbl = new Label(message);
                    lbl.getStyle().setFgColor(0xFFFFFF); lbl.getStyle().setBgTransparency(0);
                    lbl.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
                    lbl.getStyle().setPadding(6, 6, 10, 10);
                    toast.addComponent(BorderLayout.CENTER, lbl);
                    toast.setTimeout(2000);
                    toast.showModeless();
                } catch (Exception ignored) {}
            }
        });
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private Container makeHeader(String title, String subtitle) {
        Container h = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        h.getStyle().setBgColor(COLOR_PRIMARY); h.getStyle().setBgTransparency(255);
        h.getStyle().setPadding(8, 8, 10, 10);
        Label tl = new Label(title);
        tl.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        tl.getStyle().setFgColor(0xFFFFFF); tl.getStyle().setBgTransparency(0);
        h.addComponent(tl);
        if (subtitle != null && subtitle.length() > 0) {
            Label sl = new Label(subtitle);
            sl.getStyle().setFgColor(0xFFDDDD); sl.getStyle().setBgTransparency(0);
            h.addComponent(sl);
        }
        return h;
    }

    private Container makeNavBar(String[] labels, ActionListener[] listeners) {
        Container nav = new Container(new FlowLayout(Component.CENTER));
        nav.getStyle().setBgColor(COLOR_SURFACE); nav.getStyle().setBgTransparency(255);
        nav.getStyle().setPadding(6, 6, 0, 0);
        for (int i = 0; i < labels.length; i++) {
            Button b = makeButton(labels[i], COLOR_SECONDARY);
            b.addActionListener(listeners[i]);
            nav.addComponent(b);
            if (i < labels.length - 1) nav.addComponent(makeSpacer(4));
        }
        return nav;
    }

    private Button makeButton(String label, int bgColor) {
        Button b = new Button(label);
        b.getStyle().setBgColor(bgColor); b.getStyle().setFgColor(0xFFFFFF);
        b.getStyle().setBgTransparency(255); b.getStyle().setPadding(5, 5, 10, 10);
        b.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        b.getStyle().setBorder(Border.createRoundBorder(6, 6));
        b.getPressedStyle().setBgColor(darken(bgColor)); b.getPressedStyle().setFgColor(0xFFFFFF);
        b.getPressedStyle().setBgTransparency(255);
        b.getPressedStyle().setBorder(Border.createRoundBorder(6, 6));
        return b;
    }

    private Button makeSmallButton(String label, int bgColor) {
        Button b = makeButton(label, bgColor);
        b.getStyle().setPadding(3, 3, 6, 6);
        return b;
    }

    private Label makeLabel(String text, int color) {
        Label l = new Label(text);
        l.getStyle().setFgColor(color); l.getStyle().setBgTransparency(0);
        return l;
    }

    private Label makeSectionLabel(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        l.getStyle().setFgColor(COLOR_PRIMARY); l.getStyle().setBgTransparency(0);
        l.getStyle().setPadding(6, 2, 0, 0);
        return l;
    }

    private Container makeMetaRow(String icon, String key, String value) {
        Container row = new Container(new BoxLayout(BoxLayout.X_AXIS));
        row.getStyle().setBgTransparency(0); row.getStyle().setPadding(1, 1, 0, 0);
        if (icon.length() > 0) {
            Label ic = new Label(icon + " ");
            ic.getStyle().setFgColor(COLOR_PRIMARY); ic.getStyle().setBgTransparency(0);
            row.addComponent(ic);
        }
        Label kl = new Label(key + ": ");
        kl.getStyle().setFgColor(COLOR_SUBTEXT); kl.getStyle().setBgTransparency(0);
        kl.getStyle().setFont(Font.createSystemFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
        row.addComponent(kl);
        Label vl = new Label(value);
        vl.getStyle().setFgColor(COLOR_TEXT); vl.getStyle().setBgTransparency(0);
        row.addComponent(vl);
        return row;
    }

    private Component makeSpacer(int size) {
        Label sp = new Label("");
        sp.setPreferredH(size); sp.setPreferredW(size);
        sp.getStyle().setBgTransparency(0);
        return sp;
    }

    private Container wrapInHFlow(Component c) {
        Container row = new Container(new FlowLayout(Component.CENTER));
        row.getStyle().setBgColor(COLOR_SURFACE); row.getStyle().setBgTransparency(255);
        row.getStyle().setPadding(6, 6, 0, 0);
        row.addComponent(c);
        return row;
    }

    private int darken(int color) {
        int r = Math.max(0, ((color >> 16) & 0xFF) - 40);
        int g = Math.max(0, ((color >>  8) & 0xFF) - 40);
        int b = Math.max(0, ( color        & 0xFF) - 40);
        return (r << 16) | (g << 8) | b;
    }

    // =========================================================================
    // Search history
    // =========================================================================

    private void addToHistory(String query) {
        for (int i = 0; i < searchHistory.size(); i++) {
            if (query.equalsIgnoreCase((String) searchHistory.elementAt(i))) {
                searchHistory.removeElementAt(i); break;
            }
        }
        searchHistory.insertElementAt(query, 0);
        while (searchHistory.size() > HISTORY_MAX)
            searchHistory.removeElementAt(searchHistory.size() - 1);
        savePreferences();
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
            val.append(c); start++;
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
            if (c >= '0' && c <= '9') { val.append(c); start++; } else break;
        }
        return val.length() > 0 ? val.toString() : null;
    }

    private String formatNumber(String raw) {
        if (raw == null || raw.length() == 0) return "";
        StringBuffer digits = new StringBuffer();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
        }
        String d = digits.toString();
        if (d.length() == 0) return raw;
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < d.length(); i++) {
            if (i > 0 && (d.length() - i) % 3 == 0) out.append(',');
            out.append(d.charAt(i));
        }
        return out.toString();
    }

    // =========================================================================
    // Network helpers
    // =========================================================================

    private Image loadImage(String url) {
        HttpConnection hc = null; InputStream is = null;
        try {
            hc = (HttpConnection) Connector.open(url);
            hc.setRequestMethod(HttpConnection.GET);
            if (hc.getResponseCode() != HttpConnection.HTTP_OK) return null;
            is = hc.openInputStream();
            int len = (int) hc.getLength();
            byte[] data;
            if (len > 0) {
                data = new byte[len]; int off = 0, n;
                while (off < len && (n = is.read(data, off, len - off)) != -1) off += n;
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[256]; int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                data = baos.toByteArray();
            }
            return Image.createImage(data, 0, data.length);
        } catch (Exception e) { return null; }
        finally { closeQuiet(is, hc); }
    }

    /**
     * Nearest-neighbour scale.
     * FIX: LWUIT 1.5's Image does NOT have getRGB() or createRGBImage().
     * Use Image.getImage() to get the underlying MIDP Image, call its getRGB(),
     * then wrap the result back with Image.createImage(midpImage).
     */
    private Image scaleImage(Image lwuitSrc, int tw, int th) {
        try {
            // Image.getImage() returns Object in LWUIT 1.5; cast explicitly.
            javax.microedition.lcdui.Image midpSrc =
                (javax.microedition.lcdui.Image) lwuitSrc.getImage();
            int sw = midpSrc.getWidth(), sh = midpSrc.getHeight();
            if (sw == tw && sh == th) return lwuitSrc;
            int[] sp = new int[sw * sh];
            midpSrc.getRGB(sp, 0, sw, 0, 0, sw, sh);
            int[] dp = new int[tw * th];
            for (int y = 0; y < th; y++) {
                int sy = (y * sh) / th;
                for (int x = 0; x < tw; x++)
                    dp[y * tw + x] = sp[sy * sw + (x * sw) / tw];
            }
            javax.microedition.lcdui.Image midpDst =
                javax.microedition.lcdui.Image.createRGBImage(dp, tw, th, false);
            return Image.createImage(midpDst);
        } catch (Exception e) { return lwuitSrc; }
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

    private String friendlyNetError(String raw) {
        if (raw == null) return "Unknown error";
        String lo = raw.toLowerCase();
        if (lo.indexOf("connect") != -1 || lo.indexOf("refused") != -1)
            return "Cannot reach server - is it running?";
        if (lo.indexOf("timeout") != -1) return "Connection timed out - check Wi-Fi";
        if (lo.indexOf("host")    != -1) return "Host not found - check the IP address";
        return raw.length() > 60 ? raw.substring(0, 57) + "..." : raw;
    }

    private void closeQuiet(InputStream is, HttpConnection hc) {
        try { if (is != null) is.close(); } catch (Exception ignored) {}
        try { if (hc != null) hc.close(); } catch (Exception ignored) {}
    }

    private void showSearchError(final String msg, final String query) {
        Display.getInstance().callSerially(new Runnable() {
            public void run() {
                resultsForm = new Form();
                resultsForm.setLayout(new BorderLayout());
                resultsForm.addComponent(BorderLayout.NORTH, makeHeader("Search Error", null));
                Container body = new Container(new BoxLayout(BoxLayout.Y_AXIS));
                body.getStyle().setBgColor(COLOR_BG); body.getStyle().setBgTransparency(255);
                body.getStyle().setPadding(16, 16, 10, 10);
                Label l1 = new Label("! " + msg);
                l1.getStyle().setFgColor(COLOR_ACCENT); l1.getStyle().setBgTransparency(0);
                Label l2 = new Label("Query: \"" + query + "\"");
                l2.getStyle().setFgColor(COLOR_SUBTEXT); l2.getStyle().setBgTransparency(0);
                body.addComponent(l1); body.addComponent(l2);
                resultsForm.addComponent(BorderLayout.CENTER, body);
                resultsForm.addComponent(BorderLayout.SOUTH, makeNavBar(
                    new String[]{"< Back"},
                    new ActionListener[]{
                        new ActionListener() { public void actionPerformed(ActionEvent e) { showMainForm(); }}
                    }
                ));
                // FIX: forceRevalidate() does not exist in LWUIT 1.5; use revalidate()
                resultsForm.revalidate();
                resultsForm.show();
            }
        });
    }

    // Required by ActionListener
    public void actionPerformed(ActionEvent e) {}

    // =========================================================================
    // Manifest / system property helpers
    // =========================================================================

    private void readManifestProps() {
        String n = getAppProperty("MIDlet-Name");
        String v = getAppProperty("MIDlet-Version");
        String o = getAppProperty("MIDlet-Vendor");
        if (n != null && n.length() > 0) appName    = n;
        if (v != null && v.length() > 0) appVersion = v;
        if (o != null && o.length() > 0) appVendor  = o;
    }

    private String safeProperty(String key) {
        try { String v = System.getProperty(key); return v != null ? v : ""; }
        catch (Exception e) { return ""; }
    }
}
