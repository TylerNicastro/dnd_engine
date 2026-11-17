import javax.swing.SwingUtilities;
import java.util.function.Consumer;

// Browser file download / upload for the web build.
//
// The two native methods below are implemented in JavaScript by web/index.html and registered with
// cheerpjInit({natives: {...}}) (CheerpJ's "JavaScript natives"). They exist only in the browser:
// call them only when DataPaths.web is true. On the desktop the app uses in-window file overlays instead.
class WebBridge {

// Starts a browser download of `text` saved as `fileName`
static native void downloadFile(String fileName, String text);

// Opens the browser's file picker. Returns the chosen file's text, "" if the picker was cancelled,
// or TOO_LARGE. Blocks until the user chooses, so call it through pickTextFileAsync().
static native String pickTextFile();

static final String TOO_LARGE = "\u0000too-large";

// Runs the picker off the Swing thread and hands the result (null if the browser refused) back on it
static void pickTextFileAsync(Consumer<String> onResult) {
    Thread t = new Thread(() -> {
        String result;
        try {
            result = pickTextFile();
        } catch (Throwable ex) {
            result = null;
        }
        String r = result;
        SwingUtilities.invokeLater(() -> onResult.accept(r));
    }, "browser-file-picker");
    t.setDaemon(true);
    t.start();
}
}
