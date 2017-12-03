
package ru.mipt.java2017.hw3.application;
import java.math.BigDecimal;

public class WrongISBNException extends Exception {
  private BigDecimal isbn;

  WrongISBNException(BigDecimal isbn) {
    this.isbn = isbn;
  }

  @Override
  public String toString() {
    return "ISBN " + isbn + " is not unique or not exists";
  }
}
