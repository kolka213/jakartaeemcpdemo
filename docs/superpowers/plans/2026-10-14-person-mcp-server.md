# Person MCP Server (MVP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein Jakarta-EE-WAR auf WildFly 38, das per MCP (Streamable HTTP, Endpoint `/mcp`) die Tools `list_persons` und `get_person_by_id` auf einer In-Memory-Tabelle `PERSON` bereitstellt.

**Architecture:** Ein `@WebListener` (`McpServerBootstrap`) baut beim Start den `McpServer` (MCP Java SDK, `HttpServletStreamableServerTransportProvider`) und registriert den Provider programmatisch als asynchrones Servlet auf `/mcp`. Die Tools (`PersonTools`) sind reine SDK-Handler ohne Servlet-/JPA-Wissen und rufen den CDI-Service `PersonService` (JPA auf WildFly-`ExampleDS`) auf.

**Tech Stack:** Jakarta EE 10 (`jakartaee-api` 10.0.0, provided), Java 21, WildFly 38.0.1.Final, MCP Java SDK 2.0.1 (`mcp-core` + `mcp-json-jackson2`), JPA 3.1/Hibernate (vom Server), JUnit 5.13.2, Maven (`./mvnw`, Maven 3.9.6).

**Spec:** `docs/superpowers/specs/2026-10-14-person-mcp-server-design.md`

## Global Constraints

- Java **21** (`maven.compiler.release=21`); Jakarta EE **10** (`jakarta.jakartaee-api:10.0.0`, `provided`) – WildFly 38 liefert Servlet 6.0 und Persistence 3.1.
- MCP SDK **2.0.1**: `io.modelcontextprotocol.sdk:mcp-core` und `mcp-json-jackson2` (nicht das Sammelartefakt `mcp`, das Jackson 3 zieht). `org.slf4j:slf4j-api:2.0.16` mit Scope `provided` (kommt vom Server, nicht ins WAR).
- `persistence.xml` Version **3.1**; `beans.xml` Schema **`beans_4_0.xsd`**; DataSource `java:jboss/datasources/ExampleDS`.
- Tabelle `PERSON` mit Spalten `id` (Long), `vorname`, `nachname`, `email` (deutsche Feldnamen); **5** Demo-Personen mit den IDs 1–5.
- MCP-Endpoint `/mcp`, Transport Streamable HTTP, Servlet mit `asyncSupported=true`.
- Tool-Namen exakt `list_persons` und `get_person_by_id`; Tool-Beschreibungen auf Englisch.
- Fehlertexte exakt: `Person with id <id> not found` und `Argument 'id' is required and must be an integer`; beide als `CallToolResult` mit `isError=true`.
- Keine Authentifizierung, keine Schreib-Tools, keine Weboberfläche. `HelloServlet` und `index.jsp` bleiben unangetastet.
- Paket `de.schwabe.jakartamcpserverdemo`; Dateien enden mit Zeilenumbruch.

## Review Focus

Eingaben/Zustände, die die Spec impliziert, aber nicht ausdrücklich nennt (wahrscheinlichste zuerst); jede Zeile ist in der genannten Task per Test oder Smoke-Schritt abgesichert:

1. `id` fehlt, ist `null`, ein String (`"abc"`, `"1"`), `true`, nicht ganzzahlig (`1.5`, `NaN`) oder `arguments` ist `null` → `isError=true` mit der festen Meldung, keine Exception (Task 3, Test `missingNullStringAndFractionalIdAreErrorsNotExceptions`).
2. Unbekannte oder negative `id` → `Person with id <id> not found` (Task 3, Tests `unknownIdReturnsDomainErrorWithMessage`, `negativeIdIsJustNotFound`).
3. Leere Tabelle → `list_persons` liefert `[]`, keinen Fehler (Task 3, Test `listPersonsOnEmptyTableReturnsEmptyArrayNotError`).
4. Umlaute und Anführungszeichen in Namen → gültiges JSON, das zurückgelesen werden kann (Task 3, Test `umlautsAndQuotesSurviveJsonRoundTrip`).
5. `id` als `1`, `1L`, `1.0`, `BigInteger` → alle gleichwertig akzeptiert (Task 3, Test `getPersonByIdAcceptsLongAndIntegralDouble`); Redeploy/Undeploy → Server startet danach sauber neu, ohne "Servlet already registered"-Fehler und mit frisch geseedeten Daten (Task 5, Schritt "Redeploy").

