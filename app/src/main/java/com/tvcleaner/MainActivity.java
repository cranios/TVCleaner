package com.tvcleaner;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int C_BG = 0xFF0B1220;
    private static final int C_CARD = 0xFF1B2638;
    private static final int C_ACCENT = 0xFF22D3EE;
    private static final int C_TEXT = 0xFFE5EDF7;
    private static final int C_SUB = 0xFF8FA3BF;
    private static final int C_OK = 0xFF34D399;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView ramText, diskText, logView;
    private ProgressBar ramBar, diskBar;
    private ScrollView logScroll;
    private volatile boolean busy = false;
    private volatile boolean rootOk = false;

    interface Task { void run() throws Exception; }

    // ------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 23 && !hasStorage()) {
            requestPermissions(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        buildUi();
        refreshStats();
        log("Pronto. Use as setas do controle e OK para navegar.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
    }

    private boolean hasStorage() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------ interface

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int fill, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(14));
        if (strokeW > 0) g.setStroke(strokeW, stroke);
        return g;
    }

    private TextView button(String text, final Runnable action, boolean big) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(C_TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big ? 24 : 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(big ? 26 : 20), dp(14), dp(big ? 26 : 20));
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, shape(0xFF0E7490, 0xFFFFFFFF, dp(3)));
        sl.addState(new int[]{android.R.attr.state_pressed}, shape(0xFF155E75, C_ACCENT, dp(2)));
        sl.addState(new int[]{}, shape(big ? 0xFF164E63 : C_CARD, 0, 0));
        t.setBackground(sl);
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        r.setLayoutParams(lp);
        return r;
    }

    private void addToRow(LinearLayout r, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(6);
        v.setLayoutParams(lp);
        r.addView(v);
    }

    private LinearLayout statCard(String title, TextView value, ProgressBar bar) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(16), dp(20), dp(16));
        c.setBackground(shape(C_CARD, 0, 0));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(C_SUB);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setTextColor(C_TEXT);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        bar.setMax(100);
        bar.getProgressDrawable().setColorFilter(C_ACCENT, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        bp.topMargin = dp(8);
        bar.setLayoutParams(bp);
        c.addView(t);
        c.addView(value);
        c.addView(bar);
        return c;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setPadding(dp(48), dp(28), dp(48), dp(24)); // margem segura de overscan

        TextView title = new TextView(this);
        title.setText("TV Cleaner");
        title.setTextColor(C_ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = new TextView(this);
        sub.setText("Limpeza de cache, memória e arquivos inúteis");
        sub.setTextColor(C_SUB);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(title);
        root.addView(sub);

        // medidores
        ramText = new TextView(this);
        diskText = new TextView(this);
        ramBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        diskBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout stats = row();
        addToRow(stats, statCard("MEMÓRIA RAM EM USO", ramText, ramBar));
        addToRow(stats, statCard("ARMAZENAMENTO INTERNO", diskText, diskBar));
        root.addView(stats);

        // botão principal
        LinearLayout r0 = row();
        addToRow(r0, button("LIMPEZA COMPLETA", new Runnable() {
            @Override public void run() { fullClean(); }
        }, true));
        root.addView(r0);

        LinearLayout r1 = row();
        addToRow(r1, button("Liberar memória", new Runnable() {
            @Override public void run() { runTask("Liberar memória", new Task() {
                @Override public void run() throws Exception { cleanMemory(); } }); }
        }, false));
        addToRow(r1, button("Arquivos inúteis e cache", new Runnable() {
            @Override public void run() { runTask("Arquivos inúteis e cache", new Task() {
                @Override public void run() throws Exception { cleanJunk(); } }); }
        }, false));
        addToRow(r1, button("Limpeza profunda (root)", new Runnable() {
            @Override public void run() { runTask("Limpeza profunda (root)", new Task() {
                @Override public void run() throws Exception { rootClean(); } }); }
        }, false));
        root.addView(r1);

        LinearLayout r2 = row();
        addToRow(r2, button("Config. de armazenamento", new Runnable() {
            @Override public void run() { openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS); }
        }, false));
        addToRow(r2, button("Gerenciar apps (cache manual)", new Runnable() {
            @Override public void run() { openSettings(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS); }
        }, false));
        root.addView(r2);

        // log
        logView = new TextView(this);
        logView.setTextColor(C_OK);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(16), dp(12), dp(16), dp(12));
        logScroll = new ScrollView(this);
        logScroll.setBackground(shape(0xFF0F1A2B, 0, 0));
        logScroll.addView(logView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(16);
        logScroll.setLayoutParams(lp);
        root.addView(logScroll);

        setContentView(root);
        r0.getChildAt(0).requestFocus();
    }

    // ------------------------------------------------------------ utilidades

    private void log(final String s) {
        ui.post(new Runnable() {
            @Override public void run() {
                logView.append("> " + s + "\n");
                logScroll.post(new Runnable() {
                    @Override public void run() { logScroll.fullScroll(View.FOCUS_DOWN); }
                });
            }
        });
    }

    private void refreshStats() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long used = mi.totalMem - mi.availMem;
        int rp = (int) (used * 100 / Math.max(1, mi.totalMem));
        ramText.setText(fmt(used) + " / " + fmt(mi.totalMem) + "  (" + rp + "%)");
        ramBar.setProgress(rp);

        StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
        long total = sf.getBlockCountLong() * sf.getBlockSizeLong();
        long free = sf.getAvailableBlocksLong() * sf.getBlockSizeLong();
        int dpct = (int) ((total - free) * 100 / Math.max(1, total));
        diskText.setText(fmt(total - free) + " / " + fmt(total) + "  (" + dpct + "%)");
        diskBar.setProgress(dpct);
    }

    private static String fmt(long b) {
        if (b < 1024) return b + " B";
        double v = b;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = -1;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format(Locale.US, "%.1f %s", v, u[i]);
    }

    private long availMem() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    private void runTask(final String name, final Task t) {
        if (busy) { log("Aguarde: operação em andamento..."); return; }
        busy = true;
        log("Iniciando: " + name);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    t.run();
                } catch (Throwable e) {
                    log("Erro: " + e.getMessage());
                } finally {
                    busy = false;
                    ui.post(new Runnable() { @Override public void run() { refreshStats(); } });
                }
            }
        }).start();
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS));
            } catch (Exception e2) {
                log("Esta TV Box não possui essa tela de configurações.");
            }
        }
    }

    // ------------------------------------------------------------ limpezas

    private void fullClean() {
        runTask("Limpeza completa", new Task() {
            @Override public void run() throws Exception {
                cleanJunk();
                cleanMemory();
                if (rootOk) rootClean();
                log("Limpeza completa finalizada.");
            }
        });
    }

    /** Encerra processos em segundo plano de apps de terceiros. */
    private void cleanMemory() throws Exception {
        long before = availMem();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = getPackageManager();

        Set<String> skip = new HashSet<String>();
        skip.add(getPackageName());
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
            skip.add(ri.activityInfo.packageName);
        }

        int n = 0;
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (skip.contains(ai.packageName)) continue;
            am.killBackgroundProcesses(ai.packageName);
            n++;
        }
        System.gc();
        Thread.sleep(1500);
        long freed = Math.max(0, availMem() - before);
        log("Memória: " + n + " apps verificados, ~" + fmt(freed) + " liberados.");
    }

    /** Cache de apps (armazenamento externo), miniaturas, temporários e logs. */
    private void cleanJunk() throws Exception {
        if (!hasStorage()) {
            log("Permissão de armazenamento negada. Conceda em Configurações > Apps > TV Cleaner.");
            return;
        }
        long total = 0;
        total += deleteContents(getCacheDir());
        File ext = getExternalCacheDir();
        if (ext != null) total += deleteContents(ext);

        File root = Environment.getExternalStorageDirectory();

        // cache externo dos demais apps: /sdcard/Android/data/<pacote>/cache
        File[] pkgs = new File(root, "Android/data").listFiles();
        int apps = 0;
        if (pkgs != null) {
            for (File p : pkgs) {
                File c = new File(p, "cache");
                if (c.isDirectory()) {
                    long s = deleteContents(c);
                    if (s > 0) apps++;
                    total += s;
                }
            }
        }
        log("Cache de " + apps + " apps limpo.");

        long junk = scan(root, 0);
        log("Arquivos inúteis removidos: " + fmt(junk));
        total += junk;
        log("Total liberado em disco: " + fmt(total));
    }

    private long scan(File dir, int depth) {
        long freed = 0;
        if (depth > 8) return 0;
        File[] list = dir.listFiles();
        if (list == null) return 0;
        for (File f : list) {
            String n = f.getName();
            String l = n.toLowerCase(Locale.ROOT);
            if (f.isDirectory()) {
                if (depth == 0 && n.equals("Android")) continue;
                if (l.equals(".thumbnails") || l.equals("lost.dir") || l.equals(".trash")
                        || l.equals(".trashbin")) {
                    freed += deleteContents(f);
                } else {
                    freed += scan(f, depth + 1);
                }
            } else if (l.endsWith(".tmp") || l.endsWith(".temp") || l.endsWith(".log")
                    || l.endsWith(".chk") || l.endsWith(".dmp") || l.startsWith(".thumbdata")) {
                long s = f.length();
                if (f.delete()) freed += s;
            }
        }
        return freed;
    }

    private long deleteContents(File dir) {
        long freed = 0;
        File[] list = dir.listFiles();
        if (list == null) return 0;
        for (File f : list) {
            if (f.isDirectory()) {
                freed += deleteContents(f);
                f.delete();
            } else {
                long s = f.length();
                if (f.delete()) freed += s;
            }
        }
        return freed;
    }

    // ------------------------------------------------------------ root (opcional)

    private String su(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        p.waitFor();
        return sb.toString().trim();
    }

    private void rootClean() throws Exception {
        try {
            if (!su("id").contains("uid=0")) throw new Exception("sem root");
        } catch (Exception e) {
            rootOk = false;
            log("Root não disponível nesta TV Box. Função ignorada.");
            return;
        }
        rootOk = true;
        su("pm trim-caches 999999999999");
        log("Cache de todos os apps do sistema limpo (pm trim-caches).");
        su("sync; echo 3 > /proc/sys/vm/drop_caches");
        log("Cache de páginas do kernel descartado (drop_caches).");
    }
}
