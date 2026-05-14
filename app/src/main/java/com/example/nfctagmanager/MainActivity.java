package com.example.nfctagmanager;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends android.app.Activity {
    private static final String PREFS = "nfc_tags";
    private static final String KEY_TAGS = "tags";

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private TextView status;
    private TextView savedTags;
    private EditText labelInput;
    private EditText writeInput;
    private EditText emulatedInput;
    private JSONObject lastScan;
    private boolean writeMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );

        buildUi();
        renderSavedTags();
        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
        updateNfcStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFFF8FAFC);

        TextView title = new TextView(this);
        title.setText("NFC HCE");
        title.setTextSize(24);
        title.setTextColor(0xFF0F172A);
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        status = bodyText();
        root.addView(status, matchWrap());

        emulatedInput = new EditText(this);
        emulatedInput.setHint("Texto NDEF emulado pelo celular");
        emulatedInput.setSingleLine(false);
        emulatedInput.setMinLines(2);
        emulatedInput.setText(prefs().getString(MyHostApduService.KEY_EMULATED_TEXT, "NFC HCE ativo"));
        root.addView(emulatedInput, matchWrap());

        Button saveEmulatedButton = new Button(this);
        saveEmulatedButton.setText("Atualizar emulacao HCE");
        saveEmulatedButton.setOnClickListener(v -> saveEmulatedText());
        root.addView(saveEmulatedButton, matchWrap());

        labelInput = new EditText(this);
        labelInput.setHint("Apelido da tag");
        labelInput.setSingleLine(true);
        root.addView(labelInput, matchWrap());

        Button saveButton = new Button(this);
        saveButton.setText("Salvar ultima leitura");
        saveButton.setOnClickListener(v -> saveLastScan());
        root.addView(saveButton, matchWrap());

        writeInput = new EditText(this);
        writeInput.setHint("Texto para gravar em tag NDEF sua");
        writeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        writeInput.setMinLines(2);
        root.addView(writeInput, matchWrap());

        Button writeButton = new Button(this);
        writeButton.setText("Gravar texto na proxima tag");
        writeButton.setOnClickListener(v -> {
            writeMode = true;
            Toast.makeText(this, "Aproxime uma tag NDEF gravavel.", Toast.LENGTH_LONG).show();
        });
        root.addView(writeButton, matchWrap());

        Button settingsButton = new Button(this);
        settingsButton.setText("Abrir configuracoes NFC");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
        root.addView(settingsButton, matchWrap());

        savedTags = bodyText();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(savedTags);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
    }

    private TextView bodyText() {
        TextView view = new TextView(this);
        view.setTextSize(15);
        view.setTextColor(0xFF334155);
        view.setPadding(0, dp(10), 0, dp(10));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void updateNfcStatus() {
        if (nfcAdapter == null) {
            status.setText("Este aparelho nao informa suporte NFC para apps Android.");
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            status.setText("NFC desativado. Ative nas configuracoes para ler ou gravar tags.");
            return;
        }
        String hceStatus = getHceStatus();
        status.setText("NFC ativo. " + hceStatus + "\nAproxime uma tag NDEF para ler/gravar ou aproxime o celular de um leitor compativel com Type 4 NDEF.");
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;

        if (writeMode) {
            writeMode = false;
            writeTextTag(tag, writeInput.getText().toString());
            return;
        }

        try {
            lastScan = scanTag(tag);
            status.setText(formatTag(lastScan));
        } catch (JSONException e) {
            Toast.makeText(this, "Falha ao ler tag.", Toast.LENGTH_SHORT).show();
        }
    }

    private JSONObject scanTag(Tag tag) throws JSONException {
        JSONObject scan = new JSONObject();
        scan.put("fingerprint", sha256(tag.getId()));
        scan.put("techs", new JSONArray(Arrays.asList(tag.getTechList())));
        scan.put("ndefText", readNdefText(tag));
        scan.put("scannedAt", DateFormat.getDateTimeInstance().format(new Date()));
        return scan;
    }

    private String readNdefText(Tag tag) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) return "";
        try {
            ndef.connect();
            NdefMessage message = ndef.getNdefMessage();
            if (message == null) return "";
            StringBuilder builder = new StringBuilder();
            for (NdefRecord record : message.getRecords()) {
                String text = parseTextRecord(record);
                if (!text.isEmpty()) {
                    if (builder.length() > 0) builder.append("\n");
                    builder.append(text);
                }
            }
            return builder.toString();
        } catch (IOException | android.nfc.FormatException e) {
            return "";
        } finally {
            try {
                ndef.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String parseTextRecord(NdefRecord record) {
        if (record.getTnf() != NdefRecord.TNF_WELL_KNOWN) return "";
        if (!Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)) return "";

        byte[] payload = record.getPayload();
        if (payload.length == 0) return "";

        boolean utf16 = (payload[0] & 0x80) != 0;
        int languageLength = payload[0] & 0x3F;
        int textStart = 1 + languageLength;
        if (textStart > payload.length) return "";

        Charset charset = utf16 ? StandardCharsets.UTF_16 : StandardCharsets.UTF_8;
        return new String(payload, textStart, payload.length - textStart, charset);
    }

    private void saveLastScan() {
        if (lastScan == null) {
            Toast.makeText(this, "Leia uma tag antes de salvar.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String label = labelInput.getText().toString().trim();
            if (label.isEmpty()) label = "Tag sem nome";
            lastScan.put("label", label);

            JSONArray tags = loadTags();
            tags.put(lastScan);
            prefs().edit().putString(KEY_TAGS, tags.toString()).apply();
            renderSavedTags();
            Toast.makeText(this, "Tag salva localmente.", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Falha ao salvar.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveEmulatedText() {
        String text = emulatedInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Digite um texto para emular.", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs().edit().putString(MyHostApduService.KEY_EMULATED_TEXT, text).apply();
        Toast.makeText(this, "Texto HCE atualizado.", Toast.LENGTH_SHORT).show();
        updateNfcStatus();
    }

    private String getHceStatus() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
            return "HCE indisponivel nesta versao do Android.";
        }

        CardEmulation cardEmulation = CardEmulation.getInstance(nfcAdapter);
        ComponentName service = new ComponentName(this, MyHostApduService.class);
        boolean isDefault = cardEmulation.isDefaultServiceForCategory(service, CardEmulation.CATEGORY_OTHER);
        if (isDefault) {
            return "Emulacao HCE pronta.";
        }

        return "HCE instalado; defina este app como servico NFC padrao se o leitor nao selecionar o AID.";
    }

    private void renderSavedTags() {
        JSONArray tags = loadTags();
        StringBuilder builder = new StringBuilder("Tags salvas\n\n");
        for (int i = 0; i < tags.length(); i++) {
            JSONObject tag = tags.optJSONObject(i);
            if (tag == null) continue;
            builder.append(i + 1).append(". ")
                    .append(tag.optString("label", "Tag"))
                    .append("\nImpressao: ")
                    .append(tag.optString("fingerprint", ""))
                    .append("\nNDEF: ")
                    .append(emptyFallback(tag.optString("ndefText", "")))
                    .append("\nLida em: ")
                    .append(tag.optString("scannedAt", ""))
                    .append("\n\n");
        }
        savedTags.setText(builder.toString());
    }

    private String formatTag(JSONObject tag) {
        return "Ultima leitura\n"
                + "Impressao: " + tag.optString("fingerprint") + "\n"
                + "NDEF: " + emptyFallback(tag.optString("ndefText")) + "\n"
                + "Tecnologias: " + tag.optJSONArray("techs");
    }

    private String emptyFallback(String value) {
        return value == null || value.isEmpty() ? "(sem texto NDEF legivel)" : value;
    }

    private JSONArray loadTags() {
        try {
            return new JSONArray(prefs().getString(KEY_TAGS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void writeTextTag(Tag tag, String text) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "Digite um texto antes de gravar.", Toast.LENGTH_SHORT).show();
            return;
        }

        NdefMessage message = new NdefMessage(new NdefRecord[]{
                NdefRecord.createTextRecord("pt-BR", text)
        });

        Ndef ndef = Ndef.get(tag);
        NdefFormatable formatable = null;
        try {
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    Toast.makeText(this, "Tag NDEF nao gravavel.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (ndef.getMaxSize() < message.toByteArray().length) {
                    Toast.makeText(this, "Mensagem grande demais para esta tag.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ndef.writeNdefMessage(message);
                Toast.makeText(this, "Texto gravado na tag.", Toast.LENGTH_SHORT).show();
                return;
            }

            formatable = NdefFormatable.get(tag);
            if (formatable != null) {
                formatable.connect();
                formatable.format(message);
                Toast.makeText(this, "Tag formatada e gravada.", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Esta tag nao aceita gravacao NDEF via Android.", Toast.LENGTH_SHORT).show();
        } catch (IOException | android.nfc.FormatException e) {
            Toast.makeText(this, "Falha ao gravar tag.", Toast.LENGTH_SHORT).show();
        } finally {
            try {
                if (ndef != null) ndef.close();
            } catch (Exception ignored) {
            }
            try {
                if (formatable != null) formatable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
