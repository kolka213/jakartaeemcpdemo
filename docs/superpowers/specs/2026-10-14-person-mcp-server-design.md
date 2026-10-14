# Person MCP Server (MVP) – Design

Datum: 2026-10-14

## Ziel

Ein minimaler MCP-Server (Model Context Protocol) auf Jakarta EE, der MCP-Clients zwei lesende Tools bereitstellt, mit denen Daten einer Tabelle `PERSON` (In-Memory-DB mit Demodaten) über einen Service abgefragt werden können.

**Erfolgskriterium:** Ein MCP-Client (oder `curl`) kann sich gegen `http://localhost:8080/mcp` verbinden, `tools/list` zeigt `list_persons` und `get_person_by_id`, und `tools/call` liefert die Demodaten bzw. einen sauberen Fehler für unbekannte IDs.

**Nicht-Ziele (MVP):** Weboberfläche, Authentifizierung, Schreib-Tools, Origin-Check (WildFly lauscht standardmäßig nur auf localhost), Arquillian-/Container-Integrationstests.

## Rahmenbedingungen (verifiziert)

- Laufzeit: **WildFly 38.0.1.Final** unter `~/Library/wildfly-38.0.1.Final` (IDEA-Run-Config "JBoss/WildFly 38.0.0.Final", deployt Artefakt `jakartamcpserverdemo:war exploded`).
- WildFly 38 liefert **Jakarta EE 10**: `jakarta.servlet-api-6.0.0`, `jakarta.persistence-api-3.1.0`. Das Skeleton (EE 11, Java 8) wird daher auf **Jakarta EE 10 und Java 21** angepasst (vom Nutzer freigegeben). Lokal sind Java 21 und 25 installiert.
- `ExampleDS` (`java:jboss/datasources/ExampleDS`, H2 in-memory) ist in `standalone.xml` vorhanden und Default-DataSource.
- MCP Java SDK **2.0.1** (Maven Central: `mcp`, `mcp-core`, `mcp-json-jackson2`, `mcp-json-jackson3`); Class-File-Version 61 (Java 17+). `mcp-core` ist gegen Servlet 6.1 gebaut, ruft im Servlet-Transport aber nur Methoden auf, die auch in Servlet 6.0 existieren (Prüfung auf Methodennamen-Ebene, 21 Aufrufe; endgültige Bestätigung erst durch Deployment).
- Der Streamable-HTTP-Transport des SDK nutzt `startAsync`; das Servlet muss `asyncSupported=true` registriert sein.
- Die SDK-Doku zeigt keine Einbindung in einen reinen Jakarta-Container (nur Spring). Die Registrierung per `ServletContext.addServlet` ist daher eine eigene Lösung und beim Deployment zu verifizieren.

## Architektur

Paket `de.schwabe.jakartamcpserverdemo`:

| Komponente | Verantwortung | Abhängigkeiten |
|---|---|---|
| `Person` (`@Entity`, Tabelle `PERSON`) | Felder `id` (`Long`, `@Id`), `vorname`, `nachname`, `email` | – |
| `PersonService` (`@ApplicationScoped`) | `List<Person> findAll()`, `Optional<Person> findById(long id)`; nur lesend | `@PersistenceContext EntityManager` |
| `PersonTools` | Baut die zwei `SyncToolSpecification`s, serialisiert Ergebnisse als JSON-Text; kennt weder Servlet noch JPA | `PersonService`, SDK-`JsonMapper` |
| `McpServerBootstrap` (`@WebListener`, `ServletContextListener`) | Beim Start: `HttpServletStreamableServerTransportProvider` (Endpoint `/mcp`) und `McpServer.sync(...)` bauen, Tools registrieren, Provider mit `asyncSupported=true` als Servlet auf `/mcp` registrieren. Beim Undeploy: Server sauber schließen. | `PersonTools`, `ServletContext` |

`PersonService` wird per `@Inject` in `McpServerBootstrap` bezogen (ein `@WebListener` unterstützt Injection) und an `PersonTools` weitergereicht. Die Tool-Handler laufen auf SDK-Threads ohne Request-Scope; deshalb ist der Service `@ApplicationScoped`, und die Transaktionsgrenze liegt im Service (`@Transactional` an den Methoden, `@PersistenceContext` mit JTA).

