package com.audiotronplayer;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;

import java.util.HashSet;
import java.util.Set;

@ReactModule(name = NativeAudioLibraryModule.NAME)
public class NativeAudioLibraryModule
        extends NativeAudioLibrarySpec
        implements ActivityEventListener {

    public static final String NAME = "NativeAudioLibrary";
    private static final int REQUEST_PICK_FOLDER = 4101;

    private final ReactApplicationContext context;
    private Promise folderPromise;

    public NativeAudioLibraryModule(
            ReactApplicationContext context) {
        super(context);
        this.context = context;
        context.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void pickAudioFolder(Promise promise) {
        Activity activity = context.getCurrentActivity();

        if (activity == null) {
            promise.reject(
                    "NO_ACTIVITY",
                    "No active Android activity");
            return;
        }

        if (folderPromise != null) {
            promise.reject(
                    "PICKER_BUSY",
                    "A folder picker is already open");
            return;
        }

        folderPromise = promise;

        try {
            Intent intent = new Intent(
                    Intent.ACTION_OPEN_DOCUMENT_TREE);

            activity.startActivityForResult(
                    intent,
                    REQUEST_PICK_FOLDER);

        } catch (Exception e) {
            folderPromise = null;
            promise.reject(
                    "PICKER_ERROR",
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void onActivityResult(
            Activity activity,
            int requestCode,
            int resultCode,
            Intent data) {

        if (requestCode != REQUEST_PICK_FOLDER) {
            return;
        }

        Promise promise = folderPromise;
        folderPromise = null;

        if (promise == null) {
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            promise.reject(
                    "CANCELLED",
                    "Folder selection was cancelled");
            return;
        }

        Uri uri = data != null ? data.getData() : null;

        if (uri == null) {
            promise.reject(
                    "NO_URI",
                    "No folder URI was returned");
            return;
        }

        try {
            int readPermission =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION;

            int grantedFlags = data.getFlags() & readPermission;

            if (grantedFlags != 0) {
                context.getContentResolver()
                        .takePersistableUriPermission(
                                uri,
                                grantedFlags);
            } else {
                promise.reject(
                        "PERMISSION_ERROR",
                        "Read permission was not granted");
                return;
            }

            promise.resolve(uri.toString());

        } catch (Exception e) {
            promise.reject(
                    "PERMISSION_ERROR",
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // No action required.
    }

    @Override
    public void listAudioFiles(
            String treeUri,
            Promise promise) {

        try {
            WritableArray results = Arguments.createArray();
            Uri rootUri = Uri.parse(treeUri);

            scanDirectory(
                    rootUri,
                    results,
                    new HashSet<String>());

            promise.resolve(results);

        } catch (Exception e) {
            promise.reject(
                    "LIST_AUDIO_ERROR",
                    e.getMessage(),
                    e);
        }
    }

    private void scanDirectory(
            Uri treeUri,
            WritableArray results,
            Set<String> visited) {

        String rootDocumentId =
                DocumentsContract.getTreeDocumentId(treeUri);

        scanDocument(
                treeUri,
                rootDocumentId,
                results,
                visited);
    }

    private void scanDocument(
            Uri treeUri,
            String documentId,
            WritableArray results,
            Set<String> visited) {

        if (!visited.add(documentId)) {
            return;
        }

        Uri childrenUri =
                DocumentsContract
                        .buildChildDocumentsUriUsingTree(
                                treeUri,
                                documentId);

        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };

        try (Cursor cursor =
                     context.getContentResolver().query(
                             childrenUri,
                             projection,
                             null,
                             null,
                             null)) {

            if (cursor == null) {
                return;
            }

            int idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID);

            int nameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME);

            int mimeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE);

            int sizeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_SIZE);

            while (cursor.moveToNext()) {
                String id = cursor.getString(idIndex);

                String name = cursor.isNull(nameIndex)
                        ? null
                        : cursor.getString(nameIndex);

                String mime = cursor.isNull(mimeIndex)
                        ? ""
                        : cursor.getString(mimeIndex);

                if (name == null) {
                    continue;
                }

                if (DocumentsContract.Document.MIME_TYPE_DIR
                        .equals(mime)) {

                    scanDocument(
                            treeUri,
                            id,
                            results,
                            visited);

                    continue;
                }

                if (!isAudioFile(name, mime)) {
                    continue;
                }

                Uri fileUri =
                        DocumentsContract
                                .buildDocumentUriUsingTree(
                                        treeUri,
                                        id);

                WritableMap item = Arguments.createMap();

                item.putString("name", name);
                item.putString("uri", fileUri.toString());

                item.putString(
                        "mimeType",
                        mime.isEmpty()
                                ? "application/octet-stream"
                                : mime);

                double size = cursor.isNull(sizeIndex)
                        ? 0.0
                        : (double) cursor.getLong(sizeIndex);

                item.putDouble("size", size);

                results.pushMap(item);
            }

        } catch (Exception e) {
            // In production, consider reporting inaccessible
            // directories instead of silently skipping them.
            android.util.Log.e(
                    NAME,
                    "Unable to scan directory: " + documentId,
                    e);
        }
    }

    private boolean isAudioFile(
            String name,
            String mime) {

        if (mime != null && mime.startsWith("audio/")) {
            return true;
        }

        int dotIndex = name.lastIndexOf('.');

        String extension = dotIndex >= 0
                ? name.substring(dotIndex + 1)
                        .toLowerCase(java.util.Locale.ROOT)
                : "";

        return extension.equals("mp3")
                || extension.equals("m4a")
                || extension.equals("aac")
                || extension.equals("wav")
                || extension.equals("ogg")
                || extension.equals("opus")
                || extension.equals("flac")
                || extension.equals("wma")
                || extension.equals("amr")
                || extension.equals("aiff");
    }
}