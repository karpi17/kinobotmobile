package com.asystent.kinowy.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

/**
 * RemoteViewsService — host dla {@link ShiftStackRemoteViewsFactory}.
 * <p>
 * Wymagany przez Android do obsługi kolekcji (StackView) w widgecie.
 * Musi być zarejestrowany w AndroidManifest.xml z uprawnieniem BIND_REMOTEVIEWS.
 */
public class ShiftStackRemoteViewsService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new ShiftStackRemoteViewsFactory(getApplicationContext());
    }
}
