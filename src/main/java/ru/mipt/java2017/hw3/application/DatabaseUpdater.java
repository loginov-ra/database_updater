
package ru.mipt.java2017.hw3.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mipt.java2017.hw3.models.Author;
import ru.mipt.java2017.hw3.models.Book;
import ru.mipt.java2017.hw3.models.BookAuthor;

public class DatabaseUpdater {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final EntityManager entityManager;
  private final EntityManagerFactory entityManagerFactory;

  private DatabaseUpdater(String databaseUrl) {
    Properties props = new Properties();
    props.setProperty("javax.persistence.jdbc.url", databaseUrl);
    entityManagerFactory = Persistence.createEntityManagerFactory("booksdb", props);

    entityManager = entityManagerFactory.createEntityManager();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        shutdown();
      }
    });
  }

  private Book getBookByISBN(BigDecimal ISBN) throws WrongISBNException {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Book> query = builder.createQuery(Book.class);

    Predicate equalsToISBN = builder.equal(query.from(Book.class).get("isbn"), ISBN);
    query.where(equalsToISBN);
    List<Book> books = entityManager.createQuery(query).getResultList();

    if (books.size() != 1) {
      throw new WrongISBNException(ISBN);
    }

    return books.get(0);
  }

  private static String eraseSpacesFromSuffix(String str) {
    int lastIndex = str.length() - 1;
    while (str.charAt(lastIndex) == ' ') {
      --lastIndex;
    }
    return str.substring(0, lastIndex + 1);
  }

  private static BigDecimal getISBNFromString(String isbnString) {
    isbnString = eraseSpacesFromSuffix(isbnString);
    String idStr = isbnString.substring(8);
    return new BigDecimal(Long.parseLong(idStr));
  }

  private static String[] getAuthorsList(String authorsString) {
    authorsString = eraseSpacesFromSuffix(authorsString);
    return authorsString.split("\\s*,\\s*");
  }

  /**
   * @param newBook Book found in excel file to correct DB
   * @return Book with the same ISBN
   * @throws WrongISBNException in case of not found ISBN or not unique
   **/
  public Book addBookToDB(Book newBook) throws WrongISBNException {
    entityManager.getTransaction().begin();
    Book bookToChange = getBookByISBN(newBook.getIsbn());
    bookToChange.setTitle(newBook.getTitle());
    entityManager.getTransaction().commit();
    return bookToChange;
  }

  private Author checkIfAuthorExists(String name) {
    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Author> query = builder.createQuery(Author.class);

    Predicate equalsToName = builder.equal(query.from(Author.class).get("name"), name);
    query.where(equalsToName);
    List<Author> authors = entityManager.createQuery(query).getResultList();

    if(authors.size() > 0) {
      return authors.get(0);
    } else {
      return null;
    }
  }

  private Author addAuthorToDB(String name) {
    entityManager.getTransaction().begin();
    Author author = new Author();
    author.setName(name);
    entityManager.persist(author);
    entityManager.flush();
    entityManager.getTransaction().commit();

    logger.info("Id for new author is {}", author.getId());
    return author;
  }

  public ArrayList<Author> addAuthorsToDB(String[] names) {
    ArrayList<Author> authors = new ArrayList<>();

    for (int i = 0; i < names.length; ++i) {
      Author existing = checkIfAuthorExists(names[i]);
      if (existing != null) {
        authors.add(existing);
      } else {
        authors.add(addAuthorToDB(names[i]));
      }
    }

    return authors;
  }

  public void fillAuthorToBookRelation(Book book, ArrayList<Author> authors) {
    for (int i = 0; i < authors.size(); ++i) {
      entityManager.getTransaction().begin();
      BookAuthor connection = new BookAuthor();
      connection.setAuthor(authors.get(i));
      connection.setBook(book);
      connection.setNum(i + 1);
      entityManager.persist(connection);
      entityManager.getTransaction().commit();
    }
  }

  private void readAllBooks(String pathToFile) throws FileNotFoundException {
    InputStream inputStream = new FileInputStream(pathToFile);

    try {
      Workbook wb = WorkbookFactory.create(inputStream);
      Sheet sheet = wb.getSheetAt(0);
      int rowsCount = sheet.getLastRowNum();

      HashMap<String, Integer> headerToIndex = new HashMap<>();

      Row header = sheet.getRow(0);
      for (int i = 0; i < 3; ++i) {
        Cell nextCell = header.getCell(i);
        headerToIndex.put(nextCell.getStringCellValue(), i);
      }

      for (int i = 1; i <= rowsCount; ++i) {
        Row row = sheet.getRow(i);
        Cell nameCell = row.getCell(headerToIndex.get("Title"));
        Cell isbnCell = row.getCell(headerToIndex.get("ISBN"));
        Cell authorsCell = row.getCell(headerToIndex.get("Authors"));

        String name = nameCell.getStringCellValue();
        String isbnString = isbnCell.getStringCellValue();
        String authorsString = authorsCell.getStringCellValue();
        BigDecimal isbn = getISBNFromString(isbnString);
        logger.info("Found a book {} - {}", isbn, name);

        Book newBook = new Book();
        newBook.setTitle(name);
        newBook.setIsbn(isbn);

        Book book = addBookToDB(newBook);
        ArrayList<Author> authors = addAuthorsToDB(getAuthorsList(authorsString));
        fillAuthorToBookRelation(book, authors);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        inputStream.close();
      } catch (IOException e) {
        logger.error("Unable to close input file {}", pathToFile);
        System.exit(1);
      }
    }
  }

  private void makeHeading(XSSFWorkbook wb, XSSFSheet sheet, String[] headers) {
    Row heading = sheet.createRow(0);

    CellStyle style = wb.createCellStyle();
    Font font = wb.createFont();
    font.setBold(true);
    style.setFont(font);
    style.setAlignment(HorizontalAlignment.CENTER);

    for (int i = 0; i < headers.length; ++i) {
      heading.createCell(i);
      heading.getCell(i).setCellValue(headers[i]);
      heading.getCell(i).setCellStyle(style);
      sheet.setColumnWidth(i, 10000);
    }
    sheet.setColumnWidth(0, 2500);
  }

  private void dumpBooks(XSSFWorkbook wb) {
    XSSFSheet sheet = wb.createSheet("Books");

    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Book> query = builder.createQuery(Book.class);
    Root<Book> booksRoot = query.from(Book.class);
    query.select(booksRoot);
    List<Book> books = entityManager.createQuery(query).getResultList();

    String[] headers = {"ID", "Title", "ISBN", "Cover"};
    makeHeading(wb, sheet, headers);
    int rowNumber = 1;

    for (Book book : books) {
      Row newBook = sheet.createRow(rowNumber);

      Cell id = newBook.createCell(0);
      Cell title = newBook.createCell(1);
      Cell isbn = newBook.createCell(2);
      Cell cover = newBook.createCell(3);

      id.setCellValue(book.getId());
      title.setCellValue(book.getTitle());
      isbn.setCellValue("ISBN13: " + book.getIsbn());
      cover.setCellValue("");
      ++rowNumber;
    }
  }

  private void dumpAuthors(XSSFWorkbook wb) {
    XSSFSheet sheet = wb.createSheet("Authors");

    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Author> query = builder.createQuery(Author.class);
    Root<Author> authorsRoot = query.from(Author.class);
    query.select(authorsRoot);
    List<Author> authors = entityManager.createQuery(query).getResultList();

    String[] headers = {"ID", "Name"};
    makeHeading(wb, sheet, headers);
    int rowNumber = 1;

    for (Author author : authors) {
      Row newAuthor = sheet.createRow(rowNumber);

      Cell id = newAuthor.createCell(0);
      Cell title = newAuthor.createCell(1);

      id.setCellValue(author.getId());
      title.setCellValue(author.getName());
      ++rowNumber;
    }
  }

  private void dumpBooksAuthors(XSSFWorkbook wb) {
    XSSFSheet sheet = wb.createSheet("BooksAuthors");

    CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<BookAuthor> query = builder.createQuery(BookAuthor.class);
    Root<BookAuthor> bookAuthorsRoot = query.from(BookAuthor.class);
    query.select(bookAuthorsRoot);
    List<BookAuthor> bookAuthors = entityManager.createQuery(query).getResultList();

    String[] headers = {"ID", "BookID", "AuthorID", "Num"};
    makeHeading(wb, sheet, headers);
    int rowNumber = 1;

    for (BookAuthor bookAuthor : bookAuthors) {
      Row newBookAuthor = sheet.createRow(rowNumber);

      Cell id = newBookAuthor.createCell(0);
      Cell bookId = newBookAuthor.createCell(1);
      Cell authorId = newBookAuthor.createCell(2);
      Cell num = newBookAuthor.createCell(3);

      id.setCellValue(bookAuthor.getId());
      bookId.setCellValue(bookAuthor.getBook().getId());
      authorId.setCellValue(bookAuthor.getAuthor().getId());
      num.setCellValue(bookAuthor.getNum());
      ++rowNumber;
    }
  }

  public static void main(String[] args) throws WrongISBNException, IOException {
    DatabaseUpdater databaseUpdater = new DatabaseUpdater(args[0]);
    try {
      databaseUpdater.readAllBooks(args[1]);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    XSSFWorkbook wb = new XSSFWorkbook();
    databaseUpdater.dumpBooks(wb);
    databaseUpdater.dumpAuthors(wb);
    databaseUpdater.dumpBooksAuthors(wb);

    File outFile = new File(args[2]);
    FileOutputStream fileOutputStream = new FileOutputStream(outFile);
    wb.write(fileOutputStream);

    System.exit(0);
  }

  private void shutdown() {
    entityManagerFactory.close();
  }
}
