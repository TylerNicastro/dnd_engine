import javax.swing.SwingUtilities;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Browser file download / upload and clipboard access for the web build.
//
// The native methods below are implemented in JavaScript by web/index.html and registered with
// cheerpjInit({natives: {...}}) (CheerpJ's "JavaScript natives"). They exist only in the browser:
// call them only when DataPaths.web is true. On the desktop the app uses in-window file overlays instead.
class WebBridge {

// Starts a browser download of `text` saved as `fileName`
static native void downloadFile(String fileName, String text);

// Opens the browser's file picker. Returns the chosen file's text, "" if the picker was cancelled,
// or TOO_LARGE. Blocks until the user chooses, so call it through pickTextFileAsync().
static native String pickTextFile();

// Token images (see Tokens.editor). Both block, so call them through callAsync(). They return a
// Base64 PNG already center-cropped and downscaled to Tokens.THUMB × Tokens.THUMB in JavaScript,
// "" if cancelled / the clipboard holds no image, or "\0" + a message to show the user.
static native String pickImageFile();
static native String readClipboardImage();

static final String TOO_LARGE = "\u0000too-large";

// Runs the picker off the Swing thread and hands the result (null if the browser refused) back on it
static void pickTextFileAsync(Consumer<String> onResult) {
    callAsync(WebBridge::pickTextFile, onResult);
}

// Runs a blocking native off the Swing thread and hands its result (null if it threw) back on it
static void callAsync(Supplier<String> call, Consumer<String> onResult) {
    Thread t = new Thread(() -> {
        String result;
        try {
            result = call.get();
        } catch (Throwable ex) {
            result = null;
        }
        String r = result;
        SwingUtilities.invokeLater(() -> onResult.accept(r));
    }, "browser-bridge");
    t.setDaemon(true);
    t.start();
}
}
