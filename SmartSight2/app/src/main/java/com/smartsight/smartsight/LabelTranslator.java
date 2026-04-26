package com.example.smartsight;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

public class LabelTranslator {

    private static final Map<String, String> FR = new HashMap<>();

    static {
        FR.put("person", "personne");
        FR.put("bicycle", "vélo");
        FR.put("car", "voiture");
        FR.put("motorcycle", "moto");
        FR.put("airplane", "avion");
        FR.put("bus", "bus");
        FR.put("train", "train");
        FR.put("truck", "camion");
        FR.put("boat", "bateau");
        FR.put("traffic light", "feu de circulation");
        FR.put("fire hydrant", "bouche d'incendie");
        FR.put("stop sign", "panneau stop");
        FR.put("parking meter", "parcmètre");
        FR.put("bench", "banc");
        FR.put("bird", "oiseau");
        FR.put("cat", "chat");
        FR.put("dog", "chien");
        FR.put("horse", "cheval");
        FR.put("sheep", "mouton");
        FR.put("cow", "vache");
        FR.put("elephant", "éléphant");
        FR.put("bear", "ours");
        FR.put("zebra", "zèbre");
        FR.put("giraffe", "girafe");
        FR.put("backpack", "sac à dos");
        FR.put("umbrella", "parapluie");
        FR.put("handbag", "sac à main");
        FR.put("tie", "cravate");
        FR.put("suitcase", "valise");
        FR.put("frisbee", "frisbee");
        FR.put("skis", "skis");
        FR.put("snowboard", "snowboard");
        FR.put("sports ball", "ballon de sport");
        FR.put("kite", "cerf-volant");
        FR.put("baseball bat", "batte de baseball");
        FR.put("baseball glove", "gant de baseball");
        FR.put("skateboard", "skateboard");
        FR.put("surfboard", "planche de surf");
        FR.put("tennis racket", "raquette de tennis");
        FR.put("bottle", "bouteille");
        FR.put("wine glass", "verre à vin");
        FR.put("cup", "tasse");
        FR.put("fork", "fourchette");
        FR.put("knife", "couteau");
        FR.put("spoon", "cuillère");
        FR.put("bowl", "bol");
        FR.put("banana", "banane");
        FR.put("apple", "pomme");
        FR.put("sandwich", "sandwich");
        FR.put("orange", "orange");
        FR.put("broccoli", "brocoli");
        FR.put("carrot", "carotte");
        FR.put("hot dog", "hot-dog");
        FR.put("pizza", "pizza");
        FR.put("donut", "beignet");
        FR.put("cake", "gâteau");
        FR.put("chair", "chaise");
        FR.put("couch", "canapé");
        FR.put("potted plant", "plante en pot");
        FR.put("bed", "lit");
        FR.put("dining table", "table à manger");
        FR.put("toilet", "toilettes");
        FR.put("tv", "télévision");
        FR.put("laptop", "ordinateur portable");
        FR.put("mouse", "souris");
        FR.put("remote", "télécommande");
        FR.put("keyboard", "clavier");
        FR.put("cell phone", "téléphone portable");
        FR.put("microwave", "micro-ondes");
        FR.put("oven", "four");
        FR.put("toaster", "grille-pain");
        FR.put("sink", "évier");
        FR.put("refrigerator", "réfrigérateur");
        FR.put("book", "livre");
        FR.put("clock", "horloge");
        FR.put("vase", "vase");
        FR.put("scissors", "ciseaux");
        FR.put("teddy bear", "ours en peluche");
        FR.put("hair drier", "sèche-cheveux");
        FR.put("toothbrush", "brosse à dents");
    }

    public static String translate(Context context, String englishLabel) {
        if (englishLabel == null) return "";

        String lang = SettingsPrefs.getLanguage(context);
        String normalized = englishLabel.toLowerCase().trim();

        if ("fr".equals(lang)) {
            return FR.getOrDefault(normalized, englishLabel);
        }
        return englishLabel;
    }
}