---

## File Structure

| Datei | Aktion | Verantwortung |
|---|---|---|
| `pom.xml` | Ändern | EE 10, Java 21, SDK-Abhängigkeiten, slf4j `provided`, Surefire |
| `src/main/resources/META-INF/beans.xml` | Ändern | CDI-4.0-Schema |
| `src/main/resources/META-INF/persistence.xml` | Ändern | Persistence-Unit `default` (3.1, JTA, ExampleDS, Seed) |
| `src/main/resources/META-INF/sql/seed-person.sql` | Neu | 5 Demo-Personen |
| `src/main/java/de/schwabe/jakartamcpserverdemo/Person.java` | Neu | JPA-Entity |
| `src/main/java/de/schwabe/jakartamcpserverdemo/PersonService.java` | Neu | Lesezugriff `findAll`, `findById` |
| `src/main/java/de/schwabe/jakartamcpserverdemo/PersonTools.java` | Neu | MCP-Tool-Spezifikationen und Handler |
| `src/main/java/de/schwabe/jakartamcpserverdemo/McpServerBootstrap.java` | Neu | Server-Aufbau, Servlet-Registrierung, Shutdown |
| `src/main/webapp/WEB-INF/jboss-web.xml` | Neu | `context-root=/` |
| `src/test/java/de/schwabe/jakartamcpserverdemo/PersonToolsTest.java` | Neu | Unit-Tests der Tool-Handler mit Fake-Service |

Hinweis zum ersten Commit: Das Repo hat noch **keinen Commit**; die bereits gestagten Skeleton-Dateien (`.gitignore`, `mvnw`, `.mvn/…`, `pom.xml`, …) gehen im ersten Commit (Task 1) mit hinein. Das ist gewollt. `.idea/encodings.xml` ist untracked und wird nicht mit hinzugefügt.

Commit-Nachrichten enden mit der Zeile `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

---

### Task 1: Skeleton auf Jakarta EE 10 / Java 21 umstellen und SDK einbinden

**Files:**
- Modify: `pom.xml` (komplett ersetzen)
- Modify: `src/main/resources/META-INF/beans.xml`
- Commit (bereits vorhanden, noch nicht eingecheckt): `docs/superpowers/specs/2026-10-14-person-mcp-server-design.md` und dieser Plan

**Interfaces:**
- Produces: Ein baubares WAR-Projekt mit allen Abhängigkeiten (`jakartaee-api 10.0.0`, `mcp-core`, `mcp-json-jackson2`, JUnit 5) für alle folgenden Tasks.

- [ ] **Step 1: `pom.xml` ersetzen**

Vollständiger neuer Inhalt:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>de.schwabe</groupId>
    <artifactId>jakartamcpserverdemo</artifactId>
    <version>1.0-SNAPSHOT</version>
    <name>jakartamcpserverdemo</name>
    <packaging>war</packaging>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>21</maven.compiler.release>
        <junit.version>5.13.2</junit.version>
        <mcp.version>2.0.1</mcp.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>jakarta.platform</groupId>
            <artifactId>jakarta.jakartaee-api</artifactId>
            <version>10.0.0</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-core</artifactId>
            <version>${mcp.version}</version>
        </dependency>
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-json-jackson2</artifactId>
            <version>${mcp.version}</version>
        </dependency>
        <!-- slf4j wird von WildFly bereitgestellt und nicht ins WAR gepackt -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.16</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.6.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: `beans.xml` auf CDI 4.0 umstellen**

Vollständiger neuer Inhalt von `src/main/resources/META-INF/beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd"
       version="4.0" bean-discovery-mode="annotated">
