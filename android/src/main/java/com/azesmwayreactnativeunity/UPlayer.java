package com.azesmwayreactnativeunity;

import android.app.Activity;
import android.content.res.Configuration;
import android.util.Log;
import android.widget.FrameLayout;

import com.unity3d.player.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class UPlayer {
    private static final String TAG = "ReactNativeUnity";
    private static UnityPlayer unityPlayer;

    public UPlayer(final Activity activity, final ReactNativeUnity.UnityPlayerCallback callback) throws ClassNotFoundException, InvocationTargetException, IllegalAccessException, InstantiationException, NoSuchMethodException {
        super();
        Class<?> _player = null;

        try {
            _player = Class.forName("com.unity3d.player.UnityPlayerForActivityOrService");
            Log.d(TAG, "UPlayer: using UnityPlayerForActivityOrService");
        } catch (ClassNotFoundException e) {
            _player = Class.forName("com.unity3d.player.UnityPlayer");
            Log.d(TAG, "UPlayer: using UnityPlayer");
        }

        // Log all available constructors to aid debugging on new Android versions.
        Constructor<?>[] allConstructors = _player.getConstructors();
        Log.d(TAG, "UPlayer: found " + allConstructors.length + " public constructor(s) for " + _player.getName());
        for (Constructor<?> c : allConstructors) {
            Log.d(TAG, "UPlayer:   " + c.toGenericString());
        }

        // Prefer the 2-arg constructor (Context/Activity, IUnityPlayerLifecycleEvents).
        Constructor<?> constructor = null;
        for (Constructor<?> c : allConstructors) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 2 && params[1].isAssignableFrom(IUnityPlayerLifecycleEvents.class)) {
                constructor = c;
                break;
            }
        }

        // Fallback: some Unity versions may have added a 3rd parameter constructor.
        if (constructor == null) {
            for (Constructor<?> c : allConstructors) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length >= 2 && params[1].isAssignableFrom(IUnityPlayerLifecycleEvents.class)) {
                    Log.w(TAG, "UPlayer: falling back to " + params.length + "-param constructor");
                    constructor = c;
                    break;
                }
            }
        }

        if (constructor == null) {
            Log.e(TAG, "UPlayer: no suitable constructor found — Unity SDK may be incompatible");
            throw new NoSuchMethodException("No matching UnityPlayer constructor found");
        }

        final IUnityPlayerLifecycleEvents lifecycleEvents = new IUnityPlayerLifecycleEvents() {
            @Override
            public void onUnityPlayerUnloaded() {
                callback.onUnload();
            }

            @Override
            public void onUnityPlayerQuitted() {
                callback.onQuit();
            }
        };

        try {
            if (constructor.getParameterTypes().length == 2) {
                unityPlayer = (UnityPlayer) constructor.newInstance(activity, lifecycleEvents);
            } else {
                // 3+ param constructor: pass null for extra params and hope for the best;
                // realistically this branch means the Unity SDK needs an update.
                Object[] args = new Object[constructor.getParameterTypes().length];
                args[0] = activity;
                args[1] = lifecycleEvents;
                unityPlayer = (UnityPlayer) constructor.newInstance(args);
            }
            Log.d(TAG, "UPlayer: UnityPlayer instantiated successfully");
        } catch (InvocationTargetException e) {
            Log.e(TAG, "UPlayer: UnityPlayer constructor threw an exception", e.getCause() != null ? e.getCause() : e);
            throw e;
        } catch (InstantiationException | IllegalAccessException e) {
            Log.e(TAG, "UPlayer: failed to instantiate UnityPlayer", e);
            throw e;
        }
    }

    public static void UnitySendMessage(String gameObject, String methodName, String message) {
        UnityPlayer.UnitySendMessage(gameObject, methodName, message);
    }

    public void pause() {
        unityPlayer.pause();
    }

    public void windowFocusChanged(boolean b) {
        unityPlayer.windowFocusChanged(b);
    }

    public void resume() {
        unityPlayer.resume();
    }

    public void unload() {
        unityPlayer.unload();
    }

    public Object getParentPlayer() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Method getFrameLayout = unityPlayer.getClass().getMethod("getFrameLayout");
            FrameLayout frame = (FrameLayout) this.requestFrame();

            return frame.getParent();
        } catch (NoSuchMethodException e) {
            Method getParent = unityPlayer.getClass().getMethod("getParent");

            return getParent.invoke(unityPlayer);
        }
    }

    public void configurationChanged(Configuration newConfig) {
        unityPlayer.configurationChanged(newConfig);
    }

    public void destroy() {
        unityPlayer.destroy();
    }

    public void requestFocusPlayer() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Method getFrameLayout = unityPlayer.getClass().getMethod("getFrameLayout");

            FrameLayout frame = (FrameLayout) this.requestFrame();
            frame.requestFocus();
        } catch (NoSuchMethodException e) {
            Method requestFocus = unityPlayer.getClass().getMethod("requestFocus");

            requestFocus.invoke(unityPlayer);
        }
    }

    public FrameLayout requestFrame() throws NoSuchMethodException {
        try {
            Method getFrameLayout = unityPlayer.getClass().getMethod("getFrameLayout");

            return (FrameLayout) getFrameLayout.invoke(unityPlayer);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return (FrameLayout)(Object) unityPlayer;
        }
    }

    public void setZ(float v) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Method setZ = unityPlayer.getClass().getMethod("setZ", float.class);

            setZ.invoke(unityPlayer, v);
        } catch (NoSuchMethodException e) {}
    }

    public Object getContextPlayer() {
        return unityPlayer.getContext();
    }
}
