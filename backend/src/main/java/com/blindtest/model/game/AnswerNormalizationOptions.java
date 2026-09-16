package com.blindtest.model.game;

/** Explicit options for gameplay answer normalization. */
public record AnswerNormalizationOptions(boolean removeCommonLeadingArticle) {

    private static final AnswerNormalizationOptions PRESERVE_ARTICLES =
            new AnswerNormalizationOptions(false);
    private static final AnswerNormalizationOptions REMOVE_COMMON_ARTICLE =
            new AnswerNormalizationOptions(true);

    /** Keeps every normalized token, including a leading article. */
    public static AnswerNormalizationOptions preservingArticles() {
        return PRESERVE_ARTICLES;
    }

    /** Removes one recognized French or English leading article when another token remains. */
    public static AnswerNormalizationOptions removingCommonLeadingArticle() {
        return REMOVE_COMMON_ARTICLE;
    }
}
