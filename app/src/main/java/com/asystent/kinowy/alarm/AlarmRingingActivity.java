package com.asystent.kinowy.alarm;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.asystent.kinowy.R;

/**
 * Full-Screen Alarm Ringing Activity.
 * <p>
 * Wyświetlana jako Full-Screen Intent, gdy alarm budzika się włącza.
 * Budzi ekran z uśpienia, gra głośny dźwięk alarmu w pętli i wibruje,
 * dopóki użytkownik nie naciśnie „WSTAŁEM" lub „DRZEMKA 10 MIN".
 * <p>
 * Kluczowe mechanizmy:
 * <ul>
 *   <li>{@code setShowWhenLocked(true)} / {@code setTurnScreenOn(true)} — omijanie lock-screen</li>
 *   <li>{@code AudioAttributes.USAGE_ALARM} — dźwięk gra nawet na wyciszonym telefonie</li>
 *   <li>Agresywny wzorzec wibracji (500ms on / 500ms off) w pętli</li>
 * </ul>
 *
 * @see ShiftAlarmReceiver
 * @see AlarmScheduler
 */
public class AlarmRingingActivity extends AppCompatActivity {

    private static final String TAG = "AlarmRingingActivity";

    /** Extras przekazywane przez ShiftAlarmReceiver */
    public static final String EXTRA_SHIFT_START = "extra_alarm_shift_start";
    public static final String EXTRA_SHIFT_DATE = "extra_alarm_shift_date";
    public static final String EXTRA_SHIFT_CATEGORY = "extra_alarm_shift_category";
    public static final String EXTRA_SHIFT_ID = "extra_alarm_shift_id";

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ─── Wybudzanie ekranu ──────────────────────────────────────────
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        super.onCreate(savedInstanceState);

        // Utrzymuj ekran włączony
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_alarm_ringing);

        // ─── Odczyt danych zmiany ───────────────────────────────────────
        String shiftStart = getIntent().getStringExtra(EXTRA_SHIFT_START);
        String shiftDate = getIntent().getStringExtra(EXTRA_SHIFT_DATE);
        String shiftCategory = getIntent().getStringExtra(EXTRA_SHIFT_CATEGORY);

        // ─── Ustawianie UI ──────────────────────────────────────────────
        TextView tvShiftInfo = findViewById(R.id.tv_alarm_shift_info);
        TextView tvShiftDate = findViewById(R.id.tv_alarm_shift_date);

        if (shiftStart != null) {
            tvShiftInfo.setText(getString(R.string.alarm_shift_info, shiftStart));
        }
        if (shiftDate != null) {
            String dateLabel = shiftDate;
            if (shiftCategory != null && !shiftCategory.equals("UNKNOWN")) {
                dateLabel += "  •  " + shiftCategory;
            }
            tvShiftDate.setText(dateLabel);
        }

        // ─── Animacja pulsowania ikony alarmu ────────────────────────────
        ImageView alarmIcon = findViewById(R.id.alarm_icon);
        startPulseAnimation(alarmIcon);

        // Animacja pulsowania separatora
        View pulseDivider = findViewById(R.id.pulse_divider);
        startPulseAnimation(pulseDivider);

        // ─── Silnik Audio ───────────────────────────────────────────────
        startAlarmSound();

        // ─── Silnik Wibracji ────────────────────────────────────────────
        startVibration();

        // ─── Akcje Przycisków ───────────────────────────────────────────
        findViewById(R.id.btn_stop_alarm).setOnClickListener(v -> {
            Log.i(TAG, "WSTAŁEM — zatrzymuję alarm.");
            stopAlarmAndFinish();
        });

        findViewById(R.id.btn_snooze_alarm).setOnClickListener(v -> {
            Log.i(TAG, "DRZEMKA — ustawiam alarm za 10 minut.");
            snoozeAlarm();
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Audio
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Inicjalizuje i uruchamia dźwięk alarmu systemowego w pętli.
     * Używa {@code USAGE_ALARM}, dzięki czemu gra na wyciszonym telefonie.
     */
    private void startAlarmSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                // Fallback: domyślna dzwonek powiadomienia
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);

            // KRYTYCZNE: USAGE_ALARM gra głośno nawet na trybie cichym
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            Log.i(TAG, "🔊 Alarm audio uruchomiony.");
        } catch (Exception e) {
            Log.e(TAG, "Błąd inicjalizacji MediaPlayer: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Wibracje
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Uruchamia agresywny wzorzec wibracji (500ms on, 500ms off) w pętli.
     */
    private void startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager =
                        (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                }
            } else {
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                // Wzorzec: 0ms opóźnienia, 500ms wibracji, 500ms przerwy
                long[] pattern = {0, 500, 500};

                VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0); // 0 = powtarzaj od indeksu 0
                vibrator.vibrate(effect);

                Log.i(TAG, "📳 Wibracje uruchomione.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Błąd inicjalizacji wibracji: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Animacje UI
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Uruchamia animację pulsowania (scale) na widoku.
     */
    private void startPulseAnimation(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.15f);
        scaleX.setDuration(600);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.15f);
        scaleY.setDuration(600);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        scaleX.start();
        scaleY.start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Akcje przycisków
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Zatrzymuje dźwięk i wibracje, zamyka aktywność.
     */
    private void stopAlarmAndFinish() {
        stopMediaAndVibration();

        // Anuluj powiadomienie (ten sam ID co w ShiftAlarmReceiver)
        int shiftId = getIntent().getIntExtra(EXTRA_SHIFT_ID, -1);
        if (shiftId != -1) {
            android.app.NotificationManager nm = (android.app.NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(shiftId);
                Log.i(TAG, "🗑️ Powiadomienie #" + shiftId + " anulowane.");
            }
        }

        finish();
    }

    /**
     * Zatrzymuje alarm, ustawia nowy za 10 minut (snooze), zamyka aktywność.
     */
    private void snoozeAlarm() {
        stopMediaAndVibration();

        AlarmScheduler.scheduleSnoozeAlarm(
                this,
                getIntent().getStringExtra(EXTRA_SHIFT_START),
                getIntent().getStringExtra(EXTRA_SHIFT_DATE),
                getIntent().getStringExtra(EXTRA_SHIFT_CATEGORY)
        );

        finish();
    }

    /**
     * Zwalnia MediaPlayer i anuluje wibracje.
     */
    private void stopMediaAndVibration() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                Log.i(TAG, "🔇 Alarm audio zatrzymany.");
            } catch (Exception e) {
                Log.e(TAG, "Błąd zatrzymywania MediaPlayer: " + e.getMessage(), e);
            }
        }

        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
            Log.i(TAG, "📴 Wibracje zatrzymane.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    protected void onDestroy() {
        stopMediaAndVibration();
        super.onDestroy();
    }
}
