package com.example.demo.Exceptions;

//Specific Exceptions
public class InvalidPromptException extends GeminiException {
 public InvalidPromptException(String message) {
     super(message);
 }
}
