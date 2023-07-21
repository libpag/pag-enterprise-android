package libpag.pagviewer;


import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class Utils {

    private static final String TAG = "Utils";

    static void tryCopyVideoToDCIM(Context context, String sourceFilePath) {
        try {
            copyVideoToDCIM(context, sourceFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void copyVideoToDCIM(Context context, String sourceFilePath) throws IOException {
        File sourceFile = new File(sourceFilePath);
        String targetFileName = sourceFile.getName();

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM);

        ContentResolver contentResolver = context.getContentResolver();
        Uri targetUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);

        if (targetUri != null) {
            try (InputStream in = new FileInputStream(sourceFilePath);
                    OutputStream out = contentResolver.openOutputStream(targetUri)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } else {
            throw new IOException("Failed to insert video into MediaStore");
        }

        // 获取目标文件的绝对路径
        String[] projection = {MediaStore.MediaColumns.DATA};
        try (Cursor cursor = contentResolver.query(targetUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                String targetFilePath = cursor.getString(columnIndex);
                Log.i(TAG,
                        "copyVideoToDCIM: Target file absolute path: " + targetFilePath + ", targetUri:" + targetUri);
            }
        }
    }

    // 静态方法，删除文件夹下的所有文件
    public static void deleteAllFiles(String path) {
        deleteAllFiles(new File(path));
    }

    // 静态方法，删除文件夹下的所有文件
    public static void deleteAllFiles(File file) {
        if (file.exists()) {
            if (file.isFile()) {
                file.delete();
            } else if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (int i = 0; i < files.length; i++) {
                    deleteAllFiles(files[i]);
                }
            }
            file.delete();
        }
    }

    public static boolean copyAssets(Context pContext, String pDestDirPath) {
        // 遍历assets里面的文件
        AssetManager assetManager = pContext.getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        Log.e(TAG, "copyAssets: files = " + Arrays.toString(files));
        // 判空
        if (files == null || files.length == 0) {
            return false;
        }
        // 遍历
        for (String filename : files) {
            if (!copyAssets(pContext, filename, pDestDirPath)) {
                return false;
            }
        }
        return true;
    }

    public static boolean copyAssets(Context pContext, String pAssetFilePath, String pDestDirPath) {
        AssetManager assetManager = pContext.getAssets();
        InputStream in = null;
        OutputStream out = null;
        try {
            File outFile = makeFileAndDirs(pDestDirPath, pAssetFilePath);
            out = new FileOutputStream(outFile);
            in = assetManager.open(pAssetFilePath);
            copyAssetFile(in, out);
            in.close();
            out.flush();
            out.close();
        } catch (Exception e) {
            Log.w(TAG, "Failed to copy asset file: " + pAssetFilePath + ", msg = " + e.getMessage());
        }
        return true;
    }

    /**
     * 解决name带斜杠导致创建文件失败
     */
    @NonNull
    private static File makeFileAndDirs(String dir, String name) {
        // 把assets子目录拼到pDestDirPath
        String[] split = name.split("/");
        for (int i = 0; i < split.length - 1; i++) {
            dir += split[i];
            dir += "/";
        }
        new File(dir).mkdirs();
        return new File(dir, split[split.length -1]);
    }

    private static void copyAssetFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024 * 16];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    @NonNull
    public static File newFile(String dirPath, String name) {
        File file = makeFileAndDirs(dirPath, name);
        try {
            if (file.exists()) {
                file.delete();
            }
            if (file.createNewFile()) {
                return file;
            }
        } catch (Exception e) {
            Log.e(TAG, "newFile: file.createNewFile() error path = " + file.getAbsolutePath());
            throw new RuntimeException(e);
        }
        throw new RuntimeException("创建文件失败");

    }

    public static String getOutputFileName(String suffix) {
        String name = new SimpleDateFormat("MMdd_HHmmss").format(new Date(System.currentTimeMillis()));
        name += suffix;
        return "pag_" + name;
    }


    static String getPathFromUri(Uri uri, Context context) {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(uri)) {
                final String[] split = DocumentsContract.getDocumentId(uri).split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String[] projection = {MediaStore.Images.Media.DATA};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(columnIndex);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
