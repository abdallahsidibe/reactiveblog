package com.reactiveblog.exception;

public class ArticleNotFoundException extends RuntimeException {

    public ArticleNotFoundException(Long id) {
        super("Article introuvable avec l'id : " + id);
    }
}
