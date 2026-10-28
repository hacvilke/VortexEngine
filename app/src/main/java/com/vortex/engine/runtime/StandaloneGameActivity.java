package com.vortex.engine.runtime;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.render.VortexGLRenderer;
import com.vortex.engine.core.storage.VortexSaveStore;
import com.vortex.engine.core.ui.VortexGameSurfaceView;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Standalone Game Launcher Activity packaged into every APK built via Termux
 * (supports both Lua 5.2 via LuaJ and VortexScript .vxs games!).
 */
public class StandaloneGameActivity extends Activity {

    public static final String EXTRA_PROJECT_JSON = "extra_project_json";

    private VortexGameSurfaceView gameSurface;
    private GameProject masterProject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enableImmersiveFullscreen();
        VortexSaveStore.setSaveRootDir(new File(getFilesDir(), "vortex_saves"));

        masterProject = loadGameProject();
        applyOrientation(masterProject.orientation);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#09090B"));

        gameSurface = new VortexGameSurfaceView(this);
        gameSurface.renderer.setTelemetryListener(new VortexGLRenderer.TelemetryListener() {
            @Override
            public void onHudUpdate(int fps, int entityCount, int score, int health, String banner) {
                gameSurface.controlsOverlay.updateHud(fps, score, health, banner);
            }

            @Override
            public void onScriptLog(String line) {}
        });

        root.addView(gameSurface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        TextView restartBtn = new TextView(this);
        restartBtn.setText("↻ RESTART");
        restartBtn.setTextColor(Color.WHITE);
        restartBtn.setTextSize(12f);
        restartBtn.setPadding(28, 14, 28, 14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(190, 18, 18, 22));
        bg.setCornerRadius(24f);
        bg.setStroke(2, Color.argb(210, 255, 255, 255));
        restartBtn.setBackground(bg);
        restartBtn.setOnClickListener(v -> restartGame());

        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        btnLp.setMargins(20, 20, 24, 20);
        root.addView(restartBtn, btnLp);

        setContentView(root);
        restartGame();
    }

    private void restartGame() {
        if (masterProject != null && gameSurface != null) {
            gameSurface.startPlayMode(masterProject.deepCopy());
        }
    }

    private GameProject loadGameProject() {
        try {
            String extraJson = getIntent() != null ? getIntent().getStringExtra(EXTRA_PROJECT_JSON) : null;
            if (extraJson != null && !extraJson.isEmpty()) {
                return GameProject.fromJsonString(extraJson);
            }
            String assetJson = readAssetText("project.vortex.json");
            GameProject p = GameProject.fromJsonString(assetJson);
            p.ensureScriptFilesInitialized();
            if (p.scriptFiles.size() <= 1) {
                for (String[] pair : new String[][]{
                        {"JAVA", "scripts/main.java"},
                        {"JS", "scripts/main.js"},
                        {"LUA", "scripts/main.lua"},
                        {"VXS", "scripts/main.vxs"}}) {
                    String ov = readAssetTextOptional(pair[1]);
                    if (ov != null && !ov.trim().isEmpty()) {
                        p.scriptLanguage = pair[0];
                        p.scriptCode = ov;
                        p.putScriptFile(pair[1], ov);
                        break;
                    }
                }
            }
            return p;
        } catch (Exception ex) {
            GameProject fallback = new GameProject();
            fallback.title = "Vortex Standalone";
            return fallback;
        }
    }

    private String readAssetText(String assetPath) throws Exception {
        try (InputStream is = getAssets().open(assetPath);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private String readAssetTextOptional(String assetPath) {
        try { return readAssetText(assetPath); } catch (Exception e) { return null; }
    }

    private void applyOrientation(String orientation) {
        if ("portrait".equalsIgnoreCase(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if ("sensor".equalsIgnoreCase(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    private void enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableImmersiveFullscreen();
        if (gameSurface != null) gameSurface.onResume();
    }

    @Override
    protected void onPause() {
        if (gameSurface != null) {
            gameSurface.renderer.audioEngine.stopAll();
            gameSurface.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (gameSurface != null) {
            gameSurface.renderer.audioEngine.shutdown();
            gameSurface.onDestroy();
        }
        super.onDestroy();
    }
}
