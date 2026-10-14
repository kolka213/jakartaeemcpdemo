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
        return CallToolResult.builder().content(List.of(TextContent.builder(text).build())).build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().content(List.of(TextContent.builder(message).build())).isError(true).build();
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