</beans>
```

(`annotated` ist der CDI-4-Default und macht ihn explizit; `@ApplicationScoped` ist ein bean-defining annotation.)

- [ ] **Step 3: Build ausführen**

Run: `./mvnw -q clean package`
Expected: Exit-Code 0, keine Ausgabe (keine Tests vorhanden, `HelloServlet` kompiliert gegen EE 10).

- [ ] **Step 4: WAR-Inhalt prüfen**

Run: `unzip -l target/jakartamcpserverdemo-1.0-SNAPSHOT.war | grep -E 'WEB-INF/lib/' | awk '{print $4}'`
Expected: Liste enthält `mcp-core-2.0.1.jar`, `mcp-json-jackson2-2.0.1.jar`, `jackson-databind-2.21.1.jar`, `reactor-core-3.7.0.jar`; **keine** `slf4j-api-*.jar`.

- [ ] **Step 5: Commit (erster Commit des Repos)**

```bash
git add pom.xml src/main/resources/META-INF/beans.xml docs/
git commit -m "build: Skeleton auf Jakarta EE 10 und Java 21 umstellen, MCP SDK einbinden

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Persistenzschicht (Entity, Service, persistence.xml, Seed-Daten)

**Files:**
- Create: `src/main/java/de/schwabe/jakartamcpserverdemo/Person.java`
- Create: `src/main/java/de/schwabe/jakartamcpserverdemo/PersonService.java`
- Modify: `src/main/resources/META-INF/persistence.xml` (komplett ersetzen)
- Create: `src/main/resources/META-INF/sql/seed-person.sql`

**Interfaces:**
- Produces: `Person(Long id, String vorname, String nachname, String email)` mit No-Arg-Konstruktor, Gettern/Settern (`getId()`, `getVorname()`, `getNachname()`, `getEmail()`); `PersonService` (`@ApplicationScoped`, nicht-final, Default-Konstruktor) mit `List<Person> findAll()` und `Optional<Person> findById(long id)`, beide `public` und `@Transactional`.

Diese Task hat keinen Unit-Test: JPA gegen `ExampleDS` ist ohne Container nicht sinnvoll testbar. Das Verhalten wird in Task 5 gegen den echten WildFly verifiziert.

- [ ] **Step 1: `Person.java` anlegen**

```java
package de.schwabe.jakartamcpserverdemo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "PERSON")
public class Person {

    @Id
    private Long id;
    private String vorname;
    private String nachname;
    private String email;

    public Person() {
    }

    public Person(Long id, String vorname, String nachname, String email) {
        this.id = id;
        this.vorname = vorname;
        this.nachname = nachname;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVorname() {
        return vorname;
    }

    public void setVorname(String vorname) {
        this.vorname = vorname;
    }

    public String getNachname() {
        return nachname;
    }

    public void setNachname(String nachname) {
        this.nachname = nachname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
```

- [ ] **Step 2: `PersonService.java` anlegen**

```java
package de.schwabe.jakartamcpserverdemo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PersonService {

    @PersistenceContext(unitName = "default")
    private EntityManager em;

    @Transactional
    public List<Person> findAll() {
        return em.createQuery("select p from Person p order by p.id", Person.class).getResultList();
    }

    @Transactional
    public Optional<Person> findById(long id) {
        return Optional.ofNullable(em.find(Person.class, id));
    }
}
```

- [ ] **Step 3: `persistence.xml` ersetzen**

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<persistence xmlns="https://jakarta.ee/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_1.xsd"
             version="3.1">
    <persistence-unit name="default" transaction-type="JTA">
        <jta-data-source>java:jboss/datasources/ExampleDS</jta-data-source>
        <class>de.schwabe.jakartamcpserverdemo.Person</class>
        <exclude-unlisted-classes>true</exclude-unlisted-classes>
        <properties>
            <property name="jakarta.persistence.schema-generation.database.action" value="drop-and-create"/>
            <property name="jakarta.persistence.sql-load-script-source" value="META-INF/sql/seed-person.sql"/>
        </properties>
    </persistence-unit>
