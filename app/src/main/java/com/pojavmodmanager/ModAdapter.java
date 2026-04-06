package com.pojavmodmanager;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.*;

public class ModAdapter extends ArrayAdapter<ModItem> {

    private String targetFolder;
    private ExecutorService executor = Executors.newFixedThreadPool(3);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public ModAdapter(Context ctx, ArrayList<ModItem> items, String folder) {
        super(ctx, R.layout.item_mod, items);
        this.targetFolder = folder;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_mod, parent, false);

        ModItem mod = getItem(position);

        ImageView imgIcon = convertView.findViewById(R.id.imgModIcon);
        TextView tvName = convertView.findViewById(R.id.tvModName);
        TextView tvReq = convertView.findViewById(R.id.tvRequirements);
        TextView tvDesc = convertView.findViewById(R.id.tvDescription);
        TextView tvSource = convertView.findViewById(R.id.tvSource);
        TextView tvStatus = convertView.findViewById(R.id.tvDownloadStatus);
        Button btnDownload = convertView.findViewById(R.id.btnDownload);

        tvName.setText(mod.name);
        tvReq.setText("🔧 " + mod.requirements);
        tvDesc.setText(mod.description);
        tvSource.setText(mod.source.toUpperCase());
        tvStatus.setText("");
        btnDownload.setEnabled(true);

        // Load icon
        imgIcon.setImageResource(android.R.drawable.ic_menu_gallery);
        if (mod.iconUrl != null && !mod.iconUrl.isEmpty()) {
            loadImage(mod.iconUrl, imgIcon);
        }

        if (mod.source.equals("curseforge")) {
            tvSource.setBackgroundColor(0xFFFF6B35);
            btnDownload.setText("⚠ Need API Key");
            btnDownload.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF555555));
            btnDownload.setOnClickListener(v ->
                Toast.makeText(getContext(), "Add CurseForge API Key in settings", Toast.LENGTH_LONG).show());
        } else {
            tvSource.setBackgroundColor(0xFF03DAC5);
            btnDownload.setText("⬇ Download");
            btnDownload.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF03DAC5));
            btnDownload.setOnClickListener(v -> {
                tvStatus.setText("⏳ Fetching...");
                btnDownload.setEnabled(false);
                downloadFromModrinth(mod, tvStatus, btnDownload);
            });
        }

        return convertView;
    }

    private void loadImage(String url, ImageView imageView) {
        executor.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();
                InputStream in = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                in.close();
                if (bitmap != null) {
                    mainHandler.post(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception e) {
                // keep default icon
            }
        });
    }

    private void downloadFromModrinth(ModItem mod, TextView tvStatus, Button btnDownload) {
        executor.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                String versionUrl = "https://api.modrinth.com/v2/project/" + mod.id + "/version";
                Request req = new Request.Builder()
                    .url(versionUrl)
                    .addHeader("User-Agent", "PojavModManager/1.0")
                    .build();
                Response res = client.newCall(req).execute();
                JSONArray versions = new JSONArray(res.body().string());

                if (versions.length() == 0) {
                    showMsg("No versions found", tvStatus, btnDownload);
                    return;
                }

                JSONObject latest = versions.getJSONObject(0);
                JSONArray files = latest.getJSONArray("files");

                String downloadUrl = null, fileName = null;
                for (int i = 0; i < files.length(); i++) {
                    JSONObject f = files.getJSONObject(i);
                    String fname = f.getString("filename");
                    if (fname.endsWith(".jar") || fname.endsWith(".zip") || fname.endsWith(".mrpack")) {
                        downloadUrl = f.getString("url");
                        fileName = fname;
                        break;
                    }
                }

                if (downloadUrl == null) {
                    showMsg("No file found", tvStatus, btnDownload);
                    return;
                }

                File dir = new File(targetFolder);
                if (!dir.exists()) dir.mkdirs();

                File dest = new File(targetFolder + fileName);
                if (dest.exists()) {
                    showMsg("✅ Already downloaded!", tvStatus, btnDownload);
                    return;
                }

                final String finalName = fileName;
                mainHandler.post(() -> tvStatus.setText("⬇ Downloading..."));

                HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
                conn.connect();
                InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(dest);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                in.close();
                out.close();

                mainHandler.post(() -> {
                    tvStatus.setText("✅ Done!");
                    btnDownload.setEnabled(true);
                    btnDownload.setText("✅ Done");
                    Toast.makeText(getContext(), finalName + " saved!", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                showMsg("❌ " + e.getMessage(), tvStatus, btnDownload);
            }
        });
    }

    private void showMsg(String msg, TextView tv, Button btn) {
        mainHandler.post(() -> { tv.setText(msg); btn.setEnabled(true); });
    }
}
