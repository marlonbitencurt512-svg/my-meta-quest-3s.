
package com.mymetaquest3s;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableException;

public class MainActivity extends Activity {

    private GLSurfaceView surface;
    private SpatialRenderer renderer;
    private Session session;
    private TextView status;
    private boolean installRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        criarInterface();
    }

    private void criarInterface() {
        FrameLayout root = new FrameLayout(this);

        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);

        renderer = new SpatialRenderer(this);
        surface.setRenderer(renderer);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        root.addView(surface);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(20, 12, 20, 12);
        menu.setBackgroundColor(0x66000000);

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(14);
        status.setText("MY META QUEST 3S");
        menu.addView(status);

        Button reset = new Button(this);
        reset.setText("REPOSICIONAR PAINEL");
        reset.setOnClickListener(v -> {
            renderer.clearAnchor();
            status.setText("Toque em uma superfície para fixar o painel");
        });
        menu.addView(reset);

        FrameLayout.LayoutParams params =
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
            );

        root.addView(menu, params);

        surface.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                renderer.requestAnchor(event.getX(), event.getY());
                status.setText("Procurando superfície...");
            }
            return true;
        });

        setContentView(root);
    }

    private void iniciarAR() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.CAMERA}, 10
            );
            return;
        }

        try {
            ArCoreApk.InstallStatus installStatus =
                ArCoreApk.getInstance().requestInstall(
                    this, !installRequested
                );

            if (installStatus ==
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true;
                return;
            }

            if (session == null) {
                session = new Session(this);

                Config config = new Config(session);
                config.setPlaneFindingMode(
                    Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                );

                session.configure(config);
                renderer.setSession(session);
            }

            session.resume();
            status.setText(
                "AR ATIVO — toque em uma superfície para posicionar"
            );

        } catch (UnavailableException e) {
            status.setText("Não foi possível iniciar o ARCore.");
            Toast.makeText(
                this,
                "Erro ARCore: " + e.getMessage(),
                Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            status.setText("Erro ao iniciar a realidade aumentada.");
        }
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        String[] permissions,
        int[] grantResults
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        );

        if (requestCode == 10 &&
            grantResults.length > 0 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarAR();
        } else {
            Toast.makeText(
                this,
                "A câmera é necessária.",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (surface != null) {
            surface.onResume();
        }

        iniciarAR();
    }

    @Override
    protected void onPause() {
        if (session != null) {
            session.pause();
        }

        if (surface != null) {
            surface.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }

        super.onDestroy();
    }
}
