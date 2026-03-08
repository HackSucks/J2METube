import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Vector;

public class J2METube extends MIDlet implements CommandListener {

    private Display display;

    private Form   configForm;
    private Form   searchForm;
    private Form   detailForm;
    private Form   aboutForm;
    private List   resultsList;

    private TextField ipField;
    private TextField searchField;

    private Command nextCmd, searchCmd, exitCmd, backCmd;
    private Command watchCmd, backToResultsCmd, aboutCmd, backFromAboutCmd;

    private Vector videoIds     = new Vector();
    private Vector videoTitles  = new Vector();
    private Vector videoAuthors = new Vector();
    private Vector videoViews   = new Vector();
    private Vector videoDescs   = new Vector();
    private Vector videoLengths = new Vector();
    private Vector thumbImages  = new Vector();

    private String serverAddress = "";
    private int    selectedIndex = -1;

    private volatile boolean thumbLoaderRunning = false;

    public J2METube() {
        display = Display.getDisplay(this);
        initUI();
    }

    private void initUI() {
        exitCmd          = new Command("Exit",    Command.EXIT, 2);
        backCmd          = new Command("Back",    Command.BACK, 1);
        backToResultsCmd = new Command("Back",    Command.BACK, 1);
        backFromAboutCmd = new Command("Back",    Command.BACK, 1);
        watchCmd         = new Command("Watch",   Command.OK,   1);
        aboutCmd         = new Command("About",   Command.ITEM, 3);

        configForm = new Form("Server Config");
        ipField    = new TextField("PC IP & Port:", "192.168.1.X:5000", 100, TextField.ANY);
        nextCmd    = new Command("Next", Command.OK, 1);
        configForm.append(ipField);
        configForm.addCommand(nextCmd);
        configForm.addCommand(aboutCmd);
        configForm.addCommand(exitCmd);
        configForm.setCommandListener(this);

        searchForm  = new Form("J2METube Search");
        searchField = new TextField("Query:", "dankpods", 50, TextField.ANY);
        searchCmd   = new Command("Search", Command.OK, 1);
        searchForm.append(searchField);
        searchForm.addCommand(searchCmd);
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

        aboutForm = new Form("About J2METube");
        aboutForm.append(
            "J2METube\n\n" +
            "Compiled with:\n" +
            "Java JDK 6\n" +
            "(1.6.0_45 amd64)\n\n" +
            "With Java ME SDK 3.0.5\n\n" +
            "Target:\n" +
            "MIDP 2.1 / CLDC 1.1\n\n" +
            "Uses Invidious API\n" +
            "via s60tube.io.vn"
        );
        aboutForm.addCommand(backFromAboutCmd);
        aboutForm.addCommand(exitCmd);
        aboutForm.setCommandListener(this);
    }

    public void startApp() {
        display.setCurrent(configForm);
    }

    public void commandAction(Command c, Displayable d) {

        if (c == exitCmd) {
            notifyDestroyed();

        } else if (c == aboutCmd) {
            display.setCurrent(aboutForm);

        } else if (c == backFromAboutCmd) {
            display.setCurrent(configForm);

        } else if (c == nextCmd && d == configForm) {
            serverAddress = ipField.getString().trim();
            display.setCurrent(searchForm);

        } else if (c == backCmd && d == searchForm) {
            display.setCurrent(configForm);

        } else if (c == searchCmd && d == searchForm) {
            performSearch(searchField.getString());

        } else if (c == backCmd && d == resultsList) {
            display.setCurrent(searchForm);

        } else if (d == resultsList && c == List.SELECT_COMMAND) {
            int idx = resultsList.getSelectedIndex();
            if (idx >= 0) {
                selectedIndex = idx;
                showDetail(idx);
            }

        } else if (c == backToResultsCmd && d == detailForm) {
            display.setCurrent(resultsList);

        } else if (c == watchCmd && d == detailForm) {
            if (selectedIndex >= 0) {
                playVideoViaProxy((String) videoIds.elementAt(selectedIndex));
            }
        }
    }

    private void showDetail(int idx) {
        detailForm.deleteAll();

        String title  = (String) videoTitles .elementAt(idx);
        String author = (String) videoAuthors.elementAt(idx);
        String views  = (String) videoViews  .elementAt(idx);
        String length = (String) videoLengths.elementAt(idx);
        String desc   = (String) videoDescs  .elementAt(idx);
        Image  thumb  = (Image)  thumbImages  .elementAt(idx);

        if (thumb != null) {
            detailForm.append(thumb);
        }

        StringItem titleItem = new StringItem(null, title);
        titleItem.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        detailForm.append(titleItem);
        detailForm.append("\n");

        detailForm.append(new StringItem("Channel: ", author));
        detailForm.append(new StringItem("Views: ",   views));
        if (length != null && length.length() > 0)
            detailForm.append(new StringItem("Length: ", length));
        detailForm.append("\n");

        if (desc != null && desc.length() > 0) {
            String s = desc.length() > 280 ? desc.substring(0, 277) + "..." : desc;
            detailForm.append(new StringItem("About:\n", s));
        }

        detailForm.setTitle(title.length() > 20 ? title.substring(0, 17) + "..." : title);
        display.setCurrent(detailForm);
    }

