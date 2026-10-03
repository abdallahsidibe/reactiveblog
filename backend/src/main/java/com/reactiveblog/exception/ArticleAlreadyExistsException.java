package com.reactiveblog.exception;

public class ArticleAlreadyExistsException extends RuntimeException {

    public ArticleAlreadyExistsException(String title) {
        super("Un article avec le titre « " + title + " » existe déjà");
    }
}
