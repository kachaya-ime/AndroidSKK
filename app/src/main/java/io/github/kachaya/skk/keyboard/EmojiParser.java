package io.github.kachaya.skk.keyboard;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.kachaya.skk.AssetLoader;

/**
 * tool 側で生成された emoji.txt を読み込み、グループごとに分類した絵文字アイテムを返すパーサークラスです。
 */
public class EmojiParser {
    private static final String TAG = "EmojiParser";

    public static class EmojiItem {
        public String emoji;
        public String description;
        public String group;

        public EmojiItem(String emoji, String description, String group) {
            this.emoji = emoji;
            this.description = description;
            this.group = group;
        }
    }

    /**
     * emoji.txt を読み込み、グループごとに分類したマップを返します。
     *
     * @param context コンテキスト
     * @return グループ名をキーとする EmojiItem リストのマップ
     */
    public static Map<String, List<EmojiItem>> parse(Context context) {
        Map<String, List<EmojiItem>> groupMap = new LinkedHashMap<>();

        String text = AssetLoader.loadAssetString(context, "emoji.txt");
        if (text == null) {
            return getFallbackEmojis();
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String currentGroup = "Smileys & Emotion";
            List<EmojiItem> currentItems = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("# ")) {
                    currentGroup = line.substring(2).trim();
                    currentItems = new ArrayList<>();
                    groupMap.put(currentGroup, currentItems);
                    continue;
                }
                if (currentItems == null) {
                    currentItems = new ArrayList<>();
                    groupMap.put(currentGroup, currentItems);
                }
                currentItems.add(new EmojiItem(line, "", currentGroup));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse emoji.txt", e);
        }

        if (groupMap.isEmpty()) {
            return getFallbackEmojis();
        }

        return groupMap;
    }

    private static Map<String, List<EmojiItem>> getFallbackEmojis() {
        Map<String, List<EmojiItem>> map = new LinkedHashMap<>();
        List<EmojiItem> smileys = new ArrayList<>();
        smileys.add(new EmojiItem("😀", "grinning face", "Smileys & Emotion"));
        smileys.add(new EmojiItem("😂", "face with tears of joy", "Smileys & Emotion"));
        smileys.add(new EmojiItem("😍", "smiling face with heart-eyes", "Smileys & Emotion"));
        smileys.add(new EmojiItem("👍", "thumbs up", "People & Body"));
        smileys.add(new EmojiItem("❤️", "red heart", "Smileys & Emotion"));
        map.put("Smileys & Emotion", smileys);
        return map;
    }
}
