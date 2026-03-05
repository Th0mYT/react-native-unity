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
                    int flag = activity.getWindow().getAttributes().flags;
                    final boolean fullScreen = (flag & WindowManager.LayoutParams.FLAG_FULLSCREEN) == WindowManager.LayoutParams.FLAG_FULLSCREEN;

                    try {
                        unityPlayer = new UPlayer(activity, callback);
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        Log.e(TAG, "Failed to create UPlayer", e);
                    }

                    if (unityPlayer == null) {
                        return;
                    }

                    // wait a moment before starting unity to fix cannot-start-on-startup issue
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                addUnityViewToBackground();
                            } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                                Log.e(TAG, "addUnityViewToBackground failed", e);
                            }

                            unityPlayer.windowFocusChanged(true);

                            try {
                                unityPlayer.requestFocusPlayer();
                            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                                Log.e(TAG, "requestFocusPlayer failed", e);
                            }

                            unityPlayer.resume();

                            if (!fullScreen) {
                                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                            }

                            _isUnityReady = true;

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
            frame.setZ(0f);
            unityPlayer.setZ(0f);
        }
        group.addView(frame, 0, layoutParams);

        // In Fabric (New Architecture), parent views can intercept requestLayout() so Unity's
        // frame may never receive its dimensions. Force bounds explicitly once the group has a
        // valid size. Use OnGlobalLayoutListener as fallback if dimensions aren't ready yet.
        group.post(new Runnable() {
            @Override
            public void run() {
                int w = group.getWidth();
                int h = group.getHeight();
                Log.d(TAG, "addUnityViewToGroup post: group=" + w + "x" + h);
                if (w > 0 && h > 0) {
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
        // Delay resume so SurfaceView.surfaceCreated() can fire before Unity starts rendering.
        // Calling resume() synchronously here causes a black screen on second mount because
        // the SurfaceView has just been re-parented and its surface doesn't exist yet.
        // Background/foreground then fixes it because onHostResume() calls resume() after
        // surfaceCreated(). A short delay gives the surface time to be created first.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (unityPlayer != null) {
                    unityPlayer.resume();
                }
            }
        }, 100);
    }

    public interface UnityPlayerCallback {
        void onReady() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException;

        void onUnload();

        void onQuit();
    }
}
