package com.reactiveblog.exception;

public class ArticleNotFoundException extends RuntimeException {

    public ArticleNotFoundException(String id) {
        super("Article introuvable avec l'id : " + id);
    }
}
