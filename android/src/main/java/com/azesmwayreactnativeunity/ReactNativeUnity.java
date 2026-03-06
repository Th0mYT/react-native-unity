package com.azesmwayreactnativeunity;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import java.lang.reflect.InvocationTargetException;

public class ReactNativeUnity {
    private static final String TAG = "ReactNativeUnity";
    private static UPlayer unityPlayer;
    public static boolean _isUnityReady;
    public static boolean _isUnityPaused;
    public static boolean _fullScreen;

    public static UPlayer getPlayer() {
        if (!_isUnityReady) {
            return null;
        }
        return unityPlayer;
    }

    public static boolean isUnityReady() {
        return _isUnityReady;
    }

    public static boolean isUnityPaused() {
        return _isUnityPaused;
    }

    public static void createPlayer(final Activity activity, final UnityPlayerCallback callback) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        if (unityPlayer != null) {
            // Post to main thread: in Fabric (new arch) createViewInstance runs on a
            // background thread, so calling onReady() (which calls addUnityViewToGroup)
            // directly would modify the view hierarchy off the main thread.
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        callback.onReady();
                    } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                        Log.e(TAG, "callback.onReady failed (early return)", e);
                    }
                }
            });
            return;
        }

        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.getWindow().setFormat(PixelFormat.RGBA_8888);
                    // FLAG_FULLSCREEN was deprecated in API 30 and is fully ignored on
                    // Android 15+ (edge-to-edge enforced). Only read it on older APIs.
                    int flag = activity.getWindow().getAttributes().flags;
                    final boolean fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                            ? (flag & WindowManager.LayoutParams.FLAG_FULLSCREEN) == WindowManager.LayoutParams.FLAG_FULLSCREEN
                            : false;

                    try {
                        unityPlayer = new UPlayer(activity, callback);
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        Log.e(TAG, "Failed to create UPlayer", e);
                    } catch (Error e) {
                        // Catches UnsatisfiedLinkError thrown when Unity's native .so libraries
                        // fail to load — most commonly on Android 15+ (API 35+) devices that
                        // enforce 16KB page-size alignment. Unity 6.1+ is required for these
                        // devices. See: https://developer.android.com/guide/practices/page-sizes
                        Log.e(TAG, "Failed to load Unity native library — if running on Android 15+/16," +
                                " ensure Unity is built with 16KB page-size support (Unity 6.1+): " + e.getMessage(), e);
                    }

                    if (unityPlayer == null) {
                        return;
                    }

                    // wait a moment before starting unity to fix cannot-start-on-startup issue
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "createPlayer: starting Unity init sequence (Android API " + Build.VERSION.SDK_INT + ")");
                            try {
                                addUnityViewToBackground();
                                Log.d(TAG, "createPlayer: addUnityViewToBackground done");
                            } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                                Log.e(TAG, "addUnityViewToBackground failed", e);
                            }

                            unityPlayer.windowFocusChanged(true);
                            Log.d(TAG, "createPlayer: windowFocusChanged(true) done");

                            try {
                                unityPlayer.requestFocusPlayer();
                                Log.d(TAG, "createPlayer: requestFocusPlayer done");
                            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                                Log.e(TAG, "requestFocusPlayer failed", e);
                            }

                            unityPlayer.resume();
                            Log.d(TAG, "createPlayer: resume() done");

                            // FLAG_FULLSCREEN / FLAG_FORCE_NOT_FULLSCREEN are deprecated from
                            // API 30 and have no effect on API 35+ (edge-to-edge mandatory).
                            if (!fullScreen && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                            }

                            _isUnityReady = true;
                            Log.d(TAG, "createPlayer: _isUnityReady = true, invoking onReady()");

                            try {
                                callback.onReady();
                            } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                                Log.e(TAG, "callback.onReady failed", e);
                            }
                        }
                    }, 1000);
                }
            });
        }
    }

    public static void pause() {
        if (unityPlayer != null) {
            unityPlayer.pause();
            _isUnityPaused = true;
        }
    }

    public static void resume() {
        if (unityPlayer != null) {
            unityPlayer.resume();
            _isUnityPaused = false;
        }
    }

    public static void unload() {
        if (unityPlayer != null) {
            unityPlayer.unload();
            _isUnityPaused = false;
        }
    }

    public static void addUnityViewToBackground() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        if (unityPlayer == null) {
            return;
        }

        if (unityPlayer.getParentPlayer() != null) {
            // NOTE: If we're being detached as part of the transition, make sure
            // to explicitly finish the transition first, as it might still keep
            // the view's parent around despite calling `removeView()` here. This
            // prevents a crash on an `addContentView()` later on.
            // Otherwise, if there's no transition, it's a no-op.
            // See https://stackoverflow.com/a/58247331
            ((ViewGroup) unityPlayer.getParentPlayer()).endViewTransition(unityPlayer.requestFrame());
            ((ViewGroup) unityPlayer.getParentPlayer()).removeView(unityPlayer.requestFrame());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            unityPlayer.setZ(-1f);
        }

        final Activity activity = ((Activity) unityPlayer.getContextPlayer());
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(1, 1);
        activity.addContentView(unityPlayer.requestFrame(), layoutParams);
    }

    public static void addUnityViewToGroup(ViewGroup group) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (unityPlayer == null) {
            return;
        }

        if (unityPlayer.getParentPlayer() != null) {
            ((ViewGroup) unityPlayer.getParentPlayer()).removeView(unityPlayer.requestFrame());
        }

        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        final android.widget.FrameLayout frame = unityPlayer.requestFrame();
        // Reset z-elevation that was set to -1 in addUnityViewToBackground so Unity is visible.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            frame.setZ(1f);
            unityPlayer.setZ(1f);
        }
        group.addView(frame, 0, layoutParams);

        // Resume Unity only after:
        //   1. surfaceCreated fires  — SurfaceView has a valid rendering surface
        //   2. frame.post() exits the current traversal — so the next Choreographer pass runs
        //   3. OnPreDrawListener fires — SurfaceView's own listener fires FIRST (registered
        //      earlier during onAttachedToWindow) and updates the compositor with the correct
        //      window position. Without this, Unity renders but the "hole" is at the stale
        //      1×1 background position, making it invisible under React Native views.
        //
        // frame.layout() below (in group.post) triggers SurfaceView.onSizeChanged →
        // updateSurface() IPC → surfaceCreated, so our callback fires even when the surface
        // was previously valid at 1×1 (size change always causes a surfaceDestroyed/Created).
        final android.view.SurfaceView sv = findSurfaceViewInFrame(frame);
        if (sv != null) {
            // Timeout fallback: if surfaceCreated never fires (e.g. SurfaceView never gets a
            // valid frame on some Android versions), resume Unity anyway after 2 seconds so it
            // is not left permanently paused. The flag prevents a double-resume if the callback
            // does fire normally.
            final boolean[] surfaceCreatedFired = {false};
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!surfaceCreatedFired[0]) {
                        Log.w(TAG, "addUnityViewToGroup: surfaceCreated timeout — resuming Unity as fallback");
                        if (unityPlayer != null) unityPlayer.resume();
                    }
                }
            }, 2000);

            sv.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(android.view.SurfaceHolder holder) {
                    surfaceCreatedFired[0] = true;
                    sv.getHolder().removeCallback(this);
                    // Exit the current traversal so the Choreographer runs its next pass.
                    frame.post(new Runnable() {
                        @Override
                        public void run() {
                            // SurfaceView's OnPreDrawListener was registered during
                            // onAttachedToWindow (before this code), so it fires first,
                            // updating the compositor position. Our listener fires after
                            // and calls resume() with the correct position already set.
                            android.view.ViewTreeObserver vto = frame.getViewTreeObserver();
                            if (vto.isAlive()) {
                                vto.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                                    @Override
                                    public boolean onPreDraw() {
                                        frame.getViewTreeObserver().removeOnPreDrawListener(this);
                                        Log.d(TAG, "addUnityViewToGroup: surface ready + pre-draw, resuming Unity");
                                        if (unityPlayer != null) unityPlayer.resume();
                                        return true;
                                    }
                                });
                            } else {
                                if (unityPlayer != null) unityPlayer.resume();
                            }
                        }
                    });
                }
                @Override public void surfaceChanged(android.view.SurfaceHolder h, int f, int w, int ht) {}
                @Override public void surfaceDestroyed(android.view.SurfaceHolder h) {}
            });
        } else {
            // No SurfaceView found in the hierarchy (newer Unity with TextureView or similar).
            // Fall back to a generous fixed delay to let the surface settle.
            Log.w(TAG, "addUnityViewToGroup: no SurfaceView found, falling back to delayed resume");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (unityPlayer != null) unityPlayer.resume();
                }
            }, 300);
        }

        // In Fabric (New Architecture), parent views can intercept requestLayout() so Unity's
        // frame may never receive its dimensions. Force bounds explicitly once the group has a
        // valid size. Use OnGlobalLayoutListener as fallback if dimensions aren't ready yet.
        //
        // IMPORTANT: frame.measure() must precede frame.layout(). FrameLayout.onLayout()
        // positions children using their *measured* dimensions; if the frame was never
        // measured its children report measuredWidth/Height == 0, so SurfaceView.onSizeChanged
        // is never called, mHaveFrame stays false, updateSurface() returns early, and
        // surfaceCreated never fires — leaving Unity paused with no surface (Android 16).
        group.post(new Runnable() {
            @Override
            public void run() {
                int w = group.getWidth();
                int h = group.getHeight();
                Log.d(TAG, "addUnityViewToGroup post: group=" + w + "x" + h);
                if (w > 0 && h > 0) {
                    frame.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY)
                    );
                    frame.layout(0, 0, w, h);
                } else {
                    Log.w(TAG, "addUnityViewToGroup: group has no size yet, deferring via OnGlobalLayoutListener");
                    group.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            int w2 = group.getWidth();
                            int h2 = group.getHeight();
                            if (w2 > 0 && h2 > 0) {
                                group.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                frame.measure(
                                    android.view.View.MeasureSpec.makeMeasureSpec(w2, android.view.View.MeasureSpec.EXACTLY),
                                    android.view.View.MeasureSpec.makeMeasureSpec(h2, android.view.View.MeasureSpec.EXACTLY)
                                );
                                frame.layout(0, 0, w2, h2);
                                Log.d(TAG, "addUnityViewToGroup deferred layout applied: " + w2 + "x" + h2);
                            }
                        }
                    });
                }
            }
        });

        unityPlayer.windowFocusChanged(true);
        unityPlayer.requestFocusPlayer();
    }

    private static android.view.SurfaceView findSurfaceViewInFrame(android.view.View view) {
        if (view instanceof android.view.SurfaceView) {
            return (android.view.SurfaceView) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.view.SurfaceView sv = findSurfaceViewInFrame(vg.getChildAt(i));
                if (sv != null) return sv;
            }
        }
        return null;
    }

    public interface UnityPlayerCallback {
        void onReady() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException;

        void onUnload();

        void onQuit();
    }
}
