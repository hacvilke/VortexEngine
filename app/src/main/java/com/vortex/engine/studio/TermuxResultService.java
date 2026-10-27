package com.vortex.engine.studio;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Receives command execution results (stdout, stderr, exitCode, errmsg) sent back
 * from Termux's RunCommandService via PendingIntent.
 */
public class TermuxResultService extends Service {

    public interface ResultListener {
        void onTermuxResult(int exitCode, String stdout, String stderr, String errmsg);
    }

    private static volatile ResultListener activeListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void setListener(ResultListener listener) {
        activeListener = listener;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final Bundle resultBundle = intent.getBundleExtra("result");
            if (resultBundle != null) {
                final String stdout = resultBundle.getString("stdout", "");
                final String stderr = resultBundle.getString("stderr", "");
                final int exitCode = resultBundle.getInt("exitCode", -1);
                final String errmsg = resultBundle.getString("errmsg", "");

                mainHandler.post(() -> {
                    ResultListener l = activeListener;
                    if (l != null) {
                        l.onTermuxResult(exitCode, stdout, stderr, errmsg);
                    }
                });
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
