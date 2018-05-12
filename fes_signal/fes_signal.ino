#include <Adafruit_Sensor.h>
#include <Adafruit_LSM9DS0.h>

#define BLUETOOTH_RECEIVE_BUTTON 3
#define BLUETOOTH_RECEIVE_LED 2
#define SIGNAL_OUT_PIN DAC0

#define ONE_VOLT 1861
#define DTV(x) (uint16_t)(x * ONE_VOLT)

#define NUM_OF_SAMPLES 26

// Acceptable ranges
#define PULSE_FREQUENCY_MIN 1 // HZ
#define PULSE_FREQUENCY_MAX 100  // Hz
#define RISE_TIME_MIN 1  // us
#define RISE_TIME_MAX 500  // us
#define MAX_CURRENT_MIN 1  // mA
#define MAX_CURRENT_MAX 100  // mA
#define DECAY_COEFFICIENT_MIN 1
#define DECAY_COEFFICIENT_MAX 1000

int debounce(int pin);
void getBluetoothData();
void calculateWaveform();
void generateWaveform();
int readGyroSensor();
double averageDerivative();
// The four variables that are set by the Bluetooth app.
// Rise time should be a multiple of 4.
int pulse_frequency = 40;
int rise_time = 20; // In microseconds.
int max_current = 100; // Actually max voltage
int decay_coeff = 100;

// Holds the waveform samples
int should_output_waveform = 0;
uint16_t waveform[NUM_OF_SAMPLES];

// Variables to help read in the info
int should_toggle_bluetooth = 1;
int should_read_bluetooth = 0;
int temp[4];  // Temporary values for data validation

/* Assign a unique base ID for this sensor */   
Adafruit_LSM9DS0 lsm = Adafruit_LSM9DS0(1000);  // Use I2C, ID #1000

/**************************************************************************/
/*
    Configures the gain and integration time for the TSL2561
*/
/**************************************************************************/
void configureSensor(void)
{
  // 1.) Set the accelerometer range
  lsm.setupAccel(lsm.LSM9DS0_ACCELRANGE_2G);
  
  // 2.) Set the magnetometer sensitivity
  lsm.setupMag(lsm.LSM9DS0_MAGGAIN_2GAUSS);

  // 3.) Setup the gyroscope
  lsm.setupGyro(lsm.LSM9DS0_GYROSCALE_245DPS);
}

void setup() {
#ifndef ESP8266
  while (!Serial);     // will pause Zero, Leonardo, etc until serial console opens
#endif

  pinMode(13, OUTPUT); // Used to show that the values sent were not accepted. LED on Arduino.
  pinMode(BLUETOOTH_RECEIVE_BUTTON, INPUT);
  pinMode(BLUETOOTH_RECEIVE_LED, OUTPUT);

  analogWriteResolution(12);
  
  Serial.begin(9600);

  /* Initialise the sensor */
  if(!lsm.begin())
  {
    Serial.println("oh no");
    while(1);
  }
  
  /* Setup the sensor gain and integration time */
  configureSensor();

  calculateWaveform();
}

void loop() {

  if(should_toggle_bluetooth) {
    if(debounce(BLUETOOTH_RECEIVE_BUTTON)) {
      should_read_bluetooth = !should_read_bluetooth;
      should_toggle_bluetooth = 0;
    }
  }
  else {
    should_toggle_bluetooth = 1;
  }

  if(should_read_bluetooth) {
    digitalWrite(BLUETOOTH_RECEIVE_LED, HIGH);
    getBluetoothData();
    calculateWaveform();
    should_read_bluetooth = 0;
  }
  // Generate waveform
  else {
    digitalWrite(BLUETOOTH_RECEIVE_LED, LOW);

    should_output_waveform = readGyroSensor();
    //should_output_waveform = 1;
    if(should_output_waveform) {
      generateWaveform();
    }
    else {
      analogWrite(SIGNAL_OUT_PIN, 0);
    }
  }
}

