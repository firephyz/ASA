#define SIGNAL_OUT_PIN DAC0

#define ONE_VOLT 1861
#define DTV(x) (uint16_t)(x * ONE_VOLT)

#define NUM_OF_SAMPLES 26
#define RAMP_SAMPLES 6


uint16_t waveform[NUM_OF_SAMPLES];

void setup() {
  Serial.begin(9600);
  analogWriteResolution(12);

  double e_coef_1 = pow(1 / 0.333, (double)RAMP_SAMPLES / (NUM_OF_SAMPLES - RAMP_SAMPLES));
  double e_coef_2 = log(1 / 0.333) / (NUM_OF_SAMPLES - RAMP_SAMPLES);

  uint16_t value = 0;
  for(int i = 0; i < NUM_OF_SAMPLES; ++i) {
    if(i < RAMP_SAMPLES) {
      value = DTV(i * (1.0 / (RAMP_SAMPLES - 1)));
    }
    else {
      value = DTV(e_coef_1 * exp(-i * e_coef_2));
    }

    waveform[i] = value;
  }
}

void loop() {
  for(int i = 0; i < NUM_OF_SAMPLES; ++i) {
    analogWrite(SIGNAL_OUT_PIN, waveform[i]);
  }
  analogWrite(SIGNAL_OUT_PIN, 0);

  delayMicroseconds(1000000 / 40);
}

