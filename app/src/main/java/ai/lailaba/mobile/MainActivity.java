package ai.lailaba.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String DEFAULT_BASE = "https://100.57.251.241.nip.io/api/v1";
    private static final String PREFS = "lailaba_settings";
    private LinearLayout root, messagesBox;
    private ScrollView scroll;
    private EditText promptInput, baseInput, keyInput, modelInput;
    private TextView status;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final JSONArray messages = new JSONArray();
    private JSONArray pendingCalls;
    private int pendingCallIndex;
    private int turnDepth;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        appendMessage("Lailaba", "I'm your device assistant. I can help with contacts, message previews, dialing, opening apps/settings, and optional on-screen actions. Android permissions still apply. Calls and message composition require your confirmation.");
        loadPrefs();
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18, 18, 18, 12); root.setBackgroundColor(Color.BLACK);
        TextView title = new TextView(this); title.setText("LAILABA AI  ·  DEVICE AGENT"); title.setTextColor(Color.WHITE); title.setTextSize(19); title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(0, 4, 0, 12); title.setTypeface(null, 1); root.addView(title);
        status = new TextView(this); status.setText("● Ready · Device actions require permission"); status.setTextColor(Color.LTGRAY); status.setTextSize(12); status.setPadding(0, 0, 0, 10); root.addView(status);
        scroll = new ScrollView(this); scroll.setFillViewport(false); messagesBox = new LinearLayout(this); messagesBox.setOrientation(LinearLayout.VERTICAL); messagesBox.setPadding(0, 8, 0, 8); scroll.addView(messagesBox);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        promptInput = new EditText(this); promptInput.setHint("Tell the agent what you want to do…"); promptInput.setTextColor(Color.WHITE); promptInput.setHintTextColor(0xFF999999); promptInput.setBackgroundColor(0xFF2B2B2B); promptInput.setPadding(12, 10, 12, 10); promptInput.setMinLines(2); promptInput.setGravity(Gravity.TOP); root.addView(promptInput, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button send = button("Send task"); send.setOnClickListener(v -> sendPrompt()); row.addView(send, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = button("Provider setup"); settings.setOnClickListener(v -> showProviderDialog()); row.addView(settings, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(row);
        LinearLayout perms = new LinearLayout(this); perms.setOrientation(LinearLayout.HORIZONTAL);
        Button contacts = button("Allow contacts"); contacts.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 101)); perms.addView(contacts, new LinearLayout.LayoutParams(0, -2, 1));
        Button sms = button("Allow SMS read"); sms.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_SMS}, 102)); perms.addView(sms, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(perms);
        Button accessibility = button("Enable on-screen actions in Android Settings"); accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); root.addView(accessibility);
        setContentView(root);
    }

    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2B2B2B)); return b; }
    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (p.getString("base", null) == null) p.edit().putString("base", DEFAULT_BASE).apply();
        updateStatus();
    }
    private void updateStatus() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String base = p.getString("base", DEFAULT_BASE);
        String model = p.getString("model", "");
        status.setText("● " + (SecretStore.getApiKey(this).isEmpty() ? "API key not configured" : "API key saved") + " · " + (base.contains("100.57.251.241.nip.io") ? "Lailaba AI" : "Custom provider") + (model.isEmpty() ? " · model auto-discovery" : " · " + model) + " · accessibility " + (LailabaAccessibilityService.isEnabled() ? "enabled" : "off"));
    }

    private void showProviderDialog() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(20, 10, 20, 0);
        baseInput = field("API base URL", p.getString("base", DEFAULT_BASE), false); form.addView(baseInput);
        keyInput = field("API key (stored encrypted on this device)", SecretStore.getApiKey(this), true); form.addView(keyInput);
        modelInput = field("Model name (leave blank for Lailaba auto-discovery)", p.getString("model", ""), false); form.addView(modelInput);
        new AlertDialog.Builder(this).setTitle("Provider setup").setMessage("Use Lailaba AI or an OpenAI-compatible provider. HTTPS is required. Provider credentials are sent only to the base URL you enter.").setView(form)
            .setNegativeButton("Cancel", null).setPositiveButton("Validate & save", (d, w) -> {
                String base = baseInput.getText().toString().trim().replaceAll("/+$", ""); String key = keyInput.getText().toString().trim(); String model = modelInput.getText().toString().trim();
                if (!base.startsWith("https://") || key.isEmpty()) { toast("Enter an HTTPS base URL and API key."); return; }
                executor.execute(() -> validateAndSave(base, key, model));
            }).show();
    }
    private EditText field(String hint, String value, boolean secret) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setTextColor(Color.WHITE); e.setHintTextColor(0xFF999999); e.setSingleLine(true); e.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); return e; }
    private void validateAndSave(String base, String key, String model) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(base + "/models").openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(10000); c.setReadTimeout(12000); c.setRequestProperty("Authorization", "Bearer " + key); int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Provider validation failed (HTTP " + code + "). Check the URL and key.");
            String body = readBody(c.getInputStream()); JSONObject json = new JSONObject(body);
            if (model.isEmpty() && !base.contains("100.57.251.241.nip.io")) {
                JSONArray data = json.optJSONArray("data"); if (data != null && data.length() > 0) model = data.getJSONObject(0).optString("id", "");
                if (model.isEmpty()) throw new Exception("No model returned. Enter a model name in Provider setup.");
            }
            SecretStore.saveApiKey(this, key); getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("base", base).putString("model", model).apply();
            String finalModel = model;
            runOnUiThread(() -> { updateStatus(); toast("Provider saved and API key validated" + (finalModel.isEmpty() ? "" : " · model " + finalModel)); });
        } catch (Exception e) { runOnUiThread(() -> toast(e.getMessage() == null ? "Provider validation failed" : e.getMessage())); }
    }

    private void sendPrompt() {
        String prompt = promptInput.getText().toString().trim(); if (prompt.isEmpty()) return;
        if (SecretStore.getApiKey(this).isEmpty()) { showProviderDialog(); return; }
        promptInput.setText(""); appendMessage("You", prompt);
        if (messages.length() == 0) messages.put(systemMessage());
        messages.put(new JSONObjectSafe().message("user", prompt)); turnDepth = 0; status.setText("◉ Agent working…"); requestAgent();
    }
    private JSONObject systemMessage() {
        JSONObject o = new JSONObject();
        try { o.put("role", "system"); o.put("content", "You are Lailaba AI Device Agent on the user's Android phone. Use provided tools for device actions instead of claiming to have done them. Never claim access unless a tool succeeds. Explain permissions when needed. Ask a concise clarification when a person/number/action is ambiguous. Reading contacts/SMS is sensitive and requires explicit user approval. Calls open the dialer; SMS opens the compose screen and the user must press Send. Do not try to bypass Android security or accessibility settings. Use the minimum necessary actions."); } catch (Exception ignored) { }
        return o;
    }
    private void requestAgent() {
        if (++turnDepth > 8) { status.setText("● Ready"); appendMessage("Lailaba", "Stopped after the tool-call safety limit. Send a follow-up if more work is needed."); return; }
        status.setText("◉ Thinking…");
        executor.execute(() -> {
            try {
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE); String base = p.getString("base", DEFAULT_BASE); String key = SecretStore.getApiKey(this); String model = p.getString("model", "");
                if (model.isEmpty()) model = discoverModel(base, key);
                JSONObject payload = new JSONObject(); payload.put("model", model); payload.put("messages", messages); payload.put("tools", toolSchemas()); payload.put("tool_choice", "auto"); payload.put("temperature", 0.2);
                JSONObject response = postJson(base + "/chat/completions", key, payload); JSONArray choices = response.getJSONArray("choices"); JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                JSONArray calls = message.optJSONArray("tool_calls");
                if (calls != null && calls.length() > 0) {
                    messages.put(message); pendingCalls = calls; pendingCallIndex = 0; runOnUiThread(this::processNextToolCall);
                } else {
                    String content = message.optString("content", "(No text response)"); messages.put(message); runOnUiThread(() -> { appendMessage("Lailaba", content); status.setText("● Ready"); updateStatus(); });
                }
            } catch (Exception e) { runOnUiThread(() -> { appendMessage("Error", e.getMessage() == null ? "Request failed" : e.getMessage()); status.setText("● Request failed"); }); }
        });
    }
    private String discoverModel(String base, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/models").openConnection(); c.setRequestMethod("GET"); c.setConnectTimeout(10000); c.setReadTimeout(12000); c.setRequestProperty("Authorization", "Bearer " + key); int code = c.getResponseCode(); if (code < 200 || code >= 300) throw new Exception("Model discovery failed (HTTP " + code + ")");
        JSONArray data = new JSONObject(readBody(c.getInputStream())).optJSONArray("data"); if (data == null || data.length() == 0) throw new Exception("Provider returned no models; configure a model name."); return data.getJSONObject(0).optString("id", "");
    }
    private JSONObject postJson(String url, String key, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setDoOutput(true); c.setRequestProperty("Authorization", "Bearer " + key); c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode(); InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(); String response = readBody(stream); if (code < 200 || code >= 300) throw new Exception("API error HTTP " + code + ": " + response.substring(0, Math.min(response.length(), 500))); return new JSONObject(response);
    }
    private String readBody(InputStream in) throws Exception { StringBuilder b = new StringBuilder(); try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) { String line; while ((line = r.readLine()) != null) b.append(line); } return b.toString(); }

    private JSONArray toolSchemas() throws Exception {
        JSONArray a = new JSONArray();
        a.put(tool("get_contacts", "Search contacts by name. Requires Contacts permission and user approval.", props("query", "string", "Name or partial name"), new String[]{"query"}));
        a.put(tool("read_sms", "Read recent SMS messages matching a sender/query. Requires SMS permission and user approval.", props("query", "string", "Optional sender/name/number filter"), new String[]{}));
        a.put(tool("dial_number", "Open Android dialer with a number filled in. Does not place the call automatically.", props("number", "string", "Phone number"), new String[]{"number"}));
        a.put(tool("compose_sms", "Open SMS app with recipient and message filled in. User must review and press Send.", props2("number", "string", "Phone number", "message", "string", "Message text"), new String[]{"number", "message"}));
        a.put(tool("open_settings", "Open an Android settings page such as wifi, bluetooth, display, or sound. User changes settings manually.", props("page", "string", "wifi, bluetooth, display, sound, location, accessibility, or general"), new String[]{"page"}));
        a.put(tool("open_app", "Launch an installed app by its Android package name, e.g. com.android.settings.", props("package_name", "string", "Installed Android package name"), new String[]{"package_name"}));
        a.put(tool("tap_text", "Tap a visible, matching on-screen label using the user-enabled Accessibility service. The service must be enabled by the user.", props("text", "string", "Visible label to tap"), new String[]{"text"}));
        return a;
    }
    private JSONObject tool(String name, String description, JSONObject properties, String[] required) throws Exception { JSONObject fn = new JSONObject(); fn.put("name", name); fn.put("description", description); JSONObject params = new JSONObject(); params.put("type", "object"); params.put("properties", properties); JSONArray req = new JSONArray(); for (String r : required) req.put(r); params.put("required", req); fn.put("parameters", params); JSONObject wrap = new JSONObject(); wrap.put("type", "function"); wrap.put("function", fn); return wrap; }
    private JSONObject props(String name, String type, String desc) throws Exception { JSONObject p = new JSONObject(); JSONObject x = new JSONObject(); x.put("type", type); x.put("description", desc); p.put(name, x); return p; }
    private JSONObject props2(String n1, String t1, String d1, String n2, String t2, String d2) throws Exception { JSONObject p = props(n1,t1,d1); JSONObject x = new JSONObject(); x.put("type",t2); x.put("description",d2); p.put(n2,x); return p; }

    private void processNextToolCall() {
        if (pendingCalls == null || pendingCallIndex >= pendingCalls.length()) { pendingCalls = null; requestAgent(); return; }
        try {
            JSONObject call = pendingCalls.getJSONObject(pendingCallIndex); JSONObject fn = call.getJSONObject("function"); String name = fn.getString("name"); JSONObject args = new JSONObject(fn.optString("arguments", "{}"));
            String summary = name + "\n\n" + args.toString(2) + "\n\nAllow Lailaba to perform this action?";
            new AlertDialog.Builder(this).setTitle("Approve device action").setMessage(summary).setNegativeButton("Reject", (d,w) -> finishToolCall(call, "Action rejected by user."))
                .setPositiveButton("Approve", (d,w) -> executor.execute(() -> { String result = executeTool(name, args); runOnUiThread(() -> finishToolCall(call, result)); })).setOnCancelListener(d -> finishToolCall(call, "Action cancelled by user.")).show();
        } catch (Exception e) { appendMessage("Error", "Invalid tool call: " + e.getMessage()); pendingCallIndex++; processNextToolCall(); }
    }
    private void finishToolCall(JSONObject call, String result) {
        try { JSONObject toolMessage = new JSONObject(); toolMessage.put("role", "tool"); toolMessage.put("tool_call_id", call.getString("id")); toolMessage.put("content", result); messages.put(toolMessage); appendMessage("Tool", result); } catch (Exception ignored) { }
        pendingCallIndex++; processNextToolCall();
    }
    private String executeTool(String name, JSONObject a) {
        try {
            switch (name) {
                case "get_contacts": return getContacts(a.optString("query", ""));
                case "read_sms": return readSms(a.optString("query", ""));
                case "dial_number": { String n = a.getString("number"); runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))))); return "Opened dialer for " + n + ". The user must press the call button."; }
                case "compose_sms": { String n = a.getString("number"), body = a.getString("message"); runOnUiThread(() -> { Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(n))); i.putExtra("sms_body", body); startActivity(i); }); return "Opened SMS composer for " + n + ". The user must review and press Send."; }
                case "open_settings": return openSettings(a.optString("page", "general"));
                case "open_app": return openApp(a.getString("package_name"));
                case "tap_text": { String t = a.getString("text"); boolean ok = LailabaAccessibilityService.requestTapText(t); if (!ok) { runOnUiThread(() -> toast("Accessibility service is off or the text is not currently visible.")); return "Tap was not completed. Ask the user to enable the Lailaba accessibility service and bring the target app to the foreground."; } return "Requested tap on visible label: " + t; }
                default: return "Unsupported tool: " + name;
            }
        } catch (SecurityException e) { return "Permission denied. Ask the user to grant the required Android permission in the app."; }
        catch (Exception e) { return "Tool failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()); }
    }
    private String getContacts(String query) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return "Contacts permission is not granted. Ask user to tap Allow contacts, then repeat the request.";
        StringBuilder out = new StringBuilder(); int count = 0;
        try (Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (c != null) while (c.moveToNext() && count < 30) { String n = c.getString(0), number = c.getString(1); if (query.isEmpty() || n.toLowerCase().contains(query.toLowerCase()) || number.contains(query)) { out.append(n).append(" — ").append(number).append('\n'); count++; } }
        } catch (Exception e) { return "Unable to read contacts: " + e.getMessage(); }
        return count == 0 ? "No matching contacts found." : out.toString();
    }
    private String readSms(String query) {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return "SMS read permission is not granted. Ask user to tap Allow SMS read, then repeat the request.";
        StringBuilder out = new StringBuilder(); int count = 0;
        try (Cursor c = getContentResolver().query(Uri.parse("content://sms/inbox"), new String[]{"address", "body", "date"}, null, null, "date DESC")) {
            if (c != null) while (c.moveToNext() && count < 20) { String address = c.getString(0), body = c.getString(1); if (query.isEmpty() || address.contains(query) || body.toLowerCase().contains(query.toLowerCase())) { out.append("From ").append(address).append(": ").append(body).append('\n'); count++; } }
        } catch (Exception e) { return "Unable to read SMS: " + e.getMessage(); }
        return count == 0 ? "No matching inbox messages found." : out.toString();
    }
    private String openSettings(String page) {
        String p = page.toLowerCase(); String action;
        switch (p) {
            case "wifi": action = Settings.ACTION_WIFI_SETTINGS; break;
            case "bluetooth": action = Settings.ACTION_BLUETOOTH_SETTINGS; break;
            case "display": action = Settings.ACTION_DISPLAY_SETTINGS; break;
            case "sound": action = Settings.ACTION_SOUND_SETTINGS; break;
            case "location": action = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
            case "accessibility": action = Settings.ACTION_ACCESSIBILITY_SETTINGS; break;
            default: action = Settings.ACTION_SETTINGS;
        }
        runOnUiThread(() -> startActivity(new Intent(action))); return "Opened Android " + p + " settings. The user must review and make changes.";
    }
    private String openApp(String packageName) {
        Intent i = getPackageManager().getLaunchIntentForPackage(packageName);
        if (i == null) return "No launchable installed app found for package " + packageName;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); runOnUiThread(() -> startActivity(i)); return "Opened app " + packageName;
    }
    private void appendMessage(String who, String text) {
        TextView tv = new TextView(this); tv.setText(who + "\n" + text); tv.setTextColor(who.equals("You") ? Color.WHITE : 0xFFE0E0E0); tv.setTextSize(14); tv.setPadding(12, 10, 12, 10); tv.setBackgroundColor(who.equals("You") ? 0xFF202020 : 0xFF111111);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, 8); messagesBox.addView(tv, lp); if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() { super.onResume(); if (status != null) updateStatus(); }
    @Override protected void onDestroy() { super.onDestroy(); executor.shutdownNow(); }

    private static final class JSONObjectSafe {
        JSONObject message(String role, String content) { JSONObject o = new JSONObject(); try { o.put("role", role); o.put("content", content); } catch (Exception ignored) {} return o; }
    }
}
