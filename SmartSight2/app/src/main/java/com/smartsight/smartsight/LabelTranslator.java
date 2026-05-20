package com.example.smartsight;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

public class LabelTranslator {

    // Plain noun — used for STT keyword matching (no article)
    private static final Map<String, String> FR = new HashMap<>();

    // With correct French article — used for TTS announcements
    private static final Map<String, String> FR_TTS = new HashMap<>();

    static {
        FR.put("person",        "personne");
        FR.put("bicycle",       "vélo");
        FR.put("car",           "voiture");
        FR.put("motorcycle",    "moto");
        FR.put("airplane",      "avion");
        FR.put("bus",           "bus");
        FR.put("train",         "train");
        FR.put("truck",         "camion");
        FR.put("boat",          "bateau");
        FR.put("traffic light", "feu de circulation");
        FR.put("fire hydrant",  "bouche d'incendie");
        FR.put("stop sign",     "panneau stop");
        FR.put("parking meter", "parcmètre");
        FR.put("bench",         "banc");
        FR.put("bird",          "oiseau");
        FR.put("cat",           "chat");
        FR.put("dog",           "chien");
        FR.put("horse",         "cheval");
        FR.put("sheep",         "mouton");
        FR.put("cow",           "vache");
        FR.put("elephant",      "éléphant");
        FR.put("bear",          "ours");
        FR.put("zebra",         "zèbre");
        FR.put("giraffe",       "girafe");
        FR.put("backpack",      "sac à dos");
        FR.put("umbrella",      "parapluie");
        FR.put("handbag",       "sac à main");
        FR.put("tie",           "cravate");
        FR.put("suitcase",      "valise");
        FR.put("frisbee",       "frisbee");
        FR.put("skis",          "skis");
        FR.put("snowboard",     "snowboard");
        FR.put("sports ball",   "ballon de sport");
        FR.put("kite",          "cerf-volant");
        FR.put("baseball bat",  "batte de baseball");
        FR.put("baseball glove","gant de baseball");
        FR.put("skateboard",    "skateboard");
        FR.put("surfboard",     "planche de surf");
        FR.put("tennis racket", "raquette de tennis");
        FR.put("bottle",        "bouteille");
        FR.put("wine glass",    "verre à vin");
        FR.put("cup",           "tasse");
        FR.put("fork",          "fourchette");
        FR.put("knife",         "couteau");
        FR.put("spoon",         "cuillère");
        FR.put("bowl",          "bol");
        FR.put("banana",        "banane");
        FR.put("apple",         "pomme");
        FR.put("sandwich",      "sandwich");
        FR.put("orange",        "orange");
        FR.put("broccoli",      "brocoli");
        FR.put("carrot",        "carotte");
        FR.put("hot dog",       "hot-dog");
        FR.put("pizza",         "pizza");
        FR.put("donut",         "beignet");
        FR.put("cake",          "gâteau");
        FR.put("chair",         "chaise");
        FR.put("couch",         "canapé");
        FR.put("potted plant",  "plante en pot");
        FR.put("bed",           "lit");
        FR.put("dining table",  "table à manger");
        FR.put("toilet",        "toilettes");
        FR.put("tv",            "télévision");
        FR.put("laptop",        "ordinateur portable");
        FR.put("mouse",         "souris");
        FR.put("remote",        "télécommande");
        FR.put("keyboard",      "clavier");
        FR.put("cell phone",    "téléphone portable");
        FR.put("microwave",     "micro-ondes");
        FR.put("oven",          "four");
        FR.put("toaster",       "grille-pain");
        FR.put("sink",          "évier");
        FR.put("refrigerator",  "réfrigérateur");
        FR.put("book",          "livre");
        FR.put("clock",         "horloge");
        FR.put("vase",          "vase");
        FR.put("scissors",      "ciseaux");
        FR.put("teddy bear",    "ours en peluche");
        FR.put("hair drier",    "sèche-cheveux");
        FR.put("toothbrush",    "brosse à dents");

        // With grammatically correct French article (un/une/des)
        FR_TTS.put("person",        "une personne");
        FR_TTS.put("bicycle",       "un vélo");
        FR_TTS.put("car",           "une voiture");
        FR_TTS.put("motorcycle",    "une moto");
        FR_TTS.put("airplane",      "un avion");
        FR_TTS.put("bus",           "un bus");
        FR_TTS.put("train",         "un train");
        FR_TTS.put("truck",         "un camion");
        FR_TTS.put("boat",          "un bateau");
        FR_TTS.put("traffic light", "un feu de circulation");
        FR_TTS.put("fire hydrant",  "une bouche d'incendie");
        FR_TTS.put("stop sign",     "un panneau stop");
        FR_TTS.put("parking meter", "un parcmètre");
        FR_TTS.put("bench",         "un banc");
        FR_TTS.put("bird",          "un oiseau");
        FR_TTS.put("cat",           "un chat");
        FR_TTS.put("dog",           "un chien");
        FR_TTS.put("horse",         "un cheval");
        FR_TTS.put("sheep",         "un mouton");
        FR_TTS.put("cow",           "une vache");
        FR_TTS.put("elephant",      "un éléphant");
        FR_TTS.put("bear",          "un ours");
        FR_TTS.put("zebra",         "un zèbre");
        FR_TTS.put("giraffe",       "une girafe");
        FR_TTS.put("backpack",      "un sac à dos");
        FR_TTS.put("umbrella",      "un parapluie");
        FR_TTS.put("handbag",       "un sac à main");
        FR_TTS.put("tie",           "une cravate");
        FR_TTS.put("suitcase",      "une valise");
        FR_TTS.put("frisbee",       "un frisbee");
        FR_TTS.put("skis",          "des skis");
        FR_TTS.put("snowboard",     "un snowboard");
        FR_TTS.put("sports ball",   "un ballon de sport");
        FR_TTS.put("kite",          "un cerf-volant");
        FR_TTS.put("baseball bat",  "une batte de baseball");
        FR_TTS.put("baseball glove","un gant de baseball");
        FR_TTS.put("skateboard",    "un skateboard");
        FR_TTS.put("surfboard",     "une planche de surf");
        FR_TTS.put("tennis racket", "une raquette de tennis");
        FR_TTS.put("bottle",        "une bouteille");
        FR_TTS.put("wine glass",    "un verre à vin");
        FR_TTS.put("cup",           "une tasse");
        FR_TTS.put("fork",          "une fourchette");
        FR_TTS.put("knife",         "un couteau");
        FR_TTS.put("spoon",         "une cuillère");
        FR_TTS.put("bowl",          "un bol");
        FR_TTS.put("banana",        "une banane");
        FR_TTS.put("apple",         "une pomme");
        FR_TTS.put("sandwich",      "un sandwich");
        FR_TTS.put("orange",        "une orange");
        FR_TTS.put("broccoli",      "un brocoli");
        FR_TTS.put("carrot",        "une carotte");
        FR_TTS.put("hot dog",       "un hot-dog");
        FR_TTS.put("pizza",         "une pizza");
        FR_TTS.put("donut",         "un beignet");
        FR_TTS.put("cake",          "un gâteau");
        FR_TTS.put("chair",         "une chaise");
        FR_TTS.put("couch",         "un canapé");
        FR_TTS.put("potted plant",  "une plante en pot");
        FR_TTS.put("bed",           "un lit");
        FR_TTS.put("dining table",  "une table à manger");
        FR_TTS.put("toilet",        "des toilettes");
        FR_TTS.put("tv",            "une télévision");
        FR_TTS.put("laptop",        "un ordinateur portable");
        FR_TTS.put("mouse",         "une souris");
        FR_TTS.put("remote",        "une télécommande");
        FR_TTS.put("keyboard",      "un clavier");
        FR_TTS.put("cell phone",    "un téléphone portable");
        FR_TTS.put("microwave",     "un micro-ondes");
        FR_TTS.put("oven",          "un four");
        FR_TTS.put("toaster",       "un grille-pain");
        FR_TTS.put("sink",          "un évier");
        FR_TTS.put("refrigerator",  "un réfrigérateur");
        FR_TTS.put("book",          "un livre");
        FR_TTS.put("clock",         "une horloge");
        FR_TTS.put("vase",          "un vase");
        FR_TTS.put("scissors",      "des ciseaux");
        FR_TTS.put("teddy bear",    "un ours en peluche");
        FR_TTS.put("hair drier",    "un sèche-cheveux");
        FR_TTS.put("toothbrush",    "une brosse à dents");
    }

    /**
     * Returns the plain noun for STT keyword matching — no article.
     * Use this when comparing against what the user said.
     */
    public static String translate(Context context, String englishLabel) {
        if (englishLabel == null) return "";
        String lang = SettingsPrefs.getLanguage(context);
        String normalized = englishLabel.toLowerCase().trim();
        if ("fr".equals(lang)) {
            return FR.getOrDefault(normalized, englishLabel);
        }
        return englishLabel;
    }

    /**
     * Returns the label with correct grammar for TTS announcements.
     * French: includes the proper indefinite article ("un chien", "une vache").
     * English: returns the plain noun — the English templates already supply "a ".
     */
    public static String translateForTts(Context context, String englishLabel) {
        if (englishLabel == null) return "";
        String lang = SettingsPrefs.getLanguage(context);
        String normalized = englishLabel.toLowerCase().trim();
        if ("fr".equals(lang)) {
            String withArticle = FR_TTS.get(normalized);
            if (withArticle != null) return withArticle;
            return FR.getOrDefault(normalized, englishLabel);
        }
        return englishLabel;
    }
}