double gyro_data = 0;
double old_sample = 0;
#define FIND_SWING_START_STATE 0
#define FIND_SWING_PEEK_STATE 1
#define FIND_SWING_DONE_STATE 2
int sample_count = 0;
int readGyroSensor() {

  static int state = FIND_SWING_START_STATE;
  int result = 0;

  /* Get a new sensor event */
  sensors_event_t accel, mag, gyro, temp;
  lsm.getEvent(&accel, &mag, &gyro, &temp);

  gyro_data = gyro.gyro.z; // y and z
  double deriv = -1 * averageDerivative();

  Serial.print(gyro_data); Serial.print(" ");
  Serial.print(deriv); Serial.print(" ");
  Serial.print(state * 100); Serial.print(" ");

  if(sample_count > 150) {
    sample_count = 0;
    result = 0;
    state = FIND_SWING_START_STATE;
  }
  else {
    switch(state) {
      case FIND_SWING_START_STATE:
        if(gyro_data < -160) {
          if(deriv > 0) {
            result = 1;
            state = FIND_SWING_PEEK_STATE;
          }
          else {
            result = 0;
          }
        }
        else {
          result = 0;
        }
        break;
      case FIND_SWING_PEEK_STATE:
        if(deriv < 0 && gyro_data > 150) {
          state = FIND_SWING_DONE_STATE;
        }
        result = 1;
        break;
      case FIND_SWING_DONE_STATE:
        if(deriv > 0 && gyro_data < 0) {
          result = 0;
          state = FIND_SWING_START_STATE;
        }
        else {
          result = 1;
        }
        break;
    }
  }

  old_sample = gyro_data;
  if(state != FIND_SWING_START_STATE) ++sample_count;
  Serial.print(result * 200); Serial.println(" ");

  return result;
}

double averageDerivative() {
  return gyro_data - old_sample;
}

void calculateWaveform() {
  int ramp_samples = (rise_time / 4) + 1;

  double height = max_current / 100.0;
  double e_coef_2 = decay_coeff / 1000.0 * 2.0;
  double e_coef_1 = (2.0 / 3) * height;

  uint16_t value = 0;
  for(int i = 0; i < NUM_OF_SAMPLES; ++i) {
    if(i < ramp_samples) {
      value = DTV(i * (1.0 / (ramp_samples - 1)));
    }
    else {
      value = 0;
      value = DTV(e_coef_1 * exp(-(i-ramp_samples) * e_coef_2)) + DTV(height / 3);
    }

    waveform[i] = (uint16_t)((max_current / 100.0) * 0.5 * value);
  }
}

void generateWaveform() {
  for(int i = 0; i < NUM_OF_SAMPLES; ++i) {
    analogWrite(SIGNAL_OUT_PIN, waveform[i]);
  }
  analogWrite(SIGNAL_OUT_PIN, 0);

  delayMicroseconds((int)(1000000 / pulse_frequency));
}

int debounce(int pin) {
  if(digitalRead(pin) == HIGH) {
    delay(50);
    if(digitalRead(pin) == HIGH) {
      return 1;
    }
    else {
      return 0;
    }
  }
  else {
    return 0;
  }
}

void getBluetoothData() {

  pulse_frequency = rise_time = max_current = decay_coeff = -1;

  int info;  // Info read from the HC-06
  int val[8];  // Bytes received from phone
  
  // Get the bytes
  int i = 0;
  while (i < 8) {
    info = Serial.read();
    if (info != -1) {
      val[i] = info - 48;
      i++;
    }
  }

  // Get the values from the bytes
  temp[0] = val[0] * 78 + val[1];
  temp[1] = val[2] * 78 + val[3];
  temp[2] = val[4] * 78 + val[5];
  temp[3] = val[6] * 78 + val[7];

  // Data validation
  if (temp[0] < PULSE_FREQUENCY_MIN ||
      temp[0] > PULSE_FREQUENCY_MAX ||
      temp[1] < RISE_TIME_MIN ||
      temp[1] > RISE_TIME_MAX ||
      temp[2] < MAX_CURRENT_MIN ||
      temp[2] > MAX_CURRENT_MAX ||
      temp[3] < DECAY_COEFFICIENT_MIN ||
      temp[3] > DECAY_COEFFICIENT_MAX ||
      temp[1] % 4 != 0) {
    digitalWrite(13, HIGH);
  } else {
    digitalWrite(13, LOW);
    pulse_frequency = temp[0];
    rise_time = temp[1];
    max_current = temp[2];
    decay_coeff = temp[3];
    Serial.print(temp[0]); Serial.print(", ");
    Serial.print(temp[1]); Serial.print(", ");
    Serial.print(temp[2]); Serial.print(", ");
    Serial.print(temp[3]); Serial.print(", ");
  }
}

