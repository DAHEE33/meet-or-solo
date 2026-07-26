package com.survey.meetorsolo.global.text;

import java.util.regex.Pattern;

/** 관광공사 API가 소개글 등에 흔히 섞어 보내는 단순 HTML 태그/엔티티를 평문으로 정리한다. */
public final class TextSanitizer {

    private static final Pattern LINE_BREAK_TAG = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern BLANK_LINES = Pattern.compile("\n{3,}");

    private TextSanitizer() {
    }

    public static String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        String withLineBreaks = LINE_BREAK_TAG.matcher(value).replaceAll("\n");
        String withoutTags = HTML_TAG.matcher(withLineBreaks).replaceAll("");
        String withoutEntities = withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return BLANK_LINES.matcher(withoutEntities).replaceAll("\n\n").trim();
    }
}
