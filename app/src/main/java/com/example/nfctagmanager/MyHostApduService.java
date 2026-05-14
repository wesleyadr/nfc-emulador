package com.example.nfctagmanager;

import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

public class MyHostApduService extends HostApduService {
    static final String PREFS = "nfc_tags";
    static final String KEY_EMULATED_TEXT = "emulated_ndef_text";

    private static final String TAG = "HCE";
    private static final byte[] STATUS_OK = hex("9000");
    private static final byte[] STATUS_FILE_NOT_FOUND = hex("6A82");
    private static final byte[] STATUS_WRONG_LENGTH = hex("6700");
    private static final byte[] STATUS_CONDITIONS_NOT_SATISFIED = hex("6985");
    private static final byte[] STATUS_INS_NOT_SUPPORTED = hex("6D00");

    private static final byte[] NDEF_AID = hex("D2760000850101");
    private static final byte[] CUSTOM_AID = hex("F001020304050607");
    private static final byte[] CC_FILE_ID = hex("E103");
    private static final byte[] NDEF_FILE_ID = hex("E104");

    private enum SelectedFile {
        NONE,
        CC,
        NDEF
    }

    private SelectedFile selectedFile = SelectedFile.NONE;

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        Log.d(TAG, "Command: " + toHex(commandApdu));

        if (commandApdu == null || commandApdu.length < 4) {
            return STATUS_WRONG_LENGTH;
        }

        int cla = unsigned(commandApdu[0]);
        int ins = unsigned(commandApdu[1]);
        int p1 = unsigned(commandApdu[2]);
        int p2 = unsigned(commandApdu[3]);

        if (cla != 0x00) {
            return STATUS_INS_NOT_SUPPORTED;
        }

        if (ins == 0xA4) {
            return handleSelect(commandApdu, p1, p2);
        }

        if (ins == 0xB0) {
            return handleReadBinary(commandApdu, p1, p2);
        }

        Log.d(TAG, "Unsupported INS: " + String.format("%02X", ins));
        return STATUS_INS_NOT_SUPPORTED;
    }

    @Override
    public void onDeactivated(int reason) {
        selectedFile = SelectedFile.NONE;
        Log.d(TAG, "Deactivated. Reason: " + reason);
    }

    private byte[] handleSelect(byte[] apdu, int p1, int p2) {
        if (apdu.length < 5) {
            return STATUS_WRONG_LENGTH;
        }

        int lc = unsigned(apdu[4]);
        if (apdu.length < 5 + lc) {
            return STATUS_WRONG_LENGTH;
        }

        byte[] data = Arrays.copyOfRange(apdu, 5, 5 + lc);

        if (p1 == 0x04 && (p2 == 0x00 || p2 == 0x0C)
                && (Arrays.equals(data, NDEF_AID) || Arrays.equals(data, CUSTOM_AID))) {
            selectedFile = SelectedFile.NONE;
            return STATUS_OK;
        }

        if (p1 == 0x00 && (p2 == 0x00 || p2 == 0x0C)) {
            if (Arrays.equals(data, CC_FILE_ID)) {
                selectedFile = SelectedFile.CC;
                return STATUS_OK;
            }
            if (Arrays.equals(data, NDEF_FILE_ID)) {
                selectedFile = SelectedFile.NDEF;
                return STATUS_OK;
            }
        }

        return STATUS_FILE_NOT_FOUND;
    }

    private byte[] handleReadBinary(byte[] apdu, int p1, int p2) {
        if (apdu.length < 5) {
            return STATUS_WRONG_LENGTH;
        }

        int offset = (p1 << 8) | p2;
        int le = unsigned(apdu[4]);
        if (le == 0) {
            le = 256;
        }

        byte[] file = selectedFile == SelectedFile.CC ? buildCapabilityContainer()
                : selectedFile == SelectedFile.NDEF ? buildNdefFile()
                : null;

        if (file == null) {
            return STATUS_CONDITIONS_NOT_SATISFIED;
        }

        if (offset > file.length) {
            return STATUS_WRONG_LENGTH;
        }

        int end = Math.min(file.length, offset + le);
        byte[] payload = Arrays.copyOfRange(file, offset, end);
        return concat(payload, STATUS_OK);
    }

    private byte[] buildCapabilityContainer() {
        return new byte[]{
                0x00, 0x0F,
                0x20,
                0x00, (byte) 0xFF,
                0x00, (byte) 0xFF,
                0x04,
                0x06,
                NDEF_FILE_ID[0], NDEF_FILE_ID[1],
                0x00, (byte) 0xFE,
                0x00,
                (byte) 0xFF
        };
    }

    private byte[] buildNdefFile() {
        byte[] message = buildNdefMessage().toByteArray();
        byte[] file = new byte[message.length + 2];
        file[0] = (byte) ((message.length >> 8) & 0xFF);
        file[1] = (byte) (message.length & 0xFF);
        System.arraycopy(message, 0, file, 2, message.length);
        return file;
    }

    private NdefMessage buildNdefMessage() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String text = prefs.getString(KEY_EMULATED_TEXT, "NFC HCE ativo");
        return new NdefMessage(new NdefRecord[]{NdefRecord.createTextRecord("pt-BR", text)});
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static byte[] hex(String value) {
        int length = value.length();
        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }
        return bytes;
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(bytes.length * 3);
        for (byte value : bytes) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", value));
        }
        return builder.toString();
    }
}
