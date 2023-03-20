package libpag.pagviewer;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

class Utils {

    private static final String TAG = "Utils";

    static boolean copyAssets(Context pContext, String pAssetFilePath, String pDestDirPath) {
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
        } catch (IOException e) {
            Log.e("tag", "Failed to copy asset file: " + pAssetFilePath, e);
            return false;
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
    static File newFile(String dirPath, String name) {
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

    static void CheckStoragePermissions(Activity activity, int requestCode) {
        // Check if we have write permission
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};


        int checkStoragePermissions = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if (checkStoragePermissions != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, requestCode);
        } else {
            Log.i(TAG, "CheckStoragePermissions: Granted");
        }
    }

    static String getOutputFileName(String suffix) {
        String name = new SimpleDateFormat("MMdd_HHmmss").format(new Date(System.currentTimeMillis()));
        name += suffix;
        return name;
    }
}