Bestehende Skeleton-Dateien `HelloServlet` und `index.jsp` bleiben unangetastet.

## Persistenz

`persistence.xml` (Version **3.1**, Schema `persistence_3_1.xsd`):

- Persistence-Unit `default`, JTA, `jta-data-source` = `java:jboss/datasources/ExampleDS`.
- `jakarta.persistence.schema-generation.database.action` = `drop-and-create`.
- `jakarta.persistence.sql-load-script-source` = `META-INF/sql/seed-person.sql` mit **5 Demo-Personen** (feste IDs 1–5).

## MCP-Tools

Alle Tool-Beschreibungen auf Englisch (LLM-Clients); Feldnamen der Daten deutsch.

1. **`list_persons`** – keine Argumente (leeres Objekt-Schema). Ergebnis: ein `TextContent` mit JSON-Array aller Personen `[{"id":1,"vorname":"…","nachname":"…","email":"…"}, …]`.
2. **`get_person_by_id`** – Pflichtargument `id` (integer).
   - Gefunden: `TextContent` mit JSON-Objekt der Person.
   - Nicht gefunden: `CallToolResult` mit `isError(true)` und Text `Person with id <id> not found` (recoverable Domain-Fehler laut SDK-Doku, das LLM kann es erneut versuchen).
   - Ungültige Eingabe: Das SDK validiert Argumente standardmäßig gegen das `inputSchema`; die Validierung bleibt aktiviert.
   - Unerwartete Fehler (z. B. DB nicht erreichbar): Exception propagiert, das SDK übersetzt sie in einen MCP-Fehler.

## Build und Deployment

**`pom.xml`:**
- `jakarta.jakartaee-api` **10.0.0** (`provided`), Java **21** (`maven.compiler.release=21`).
- `io.modelcontextprotocol.sdk:mcp-core:2.0.1` und `mcp-json-jackson2:2.0.1` (Jackson 2; das Sammelartefakt `mcp` würde Jackson 3 ziehen). `slf4j-api` wird vom Server bereitgestellt und nicht ins WAR gepackt. Zu verifizieren beim Deployment: Konflikte zwischen mitgepackten Bibliotheken (`reactor-core`, Jackson, JSON-Schema-Validator) und WildFly-Modulen.
- JUnit 5 bleibt bestehen.

**EE-11-Altlasten im Skeleton:**
- `persistence.xml`: Version/Schema 3.2 → 3.1.
- `META-INF/beans.xml`: Schema `beans_4_1.xsd` → `beans_4_0.xsd` (CDI 4.0 = EE 10). `web.xml` (`web-app_6_0.xsd`) passt bereits zu EE 10.

**`WEB-INF/jboss-web.xml`:** `context-root` = `/`, damit der Endpoint `http://localhost:8080/mcp` lautet (sonst hängt IDEA `/jakartamcpserverdemo_war_exploded` davor). Ob WildFly das beim Exploded-Deployment übernimmt, wird beim Deployment geprüft; Fallback ist der längere Pfad.

## Tests

- **Unit-Tests** (JUnit 5) für `PersonTools` mit einem Fake-`PersonService`: `list_persons` liefert alle Personen als JSON; `get_person_by_id` liefert Treffer; unbekannte ID liefert `isError=true`. Kein Container nötig.
- **Smoke-Test** gegen den laufenden WildFly per `curl`: `initialize` → `tools/list` → `tools/call` für beide Tools (inkl. unbekannter ID). Dies ist zugleich die Verifikation der Annahmen zu Servlet-6.0-Kompatibilität, Async-Registrierung, Kontext-Pfad und Bibliothekskonflikten.

## Offene Verifikationspunkte (beim Deployment zu klären)

1. Läuft das SDK (gegen Servlet 6.1 gebaut) fehlerfrei auf Servlet 6.0 / Undertow?
2. Funktioniert die programmatische Servlet-Registrierung mit `asyncSupported=true` aus dem `@WebListener` heraus, und der Zugriff auf den CDI-Service dort?
3. Wird `jboss-web.xml` `context-root=/` beim Exploded-Deployment aus IDEA übernommen?
4. Gibt es Klassenpfad-Konflikte zwischen den gepackten SDK-Abhängigkeiten und WildFly-Modulen?
