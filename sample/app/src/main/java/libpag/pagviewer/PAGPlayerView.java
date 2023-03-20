package libpag.pagviewer;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import org.libpag.PAGComposition;
import org.libpag.PAGFile;
import org.libpag.PAGImage;
import org.libpag.PAGText;
import org.libpag.PAGView;

/**
 * Copyright © 2019 tencent. All rights reserved.
 * <p>
 * File: PAGPlayer.java <p>
 * Project: android <p>
 * Package: libpag.pagviewer <p>
 * Description:
 * <p>
 * author: hamlingong <p>
 * date: 2019/4/19 3:46 PM <p>
 * version: V1.0 <p>
 */
public class PAGPlayerView {

    private PAGView mPagView;
    private int repeatCount = 1;
    private EGLDisplay eglDisplay = null;
    private EGLSurface eglSurface = null;
    private EGLContext eglContext = null;

    boolean isPlaying = true;

    public View createView(Context context, String pagPath) {
        PAGFile pagFile = PAGFile.Load(context.getAssets(), pagPath);
        if (pagFile.numTexts() > 0) {
            PAGText pagText = pagFile.getTextData(0);
            pagText.text = "hahhha哈哈哈😆哈哈哈";
            pagText.fauxItalic = true;
            pagFile.replaceText(0, pagText);
        }
        if (pagFile.numImages() > 0) {
            PAGImage pagImage = PAGImage.FromAssets(context.getAssets(), "rotation.jpg");
//                PAGImage pagImage = makePAGImage(context, "mountain.jpg");
            pagFile.replaceImage(0, pagImage);
        }
        return createView(context, pagFile);
    }

    public View createView(Context context, PAGComposition composition) {
        if (composition == null) {
            return null;
        }
        eglSetup();
        mPagView = new PAGView(context, eglContext);
        mPagView.setComposition(composition);
        mPagView.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mPagView.setRepeatCount(repeatCount);
        return mPagView;
    }

    private void eglSetup() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1);
        EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API);
        int[] attributeList = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_STENCIL_SIZE, 8,
                EGL14.EGL_SAMPLE_BUFFERS, 1,
                EGL14.EGL_SAMPLES, 4,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(eglDisplay, attributeList, 0, configs, 0,
                configs.length, numConfigs, 0);

        int[] attribute_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };

        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                attribute_list, 0);

        int[] surfaceAttributes = {
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
        };
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0],
                surfaceAttributes, 0);
    }

    public void onRelease() {
        if (mPagView != null) {
            mPagView.stop();
            mPagView.freeCache();
            mPagView = null;
        }
        if (eglContext != null && EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            eglSurface = null;
            eglContext = null;
        }
    }

    public void play() {
        if (mPagView != null) {
            isPlaying = true;
            mPagView.play();
        }
    }

    public void stop() {
        if (mPagView != null) {
            isPlaying = false;
            mPagView.stop();
        }
    }

    public void setRepeatCount(int count) {
        this.repeatCount = count;
        if (mPagView != null) {
            mPagView.setRepeatCount(count);
        }
    }
}
