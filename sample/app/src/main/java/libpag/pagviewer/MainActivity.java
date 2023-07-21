package libpag.pagviewer;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.engine.impl.GlideEngine;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.libpag.PAG;
import org.libpag.PAGFile;
import org.libpag.PAGImage;
import org.libpag.PAGLicenseManager;
import org.libpag.PAGMovie;
import org.libpag.PAGMovieExporter;
import org.libpag.PAGMovieExporter.Callback;
import org.libpag.PAGMovieExporter.Status;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CODE_SELECT = 10086;
    private static final int REQUEST_CODE_PERMISSION = 10010;
    private static String DEMO_FILES_DIR;
    private PAGPlayerView playerView;
    private PAGMovieExporter exporter;
    private PAGFile pagFile;
    private Runnable onSelectedVideo;
    private List<MediaItem> selectData;
    private String selectedPAGFileName;
    private ProgressDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate: PAG.SDKVersion() = " + PAG.SDKVersion());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        DEMO_FILES_DIR = getFilesDir() + File.separator + "pag_enterprise_demo" + File.separator;
        checkStoragePermissions();
    }

    private void onPermissionsGranted() {
        // SDK鉴权
        initSDKLicense();
        // 添加素材证书
        initFileLicense();
        initView();
    }

    private void checkStoragePermissions() {
        // Check if we have write permission
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};

        int checkStoragePermissions = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if (checkStoragePermissions != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_CODE_PERMISSION);
        } else {
            Log.i(TAG, "checkStoragePermissions: Granted");
            onPermissionsGranted();
        }
    }

    /**
     * SDK鉴权
     */
    private void initSDKLicense() {
        String licenseUrl = "replace_your_license_url";
        String licenseAppId = "replace_your_app_id";
        String licenseKey = "replace_your_key";
        String licenseDir = getFilesDir() + File.separator + "pag";
        String licenseName = "pag_enterprise.license";

        File localLicenseFile = new File(licenseDir, licenseName);
        int licenseResult = PAGLicenseManager.LoadSDKLicense(getApplicationContext(),
                localLicenseFile.getAbsolutePath(), licenseAppId, licenseKey);
        if (PAGLicenseManager.LicenseResultSuccess == licenseResult) {
            Toast.makeText(MainActivity.this, "鉴权成功", Toast.LENGTH_SHORT).show();
            // 如果本地鉴权文件存在，并且鉴权成功，直接返回
            return;
        }
        // 使用网络下载鉴权文件
        Downloader.Listener downloadListener = new Downloader.Listener() {
            @Override
            public void onDownloadSuccess(File file) {
                int licenseResult = PAGLicenseManager.LoadSDKLicense(getApplicationContext(),
                        file.getAbsolutePath(), licenseAppId, licenseKey);
                if (licenseResult != PAGLicenseManager.LicenseResultSuccess) {
                    runOnUiThread(() -> {
                        String msg = "鉴权失败：result is " + licenseResult;
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onDownloadFailed() {
                Log.e(TAG, "onDownloadFailed: ");
            }
        };
        new Thread(new Downloader(licenseUrl, licenseDir, licenseName, downloadListener)).start();
    }

    /**
     * 使用加密素材时需要添加素材证书，否则文件会加载失败，返回nil，建议在APP启动后直接添加，防止加密素材无法使用
     */
    private void initFileLicense() {
        String fileName = "android_demo.license";
        String dirPath = DEMO_FILES_DIR;
        Utils.copyAssets(this, fileName, dirPath);
        File file = new File(dirPath, fileName);
        if (!file.exists() || !file.isFile()) {
            throw new RuntimeException("copy license file failed, path is " + file.getAbsolutePath());
        }
        int result = PAGLicenseManager.AddFileLicense(getApplicationContext(), file.getAbsolutePath());
        Log.i(TAG, "PAGLicenseManager.AddFileLicense result = " + result);
    }

    /**
     * 替换占位图
     */
    public void onReplaceClick(View view) {
        int numImages = pagFile.numImages();
        Toast.makeText(this, "请选择1~" + numImages + "个素材", Toast.LENGTH_SHORT).show();
        Matisse.from(this)
                .choose(MimeType.ofAll())
                .countable(true)
                .maxSelectable(10)
                .thumbnailScale(0.85f)
                .imageEngine(new GlideEngine())
                .forResult(REQUEST_CODE_SELECT);
        onSelectedVideo = () -> replaceImages(pagFile);
    }

    /**
     * 导出视频
     */
    public void onExportClick(View view) {
        if (dialog == null) {
            dialog = new ProgressDialog(this);
            dialog.setTitle("导出进度");
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setIndeterminate(false);
            dialog.setCancelable(true);
            dialog.setOnCancelListener(dialogInterface -> {
                if (exporter != null) {
                    exporter.cancel();
                    Toast.makeText(MainActivity.this, "取消导出", Toast.LENGTH_SHORT).show();
                }
            });
        }
        dialog.show();
        doExport();
    }

    private void initView() {
        final HashMap<Integer, String> fileMap = new HashMap<>();
        fileMap.put(R.id.rb_license, "3D_BOX_encrypted.pag");
        fileMap.put(R.id.rb_audio, "audio_2.pag");
        fileMap.put(R.id.rb_movie, "sizhi5.pag");
        RadioGroup rgSelectPAG = findViewById(R.id.rg_select_pag);
        rgSelectPAG.setOnCheckedChangeListener((radioGroup, id) -> {
            selectedPAGFileName = fileMap.get(id);
            preparePlayer();
        });
        ((RadioButton) rgSelectPAG.findViewById(R.id.rb_license)).setChecked(true);

        Switch stAudioEnable = findViewById(R.id.st_audio_enable);
        stAudioEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            playerView.setAudioEnable(isChecked);
        });
    }

    /**
     * 初始化播放器
     */
    private void preparePlayer() {
        if (playerView != null) {
            playerView.onRelease();
        }
        playerView = new PAGPlayerView();
        playerView.setRepeatCount(-1);
        FrameLayout containerView = findViewById(R.id.container_view);
        containerView.removeAllViews();
        BackgroundView backgroundView = new BackgroundView(this);
        backgroundView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        containerView.addView(backgroundView);
        pagFile = loadPAGFile();
        View pagView = playerView.createView(this, pagFile);
        pagView.setOnClickListener(view -> {
            if (playerView.isPlaying) {
                playerView.stop();
            } else {
                playerView.play();
            }
        });

        containerView.addView(pagView);
        playerView.play();
    }

    /**
     * 加载PAG文件
     *
     * @return PAGFile
     */
    private PAGFile loadPAGFile() {
        PAGFile pagFile = PAGFile.Load(getAssets(), selectedPAGFileName);
        if (pagFile == null) {
            throw new RuntimeException("文件加载失败 name = " + selectedPAGFileName);
        }
        return pagFile;
    }

    private void replaceImages(PAGFile pagFile) {
        if (selectData == null || pagFile == null) {
            return;
        }
        // 方式1，直接替换图片资源
        for (int i = 0; i < selectData.size() && i < pagFile.numImages(); i++) {
            PAGImage image = makePAGImage(selectData.get(i));
            pagFile.replaceImage(i, image);
        }
        // 方式二，通过可编辑图层替换
//        int[] indices = pagFile.getEditableIndices(PAGLayer.LayerTypeImage);
//        for (int i = 0; i < selectData.size() && i < indices.length; i++) {
//            PAGImage image = makePAGImage(selectData.get(i));
//            PAGLayer[] pagLayers = pagFile
//                    .getLayersByEditableIndex(indices[i], PAGLayer.LayerTypeImage);
//            for (PAGLayer layer : pagLayers) {
//                ((PAGImageLayer) layer).replaceImage(image);
//            }
//        }
    }

    private PAGImage makePAGImage(MediaItem datum) {
        // 通过选取的素材path构建PAGImage（视频素材需要用PAGMovie构建）
        if (datum.isVideo()) {
            PAGMovie movie = PAGMovie.MakeFromFile(datum.getPath());
            if (movie == null) {
                return null;
            }
            return movie;
        }
        return PAGImage.FromPath(datum.getPath());
    }

    /**
     * 导出视频
     */
    private void doExport() {
        // 重新构建一个PAGFile
        PAGFile pagFile = loadPAGFile();
        // 替换占位图
        replaceImages(pagFile);
        PAGMovieExporter.Config config = new PAGMovieExporter.Config();
        config.width = pagFile.width();
        config.height = pagFile.height();
        File outputFile = Utils.newFile(DEMO_FILES_DIR + "pag_export/", Utils.getOutputFileName(".mp4"));
        Log.d(TAG, "doExport: outputFile is:" + outputFile.getAbsolutePath());
        config.outputPath = outputFile.getAbsolutePath();
        exporter = PAGMovieExporter.Make(pagFile, config, new Callback() {
            @Override
            public void onProgress(final float progress) {
                Log.d(TAG, "onProgress() called with: progress = [" + progress + "]");
                runOnUiThread(() -> dialog.setProgress((int) (progress * 100)));
            }

            @Override
            public void onStatusChange(Status status, String[] msg) {
                Log.d(TAG, "onStatusChange: status = [" + status + "], msg = [" + Arrays.toString(msg) + "]");
                switch (status) {
                    case Exporting:
                        // do nothing
                        break;
                    case UnKnow:
                    case Failed:
                    case Canceled:
                        if (outputFile.exists()) {
                            outputFile.delete();
                        }
                    case Complete:
                        Utils.tryCopyVideoToDCIM(MainActivity.this, outputFile.getAbsolutePath());
                        runOnUiThread(() -> {
                            // 调用exporter.release()，及时释放内存, 不能在onStatusChange直接调用，因为exporter持有callback生命周期
                            exporter.release();
                            exporter = null;
                            dialog.dismiss();
                        });
                        break;
                }
            }
        });
        if (exporter == null) {
            Toast.makeText(this, "no_movie 版本不包含导出模块", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return;
        }
        exporter.start();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CODE_PERMISSION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // 如果权限被允许，执行方法
            onPermissionsGranted();
        } else {
            // 如果权限被拒绝，做出相应提示或处理
            Toast.makeText(MainActivity.this, "未授权存储权限，无法执行操作", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT && resultCode == RESULT_OK && data != null) {
            selectData = new ArrayList<>();
            List<Uri> selectedUris = Matisse.obtainResult(data);
            for (Uri uri : selectedUris) {
                String mimeType = getContentResolver().getType(uri);
                MediaItem.MediaType type = mimeType.startsWith("image/") ?
                        MediaItem.MediaType.IMAGE :
                        MediaItem.MediaType.VIDEO;
                selectData.add(new MediaItem(Utils.getPathFromUri(uri, this), type));
            }
            Log.i(TAG, "onActivityResult: " + selectData);
            if (onSelectedVideo != null) {
                onSelectedVideo.run();
                onSelectedVideo = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        playerView.onRelease();
        if (exporter != null) {
            exporter.release();
            exporter = null;
        }
    }

}
