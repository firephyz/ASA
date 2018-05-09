package fes.coryhufford.com.fes;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {


    private static final int PULSE_FREQUENCY_MIN = 1;  // Hz
    private static final int PULSE_FREQUENCY_MAX = 100;  // Hz
    private static final int RISE_TIME_MIN = 1;  // us
    private static final int RISE_TIME_MAX = 500;  // us
    private static final int MAX_CURRENT_MIN = 1;  // mA
    private static final int MAX_CURRENT_MAX = 100;  // mA
    private static final int DECAY_COEFFICIENT_MIN = 1;
    private static final int DECAY_COEFFICIENT_MAX = 1000;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mDevice;
    BluetoothSocket mSocket;
    OutputStream mOutStream;

    EditText pulseFrequencyEditText;
    EditText riseTimeEditText;
    EditText maxCurrentEditText;
    EditText decayCoefficientEditText;
    Button setupBluetoothButton;
    Button sendButton;
    TextView errorTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sharedPreferences = MainActivity.this.getPreferences(Context.MODE_PRIVATE);

        pulseFrequencyEditText = findViewById(R.id.pulseFrequencyEditText);
        pulseFrequencyEditText.setText(String.format(Locale.US, "%d", sharedPreferences.getInt("pulse_frequency", -1)));
        pulseFrequencyEditText.setEnabled(false);

        riseTimeEditText = findViewById(R.id.riseTimeEditText);
        riseTimeEditText.setText(String.format(Locale.US, "%d", sharedPreferences.getInt("rise_time", -1)));
        riseTimeEditText.setEnabled(false);

        maxCurrentEditText = findViewById(R.id.maxCurrentEditText);
        maxCurrentEditText.setText(String.format(Locale.US, "%d", sharedPreferences.getInt("max_current", -1)));
        maxCurrentEditText.setEnabled(false);

        decayCoefficientEditText = findViewById(R.id.decayCoefficientEditText);
        decayCoefficientEditText.setText(String.format(Locale.US, "%d", sharedPreferences.getInt("decay_coefficient", -1)));
        decayCoefficientEditText.setEnabled(false);

        sendButton = findViewById(R.id.sendButton);
        sendButton.setEnabled(false);
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                clearError();
                int[] values = new int[4];
                try {
                    values[0] = (int) Float.parseFloat(pulseFrequencyEditText.getText().toString());
                    values[1] = (int) Float.parseFloat(riseTimeEditText.getText().toString());
                    values[2] = (int) Float.parseFloat(maxCurrentEditText.getText().toString());
                    values[3] = (int) Float.parseFloat(decayCoefficientEditText.getText().toString());
                } catch (NumberFormatException e) {
                    addErrorMessage("Do not leave any values blank.");
                    return;
                }
                boolean dataIsValid = isDataValid(values);
                if (dataIsValid) {
                    saveValues(values);
                    String message = getMessage(values);
                    sendData(message);
                }
            }
        });

        setupBluetoothButton = findViewById(R.id.setupBluetoothButton);
        setupBluetoothButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setupBluetooth();
                if (mOutStream != null) {
                    pulseFrequencyEditText.setEnabled(true);
                    riseTimeEditText.setEnabled(true);
                    maxCurrentEditText.setEnabled(true);
                    decayCoefficientEditText.setEnabled(true);
                    setupBluetoothButton.setEnabled(false);
                    sendButton.setEnabled(true);
                }
            }
        });

        errorTextView = findViewById(R.id.errorTextView);
    }


    /**
     * Checks if the values the user has entered is valid and displays error messages if it isn't.
     *
     * @param values values to be checked
     * @return true if all values are valid false otherwise
     */
    private boolean isDataValid(int[] values) {
        boolean dataIsValid = true;
        if (values[0] < PULSE_FREQUENCY_MIN || values[0] > PULSE_FREQUENCY_MAX) {
            dataIsValid = false;
            addErrorMessage("Pulse frequency should be in the range " + PULSE_FREQUENCY_MIN + " to " + PULSE_FREQUENCY_MAX + " hertz.");
        }
        if (values[1] < RISE_TIME_MIN || values[1] > RISE_TIME_MAX || values[1] % 4 != 0) {
            dataIsValid = false;
            addErrorMessage("Rise time should be in the range " + RISE_TIME_MIN + " to " + RISE_TIME_MAX + " microseconds and a multiple of 4.");
        }
        if (values[2] < MAX_CURRENT_MIN || values[2] > MAX_CURRENT_MAX) {
            dataIsValid = false;
            addErrorMessage("Max current should be in the range " + MAX_CURRENT_MIN + " to " + MAX_CURRENT_MAX + " milliamps.");
        }
        if (values[3] < DECAY_COEFFICIENT_MIN || values[3] > DECAY_COEFFICIENT_MAX) {
            dataIsValid = false;
            addErrorMessage("Decay coefficient should be in the range " + DECAY_COEFFICIENT_MIN + " to " + DECAY_COEFFICIENT_MAX + ".");
        }
        return dataIsValid;
    }


    /**
     * Changes values into base 78 to send.
     * @param values User-entered values
     * @return message string
     */
    private String getMessage(int[] values) {
        StringBuilder stringBuilder = new StringBuilder();
        for(int value : values) {
            if(value > 78) {
                int firstDigitValue = value / 78;
                int secondDigitValue = value % 78;
                stringBuilder.append((char) (firstDigitValue + 48));
                stringBuilder.append((char) (secondDigitValue + 48));
            } else {
                stringBuilder.append("0");
                stringBuilder.append((char) (value + 48));
            }
        }
        return stringBuilder.toString();
    }


    /**
     * Saves the values to shared preferences.
     *
     * @param values values to be saved
     */
    private void saveValues(int[] values) {
        SharedPreferences sharedPreferences = MainActivity.this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("pulse_frequency", values[0]);
        pulseFrequencyEditText.setText(String.format(Locale.US, "%d", values[0]));
        editor.putInt("rise_time", values[1]);
        riseTimeEditText.setText(String.format(Locale.US, "%d", values[1]));
        editor.putInt("max_current", values[2]);
        maxCurrentEditText.setText(String.format(Locale.US, "%d", values[2]));
        editor.putInt("decay_coefficient", values[3]);
        decayCoefficientEditText.setText(String.format(Locale.US, "%d", values[3]));
        editor.apply();
    }


    /**
     * Sends a message to the HC-06.
     *
     * @param message Message to send
     */
    private void sendData(String message) {
        byte[] messageBuffer = message.getBytes();
        try {
            mOutStream.write(messageBuffer);
            Toast toast = Toast.makeText(MainActivity.this, "Data sent", Toast.LENGTH_SHORT);
            toast.show();
        } catch (IOException messageException) {
            addErrorMessage("Message sending error. Make sure that the FES device is powered and paired to your Android device, then restart the app and FES device.");
        }
    }


    /**
     * Adds an error message to the screen.
     *
     * @param error error message to display
     */
    private void addErrorMessage(String error) {
        errorTextView.append(error + "\n\n");
    }


    /**
     * Clears all of the current error messages.
     */
    private void clearError() {
        errorTextView.setText("");
    }


    /**
     * Disables all the text fields and buttons in the app.
     */
    private void disableAll() {
        pulseFrequencyEditText.setEnabled(false);
        riseTimeEditText.setEnabled(false);
        maxCurrentEditText.setEnabled(false);
        decayCoefficientEditText.setEnabled(false);
        setupBluetoothButton.setEnabled(false);
        sendButton.setEnabled(false);
    }


    /**
     * Sets up the bluetooth capabilities by connecting to the HC-06.
     */
    private void setupBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // Device doesn't support bluetooth or bluetooth isn't on
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage("Please turn on bluetooth and pair with the device.").setTitle("Bluetooth not available");
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            // Finish setting up
            findFesDevice();
            connectToFesDevice();
            setupStreams();
        }
    }


    /**
     * Finds the paired HC-06.
     */
    private void findFesDevice() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        mBluetoothAdapter.cancelDiscovery();
        if (pairedDevices.size() > 0) {
            // Get the name and MAC address of each paired device
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                if (deviceName.contains("HC-06")) {
                    // Grab the first paired HC-06 and save it
                    mDevice = device;
                    return;
                }
            }
        }
        addErrorMessage("FES device not found. Make sure that the FES device is powered and paired to your Android device, then restart the app and FES device.");
    }


    /**
     * Connects to the paired HC-06.
     */
    private void connectToFesDevice() {
        try {
            mSocket = mDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
        } catch (IOException socketException) {
            addErrorMessage("Bluetooth socket initialization error. Restart your device.");
            disableAll();
        }

        try {
            mSocket.connect();
        } catch (IOException connectException) {
            try {
                mSocket.close();
            } catch (IOException closeException) {
                addErrorMessage("Bluetooth socket close error. Restart your device.");
                disableAll();
            }
        }
    }


    /**
     * Sets up the output streams to send data.
     */
    private void setupStreams() {
        try {
            mOutStream = mSocket.getOutputStream();
        } catch (IOException outputStreamException) {
            addErrorMessage("Output stream error. Restart the app or your device.");
        }
    }


}
