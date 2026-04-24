package com.example.smartsight;

import android.content.Context;

import android.content.Intent;

import android.os.Bundle;

import android.speech.RecognitionListener;

import android.speech.RecognizerIntent;

import android.speech.SpeechRecognizer;

import android.speech.tts.TextToSpeech;

import android.speech.tts.UtteranceProgressListener;

import android.util.Log;

import java.util.ArrayList;

import java.util.Calendar;

/**

 * Guides the user through a voice conversation to define a reminder.

 * Steps: frequency → hour → minute → AM/PM → confirm.

 *

 * Usage:

 *   ReminderVoiceFlow flow = new ReminderVoiceFlow(context, tts, new Callbacks() {

 *       @Override public void onReminderDefined(String repeatType, long reminderTimeMs) { ... }

 *       @Override public void onCancelled() { ... }

 *   });

 *   flow.start();

 *   ...

 *   flow.shutdown();  // when done, in onDestroy

 */

public class ReminderVoiceFlow {

    private static final String TAG = "ReminderVoiceFlow";

    public interface Callbacks {

        /** Called on the UI thread when the user confirms a reminder. */

        void onReminderDefined(String repeatType, long reminderTimeMs);

        /** Called on the UI thread if the user cancels at any point. */

        void onCancelled();

    }

    private enum Step { FREQUENCY, HOUR, MINUTE, AMPM, CONFIRM }

    private final Context context;

    private final TextToSpeech tts;

    private final Callbacks callbacks;

    private final SpeechRecognizer recognizer;

    private final Intent recognizerIntent;

    private Step step = Step.FREQUENCY;

    // Collected answers

    private String repeatType;   // "ONCE" | "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY"

    private int hour12;          // 1..12

    private int minute;          // 0..59

    private boolean isPm;

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

