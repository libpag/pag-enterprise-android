package libpag.pagviewer;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tencent.libav.LocalAlbumActivity;
import com.tencent.libav.PhotoSelectorProxyConsts;
import com.tencent.libav.model.TinLocalImageInfoBean;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.libpag.PAG;
import org.libpag.PAGMovieExporter;
import org.libpag.PAGMovieExporter.Callback;
import org.libpag.PAGMovieExporter.Status;
import org.libpag.PAGFile;
import org.libpag.PAGImage;
import org.libpag.PAGLicenseManager;
import org.libpag.PAGMovie;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CODE_SELECT = 10086;
    private static final int REQUEST_CODE_PERMISSION = 10010;
    public static final String DEMO_FILES_DIR = "/sdcard/pag_enterprise_demo/";


    private final PAGPlayerView playerView = new PAGPlayerView();
    private PAGMovieExporter session;
    private PAGFile pagFile;
    private Runnable onSelectedVideo;
    private List<TinLocalImageInfoBean> selectData;
    private String selectedPAGFileName;
    private ProgressDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate: PAG.SDKVersion() = " + PAG.SDKVersion());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        playerView.setRepeatCount(-1);
        Utils.CheckStoragePermissions(this, REQUEST_CODE_PERMISSION);
        // SDK鉴权
        initSDKLicense();
        // 添加素材证书
        initPAGLicense();
        initRadiaGroup();
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
                        String msg = "鉴权失败：result is " +licenseResult;
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
     * 添加素材证书
     */
    private void initPAGLicense() {
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
        LocalAlbumActivity.startChoosePhotoAndVideo(this, numImages, REQUEST_CODE_SELECT);
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
                if (session != null) {
                    session.cancel();
                    Toast.makeText(MainActivity.this, "取消导出", Toast.LENGTH_SHORT).show();
                }
            });
        }
        dialog.show();
        doExport();
    }

    private void initRadiaGroup() {
        final HashMap<Integer, String> fileMap = new HashMap<>();
        fileMap.put(R.id.rb_license, "3D_BOX_encrypted.pag");
        fileMap.put(R.id.rb_audio, "audio_2.pag");
        fileMap.put(R.id.rb_movie, "sizhi5.pag");
        RadioGroup rgSelectPAG = findViewById(R.id.rg_select_pag);
        rgSelectPAG.setOnCheckedChangeListener((radioGroup, id) -> {
            selectedPAGFileName = fileMap.get(id);
            preparePlayer();
        });
        rgSelectPAG.check(R.id.rb_license);
    }

    /**
     * 初始化播放器
     */
    private void preparePlayer() {
        playerView.onRelease();
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
     * @return  PAGFile
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

    private PAGImage makePAGImage(TinLocalImageInfoBean datum) {
        // 通过选取的素材path构建PAGImage（视频素材需要用PAGMovie构建）
        if (datum.isVideo()) {
            PAGMovie movie = PAGMovie.MakeFromFile(datum.mPath);
            if (movie == null) {
                return null;
            }
            return movie;
        }
        return PAGImage.FromPath(datum.mPath);
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
        config.outputPath = outputFile.getAbsolutePath();
        session = PAGMovieExporter.Make(pagFile, config, new Callback() {
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
                        runOnUiThread(() -> {
                            // 调用session.release()，及时释放内存, 不能在onStatusChange直接调用，因为session持有callback生命周期
                            session.release();
                            session = null;
                            dialog.dismiss();
                        });
                        break;
                }
            }
        });
        if (session == null) {
            Toast.makeText(this, "no_movie 版本不包含导出模块", Toast.LENGTH_SHORT).show();
            return;
        }
        session.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT && resultCode == RESULT_OK && data != null) {
            selectData = (ArrayList<TinLocalImageInfoBean>) data
                    .getSerializableExtra(PhotoSelectorProxyConsts.KEY_SELECTED_DATA);
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
        if (session != null) {
            session.release();
            session = null;
        }
    }

}
