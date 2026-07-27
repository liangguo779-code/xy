package com.campus.ai.knowledge.pipeline;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StripTocDebug {
    static final Pattern TOC_HEADING = Pattern.compile(
        "^#{1,6}\s+(目录|目\s+录|目录索引|Contents|Table of Contents|TOC)\s*$",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) {
        String md = "# Handbook\n\n## Preface\n\npreface body\n\n## Contents\n\n- Chapter A\n- Chapter B\n- Chapter C\n\n## Chapter A\n\nactual content for chapter A\n\n## Chapter B\n\nactual content for chapter B\n";
        System.out.println("Input length: " + md.length());
        Matcher m = TOC_HEADING.matcher(md);
        boolean found = m.find();
        System.out.println("TOC_HEADING found: " + found);
        if (found) {
            System.out.println("tocStart=" + m.start() + " m.end()=" + m.end());
            System.out.println("group(0)=" + m.group(0));
            String hashes = m.group(0).replaceFirst("\s+.*", "");
            System.out.println("hashes=" + hashes + " len=" + hashes.length());
            String nextPattern = "^" + hashes + "\s+";
            String sub = md.substring(m.end());
            System.out.println("substring from m.end(): [" + sub.substring(0, Math.min(100, sub.length())) + "]");
            Matcher end = Pattern.compile(nextPattern, Pattern.MULTILINE).matcher(sub);
            boolean endFound = end.find();
            System.out.println("next same-level heading found: " + endFound);
            if (endFound) {
                System.out.println("end.start()=" + end.start() + " end.group()=" + end.group());
                String result = md.substring(0, m.start()) + md.substring(m.end() + end.start());
                System.out.println("Result length: " + result.length());
                System.out.println("Result: [" + result + "]");
                System.out.println("Contains Chapter A content: " + result.contains("actual content for chapter A"));
            }
        }
    }
}
