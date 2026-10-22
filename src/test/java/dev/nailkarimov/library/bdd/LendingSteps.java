package dev.nailkarimov.library.bdd;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;

import dev.nailkarimov.library.support.Api;
import dev.nailkarimov.library.support.Isbns;
import dev.nailkarimov.library.support.TestLibrary;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Steps in the library's own words. One library runs for the whole feature run; every scenario creates its own
 * members and titles, so names like "Ann" are local to a scenario.
 */
public class LendingSteps {
  private static TestLibrary lib;
  private static Api api;

  private final Map<String, Long> members = new HashMap<>();
  private final Map<String, String> titles = new HashMap<>();
  private final Map<String, Long> loans = new HashMap<>();
  private Response last;

  @BeforeAll
  public static void start() {
    lib = TestLibrary.start();
    api = new Api(lib);
  }

  @AfterAll
  public static void stop() {
    lib.close();
  }

  @Given("today is {isoDate}")
  public void today(LocalDate date) {
    lib.clock.set(date);
  }

  @Given("the library has {int} cop(y)(ies) of {string}")
  public void stock(int copies, String title) {
    String isbn = Isbns.next();
    lib.knowsBook(isbn, title);
    api.post("/books", Map.of("isbn", isbn, "copies", copies)).then().statusCode(201);
    titles.put(title, isbn);
  }

  @Given("the library has 1 copy of each of these books:")
  public void stockEach(List<String> list) {
    list.forEach(t -> stock(1, t));
  }

  @Given("{word} is a member")
  public void member(String name) {
    members.put(name, api.newMember());
  }

  @When("{word} borrows {string}")
  public void borrows(String name, String title) {
    last = api.borrow(members.get(name), titles.get(title));
    if (last.statusCode() == 201) {
      loans.put(name + "/" + title, last.jsonPath().getLong("id"));
    }
  }

  @Given("{word} has borrowed {string}")
  public void hasBorrowed(String name, String title) {
    borrows(name, title);
    assertThat("borrowing " + title, last.statusCode(), equalTo(201));
  }

  @And("{int} days have passed")
  public void daysPass(int days) {
    lib.clock.plusDays(days);
  }

  @When("{word} returns {string}")
  public void returns(String name, String title) {
    last = api.giveBack(loans.get(name + "/" + title));
    assertThat(last.statusCode(), equalTo(200));
  }

  @When("{word} pays {bigdecimal}")
  public void pays(String name, BigDecimal amount) {
    last = api.post("/members/" + members.get(name) + "/payments", Map.of("amount", amount));
    assertThat(last.statusCode(), equalTo(200));
  }

  @Then("the loan is due on {isoDate}")
  public void dueOn(LocalDate date) {
    assertThat(last.statusCode(), equalTo(201));
    assertThat(last.jsonPath().getString("due"), equalTo(date.toString()));
  }

  @Then("{string} has {int} copies available")
  public void available(String title, int n) {
    api.get("/books/" + titles.get(title)).then().body("available", equalTo(n));
  }

  @Then("the library refuses with {string}")
  public void refuses(String code) {
    assertThat(last.statusCode(), equalTo(409));
    assertThat(last.jsonPath().getString("code"), equalTo(code));
  }

  @Then("the fine is {bigdecimal}")
  public void fine(BigDecimal amount) {
    assertThat(new BigDecimal(last.jsonPath().getString("fine")), comparesEqualTo(amount));
  }

  @And("{word} owes {bigdecimal}")
  public void owes(String name, BigDecimal amount) {
    String fines = api.get("/members/" + members.get(name)).jsonPath().getString("fines");
    assertThat(new BigDecimal(fines), comparesEqualTo(amount));
  }
}
