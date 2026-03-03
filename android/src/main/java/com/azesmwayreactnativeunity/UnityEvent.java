package com.azesmwayreactnativeunity;

import androidx.annotation.Nullable;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.Event;

public class UnityEvent extends Event<UnityEvent> {
    private final String mEventName;
    private final String mMessage;

    public UnityEvent(String eventName, String message, int surfaceId, int viewTag) {
        super(surfaceId, viewTag);
        mEventName = eventName;
        mMessage = message;
    }

    @Override public String getEventName() { return mEventName; }

    @Override public boolean canCoalesce() { return false; } // events must not be dropped

    @Nullable
    @Override
    protected WritableMap getEventData() {
        WritableMap data = Arguments.createMap();
        data.putString("message", mMessage);
        return data;
    }
}
