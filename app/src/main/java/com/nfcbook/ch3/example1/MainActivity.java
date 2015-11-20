package com.nfcbook.ch3.example1;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private NfcAdapter nfcAdapter;
    private boolean isWriteMode = false;
    private int writeCounter = 1;

    /**
     * Checks if the device has an NFC Module installed
     */
    boolean hasNfc() {
        boolean hasFeature = getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
        boolean isEnabled = NfcAdapter.getDefaultAdapter(this).isEnabled();
        return hasFeature && isEnabled;
    }

    /**
     * We create a PendingIntent object so that the Android system can populate it with the tag
     * details and use the IntentFilter object to let the foreground dispatch system know what
     * intent we want to intercept. If the intent doesn’t match our required filters, the foreground
     * dispatch system will fall back to the intent dispatch system.
     */
    private void enableForegroundDispatch() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        IntentFilter[] intentFilter = new IntentFilter[]{};
        String[][] techList = new String[][]{{android.nfc.tech.Ndef.class.getName()}, {android.nfc.tech.NdefFormatable.class.getName()}};
        if (Build.DEVICE.matches(".*generic.*")) {
//clean up the tech filter when in emulator since it doesn't work properly.
            techList = null;
        }
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilter, techList);
    }

    /**
     * The first thing we need to do while writing a tag is to call the get(Tag tag) method from
     * the Ndef class, which will return an instance of the same class. Then, we need to open a
     * connection with the tag by calling the connect() method. With an open connection, we
     * can now write a NDEF message to the tag by calling the writeNdefMessage(NdefMessage
     * msg) method. Checking whether the tag is writable or not is always a good practice to
     * prevent unwanted exceptions.
     */
    private boolean formatTag(Tag tag, NdefMessage ndefMessage) {
        try {
            NdefFormatable ndefFormat = NdefFormatable.get(tag);
            if (ndefFormat != null) {
                ndefFormat.connect();
                ndefFormat.format(ndefMessage);
                ndefFormat.close();
                return true;
            }
        } catch (Exception e) {
            Log.e("formatTag", e.getMessage());
        }
        return false;
    }

    /**
     * Writes a NDEF message to a tag
     */
    private boolean writeNdefMessage(Tag tag, NdefMessage ndefMessage) {
        try {
            if (tag != null) {
                Ndef ndef = Ndef.get(tag);
                if (ndef == null) {
                    return formatTag(tag, ndefMessage);
                } else {
                    ndef.connect();
                    if (ndef.isWritable()) {
                        ndef.writeNdefMessage(ndefMessage);
                        ndef.close();
                        return true;
                    }
                    ndef.close();
                }
            }
        } catch (Exception e) {
            Log.e("formatTag", e.getMessage());
        }
        return false;
    }

    /**
     * Get the NDEF message from a tag object
     */
    public NdefMessage getNdefMessageFromTag(Tag tag) {
        NdefMessage ndefMessage = null;
        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            ndefMessage = ndef.getCachedNdefMessage();
        }
        return ndefMessage;
    }

    /**
     * Get the first record ob a NFC message
     */
    public NdefRecord getFirstNdefRecord(NdefMessage ndefMessage) {
        NdefRecord ndefRecord = null;
        NdefRecord[] ndefRecords = ndefMessage.getRecords();
        if (ndefRecords != null && ndefRecords.length > 0) {
            ndefRecord = ndefRecords[0];
        }
        return ndefRecord;
    }

    /**
     * Read the message from a tag and prints the message of whose first record
     */
    public void readNdefMessage(Tag tag) {
        NdefMessage ndefMessage = getNdefMessageFromTag(tag);
        if (ndefMessage != null) {
            NdefRecord ndefRecord = getFirstNdefRecord(ndefMessage);
            if (ndefRecord != null) {
                boolean isTextRecord = isNdefRecordOfTnfAndRdt(ndefRecord, NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT);
                if (isTextRecord) {
                    String tagContent = getTextFromNdefRecord(ndefRecord);
                    Toast.makeText(this, String.format("Content: %s", tagContent), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Record is not Text formatted.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "No Ndef record found.", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "No Ndef message found.", Toast.LENGTH_LONG).show();
        }
    }

    public boolean isNdefRecordOfTnfAndRdt(NdefRecord ndefRecord, short tnf, byte[] rdt) {
        return ndefRecord.getTnf() == tnf && Arrays.equals(ndefRecord.getType(), rdt);
    }

    /**
     * Get the the text data from an UTF8/16 NDEF record
     */
    public String getTextFromNdefRecord(NdefRecord ndefRecord) {
        String tagContent = null;
        try {
            byte[] payload = ndefRecord.getPayload();
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF- 16";
            int languageSize = payload[0] & 0063;
            tagContent = new String(payload, languageSize + 1, payload.length -
                    languageSize - 1, textEncoding);
        } catch (UnsupportedEncodingException e) {
            Log.e("getTextFromNdefRecord", e.getMessage(), e);
        }
        return tagContent;
    }

    /**
     * Verifies if any intent is an NFC intent
     */
    boolean isNfcIntent(Intent intent) {
        return intent.hasExtra(NfcAdapter.EXTRA_TAG);
    }

    /**
     * Creates a text record formatted in UTF 8 for a nfc tag
     */
    public NdefRecord createTextRecord(String content) {
        try {
            byte[] language;
            language = Locale.getDefault().getLanguage().getBytes("UTF-8");
            final byte[] text = content.getBytes("UTF-8");
            final int languageSize = language.length;
            final int textLength = text.length;
            final ByteArrayOutputStream payload = new ByteArrayOutputStream(1 +
                    languageSize + textLength);
            payload.write((byte) (languageSize & 0x1F));
            payload.write(language, 0, languageSize);
            payload.write(text, 0, textLength);
            return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload.toByteArray());
        } catch (UnsupportedEncodingException e) {
            Log.e("createTextRecord", e.getMessage());
        }
        return null;
    }

    /**
     * When the user leaves the application or the device enters sleep mode, the onPause method gets
     * called, which means that the app is no longer in active use; so, it’s a good practice to
     * disable the foreground dispatch system or unexpected behaviors can occur. If the user gets
     * back to the application, the onResume method gets called again, and with that, the
     * foreground dispatch system is started again.
     */
    @Override
    protected void onPause() {
        super.onPause();
        nfcAdapter.disableForegroundDispatch(this);
    }

    /**
     * When the activity receives an incoming intent, the
     * onNewIntent method gets called, so we need to override it and implement our code.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        try {
            if (isNfcIntent(intent)) {
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                if (isWriteMode) {
                    NdefRecord ndefRecord = createTextRecord("Tag content NR. " + (writeCounter++));
                    NdefMessage ndefMessage = new NdefMessage(new NdefRecord[]{ndefRecord});
                    if (writeNdefMessage(tag, ndefMessage)) {
                        Toast.makeText(this, "Tag written!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to write tag", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    readNdefMessage(tag);
                }
            }
        } catch (Exception e) {
            Log.e("onNewIntent", e.getMessage());
        }
    }

    /**
     * When the activity starts, the onResume method is called. Then, we enable the foreground
     * dispatch system on the NFC adapter, which indicates that any Intent should be
     * dispatched to our MainActivity file.
     */
    @Override
    protected void onResume() {
        super.onResume();
        //If the intent doesn’t match our required filters, the foreground dispatch system will fall back to the intent dispatch system.
        enableForegroundDispatch();
    }

    /**
     * When the activity receives an incoming intent, the
     * onNewIntent method gets called, so we need to override it and implement our code.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (!hasNfc()) {
            Toast.makeText(this, "NFC is not available on this device. This application may not work correctly.", Toast.LENGTH_LONG).show();
        }
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onReadTagClicked(View v) {
        isWriteMode = false;
        TextView myText = (TextView) findViewById(R.id.lblStatusMessage);
        myText.setText("read tag was clicked");
    }

    public void onWriteTagClicked(View v) {
        isWriteMode = true;
        TextView myText = (TextView) findViewById(R.id.lblStatusMessage);
        myText.setText("write tag was clicked");
    }
}
