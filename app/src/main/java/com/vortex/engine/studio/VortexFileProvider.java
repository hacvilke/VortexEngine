package com.vortex.engine.studio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lightweight zero-dependency ContentProvider to share Termux-built APKs with Android Package Installer.
 */
public class VortexFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.vortex.engine.studio.fileprovider";

    public static Uri getUriForFile(Context ctx, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .encodedPath(Uri.encode(file.getAbsolutePath(), "/"))
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolveFile(Uri uri) throws FileNotFoundException {
        String path = Uri.decode(uri.getEncodedPath());
        if (path == null || path.isEmpty()) throw new FileNotFoundException("Empty URI path");
        File f = new File(path);
        if (!f.exists()) throw new FileNotFoundException("File not found: " + path);
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolveFile(uri);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String p = uri.getPath();
        if (p != null && p.endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File f = resolveFile(uri);
            String[] cols = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor mc = new MatrixCursor(cols, 1);
            mc.addRow(new Object[]{f.getName(), f.length()});
            return mc;
        } catch (Exception e) {
            return new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, 0);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