                ArrayList<String> matches =

                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {

                    handleSpeech(matches.get(0));

                } else {

                    repromptCurrentStep(context.getString(R.string.didnt_catch));

                }

            }

            @Override

            public void onError(int error) {

                repromptCurrentStep(context.getString(R.string.didnt_catch));

            }

        });

    }

    /** Kick off the conversation from step 1. */

    public void start() {

        step = Step.FREQUENCY;

        speakAndListen(context.getString(R.string.reminder_ask_frequency));

    }

    /** Release STT resources. Call from host activity's onDestroy. */

    public void shutdown() {

        try {

            recognizer.stopListening();

            recognizer.destroy();

        } catch (Exception ignored) {}

    }

    // ─────────────────────────────────────────────────────────────────────

    // Speech handling per step

    // ─────────────────────────────────────────────────────────────────────

    private void handleSpeech(String raw) {

        String spoken = raw.toLowerCase().trim();

        Log.d(TAG, "step=" + step + " heard=" + spoken);

        // Global cancel keywords

        if (containsAny(spoken, "cancel", "stop", "annuler", "arrêter", "arreter")) {

            speak(context.getString(R.string.vem_cancelled));

            if (callbacks != null) callbacks.onCancelled();

            return;

        }

        switch (step) {

            case FREQUENCY: handleFrequency(spoken); break;

            case HOUR: handleHour(spoken); break;

            case MINUTE: handleMinute(spoken); break;

            case AMPM: handleAmPm(spoken); break;

            case CONFIRM: handleConfirm(spoken); break;

        }

    }

    private void handleFrequency(String s) {

        if (containsAny(s, "once", "one time", "single", "just once",

                "une fois", "seulement une fois")) {

            repeatType = "ONCE";

        } else if (containsAny(s, "daily", "every day", "each day",

                "tous les jours", "chaque jour", "quotidien")) {

            repeatType = "DAILY";

        } else if (containsAny(s, "weekly", "every week", "each week",

                "chaque semaine", "hebdomadaire", "toutes les semaines")) {

            repeatType = "WEEKLY";

        } else if (containsAny(s, "monthly", "every month", "each month",

                "chaque mois", "mensuel", "tous les mois")) {

            repeatType = "MONTHLY";

        } else if (containsAny(s, "yearly", "annually", "every year", "each year",

                "chaque année", "chaque annee", "annuel", "tous les ans")) {

            repeatType = "YEARLY";

        } else {

            repromptCurrentStep(context.getString(R.string.reminder_please_say_frequency));

            return;

        }

        step = Step.HOUR;

        speakAndListen(context.getString(R.string.reminder_ask_hour));

    }

    private void handleHour(String s) {

        Integer n = parseNumber(s);

        if (n == null || n < 1 || n > 12) {

            repromptCurrentStep(context.getString(R.string.reminder_please_say_hour));

            return;

        }

        hour12 = n;

        step = Step.MINUTE;

        speakAndListen(context.getString(R.string.reminder_ask_minute));

    }

    private void handleMinute(String s) {

        if (containsAny(s, "o'clock", "oclock", "sharp", "pile")) {

            minute = 0;

        } else {

            Integer n = parseNumber(s);

            if (n == null || n < 0 || n > 59) {

                repromptCurrentStep(context.getString(R.string.reminder_please_say_minute));

                return;

            }

            minute = n;

        }

        step = Step.AMPM;

        speakAndListen(context.getString(R.string.reminder_ask_ampm));

    }

    private void handleAmPm(String s) {

        if (containsAny(s, "am", "a.m", "morning", "matin", "du matin")) {

            isPm = false;

        } else if (containsAny(s, "pm", "p.m", "evening", "afternoon", "night",

                "soir", "après-midi", "apres-midi", "du soir", "nuit")) {

            isPm = true;

        } else {

            repromptCurrentStep(context.getString(R.string.reminder_please_say_ampm));

            return;

        }

        step = Step.CONFIRM;

        String summary = buildSummary();

        speakAndListen(context.getString(R.string.reminder_confirm, summary));

    }

    private void handleConfirm(String s) {

        if (isYes(s)) {

            long fireAt = computeFireTime();

            if (callbacks != null) callbacks.onReminderDefined(repeatType, fireAt);

        } else if (isNo(s)) {

            speakAndThen(context.getString(R.string.reminder_restart), this::start);

        } else {

            repromptCurrentStep(context.getString(R.string.please_say_yes_no));

        }

    }

    // ─────────────────────────────────────────────────────────────────────

    // Helpers

    // ─────────────────────────────────────────────────────────────────────

    /**

     * Compute the next absolute time (ms since epoch) when the reminder should fire,

     * based on hour12, minute, and isPm. If the target time has already passed today,

     * push it to the next valid occurrence (next day for DAILY/ONCE; still the same day

     * if >5 minutes in the future).

     */

    private long computeFireTime() {

        int hour24 = hour12 % 12;           // 12 AM → 0, 1-11 AM → 1-11

        if (isPm) hour24 += 12;             // 12 PM → 12, 1-11 PM → 13-23

        Calendar c = Calendar.getInstance();

        c.set(Calendar.HOUR_OF_DAY, hour24);

        c.set(Calendar.MINUTE, minute);

        c.set(Calendar.SECOND, 0);

        c.set(Calendar.MILLISECOND, 0);

        // If the computed time is in the past (or within 30 seconds), bump to next day.

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

    /**

     * Parse a spoken number from "5", "twelve", "trente", etc. Returns null if unparseable.

     */

    private Integer parseNumber(String s) {

        s = s.trim();

        // Try plain integer parse (handles "5", "30", "12")

        try {

            return Integer.parseInt(s.replaceAll("[^0-9]", ""));

        } catch (NumberFormatException ignored) {}

        // Fallback: word mapping for common spoken numbers (en + fr)

        switch (s) {

            case "zero": case "o'clock": case "oclock": case "zéro": case "zero.":

                return 0;

            case "one": case "un": case "une": return 1;

            case "two": case "deux":           return 2;

            case "three": case "trois":        return 3;

            case "four": case "quatre":        return 4;

            case "five": case "cinq":          return 5;

            case "six":                        return 6;

            case "seven": case "sept":         return 7;

            case "eight": case "huit":         return 8;

            case "nine": case "neuf":          return 9;

            case "ten": case "dix":            return 10;

            case "eleven": case "onze":        return 11;

            case "twelve": case "douze":       return 12;

            case "thirteen": case "treize":    return 13;

            case "fourteen": case "quatorze":  return 14;

            case "fifteen": case "quinze":     return 15;

            case "twenty": case "vingt":       return 20;

            case "thirty": case "trente":      return 30;

            case "forty-five": case "fortyfive": case "quarante-cinq": return 45;

        }

        return null;

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

    // ─────────────────────────────────────────────────────────────────────

    // Speak + listen plumbing (no overlap: STT only starts after TTS done)

    // ─────────────────────────────────────────────────────────────────────

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

                if (utterId.equals(id)) {

                    android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());

                    h.post(action);

                }

            }

            @Override public void onError(String id) {}

        });

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, utterId);

    }

    private void speakAndListen(String msg) {

        speakAndThen(msg, this::startListening);

    }

    private void startListening() {

        try {

            recognizer.startListening(recognizerIntent);

        } catch (Exception e) {

            Log.e(TAG, "startListening failed: " + e.getMessage());

        }

    }

    private void repromptCurrentStep(String preamble) {

        String msg;

        switch (step) {

            case FREQUENCY: msg = context.getString(R.string.reminder_ask_frequency); break;

            case HOUR:      msg = context.getString(R.string.reminder_ask_hour); break;

            case MINUTE:    msg = context.getString(R.string.reminder_ask_minute); break;

            case AMPM:      msg = context.getString(R.string.reminder_ask_ampm); break;

            case CONFIRM:   msg = context.getString(R.string.reminder_confirm, buildSummary()); break;

            default:        msg = context.getString(R.string.didnt_catch);

        }

        speakAndListen(preamble + " " + msg);

    }

}
