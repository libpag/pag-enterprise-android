package libpag.pagviewer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Downloader implements Runnable {

    private static final int TIME_OUT = 10000;
    private static final int DOWNLOAD_SUCCESS = 0;
    private static final int DOWNLOAD_FAILED = -1;
    private static final int DOWNLOAD_EXCEPTION = -2;

    private String mRequestUrl;
    private String mFolder;
    private String mFileName;
    private Listener mListener;


    public Downloader(String url, String folder, String filename, Listener listener) {
        mRequestUrl = url;
        mFolder = folder;
        mFileName = filename;
        mListener = listener;
    }

    @Override
    public void run() {
        File dstFolder = new File(mFolder);
        if (dstFolder.isFile()) {
            dstFolder.delete();
        }
        dstFolder.mkdir();
        File tempFile = new File(mFolder + File.separator + System.currentTimeMillis());
        File dstFile = new File(mFolder + File.separator + mFileName);
        HttpURLConnection urlConnection = null;
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        int statusCode = 200;
        int downloadResult = DOWNLOAD_FAILED;
        try {
            if (tempFile.exists()) {
                tempFile.delete();
            }
            tempFile.createNewFile();
            urlConnection = (HttpURLConnection) new URL(mRequestUrl).openConnection();
            urlConnection.setConnectTimeout(TIME_OUT);
            urlConnection.setReadTimeout(TIME_OUT);
            urlConnection.setDoInput(true);
            urlConnection.setRequestMethod("GET");
            statusCode = urlConnection.getResponseCode();
            if (statusCode == 200) {
                inputStream = urlConnection.getInputStream();
                byte[] buffer = new byte[2048];
                fileOutputStream = new FileOutputStream(tempFile);
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, length);
                }
                fileOutputStream.flush();
                downloadResult = DOWNLOAD_SUCCESS;
                // copy temp to dest
                copyTo(tempFile, dstFile);
//                Log.i(TAG, "run: files = " + Arrays.toString(dstFolder.listFiles()));
            } else {
                downloadResult = DOWNLOAD_FAILED;
            }
        } catch (IOException e) {
            downloadResult = DOWNLOAD_EXCEPTION;
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        if (mListener != null) {
            notifyDownloadResult(downloadResult, dstFile);
        }
    }

    private void notifyDownloadResult(int code, File dstFile) {
        switch (code) {
            case DOWNLOAD_SUCCESS:
                mListener.onDownloadSuccess(dstFile);
                break;
            case DOWNLOAD_FAILED:
            case DOWNLOAD_EXCEPTION:
            default:
                mListener.onDownloadFailed();
        }
    }

    public interface Listener {

        void onDownloadSuccess(File file);

        void onDownloadFailed();
    }

    public static void copyTo(File src, File dst) throws IOException {
        if (dst.exists()) {
            dst.delete();
        }
        dst.createNewFile();
        InputStream in = new FileInputStream(src);
        try {
            OutputStream out = new FileOutputStream(dst);
            try {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
            src.delete();
        }
    }

}
