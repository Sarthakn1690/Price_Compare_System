package com.pricecomparison.exception;

public class CustomExceptions {

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(Long id) {
            super("Product not found with id: " + id);
        }
    }

    public static class ScrapingFailedException extends RuntimeException {
        public ScrapingFailedException(String message) {
            super("Scraping failed: " + message);
        }
        public ScrapingFailedException(String message, Throwable cause) {
            super("Scraping failed: " + message, cause);
        }
    }

    public static class InvalidUrlException extends RuntimeException {
        public InvalidUrlException(String message) {
            super("Invalid URL: " + message);
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException() {
            super("Rate limit exceeded. Please try again later.");
        }
    }
}
