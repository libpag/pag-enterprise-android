package libpag.pagviewer;


import static org.libpag.PAGAudioReader.DEFAULT_CHANNELS;
import static org.libpag.PAGAudioReader.DEFAULT_OUTPUT_VOLUME;
import static org.libpag.PAGAudioReader.DEFAULT_SAMPLE_COUNT;
import static org.libpag.PAGAudioReader.DEFAULT_SAMPLE_RATE;

import android.content.Context;
import android.os.Build.VERSION_CODES;
import androidx.annotation.RequiresApi;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.libpag.PAGAudioReader;
import org.libpag.PAGAudioSample;
import org.libpag.PAGComposition;
import org.libpag.PAGFile;
import org.libpag.PAGImageLayer;
import org.libpag.PAGLayer;
import org.libpag.PAGLicenseManager;
import org.libpag.PAGMovie;

/**
 * 文档中的代码示例
 */
class EnterpriseSample {

    private static final String TAG = "EnterpriseSample";

    private static final String PAG_FILE_PATH = "";
    private static final String VIDEO_FILE_PATH = "";
    private static final String SDK_LICENSE_FILE_PATH = "";
    private static final String MATERIAL_LICENSE_FILE_PATH = "";

    private static void temp() {
        PAGMovie movie = PAGMovie.MakeFromFile(PAG_FILE_PATH);
        if (movie == null) {
            return;
        }
    }

    private static void audioReader() {
        PAGAudioReader reader = PAGAudioReader.Make(DEFAULT_SAMPLE_RATE, DEFAULT_SAMPLE_COUNT, DEFAULT_CHANNELS, DEFAULT_OUTPUT_VOLUME);
        PAGComposition composition = PAGFile.Load(PAG_FILE_PATH);
        // 判断素材是否带声音
        boolean hasAudio = composition.audioBytes() != null;

        reader.setComposition(composition);
        // seek到第5秒的位置
        long positionUs = 5_000_000;
        reader.seek(positionUs);

        // 循环读取音频数据，直到读取完毕
        PAGAudioSample audioSample;
        while ((audioSample = reader.readNextSample()) != null
                && audioSample.timestamp + audioSample.duration < composition.duration()) {
            audioSample = reader.readNextSample();
        }
    }

    private static void movie() {
        // 构建完整的movie
        PAGMovie movie = PAGMovie.MakeFromFile(PAG_FILE_PATH);
        // 裁剪和变速: 选取该视频素材的1~3秒，设置为0.5倍速, 0.5音量
        PAGMovie cutMovie = PAGMovie.MakeFromFile(PAG_FILE_PATH, 1_000_000, 2_000_000, 0.5f, 0.5f);
        // 替换到PAG素材中去
        PAGFile pagFile = PAGFile.Load(PAG_FILE_PATH);
        // 方式1，直接替换图片资源
        if (pagFile.numImages() > 0) {
            pagFile.replaceImage(0, movie);
        }
        // 方式二，通过可编辑图层替换
        int[] indices = pagFile.getEditableIndices(PAGLayer.LayerTypeImage);
        if (indices.length > 0) {
            PAGLayer[] pagLayers = pagFile.getLayersByEditableIndex(indices[0], PAGLayer.LayerTypeImage);
            for (PAGLayer layer : pagLayers) {
                ((PAGImageLayer) layer).replaceImage(movie);
            }
        }
    }

    @RequiresApi(api = VERSION_CODES.O)
    private static void SDKLicence(Context context) throws IOException {
        String licenseAppId = "replace_your_app_id";
        String licenseKey = "replace_your_key";
        // 通过文件路径
        PAGLicenseManager.LoadSDKLicense(context, SDK_LICENSE_FILE_PATH, licenseAppId, licenseKey);
        // 通过bytes
        File file = new File(SDK_LICENSE_FILE_PATH);
        byte[] bytes = Files.readAllBytes(file.toPath());
        PAGLicenseManager.LoadSDKLicense(context, bytes, licenseAppId, licenseKey);
    }
}