</persistence>
```

- [ ] **Step 4: Seed-Skript anlegen**

`src/main/resources/META-INF/sql/seed-person.sql` – **genau ein Statement pro Zeile** (Hibernates Load-Script-Parser erwartet das):

```sql
INSERT INTO PERSON (id, vorname, nachname, email) VALUES (1, 'Anna', 'Schmidt', 'anna.schmidt@example.com');
INSERT INTO PERSON (id, vorname, nachname, email) VALUES (2, 'Berta', 'Meier', 'berta.meier@example.com');
INSERT INTO PERSON (id, vorname, nachname, email) VALUES (3, 'Clemens', 'Weber', 'clemens.weber@example.com');
INSERT INTO PERSON (id, vorname, nachname, email) VALUES (4, 'Dora', 'Fischer', 'dora.fischer@example.com');
INSERT INTO PERSON (id, vorname, nachname, email) VALUES (5, 'Emil', 'Wagner', 'emil.wagner@example.com');
```

- [ ] **Step 5: Bauen und Paketinhalt prüfen**

Run: `./mvnw -q clean package`
Expected: Exit-Code 0.

Run: `unzip -l target/jakartamcpserverdemo-1.0-SNAPSHOT.war | grep -E 'persistence.xml|seed-person.sql|Person(Service)?\.class'`
Expected: vier Treffer unter `WEB-INF/classes/` (`persistence.xml`, `sql/seed-person.sql`, `Person.class`, `PersonService.class`).

Run: `xmllint --noout src/main/resources/META-INF/persistence.xml src/main/resources/META-INF/beans.xml`
Expected: keine Ausgabe (wohlgeformt).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/de/schwabe/jakartamcpserverdemo/Person.java \
        src/main/java/de/schwabe/jakartamcpserverdemo/PersonService.java \
        src/main/resources/META-INF/persistence.xml \
        src/main/resources/META-INF/sql/seed-person.sql
git commit -m "feat: Person-Entity, PersonService und In-Memory-Persistenz mit Demodaten

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: MCP-Tools `list_persons` und `get_person_by_id` (TDD)

**Files:**
- Create: `src/test/java/de/schwabe/jakartamcpserverdemo/PersonToolsTest.java`
- Create: `src/main/java/de/schwabe/jakartamcpserverdemo/PersonTools.java`

**Interfaces:**
- Consumes: `PersonService.findAll(): List<Person>`, `PersonService.findById(long): Optional<Person>`, `Person(Long, String, String, String)` aus Task 2.
- Produces: `PersonTools(PersonService personService, McpJsonMapper jsonMapper)` mit `List<SyncToolSpecification> specifications()` (Reihenfolge: `list_persons`, `get_person_by_id`); paketsichtbare Handler `CallToolResult listPersons(McpSyncServerExchange, CallToolRequest)` und `CallToolResult getPersonById(McpSyncServerExchange, CallToolRequest)`. Wird in Task 4 vom Bootstrap benutzt.

SDK-Typen: `io.modelcontextprotocol.json.McpJsonMapper`/`McpJsonDefaults` (`mcp-core`), `io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification`, `io.modelcontextprotocol.server.McpSyncServerExchange`, `io.modelcontextprotocol.spec.McpSchema.{CallToolRequest, CallToolResult, TextContent, Tool}`.

- [ ] **Step 1: Failing Test schreiben**

`src/test/java/de/schwabe/jakartamcpserverdemo/PersonToolsTest.java`:

```java
package de.schwabe.jakartamcpserverdemo;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonToolsTest {

    private final McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();

    /** Fake ohne Datenbank: überschreibt beide Methoden, der EntityManager bleibt null. */
    private static PersonService serviceWith(Person... persons) {
        List<Person> all = List.of(persons);
        return new PersonService() {
            @Override
            public List<Person> findAll() {
                return all;
            }

            @Override
            public Optional<Person> findById(long id) {
                return all.stream().filter(p -> p.getId() == id).findFirst();
            }
        };
    }

    private PersonTools tools(Person... persons) {
        return new PersonTools(serviceWith(persons), jsonMapper);
    }

    private static Person anna() {
        return new Person(1L, "Anna", "Schmidt", "anna.schmidt@example.com");
    }

    private static CallToolRequest request(String tool, Map<String, Object> arguments) {
        return new CallToolRequest(tool, arguments, null);
    }

    private static String textOf(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    private static Map<String, Object> idArgument(Object id) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("id", id);
        return arguments;
    }

    @Test
    void registersBothTools() {
        List<SyncToolSpecification> specs = tools().specifications();

        assertEquals(List.of("list_persons", "get_person_by_id"),
                specs.stream().map(spec -> spec.tool().name()).toList());
    }

    @Test
    void listPersonsReturnsAllPersonsAsJsonArray() throws IOException {
        Person berta = new Person(2L, "Berta", "Meier", "berta.meier@example.com");

        CallToolResult result = tools(anna(), berta).listPersons(null, request("list_persons", Map.of()));

        assertFalse(Boolean.TRUE.equals(result.isError()));
        List<?> parsed = jsonMapper.readValue(textOf(result), List.class);
        assertEquals(2, parsed.size());
        Map<?, ?> first = (Map<?, ?>) parsed.get(0);
        assertEquals(1, first.get("id"));
        assertEquals("Anna", first.get("vorname"));
        assertEquals("Schmidt", first.get("nachname"));
        assertEquals("anna.schmidt@example.com", first.get("email"));
    }

    @Test
    void listPersonsWithoutArgumentsMapStillWorks() {
        CallToolResult result = tools(anna()).listPersons(null, request("list_persons", null));

        assertFalse(Boolean.TRUE.equals(result.isError()));
    }

    @Test
    void listPersonsOnEmptyTableReturnsEmptyArrayNotError() {
        CallToolResult result = tools().listPersons(null, request("list_persons", Map.of()));

        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertEquals("[]", textOf(result));
    }

    @Test
    void getPersonByIdReturnsJsonObject() throws IOException {
        CallToolResult result = tools(anna()).getPersonById(null, request("get_person_by_id", idArgument(1)));

        assertFalse(Boolean.TRUE.equals(result.isError()));
        Map<?, ?> parsed = jsonMapper.readValue(textOf(result), Map.class);
        assertEquals("Anna", parsed.get("vorname"));
    }

    @Test
    void getPersonByIdAcceptsLongAndIntegralDouble() {
        PersonTools tools = tools(anna());

        for (Object id : new Object[]{1, 1L, 1.0, new BigInteger("1")}) {
            CallToolResult result = tools.getPersonById(null, request("get_person_by_id", idArgument(id)));
            assertFalse(Boolean.TRUE.equals(result.isError()), "id=" + id + " (" + id.getClass() + ")");
        }
    }

    @Test
    void unknownIdReturnsDomainErrorWithMessage() {
        CallToolResult result = tools(anna()).getPersonById(null, request("get_person_by_id", idArgument(99)));

        assertTrue(result.isError());
        assertEquals("Person with id 99 not found", textOf(result));
    }

    @Test
    void negativeIdIsJustNotFound() {
        CallToolResult result = tools(anna()).getPersonById(null, request("get_person_by_id", idArgument(-1)));

        assertTrue(result.isError());
        assertEquals("Person with id -1 not found", textOf(result));
    }

    @Test
    void missingNullStringAndFractionalIdAreErrorsNotExceptions() {
        PersonTools tools = tools(anna());
        List<Map<String, Object>> badArguments = List.of(
                Map.of(),
                idArgument(null),
                idArgument("1"),
                idArgument("abc"),
                idArgument(1.5),
                idArgument(Double.NaN),
                idArgument(true));

        for (Map<String, Object> arguments : badArguments) {
            CallToolResult result = tools.getPersonById(null, request("get_person_by_id", arguments));
            assertTrue(result.isError(), "arguments=" + arguments);
            assertEquals("Argument 'id' is required and must be an integer", textOf(result));
        }
        assertTrue(tools.getPersonById(null, request("get_person_by_id", null)).isError());
    }

    @Test
    void umlautsAndQuotesSurviveJsonRoundTrip() throws IOException {
        Person tricky = new Person(7L, "Jörg \"Jo\"", "Müller-Lüdenscheidt", "jörg@example.com");

        CallToolResult result = tools(tricky).getPersonById(null, request("get_person_by_id", idArgument(7)));

        Map<?, ?> parsed = jsonMapper.readValue(textOf(result), Map.class);
        assertEquals("Jörg \"Jo\"", parsed.get("vorname"));
        assertEquals("Müller-Lüdenscheidt", parsed.get("nachname"));
    }
}
```

- [ ] **Step 2: Test ausführen – muss fehlschlagen**

Run: `./mvnw -q test`
Expected: FAIL mit Kompilierfehler `cannot find symbol: class PersonTools` in `PersonToolsTest`.

- [ ] **Step 3: Minimale Implementierung**

`src/main/java/de/schwabe/jakartamcpserverdemo/PersonTools.java`:

```java
package de.schwabe.jakartamcpserverdemo;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PersonTools {

    static final String LIST_PERSONS = "list_persons";
    static final String GET_PERSON_BY_ID = "get_person_by_id";

    private final PersonService personService;
    private final McpJsonMapper jsonMapper;

    public PersonTools(PersonService personService, McpJsonMapper jsonMapper) {
        this.personService = personService;
        this.jsonMapper = jsonMapper;
    }

    public List<SyncToolSpecification> specifications() {
        Tool listPersons = Tool.builder(LIST_PERSONS, Map.of("type", "object", "properties", Map.of()))
                .description("Returns all persons as a JSON array of objects with the fields "
                        + "id, vorname, nachname and email.")
                .build();

        Tool getPersonById = Tool.builder(GET_PERSON_BY_ID, Map.of(
                        "type", "object",
                        "properties", Map.of("id", Map.of(
                                "type", "integer",
                                "description", "The id of the person to look up.")),
                        "required", List.of("id")))
                .description("Returns the person with the given id as a JSON object with the fields "
                        + "id, vorname, nachname and email. Returns an error if no such person exists.")
                .build();

        return List.of(
                SyncToolSpecification.builder().tool(listPersons).callHandler(this::listPersons).build(),
                SyncToolSpecification.builder().tool(getPersonById).callHandler(this::getPersonById).build());
    }

    CallToolResult listPersons(McpSyncServerExchange exchange, CallToolRequest request) {
        return text(toJson(personService.findAll()));
    }

    CallToolResult getPersonById(McpSyncServerExchange exchange, CallToolRequest request) {
        Object rawId = request.arguments() == null ? null : request.arguments().get("id");
        Optional<Long> id = toLong(rawId);
        if (id.isEmpty()) {
            return error("Argument 'id' is required and must be an integer");
        }
        return personService.findById(id.get())
                .map(person -> text(toJson(person)))
                .orElseGet(() -> error("Person with id " + id.get() + " not found"));
    }

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CallToolResult text(String text) {
        return CallToolResult.builder().content(List.of(new TextContent(text))).build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().content(List.of(new TextContent(message))).isError(true).build();
    }

    /** Accepts integral numbers only (also 1.0), rejects 1.5, strings, null and anything else. */
    private static Optional<Long> toLong(Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return Optional.of(((Number) value).longValue());
        }
        if (value instanceof Number number) {
            try {
                return Optional.of(new BigDecimal(number.toString()).longValueExact());
            } catch (ArithmeticException | NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Tests ausführen – müssen bestehen**

Run: `./mvnw test`
Expected: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/schwabe/jakartamcpserverdemo/PersonTools.java \
        src/test/java/de/schwabe/jakartamcpserverdemo/PersonToolsTest.java
git commit -m "feat: MCP-Tools list_persons und get_person_by_id mit Unit-Tests

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Bootstrap – MCP-Server aufbauen und als Servlet registrieren

**Files:**
- Create: `src/main/java/de/schwabe/jakartamcpserverdemo/McpServerBootstrap.java`
- Create: `src/main/webapp/WEB-INF/jboss-web.xml`

**Interfaces:**
- Consumes: `PersonTools(PersonService, McpJsonMapper)` und `PersonTools.specifications()` aus Task 3; `PersonService` aus Task 2 (per `@Inject`).
- Produces: Beim Deployment ein Servlet `mcpServlet` auf `/mcp` (`asyncSupported=true`), das den MCP-Server bedient.

Der SDK-Provider prüft den Pfad mit `requestURI.endsWith("/mcp")` (verifiziert in den SDK-Sourcen). Der Endpoint funktioniert also unter jedem Kontext-Pfad; `jboss-web.xml` dient nur dazu, dass die URL `http://localhost:8080/mcp` statt `…/jakartamcpserverdemo_war_exploded/mcp` lautet.

Auch diese Task hat keinen Unit-Test (Servlet-Container nötig). Die Verifikation erfolgt in Task 5.

- [ ] **Step 1: `McpServerBootstrap.java` anlegen**

```java
package de.schwabe.jakartamcpserverdemo;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.inject.Inject;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class McpServerBootstrap implements ServletContextListener {

    static final String MCP_ENDPOINT = "/mcp";

    @Inject
    PersonService personService;

    private McpSyncServer mcpServer;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();

        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(MCP_ENDPOINT)
                .build();

        mcpServer = McpServer.sync(transport)
                .serverInfo("person-mcp-server", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(new PersonTools(personService, jsonMapper).specifications())
                .build();

        ServletRegistration.Dynamic registration = event.getServletContext().addServlet("mcpServlet", transport);
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        registration.addMapping(MCP_ENDPOINT);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (mcpServer != null) {
            mcpServer.closeGracefully();
        }
    }
}
```

- [ ] **Step 2: `jboss-web.xml` anlegen**

`src/main/webapp/WEB-INF/jboss-web.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jboss-web>
    <context-root>/</context-root>
</jboss-web>
```

- [ ] **Step 3: Bauen und alle Tests ausführen**

Run: `./mvnw clean package`
Expected: `Tests run: 10, Failures: 0, Errors: 0`, `BUILD SUCCESS`.

Run: `unzip -l target/jakartamcpserverdemo-1.0-SNAPSHOT.war | grep -E 'McpServerBootstrap|jboss-web.xml'`
Expected: `WEB-INF/classes/…/McpServerBootstrap.class` und `WEB-INF/jboss-web.xml`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/schwabe/jakartamcpserverdemo/McpServerBootstrap.java \
        src/main/webapp/WEB-INF/jboss-web.xml
git commit -m "feat: MCP-Server beim Start aufbauen und als Servlet auf /mcp registrieren

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Deployment- und Smoke-Test auf WildFly 38

Verifiziert die vier offenen Punkte der Spec gegen den echten Server. Die WildFly-Installation des Nutzers bleibt unverändert: ein **Kopie** des `standalone`-Verzeichnisses dient als Server-Basis. Port-Offset 100 vermeidet Kollisionen mit einem evtl. laufenden WildFly aus IDEA (HTTP dann auf **8180**, Management auf 10090).

**Files:** keine Änderungen am Projekt (nur Verifikation). Schlägt ein Schritt fehl: **Anhalten**, das relevante Log (`$BASE/wf.log`) und den Befund dem Nutzer berichten, nicht improvisieren.

**Interfaces:**
- Consumes: das WAR `target/jakartamcpserverdemo-1.0-SNAPSHOT.war` aus Task 4.

Vorbereitung der Variablen (in jeder Shell-Sitzung neu setzen; `SCRATCH` = beliebiges leeres Verzeichnis **außerhalb** des Projekts, z. B. das Scratchpad der Sitzung):

```bash
export WILDFLY_HOME="$HOME/Library/wildfly-38.0.1.Final"
export BASE="$SCRATCH/wildfly-base"
export URL="http://localhost:8180/mcp"
```

- [ ] **Step 1: WAR bauen und Server-Basis vorbereiten**

```bash
./mvnw -q clean package
rm -rf "$BASE" && cp -R "$WILDFLY_HOME/standalone" "$BASE"
cp target/jakartamcpserverdemo-1.0-SNAPSHOT.war "$BASE/deployments/"
```
Expected: Build ohne Fehler; `$BASE/deployments/jakartamcpserverdemo-1.0-SNAPSHOT.war` existiert.

- [ ] **Step 2: WildFly starten (im Hintergrund)**

```bash
"$WILDFLY_HOME/bin/standalone.sh" -Djboss.server.base.dir="$BASE" -Djboss.socket.binding.port-offset=100 > "$BASE/wf.log" 2>&1 &
```
Warten, bis der Server und das Deployment fertig sind:
```bash
until grep -qE 'WFLYSRV0025|WFLYCTL0013|WFLYSRV0026' "$BASE/wf.log"; do sleep 2; done; grep -E 'WFLYSRV0010|WFLYSRV0025|WFLYCTL0013|ERROR' "$BASE/wf.log" | head -20
```
Expected: eine `WFLYSRV0010: Deployed "jakartamcpserverdemo-1.0-SNAPSHOT.war"`-Zeile und `WFLYSRV0025: … started`; **keine** `ERROR`-Zeilen und kein `WFLYCTL0013` (Operation failed). Prüft die Spec-Punkte 1, 2 (Start ohne `NoSuchMethodError`/`IllegalStateException`) und 4 (kein Klassenpfad-Konflikt) auf Startebene.

- [ ] **Step 3: `initialize` und Session-ID holen**

```bash
SID=$(curl -s -D - -o "$BASE/init-body.json" -X POST "$URL" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"0"}}}' \
  | awk -F': ' 'tolower($1)=="mcp-session-id"{print $2}' | tr -d '\r')
echo "SID=$SID"; cat "$BASE/init-body.json"
```
Expected: `SID` ist nicht leer; die Antwort enthält `"serverInfo":{"name":"person-mcp-server","version":"1.0.0"}` und `"tools"` unter `capabilities`. Ein 404 hier bedeutet: `jboss-web.xml` wurde nicht übernommen (Spec-Punkt 3) – dann den Pfad testweise mit `http://localhost:8180/jakartamcpserverdemo-1.0-SNAPSHOT/mcp` versuchen und das Ergebnis berichten (der Endpoint funktioniert unter jedem Kontext-Pfad).

- [ ] **Step 4: `initialized`-Notification senden**

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$URL" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
```
Expected: `202`.

- [ ] **Step 5: `tools/list`**

```bash
curl -s --max-time 10 -X POST "$URL" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```
Expected: SSE-Antwort (`event:`/`data:`-Zeilen), `data` enthält `"name":"list_persons"` und `"name":"get_person_by_id"` mit `inputSchema`.

- [ ] **Step 6: `list_persons` aufrufen (prüft JPA, Seed, ExampleDS)**

```bash
curl -s --max-time 10 -X POST "$URL" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_persons","arguments":{}}}'
```
Expected: `data` enthält den Text-Content mit allen 5 Personen (`Anna`, `Berta`, `Clemens`, `Dora`, `Emil`), kein `"isError":true`.

- [ ] **Step 7: `get_person_by_id` – Treffer, unbekannte ID, ungültige ID**

```bash
call() { curl -s --max-time 10 -X POST "$URL" \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"get_person_by_id\",\"arguments\":$1}}"; echo; }
call '{"id":3}'
call '{"id":99}'
call '{"id":"abc"}'
```
Expected:
- `{"id":3}` → Text-Content mit `Clemens Weber`, kein Fehler.
- `{"id":99}` → `"isError":true` und Text `Person with id 99 not found`.
- `{"id":"abc"}` → eine Fehlerantwort (SDK-Schema-Validierung oder unsere Meldung `Argument 'id' is required and must be an integer`); wichtig: **kein** HTTP-500-HTML und kein Stacktrace im Body.

- [ ] **Step 8: Redeploy (Review Focus 5)**

```bash
rm "$BASE/deployments/jakartamcpserverdemo-1.0-SNAPSHOT.war"
until [ -f "$BASE/deployments/jakartamcpserverdemo-1.0-SNAPSHOT.war.undeployed" ]; do sleep 1; done
cp target/jakartamcpserverdemo-1.0-SNAPSHOT.war "$BASE/deployments/"
until [ -f "$BASE/deployments/jakartamcpserverdemo-1.0-SNAPSHOT.war.deployed" ]; do sleep 1; done
grep -cE 'ERROR' "$BASE/wf.log"
```
Dann Schritte 3, 4 und 6 wiederholen (neue Session-ID erforderlich, die alte ist nach dem Undeploy ungültig).
Expected: `*.deployed`-Marker erscheint (nicht `*.failed`), `ERROR`-Zählung unverändert gegenüber Schritt 2 (bzw. 0), `list_persons` liefert wieder 5 Personen.

- [ ] **Step 9: WildFly sauber beenden und aufräumen**

```bash
"$WILDFLY_HOME/bin/jboss-cli.sh" --connect --controller=localhost:10090 command=:shutdown
rm -rf "$BASE"
```
Expected: `{"outcome" => "success"}`; `jps`/`ps` zeigt keinen WildFly-Prozess aus `$BASE` mehr.

- [ ] **Step 10: Ergebnis dokumentieren**

Fasse dem Nutzer pro Spec-Verifikationspunkt (1–4) zusammen: bestätigt / nicht bestätigt, mit den relevanten Logzeilen. Optional bietest du an, dass der Nutzer es zusätzlich über die IDEA-Run-Konfiguration "JBoss/WildFly 38.0.0.Final" prüft (Endpoint dann `http://localhost:8080/mcp`, z. B. mit dem MCP Inspector oder `claude mcp add --transport http person http://localhost:8080/mcp`).

Kein Commit nötig (keine Dateiänderungen).
