package com.pojavmodmanager;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    ListView listMods;
    Button btnSearch, btnTabMods, btnTabShaders, btnTabResources, btnTabWorlds, btnSettings;
    EditText etSearch;
    Spinner spinnerInstance, spinnerSource;
    TextView tvStatus;

    SharedPreferences prefs;
    String pojavPath;
    String currentTab = "mods";
    String currentSource = "modrinth";
    String CURSEFORGE_API_KEY = "";

    ArrayList<ModItem> modItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("PojavModManager", MODE_PRIVATE);
        pojavPath = prefs.getString("pojav_path", "/storage/emulated/0/games/PojavLauncher/");

        listMods = findViewById(R.id.listMods);
        btnSearch = findViewById(R.id.btnSearch);
        etSearch = findViewById(R.id.etSearch);
        spinnerInstance = findViewById(R.id.spinnerInstance);
        spinnerSource = findViewById(R.id.spinnerSource);
        tvStatus = findViewById(R.id.tvStatus);
        btnTabMods = findViewById(R.id.btnTabMods);
        btnTabShaders = findViewById(R.id.btnTabShaders);
        btnTabResources = findViewById(R.id.btnTabResources);
        btnTabWorlds = findViewById(R.id.btnTabWorlds);
        btnSettings = findViewById(R.id.btnSettings);

        requestPermissions();
        loadInstances();
        setupTabs();
        setupSource();

        btnSearch.setOnClickListener(v -> search());
        btnSettings.setOnClickListener(v -> openSettings());

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
    }

    void setupTabs() {
        btnTabMods.setOnClickListener(v -> setTab("mods", btnTabMods));
        btnTabShaders.setOnClickListener(v -> setTab("shaders", btnTabShaders));
        btnTabResources.setOnClickListener(v -> setTab("resourcepacks", btnTabResources));
        btnTabWorlds.setOnClickListener(v -> setTab("worlds", btnTabWorlds));
        setTab("mods", btnTabMods);
    }

    void setTab(String tab, Button active) {
        currentTab = tab;
        int inactive = 0xFF2a2a3e;
        int activeColor = 0xFF6200ea;
        btnTabMods.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactive));
        btnTabShaders.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactive));
        btnTabResources.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactive));
        btnTabWorlds.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactive));
        active.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeColor));
        tvStatus.setText("Tab: " + tab + " | Source: " + currentSource);
        modItems.clear();
        updateList();
    }

    void setupSource() {
        String[] sources = {"Modrinth", "CurseForge"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, sources);
        spinnerSource.setAdapter(adapter);
        spinnerSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) {
                currentSource = pos == 0 ? "modrinth" : "curseforge";
            }
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    void search() {
        String query = etSearch.getText().toString().trim();
        if (query.isEmpty()) {
            tvStatus.setText("⚠ Enter a name to search");
            return;
        }
        tvStatus.setText("🔍 Searching " + currentSource + " for " + currentTab + "...");
        modItems.clear();
        updateList();
        if (currentSource.equals("modrinth")) searchModrinth(query);
        else searchCurseForge(query);
    }

    String getModrinthType() {
        switch (currentTab) {
            case "shaders": return "shader";
            case "resourcepacks": return "resourcepack";
            case "worlds": return "world";
            default: return "mod";
        }
    }

    String getTargetFolder() {
        String instance = spinnerInstance.getSelectedItem() != null
            ? spinnerInstance.getSelectedItem().toString() : "Default";
        String base = instance.equals("Default")
            ? pojavPath + ".minecraft/"
            : pojavPath + "instances/" + instance + "/.minecraft/";
        switch (currentTab) {
            case "shaders": return base + "shaderpacks/";
            case "resourcepacks": return base + "resourcepacks/";
            case "worlds": return base + "saves/";
            default: return base + "mods/";
        }
    }

    void searchModrinth(String query) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                String type = getModrinthType();
                String encodedQuery = query.replace(" ", "%20");
                String url = "https://api.modrinth.com/v2/search"
                    + "?query=" + encodedQuery
                    + "&limit=20"
                    + "&facets=[[\"project_type:" + type + "\"]]";

                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "PojavModManager/1.0")
                    .build();

                Response response = client.newCall(request).execute();
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray hits = json.getJSONArray("hits");

                modItems.clear();
                for (int i = 0; i < hits.length(); i++) {
                    JSONObject mod = hits.getJSONObject(i);
                    String name = mod.getString("title");
                    String desc = mod.optString("description", "");
                    String slug = mod.getString("slug");
                    String iconUrl = mod.optString("icon_url", "");

                    JSONArray loaders = mod.optJSONArray("loaders");
                    JSONArray gameVersions = mod.optJSONArray("game_versions");

                    StringBuilder loadersStr = new StringBuilder();
                    if (loaders != null) {
                        for (int j = 0; j < loaders.length(); j++) {
                            if (j > 0) loadersStr.append(", ");
                            String l = loaders.getString(j);
                            loadersStr.append(Character.toUpperCase(l.charAt(0))).append(l.substring(1));
                        }
                    }
                    String latestMC = gameVersions != null && gameVersions.length() > 0
                        ? gameVersions.getString(gameVersions.length() - 1) : "?";

                    String req = loadersStr.length() > 0
                        ? "Loader: " + loadersStr + "  |  MC: " + latestMC
                        : "MC: " + latestMC;

                    ModItem item = new ModItem(name, desc, req, slug, "modrinth");
                    item.iconUrl = iconUrl;
                    modItems.add(item);
                }

                String msg = "Found " + modItems.size() + " results";
                runOnUiThread(() -> { tvStatus.setText(msg); updateList(); });

            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("❌ " + e.getMessage()));
            }
        }).start();
    }

    void searchCurseForge(String query) {
        if (CURSEFORGE_API_KEY.isEmpty()) {
            tvStatus.setText("❌ CurseForge needs API Key");
            return;
        }
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                String url = "https://api.curseforge.com/v1/mods/search?gameId=432&searchFilter="
                    + query.replace(" ", "%20") + "&pageSize=20";
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("x-api-key", CURSEFORGE_API_KEY)
                    .build();
                Response response = client.newCall(request).execute();
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray data = json.getJSONArray("data");
                modItems.clear();
                for (int i = 0; i < data.length(); i++) {
                    JSONObject mod = data.getJSONObject(i);
                    String name = mod.getString("name");
                    String desc = mod.optString("summary", "");
                    String id = String.valueOf(mod.getInt("id"));
                    String iconUrl = "";
                    JSONObject logo = mod.optJSONObject("logo");
                    if (logo != null) iconUrl = logo.optString("thumbnailUrl", "");
                    ModItem item = new ModItem(name, desc, "Check page for requirements", id, "curseforge");
                    item.iconUrl = iconUrl;
                    modItems.add(item);
                }
                String msg = "Found " + modItems.size() + " results";
                runOnUiThread(() -> { tvStatus.setText(msg); updateList(); });
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("❌ " + e.getMessage()));
            }
        }).start();
    }

    void updateList() {
        String instance = spinnerInstance.getSelectedItem() != null
            ? spinnerInstance.getSelectedItem().toString() : "Default";
        ModAdapter adapter = new ModAdapter(this, modItems, getTargetFolder());
        listMods.setAdapter(adapter);
    }

    void loadInstances() {
        File instancesDir = new File(pojavPath + "instances/");
        ArrayList<String> instances = new ArrayList<>();
        instances.add("Default");
        if (instancesDir.exists() && instancesDir.listFiles() != null) {
            for (File f : instancesDir.listFiles()) {
                if (f.isDirectory()) instances.add(f.getName());
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, instances);
        spinnerInstance.setAdapter(adapter);
    }

    void openSettings() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(pojavPath);
        input.setTextColor(0xFFFFFFFF);
        input.setBackgroundColor(0xFF1e1e2e);
        input.setPadding(20, 20, 20, 20);

        new AlertDialog.Builder(this)
            .setTitle("⚙ Download Path")
            .setMessage("Set Pojav Launcher path:")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String newPath = input.getText().toString().trim();
                if (!newPath.endsWith("/")) newPath += "/";
                pojavPath = newPath;
                prefs.edit().putString("pojav_path", pojavPath).apply();
                loadInstances();
                Toast.makeText(this, "✅ Path saved!", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 1);
            }
        }
    }
}
