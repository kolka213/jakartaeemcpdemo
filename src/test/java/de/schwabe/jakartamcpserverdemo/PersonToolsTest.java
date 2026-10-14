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
        CallToolResult nullArguments = tools.getPersonById(null, request("get_person_by_id", null));
        assertTrue(nullArguments.isError());
        assertEquals("Argument 'id' is required and must be an integer", textOf(nullArguments));
    }

    @Test
    void getPersonByIdSchemaRequiresIntegerId() {
        SyncToolSpecification spec = tools().specifications().stream()
                .filter(s -> s.tool().name().equals("get_person_by_id"))
                .findFirst()
                .orElseThrow();
        Map<String, Object> schema = spec.tool().inputSchema();

        assertEquals("object", schema.get("type"));
        assertEquals(List.of("id"), schema.get("required"));
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> idSchema = (Map<?, ?>) properties.get("id");
        assertEquals("integer", idSchema.get("type"));
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
