package com.example.smartsight;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ReminderVoiceFlow {

    private static final String TAG          = "ReminderVoiceFlow";
    // FIX: increased delay so TTS finishes before STT starts
    private static final long   STT_DELAY_MS = 1200;
    private static final int    MAX_RETRIES  = 3;

    public interface Callbacks {
        void onReminderDefined(String repeatType, long reminderTimeMs);
        void onCancelled();
    }

    // FIX: Step order is now FREQUENCY → YEAR → MONTH → DAY → HOUR → MINUTE → AMPM → CONFIRM
    // for ALL repeat types. Previously ONCE/DAILY/WEEKLY skipped YEAR/MONTH/DAY entirely.
    private enum Step { FREQUENCY, YEAR, MONTH, DAY, HOUR, MINUTE, AMPM, CONFIRM }

    private final Context      context;
    private final TextToSpeech tts;
    private final Callbacks    callbacks;
    private final Handler      mainHandler = new Handler(Looper.getMainLooper());

    private Step    step       = Step.FREQUENCY;
    private String  repeatType;
    private int     year, month, day, hour12, minute;
    private boolean isPm;
    private boolean isShutdown = false;
    private int     retryCount = 0;

    public ReminderVoiceFlow(Context context, TextToSpeech tts, Callbacks callbacks) {
        this.context   = LocaleManager.wrap(context.getApplicationContext());
        this.tts       = tts;
        this.callbacks = callbacks;

        // Pre-fill with today so partial inputs are always valid
        Calendar now = Calendar.getInstance();
        year   = now.get(Calendar.YEAR);
        month  = now.get(Calendar.MONTH) + 1;
        day    = now.get(Calendar.DAY_OF_MONTH);
        hour12 = now.get(Calendar.HOUR);
        if (hour12 == 0) hour12 = 12;
        minute = now.get(Calendar.MINUTE);
        isPm   = now.get(Calendar.AM_PM) == Calendar.PM;
    }

    public void start() {
        if (isShutdown) return;
        step = Step.FREQUENCY;
        speakThenListen(context.getString(R.string.reminder_ask_frequency));
    }

    public void shutdown() {
        isShutdown = true;
        mainHandler.removeCallbacksAndMessages(null);
        SharedSpeechRecognizer.get().cancel();
    }

    // ── STT helpers ───────────────────────────────────────────────────────────

    private void speakThenListen(String msg) {
        if (isShutdown) return;
        retryCount = 0;
        TtsHelper.speakThen(tts, msg,
                () -> mainHandler.postDelayed(this::startListeningOnce, STT_DELAY_MS));
    }

    private void startListeningOnce() {
        if (isShutdown) return;
        SharedSpeechRecognizer.get().listen(context, result -> {
            if (isShutdown) return;
            if (result.success) {
                retryCount = 0;
                handleSpeechWithAlternatives(result.allResults);
            } else {
                handleListenError(result.errorCode);
            }
        });
    }

    private void handleListenError(int errorCode) {
        if (isShutdown) return;
        boolean isSoft = errorCode == SpeechRecognizer.ERROR_NO_MATCH
                || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || errorCode == SpeechRecognizer.ERROR_CLIENT;

        if (isSoft && retryCount < MAX_RETRIES) {
            retryCount++;
            Log.d(TAG, "Soft STT error, retry #" + retryCount);
            // FIX: silent retry — don't speak "didn't catch that" on every retry
            mainHandler.postDelayed(this::startListeningOnce, STT_DELAY_MS);
        } else {
            retryCount = 0;
            repromptCurrentStep();
        }
    }

    // ── Speech handling ───────────────────────────────────────────────────────

    private void handleSpeechWithAlternatives(ArrayList<String> alternatives) {
        // Check for cancel in any alternative
        for (String alt : alternatives) {
            String s = alt.toLowerCase().trim();
            if (containsAny(s, "cancel","stop","annuler","arrêter","arreter")) {
                TtsHelper.speak(tts, context.getString(R.string.vem_cancelled));
                if (callbacks != null) callbacks.onCancelled();
                return;
            }
        }
        // Try each alternative until one succeeds
        for (String alt : alternatives) {
            if (tryHandle(alt)) return;
        }
        repromptCurrentStep();
    }

    private boolean tryHandle(String raw) {
        String s = raw.toLowerCase().trim();

        switch (step) {

            // ── FREQUENCY ────────────────────────────────────────────────────
            // FIX: ALL frequencies now proceed to YEAR first, then MONTH→DAY→HOUR→MINUTE→AMPM
            case FREQUENCY:
                if (containsAny(s, "once","one time","une fois")) {
                    repeatType = "ONCE";
                    step = Step.YEAR;
                    speakThenListen(context.getString(R.string.reminder_ask_year));
                    return true;
                }
                if (containsAny(s, "daily","every day","chaque jour","quotidien")) {
                    repeatType = "DAILY";
                    step = Step.YEAR;
                    speakThenListen(context.getString(R.string.reminder_ask_year));
                    return true;
                }
                if (containsAny(s, "weekly","every week","chaque semaine","hebdomadaire")) {
                    repeatType = "WEEKLY";
                    step = Step.YEAR;
                    speakThenListen(context.getString(R.string.reminder_ask_year));
                    return true;
                }
                if (containsAny(s, "monthly","every month","chaque mois","mensuel")) {
                    repeatType = "MONTHLY";
                    step = Step.YEAR;
                    speakThenListen(context.getString(R.string.reminder_ask_year));
                    return true;
                }
                if (containsAny(s, "yearly","every year","annually",
                        "chaque année","annuel","chaque annee")) {
                    repeatType = "YEARLY";
                    step = Step.YEAR;
                    speakThenListen(context.getString(R.string.reminder_ask_year));
                    return true;
                }
                return false;

            // ── YEAR ─────────────────────────────────────────────────────────
            case YEAR: {
                // Allow "this year" / "cette année"
                if (containsAny(s, "this year","cette année","cette annee","same year")) {
                    // keep default year (already set to current)
                    step = Step.MONTH;
                    speakThenListen(context.getString(R.string.reminder_ask_month));
                    return true;
                }
                // Allow "next year" / "l'année prochaine"
                if (containsAny(s, "next year","l'année prochaine","l'annee prochaine","année prochaine")) {
                    year += 1;
                    step = Step.MONTH;
                    speakThenListen(context.getString(R.string.reminder_ask_month));
                    return true;
                }
                Integer v = parseNumber(raw);
                if (v != null && v >= 2000 && v <= 2100) {
                    year = v;
                    step = Step.MONTH;
                    speakThenListen(context.getString(R.string.reminder_ask_month));
                    return true;
                }
                return false;
            }

            // ── MONTH ────────────────────────────────────────────────────────
            case MONTH: {
                // Allow month names (EN + FR)
                Integer byName = parseMonthName(s);
                if (byName != null) {
                    month = byName;
                    step  = Step.DAY;
                    speakThenListen(context.getString(R.string.reminder_ask_day));
                    return true;
                }
                Integer v = parseNumber(raw);
                if (v != null && v >= 1 && v <= 12) {
                    month = v;
                    step  = Step.DAY;
                    speakThenListen(context.getString(R.string.reminder_ask_day));
                    return true;
                }
                return false;
            }

            // ── DAY ──────────────────────────────────────────────────────────
            case DAY: {
                // Allow "today" / "aujourd'hui"
                if (containsAny(s, "today","aujourd'hui","aujourd hui")) {
                    Calendar now = Calendar.getInstance();
                    day = now.get(Calendar.DAY_OF_MONTH);
                    step = Step.HOUR;
                    speakThenListen(context.getString(R.string.reminder_ask_hour));
                    return true;
                }
                // Allow "tomorrow" / "demain"
                if (containsAny(s, "tomorrow","demain")) {
                    Calendar now = Calendar.getInstance();
                    now.add(Calendar.DAY_OF_MONTH, 1);
                    day = now.get(Calendar.DAY_OF_MONTH);
                    step = Step.HOUR;
                    speakThenListen(context.getString(R.string.reminder_ask_hour));
                    return true;
                }
                Integer v = parseNumber(raw);
                if (v != null && v >= 1 && v <= 31) {
                    day  = v;
                    step = Step.HOUR;
                    speakThenListen(context.getString(R.string.reminder_ask_hour));
                    return true;
                }
                return false;
            }

            // ── HOUR ─────────────────────────────────────────────────────────
            case HOUR: {
                Integer v = parseNumber(raw);
                if (v != null && v >= 1 && v <= 12) {
                    hour12 = v;
                    step   = Step.MINUTE;
                    speakThenListen(context.getString(R.string.reminder_ask_minute));
                    return true;
                }
                return false;
            }

            // ── MINUTE ───────────────────────────────────────────────────────
            case MINUTE: {
                // Allow "o'clock" / "pile" / "zero" to mean :00
                if (containsAny(s, "o'clock","oclock","o clock","pile","zéro","zero","0")) {
                    minute = 0;
                    step   = Step.AMPM;
                    speakThenListen(context.getString(R.string.reminder_ask_ampm));
                    return true;
                }
                Integer v = parseNumber(raw);
                if (v != null && v >= 0 && v <= 59) {
                    minute = v;
                    step   = Step.AMPM;
                    speakThenListen(context.getString(R.string.reminder_ask_ampm));
                    return true;
                }
                return false;
            }

            // ── AMPM ─────────────────────────────────────────────────────────
            case AMPM:
                if (containsAny(s, "am","morning","matin","a.m.")) {
                    isPm = false;
                    step = Step.CONFIRM;
                    speakThenListen(context.getString(
                            R.string.reminder_confirm, buildSummary()));
                    return true;
                }
                if (containsAny(s, "pm","afternoon","evening",
                        "après-midi","apres-midi","soir","p.m.")) {
                    isPm = true;
                    step = Step.CONFIRM;
                    speakThenListen(context.getString(
                            R.string.reminder_confirm, buildSummary()));
                    return true;
                }
                return false;

            // ── CONFIRM ──────────────────────────────────────────────────────
            case CONFIRM:
                if (isYes(s)) {
                    if (callbacks != null)
                        callbacks.onReminderDefined(repeatType, computeFireTime());
                    return true;
                }
                if (isNo(s)) {
                    // Restart the whole flow
                    step = Step.FREQUENCY;
                    speakThenListen(context.getString(R.string.reminder_ask_frequency));
                    return true;
                }
                return false;
        }
        return false;
    }

    private void repromptCurrentStep() {
        if (isShutdown) return;
        String msg;
        switch (step) {
            case FREQUENCY: msg = context.getString(R.string.reminder_please_say_frequency); break;
            case YEAR:      msg = context.getString(R.string.reminder_please_say_year);      break;
            case MONTH:     msg = context.getString(R.string.reminder_please_say_month);     break;
            case DAY:       msg = context.getString(R.string.reminder_please_say_day);       break;
            case HOUR:      msg = context.getString(R.string.reminder_please_say_hour);      break;
            case MINUTE:    msg = context.getString(R.string.reminder_please_say_minute);    break;
            case AMPM:      msg = context.getString(R.string.reminder_please_say_ampm);      break;
            case CONFIRM:   msg = context.getString(R.string.reminder_confirm, buildSummary()); break;
            default:        msg = context.getString(R.string.didnt_catch);
        }
        speakThenListen(msg);
    }

    // ── Number parsing ────────────────────────────────────────────────────────

    private static final Map<String, Integer> WORD_TO_NUM = new HashMap<>();
    static {
        WORD_TO_NUM.put("zero",0);  WORD_TO_NUM.put("oh",0);
        WORD_TO_NUM.put("one",1);   WORD_TO_NUM.put("won",1);
        WORD_TO_NUM.put("two",2);   WORD_TO_NUM.put("to",2);   WORD_TO_NUM.put("too",2);
        WORD_TO_NUM.put("three",3); WORD_TO_NUM.put("four",4); WORD_TO_NUM.put("for",4);
        WORD_TO_NUM.put("five",5);  WORD_TO_NUM.put("six",6);  WORD_TO_NUM.put("seven",7);
        WORD_TO_NUM.put("eight",8); WORD_TO_NUM.put("nine",9); WORD_TO_NUM.put("ten",10);
        WORD_TO_NUM.put("eleven",11); WORD_TO_NUM.put("twelve",12); WORD_TO_NUM.put("thirteen",13);
        WORD_TO_NUM.put("fourteen",14); WORD_TO_NUM.put("fifteen",15); WORD_TO_NUM.put("sixteen",16);
        WORD_TO_NUM.put("seventeen",17); WORD_TO_NUM.put("eighteen",18); WORD_TO_NUM.put("nineteen",19);
        WORD_TO_NUM.put("twenty",20); WORD_TO_NUM.put("thirty",30);
        WORD_TO_NUM.put("forty",40); WORD_TO_NUM.put("fifty",50);
        // French
        WORD_TO_NUM.put("zéro",0);  WORD_TO_NUM.put("un",1);   WORD_TO_NUM.put("une",1);
        WORD_TO_NUM.put("deux",2);  WORD_TO_NUM.put("trois",3); WORD_TO_NUM.put("quatre",4);
        WORD_TO_NUM.put("cinq",5);  WORD_TO_NUM.put("six",6);   WORD_TO_NUM.put("sept",7);
        WORD_TO_NUM.put("huit",8);  WORD_TO_NUM.put("neuf",9);  WORD_TO_NUM.put("dix",10);
        WORD_TO_NUM.put("onze",11); WORD_TO_NUM.put("douze",12); WORD_TO_NUM.put("treize",13);
        WORD_TO_NUM.put("quatorze",14); WORD_TO_NUM.put("quinze",15); WORD_TO_NUM.put("seize",16);
        WORD_TO_NUM.put("vingt",20); WORD_TO_NUM.put("trente",30);
        WORD_TO_NUM.put("quarante",40); WORD_TO_NUM.put("cinquante",50);
    }

    private Integer parseNumber(String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase().trim()
                .replaceAll("[.,!?]", "")
                .replaceAll("-", " ")
                .replaceAll("\\s+", " ");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group()); } catch (NumberFormatException ignored) {}
        }
        Integer d = WORD_TO_NUM.get(s);
        if (d != null) return d;
        String[] tokens = s.split("\\s+");
        int total = 0; boolean found = false;
        for (String t : tokens) {
            Integer v = WORD_TO_NUM.get(t);
            if (v != null) { total += v; found = true; }
        }
        return found ? total : null;
    }

    /** Parse month names in English and French → 1–12, or null. */
    private Integer parseMonthName(String s) {
        if (containsAny(s, "january","jan","janvier"))   return 1;
        if (containsAny(s, "february","feb","février","fevrier")) return 2;
        if (containsAny(s, "march","mar","mars"))         return 3;
        if (containsAny(s, "april","apr","avril"))        return 4;
        if (containsAny(s, "may","mai"))                  return 5;
        if (containsAny(s, "june","jun","juin"))          return 6;
        if (containsAny(s, "july","jul","juillet"))       return 7;
        if (containsAny(s, "august","aug","août","aout")) return 8;
        if (containsAny(s, "september","sep","septembre")) return 9;
        if (containsAny(s, "october","oct","octobre"))   return 10;
        if (containsAny(s, "november","nov","novembre")) return 11;
        if (containsAny(s, "december","dec","décembre","decembre")) return 12;
        return null;
    }

    // ── Fire time calculation ─────────────────────────────────────────────────

    private long computeFireTime() {
        int h24 = hour12 % 12;
        if (isPm) h24 += 12;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR,         year);
        c.set(Calendar.MONTH,        month - 1);
        c.set(Calendar.DAY_OF_MONTH, day);
        c.set(Calendar.HOUR_OF_DAY,  h24);
        c.set(Calendar.MINUTE,       minute);
        c.set(Calendar.SECOND,       0);
        c.set(Calendar.MILLISECOND,  0);
        return c.getTimeInMillis();
    }

    // ── Summary string ────────────────────────────────────────────────────────

    private String buildSummary() {
        String freq;
        switch (repeatType) {
            case "ONCE":    freq = context.getString(R.string.freq_once);    break;
            case "DAILY":   freq = context.getString(R.string.freq_daily);   break;
            case "WEEKLY":  freq = context.getString(R.string.freq_weekly);  break;
            case "MONTHLY": freq = context.getString(R.string.freq_monthly); break;
            case "YEARLY":  freq = context.getString(R.string.freq_yearly);  break;
            default:        freq = repeatType;
        }
        String ampm = isPm
                ? context.getString(R.string.ampm_pm)
                : context.getString(R.string.ampm_am);
        return String.format("%s — %04d-%02d-%02d — %d:%02d %s",
                freq, year, month, day, hour12, minute, ampm);
    }

    // ── Small helpers ─────────────────────────────────────────────────────────

    private boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private boolean isYes(String s) {
        return s.matches(
                ".*\\b(yes|yeah|yup|sure|okay|ok|correct|right|confirm|oui|ouais|d.accord)\\b.*");
    }

    private boolean isNo(String s) {
        return s.matches(
                ".*\\b(no|nope|nah|cancel|wrong|incorrect|non|annuler)\\b.*");
    }
}
