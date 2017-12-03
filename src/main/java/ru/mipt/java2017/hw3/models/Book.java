
package ru.mipt.java2017.hw3.models;

import java.math.BigDecimal;
import javax.persistence.*;

@Entity
@Table(name = "books")
public class Book {
  @Id
  @Column(name = "id")
  @GeneratedValue
  private Long id;

  @Column(name = "isbn", length = 13)
  private BigDecimal isbn;

  @Column(name = "title", length = 100)
  private String title;

  @Column(name = "cover", length = 400)
  private String cover;

  public Long getId() {
    return id;
  }

  public BigDecimal getIsbn() {
    return isbn;
  }

  public String getTitle() {
    return title;
  }

  public String getCover() {
    return cover;
  }

  public void setTitle(String newTitle) {
    this.title = newTitle;
  }

  public void setIsbn(BigDecimal newIsbn) {
    this.isbn = newIsbn;
  }
}
