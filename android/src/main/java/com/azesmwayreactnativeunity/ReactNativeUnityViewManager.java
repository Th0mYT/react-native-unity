package com.azesmwayreactnativeunity;

import static com.azesmwayreactnativeunity.ReactNativeUnity.*;

import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.EventDispatcher;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

@ReactModule(name = ReactNativeUnityViewManager.NAME)
public class ReactNativeUnityViewManager extends ReactNativeUnityViewManagerSpec<ReactNativeUnityView> implements LifecycleEventListener, View.OnAttachStateChangeListener {
  private static final String TAG = "ReactNativeUnity";
  ReactApplicationContext context;
  static ReactNativeUnityView view;
  public static final String NAME = "RNUnityView";

  public ReactNativeUnityViewManager(ReactApplicationContext context) {
    super();
    this.context = context;
    context.addLifecycleEventListener(this);
  }

  @NonNull
  @Override
  public String getName() {
    return NAME;
  }

  @NonNull
  @Override
  public ReactNativeUnityView createViewInstance(@NonNull ThemedReactContext context) {
    view = new ReactNativeUnityView(this.context);
    view.addOnAttachStateChangeListener(this);

    if (getPlayer() != null) {
        try {
            view.setUnityPlayer(getPlayer());
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            Log.e(TAG, "setUnityPlayer failed", e);
        }
    } else {
        try {
            createPlayer(context.getCurrentActivity(), new UnityPlayerCallback() {
              @Override
              public void onReady() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
                view.setUnityPlayer(getPlayer());
              }

              @Override
              public void onUnload() {
                dispatchEvent(view, "onPlayerUnload", "");
              }

              @Override
              public void onQuit() {
                dispatchEvent(view, "onPlayerQuit", "");
              }
            });
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            Log.e(TAG, "createPlayer failed", e);
        }
    }

    return view;
  }

  @Override
  public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
    Map<String, Object> export = super.getExportedCustomDirectEventTypeConstants();

    if (export == null) {
      export = MapBuilder.newHashMap();
    }

    export.put("onUnityMessage", MapBuilder.of("registrationName", "onUnityMessage"));
    export.put("onPlayerUnload", MapBuilder.of("registrationName", "onPlayerUnload"));
    export.put("onPlayerQuit", MapBuilder.of("registrationName", "onPlayerQuit"));

    return export;
  }

  @Override
  public void receiveCommand(@NonNull ReactNativeUnityView view, String commandType, @Nullable ReadableArray args) {
    Assertions.assertNotNull(view);
    Assertions.assertNotNull(args);

    switch (commandType) {
      case "postMessage":
        assert args != null;
        postMessage(view, args.getString(0), args.getString(1), args.getString(2));
        return;
      case "unloadUnity":
        unloadUnity(view);
        return;
      case "pauseUnity":
        assert args != null;
        pauseUnity(view, args.getBoolean(0));
        return;
      case "resumeUnity":
        resumeUnity(view);
        return;
      case "windowFocusChanged":
        assert args != null;
        windowFocusChanged(view, args.getBoolean(0));
        return;
      default:
        throw new IllegalArgumentException(String.format(
          "Unsupported command %s received by %s.",
          commandType,
          getClass().getSimpleName()));
    }
  }

  @Override
  public void unloadUnity(ReactNativeUnityView view) {
    if (isUnityReady()) {
      unload();
    }
  }

  @Override
  public void pauseUnity(ReactNativeUnityView view, boolean pause) {
    if (isUnityReady()) {
      if (pause) { pause(); } else { resume(); }
    }
  }

  @Override
  public void resumeUnity(ReactNativeUnityView view) {
    if (isUnityReady()) {
      assert getPlayer() != null;
      getPlayer().resume();
    }
  }

  @Override
  public void windowFocusChanged(ReactNativeUnityView view, boolean hasFocus) {
    if (isUnityReady()) {
      assert getPlayer() != null;
      getPlayer().windowFocusChanged(hasFocus);
    }
  }

  private static void dispatchEvent(ReactNativeUnityView view, String eventName, String message) {
    if (view == null) { Log.e(TAG, "dispatchEvent: null view for " + eventName); return; }
    ReactContext ctx = (ReactContext) view.getContext();
    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
      int surfaceId;
      try {
        surfaceId = UIManagerHelper.getSurfaceId(view);
      } catch (IllegalStateException e) {
        Log.w(TAG, "dispatchEvent: view not attached to a Fabric surface, dropping " + eventName);
        return;
      }
      EventDispatcher ed = UIManagerHelper.getEventDispatcherForReactTag(ctx, view.getId());
      if (ed != null) ed.dispatchEvent(new UnityEvent(eventName, message, surfaceId, view.getId()));
      else Log.e(TAG, "No EventDispatcher for " + eventName);
    } else {
      com.facebook.react.bridge.WritableMap data = com.facebook.react.bridge.Arguments.createMap();
      data.putString("message", message);
      ctx.getJSModule(com.facebook.react.uimanager.events.RCTEventEmitter.class)
         .receiveEvent(view.getId(), eventName, data);
    }
  }

  public static void sendMessageToMobileApp(String message) {
    if (view == null) { return; }
    dispatchEvent(view, "onUnityMessage", message);
  }

  @Override
  public void onDropViewInstance(ReactNativeUnityView view) {
    view.removeOnAttachStateChangeListener(this);
    super.onDropViewInstance(view);
    if (ReactNativeUnityViewManager.view == view) { ReactNativeUnityViewManager.view = null; }
  }

  @Override
  public void onHostResume() {
    if (isUnityReady()) {
      assert getPlayer() != null;
      getPlayer().resume();
      restoreUnityUserState();
    }
  }

  @Override
  public void onHostPause() {
    if (isUnityReady()) {
      assert getPlayer() != null;
      getPlayer().pause();
    }
  }

  @Override
  public void onHostDestroy() {
    if (isUnityReady()) {
      assert getPlayer() != null;
      getPlayer().destroy();
    }
  }

  private void restoreUnityUserState() {
    // restore the unity player state
    if (isUnityPaused()) {
      Handler handler = new Handler();
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          if (getPlayer() != null) {
            getPlayer().pause();
          }
        }
      }, 300);
    }
  }

  @Override
  public void onViewAttachedToWindow(View v) {
    restoreUnityUserState();
  }

  @Override
  public void onViewDetachedFromWindow(View v) {}

  @ReactProp(name = "androidKeepPlayerMounted", defaultBoolean = false)
  public void setAndroidKeepPlayerMounted(ReactNativeUnityView view, boolean keepPlayerMounted) {
    view.keepPlayerMounted = keepPlayerMounted;
  }

  @ReactProp(name = "fullScreen", defaultBoolean = true)
  public void setFullScreen(ReactNativeUnityView view, boolean fullScreen) {
    _fullScreen = fullScreen;
  }

  @Override
  public void postMessage(ReactNativeUnityView view, String gameObject, String methodName, String message) {
    if (isUnityReady()) {
      assert getPlayer() != null;
      UPlayer.UnitySendMessage(gameObject, methodName, message);
    }
  }
}