    private void performSearch(final String query) {
        resultsList.deleteAll();
        videoIds    .removeAllElements();
        videoTitles .removeAllElements();
        videoAuthors.removeAllElements();
        videoViews  .removeAllElements();
        videoDescs  .removeAllElements();
        videoLengths.removeAllElements();
        thumbImages .removeAllElements();

        resultsList.setTitle("Searching...");
        display.setCurrent(resultsList);

        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null;
                InputStream    is = null;
                try {
                    String url = "http://s60tube.io.vn/api/v1/search?q=" + urlEncode(query);
                    hc = (HttpConnection) Connector.open(url);
                    is = hc.openInputStream();
                    StringBuffer sb = new StringBuffer();
                    int ch;
                    while ((ch = is.read()) != -1) sb.append((char) ch);
                    parseAndPopulate(sb.toString());
                } catch (Exception e) {
                    errorMsg("Net Error: " + e.getMessage());
                } finally {
                    closeQuiet(is, hc);
                }
            }
        }).start();
    }

    private void parseAndPopulate(String json) {
        int pos   = 0;
        int count = 0;

        while ((pos = json.indexOf("\"type\":\"video\"", pos)) != -1) {
            try {
                int blockEnd = Math.min(pos + 2000, json.length());
                String block = json.substring(pos, blockEnd);

                String title  = extractField(block, "\"title\":\"");
                String id     = extractField(block, "\"videoId\":\"");
                String author = extractField(block, "\"author\":\"");
                String views  = extractField(block, "\"viewCountText\":\"");
                String length = extractField(block, "\"lengthText\":\"");
                String desc   = extractField(block, "\"description\":\"");

                if (id == null || id.length() == 0) { pos += 15; continue; }
                if (title  == null) title  = "(no title)";
                if (author == null) author = "";
                if (views  == null) views  = "";
                if (length == null) length = "";
                if (desc   == null) desc   = "";

                resultsList.append(title, null);
                videoIds    .addElement(id);
                videoTitles .addElement(title);
                videoAuthors.addElement(author);
                videoViews  .addElement(views);
                videoLengths.addElement(length);
                videoDescs  .addElement(desc);
                thumbImages .addElement(null);

                count++;
            } catch (Exception e) {}
            pos += 15;
        }

        if (count == 0) {
            resultsList.append("No results found.", null);
        }
        resultsList.setTitle("Results (" + count + ")");

        startThumbLoader();
    }

    private void startThumbLoader() {
        if (thumbLoaderRunning) return;
        thumbLoaderRunning = true;

        new Thread(new Runnable() {
            public void run() {
                try {
                    for (int i = 0; i < videoIds.size(); i++) {
                        if (thumbImages.elementAt(i) != null) continue;

                        String id  = (String) videoIds.elementAt(i);
                        String url = "http://i.ytimg.com/vi/" + id + "/default.jpg";

                        Image img = loadImage(url);
                        if (img != null) {
                            img = scaleImage(img, 48, 36);
                            thumbImages.setElementAt(img, i);
                            try {
                                resultsList.set(i,
                                    (String) videoTitles.elementAt(i), img);
                            } catch (Exception ex) {}
                        }

                        try { Thread.sleep(100); } catch (InterruptedException ie) {}
                    }
                } finally {
                    thumbLoaderRunning = false;
                }
            }
        }).start();
    }

    private String extractField(String block, String key) {
        int start = block.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        StringBuffer val = new StringBuffer();
        while (start < block.length()) {
            char c = block.charAt(start);
            if (c == '"') break;
            if (c == '\\' && start + 1 < block.length()) {
                char next = block.charAt(start + 1);
                if      (next == '"')  { val.append('"');  start += 2; continue; }
                else if (next == 'n')  { val.append('\n'); start += 2; continue; }
                else if (next == '\\') { val.append('\\'); start += 2; continue; }
            }
            val.append(c);
            start++;
        }
        return val.toString();
    }

    private Image loadImage(String url) {
        HttpConnection hc = null;
        InputStream    is = null;
        try {
            hc = (HttpConnection) Connector.open(url);
            hc.setRequestMethod(HttpConnection.GET);
            if (hc.getResponseCode() != HttpConnection.HTTP_OK) return null;
            is = hc.openInputStream();

            int    len  = (int) hc.getLength();
            byte[] data;

            if (len > 0) {
                data = new byte[len];
                int off = 0, n;
                while (off < len && (n = is.read(data, off, len - off)) != -1)
                    off += n;
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

    private void playVideoViaProxy(final String vid) {
        Alert a = new Alert("Connecting", "Waking up PC server,\nplease wait...",
                            null, AlertType.INFO);
        a.setTimeout(4000);
        display.setCurrent(a, detailForm);

        new Thread(new Runnable() {
            public void run() {
                HttpConnection hc = null;
                InputStream    is = null;
                try {
                    String url = "http://" + serverAddress + "/play?v=" + vid;
                    hc = (HttpConnection) Connector.open(url);
                    is = hc.openInputStream();
                    StringBuffer sb = new StringBuffer();
                    int ch;
                    while ((ch = is.read()) != -1) sb.append((char) ch);
                    platformRequest(sb.toString().trim());
                } catch (Exception e) {
                    errorMsg("Proxy Error: " + e.getMessage());
                } finally {
                    closeQuiet(is, hc);
                }
            }
        }).start();
    }

    private String urlEncode(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == ' ')  sb.append('+');
            else if (c == '&')  sb.append("%26");
            else if (c == '=')  sb.append("%3D");
            else if (c == '+')  sb.append("%2B");
            else                sb.append(c);
        }
        return sb.toString();
    }

    private void errorMsg(String msg) {
        Alert a = new Alert("Error", msg, null, AlertType.ERROR);
        a.setTimeout(Alert.FOREVER);
        display.setCurrent(a, searchForm);
    }

    private void closeQuiet(InputStream is, HttpConnection hc) {
        try { if (is != null) is.close(); } catch (Exception e) {}
        try { if (hc != null) hc.close(); } catch (Exception e) {}
    }

    public void pauseApp() {}
    public void destroyApp(boolean unconditional) {}
}
