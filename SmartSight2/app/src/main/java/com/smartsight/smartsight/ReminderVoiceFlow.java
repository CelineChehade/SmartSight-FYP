package com.example.smartsight;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ReminderVoiceFlow {

    private static final String TAG = "ReminderVoiceFlow";

    public interface Callbacks {

        void onReminderDefined(String repeatType, long reminderTimeMs);

        void onCancelled();

    }

    private enum Step { FREQUENCY, HOUR, MINUTE, AMPM, CONFIRM }

    private final Context context;

    private final TextToSpeech tts;

    private final Callbacks callbacks;

    private final SpeechRecognizer recognizer;

    private final Intent recognizerIntent;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Step step = Step.FREQUENCY;

    private String repeatType;

    private int hour12;

    private int minute;

    private boolean isPm;

    private boolean isListening = false;

    private boolean isShutdown = false;

    public ReminderVoiceFlow(Context context, TextToSpeech tts, Callbacks callbacks) {

        this.context = LocaleManager.wrap(context.getApplicationContext());

        this.tts = tts;

        this.callbacks = callbacks;

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,

                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,

                LocaleManager.getSttLanguageTag(context));

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override public void onReadyForSpeech(Bundle params) {}

            @Override public void onBeginningOfSpeech() {}

            @Override public void onBufferReceived(byte[] buffer) {}

            @Override public void onEndOfSpeech() {}

            @Override public void onEvent(int eventType, Bundle params) {}

            @Override public void onPartialResults(Bundle partialResults) {}

            @Override public void onRmsChanged(float rmsdB) {}

            @Override

            public void onResults(Bundle results) {

                isListening = false;

                if (isShutdown) return;

                ArrayList<String> matches =

                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {

                    handleSpeechWithAlternatives(matches);

                } else {

                    repromptCurrentStep();

                }

            }

            @Override

            public void onError(int error) {

                isListening = false;

                if (isShutdown) return;

                Log.w(TAG, "STT error: " + error);

                switch (error) {

                    case SpeechRecognizer.ERROR_NO_MATCH:

                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:

                        repromptCurrentStep();

                        break;

                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:

                    case SpeechRecognizer.ERROR_CLIENT:

                        mainHandler.postDelayed(ReminderVoiceFlow.this::startListening, 600);

                        break;

                    case SpeechRecognizer.ERROR_NETWORK:

                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:

                        mainHandler.postDelayed(ReminderVoiceFlow.this::startListening, 800);

                        break;

                    case SpeechRecognizer.ERROR_AUDIO:

                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:

                        speak(context.getString(R.string.didnt_catch));

                        if (callbacks != null) callbacks.onCancelled();

                        break;

                    default:

                        mainHandler.postDelayed(ReminderVoiceFlow.this::startListening, 600);

                }

            }

        });

    }

    public void start() {

        step = Step.FREQUENCY;

        speakAndListen(context.getString(R.string.reminder_ask_frequency));

    }

    public void shutdown() {

        try {

            isShutdown = true;

            isListening = false;

            mainHandler.removeCallbacksAndMessages(null);

            recognizer.stopListening();

            recognizer.destroy();

        } catch (Exception ignored) {}

    }

    // ─────────────────────────────────────────────────────────────

    // Speech dispatch — tries each alternative the engine returned

    // ─────────────────────────────────────────────────────────────

    private void handleSpeechWithAlternatives(ArrayList<String> alternatives) {

        Log.d(TAG, "step=" + step + " alternatives=" + alternatives);

        for (String alt : alternatives) {

            String s = alt.toLowerCase().trim();

            if (containsAny(s, "cancel", "stop", "annuler", "arrêter", "arreter")) {

                speak(context.getString(R.string.vem_cancelled));

                if (callbacks != null) callbacks.onCancelled();

                return;

            }

        }

        for (String alt : alternatives) {

            if (tryHandle(alt)) return;

        }

        repromptCurrentStep();

    }

    private boolean tryHandle(String raw) {

        String s = raw.toLowerCase().trim();

        switch (step) {

            case FREQUENCY: {

                String freq = matchFrequency(s);

                if (freq != null) {

                    repeatType = freq;

                    step = Step.HOUR;

                    speakAndListen(context.getString(R.string.reminder_ask_hour));

                    return true;

                }

                return false;

            }

            case HOUR: {

                Integer n = parseNumber(s);

                if (n != null && n >= 1 && n <= 12) {

                    hour12 = n;

                    step = Step.MINUTE;

                    speakAndListen(context.getString(R.string.reminder_ask_minute));

                    return true;

                }

                return false;

            }

            case MINUTE: {

                if (containsAny(s, "o'clock", "oclock", "o clock", "sharp", "pile")) {

                    minute = 0;

                    step = Step.AMPM;

                    speakAndListen(context.getString(R.string.reminder_ask_ampm));

                    return true;

                }

                Integer n = parseNumber(s);

                if (n != null && n >= 0 && n <= 59) {

                    minute = n;

                    step = Step.AMPM;

                    speakAndListen(context.getString(R.string.reminder_ask_ampm));

                    return true;

                }

                return false;

            }

            case AMPM: {

                if (containsAny(s, "am", "a.m", "a m", "morning", "matin", "du matin")) {

                    isPm = false;

                    step = Step.CONFIRM;

                    speakAndListen(context.getString(R.string.reminder_confirm, buildSummary()));

                    return true;

                }

                if (containsAny(s, "pm", "p.m", "p m", "evening", "afternoon", "night",

                        "soir", "après-midi", "apres-midi", "du soir", "nuit")) {

                    isPm = true;

                    step = Step.CONFIRM;

                    speakAndListen(context.getString(R.string.reminder_confirm, buildSummary()));

                    return true;

                }

                return false;

            }

            case CONFIRM: {

                if (isYes(s)) {

                    long fireAt = computeFireTime();

                    if (callbacks != null) callbacks.onReminderDefined(repeatType, fireAt);

                    return true;

                }

                if (isNo(s)) {

                    speakAndThen(context.getString(R.string.reminder_restart), this::start);

                    return true;

                }

                return false;

            }

        }

        return false;

    }

    private String matchFrequency(String s) {

        if (containsAny(s, "once", "one time", "single", "just once",

                "une fois", "seulement une fois")) return "ONCE";

        if (containsAny(s, "daily", "every day", "each day",

                "tous les jours", "chaque jour", "quotidien")) return "DAILY";

        if (containsAny(s, "weekly", "every week", "each week",

                "chaque semaine", "hebdomadaire", "toutes les semaines")) return "WEEKLY";

        if (containsAny(s, "monthly", "every month", "each month",

                "chaque mois", "mensuel", "tous les mois")) return "MONTHLY";

        if (containsAny(s, "yearly", "annually", "every year", "each year",

                "chaque année", "chaque annee", "annuel", "tous les ans")) return "YEARLY";

        return null;

    }

    // ─────────────────────────────────────────────────────────────

    // Robust number parsing

    // ─────────────────────────────────────────────────────────────

    private static final Map<String, Integer> WORD_TO_NUM = new HashMap<>();

    static {

        // English

        WORD_TO_NUM.put("zero", 0);     WORD_TO_NUM.put("oh", 0);

        WORD_TO_NUM.put("one", 1);      WORD_TO_NUM.put("won", 1);

        WORD_TO_NUM.put("two", 2);      WORD_TO_NUM.put("to", 2);

        WORD_TO_NUM.put("too", 2);

        WORD_TO_NUM.put("three", 3);    WORD_TO_NUM.put("tree", 3);

        WORD_TO_NUM.put("free", 3);

        WORD_TO_NUM.put("four", 4);     WORD_TO_NUM.put("for", 4);

        WORD_TO_NUM.put("five", 5);

        WORD_TO_NUM.put("six", 6);      WORD_TO_NUM.put("sex", 6);

        WORD_TO_NUM.put("seven", 7);

        WORD_TO_NUM.put("eight", 8);    WORD_TO_NUM.put("ate", 8);

        WORD_TO_NUM.put("nine", 9);

        WORD_TO_NUM.put("ten", 10);

        WORD_TO_NUM.put("eleven", 11);  WORD_TO_NUM.put("twelve", 12);

        WORD_TO_NUM.put("thirteen", 13); WORD_TO_NUM.put("fourteen", 14);

        WORD_TO_NUM.put("fifteen", 15); WORD_TO_NUM.put("sixteen", 16);

        WORD_TO_NUM.put("seventeen", 17); WORD_TO_NUM.put("eighteen", 18);

        WORD_TO_NUM.put("nineteen", 19);

        WORD_TO_NUM.put("twenty", 20);  WORD_TO_NUM.put("thirty", 30);

        WORD_TO_NUM.put("forty", 40);   WORD_TO_NUM.put("fourty", 40);

        WORD_TO_NUM.put("fifty", 50);

        // French

        WORD_TO_NUM.put("zéro", 0);

        WORD_TO_NUM.put("un", 1);       WORD_TO_NUM.put("une", 1);

        WORD_TO_NUM.put("deux", 2);     WORD_TO_NUM.put("trois", 3);

        WORD_TO_NUM.put("quatre", 4);   WORD_TO_NUM.put("cinq", 5);

        WORD_TO_NUM.put("sept", 7);     WORD_TO_NUM.put("huit", 8);

        WORD_TO_NUM.put("neuf", 9);     WORD_TO_NUM.put("dix", 10);

        WORD_TO_NUM.put("onze", 11);    WORD_TO_NUM.put("douze", 12);

        WORD_TO_NUM.put("treize", 13);  WORD_TO_NUM.put("quatorze", 14);

        WORD_TO_NUM.put("quinze", 15);  WORD_TO_NUM.put("seize", 16);

        WORD_TO_NUM.put("vingt", 20);   WORD_TO_NUM.put("trente", 30);

        WORD_TO_NUM.put("quarante", 40); WORD_TO_NUM.put("cinquante", 50);

    }

    private Integer parseNumber(String raw) {

        if (raw == null) return null;

        String s = raw.toLowerCase().trim()

                .replaceAll("[.,!?]", "")

                .replaceAll("-", " ")

                .replaceAll("\\s+", " ");

        // 1) Look for any digit run

        java.util.regex.Matcher digitMatch = java.util.regex.Pattern

                .compile("\\d+").matcher(s);

        if (digitMatch.find()) {

            try {

                return Integer.parseInt(digitMatch.group());

            } catch (NumberFormatException ignored) {}

        }

        // 2) Single-word lookup

        Integer direct = WORD_TO_NUM.get(s);

        if (direct != null) return direct;

        // 3) Compound words ("thirty five" → 35)

        String[] tokens = s.split("\\s+");

        int total = 0;

        boolean anyFound = false;

        for (String token : tokens) {

            Integer v = WORD_TO_NUM.get(token);

            if (v != null) {

                total += v;

                anyFound = true;

            }

        }

        if (anyFound) return total;

        return null;

    }

    // ─────────────────────────────────────────────────────────────

    private long computeFireTime() {

        int hour24 = hour12 % 12;

        if (isPm) hour24 += 12;

        Calendar c = Calendar.getInstance();

        c.set(Calendar.HOUR_OF_DAY, hour24);

        c.set(Calendar.MINUTE, minute);

        c.set(Calendar.SECOND, 0);

        c.set(Calendar.MILLISECOND, 0);

        long now = System.currentTimeMillis();

        if (c.getTimeInMillis() <= now + 30_000) {

            c.add(Calendar.DAY_OF_MONTH, 1);

        }

        return c.getTimeInMillis();

    }

    private String buildSummary() {

        String freqLabel;

        switch (repeatType) {

            case "ONCE":    freqLabel = context.getString(R.string.freq_once); break;

            case "DAILY":   freqLabel = context.getString(R.string.freq_daily); break;

            case "WEEKLY":  freqLabel = context.getString(R.string.freq_weekly); break;

            case "MONTHLY": freqLabel = context.getString(R.string.freq_monthly); break;

            case "YEARLY":  freqLabel = context.getString(R.string.freq_yearly); break;

            default:        freqLabel = repeatType;

        }

        String ampmLabel = isPm

                ? context.getString(R.string.ampm_pm)

                : context.getString(R.string.ampm_am);

        String minuteTwo = String.format(java.util.Locale.US, "%02d", minute);

        return context.getString(R.string.reminder_summary_format,

                freqLabel, hour12, minuteTwo, ampmLabel);

    }

    private boolean containsAny(String s, String... needles) {

        for (String n : needles) if (s.contains(n)) return true;

        return false;

    }

    private boolean isYes(String s) {

        return s.matches(".*\\b(yes|yeah|yup|yep|sure|okay|ok|correct|right|confirm|oui|ouais|d'accord)\\b.*");

    }

    private boolean isNo(String s) {

        return s.matches(".*\\b(no|nope|nah|cancel|wrong|incorrect|non|annuler)\\b.*");

    }

    // ─────────────────────────────────────────────────────────────

    // TTS / STT plumbing

    // ─────────────────────────────────────────────────────────────

    private void speak(String msg) {

        if (tts == null) return;

        tts.stop();

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "rvf_speak");

    }

    private void speakAndThen(String msg, Runnable action) {

        if (tts == null) return;

        tts.stop();

        String utterId = "rvf_then_" + System.currentTimeMillis();

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override public void onStart(String id) {}

            @Override public void onDone(String id) {

                if (utterId.equals(id) && !isShutdown) mainHandler.post(action);

            }

            @Override public void onError(String id) {}

        });

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, utterId);

    }

    private void speakAndListen(String msg) {

        speakAndThen(msg, () -> mainHandler.postDelayed(this::startListening, 300));

    }

    private void startListening() {

        if (isShutdown || isListening) {

            Log.d(TAG, "startListening skipped: shutdown=" + isShutdown + " listening=" + isListening);

            return;

        }

        try {

            isListening = true;

            recognizer.startListening(recognizerIntent);

        } catch (Exception e) {

            isListening = false;

            Log.e(TAG, "startListening failed: " + e.getMessage());

            mainHandler.postDelayed(this::startListening, 600);

        }

    }

    private void repromptCurrentStep() {

        String msg;

        switch (step) {

            case FREQUENCY: msg = context.getString(R.string.reminder_please_say_frequency); break;

            case HOUR:      msg = context.getString(R.string.reminder_please_say_hour); break;

            case MINUTE:    msg = context.getString(R.string.reminder_please_say_minute); break;

            case AMPM:      msg = context.getString(R.string.reminder_please_say_ampm); break;

            case CONFIRM:   msg = context.getString(R.string.reminder_confirm, buildSummary()); break;

            default:        msg = context.getString(R.string.didnt_catch);

        }

        speakAndListen(msg);

    }

}
