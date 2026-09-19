package io.github.kachaya.skk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * emojis.json または data/emoji-test.txt をパースし、app/src/main/assets/emoji.txt を生成するツールクラスです。
 */
public class EmojiConverter {

    public static void convert(String inputPath, String outputTxtPath) throws Exception {
        Map<String, List<EmojiItem>> groupMap = new LinkedHashMap<>();

        File jsonFile = new File("app/src/main/assets/emojis.json");
        if (jsonFile.exists()) {
            String jsonContent = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(new StringReader(jsonContent));
            String line;
            String currentGroup = "Smileys & Emotion";
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.endsWith("\": [")) {
                    int quote1 = line.indexOf('"');
                    int quote2 = line.indexOf('"', quote1 + 1);
                    if (quote1 != -1 && quote2 != -1) {
                        currentGroup = line.substring(quote1 + 1, quote2);
                    }
                } else if (line.contains("\"emoji\":")) {
                    int idx = line.indexOf("\"emoji\"");
                    int quote3 = line.indexOf('"', line.indexOf(':', idx));
                    int quote4 = line.indexOf('"', quote3 + 1);
                    if (quote3 != -1 && quote4 != -1) {
                        String emoji = line.substring(quote3 + 1, quote4);
                        groupMap.computeIfAbsent(currentGroup, k -> new ArrayList<>())
                                .add(new EmojiItem(emoji));
                    }
                }
            }
        }

        if (groupMap.isEmpty()) {
            File inputFile = new File(inputPath);
            List<String> lines = new ArrayList<>();
            if (inputFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
                    String fileLine;
                    while ((fileLine = reader.readLine()) != null) {
                        lines.add(fileLine);
                    }
                }
            }

            Set<String> componentCodePoints = new HashSet<>();
            String currentGroup = "Smileys & Emotion";
            for (String txtLine : lines) {
                txtLine = txtLine.trim();
                if (txtLine.isEmpty()) continue;

                if (txtLine.startsWith("# group:")) {
                    currentGroup = txtLine.substring(8).trim();
                    continue;
                }

                if (txtLine.startsWith("#")) {
                    continue;
                }

                int semicolonIdx = txtLine.indexOf(';');
                if (semicolonIdx != -1) {
                    if (currentGroup.equalsIgnoreCase("Component") || currentGroup.toLowerCase().contains("component") || currentGroup.toLowerCase().contains("compornent")) {
                        String codePointsPart = txtLine.substring(0, semicolonIdx).trim();
                        for (String cp : codePointsPart.split("\\s+")) {
                            if (!cp.isEmpty()) {
                                componentCodePoints.add(cp);
                            }
                        }
                    }
                }
            }

            currentGroup = "Smileys & Emotion";
            for (String txtLine : lines) {
                txtLine = txtLine.trim();
                if (txtLine.isEmpty()) continue;

                if (txtLine.startsWith("# group:")) {
                    currentGroup = txtLine.substring(8).trim();
                    continue;
                }

                if (txtLine.startsWith("#")) {
                    continue;
                }

                int semicolonIdx = txtLine.indexOf(';');
                if (semicolonIdx != -1 && txtLine.contains("fully-qualified")) {
                    if (currentGroup.equalsIgnoreCase("Component") || currentGroup.toLowerCase().contains("component") || currentGroup.toLowerCase().contains("compornent")) {
                        continue;
                    }

                    String codePointsPart = txtLine.substring(0, semicolonIdx).trim();
                    boolean containsComponentCode = false;
                    for (String cp : codePointsPart.split("\\s+")) {
                        if (componentCodePoints.contains(cp)) {
                            containsComponentCode = true;
                            break;
                        }
                    }
                    if (containsComponentCode) {
                        continue;
                    }

                    int hashIdx = txtLine.indexOf('#');
                    if (hashIdx != -1) {
                        String commentPart = txtLine.substring(hashIdx + 1).trim();
                        String[] parts = commentPart.split("\\s+", 3);
                        if (parts.length >= 1) {
                            String emoji = parts[0];
                            groupMap.computeIfAbsent(currentGroup, k -> new ArrayList<>())
                                    .add(new EmojiItem(emoji));
                        }
                    }
                }
            }
        }

        if (groupMap.isEmpty()) {
            List<EmojiItem> smileys = new ArrayList<>();
            smileys.add(new EmojiItem("😀"));
            smileys.add(new EmojiItem("😂"));
            smileys.add(new EmojiItem("😍"));
            smileys.add(new EmojiItem("👍"));
            smileys.add(new EmojiItem("❤️"));
            groupMap.put("Smileys & Emotion", smileys);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<EmojiItem>> entry : groupMap.entrySet()) {
            sb.append("# ").append(entry.getKey()).append("\n");
            for (EmojiItem item : entry.getValue()) {
                sb.append(item.emoji).append("\n");
            }
        }

        Files.createDirectories(Paths.get(outputTxtPath).getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputTxtPath), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        }
    }

    public static class EmojiItem {
        public String emoji;

        public EmojiItem(String emoji) {
            this.emoji = emoji;
        }
    }

    public static void main(String[] args) {
        String input = args.length > 0 ? args[0] : "data/emoji-test.txt";
        String output = args.length > 1 ? args[1] : "app/src/main/assets/emoji.txt";
        try {
            convert(input, output);
            System.out.println("Successfully generated " + output);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
